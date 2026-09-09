package com.showtimeplayer.ui.screens.player

import android.app.Application
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
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
import com.showtimeplayer.data.db.entity.PresetEntity
import com.showtimeplayer.data.db.entity.TrackEntity
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
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.abs

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

    // Mirror of Media3 playlist since we can't serialize TrackEntity into MediaItem
    private val queueTracks = mutableListOf<TrackEntity>()
    private var countInJob: Job? = null
    private var songStartTimeNs: Long = 0L
    private var countInEndMs: Long = 0L
    private var isCountInActive: Boolean = false
    private var isPaused: Boolean = false
    private var pauseAccumulatedNs: Long = 0L
    private var lastResumeNs: Long = 0L

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
        }
    }

    fun playTrack(track: TrackEntity) {
        playTrackAsQueue(listOf(track), 0)
    }

    fun playWithCountIn(track: TrackEntity, preset: PresetEntity) {
        countInJob?.cancel()
        isCountInActive = false
        isPaused = false
        pauseAccumulatedNs = 0L

        val beat1Ms = preset.loopStartMs ?: 0L
        val bpm = preset.metronomeBpm
        val timeSigNum = preset.metronomeTimeSignatureNum
        val countInBars = preset.metronomeCountInBars

        if (bpm <= 0 || countInBars <= 0 || !preset.metronomeEnabled) {
            playTrack(track)
            return
        }

        countInJob = viewModelScope.launch {
            if (!::mediaController.isInitialized) return@launch

            // 1. Load track and preload into memory
            val mediaItem = trackToMediaItem(track)
            queueTracks.clear()
            queueTracks.add(track)
            _uiState.update {
                it.copy(
                    currentTrack = track,
                    error = null,
                    queue = listOf(track),
                    currentQueueIndex = 0,
                    beat1Ms = beat1Ms,
                )
            }
            mediaController.setMediaItems(listOf(mediaItem), 0, 0L)
            mediaController.prepare()

            // 2. Wait for song to be fully buffered
            awaitReady()

            // 3. Warm up audio system
            val warmupTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(44100)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(44100 * 20 / 1000 * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
            warmupTrack.release()

            // 4. Start song from 0
            songStartTimeNs = System.nanoTime()
            lastResumeNs = songStartTimeNs
            countInEndMs = beat1Ms
            isCountInActive = true
            _uiState.update { it.copy(isCountInActive = true, beat1Ms = beat1Ms) }
            mediaController.play()

            // 5. Run metronome alongside song
            val beatIntervalMs = 60_000L / bpm
            val beatIntervalNs = beatIntervalMs * 1_000_000L
            val totalClicks = countInBars * timeSigNum

            for (beat in 0 until totalClicks) {
                val clickTimeMs = beat * beatIntervalMs
                if (clickTimeMs >= countInEndMs) break

                // Wait until the precise target time
                val targetNs = songStartTimeNs + clickTimeMs * 1_000_000L
                while (true) {
                    if (isPaused) {
                        delay(10)
                        continue
                    }
                    val elapsedNs = System.nanoTime() - songStartTimeNs - pauseAccumulatedNs
                    val waitNs = targetNs - (songStartTimeNs + elapsedNs)
                    if (waitNs <= 0) break
                    delay(waitNs / 1_000_000)
                }

                playClick(accent = beat % timeSigNum == 0)
            }

            // 6. Count-in done
            isCountInActive = false
            _uiState.update { it.copy(isCountInActive = false) }
        }
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

    private suspend fun awaitIsPlaying(): Boolean {
        if (!::mediaController.isInitialized) return false
        if (mediaController.isPlaying) return true

        val deferred = CompletableDeferred<Unit>()
        val playingListener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) {
                    deferred.complete(Unit)
                }
            }
        }
        mediaController.addListener(playingListener)
        try {
            deferred.await()
        } finally {
            mediaController.removeListener(playingListener)
        }
        return true
    }

    private fun playClick(accent: Boolean) {
        val sampleRate = 44100
        val durationMs = if (accent) 15 else 10
        val frequency = if (accent) 1000.0 else 800.0
        val amplitude = if (accent) 0.8 else 0.4
        val numSamples = sampleRate * durationMs / 1000

        val buffer = ShortArray(numSamples)
        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            val envelope = Math.exp(-t * 200.0) // fast exponential decay
            val sample = sin(2.0 * PI * frequency * t) * amplitude * envelope
            buffer[i] = (sample * Short.MAX_VALUE).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }

        val bufferSize = buffer.size * 2 // 16-bit PCM = 2 bytes per sample
        val audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        audioTrack.write(buffer, 0, buffer.size)
        Thread.sleep(15)
        audioTrack.play()
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            audioTrack.release()
        }, durationMs + 100L)
    }

    fun playTrackAsQueue(tracks: List<TrackEntity>, startIndex: Int) {
        if (!::mediaController.isInitialized || tracks.isEmpty()) return

        countInJob?.cancel()
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
            )
        }
    }

    fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        if (!::mediaController.isInitialized) return
        if (fromIndex == toIndex) return
        if (fromIndex < 0 || fromIndex >= mediaController.mediaItemCount) return
        if (toIndex < 0 || toIndex >= mediaController.mediaItemCount) return

        // Move in mirror list
        val item = queueTracks.removeAt(fromIndex)
        queueTracks.add(toIndex, item)

        // Move in Media3 player
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
            }
        } else {
            mediaController.play()
            if (isCountInActive) {
                isPaused = false
                lastResumeNs = System.nanoTime()
            }
        }
    }

    fun seekTo(positionMs: Long) {
        if (!::mediaController.isInitialized) return
        if (isCountInActive) {
            countInJob?.cancel()
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

        // Rebuild queue from our mirror list, clamping to available count
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
                    // Compute position from wall clock during count-in
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
                    _uiState.update {
                        it.copy(isPlaying = false, positionMs = 0L)
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
        countInJob?.cancel()
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
