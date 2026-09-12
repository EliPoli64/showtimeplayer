package com.showtimeplayer.ui.screens.presets

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.showtimeplayer.data.db.entity.MetronomeRegion
import com.showtimeplayer.data.repository.MetronomeLayerWithRegions
import kotlin.math.abs

private const val MIN_MS_PER_PIXEL = 2f
private const val MAX_MS_PER_PIXEL = 200f
private const val DEFAULT_MS_PER_PIXEL = 30f
private const val SONG_TRACK_FRACTION = 0.4f
private const val PLAYHEAD_TOUCH_DP = 24f

private data class TimelineLayout(
    val songTrackHeight: Float,
    val layerHeight: Float,
    val layerGap: Float,
) {
    fun layerTop(index: Int): Float =
        songTrackHeight + layerGap + index * (layerHeight + layerGap)

    fun layerBottom(index: Int): Float = layerTop(index) + layerHeight

    fun layerIndexAtY(y: Float, layerCount: Int): Int {
        for (i in 0 until layerCount) {
            if (y >= layerTop(i) && y <= layerBottom(i)) return i
        }
        return -1
    }

    companion object {
        fun compute(height: Float, layerCount: Int, gapPx: Float): TimelineLayout {
            val song = height * SONG_TRACK_FRACTION
            val count = layerCount.coerceAtLeast(1)
            val remaining = (height - song - gapPx * (count + 1)).coerceAtLeast(1f)
            val layerH = (remaining / count).coerceIn(20f, height * 0.25f)
            return TimelineLayout(song, layerH, gapPx)
        }
    }
}

private sealed interface DragTarget {
    data class Region(
        val regionId: Long,
        val layerIndex: Int,
        val originalStartMs: Long,
        val originalEndMs: Long?,
    ) : DragTarget

    data class Song(val originalOffsetMs: Long) : DragTarget

    data class Empty(val layerIndex: Int) : DragTarget
}

private data class RegionDragPreview(
    val regionId: Long,
    val startMs: Long,
    val endMs: Long?,
    val layerIndex: Int,
)

@Composable
fun DawTimeline(
    waveformAmplitudes: List<Float>,
    layers: List<MetronomeLayerWithRegions>,
    trackDurationMs: Long,
    songOffsetMs: Long,
    playbackPositionMs: Long,
    isPlaying: Boolean,
    selectedRegionId: Long?,
    layerColors: List<Long>,
    onRegionTap: (MetronomeRegion) -> Unit,
    onRegionMove: (regionId: Long, newStartMs: Long, newLayerId: Long?) -> Unit,
    onAddRegionAtPosition: (layerId: Long, positionMs: Long) -> Unit,
    onSongMove: (deltaMs: Long) -> Unit,
    onScrubStart: () -> Unit,
    onScrub: (Long) -> Unit,
    onScrubEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var msPerPixel by remember { mutableFloatStateOf(DEFAULT_MS_PER_PIXEL) }
    var viewOffsetMs by remember { mutableLongStateOf(0L) }
    var dragPreview by remember { mutableStateOf<RegionDragPreview?>(null) }
    var songOffsetPreview by remember { mutableStateOf<Long?>(null) }
    var scrubbing by remember { mutableStateOf(false) }

    val currentMsPerPixel by rememberUpdatedState(msPerPixel)
    val currentViewOffset by rememberUpdatedState(viewOffsetMs)
    val currentLayers by rememberUpdatedState(layers)
    val currentPlayback by rememberUpdatedState(playbackPositionMs)
    val currentSongOffset by rememberUpdatedState(songOffsetMs)
    val currentOnRegionTap by rememberUpdatedState(onRegionTap)
    val currentOnRegionMove by rememberUpdatedState(onRegionMove)
    val currentOnAddRegionAtPosition by rememberUpdatedState(onAddRegionAtPosition)
    val currentOnSongMove by rememberUpdatedState(onSongMove)
    val currentOnScrubStart by rememberUpdatedState(onScrubStart)
    val currentOnScrub by rememberUpdatedState(onScrub)
    val currentOnScrubEnd by rememberUpdatedState(onScrubEnd)

    val textMeasurer = rememberTextMeasurer()

    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val playheadColor = MaterialTheme.colorScheme.error
    val gridColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f)
    val waveformColor = MaterialTheme.colorScheme.primary
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val primaryColor = MaterialTheme.colorScheme.primary

    Box(
        modifier = modifier
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    if (zoom != 1f) {
                        val oldMpp = msPerPixel
                        msPerPixel = (msPerPixel / zoom).coerceIn(MIN_MS_PER_PIXEL, MAX_MS_PER_PIXEL)
                        val scale = msPerPixel / oldMpp
                        viewOffsetMs = (viewOffsetMs * scale).toLong()
                    }
                    if (pan.x != 0f) {
                        val timelineEnd = (trackDurationMs + songOffsetMs).coerceAtLeast(1000L)
                        viewOffsetMs = (viewOffsetMs - (pan.x * msPerPixel).toLong())
                            .coerceIn(0L, (timelineEnd - 1000).coerceAtLeast(0))
                    }
                }
            }
            .pointerInput(Unit) {
                var totalPx = 0f
                var target: DragTarget? = null
                detectDragGesturesAfterLongPress(
                    onDragStart = { offset ->
                        val h = size.height.toFloat()
                        val gap = 2.dp.toPx()
                        val layerList = currentLayers
                        val layout = TimelineLayout.compute(h, layerList.size, gap)
                        val mpp = currentMsPerPixel
                        val off = currentViewOffset
                        val ms = off + (offset.x * mpp).toLong()

                        val hit = findRegionAt(
                            layerList, ms, offset.y,
                            layout.songTrackHeight, layout.layerHeight, layout.layerGap,
                        )

                        target = when {
                            offset.y < layout.songTrackHeight -> DragTarget.Song(currentSongOffset)
                            hit != null -> {
                                val (region, layerIndex) = hit
                                DragTarget.Region(
                                    regionId = region.id,
                                    layerIndex = layerIndex,
                                    originalStartMs = region.startMs,
                                    originalEndMs = region.endMs,
                                )
                            }
                            else -> DragTarget.Empty(
                                layout.layerIndexAtY(offset.y, layerList.size),
                            )
                        }
                        totalPx = 0f

                        when (val t = target) {
                            is DragTarget.Song -> {
                                songOffsetPreview = t.originalOffsetMs
                            }
                            is DragTarget.Region -> {
                                dragPreview = RegionDragPreview(
                                    t.regionId, t.originalStartMs, t.originalEndMs, t.layerIndex,
                                )
                            }
                            is DragTarget.Empty -> {
                                if (t.layerIndex in layerList.indices) {
                                    currentOnAddRegionAtPosition(
                                        layerList[t.layerIndex].layer.id,
                                        ms.coerceAtLeast(0L),
                                    )
                                }
                            }
                            null -> Unit
                        }
                    },
                    onDrag = { change, dragAmount ->
                        totalPx += dragAmount.x
                        val mpp = currentMsPerPixel
                        when (val t = target) {
                            is DragTarget.Song -> {
                                val deltaMs = (totalPx * mpp).toLong()
                                songOffsetPreview = (t.originalOffsetMs + deltaMs).coerceAtLeast(0L)
                            }
                            is DragTarget.Region -> {
                                val h = size.height.toFloat()
                                val gap = 2.dp.toPx()
                                val layerList = currentLayers
                                val layout = TimelineLayout.compute(h, layerList.size, gap)
                                val timelineEndMs =
                                    (trackDurationMs + currentSongOffset).coerceAtLeast(0L)
                                val deltaMs = (totalPx * mpp).toLong()
                                val newLayerIndex = layout
                                    .layerIndexAtY(change.position.y, layerList.size)
                                    .let { if (it >= 0) it else t.layerIndex }
                                dragPreview = RegionDragPreview(
                                    regionId = t.regionId,
                                    startMs = (t.originalStartMs + deltaMs)
                                        .coerceIn(0L, timelineEndMs),
                                    endMs = t.originalEndMs?.let {
                                        (it + deltaMs).coerceIn(0L, timelineEndMs)
                                    },
                                    layerIndex = newLayerIndex,
                                )
                            }
                            else -> Unit
                        }
                        change.consume()
                    },
                    onDragEnd = {
                        when (val t = target) {
                            is DragTarget.Song -> {
                                songOffsetPreview?.let { preview ->
                                    currentOnSongMove(preview - t.originalOffsetMs)
                                }
                            }
                            is DragTarget.Region -> {
                                dragPreview?.let { preview ->
                                    val newLayerId = currentLayers
                                        .getOrNull(preview.layerIndex)?.layer?.id
                                    currentOnRegionMove(t.regionId, preview.startMs, newLayerId)
                                }
                            }
                            else -> Unit
                        }
                        target = null
                        dragPreview = null
                        songOffsetPreview = null
                        totalPx = 0f
                    },
                    onDragCancel = {
                        target = null
                        dragPreview = null
                        songOffsetPreview = null
                        totalPx = 0f
                    },
                )
            }
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val layerList = currentLayers
                    val gap = 2.dp.toPx()
                    val layout = TimelineLayout.compute(size.height.toFloat(), layerList.size, gap)
                    val ms = currentViewOffset + (offset.x * currentMsPerPixel).toLong()
                    val hit = findRegionAt(
                        layerList, ms, offset.y,
                        layout.songTrackHeight, layout.layerHeight, layout.layerGap,
                    )
                    if (hit != null) currentOnRegionTap(hit.first)
                }
            }
            .pointerInput(Unit) {
                val touchPx = PLAYHEAD_TOUCH_DP.dp.toPx()
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val playheadX =
                        ((currentPlayback - currentViewOffset) / currentMsPerPixel).toFloat()
                    val onPlayhead = abs(down.position.x - playheadX) <= touchPx
                    if (!onPlayhead) return@awaitEachGesture

                    down.consume()
                    scrubbing = true
                    currentOnScrubStart()
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        val pos =
                            currentViewOffset + (change.position.x * currentMsPerPixel).toLong()
                        currentOnScrub(pos.coerceAtLeast(0L))
                        change.consume()
                    }
                    scrubbing = false
                    currentOnScrubEnd()
                }
            }
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .matchParentSize(),
        ) {
            val w = size.width
            val h = size.height
            val gap = 2.dp.toPx()
            val layout = TimelineLayout.compute(h, layers.size, gap)
            val songTrackHeight = layout.songTrackHeight

            // Draw grid lines (time markers)
            val gridIntervalMs = calculateGridInterval(msPerPixel)
            val gridStartMs = (viewOffsetMs / gridIntervalMs) * gridIntervalMs
            var gridMs = gridStartMs
            while (gridMs < viewOffsetMs + w * msPerPixel) {
                val x = ((gridMs - viewOffsetMs) / msPerPixel).toFloat()
                if (x in 0f..w) {
                    drawLine(gridColor, Offset(x, 0f), Offset(x, h), strokeWidth = 0.5f)
                    val label = formatTimeShort(gridMs)
                    val measured = textMeasurer.measure(
                        label,
                        style = TextStyle(fontSize = 9.sp, color = onSurfaceVariant),
                    )
                    drawText(measured, topLeft = Offset(x + 2.dp.toPx(), 2.dp.toPx()))
                }
                gridMs += gridIntervalMs
            }

            // Waveform (shifted by the song's timeline offset)
            val effectiveOffset = songOffsetPreview ?: songOffsetMs

            // Song track background + label
            drawRoundRect(
                color = trackColor.copy(alpha = 0.3f),
                topLeft = Offset(0f, 0f),
                size = Size(w, songTrackHeight),
                cornerRadius = CornerRadius(4.dp.toPx()),
            )
            val clipStartX = ((effectiveOffset - viewOffsetMs) / msPerPixel).toFloat()
            val labelX = (clipStartX + 4.dp.toPx()).coerceIn(4.dp.toPx(), w - 40.dp.toPx())
            val trackLabelMeasured = textMeasurer.measure(
                "Song",
                style = TextStyle(fontSize = 10.sp, color = onSurfaceVariant),
            )
            drawText(trackLabelMeasured, topLeft = Offset(labelX, songTrackHeight - 14.dp.toPx()))

            if (waveformAmplitudes.isNotEmpty() && trackDurationMs > 0) {
                val totalBars = waveformAmplitudes.size
                val barWidth = 2.dp.toPx()
                val barGap = 1.dp.toPx()
                val barPitch = (barWidth + barGap).coerceAtLeast(1f)
                val barCount = (w / barPitch).toInt().coerceAtLeast(1)
                val halfWaveH = songTrackHeight * 0.35f
                val centerY = songTrackHeight / 2f

                for (i in 0 until barCount) {
                    val x = i * barPitch
                    val timelineMs = viewOffsetMs +
                        ((x + barWidth / 2f) * msPerPixel).toLong()
                    val audioMs = timelineMs - effectiveOffset
                    if (audioMs < 0 || audioMs > trackDurationMs) continue

                    val ampIdx = ((audioMs / trackDurationMs.toFloat()) * totalBars).toInt()
                        .coerceIn(0, totalBars - 1)
                    val amplitude = waveformAmplitudes[ampIdx]
                    val barH = (amplitude * halfWaveH).coerceAtLeast(1.dp.toPx())
                    val alpha = (0.4f + 0.6f * amplitude).coerceIn(0f, 1f)

                    drawRoundRect(
                        color = waveformColor.copy(alpha = alpha),
                        topLeft = Offset(x, centerY - barH),
                        size = Size(barWidth, barH),
                        cornerRadius = CornerRadius(barWidth / 2f),
                    )
                    drawRoundRect(
                        color = waveformColor.copy(alpha = alpha * 0.6f),
                        topLeft = Offset(x, centerY),
                        size = Size(barWidth, barH),
                        cornerRadius = CornerRadius(barWidth / 2f),
                    )
                }
            }

            // Song clip boundaries
            if (trackDurationMs > 0) {
                val clipEndX = ((effectiveOffset + trackDurationMs - viewOffsetMs) / msPerPixel).toFloat()
                val boundaryColor = waveformColor.copy(alpha = 0.6f)
                if (clipStartX in 0f..w) {
                    drawLine(boundaryColor, Offset(clipStartX, 0f), Offset(clipStartX, songTrackHeight), strokeWidth = 2.dp.toPx())
                }
                if (clipEndX in 0f..w) {
                    drawLine(boundaryColor, Offset(clipEndX, 0f), Offset(clipEndX, songTrackHeight), strokeWidth = 2.dp.toPx())
                }
            }

            // Metronome layers
            layers.forEachIndexed { index, layerWithRegions ->
                val layerTop = layout.layerTop(index)
                val color = Color(if (index < layerColors.size) layerColors[index] else 0xFF888888)
                val isDropTarget = dragPreview?.layerIndex == index

                drawRoundRect(
                    color = if (isDropTarget) {
                        color.copy(alpha = 0.18f)
                    } else {
                        trackColor.copy(alpha = 0.2f)
                    },
                    topLeft = Offset(0f, layerTop),
                    size = Size(w, layout.layerHeight),
                    cornerRadius = CornerRadius(4.dp.toPx()),
                )

                val layerLabelMeasured = textMeasurer.measure(
                    layerWithRegions.layer.name,
                    style = TextStyle(fontSize = 10.sp, color = color),
                )
                drawText(
                    layerLabelMeasured,
                    topLeft = Offset(4.dp.toPx(), layerTop + layout.layerHeight - 14.dp.toPx()),
                )

                for (region in layerWithRegions.regions) {
                    val isDragged = dragPreview?.regionId == region.id
                    if (isDragged) continue

                    drawRegion(
                        region = region,
                        layerTop = layerTop,
                        layout = layout,
                        color = color,
                        isSelected = region.id == selectedRegionId,
                        primaryColor = primaryColor,
                        trackDurationMs = trackDurationMs,
                        viewOffsetMs = viewOffsetMs,
                        msPerPixel = msPerPixel,
                        canvasWidth = w,
                        textMeasurer = textMeasurer,
                    )
                }
            }

            // Drag preview overlay
            dragPreview?.let { preview ->
                val layerTop = layout.layerTop(preview.layerIndex)
                val color = Color(
                    if (preview.layerIndex in layerColors.indices) layerColors[preview.layerIndex]
                    else 0xFF888888,
                )
                val original = layers.flatMap { it.regions }.find { it.id == preview.regionId }
                val region = original?.copy(
                    startMs = preview.startMs,
                    endMs = preview.endMs,
                ) ?: MetronomeRegion(
                    id = preview.regionId,
                    layerId = 0,
                    startMs = preview.startMs,
                    endMs = preview.endMs,
                )
                drawRegion(
                    region = region,
                    layerTop = layerTop,
                    layout = layout,
                    color = color,
                    isSelected = true,
                    primaryColor = primaryColor,
                    trackDurationMs = trackDurationMs,
                    viewOffsetMs = viewOffsetMs,
                    msPerPixel = msPerPixel,
                    canvasWidth = w,
                    textMeasurer = textMeasurer,
                    alpha = 1f,
                    outline = true,
                )
            }

            // Playhead
            if (trackDurationMs > 0) {
                val playheadX = ((playbackPositionMs - viewOffsetMs) / msPerPixel).toFloat()
                if (playheadX in -4f..w + 4f) {
                    if (scrubbing) {
                        val bandHalf = PLAYHEAD_TOUCH_DP.dp.toPx()
                        drawRect(
                            color = playheadColor.copy(alpha = 0.12f),
                            topLeft = Offset(playheadX - bandHalf, 0f),
                            size = Size(bandHalf * 2f, h),
                        )
                    }
                    drawLine(
                        color = playheadColor,
                        start = Offset(playheadX, 0f),
                        end = Offset(playheadX, h),
                        strokeWidth = if (scrubbing) 3.dp.toPx() else 2.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                    val handleW = if (scrubbing) 20.dp.toPx() else 16.dp.toPx()
                    val handleH = if (scrubbing) 14.dp.toPx() else 12.dp.toPx()
                    drawRoundRect(
                        color = playheadColor,
                        topLeft = Offset(playheadX - handleW / 2f, 0f),
                        size = Size(handleW, handleH),
                        cornerRadius = CornerRadius(handleH / 2f),
                    )
                }
            }
        }
    }
}

private fun countInDurationMs(region: MetronomeRegion): Long {
    if (region.bpm <= 0 || region.countInBars <= 0 || region.timeSignatureNum <= 0) return 0L
    val beatMs = 60_000L / region.bpm
    return region.countInBars * beatMs * region.timeSignatureNum
}

private fun DrawScope.drawRegion(
    region: MetronomeRegion,
    layerTop: Float,
    layout: TimelineLayout,
    color: Color,
    isSelected: Boolean,
    primaryColor: Color,
    trackDurationMs: Long,
    viewOffsetMs: Long,
    msPerPixel: Float,
    canvasWidth: Float,
    textMeasurer: TextMeasurer,
    alpha: Float = 0.7f,
    outline: Boolean = false,
) {
    if (trackDurationMs <= 0) return

    val countInMs = countInDurationMs(region)
    val barEndMs = region.startMs
    val barStartMs = barEndMs - countInMs

    val regionLeft = ((barStartMs - viewOffsetMs) / msPerPixel).toFloat()
    val regionRight = ((barEndMs - viewOffsetMs) / msPerPixel).toFloat()
    if (regionRight < 0f || regionLeft > canvasWidth) return

    val regionWidth = (regionRight - regionLeft).coerceAtLeast(10.dp.toPx())
    val regionTop = layerTop + 3.dp.toPx()
    val regionH = (layout.layerHeight - 6.dp.toPx()).coerceAtLeast(8.dp.toPx())
    val blockColor = if (isSelected) primaryColor else color
    val corner = CornerRadius(6.dp.toPx())

    drawRoundRect(
        color = blockColor.copy(alpha = alpha),
        topLeft = Offset(regionLeft, regionTop),
        size = Size(regionWidth, regionH),
        cornerRadius = corner,
    )

    if (outline) {
        drawRoundRect(
            color = Color.White,
            topLeft = Offset(regionLeft, regionTop),
            size = Size(regionWidth, regionH),
            cornerRadius = corner,
            style = Stroke(width = 1.5.dp.toPx()),
        )
    }

    // Beat ticks across the count-in
    if (countInMs > 0 && region.timeSignatureNum > 0) {
        val beatMs = 60_000.0 / region.bpm
        val totalBeats = region.countInBars * region.timeSignatureNum
        for (b in 1 until totalBeats) {
            val tickMs = barStartMs + (b * beatMs).toLong()
            val tickX = ((tickMs - viewOffsetMs) / msPerPixel).toFloat()
            if (tickX in regionLeft..regionRight) {
                val isBarStart = b % region.timeSignatureNum == 0
                drawLine(
                    color = Color.White.copy(alpha = if (isBarStart) 0.65f else 0.3f),
                    start = Offset(tickX, regionTop + regionH * if (isBarStart) 0.12f else 0.3f),
                    end = Offset(tickX, regionTop + regionH * if (isBarStart) 0.88f else 0.7f),
                    strokeWidth = if (isBarStart) 1.5.dp.toPx() else 1.dp.toPx(),
                )
            }
        }
    }

    // Downbeat marker at the end of the count-in
    if (regionRight in 0f..canvasWidth) {
        drawLine(
            color = Color.White.copy(alpha = 0.9f),
            start = Offset(regionRight, regionTop - 2.dp.toPx()),
            end = Offset(regionRight, regionTop + regionH + 2.dp.toPx()),
            strokeWidth = 2.dp.toPx(),
        )
    }

    if (regionWidth > 40.dp.toPx()) {
        val measured = textMeasurer.measure(
            "${region.countInBars} bar • ${region.bpm}",
            style = TextStyle(fontSize = 10.sp, color = Color.White),
        )
        drawText(
            measured,
            topLeft = Offset(
                (regionLeft + 6.dp.toPx()).coerceAtLeast(6.dp.toPx()),
                regionTop + (regionH - measured.size.height) / 2f,
            ),
        )
    }
}

private fun findRegionAt(
    layers: List<MetronomeLayerWithRegions>,
    ms: Long,
    y: Float,
    songTrackHeight: Float,
    layerHeight: Float,
    layerGap: Float,
): Pair<MetronomeRegion, Int>? {
    layers.forEachIndexed { index, layerWithRegions ->
        val top = songTrackHeight + layerGap + index * (layerHeight + layerGap)
        val bottom = top + layerHeight
        if (y in top..bottom) {
            for (region in layerWithRegions.regions) {
                val countInMs = countInDurationMs(region)
                val barEnd = region.startMs
                val barStart = barEnd - countInMs
                val tolerance = if (countInMs > 0) 0L else 300L
                if (ms in (barStart - tolerance)..(barEnd + tolerance)) {
                    return region to index
                }
            }
        }
    }
    return null
}

private fun calculateGridInterval(msPerPixel: Float): Long = when {
    msPerPixel < 5 -> 1_000L
    msPerPixel < 20 -> 5_000L
    msPerPixel < 50 -> 10_000L
    msPerPixel < 100 -> 30_000L
    else -> 60_000L
}

private fun formatTimeShort(ms: Long): String {
    val totalSec = ms / 1000
    return "%d:%02d".format(totalSec / 60, totalSec % 60)
}
