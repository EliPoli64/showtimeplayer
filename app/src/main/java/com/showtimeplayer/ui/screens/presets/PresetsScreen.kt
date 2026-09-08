package com.showtimeplayer.ui.screens.presets

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.showtimeplayer.data.db.entity.PresetEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PresetsScreen(
    viewModel: PresetsViewModel,
    onPresetPlay: (PresetEntity, com.showtimeplayer.data.db.entity.TrackEntity) -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    AnimatedContent(
        targetState = uiState.creationStep,
        transitionSpec = {
            if (targetState != null && initialState == null) {
                slideInHorizontally { it } togetherWith slideOutHorizontally { -it }
            } else if (targetState == null && initialState != null) {
                slideInHorizontally { -it } togetherWith slideOutHorizontally { it }
            } else {
                slideInHorizontally { it } togetherWith slideOutHorizontally { -it }
            }
        },
        label = "preset_creation",
    ) { step ->
        when (step) {
            null -> PresetsListContent(
                uiState = uiState,
                onStartCreation = viewModel::startCreation,
                onPresetPlay = onPresetPlay,
                onDeletePreset = viewModel::deletePreset,
            )
            CreationStep.TRACK_SELECTION -> AddPresetTrackScreen(
                tracks = uiState.allTracks,
                selectedTrack = uiState.selectedTrack,
                onTrackSelected = viewModel::selectTrack,
                onBack = viewModel::previousStep,
                onNext = viewModel::nextStep,
            )
            CreationStep.TEMPO -> AddPresetTempoScreen(
                selectedTrack = uiState.selectedTrack,
                bpm = uiState.bpm,
                onBpmChanged = viewModel::updateBpm,
                onTapTempo = viewModel::onTapTempo,
                onBack = viewModel::previousStep,
                onNext = viewModel::nextStep,
            )
            CreationStep.TIME_SIGNATURE -> AddPresetTimeSignatureScreen(
                selectedTrack = uiState.selectedTrack,
                currentTimeSigNum = uiState.timeSignatureNum,
                currentTimeSigDenom = uiState.timeSignatureDenom,
                onTimeSignatureChanged = viewModel::updateTimeSignature,
                onBack = viewModel::previousStep,
                onNext = viewModel::nextStep,
            )
            CreationStep.BEAT_MARKER -> AddPresetBeatScreen(
                selectedTrack = uiState.selectedTrack,
                bpm = uiState.bpm,
                timeSignatureNum = uiState.timeSignatureNum,
                centerMs = uiState.beatMarkerMs,
                onCenterMsChanged = viewModel::updateCenterMs,
                totalDurationMs = uiState.selectedTrack?.durationMs ?: 0L,
                waveformAmplitudes = uiState.waveformAmplitudes,
                isPlaying = uiState.isPreviewPlaying,
                onPlayPreview = viewModel::playPreview,
                onStopPreview = viewModel::stopPreview,
                onBack = viewModel::previousStep,
                onNext = viewModel::nextStep,
            )
            CreationStep.REVIEW -> AddPresetReviewScreen(
                selectedTrack = uiState.selectedTrack,
                presetName = uiState.presetName,
                onPresetNameChanged = viewModel::updatePresetName,
                bpm = uiState.bpm,
                timeSigNum = uiState.timeSignatureNum,
                timeSigDenom = uiState.timeSignatureDenom,
                beatMarkerMs = uiState.beatMarkerMs,
                countInBars = uiState.countInBars,
                onCountInBarsChanged = viewModel::updateCountInBars,
                onBack = viewModel::previousStep,
                onSave = viewModel::nextStep,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PresetsListContent(
    uiState: PresetsUiState,
    onStartCreation: () -> Unit,
    onPresetPlay: (PresetEntity, com.showtimeplayer.data.db.entity.TrackEntity) -> Unit,
    onDeletePreset: (PresetEntity) -> Unit,
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("Presets") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = onStartCreation) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "Add preset",
                )
            }
        },
    ) { padding ->
        if (uiState.presets.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "No presets yet",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Tap + to create one",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(items = uiState.presets, key = { it.preset.id }) { presetWithTrack ->
                    val preset = presetWithTrack.preset
                    val track = presetWithTrack.track

                    ListItem(
                        headlineContent = { Text(preset.name) },
                        supportingContent = {
                            val trackName = track?.title ?: "Unknown track"
                            val tempoText = if (preset.metronomeBpm > 0) " • ${preset.metronomeBpm} BPM" else ""
                            val countInText = if (preset.metronomeCountInBars > 0) {
                                " • Count-in: ${preset.metronomeCountInBars}"
                            } else {
                                ""
                            }
                            Text("$trackName$tempoText$countInText")
                        },
                        leadingContent = {
                            Icon(
                                imageVector = Icons.Filled.MusicNote,
                                contentDescription = null,
                                modifier = Modifier.size(40.dp),
                            )
                        },
                        trailingContent = {
                            IconButton(onClick = { onDeletePreset(preset) }) {
                                Icon(
                                    imageVector = Icons.Filled.Delete,
                                    contentDescription = "Delete preset",
                                )
                            }
                        },
                        modifier = Modifier.clickable(enabled = track != null) {
                            if (track != null) onPresetPlay(preset, track)
                        },
                    )
                }
            }
        }
    }
}
