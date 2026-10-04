package com.showtimeplayer.ui.screens.player

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.showtimeplayer.app.PracticeApplication
import com.showtimeplayer.data.db.entity.MetronomeRegion
import com.showtimeplayer.data.db.entity.TrackEntity
import com.showtimeplayer.data.preferences.SavedPlaybackState
import com.showtimeplayer.data.repository.MetronomeLayerWithRegions
import com.showtimeplayer.data.repository.PresetWithLayers
import com.showtimeplayer.player.service.PlaybackService
import com.showtimeplayer.ui.screens.presets.MAX_CLICK_LEAD_MS
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt

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
    val presetName: String? = null,
    val presetSummary: String? = null,
)

class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    private lateinit var mediaController: MediaController
    private var listener: PlayerListener? = null
    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private val app = application as PracticeApplication

    private val metronomeEngine = app.metronomeEngine
    private val trackRepository = app.trackRepository
    private val playbackStatePreferences = app.playbackStatePreferences
    private val applicationScope = app.applicationScope

    private val queueTracks = mutableListOf<TrackEntity>()
    private val metronomeJobs = mutableListOf<Job>()
    private var countInJob: Job? = null
    private var songStartTimeNs: Long = 0L
    private var isCountInActive: Boolean = false
    private var isPaused: Boolean = false
    private var pauseAccumulatedNs: Long = 0L
    private var lastResumeNs: Long = 0L
    private var metronomeActiveStreams = mutableSetOf<Int>()
    private var activePlaybackRate: Float = 1.0f
    private var activePresetTrack: TrackEntity? = null
    private var activePreset: PresetWithLayers? = null
    private var activeEnabledLayers: List<MetronomeLayerWithRegions> = emptyList()
    // Media position at which the current count-in sequence began, so seeking re-syncs
    // the metronome ("load on beat") instead of losing it.
    private var seekBaseMs = 0L
    // Restoring is a cold-start concern only; it must never run over an active session.
    private var hasRestoredPlayback = false
    private val isSaveInFlight = AtomicBoolean(false)

    @Volatile
    private var isSavePending = false
    private var progressTicks = 0

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
            restoreSavedPlayback()
        }
    }

    fun playTrack(track: TrackEntity) {
        activePreset = null
        activePresetTrack = null
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

        val preset = presetWithLayers.preset
        activeEnabledLayers = enabledLayers
        seekBaseMs = 0L
        activePresetTrack = track
        activePreset = presetWithLayers
        activePlaybackRate = preset.playbackRate.coerceIn(0.25f, 2.0f)
        val semitoneFactor = 2.0.pow(preset.pitchOffsetSemitones / 12.0).toFloat()
        val pitch = if (preset.pitchFollowsSpeed) {
            semitoneFactor * activePlaybackRate
        } else {
            semitoneFactor
        }
        mediaController.setPlaybackParameters(
            PlaybackParameters(activePlaybackRate, pitch),
        )

        // Stop any current playback and clear the queue so the preset plays on its own.
        mediaController.stop()
        queueTracks.clear()
        queueTracks.add(track)
        _uiState.update {
            it.copy(
                currentTrack = track,
                error = null,
                queue = listOf(track),
                currentQueueIndex = 0,
                isCountInActive = false,
                presetName = preset.name,
                presetSummary = presetSummary(preset),
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
            startCountInScheduling()

            // Start the song audio after any leading count-in, so the sequence plays
            // count-in first, then the track (not the track overlapping the count-in).
            val leadingDownbeat = activeEnabledLayers.flatMap { it.regions }
                .filter { it.countInBars > 0 && it.bpm > 0 }
                .minOfOrNull { it.startMs }
                ?: 0L
            val effectiveSongStart = maxOf(songOffsetMs, leadingDownbeat)
            if (effectiveSongStart > 0) {
                preciseDelay(effectiveSongStart)
            }
            if (!isPaused) {
                mediaController.play()
            }
        }
    }

    // Anchors the count-in/click scheduling at the current media position (`seekBaseMs`)
    // and fires the pre-rendered clips for regions ahead of it.
    private fun startCountInScheduling() {
        songStartTimeNs = System.nanoTime()
        lastResumeNs = songStartTimeNs
        isCountInActive = activeEnabledLayers.isNotEmpty()
        _uiState.update { it.copy(isCountInActive = isCountInActive) }

        metronomeEngine.start()

        var earliestBeat1Ms = Long.MAX_VALUE
        for (layerWithRegions in activeEnabledLayers) {
            for (region in layerWithRegions.regions) {
                val job = viewModelScope.launch { scheduleRegion(layerWithRegions, region) }
                metronomeJobs.add(job)
                if (region.countInBars > 0 && region.bpm > 0 && region.startMs < earliestBeat1Ms) {
                    earliestBeat1Ms = region.startMs
                }
            }
        }

        if (earliestBeat1Ms == Long.MAX_VALUE) {
            isCountInActive = false
            _uiState.update { it.copy(isCountInActive = false) }
        } else {
            _uiState.update { it.copy(beat1Ms = earliestBeat1Ms) }
        }
    }

    private suspend fun scheduleRegion(
        layerWithRegions: MetronomeLayerWithRegions,
        region: MetronomeRegion,
    ) {
        if (region.bpm <= 0 || region.countInBars <= 0 || region.timeSignatureNum <= 0) return

        val beatIntervalMs = 60_000.0 / region.bpm
        val countInDurationMs = (region.countInBars * beatIntervalMs * region.timeSignatureNum).toLong()
        // Start early by the stream's output latency so the clicks are *heard* on the beat
        // (matters most on Bluetooth).
        val leadMs = metronomeEngine.latencyMs().toLong().coerceIn(0L, MAX_CLICK_LEAD_MS)
        // Schedule relative to the position we started from, so count-ins re-sync on seek.
        val countInStartMs = (region.startMs - seekBaseMs - countInDurationMs - leadMs)
            .coerceAtLeast(0L)

        preciseDelay(countInStartMs)

        val sr = metronomeEngine.sampleRate().takeIf { it > 0 } ?: 44100
        val clip = metronomeEngine.renderCountInClip(
            sampleRate = sr,
            playbackRate = activePlaybackRate,
            bpm = region.bpm,
            timeSigNum = region.timeSignatureNum,
            countInBars = region.countInBars,
            volume = region.volume,
        )
        if (clip.isNotEmpty()) {
            val id = region.id.toInt()
            // Starts immediately; the clip is pre-rendered to span exactly the count-in.
            metronomeEngine.addClip(id, clip, metronomeEngine.sampleCount(), 1.0f)
            metronomeActiveStreams.add(id)
        }
    }

    private fun elapsedMsSinceStart(): Long {
        if (songStartTimeNs <= 0) return 0
        val elapsedNs = System.nanoTime() - songStartTimeNs - pauseAccumulatedNs
        return (elapsedNs / 1_000_000.0 * activePlaybackRate).toLong().coerceAtLeast(0)
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
        isCountInActive = false
        metronomeJobs.forEach { it.cancel() }
        metronomeJobs.clear()
        for (id in metronomeActiveStreams) {
            metronomeEngine.removeClip(id)
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

        activePreset = null
        activePresetTrack = null
        cancelMetronome()
        isCountInActive = false
        isPaused = false
        pauseAccumulatedNs = 0L
        activePlaybackRate = 1.0f
        mediaController.setPlaybackParameters(PlaybackParameters(1.0f, 1.0f))

        applyQueue(tracks, startIndex, 0L)

        mediaController.prepare()
        mediaController.play()
    }

    /**
     * Replaces the queue with [tracks] and points the controller at [startIndex] / [positionMs].
     * Leaves playback state alone — callers decide whether to prepare, play, or pause.
     */
    private fun applyQueue(tracks: List<TrackEntity>, startIndex: Int, positionMs: Long) {
        val safeIndex = startIndex.coerceIn(0, tracks.lastIndex)

        queueTracks.clear()
        queueTracks.addAll(tracks)

        _uiState.update {
            it.copy(
                currentTrack = tracks[safeIndex],
                error = null,
                queue = tracks.toList(),
                currentQueueIndex = safeIndex,
                positionMs = positionMs,
                isCountInActive = false,
                presetName = null,
                presetSummary = null,
            )
        }

        mediaController.setMediaItems(tracks.map { trackToMediaItem(it) }, safeIndex, positionMs)
    }

    /**
     * Rebuilds the previous session from disk on a cold start, left paused at the saved
     * position so nothing starts playing on its own. Preset sessions are restored as plain
     * playback — a preset's tempo/pitch/count-in is intentionally not reapplied.
     */
    private suspend fun restoreSavedPlayback() {
        if (hasRestoredPlayback) return
        hasRestoredPlayback = true

        if (!::mediaController.isInitialized) return
        // Never clobber a session that is already live (e.g. the service outlived the UI).
        if (mediaController.mediaItemCount != 0 || _uiState.value.currentTrack != null) return

        val saved = playbackStatePreferences.state.first() ?: return
        if (saved.trackIds.isEmpty()) return

        val tracks = trackRepository.getTracksByIds(saved.trackIds)
        if (tracks.isEmpty()) {
            // Every saved track is gone; don't leave a record we can never restore again.
            playbackStatePreferences.clear()
            return
        }

        // Re-anchor on the track that was actually current, in case the queue shrank.
        val startIndex = tracks.indexOfFirst { it.id == saved.currentTrackId }
            .takeIf { it >= 0 }
            ?: saved.currentIndex.coerceIn(0, tracks.lastIndex)

        applyQueue(tracks, startIndex, saved.positionMs)
        mediaController.prepare()
        mediaController.pause()
    }

    /**
     * Snapshots the current queue, track and position to disk. Writes are fire-and-forget on the
     * application scope so they survive `onCleared`. DataStore serialises writes internally, but
     * this keeps at most one of ours in flight; a call arriving mid-write is re-run afterwards so
     * the newest state is never dropped.
     */
    private fun persistPlaybackState() {
        if (!::mediaController.isInitialized) return

        if (!isSaveInFlight.compareAndSet(false, true)) {
            isSavePending = true
            return
        }

        // Snapshot synchronously; the controller may be released before the write lands.
        val trackIds = queueTracks.map { it.id }
        val currentIndex =
            if (trackIds.isEmpty()) -1 else mediaController.currentMediaItemIndex.coerceIn(0, trackIds.lastIndex)
        val currentTrackId = trackIds.getOrNull(currentIndex)
        val positionMs = max(0L, mediaController.currentPosition)

        applicationScope.launch {
            try {
                if (currentTrackId == null) {
                    // Queue emptied (or cleared): nothing left to remember.
                    playbackStatePreferences.clear()
                } else {
                    playbackStatePreferences.save(
                        SavedPlaybackState(
                            trackIds = trackIds,
                            currentIndex = currentIndex,
                            currentTrackId = currentTrackId,
                            positionMs = positionMs,
                        )
                    )
                }
            } finally {
                isSaveInFlight.set(false)
                if (isSavePending) {
                    isSavePending = false
                    persistPlaybackState()
                }
            }
        }
    }

    /** Called when the UI goes to the background, the last reliable chance to record state. */
    fun onAppBackgrounded() {
        persistPlaybackState()
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
        activePreset = null
        activePresetTrack = null
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
                presetName = null,
                presetSummary = null,
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
        val clamped = positionMs.coerceIn(0L, max(0L, mediaController.duration))
        mediaController.seekTo(clamped)
        _uiState.update { it.copy(positionMs = clamped) }

        if (activePreset != null) {
            // Re-sync the metronome to the new position so count-ins stay on beat.
            seekBaseMs = clamped
            cancelMetronome()
            countInJob = viewModelScope.launch {
                awaitReady()
                startCountInScheduling()
            }
        } else {
            cancelMetronome()
        }
    }

    fun skipToPrevious() {
        if (!::mediaController.isInitialized) return
        val preset = activePreset
        val track = activePresetTrack
        if (preset != null && track != null) {
            // Pause the song, then replay the whole preset sequence (count-in first, then track).
            mediaController.pause()
            playWithLayers(track, preset)
        } else {
            mediaController.seekToPrevious()
        }
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

    private fun presetSummary(preset: com.showtimeplayer.data.db.entity.PresetEntity): String {
        val speed = "${(preset.playbackRate * 100).roundToInt()}%"
        val pitch = preset.pitchOffsetSemitones
        val pitchText = when {
            pitch > 0 -> " • +$pitch st"
            pitch < 0 -> " • $pitch st"
            else -> ""
        }
        val tape = if (preset.pitchFollowsSpeed) " • tape" else ""
        return "$speed$pitchText$tape"
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
                    // onCleared() is not reliable when the process is killed, so checkpoint
                    // the position periodically while playing.
                    progressTicks++
                    if (progressTicks >= TICKS_PER_CHECKPOINT) {
                        progressTicks = 0
                        persistPlaybackState()
                    }
                }
                delay(200)
            }
        }
    }

    private inner class PlayerListener : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _uiState.update { it.copy(isPlaying = isPlaying) }
            // Covers both pausing and the track ending. Buffering also reports false, so only
            // record a position the player actually settled on.
            if (!isPlaying && mediaController.playbackState == Player.STATE_READY) {
                persistPlaybackState()
            }
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
            persistPlaybackState()
        }

        override fun onPlayerError(error: PlaybackException) {
            _uiState.update {
                it.copy(error = error.message ?: "Playback error", isPlaying = false)
            }
        }
    }

    override fun onCleared() {
        cancelMetronome()
        // Must snapshot the position before the controller goes away.
        persistPlaybackState()
        listener?.let { mediaController.removeListener(it) }
        mediaController.release()
        super.onCleared()
    }

    private companion object {
        // 200ms progress tick x 25 ~= a checkpoint every 5 seconds while playing.
        const val TICKS_PER_CHECKPOINT = 25
    }

    class Factory(private val application: Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
            return PlayerViewModel(application) as T
        }
    }
}
