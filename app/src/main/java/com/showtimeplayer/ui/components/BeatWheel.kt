package com.showtimeplayer.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.PI
import kotlin.math.cos

private const val MS_PER_PIXEL = 40f

@Composable
fun BeatWheel(
    currentPositionMs: Long,
    durationMs: Long,
    waveformAmplitudes: List<Float>,
    bpm: Int,
    timeSignatureNum: Int,
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

    // Sync display with external position when not dragging
    if (!isDragging && currentPositionMs != displayPositionMs) {
        displayPositionMs = currentPositionMs
    }

    val barCount = 120

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    var startDragX = 0f
                    var startDragPosMs = 0L
                    while (true) {
                        val event = awaitPointerEvent()
                        when (event.type) {
                            PointerEventType.Press -> {
                                isDragging = true
                                startDragX = event.changes.first().position.x
                                startDragPosMs = displayPositionMs
                                event.changes.first().consume()
                            }
                            PointerEventType.Move -> {
                                if (isDragging && durationMs > 0) {
                                    val dx = event.changes.first().position.x - startDragX
                                    val timeDelta = (dx * MS_PER_PIXEL).toLong()
                                    val newPos = (startDragPosMs + timeDelta)
                                        .coerceIn(0L, durationMs)
                                    displayPositionMs = newPos
                                    onPositionChange(newPos)
                                }
                                event.changes.forEach { it.consume() }
                            }
                            PointerEventType.Release -> {
                                if (isDragging) {
                                    isDragging = false
                                    onSeek(displayPositionMs)
                                }
                            }
                        }
                    }
                }
            },
    ) {
        val w = size.width
        val h = size.height
        val centerY = h / 2f
        val stripHeight = h * 0.55f
        val stripTop = centerY - stripHeight / 2f
        val stripBottom = centerY + stripHeight / 2f
        val barWidth = (w / barCount) * 0.7f
        val barGap = (w / barCount) * 0.3f

        val posMs = displayPositionMs.toFloat()
        val windowDurationMs = w * MS_PER_PIXEL
        val windowStart = posMs - windowDurationMs / 2f
        val windowEnd = posMs + windowDurationMs / 2f

        // ── Strip background ──
        drawRoundRect(
            color = surfaceVariant.copy(alpha = 0.25f),
            topLeft = Offset(0f, stripTop),
            size = androidx.compose.ui.geometry.Size(w, stripHeight),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(8.dp.toPx()),
        )

        // ── Waveform bars ──
        if (waveformAmplitudes.isNotEmpty() && durationMs > 0) {
            val totalBars = waveformAmplitudes.size
            for (i in 0 until barCount) {
                val barCenterMs = windowStart + (i.toFloat() / barCount) * windowDurationMs
                if (barCenterMs < 0 || barCenterMs > durationMs) continue

                // Map bar time to waveform amplitude index
                val ampIdx = ((barCenterMs / durationMs.toFloat()) * totalBars).toInt()
                    .coerceIn(0, totalBars - 1)
                val amplitude = waveformAmplitudes[ampIdx]

                val barHeight = (amplitude * stripHeight * 0.85f).coerceAtLeast(2.dp.toPx())
                val x = i * (barWidth + barGap) + barGap / 2f

                val alpha = (0.3f + 0.7f * amplitude).coerceIn(0f, 1f)
                drawRect(
                    color = primaryColor.copy(alpha = alpha),
                    topLeft = Offset(x, centerY - barHeight / 2f),
                    size = androidx.compose.ui.geometry.Size(barWidth, barHeight),
                )
            }
        } else {
            // Placeholder bars
            for (i in 0 until barCount) {
                val h2 = stripHeight * 0.15f
                val x = i * (barWidth + barGap) + barGap / 2f
                drawRect(
                    color = onSurfaceVariant.copy(alpha = 0.12f),
                    topLeft = Offset(x, centerY - h2 / 2f),
                    size = androidx.compose.ui.geometry.Size(barWidth, h2),
                )
            }
        }

        // ── Beat grid ticks (vertical lines) ──
        if (bpm > 0 && durationMs > 0) {
            val beatMs = 60_000f / bpm
            val firstBeat = (windowStart / beatMs).toInt().coerceAtLeast(0)
            val lastBeat = (windowEnd / beatMs).toInt() + 1
            for (beat in firstBeat..lastBeat) {
                val timeMs = beat * beatMs
                if (timeMs < 0 || timeMs > durationMs) continue
                val x = ((timeMs - windowStart) / windowDurationMs) * w
                val isDown = beat % timeSignatureNum == 0
                drawLine(
                    color = primaryColor.copy(alpha = if (isDown) 0.35f else 0.15f),
                    start = Offset(x, stripTop),
                    end = Offset(x, stripBottom),
                    strokeWidth = if (isDown) 1.5.dp.toPx() else 0.75.dp.toPx(),
                )
            }
        }

        // ── Center playhead (red line + triangle) ──
        val cx = w / 2f
        val triSize = 6.dp.toPx()

        // Triangle above strip
        drawLine(
            color = errorColor,
            start = Offset(cx, stripTop - 12.dp.toPx()),
            end = Offset(cx - triSize, stripTop - 12.dp.toPx() - triSize * 1.5f),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round,
        )
        drawLine(
            color = errorColor,
            start = Offset(cx, stripTop - 12.dp.toPx()),
            end = Offset(cx + triSize, stripTop - 12.dp.toPx() - triSize * 1.5f),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round,
        )
        drawLine(
            color = errorColor,
            start = Offset(cx - triSize, stripTop - 12.dp.toPx() - triSize * 1.5f),
            end = Offset(cx + triSize, stripTop - 12.dp.toPx() - triSize * 1.5f),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round,
        )

        // Line through strip
        drawLine(
            color = errorColor,
            start = Offset(cx, stripTop),
            end = Offset(cx, stripBottom),
            strokeWidth = 2.dp.toPx(),
        )

        // Triangle below strip
        drawLine(
            color = errorColor,
            start = Offset(cx, stripBottom + 12.dp.toPx()),
            end = Offset(cx - triSize, stripBottom + 12.dp.toPx() + triSize * 1.5f),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round,
        )
        drawLine(
            color = errorColor,
            start = Offset(cx, stripBottom + 12.dp.toPx()),
            end = Offset(cx + triSize, stripBottom + 12.dp.toPx() + triSize * 1.5f),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round,
        )
        drawLine(
            color = errorColor,
            start = Offset(cx - triSize, stripBottom + 12.dp.toPx() + triSize * 1.5f),
            end = Offset(cx + triSize, stripBottom + 12.dp.toPx() + triSize * 1.5f),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round,
        )

        // ── Edge fades ──
        val fadeWidth = w * 0.1f
        drawRect(
            color = surfaceVariant.copy(alpha = 0.6f),
            topLeft = Offset(0f, stripTop),
            size = androidx.compose.ui.geometry.Size(fadeWidth, stripHeight),
        )
        drawRect(
            color = surfaceVariant.copy(alpha = 0.6f),
            topLeft = Offset(w - fadeWidth, stripTop),
            size = androidx.compose.ui.geometry.Size(fadeWidth, stripHeight),
        )
    }
}
