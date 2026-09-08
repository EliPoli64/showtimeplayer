package com.showtimeplayer.ui.screens.player

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerQueueSheet(
    uiState: PlayerUiState,
    onDismiss: () -> Unit,
    onTrackClick: (Int) -> Unit,
    onRemoveTrack: (Int) -> Unit,
    onClearQueue: () -> Unit,
    onMoveItem: (fromIndex: Int, toIndex: Int) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    val context = LocalContext.current
    val listState = rememberLazyListState()

    var draggedItemIndex by remember { mutableStateOf<Int?>(null) }
    var draggedOffsetY by remember { mutableStateOf(0f) }
    val itemHeight = 72f

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Queue (${uiState.queue.size})",
                    style = MaterialTheme.typography.titleMedium,
                )
                if (uiState.queue.isNotEmpty()) {
                    TextButton(onClick = onClearQueue) {
                        Text("Clear")
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (uiState.queue.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Queue is empty",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.heightIn(max = 400.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    itemsIndexed(
                        items = uiState.queue,
                        key = { index, track -> "${track.id}_$index" },
                    ) { index, track ->
                        val isCurrentTrack = index == uiState.currentQueueIndex
                        val isDragged = index == draggedItemIndex

                        QueueItemRow(
                            track = track,
                            index = index,
                            isCurrentTrack = isCurrentTrack,
                            isDragged = isDragged,
                            draggedOffsetY = if (isDragged) draggedOffsetY else 0f,
                            onClick = { onTrackClick(index) },
                            onRemove = { onRemoveTrack(index) },
                            onDragStart = {
                                draggedItemIndex = index
                                draggedOffsetY = 0f
                            },
                            onDrag = { deltaY ->
                                draggedOffsetY += deltaY
                                val currentIndex = draggedItemIndex ?: index
                                val targetIndex = (currentIndex + (draggedOffsetY / itemHeight).roundToInt())
                                    .coerceIn(0, uiState.queue.lastIndex)
                                if (targetIndex != currentIndex) {
                                    onMoveItem(currentIndex, targetIndex)
                                    draggedItemIndex = targetIndex
                                    draggedOffsetY = 0f
                                }
                            },
                            onDragEnd = {
                                draggedItemIndex = null
                                draggedOffsetY = 0f
                            },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QueueItemRow(
    track: com.showtimeplayer.data.db.entity.TrackEntity,
    index: Int,
    isCurrentTrack: Boolean,
    isDragged: Boolean,
    draggedOffsetY: Float,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
) {
    val context = LocalContext.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isDragged) {
                    Modifier
                        .zIndex(1f)
                        .shadow(8.dp, RoundedCornerShape(8.dp))
                        .graphicsLayer {
                            translationY = draggedOffsetY
                        }
                } else {
                    Modifier
                },
            )
            .clip(RoundedCornerShape(8.dp))
            .then(
                if (isCurrentTrack && !isDragged) {
                    Modifier.background(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                    )
                } else if (!isDragged) {
                    Modifier.background(MaterialTheme.colorScheme.surface)
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Drag handle — only this area responds to drag gestures
        Box(
            modifier = Modifier
                .size(32.dp)
                .pointerInput(Unit) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { onDragStart() },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            onDrag(dragAmount.y)
                        },
                        onDragEnd = { onDragEnd() },
                        onDragCancel = { onDragEnd() },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.DragHandle,
                contentDescription = "Drag to reorder",
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(modifier = Modifier.width(4.dp))

        // Track number or playing indicator — clickable
        if (isCurrentTrack) {
            Icon(
                imageVector = Icons.Filled.MusicNote,
                contentDescription = "Now playing",
                modifier = Modifier
                    .size(20.dp)
                    .pointerInput(Unit) {
                        detectTapGestures { onClick() }
                    },
                tint = MaterialTheme.colorScheme.primary,
            )
        } else {
            Text(
                text = "${index + 1}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(20.dp)
                    .pointerInput(Unit) {
                        detectTapGestures { onClick() }
                    },
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Album art thumbnail — clickable
        SubcomposeAsyncImage(
            model = ImageRequest.Builder(context)
                .data(track.albumArtUri)
                .crossfade(true)
                .build(),
            contentDescription = null,
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(4.dp))
                .pointerInput(Unit) {
                    detectTapGestures { onClick() }
                },
            error = {
                Icon(
                    imageVector = Icons.Filled.MusicNote,
                    contentDescription = null,
                    modifier = Modifier
                        .size(40.dp)
                        .padding(8.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
        )

        Spacer(modifier = Modifier.width(12.dp))

        // Track info — clickable
        Column(
            modifier = Modifier
                .weight(1f)
                .pointerInput(Unit) {
                    detectTapGestures { onClick() }
                },
        ) {
            Text(
                text = track.title ?: "Unknown title",
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (isCurrentTrack) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            Text(
                text = track.artist ?: "",
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Remove button
        IconButton(
            onClick = onRemove,
            modifier = Modifier.size(32.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "Remove from queue",
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Modifier.clickableWithoutDrag(onClick: () -> Unit): Modifier {
    return this.then(
        Modifier.pointerInput(Unit) {
            detectDragGesturesAfterLongPress(
                onDragStart = {},
                onDrag = { _, _ -> },
                onDragEnd = {},
                onDragCancel = {},
            )
        }
    )
}
