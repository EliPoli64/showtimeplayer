package com.showtimeplayer.ui.screens.presets

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.showtimeplayer.data.db.entity.TrackEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditPresetTempoScreen(
    selectedTrack: TrackEntity?,
    bpm: Int,
    timeSigNum: Int,
    timeSigDenom: Int,
    countInBars: Int,
    onBpmChanged: (Int) -> Unit,
    onTimeSigChanged: (num: Int, denom: Int) -> Unit,
    onCountInBarsChanged: (Int) -> Unit,
    onBack: () -> Unit,
    onNext: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit Tempo") },
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
            Text(
                text = selectedTrack?.title ?: "",
                style = MaterialTheme.typography.titleMedium,
            )

            Spacer(modifier = Modifier.height(24.dp))

            // BPM
            Text(
                text = "Tempo",
                style = MaterialTheme.typography.labelLarge,
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = if (bpm > 0) bpm.toString() else "",
                onValueChange = { value ->
                    value.filter { it.isDigit() }.toIntOrNull()?.let { onBpmChanged(it) }
                },
                label = { Text("BPM") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(0.5f),
                textStyle = MaterialTheme.typography.headlineLarge.copy(textAlign = TextAlign.Center),
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Time signature
            Text(
                text = "Time Signature",
                style = MaterialTheme.typography.labelLarge,
            )
            Spacer(modifier = Modifier.height(8.dp))

            val timeSigs = listOf(2 to 4, 3 to 4, 4 to 4, 5 to 4, 6 to 8, 7 to 8)
            val currentIdx = timeSigs.indexOfFirst { it.first == timeSigNum && it.second == timeSigDenom }
            Slider(
                value = currentIdx.toFloat().coerceAtLeast(0f),
                onValueChange = { idx ->
                    val (num, denom) = timeSigs[idx.toInt().coerceIn(0, timeSigs.lastIndex)]
                    onTimeSigChanged(num, denom)
                },
                valueRange = 0f..(timeSigs.lastIndex).toFloat(),
                steps = timeSigs.size - 2,
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                ),
            )
            Text(
                text = "$timeSigNum/$timeSigDenom",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Count-in bars
            Text(
                text = "Count-in: $countInBars bar${if (countInBars != 1) "s" else ""}",
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
                onClick = onNext,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Done")
            }
        }
    }
}
