package com.showtimeplayer.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun WaveformView(
    totalDurationMs: Long,
    visibleStartMs: Float,
    visibleDurationMs: Float,
    onVisibleRangeChanged: (startMs: Float, durationMs: Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (totalDurationMs <= 0) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(80.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
        return
    }

    val padding = 100f
    val totalWithPadding = totalDurationMs.toFloat() + padding * 2
    val startWithPadding = visibleStartMs + padding

    // The slider value represents the visible window's center position
    val sliderValue = startWithPadding + visibleDurationMs / 2

    Box(modifier = modifier) {
        // Decorative bar background
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp),
        ) {
            val barColor = Color.Gray.copy(alpha = 0.3f)
            val barCount = (size.width / 4).toInt()
            for (i in 0 until barCount) {
                val x = i * 4f
                val h = (0.3f + 0.7f * ((i * 7 + 3) % 10) / 10f) * size.height * 0.7f
                drawRect(barColor, Offset(x, (size.height - h) / 2), Size(2f, h))
            }
        }

        // Slider on top
        Slider(
            value = sliderValue,
            onValueChange = { newValue ->
                val newStart = newValue - visibleDurationMs / 2 - padding
                onVisibleRangeChanged(newStart, visibleDurationMs)
            },
            valueRange = visibleDurationMs / 2..totalWithPadding - visibleDurationMs / 2,
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp),
            colors = SliderDefaults.colors(
                thumbColor = Color.Red,
                activeTrackColor = Color.Transparent,
                inactiveTrackColor = Color.Transparent,
            ),
        )
    }
}
