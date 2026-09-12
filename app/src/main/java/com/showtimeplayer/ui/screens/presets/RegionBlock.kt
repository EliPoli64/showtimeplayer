package com.showtimeplayer.ui.screens.presets

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.showtimeplayer.data.db.entity.MetronomeRegion

@Composable
fun RegionBlock(
    region: MetronomeRegion,
    colorIndex: Int,
    trackDurationMs: Long,
    isSelected: Boolean,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val color = Color(LAYER_COLORS[colorIndex])
    val selectedColor = MaterialTheme.colorScheme.primary

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(32.dp)
            .clickable { onTap() },
    ) {
        val w = size.width
        val h = size.height
        val barH = h * 0.6f
        val barTop = (h - barH) / 2f

        if (trackDurationMs > 0) {
            val startFraction = (region.startMs.toFloat() / trackDurationMs).coerceIn(0f, 1f)
            val endFraction = if (region.endMs != null) {
                (region.endMs.toFloat() / trackDurationMs).coerceIn(0f, 1f)
            } else {
                1f
            }

            val barLeft = startFraction * w
            val barRight = endFraction * w
            val barWidth = (barRight - barLeft).coerceAtLeast(8.dp.toPx())

            val blockColor = if (isSelected) selectedColor else color
            val alpha = 0.8f

            drawRoundRect(
                color = blockColor.copy(alpha = alpha),
                topLeft = Offset(barLeft, barTop),
                size = Size(barWidth, barH),
                cornerRadius = CornerRadius(4.dp.toPx()),
            )

            // BPM label area - small text background
            if (barWidth > 40.dp.toPx()) {
                drawRoundRect(
                    color = blockColor.copy(alpha = 0.3f),
                    topLeft = Offset(barLeft + 4.dp.toPx(), barTop + 2.dp.toPx()),
                    size = Size(barWidth - 8.dp.toPx(), 14.dp.toPx()),
                    cornerRadius = CornerRadius(2.dp.toPx()),
                )
            }
        }
    }
}
