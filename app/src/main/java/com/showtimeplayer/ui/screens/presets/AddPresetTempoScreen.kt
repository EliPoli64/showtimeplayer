package com.showtimeplayer.ui.screens.presets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
fun AddPresetTempoScreen(
    selectedTrack: TrackEntity?,
    bpm: Int,
    onBpmChanged: (Int) -> Unit,
    onTapTempo: () -> Unit,
    onBack: () -> Unit,
    onNext: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Set Tempo") },
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
            verticalArrangement = Arrangement.Center,
        ) {
            // Track info
            Text(
                text = selectedTrack?.title ?: "",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            Text(
                text = selectedTrack?.artist ?: "",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(32.dp))

            // BPM display
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

            // Tap tempo button
            Button(
                onClick = onTapTempo,
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .height(80.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                ),
            ) {
                Text(
                    text = "Tap Tempo",
                    style = MaterialTheme.typography.headlineSmall,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Tap the button to the beat of the song",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Skip / Next
            OutlinedButton(
                onClick = {
                    onBpmChanged(0)
                    onNext()
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Skip (no tempo)")
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = onNext,
                enabled = bpm > 0,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Next")
            }
        }
    }
}
