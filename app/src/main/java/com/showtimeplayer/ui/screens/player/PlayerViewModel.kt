package com.showtimeplayer.ui.screens.player

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.showtimeplayer.data.db.entity.MetronomeRegion
import com.showtimeplayer.data.db.entity.TrackEntity
import com.showtimeplayer.data.repository.MetronomeLayerWithRegions
import com.showtimeplayer.data.repository.PresetWithLayers
import com.showtimeplayer.player.metronome.MetronomeLayerConfig
import com.showtimeplayer.player.service.PlaybackService
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max

data class PlayerUiState(
    val currentTrack: TrackEntity? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val isBuffering: Boolean = false,
    val error: String? = null,
    val queue: List<TrackEntity> = emptyList(),
    val currentQueueIndex: Int = -1,
    val isCountInActive: Boolean = false,
    val beat1Ms: Long = 0L,
)

class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    private lateinit var mediaController: MediaController
    private var listener: PlayerListener? = null
    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private val metronomeEngine =
        (application as com.showtimeplayer.app.PracticeApplication).metronomeEngine

    private val queueTracks = mutableListOf<TrackEntity>()
    private val metronomeJobs = mutableListOf<Job>()
    private var countInJob: Job? = null
    private var songStartTimeNs: Long = 0L
    private var isCountInActive: Boolean = false
    private var isPaused: Boolean = false
    private var pauseAccumulatedNs: Long = 0L
    private var lastResumeNs: Long = 0L
    private var metronomeActiveStreams = mutableSetOf<Int>()

    init {
        val sessionToken = SessionToken(
            application,
            android.content.ComponentName(application, PlaybackService::class.java),
        )

        val controllerFuture = MediaController.Builder(application, sessionToken)
            .buildAsync()

        viewModelScope.launch {
            mediaController = withContext(Dispatchers.IO) {
                controllerFuture.get()
            }
            listener = PlayerListener()
            mediaController.addListener(listener!!)
            startProgressUpdates()
            metronomeEngine.initialize()
        }
    }

    fun playTrack(track: TrackEntity) {
        playTrackAsQueue(listOf(track), 0)
    }

    fun playWithLayers(track: TrackEntity, presetWithLayers: PresetWithLayers) {
        cancelMetronome()
        isCountInActive = false
        isPaused = false
        pauseAccumulatedNs = 0L

        if (!::mediaController.isInitialized) return

        val enabledLayers = presetWithLayers.layers.filter { it.layer.enabled && it.regions.isNotEmpty() }
        val songOffsetMs = presetWithLayers.preset.songOffsetMs.coerceAtLeast(0L)

        queueTracks.clear()
        queueTracks.add(track)
        _uiState.update {
            it.copy(
                currentTrack = track,
                error = null,
                queue = listOf(track),
                currentQueueIndex = 0,
                isCountInActive = false,
            )
        }
        mediaController.setMediaItems(listOf(trackToMediaItem(track)), 0, 0L)
        mediaController.prepare()

        if (enabledLayers.isEmpty() && songOffsetMs <= 0) {
            mediaController.play()
            return
        }

        countInJob = viewModelScope.launch {
            awaitReady()

            songStartTimeNs = System.nanoTime()
            lastResumeNs = songStartTimeNs
            isCountInActive = enabledLayers.isNotEmpty()
            _uiState.update { it.copy(isCountInActive = isCountInActive) }

            metronomeEngine.start()

            var earliestBeat1Ms = Long.MAX_VALUE

            for (layerWithRegions in enabledLayers) {
                for (region in layerWithRegions.regions) {
                    val job = launch { scheduleRegion(layerWithRegions, region) }
                    metronomeJobs.add(job)

                    if (region.countInBars > 0 && region.bpm > 0) {
                        val beatIntervalMs = 60_000L / region.bpm
                        val beat1Ms = region.startMs
                        if (beat1Ms < earliestBeat1Ms) {
                            earliestBeat1Ms = beat1Ms
                        }
                    }
                }
            }

            if (earliestBeat1Ms == Long.MAX_VALUE) {
                isCountInActive = false
                _uiState.update { it.copy(isCountInActive = false) }
            } else {
                _uiState.update { it.copy(beat1Ms = earliestBeat1Ms) }
            }

            // The song audio starts after the preset's timeline offset (lead-in silence).
            if (songOffsetMs > 0) {
                preciseDelay(songOffsetMs)
            }
            if (!isPaused) {
                mediaController.play()
            }
        }
    }

    private suspend fun scheduleRegion(
        layerWithRegions: MetronomeLayerWithRegions,
        region: MetronomeRegion,
    ) {
        if (region.bpm <= 0 || region.countInBars <= 0 || region.timeSignatureNum <= 0) return

        val beatIntervalMs = 60_000L / region.bpm
        val countInDurationMs = region.countInBars * beatIntervalMs * region.timeSignatureNum
        val countInStartMs = (region.startMs - countInDurationMs).coerceAtLeast(0L)
        val countInEndMs = region.startMs

        preciseDelay(countInStartMs)
        if (countInEndMs <= elapsedMsSinceStart()) return

        val streamId = region.id.toInt()
        val config = MetronomeLayerConfig(
            id = streamId,
            bpm = region.bpm.toFloat(),
            timeSigNum = region.timeSignatureNum,
            timeSigDenom = region.timeSignatureDenom,
        )
        metronomeEngine.addLayer(config)
        metronomeActiveStreams.add(streamId)

        preciseDelay(countInEndMs)
        metronomeEngine.removeLayer(streamId)
        metronomeActiveStreams.remove(streamId)
    }

    private fun elapsedMsSinceStart(): Long {
        if (songStartTimeNs <= 0) return 0
        val elapsedNs = System.nanoTime() - songStartTimeNs - pauseAccumulatedNs
        return (elapsedNs / 1_000_000).coerceAtLeast(0)
    }

    private suspend fun preciseDelay(targetMs: Long) {
        while (true) {
            if (isPaused) {
                delay(10)
                continue
            }
            val elapsed = elapsedMsSinceStart()
            val remaining = targetMs - elapsed
            if (remaining <= 0) break
            delay(remaining.coerceAtMost(50))
        }
    }

    private fun cancelMetronome() {
        countInJob?.cancel()
        countInJob = null
        metronomeJobs.forEach { it.cancel() }
        metronomeJobs.clear()
        for (streamId in metronomeActiveStreams) {
            metronomeEngine.removeLayer(streamId)
        }
        metronomeActiveStreams.clear()
        metronomeEngine.stop()
    }

    private suspend fun awaitReady(): Boolean {
        if (!::mediaController.isInitialized) return false
        if (mediaController.playbackState == Player.STATE_READY) return true

        val deferred = CompletableDeferred<Unit>()
        val readyListener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    deferred.complete(Unit)
                }
            }
        }
        mediaController.addListener(readyListener)
        try {
            deferred.await()
        } finally {
            mediaController.removeListener(readyListener)
        }
        return true
    }

    fun playTrackAsQueue(tracks: List<TrackEntity>, startIndex: Int) {
        if (!::mediaController.isInitialized || tracks.isEmpty()) return

        cancelMetronome()
        isCountInActive = false
        isPaused = false
        pauseAccumulatedNs = 0L

        val mediaItems = tracks.map { trackToMediaItem(it) }

        queueTracks.clear()
        queueTracks.addAll(tracks)

        _uiState.update {
            it.copy(
                currentTrack = tracks[startIndex],
                error = null,
                queue = tracks.toList(),
                currentQueueIndex = startIndex,
                isCountInActive = false,
            )
        }

        mediaController.setMediaItems(mediaItems, startIndex, 0L)
        mediaController.prepare()
        mediaController.play()
    }

    fun addToQueue(track: TrackEntity) {
        if (!::mediaController.isInitialized) return

        val mediaItem = trackToMediaItem(track)
        mediaController.addMediaItem(mediaItem)
        queueTracks.add(track)
        syncQueueFromController()
    }

    fun removeFromQueue(index: Int) {
        if (!::mediaController.isInitialized) return
        if (index < 0 || index >= mediaController.mediaItemCount) return

        mediaController.removeMediaItem(index)
        if (index < queueTracks.size) {
            queueTracks.removeAt(index)
        }

        if (mediaController.mediaItemCount == 0) {
            queueTracks.clear()
            _uiState.update {
                it.copy(
                    currentTrack = null,
                    queue = emptyList(),
                    currentQueueIndex = -1,
                    isPlaying = false,
                )
            }
            return
        }

        syncQueueFromController()
    }

    fun clearQueue() {
        if (!::mediaController.isInitialized) return
        cancelMetronome()
        mediaController.clearMediaItems()
        queueTracks.clear()
        _uiState.update {
            it.copy(
                currentTrack = null,
                queue = emptyList(),
                currentQueueIndex = -1,
                isPlaying = false,
                positionMs = 0L,
                durationMs = 0L,
                isCountInActive = false,
            )
        }
    }

    fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        if (!::mediaController.isInitialized) return
        if (fromIndex == toIndex) return
        if (fromIndex < 0 || fromIndex >= mediaController.mediaItemCount) return
        if (toIndex < 0 || toIndex >= mediaController.mediaItemCount) return

        val item = queueTracks.removeAt(fromIndex)
        queueTracks.add(toIndex, item)

        mediaController.moveMediaItem(fromIndex, toIndex)
        syncQueueFromController()
    }

    fun togglePlayPause() {
        if (!::mediaController.isInitialized) return
        if (mediaController.isPlaying) {
            mediaController.pause()
            if (isCountInActive) {
                isPaused = true
                pauseAccumulatedNs += System.nanoTime() - lastResumeNs
                metronomeEngine.stop()
            }
        } else {
            mediaController.play()
            if (isCountInActive) {
                isPaused = false
                lastResumeNs = System.nanoTime()
                metronomeEngine.start()
            }
        }
    }

    fun seekTo(positionMs: Long) {
        if (!::mediaController.isInitialized) return
        if (isCountInActive) {
            cancelMetronome()
            isCountInActive = false
            _uiState.update { it.copy(isCountInActive = false) }
        }
        mediaController.seekTo(positionMs)
        _uiState.update { it.copy(positionMs = positionMs) }
    }

    fun skipToPrevious() {
        if (!::mediaController.isInitialized) return
        mediaController.seekToPrevious()
    }

    fun skipToNext() {
        if (!::mediaController.isInitialized) return
        mediaController.seekToNext()
    }

    fun seekToQueueItem(index: Int) {
        if (!::mediaController.isInitialized) return
        if (index < 0 || index >= mediaController.mediaItemCount) return
        mediaController.seekTo(index, 0L)
        syncQueueFromController()
    }

    private fun syncQueueFromController() {
        if (!::mediaController.isInitialized) return
        val currentIdx = mediaController.currentMediaItemIndex
        val count = mediaController.mediaItemCount

        val tracks = queueTracks.take(count)
        val currentTrack = tracks.getOrNull(currentIdx)

        _uiState.update {
            it.copy(
                queue = tracks,
                currentQueueIndex = currentIdx,
                currentTrack = currentTrack,
            )
        }
    }

    private fun trackToMediaItem(track: TrackEntity): MediaItem {
        return MediaItem.Builder()
            .setMediaId(track.uri)
            .setUri(Uri.parse(track.uri))
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(track.title)
                    .setArtist(track.artist)
                    .setAlbumTitle(track.album)
                    .build(),
            )
            .build()
    }

    private fun startProgressUpdates() {
        viewModelScope.launch {
            while (true) {
                if (isCountInActive && !isPaused && songStartTimeNs > 0) {
                    val elapsedNs = System.nanoTime() - songStartTimeNs - pauseAccumulatedNs
                    val pos = (elapsedNs / 1_000_000).coerceAtLeast(0)
                    _uiState.update {
                        it.copy(
                            positionMs = pos,
                            isPlaying = true,
                            isBuffering = false,
                        )
                    }
                } else if (::mediaController.isInitialized && mediaController.isPlaying) {
                    _uiState.update {
                        it.copy(
                            positionMs = max(0, mediaController.currentPosition),
                            durationMs = max(0, mediaController.duration),
                            isPlaying = true,
                            isBuffering = false,
                        )
                    }
                }
                delay(200)
            }
        }
    }

    private inner class PlayerListener : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _uiState.update { it.copy(isPlaying = isPlaying) }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_BUFFERING -> {
                    _uiState.update { it.copy(isBuffering = true) }
                }
                Player.STATE_READY -> {
                    _uiState.update {
                        it.copy(
                            isBuffering = false,
                            durationMs = if (::mediaController.isInitialized) max(0, mediaController.duration) else 0L,
                        )
                    }
                }
                Player.STATE_ENDED -> {
                    cancelMetronome()
                    isCountInActive = false
                    _uiState.update {
                        it.copy(isPlaying = false, positionMs = 0L, isCountInActive = false)
                    }
                }
                Player.STATE_IDLE -> {
                    _uiState.update { it.copy(isBuffering = false) }
                }
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            syncQueueFromController()
        }

        override fun onPlayerError(error: PlaybackException) {
            _uiState.update {
                it.copy(error = error.message ?: "Playback error", isPlaying = false)
            }
        }
    }

    override fun onCleared() {
        cancelMetronome()
        listener?.let { mediaController.removeListener(it) }
        mediaController.release()
        super.onCleared()
    }

    class Factory(private val application: Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
            return PlayerViewModel(application) as T
        }
    }
}
