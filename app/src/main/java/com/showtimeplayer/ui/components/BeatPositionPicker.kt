package com.showtimeplayer.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.showtimeplayer.util.formatDurationMs

@Composable
fun BeatPositionPicker(
    totalDurationMs: Long,
    visibleStartMs: Float,
    visibleDurationMs: Float,
    onVisibleRangeChanged: (Float, Float) -> Unit,
    centerMs: Long,
    bpm: Int,
    isPlaying: Boolean,
    onPlayPreview: (Long) -> Unit,
    onStopPreview: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        WaveformView(
            totalDurationMs = totalDurationMs,
            visibleStartMs = visibleStartMs,
            visibleDurationMs = visibleDurationMs,
            onVisibleRangeChanged = onVisibleRangeChanged,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Beat 1: ${centerMs.formatDurationMs()}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )

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

        Text(
            text = "Drag to scroll • Pinch to zoom • Red line = beat 1",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
    }
}
