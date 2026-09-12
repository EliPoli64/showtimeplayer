package com.showtimeplayer.ui.screens.presets

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
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
import kotlin.math.roundToInt

val LAYER_COLORS = listOf(
    0xFF4CAF50,
    0xFF2196F3,
    0xFFFF9800,
    0xFFE91E63,
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
    val visibleStartMs: Float = 0f,
    val visibleDurationMs: Float = 10_000f,
    val waveformAmplitudes: List<Float> = emptyList(),
) {
    val activeLayerCount: Int get() = layers.count { it.layer.enabled }
    val canAddLayer: Boolean get() = layers.size < 4
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

    private var previewPlayer: ExoPlayer? = null
    private var songPlayer: ExoPlayer? = null
    private val tapTimes = mutableListOf<Long>()
    private var tapResetJob: Job? = null
    private var waveformJob: Job? = null
    private var layersObservingJob: Job? = null

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
        isSongPlaying,
    ) { (presets, tracks), edit, layerState, preview, songPlaying ->
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
            isSongPlaying = songPlaying,
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

    fun startCreation() {
        isEditing.value = true
        editingPreset.value = null
        selectedTrack.value = null
        presetName.value = ""
        layers.value = emptyList()
        selectedRegion.value = null
        visibleStartMs.value = 0f
        stopPreview()
        stopSongPlayback()
    }

    fun selectTrackForCreation(track: TrackEntity) {
        selectedTrack.value = track
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
        }
    }

    fun startEditing(preset: PresetEntity) {
        isEditing.value = true
        editingPreset.value = preset
        presetName.value = preset.name
        selectedRegion.value = null
        visibleStartMs.value = 0f
        releasePreviewPlayer()
        viewModelScope.launch {
            val track = trackRepository.getTrack(preset.trackId)
            selectedTrack.value = track
            observeLayers(preset.id)
            loadWaveform()
            initPreviewPlayer()
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
        viewModelScope.launch {
            presetRepository.insert(
                preset.copy(name = name, updatedAt = System.currentTimeMillis()),
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
        val track = selectedTrack.value ?: return
        val existingRegions = layers.value.find { it.layer.id == layerId }?.regions ?: emptyList()
        val startMs = if (existingRegions.isNotEmpty()) {
            existingRegions.maxOf { it.endMs ?: it.startMs }
        } else {
            0L
        }
        viewModelScope.launch {
            presetRepository.insertRegion(
                MetronomeRegion(
                    layerId = layerId,
                    startMs = startMs,
                    endMs = null,
                    bpm = 120,
                    timeSignatureNum = 4,
                    timeSignatureDenom = 4,
                    countInBars = 2,
                    sortOrder = existingRegions.size,
                ),
            )
        }
    }

    fun updateRegion(region: MetronomeRegion) {
        viewModelScope.launch { presetRepository.updateRegion(region) }
    }

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
        viewModelScope.launch {
            presetRepository.insert(preset.copy(loopStartMs = positionMs.coerceAtLeast(0)))
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
        if (isSongPlaying.value) {
            stopSongPlayback()
        } else {
            startSongPlayback()
        }
    }

    private fun startSongPlayback() {
        val track = selectedTrack.value ?: return
        stopSongPlayback()
        val player = ExoPlayer.Builder(application).build()
        songPlayer = player
        player.setMediaItem(MediaItem.Builder().setUri(Uri.parse(track.uri)).build())
        player.repeatMode = Player.REPEAT_MODE_ALL
        player.prepare()
        player.play()
        isSongPlaying.value = true
    }

    fun stopSongPlayback() {
        songPlayer?.let { try { it.stop(); it.release() } catch (_: Exception) {} }
        songPlayer = null
        isSongPlaying.value = false
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
