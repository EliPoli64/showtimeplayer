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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.showtimeplayer.data.db.entity.MetronomeRegion
import com.showtimeplayer.data.db.entity.PresetEntity
import com.showtimeplayer.data.db.entity.TrackEntity
import com.showtimeplayer.data.repository.MetronomeLayerWithRegions

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PresetsScreen(
    viewModel: PresetsViewModel,
    onPresetPlay: (PresetEntity, TrackEntity) -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    AnimatedContent(
        targetState = uiState.isEditing,
        transitionSpec = {
            if (targetState) {
                slideInHorizontally { it } togetherWith slideOutHorizontally { -it }
            } else {
                slideInHorizontally { -it } togetherWith slideOutHorizontally { it }
            }
        },
        label = "preset_mode",
    ) { editing ->
        if (editing) {
            DawEditor(
                uiState = uiState,
                viewModel = viewModel,
            )
        } else {
            PresetsListContent(
                uiState = uiState,
                onStartCreation = viewModel::startCreation,
                onPresetPlay = onPresetPlay,
                onEditPreset = viewModel::startEditing,
                onDeletePreset = viewModel::deletePreset,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DawEditor(
    uiState: PresetsUiState,
    viewModel: PresetsViewModel,
) {
    var regionToEdit by remember { mutableStateOf<MetronomeRegion?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit Metronome") },
                navigationIcon = {
                    IconButton(onClick = viewModel::cancelEditing) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Close editor",
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            // Preset name
            OutlinedTextField(
                value = uiState.presetName,
                onValueChange = viewModel::updatePresetName,
                label = { Text("Preset name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Track name
            Text(
                text = uiState.selectedTrack?.title ?: "",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Layer stack
            MetronomeLayerStack(
                layers = uiState.layers,
                selectedRegion = uiState.selectedRegion,
                trackDurationMs = uiState.selectedTrack?.durationMs ?: 0L,
                onRegionTap = { region ->
                    regionToEdit = region
                    viewModel.selectRegion(region)
                },
                onAddRegion = viewModel::addRegion,
                onAddLayer = viewModel::addLayer,
                onRemoveLayer = viewModel::removeLayer,
                onToggleLayer = viewModel::toggleLayerEnabled,
                canAddLayer = uiState.canAddLayer,
            )

            Spacer(modifier = Modifier.weight(1f))

            // Save button
            TextButton(
                onClick = viewModel::savePreset,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Save")
            }
        }
    }

    regionToEdit?.let { region ->
        RegionEditSheet(
            region = region,
            onDismiss = {
                regionToEdit = null
                viewModel.clearSelectedRegion()
            },
            onBpmChanged = { newBpm -> viewModel.updateRegion(region.copy(bpm = newBpm)) },
            onTimeSigChanged = { num, denom ->
                viewModel.updateRegion(region.copy(timeSignatureNum = num, timeSignatureDenom = denom))
            },
            onCountInBarsChanged = { bars -> viewModel.updateRegion(region.copy(countInBars = bars)) },
            onTapTempo = { viewModel.onTapTempo(region) },
            onDelete = {
                viewModel.removeRegion(region.id)
                regionToEdit = null
            },
        )
    }
}

@Composable
private fun MetronomeLayerStack(
    layers: List<MetronomeLayerWithRegions>,
    selectedRegion: MetronomeRegion?,
    trackDurationMs: Long,
    onRegionTap: (MetronomeRegion) -> Unit,
    onAddRegion: (Long) -> Unit,
    onAddLayer: () -> Unit,
    onRemoveLayer: (Long) -> Unit,
    onToggleLayer: (Long) -> Unit,
    canAddLayer: Boolean,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        layers.forEachIndexed { index, layerWithRegions ->
            MetronomeTrackRow(
                layerWithRegions = layerWithRegions,
                colorIndex = index,
                selectedRegionId = selectedRegion?.id,
                trackDurationMs = trackDurationMs,
                onRegionTap = onRegionTap,
                onAddRegion = { onAddRegion(layerWithRegions.layer.id) },
                onRemove = { onRemoveLayer(layerWithRegions.layer.id) },
                onToggleEnabled = { onToggleLayer(layerWithRegions.layer.id) },
            )
        }

        if (canAddLayer) {
            TextButton(
                onClick = onAddLayer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Text(" Add Layer")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PresetsListContent(
    uiState: PresetsUiState,
    onStartCreation: () -> Unit,
    onPresetPlay: (PresetEntity, TrackEntity) -> Unit,
    onEditPreset: (PresetEntity) -> Unit,
    onDeletePreset: (PresetEntity) -> Unit,
) {
    var presetToDelete by remember { mutableStateOf<PresetEntity?>(null) }

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
                            Text(trackName)
                        },
                        leadingContent = {
                            Icon(
                                imageVector = Icons.Filled.MusicNote,
                                contentDescription = null,
                                modifier = Modifier.size(40.dp),
                            )
                        },
                        trailingContent = {
                            Row {
                                IconButton(onClick = { onEditPreset(preset) }) {
                                    Icon(
                                        imageVector = Icons.Filled.Edit,
                                        contentDescription = "Edit preset",
                                    )
                                }
                                IconButton(onClick = { presetToDelete = preset }) {
                                    Icon(
                                        imageVector = Icons.Filled.Delete,
                                        contentDescription = "Delete preset",
                                    )
                                }
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

    presetToDelete?.let { preset ->
        AlertDialog(
            onDismissRequest = { presetToDelete = null },
            title = { Text("Delete preset") },
            text = { Text("Delete \"${preset.name}\"? This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    onDeletePreset(preset)
                    presetToDelete = null
                }) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { presetToDelete = null }) {
                    Text("Cancel")
                }
            },
        )
    }
}
