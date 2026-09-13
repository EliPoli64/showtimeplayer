package com.showtimeplayer.ui.screens.presets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun SongSettingsBadge(
    settingsFlow: StateFlow<SongSettingsState>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val settings by settingsFlow.collectAsStateWithLifecycle()
    TextButton(onClick = onClick, modifier = modifier) {
        Icon(
            imageVector = Icons.Filled.Tune,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(settingsSummary(settings))
    }
}

private fun settingsSummary(settings: SongSettingsState): String {
    val speed = "${(settings.playbackRate * 100).roundToInt()}%"
    val pitch = settings.pitchOffsetSemitones
    val pitchText = when {
        pitch > 0 -> " • +$pitch st"
        pitch < 0 -> " • $pitch st"
        else -> ""
    }
    val tape = if (settings.pitchFollowsSpeed) " • tape" else ""
    return "$speed$pitchText$tape"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongSettingsSheet(
    settingsFlow: StateFlow<SongSettingsState>,
    onSpeedChanged: (Float) -> Unit,
    onPitchChanged: (Int) -> Unit,
    onPitchFollowsSpeedChanged: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    val settings by settingsFlow.collectAsStateWithLifecycle()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Song Settings",
                style = MaterialTheme.typography.titleMedium,
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Speed
            Text(
                text = "Speed: ${(settings.playbackRate * 100).roundToInt()}%",
                style = MaterialTheme.typography.labelLarge,
            )
            Slider(
                value = settings.playbackRate,
                onValueChange = onSpeedChanged,
                valueRange = MIN_PLAYBACK_RATE..MAX_PLAYBACK_RATE,
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                ),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "${(MIN_PLAYBACK_RATE * 100).roundToInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "${(MAX_PLAYBACK_RATE * 100).roundToInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Pitch
            val pitch = settings.pitchOffsetSemitones
            Text(
                text = "Pitch: ${if (pitch > 0) "+" else ""}$pitch st",
                style = MaterialTheme.typography.labelLarge,
            )
            Slider(
                value = pitch.toFloat(),
                onValueChange = { onPitchChanged(it.roundToInt()) },
                valueRange = -MAX_PITCH_SEMITONES.toFloat()..MAX_PITCH_SEMITONES.toFloat(),
                steps = MAX_PITCH_SEMITONES * 2 - 1,
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                ),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "-$MAX_PITCH_SEMITONES st",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "+$MAX_PITCH_SEMITONES st",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Pitch follows speed (tape)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Pitch follows speed",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = if (settings.pitchFollowsSpeed) {
                            "Tape mode — pitch rises and falls with speed"
                        } else {
                            "Time-stretch — pitch stays constant"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = settings.pitchFollowsSpeed,
                    onCheckedChange = onPitchFollowsSpeedChanged,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            val isDefault = settings.playbackRate == 1.0f &&
                abs(settings.pitchOffsetSemitones) == 0 &&
                !settings.pitchFollowsSpeed
            TextButton(
                onClick = {
                    onSpeedChanged(1.0f)
                    onPitchChanged(0)
                    onPitchFollowsSpeedChanged(false)
                },
                enabled = !isDefault,
            ) {
                Text("Reset")
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
