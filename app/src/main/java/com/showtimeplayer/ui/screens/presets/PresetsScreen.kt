package com.showtimeplayer.ui.screens.presets

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.showtimeplayer.data.db.entity.PresetEntity
import com.showtimeplayer.data.db.entity.TrackEntity

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
    var trackSearchQuery by remember { mutableStateOf("") }
    var showLayerManager by remember { mutableStateOf(false) }
    var showSongSettings by remember { mutableStateOf(false) }
    val targetLayerId = uiState.layers.firstOrNull { it.layer.enabled }?.layer?.id
        ?: uiState.layers.firstOrNull()?.layer?.id

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (uiState.editingPreset != null) "Edit Metronome" else "Select Track") },
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
        if (uiState.editingPreset == null) {
            // Track picker
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
            ) {
                OutlinedTextField(
                    value = trackSearchQuery,
                    onValueChange = { trackSearchQuery = it },
                    label = { Text("Search tracks") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = null,
                        )
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(8.dp))

                val filteredTracks = if (trackSearchQuery.isBlank()) {
                    uiState.allTracks
                } else {
                    uiState.allTracks.filter {
                        it.title?.contains(trackSearchQuery, ignoreCase = true) == true ||
                            it.artist?.contains(trackSearchQuery, ignoreCase = true) == true
                    }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(items = filteredTracks, key = { it.id }) { track ->
                        ListItem(
                            headlineContent = { Text(track.title ?: "Unknown") },
                            supportingContent = { Text(track.artist ?: "") },
                            leadingContent = {
                                Icon(
                                    imageVector = Icons.Filled.MusicNote,
                                    contentDescription = null,
                                    modifier = Modifier.size(32.dp),
                                )
                            },
                            modifier = Modifier.clickable {
                                viewModel.selectTrackForCreation(track)
                            },
                        )
                    }
                }
            }
        } else {
            // DAW editor
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                // Transport bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    IconButton(onClick = viewModel::toggleSongPlayback) {
                        Icon(
                            imageVector = if (uiState.isSongPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (uiState.isSongPlaying) "Pause" else "Play",
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        OutlinedTextField(
                            value = uiState.presetName,
                            onValueChange = viewModel::updatePresetName,
                            label = { Text("Preset name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    SongSettingsBadge(
                        settingsFlow = viewModel.songSettingsState,
                        onClick = { showSongSettings = true },
                    )
                }

                // DAW Timeline
                DawTimeline(
                    waveformAmplitudes = uiState.waveformAmplitudes,
                    layers = uiState.layers,
                    trackDurationMs = uiState.selectedTrack?.durationMs ?: 0L,
                    songOffsetMs = uiState.songOffsetMs,
                    playbackPositionMs = uiState.playbackPositionMs,
                    isPlaying = uiState.isSongPlaying,
                    selectedRegionId = uiState.selectedRegion?.id,
                    layerColors = LAYER_COLORS,
                    onRegionTap = { region -> viewModel.selectRegion(region) },
                    onRegionMove = { regionId, newStartMs, newLayerId ->
                        viewModel.moveRegion(regionId, newStartMs, newLayerId)
                    },
                    onAddRegionAtPosition = { layerId, positionMs ->
                        viewModel.addRegionAtPosition(layerId, positionMs)
                    },
                    onSongMove = viewModel::moveSong,
                    onSeek = viewModel::seekTo,
                    onScrubStart = viewModel::onScrubStart,
                    onScrub = viewModel::onScrub,
                    onScrubEnd = viewModel::onScrubEnd,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 16.dp),
                )

                // Layer controls
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = { showLayerManager = true }) {
                        Icon(
                            imageVector = Icons.Filled.Tune,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Text("Layers (${uiState.layers.size}/4)")
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (targetLayerId != null) {
                            TextButton(onClick = {
                                viewModel.addRegionAtPosition(targetLayerId, uiState.playbackPositionMs)
                            }) {
                                Icon(
                                    imageVector = Icons.Filled.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                                Text("Count-in")
                            }
                        }
                    }
                }

                // Save button
                TextButton(
                    onClick = viewModel::savePreset,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                ) {
                    Text("Save")
                }
            }
        }
    }

    uiState.selectedRegion?.let { region ->
        RegionEditSheet(
            region = region,
            onDismiss = viewModel::clearSelectedRegion,
            onBpmChanged = { newBpm -> viewModel.updateRegion(region.copy(bpm = newBpm)) },
            onTimeSigChanged = { num, denom ->
                viewModel.updateRegion(region.copy(timeSignatureNum = num, timeSignatureDenom = denom))
            },
            onCountInBarsChanged = { bars -> viewModel.updateRegion(region.copy(countInBars = bars)) },
            onVolumeChanged = { vol -> viewModel.updateRegion(region.copy(volume = vol)) },
            onTapTempo = { viewModel.onTapTempo(region) },
            onReset = { viewModel.resetRegion(region.id) },
            onDelete = { viewModel.removeRegion(region.id) },
        )
    }

    if (showLayerManager) {
        LayerManagerDialog(
            uiState = uiState,
            onDismiss = { showLayerManager = false },
            onAddLayer = viewModel::addLayer,
            onToggleLayer = viewModel::toggleLayerEnabled,
            onRemoveLayer = viewModel::removeLayer,
        )
    }

    if (showSongSettings) {
        SongSettingsSheet(
            settingsFlow = viewModel.songSettingsState,
            onSpeedChanged = viewModel::setSongSpeed,
            onPitchChanged = viewModel::setSongPitch,
            onPitchFollowsSpeedChanged = viewModel::setPitchFollowsSpeed,
            onDismiss = { showSongSettings = false },
        )
    }
}

@Composable
private fun LayerManagerDialog(
    uiState: PresetsUiState,
    onDismiss: () -> Unit,
    onAddLayer: () -> Unit,
    onToggleLayer: (Long) -> Unit,
    onRemoveLayer: (Long) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Metronome Layers") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (uiState.layers.isEmpty()) {
                    Text(
                        text = "No layers yet. Add one to place count-ins.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                uiState.layers.forEachIndexed { index, layerWithRegions ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .clip(CircleShape)
                                .background(
                                    Color(
                                        if (index < LAYER_COLORS.size) LAYER_COLORS[index]
                                        else 0xFF888888,
                                    ),
                                ),
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = layerWithRegions.layer.name,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                text = "${layerWithRegions.regions.size} count-in(s)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = layerWithRegions.layer.enabled,
                            onCheckedChange = { onToggleLayer(layerWithRegions.layer.id) },
                        )
                        IconButton(onClick = { onRemoveLayer(layerWithRegions.layer.id) }) {
                            Icon(
                                imageVector = Icons.Filled.Delete,
                                contentDescription = "Remove layer",
                            )
                        }
                    }
                }
                if (uiState.canAddLayer) {
                    TextButton(onClick = onAddLayer) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Text("Add Layer")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        },
    )
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
