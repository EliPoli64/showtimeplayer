package com.showtimeplayer.ui.screens.presets

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.showtimeplayer.data.db.entity.MetronomeLayer
import com.showtimeplayer.data.db.entity.MetronomeRegion
import com.showtimeplayer.data.db.entity.PresetEntity
import com.showtimeplayer.data.db.entity.TrackEntity
import com.showtimeplayer.data.repository.MetronomeLayerWithRegions
import com.showtimeplayer.data.repository.PresetRepositoryImpl
import com.showtimeplayer.data.repository.TrackRepositoryImpl
import com.showtimeplayer.util.AudioDecoder
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt

// Purple-family palette drawn from Material 3's tonal scheme (deep violet → light
// violet → plum → slate) so layer rows and count-in blocks harmonize with the app theme.
val LAYER_COLORS = listOf(
    0xFF4F378B,
    0xFF7F67BE,
    0xFF8E4585,
    0xFF625B71,
)

const val MIN_PLAYBACK_RATE = 0.25f
const val MAX_PLAYBACK_RATE = 2.0f
const val MAX_PITCH_SEMITONES = 12

const val DEFAULT_BPM = 120
const val DEFAULT_TIME_SIG_NUM = 4
const val DEFAULT_TIME_SIG_DENOM = 4
const val DEFAULT_COUNT_IN_BARS = 2
const val DEFAULT_VOLUME = 1.0f
const val MAX_VOLUME = 2.0f

// Upper bound for output-latency click compensation; guards against absurd stream readings.
const val MAX_CLICK_LEAD_MS = 500L

data class SongSettingsState(
    val playbackRate: Float = 1.0f,
    val pitchOffsetSemitones: Int = 0,
    val pitchFollowsSpeed: Boolean = false,
)

data class PresetWithTrack(
    val preset: PresetEntity,
    val track: TrackEntity?,
)

data class PresetsUiState(
    val presets: List<PresetWithTrack> = emptyList(),
    val allTracks: List<TrackEntity> = emptyList(),
    val isEditing: Boolean = false,
    val editingPreset: PresetEntity? = null,
    val selectedTrack: TrackEntity? = null,
    val presetName: String = "",
    val layers: List<MetronomeLayerWithRegions> = emptyList(),
    val selectedRegion: MetronomeRegion? = null,
    val isPreviewPlaying: Boolean = false,
    val isSongPlaying: Boolean = false,
    val playbackPositionMs: Long = 0L,
    val songOffsetMs: Long = 0L,
    val visibleStartMs: Float = 0f,
    val visibleDurationMs: Float = 10_000f,
    val waveformAmplitudes: List<Float> = emptyList(),
) {
    val activeLayerCount: Int get() = layers.count { it.layer.enabled }
    val canAddLayer: Boolean get() = layers.size < 4
    val timelineDurationMs: Long get() = (selectedTrack?.durationMs ?: 0L) + songOffsetMs
}

class PresetsViewModel(
    private val application: Application,
    private val presetRepository: PresetRepositoryImpl,
    private val trackRepository: TrackRepositoryImpl,
) : AndroidViewModel(application) {

    private val isEditing = MutableStateFlow(false)
    private val editingPreset = MutableStateFlow<PresetEntity?>(null)
    private val selectedTrack = MutableStateFlow<TrackEntity?>(null)
    private val presetName = MutableStateFlow("")
    private val layers = MutableStateFlow<List<MetronomeLayerWithRegions>>(emptyList())
    private val selectedRegion = MutableStateFlow<MetronomeRegion?>(null)
    private val isPreviewPlaying = MutableStateFlow(false)
    private val visibleStartMs = MutableStateFlow(0f)
    private val visibleDurationMs = MutableStateFlow(10_000f)
    private val _waveformAmplitudes = MutableStateFlow<List<Float>>(emptyList())
    private val isSongPlaying = MutableStateFlow(false)
    private val playbackPositionMs = MutableStateFlow(0L)
    private val songOffsetMs = MutableStateFlow(0L)

    private val playbackRate = MutableStateFlow(1.0f)
    private val pitchOffsetSemitones = MutableStateFlow(0)
    private val pitchFollowsSpeed = MutableStateFlow(false)

    private var previewPlayer: ExoPlayer? = null
    private var songPlayer: ExoPlayer? = null
    private val tapTimes = mutableListOf<Long>()
    private var tapResetJob: Job? = null
    private var waveformJob: Job? = null
    private var layersObservingJob: Job? = null
    private var wasPlayingBeforeScrub = false
    private var persistSettingsJob: Job? = null

    private val metronomeEngine =
        (application as com.showtimeplayer.app.PracticeApplication).metronomeEngine
    private var metronomeInitialized = false
    private val metronomeJobs = mutableListOf<Job>()
    private val activeStreamIds = mutableSetOf<Int>()
    private var playbackClockJob: Job? = null
    // Sub-millisecond accumulator so the clock doesn't drift when scaled by the rate.
    private var clockPositionMs = 0.0

    val songSettingsState: StateFlow<SongSettingsState> = combine(
        playbackRate, pitchOffsetSemitones, pitchFollowsSpeed,
    ) { rate, pitch, follows ->
        SongSettingsState(rate, pitch, follows)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SongSettingsState())

    val uiState: StateFlow<PresetsUiState> = combine(
        combine(
            presetRepository.observeAll(),
            trackRepository.observeTracks(),
        ) { presets, tracks -> presets to tracks },
        combine(
            isEditing, editingPreset, selectedTrack, presetName,
        ) { editing, preset, track, name -> EditState(editing, preset, track, name) },
        combine(
            layers, selectedRegion,
        ) { l, r -> LayersState(l, r) },
        combine(
            isPreviewPlaying, visibleStartMs, visibleDurationMs, _waveformAmplitudes,
        ) { playing, visStart, visDur, waveform -> PreviewState(playing, visStart, visDur, waveform) },
        combine(isSongPlaying, playbackPositionMs, songOffsetMs) { playing, pos, offset ->
            SongState(playing, pos, offset)
        },
    ) { (presets, tracks), edit, layerState, preview, songState ->
        val presetsWithTracks = presets.map { preset ->
            PresetWithTrack(
                preset = preset,
                track = tracks.find { it.id == preset.trackId },
            )
        }
        PresetsUiState(
            presets = presetsWithTracks,
            allTracks = tracks,
            isEditing = edit.isEditing,
            editingPreset = edit.preset,
            selectedTrack = edit.track,
            presetName = edit.name,
            layers = layerState.layers,
            selectedRegion = layerState.selectedRegion,
            isPreviewPlaying = preview.isPlaying,
            isSongPlaying = songState.playing,
            playbackPositionMs = songState.position,
            songOffsetMs = songState.offset,
            visibleStartMs = preview.visibleStart,
            visibleDurationMs = preview.visibleDuration,
            waveformAmplitudes = preview.waveform,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PresetsUiState())

    private data class EditState(
        val isEditing: Boolean,
        val preset: PresetEntity?,
        val track: TrackEntity?,
        val name: String,
    )

    private data class LayersState(
        val layers: List<MetronomeLayerWithRegions>,
        val selectedRegion: MetronomeRegion?,
    )

    private data class PreviewState(
        val isPlaying: Boolean,
        val visibleStart: Float,
        val visibleDuration: Float,
        val waveform: List<Float>,
    )

    private data class SongState(
        val playing: Boolean,
        val position: Long,
        val offset: Long,
    )

    fun startCreation() {
        isEditing.value = true
        editingPreset.value = null
        selectedTrack.value = null
        presetName.value = ""
        layers.value = emptyList()
        selectedRegion.value = null
        visibleStartMs.value = 0f
        songOffsetMs.value = 0L
        playbackRate.value = 1.0f
        pitchOffsetSemitones.value = 0
        pitchFollowsSpeed.value = false
        stopPreview()
        stopSongPlayback()
    }

    fun selectTrackForCreation(track: TrackEntity) {
        selectedTrack.value = track
        songOffsetMs.value = 0L
        playbackRate.value = 1.0f
        pitchOffsetSemitones.value = 0
        pitchFollowsSpeed.value = false
        if (presetName.value.isBlank()) {
            presetName.value = track.title ?: "Preset"
        }
        viewModelScope.launch {
            val presetId = presetRepository.insert(
                PresetEntity(trackId = track.id, name = track.title ?: "Preset"),
            )
            val preset = presetRepository.getPreset(presetId)
            editingPreset.value = preset
            observeLayers(presetId)
            loadWaveform()
            initSongPlayer()
        }
    }

    fun startEditing(preset: PresetEntity) {
        isEditing.value = true
        editingPreset.value = preset
        presetName.value = preset.name
        selectedRegion.value = null
        visibleStartMs.value = 0f
        songOffsetMs.value = preset.songOffsetMs
        playbackRate.value = preset.playbackRate.coerceIn(MIN_PLAYBACK_RATE, MAX_PLAYBACK_RATE)
        pitchOffsetSemitones.value = preset.pitchOffsetSemitones.coerceIn(-MAX_PITCH_SEMITONES, MAX_PITCH_SEMITONES)
        pitchFollowsSpeed.value = preset.pitchFollowsSpeed
        releasePreviewPlayer()
        viewModelScope.launch {
            val track = trackRepository.getTrack(preset.trackId)
            selectedTrack.value = track
            observeLayers(preset.id)
            loadWaveform()
            initPreviewPlayer()
            initSongPlayer()
        }
    }

    private fun observeLayers(presetId: Long) {
        layersObservingJob?.cancel()
        layersObservingJob = viewModelScope.launch {
            presetRepository.observePresetLayers(presetId).collect { layerList ->
                val withRegions = layerList.map { layer ->
                    MetronomeLayerWithRegions(
                        layer = layer,
                        regions = emptyList(),
                    )
                }
                layers.value = withRegions
                // Observe regions for each layer
                for (layer in layerList) {
                    launch {
                        presetRepository.observeLayerRegions(layer.id).collect { regions ->
                            layers.value = layers.value.map {
                                if (it.layer.id == layer.id) {
                                    it.copy(regions = regions)
                                } else {
                                    it
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    fun cancelEditing() {
        releasePreviewPlayer()
        stopSongPlayback()
        layersObservingJob?.cancel()
        isEditing.value = false
        editingPreset.value = null
        selectedTrack.value = null
        selectedRegion.value = null
        layers.value = emptyList()
    }

    fun updatePresetName(name: String) {
        presetName.value = name
    }

    fun savePreset() {
        val preset = editingPreset.value ?: return
        val name = presetName.value.ifBlank { selectedTrack.value?.title ?: "Preset" }
        persistSettingsJob?.cancel()
        viewModelScope.launch {
            presetRepository.update(
                preset.copy(
                    name = name,
                    songOffsetMs = songOffsetMs.value,
                    playbackRate = playbackRate.value,
                    pitchOffsetSemitones = pitchOffsetSemitones.value,
                    pitchFollowsSpeed = pitchFollowsSpeed.value,
                    updatedAt = System.currentTimeMillis(),
                ),
            )
            isEditing.value = false
            editingPreset.value = null
            selectedTrack.value = null
            selectedRegion.value = null
            layers.value = emptyList()
        }
    }

    fun deletePreset(preset: PresetEntity) {
        viewModelScope.launch { presetRepository.delete(preset) }
    }

    // -- Layer operations --

    fun addLayer() {
        val preset = editingPreset.value ?: return
        val currentLayerCount = layers.value.size
        if (currentLayerCount >= 4) return
        viewModelScope.launch {
            presetRepository.insertLayer(
                MetronomeLayer(
                    presetId = preset.id,
                    name = "Layer ${currentLayerCount + 1}",
                    sortOrder = currentLayerCount,
                ),
            )
        }
    }

    fun removeLayer(layerId: Long) {
        viewModelScope.launch { presetRepository.deleteLayerById(layerId) }
    }

    fun toggleLayerEnabled(layerId: Long) {
        val layer = layers.value.find { it.layer.id == layerId }?.layer ?: return
        viewModelScope.launch {
            presetRepository.updateLayer(layer.copy(enabled = !layer.enabled))
        }
    }

    // -- Region operations --

    fun addRegion(layerId: Long) {
        val existingRegions = layers.value.find { it.layer.id == layerId }?.regions ?: emptyList()
        val startMs = existingRegions.maxOfOrNull { it.endMs ?: it.startMs } ?: playbackPositionMs.value
        addRegionAtPosition(layerId, startMs)
    }

    fun addRegionAtPosition(layerId: Long, positionMs: Long) {
        val timelineDuration = timelineDurationMs()
        val existingRegions = layers.value.find { it.layer.id == layerId }?.regions ?: emptyList()
        val defaultCountInDurationMs =
            DEFAULT_COUNT_IN_BARS * (60_000L / DEFAULT_BPM) * DEFAULT_TIME_SIG_NUM
        // Place the downbeat at the tapped position, but keep the whole count-in on the timeline.
        val startMs = maxOf(positionMs, defaultCountInDurationMs).coerceIn(0L, timelineDuration)
        viewModelScope.launch {
            val region = MetronomeRegion(
                layerId = layerId,
                startMs = startMs,
                endMs = null,
                bpm = DEFAULT_BPM,
                timeSignatureNum = DEFAULT_TIME_SIG_NUM,
                timeSignatureDenom = DEFAULT_TIME_SIG_DENOM,
                countInBars = DEFAULT_COUNT_IN_BARS,
                volume = DEFAULT_VOLUME,
                sortOrder = existingRegions.size,
            )
            val id = presetRepository.insertRegion(region)
            selectedRegion.value = region.copy(id = id)
        }
    }

    fun resetRegion(regionId: Long) {
        val region = layers.value.flatMap { it.regions }.find { it.id == regionId } ?: return
        updateRegion(
            region.copy(
                bpm = DEFAULT_BPM,
                timeSignatureNum = DEFAULT_TIME_SIG_NUM,
                timeSignatureDenom = DEFAULT_TIME_SIG_DENOM,
                countInBars = DEFAULT_COUNT_IN_BARS,
                volume = DEFAULT_VOLUME,
            ),
        )
    }

    fun updateRegion(region: MetronomeRegion) {
        viewModelScope.launch {
            presetRepository.updateRegion(region)
            if (selectedRegion.value?.id == region.id) {
                selectedRegion.value = region
            }
        }
        // A clip is pre-rendered from the region's settings, so a live edit re-renders it.
        scheduleClipReRender()
    }

    private var clipReRenderJob: Job? = null
    private fun scheduleClipReRender() {
        if (!isSongPlaying.value) return
        clipReRenderJob?.cancel()
        clipReRenderJob = viewModelScope.launch {
            delay(150)
            scheduleMetronomeStreams()
        }
    }

    fun moveRegion(regionId: Long, newStartMs: Long, newLayerId: Long?) {
        val region = layers.value.flatMap { it.regions }.find { it.id == regionId } ?: return
        val timelineDuration = timelineDurationMs()
        val minStart = countInDurationMs(region).coerceAtMost(timelineDuration)
        val clampedStart = newStartMs.coerceIn(minStart, timelineDuration)
        val deltaMs = clampedStart - region.startMs
        val newEnd = region.endMs?.let { (it + deltaMs).coerceIn(0L, timelineDuration) }
        val updated = region.copy(
            startMs = clampedStart,
            endMs = newEnd,
            layerId = newLayerId ?: region.layerId,
        )
        viewModelScope.launch {
            presetRepository.updateRegion(updated)
            if (selectedRegion.value?.id == regionId) {
                selectedRegion.value = updated
            }
        }
    }

    private fun countInDurationMs(region: MetronomeRegion): Long {
        if (region.bpm <= 0 || region.countInBars <= 0 || region.timeSignatureNum <= 0) return 0L
        return region.countInBars * (60_000L / region.bpm) * region.timeSignatureNum
    }

    fun moveSong(deltaMs: Long) {
        val trackDuration = selectedTrack.value?.durationMs ?: 0L
        val newOffset = (songOffsetMs.value + deltaMs).coerceIn(0L, trackDuration)
        songOffsetMs.value = newOffset
        val preset = editingPreset.value
        if (preset != null) {
            val updated = preset.copy(
                songOffsetMs = newOffset,
                updatedAt = System.currentTimeMillis(),
            )
            editingPreset.value = updated
            viewModelScope.launch {
                presetRepository.update(updated)
            }
        }
    }

    private fun timelineDurationMs(): Long =
        (selectedTrack.value?.durationMs ?: 0L) + songOffsetMs.value

    fun removeRegion(regionId: Long) {
        viewModelScope.launch {
            presetRepository.deleteRegionById(regionId)
            if (selectedRegion.value?.id == regionId) {
                selectedRegion.value = null
            }
        }
    }

    fun selectRegion(region: MetronomeRegion) {
        selectedRegion.value = region
    }

    fun clearSelectedRegion() {
        selectedRegion.value = null
    }

    // -- Preview & waveform --

    fun updateCenterMs(positionMs: Long) {
        val preset = editingPreset.value ?: return
        val updated = preset.copy(
            loopStartMs = positionMs.coerceAtLeast(0),
            songOffsetMs = songOffsetMs.value,
        )
        editingPreset.value = updated
        viewModelScope.launch {
            presetRepository.update(updated)
        }
    }

    fun updateVisibleRange(startMs: Float, durationMs: Float) {
        val totalMs = selectedTrack.value?.durationMs?.toFloat() ?: return
        visibleDurationMs.value = durationMs.coerceIn(1_000f, totalMs + 200f)
        visibleStartMs.value = startMs.coerceIn(-100f, totalMs + 100f - visibleDurationMs.value)
    }

    fun playPreview(positionMs: Long) {
        val player = previewPlayer ?: return
        player.seekTo(positionMs)
        player.play()
        isPreviewPlaying.value = true
    }

    fun stopPreview() {
        previewPlayer?.let { try { it.pause(); it.seekTo(0) } catch (_: Exception) {} }
        isPreviewPlaying.value = false
    }

    private fun initPreviewPlayer() {
        stopPreview()
        val track = selectedTrack.value ?: return
        val player = ExoPlayer.Builder(application).build()
        previewPlayer = player
        player.setMediaItem(MediaItem.Builder().setUri(Uri.parse(track.uri)).build())
        player.prepare()
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED || state == Player.STATE_IDLE) {
                    isPreviewPlaying.value = false
                }
            }
        })
    }

    private fun releasePreviewPlayer() {
        previewPlayer?.let { try { it.stop(); it.release() } catch (_: Exception) {} }
        previewPlayer = null
        isPreviewPlaying.value = false
    }

    fun toggleSongPlayback() {
        if (isSongPlaying.value) pauseSongPlayback() else startSongPlayback()
    }

    private fun startSongPlayback() {
        if (songPlayer == null) initSongPlayer()
        val player = songPlayer ?: return
        val timelineDuration = timelineDurationMs()
        if (timelineDuration <= 0) return

        if (playbackPositionMs.value >= timelineDuration) {
            setTimelinePosition(0L)
        }
        isSongPlaying.value = true
        applySongParameters()
        player.pause()
        startPlaybackClock()
        startEditorMetronome()
    }

    private fun pauseSongPlayback() {
        isSongPlaying.value = false
        playbackClockJob?.cancel()
        playbackClockJob = null
        songPlayer?.pause()
        // Keep the native stream warm while the editor is open so resuming (and the next
        // count-in, especially over Bluetooth) starts smoothly. It is stopped on editor exit.
        clearEditorStreams()
    }

    private fun initSongPlayer() {
        releaseSongPlayer()
        val track = selectedTrack.value ?: return
        val player = ExoPlayer.Builder(application).build()
        songPlayer = player
        player.setMediaItem(MediaItem.Builder().setUri(Uri.parse(track.uri)).build())
        player.repeatMode = Player.REPEAT_MODE_OFF
        player.playbackParameters = currentPlaybackParameters()
        player.prepare()

        // Pre-open the metronome stream so it is already routed and warmed up by the time the
        // first click plays (Bluetooth streams take noticeably longer to spin up).
        if (!metronomeInitialized) {
            metronomeInitialized = metronomeEngine.initialize()
        }
        metronomeEngine.start()
    }

    private fun startPlaybackClock() {
        playbackClockJob?.cancel()
        playbackClockJob = viewModelScope.launch {
            var lastNs = System.nanoTime()
            while (isSongPlaying.value) {
                val now = System.nanoTime()
                clockPositionMs += (now - lastNs) / 1_000_000.0 * playbackRate.value
                lastNs = now
                val timelineDuration = timelineDurationMs()
                if (timelineDuration > 0 && clockPositionMs >= timelineDuration) {
                    setTimelinePosition(0L)
                    syncPlayerToTimeline(0L)
                    scheduleMetronomeStreams()
                } else {
                    playbackPositionMs.value = clockPositionMs.toLong().coerceAtLeast(0L)
                    syncPlayerToTimeline(playbackPositionMs.value)
                }
                delay(16)
            }
        }
    }

    private fun syncPlayerToTimeline(timelineMs: Long) {
        val player = songPlayer ?: return
        val audioMs = timelineMs - songOffsetMs.value
        if (audioMs < 0) {
            if (player.isPlaying) player.pause()
            return
        }
        if (!player.isPlaying) {
            player.seekTo(audioMs)
            player.play()
        } else if (abs(player.currentPosition - audioMs) > 250) {
            player.seekTo(audioMs)
        }
    }

    private fun setTimelinePosition(positionMs: Long) {
        playbackPositionMs.value = positionMs
        clockPositionMs = positionMs.toDouble()
    }

    fun stopSongPlayback() {
        releaseSongPlayer()
    }

    private fun releaseSongPlayer() {
        isSongPlaying.value = false
        playbackClockJob?.cancel()
        playbackClockJob = null
        stopEditorMetronome()
        songPlayer?.let { try { it.stop(); it.release() } catch (_: Exception) {} }
        songPlayer = null
        setTimelinePosition(0L)
    }

    fun onScrubStart() {
        wasPlayingBeforeScrub = isSongPlaying.value
        if (isSongPlaying.value) {
            isSongPlaying.value = false
            playbackClockJob?.cancel()
            playbackClockJob = null
            songPlayer?.pause()
            clearEditorStreams()
        }
    }

    fun onScrub(positionMs: Long) {
        val timelineDuration = timelineDurationMs()
        val pos = positionMs.coerceIn(0L, timelineDuration)
        setTimelinePosition(pos)
        songPlayer?.seekTo((pos - songOffsetMs.value).coerceAtLeast(0L))
    }

    fun seekTo(positionMs: Long) {
        val timelineDuration = timelineDurationMs()
        val pos = positionMs.coerceIn(0L, timelineDuration)
        setTimelinePosition(pos)
        songPlayer?.seekTo((pos - songOffsetMs.value).coerceAtLeast(0L))
        if (isSongPlaying.value) {
            scheduleMetronomeStreams()
        }
    }

    // -- Song settings (pitch / speed) --

    fun setSongSpeed(rate: Float) {
        val clamped = rate.coerceIn(MIN_PLAYBACK_RATE, MAX_PLAYBACK_RATE)
        if (playbackRate.value == clamped) return
        playbackRate.value = clamped
        applySongParameters()
        // Clips are pre-rendered with the rate baked in, so re-render them at the new speed.
        scheduleClipReRender()
        scheduleSongSettingsPersist()
    }

    fun setSongPitch(semitones: Int) {
        val clamped = semitones.coerceIn(-MAX_PITCH_SEMITONES, MAX_PITCH_SEMITONES)
        if (pitchOffsetSemitones.value == clamped) return
        pitchOffsetSemitones.value = clamped
        applySongParameters()
        scheduleSongSettingsPersist()
    }

    fun setPitchFollowsSpeed(enabled: Boolean) {
        if (pitchFollowsSpeed.value == enabled) return
        pitchFollowsSpeed.value = enabled
        applySongParameters()
        scheduleSongSettingsPersist()
    }

    private fun pitchFactor(): Float {
        val semitoneFactor = 2.0.pow(pitchOffsetSemitones.value / 12.0).toFloat()
        return if (pitchFollowsSpeed.value) semitoneFactor * playbackRate.value else semitoneFactor
    }

    private fun currentPlaybackParameters(): PlaybackParameters =
        PlaybackParameters(playbackRate.value, pitchFactor())

    private fun applySongParameters() {
        val player = songPlayer ?: return
        val params = currentPlaybackParameters()
        if (player.playbackParameters != params) {
            player.playbackParameters = params
        }
    }

    private fun scheduleSongSettingsPersist() {
        persistSettingsJob?.cancel()
        persistSettingsJob = viewModelScope.launch {
            delay(400)
            val preset = editingPreset.value ?: return@launch
            presetRepository.update(
                preset.copy(
                    playbackRate = playbackRate.value,
                    pitchOffsetSemitones = pitchOffsetSemitones.value,
                    pitchFollowsSpeed = pitchFollowsSpeed.value,
                    updatedAt = System.currentTimeMillis(),
                ),
            )
        }
    }

    fun onScrubEnd() {
        if (wasPlayingBeforeScrub) {
            wasPlayingBeforeScrub = false
            startSongPlayback()
        }
        wasPlayingBeforeScrub = false
    }

    // -- Editor metronome preview --

    private fun startEditorMetronome() {
        if (!metronomeInitialized) {
            metronomeInitialized = metronomeEngine.initialize()
        }
        metronomeEngine.start()
        scheduleMetronomeStreams()
    }

    private fun stopEditorMetronome() {
        clearEditorStreams()
        metronomeEngine.stop()
    }

    // Cancels scheduled count-ins and removes active clicks, but leaves the native output
    // stream running so it stays warm (important for smooth Bluetooth playback).
    private fun clearEditorStreams() {
        metronomeJobs.forEach { it.cancel() }
        metronomeJobs.clear()
        for (id in activeStreamIds.toList()) {
            metronomeEngine.removeClip(id)
        }
        activeStreamIds.clear()
    }

    // Pre-renders every enabled count-in region into a PCM clip and schedules playback via the
    // engine's sample counter. Nothing is synthesized on the audio thread at runtime.
    private fun scheduleMetronomeStreams() {
        metronomeJobs.forEach { it.cancel() }
        metronomeJobs.clear()
        for (id in activeStreamIds.toList()) {
            metronomeEngine.removeClip(id)
        }
        activeStreamIds.clear()

        val sr = metronomeEngine.sampleRate().takeIf { it > 0 } ?: 44100
        val rate = playbackRate.value
        val sampleBase = metronomeEngine.sampleCount()
        val timelineBase = playbackPositionMs.value
        val leadMs = metronomeEngine.latencyMs().toLong().coerceIn(0L, MAX_CLICK_LEAD_MS)

        val enabledLayers = layers.value.filter { it.layer.enabled }
        for (layer in enabledLayers) {
            for (region in layer.regions) {
                if (region.bpm <= 0 || region.countInBars <= 0 || region.timeSignatureNum <= 0) continue
                val beatMs = 60_000.0 / region.bpm
                val countInDurationMs = region.countInBars * beatMs * region.timeSignatureNum
                val startTimeline = region.startMs - countInDurationMs.toLong() - leadMs
                // Engine sample counter for a future media-time position, at current rate.
                val futureWallMs = (startTimeline - timelineBase) / rate
                val startSample = sampleBase + (futureWallMs * sr / 1000.0).toLong()

                val clip = metronomeEngine.renderCountInClip(
                    sampleRate = sr,
                    playbackRate = rate,
                    bpm = region.bpm,
                    timeSigNum = region.timeSignatureNum,
                    countInBars = region.countInBars,
                    volume = region.volume,
                )
                if (clip.isNotEmpty()) {
                    val id = region.id.toInt()
                    metronomeEngine.addClip(id, clip, startSample, 1.0f)
                    activeStreamIds.add(id)
                }
            }
        }
    }

    fun onTapTempo(region: MetronomeRegion) {
        val now = System.currentTimeMillis()
        tapResetJob?.cancel()
        if (tapTimes.isNotEmpty() && (now - tapTimes.last()) > 2_000) tapTimes.clear()
        tapTimes.add(now)
        if (tapTimes.size >= 2) {
            val avg = tapTimes.zipWithNext { a, b -> b - a }.average()
            if (avg > 0) {
                val newBpm = (60_000.0 / avg).roundToInt().coerceIn(1, 300)
                updateRegion(region.copy(bpm = newBpm))
            }
        }
        tapResetJob = viewModelScope.launch { delay(2_000); tapTimes.clear() }
    }

    private fun loadWaveform() {
        val track = selectedTrack.value ?: return
        waveformJob?.cancel()
        _waveformAmplitudes.value = emptyList()
        waveformJob = viewModelScope.launch {
            val context = application
            val uri = Uri.parse(track.uri)
            val waveform = AudioDecoder.decodeFullWaveform(context, uri)
            _waveformAmplitudes.value = waveform.amplitudes
        }
    }

    override fun onCleared() {
        releasePreviewPlayer()
        stopSongPlayback()
        layersObservingJob?.cancel()
        playbackClockJob?.cancel()
        persistSettingsJob?.cancel()
        clipReRenderJob?.cancel()
        metronomeJobs.forEach { it.cancel() }
        super.onCleared()
    }

    class Factory(
        private val application: Application,
        private val presetRepository: PresetRepositoryImpl,
        private val trackRepository: TrackRepositoryImpl,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
            return PresetsViewModel(application, presetRepository, trackRepository) as T
        }
    }
}
