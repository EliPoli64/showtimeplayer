package com.showtimeplayer.ui.screens.presets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.showtimeplayer.data.db.entity.MetronomeRegion
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegionEditSheet(
    region: MetronomeRegion,
    onDismiss: () -> Unit,
    onBpmChanged: (Int) -> Unit,
    onTimeSigChanged: (Int, Int) -> Unit,
    onCountInBarsChanged: (Int) -> Unit,
    onTapTempo: () -> Unit,
    onDelete: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()
    var bpmText by remember(region.id) { mutableIntStateOf(region.bpm) }
    var timeSigNum by remember(region.id) { mutableIntStateOf(region.timeSignatureNum) }
    var timeSigDenom by remember(region.id) { mutableIntStateOf(region.timeSignatureDenom) }
    var countInBars by remember(region.id) { mutableIntStateOf(region.countInBars) }

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
                text = "Edit Region",
                style = MaterialTheme.typography.titleMedium,
            )

            Spacer(modifier = Modifier.height(20.dp))

            // BPM
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = bpmText.toString(),
                    onValueChange = { value ->
                        value.filter { it.isDigit() }.toIntOrNull()?.let {
                            val clamped = it.coerceIn(1, 300)
                            bpmText = clamped
                            onBpmChanged(clamped)
                        }
                    },
                    label = { Text("BPM") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    textStyle = MaterialTheme.typography.headlineSmall.copy(textAlign = TextAlign.Center),
                )

                IconButton(onClick = {
                    onTapTempo()
                }) {
                    Icon(
                        imageVector = Icons.Filled.AccessTime,
                        contentDescription = "Tap tempo",
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Time signature
            Text(
                text = "Time Signature",
                style = MaterialTheme.typography.labelLarge,
            )
            Spacer(modifier = Modifier.height(4.dp))

            val timeSigs = listOf(2 to 4, 3 to 4, 4 to 4, 5 to 4, 6 to 8, 7 to 8)
            val currentIdx = timeSigs.indexOfFirst { it.first == timeSigNum && it.second == timeSigDenom }
            Slider(
                value = currentIdx.toFloat().coerceAtLeast(0f),
                onValueChange = { idx ->
                    val (num, denom) = timeSigs[idx.toInt().coerceIn(0, timeSigs.lastIndex)]
                    timeSigNum = num
                    timeSigDenom = denom
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

            Spacer(modifier = Modifier.height(16.dp))

            // Count-in bars
            Text(
                text = "Count-in: $countInBars bar${if (countInBars != 1) "s" else ""}",
                style = MaterialTheme.typography.labelLarge,
            )
            Slider(
                value = countInBars.toFloat(),
                onValueChange = {
                    countInBars = it.toInt()
                    onCountInBarsChanged(it.toInt())
                },
                valueRange = 0f..8f,
                steps = 7,
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                ),
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Delete
            TextButton(
                onClick = {
                    scope.launch {
                        sheetState.hide()
                        onDismiss()
                        onDelete()
                    }
                },
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
            ) {
                Text("Delete Region")
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
