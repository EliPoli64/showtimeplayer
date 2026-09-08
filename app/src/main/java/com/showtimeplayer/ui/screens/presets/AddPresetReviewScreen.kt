package com.showtimeplayer.ui.screens.presets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.showtimeplayer.data.db.entity.TrackEntity
import com.showtimeplayer.util.formatDurationMs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddPresetReviewScreen(
    selectedTrack: TrackEntity?,
    presetName: String,
    onPresetNameChanged: (String) -> Unit,
    bpm: Int,
    timeSigNum: Int,
    timeSigDenom: Int,
    beatMarkerMs: Long,
    countInBars: Int,
    onCountInBarsChanged: (Int) -> Unit,
    onBack: () -> Unit,
    onSave: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Review Preset") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
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
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Preset name
            OutlinedTextField(
                value = presetName,
                onValueChange = onPresetNameChanged,
                label = { Text("Preset name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Summary card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = "Preset Summary",
                        style = MaterialTheme.typography.titleMedium,
                    )

                    SummaryRow("Track", selectedTrack?.title ?: "")

                    SummaryRow(
                        "Tempo",
                        if (bpm > 0) "$bpm BPM" else "Not set",
                    )

                    SummaryRow(
                        "Time Signature",
                        "$timeSigNum/$timeSigDenom",
                    )

                    SummaryRow(
                        "Beat 1 Position",
                        if (beatMarkerMs > 0) beatMarkerMs.formatDurationMs() else "Start of track",
                    )

                    SummaryRow(
                        "Count-in",
                        if (countInBars > 0) {
                            "$countInBars bar${if (countInBars != 1) "s" else ""}"
                        } else {
                            "None"
                        },
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Count-in bars
            Text(
                text = "Count-in bars: $countInBars",
                style = MaterialTheme.typography.labelLarge,
            )
            Text(
                text = if (countInBars > 0) {
                    "${countInBars} metronome bar${if (countInBars != 1) "s" else ""} before playback"
                } else {
                    "No count-in"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Slider(
                value = countInBars.toFloat(),
                onValueChange = { onCountInBarsChanged(it.toInt()) },
                valueRange = 0f..8f,
                steps = 7,
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                ),
            )

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = onSave,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Save Preset")
            }
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
