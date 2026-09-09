package com.showtimeplayer.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.min
import kotlin.math.sqrt

private const val MS_PER_PIXEL_DEFAULT = 40f
private const val MS_PER_PIXEL_MIN = 5f
private const val MS_PER_PIXEL_MAX = 200f

@Composable
fun BeatWheel(
    currentPositionMs: Long,
    durationMs: Long,
    waveformAmplitudes: List<Float>,
    onSeek: (Long) -> Unit,
    onPositionChange: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val errorColor = MaterialTheme.colorScheme.error

    var displayPositionMs by remember { mutableLongStateOf(currentPositionMs) }
    var isDragging by remember { mutableStateOf(false) }
    var msPerPixel by remember { mutableFloatStateOf(MS_PER_PIXEL_DEFAULT) }

    if (!isDragging && currentPositionMs != displayPositionMs) {
        displayPositionMs = currentPositionMs
    }

    val barCount = 160

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(220.dp)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    var mode = 0
                    var startDragX = 0f
                    var startDragPosMs = 0L
                    var pinchStartDist = 0f
                    var pinchStartMsPerPixel = 0f

                    while (true) {
                        val event = awaitPointerEvent()
                        val pointers = event.changes.filter { it.pressed }

                        when {
                            pointers.size >= 2 && mode != 2 -> {
                                mode = 2
                                val p0 = pointers[0].position
                                val p1 = pointers[1].position
                                pinchStartDist = sqrt((p0.x - p1.x) * (p0.x - p1.x) + (p0.y - p1.y) * (p0.y - p1.y))
                                pinchStartMsPerPixel = msPerPixel
                                event.changes.forEach { it.consume() }
                            }
                            pointers.size == 1 && mode == 0 && event.type == PointerEventType.Press -> {
                                mode = 1
                                isDragging = true
                                startDragX = pointers[0].position.x
                                startDragPosMs = displayPositionMs
                                event.changes.forEach { it.consume() }
                            }
                            pointers.size >= 2 && mode == 2 && event.type == PointerEventType.Move -> {
                                val p0 = pointers[0].position
                                val p1 = pointers[1].position
                                val dist = sqrt((p0.x - p1.x) * (p0.x - p1.x) + (p0.y - p1.y) * (p0.y - p1.y))
                                if (pinchStartDist > 0f) {
                                    val ratio = pinchStartDist / dist
                                    msPerPixel = (pinchStartMsPerPixel * ratio)
                                        .coerceIn(MS_PER_PIXEL_MIN, MS_PER_PIXEL_MAX)
                                }
                                event.changes.forEach { it.consume() }
                            }
                            pointers.size == 1 && mode == 1 && event.type == PointerEventType.Move -> {
                                if (durationMs > 0) {
                                    val dx = pointers[0].position.x - startDragX
                                    val timeDelta = (dx * msPerPixel).toLong()
                                    val newPos = (startDragPosMs + timeDelta)
                                        .coerceIn(0L, durationMs)
                                    displayPositionMs = newPos
                                    onPositionChange(newPos)
                                }
                                event.changes.forEach { it.consume() }
                            }
                            pointers.isEmpty() && mode != 0 -> {
                                if (mode == 1) {
                                    isDragging = false
                                    onSeek(displayPositionMs)
                                }
                                mode = 0
                            }
                        }
                    }
                }
            },
    ) {
        val w = size.width
        val h = size.height
        val centerY = h / 2f
        val stripHeight = h * 0.7f
        val stripTop = centerY - stripHeight / 2f
        val stripBottom = centerY + stripHeight / 2f
        val barWidth = (w / barCount) * 0.65f
        val barGap = (w / barCount) * 0.35f
        val halfBar = stripHeight * 0.5f

        val posMs = displayPositionMs.toFloat()
        val windowDurationMs = w * msPerPixel
        val windowStart = posMs - windowDurationMs / 2f
        val windowEnd = posMs + windowDurationMs / 2f

        // ── Strip background ──
        drawRoundRect(
            color = surfaceVariant.copy(alpha = 0.35f),
            topLeft = Offset(0f, stripTop),
            size = Size(w, stripHeight),
            cornerRadius = CornerRadius(10.dp.toPx()),
        )

        // ── Center line ──
        drawLine(
            color = onSurfaceVariant.copy(alpha = 0.2f),
            start = Offset(0f, centerY),
            end = Offset(w, centerY),
            strokeWidth = 0.5.dp.toPx(),
        )

        // ── Mirrored waveform bars ──
        if (waveformAmplitudes.isNotEmpty() && durationMs > 0) {
            val totalBars = waveformAmplitudes.size
            for (i in 0 until barCount) {
                val barCenterMs = windowStart + (i.toFloat() / barCount) * windowDurationMs
                if (barCenterMs < 0 || barCenterMs > durationMs) continue

                val ampIdx = ((barCenterMs / durationMs.toFloat()) * totalBars).toInt()
                    .coerceIn(0, totalBars - 1)
                val amplitude = waveformAmplitudes[ampIdx]

                val barH = (amplitude * halfBar).coerceAtLeast(2.dp.toPx())
                val x = i * (barWidth + barGap) + barGap / 2f

                val alpha = (0.4f + 0.6f * amplitude).coerceIn(0f, 1f)
                // Top half
                drawRoundRect(
                    color = primaryColor.copy(alpha = alpha),
                    topLeft = Offset(x, centerY - barH),
                    size = Size(barWidth, barH),
                    cornerRadius = CornerRadius(barWidth / 2f),
                )
                // Bottom half (mirror)
                drawRoundRect(
                    color = primaryColor.copy(alpha = alpha * 0.7f),
                    topLeft = Offset(x, centerY),
                    size = Size(barWidth, barH),
                    cornerRadius = CornerRadius(barWidth / 2f),
                )
            }
        } else {
            // Placeholder
            for (i in 0 until barCount) {
                val ph = stripHeight * 0.08f
                val x = i * (barWidth + barGap) + barGap / 2f
                drawRoundRect(
                    color = onSurfaceVariant.copy(alpha = 0.1f),
                    topLeft = Offset(x, centerY - ph),
                    size = Size(barWidth, ph),
                    cornerRadius = CornerRadius(barWidth / 2f),
                )
                drawRoundRect(
                    color = onSurfaceVariant.copy(alpha = 0.07f),
                    topLeft = Offset(x, centerY),
                    size = Size(barWidth, ph),
                    cornerRadius = CornerRadius(barWidth / 2f),
                )
            }
        }

        // ── Center playhead (red) ──
        val cx = w / 2f

        // Line through strip
        drawLine(
            color = errorColor,
            start = Offset(cx, stripTop - 10.dp.toPx()),
            end = Offset(cx, stripBottom + 10.dp.toPx()),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round,
        )

        // Top triangle
        val triTop = stripTop - 10.dp.toPx()
        val triH = 8.dp.toPx()
        val triW = 5.dp.toPx()
        drawLine(color = errorColor, start = Offset(cx, triTop), end = Offset(cx - triW, triTop - triH), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
        drawLine(color = errorColor, start = Offset(cx, triTop), end = Offset(cx + triW, triTop - triH), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
        drawLine(color = errorColor, start = Offset(cx - triW, triTop - triH), end = Offset(cx + triW, triTop - triH), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)

        // Bottom triangle
        val triBot = stripBottom + 10.dp.toPx()
        drawLine(color = errorColor, start = Offset(cx, triBot), end = Offset(cx - triW, triBot + triH), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
        drawLine(color = errorColor, start = Offset(cx, triBot), end = Offset(cx + triW, triBot + triH), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
        drawLine(color = errorColor, start = Offset(cx - triW, triBot + triH), end = Offset(cx + triW, triBot + triH), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)

        // ── Edge fades ──
        val fadeWidth = w * 0.08f
        drawRect(
            color = surfaceVariant.copy(alpha = 0.7f),
            topLeft = Offset(0f, stripTop),
            size = Size(fadeWidth, stripHeight),
        )
        drawRect(
            color = surfaceVariant.copy(alpha = 0.7f),
            topLeft = Offset(w - fadeWidth, stripTop),
            size = Size(fadeWidth, stripHeight),
        )
    }
}
