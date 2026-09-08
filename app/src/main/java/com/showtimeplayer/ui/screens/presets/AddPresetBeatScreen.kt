package com.showtimeplayer.ui.screens.presets

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.showtimeplayer.data.db.entity.TrackEntity
import com.showtimeplayer.ui.components.BeatWheel
import com.showtimeplayer.util.formatDurationMs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddPresetBeatScreen(
    selectedTrack: TrackEntity?,
    bpm: Int,
    timeSignatureNum: Int,
    centerMs: Long,
    onCenterMsChanged: (Long) -> Unit,
    totalDurationMs: Long,
    waveformAmplitudes: List<Float>,
    isPlaying: Boolean,
    onPlayPreview: (Long) -> Unit,
    onStopPreview: () -> Unit,
    onBack: () -> Unit,
    onNext: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Beat 1 Position") },
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
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = selectedTrack?.title ?: "",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            if (bpm > 0) {
                Text(
                    text = "$bpm BPM",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Scroll the wheel to place beat 1 under the red marker.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(16.dp))

            BeatWheel(
                currentPositionMs = centerMs,
                durationMs = totalDurationMs,
                waveformAmplitudes = waveformAmplitudes,
                bpm = bpm,
                timeSignatureNum = timeSignatureNum,
                onSeek = onCenterMsChanged,
                onPositionChange = onCenterMsChanged,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Beat 1: ${centerMs.formatDurationMs()}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.weight(1f))
                IconButton(
                    onClick = {
                        if (isPlaying) onStopPreview() else onPlayPreview(centerMs)
                    },
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (isPlaying) "Stop" else "Preview",
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = onNext,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Next")
            }
        }
    }
}
