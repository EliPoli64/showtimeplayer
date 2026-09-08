package com.showtimeplayer.ui.screens.presets

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.showtimeplayer.data.db.entity.PresetEntity
import com.showtimeplayer.data.db.entity.TrackEntity
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
import kotlin.math.roundToInt

enum class CreationStep {
    TRACK_SELECTION,
    TEMPO,
    TIME_SIGNATURE,
    BEAT_MARKER,
    REVIEW,
}

data class PresetWithTrack(
    val preset: PresetEntity,
    val track: TrackEntity?,
)

data class PresetsUiState(
    val presets: List<PresetWithTrack> = emptyList(),
    val allTracks: List<TrackEntity> = emptyList(),
    val creationStep: CreationStep? = null,
    val selectedTrack: TrackEntity? = null,
    val presetName: String = "",
    val bpm: Int = 0,
    val timeSignatureNum: Int = 4,
    val timeSignatureDenom: Int = 4,
    val beatMarkerMs: Long = 0L,
    val countInBars: Int = 2,
    val isPreviewPlaying: Boolean = false,
    val visibleStartMs: Float = 0f,
    val visibleDurationMs: Float = 10_000f,
    val waveformAmplitudes: List<Float> = emptyList(),
)

class PresetsViewModel(
    private val application: Application,
    private val presetRepository: PresetRepositoryImpl,
    trackRepository: TrackRepositoryImpl,
) : AndroidViewModel(application) {

    private val creationStep = MutableStateFlow<CreationStep?>(null)
    private val selectedTrack = MutableStateFlow<TrackEntity?>(null)
    private val presetName = MutableStateFlow("")
    private val bpm = MutableStateFlow(0)
    private val timeSignatureNum = MutableStateFlow(4)
    private val timeSignatureDenom = MutableStateFlow(4)
    private val beatMarkerMs = MutableStateFlow(0L)
    private val countInBars = MutableStateFlow(2)
    private val isPreviewPlaying = MutableStateFlow(false)
    private val visibleStartMs = MutableStateFlow(0f)
    private val visibleDurationMs = MutableStateFlow(10_000f)
    private val _waveformAmplitudes = MutableStateFlow<List<Float>>(emptyList())

    private var previewPlayer: ExoPlayer? = null
    private val tapTimes = mutableListOf<Long>()
    private var tapResetJob: Job? = null
    private var waveformJob: Job? = null

    val uiState: StateFlow<PresetsUiState> = combine(
        combine(
            presetRepository.observeAll(),
            trackRepository.observeTracks(),
        ) { presets, tracks -> presets to tracks },
        combine(
            creationStep, selectedTrack, presetName, bpm,
        ) { step, track, name, b -> CreationState(step, track, name, b) },
        combine(
            timeSignatureNum, timeSignatureDenom, beatMarkerMs, countInBars,
        ) { num, denom, marker, bars -> BeatState(num, denom, marker, bars) },
        combine(
            isPreviewPlaying, visibleStartMs, visibleDurationMs, _waveformAmplitudes,
        ) { playing, visStart, visDur, waveform -> PreviewState(playing, visStart, visDur, waveform) },
    ) { (presets, tracks), creation, beat, preview ->
        val presetsWithTracks = presets.map { preset ->
            PresetWithTrack(
                preset = preset,
                track = tracks.find { it.id == preset.trackId },
            )
        }
        PresetsUiState(
            presets = presetsWithTracks,
            allTracks = tracks,
            creationStep = creation.step,
            selectedTrack = creation.track,
            presetName = creation.name,
            bpm = creation.bpm,
            timeSignatureNum = beat.timeSigNum,
            timeSignatureDenom = beat.timeSigDenom,
            beatMarkerMs = beat.beatMarker,
            countInBars = beat.countInBars,
            isPreviewPlaying = preview.isPlaying,
            visibleStartMs = preview.visibleStart,
            visibleDurationMs = preview.visibleDuration,
            waveformAmplitudes = preview.waveform,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PresetsUiState())

    private data class CreationState(val step: CreationStep?, val track: TrackEntity?, val name: String, val bpm: Int)
    private data class BeatState(val timeSigNum: Int, val timeSigDenom: Int, val beatMarker: Long, val countInBars: Int)
    private data class PreviewState(val isPlaying: Boolean, val visibleStart: Float, val visibleDuration: Float, val waveform: List<Float>)

    fun startCreation() {
        creationStep.value = CreationStep.TRACK_SELECTION
        selectedTrack.value = null
        presetName.value = ""
        bpm.value = 0
        timeSignatureNum.value = 4
        timeSignatureDenom.value = 4
        beatMarkerMs.value = 0L
        countInBars.value = 2
        visibleStartMs.value = 0f
        stopPreview()
    }

    fun cancelCreation() {
        stopPreview()
        creationStep.value = null
    }

    fun nextStep() {
        val current = creationStep.value ?: return
        when (current) {
            CreationStep.TRACK_SELECTION -> {
                if (selectedTrack.value != null) {
                    creationStep.value = CreationStep.TEMPO
                    if (presetName.value.isBlank()) {
                        presetName.value = selectedTrack.value?.title ?: "Preset"
                    }
                }
            }
            CreationStep.TEMPO -> creationStep.value = CreationStep.TIME_SIGNATURE
            CreationStep.TIME_SIGNATURE -> {
                creationStep.value = CreationStep.BEAT_MARKER
                visibleStartMs.value = 0f
                val dur = selectedTrack.value?.durationMs?.toFloat() ?: 10_000f
                visibleDurationMs.value = dur.coerceAtMost(10_000f)
                beatMarkerMs.value = 0L
                loadWaveform()
            }
            CreationStep.BEAT_MARKER -> {
                stopPreview()
                creationStep.value = CreationStep.REVIEW
            }
            CreationStep.REVIEW -> savePreset()
        }
    }

    fun previousStep() {
        stopPreview()
        val current = creationStep.value ?: return
        when (current) {
            CreationStep.TRACK_SELECTION -> cancelCreation()
            CreationStep.TEMPO -> creationStep.value = CreationStep.TRACK_SELECTION
            CreationStep.TIME_SIGNATURE -> creationStep.value = CreationStep.TEMPO
            CreationStep.BEAT_MARKER -> creationStep.value = CreationStep.TIME_SIGNATURE
            CreationStep.REVIEW -> creationStep.value = CreationStep.BEAT_MARKER
        }
    }

    fun selectTrack(track: TrackEntity) { selectedTrack.value = track }
    fun updatePresetName(name: String) { presetName.value = name }
    fun updateBpm(newBpm: Int) { bpm.value = newBpm.coerceIn(1, 300) }

    fun onTapTempo() {
        val now = System.currentTimeMillis()
        tapResetJob?.cancel()
        if (tapTimes.isNotEmpty() && (now - tapTimes.last()) > 2_000) tapTimes.clear()
        tapTimes.add(now)
        if (tapTimes.size >= 2) {
            val avg = tapTimes.zipWithNext { a, b -> b - a }.average()
            if (avg > 0) bpm.value = (60_000.0 / avg).roundToInt().coerceIn(1, 300)
        }
        tapResetJob = viewModelScope.launch { delay(2_000); tapTimes.clear() }
    }

    fun updateTimeSignature(num: Int, denom: Int) {
        timeSignatureNum.value = num
        timeSignatureDenom.value = denom
    }

    fun updateCenterMs(positionMs: Long) {
        beatMarkerMs.value = positionMs.coerceAtLeast(0)
    }

    fun updateVisibleRange(startMs: Float, durationMs: Float) {
        val totalMs = selectedTrack.value?.durationMs?.toFloat() ?: return
        visibleDurationMs.value = durationMs.coerceIn(1_000f, totalMs + 200f)
        visibleStartMs.value = startMs.coerceIn(-100f, totalMs + 100f - visibleDurationMs.value)
    }

    fun updateCountInBars(bars: Int) { countInBars.value = bars.coerceIn(0, 8) }

    fun playPreview(positionMs: Long) {
        val track = selectedTrack.value ?: return
        stopPreview()
        val player = ExoPlayer.Builder(application).build()
        previewPlayer = player
        player.setMediaItem(MediaItem.Builder().setUri(Uri.parse(track.uri)).build())
        player.prepare()
        player.seekTo(positionMs)
        player.play()
        isPreviewPlaying.value = true
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED || state == Player.STATE_IDLE) stopPreview()
            }
        })
        viewModelScope.launch { delay(4_000); stopPreview() }
    }

    fun stopPreview() {
        previewPlayer?.let { try { it.stop(); it.release() } catch (_: Exception) {} }
        previewPlayer = null
        isPreviewPlaying.value = false
    }

    private fun savePreset() {
        val track = selectedTrack.value ?: return
        val name = presetName.value.ifBlank { track.title ?: "Preset" }
        viewModelScope.launch {
            presetRepository.insert(
                PresetEntity(
                    trackId = track.id, name = name,
                    metronomeBpm = bpm.value,
                    metronomeTimeSignatureNum = timeSignatureNum.value,
                    metronomeTimeSignatureDenom = timeSignatureDenom.value,
                    metronomeCountInBars = countInBars.value,
                    metronomeEnabled = countInBars.value > 0,
                    loopStartMs = if (beatMarkerMs.value > 0) beatMarkerMs.value else null,
                ),
            )
            creationStep.value = null
        }
    }

    fun deletePreset(preset: PresetEntity) {
        viewModelScope.launch { presetRepository.delete(preset) }
    }

    private fun loadWaveform() {
        val track = selectedTrack.value ?: return
        waveformJob?.cancel()
        _waveformAmplitudes.value = emptyList()
        waveformJob = viewModelScope.launch {
            val context = application
            val uri = android.net.Uri.parse(track.uri)
            val waveform = AudioDecoder.decodeFullWaveform(context, uri)
            _waveformAmplitudes.value = waveform.amplitudes
        }
    }

    override fun onCleared() { stopPreview(); super.onCleared() }

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
