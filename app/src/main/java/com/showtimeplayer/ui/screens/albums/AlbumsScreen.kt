package com.showtimeplayer.ui.screens.albums

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.showtimeplayer.data.db.entity.TrackEntity
import com.showtimeplayer.util.formatDurationMs
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumsScreen(
    viewModel: AlbumsViewModel,
    onTrackClick: (TrackEntity) -> Unit,
    onTrackAddToQueue: (TrackEntity) -> Unit,
    onPlayAlbum: (List<TrackEntity>, Int) -> Unit,
    onAddAlbumToQueue: (List<TrackEntity>) -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    if (uiState.selectedAlbum != null) {
        AlbumDetailScreen(
            album = uiState.selectedAlbum!!,
            onBack = viewModel::clearSelection,
            onTrackClick = onTrackClick,
            onTrackAddToQueue = onTrackAddToQueue,
            onPlayAlbum = { onPlayAlbum(uiState.selectedAlbum!!.tracks, 0) },
            onAddAlbumToQueue = { onAddAlbumToQueue(uiState.selectedAlbum!!.tracks) },
            snackbarHostState = snackbarHostState,
        )
    } else {
        AlbumListScreen(
            albums = uiState.albums,
            onAlbumClick = viewModel::selectAlbum,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlbumListScreen(
    albums: List<Album>,
    onAlbumClick: (Album) -> Unit,
) {
    val context = LocalContext.current

    Scaffold(
        topBar = { TopAppBar(title = { Text("Albums") }) },
    ) { padding ->
        if (albums.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "No albums found",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(items = albums, key = { it.name }) { album ->
                ListItem(
                    headlineContent = { Text(album.name) },
                    supportingContent = {
                        Text("${album.tracks.size} track${if (album.tracks.size != 1) "s" else ""}")
                    },
                    leadingContent = {
                        SubcomposeAsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(album.albumArtUri)
                                .crossfade(true)
                                .build(),
                            contentDescription = "Album art",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            error = {
                                Icon(
                                    imageVector = Icons.Filled.Album,
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                        )
                    },
                    modifier = Modifier.clickable { onAlbumClick(album) },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun AlbumDetailScreen(
    album: Album,
    onBack: () -> Unit,
    onTrackClick: (TrackEntity) -> Unit,
    onTrackAddToQueue: (TrackEntity) -> Unit,
    onPlayAlbum: () -> Unit,
    onAddAlbumToQueue: () -> Unit,
    snackbarHostState: SnackbarHostState,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(album.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
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
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            // Album art header
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    SubcomposeAsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(album.albumArtUri)
                            .crossfade(true)
                            .build(),
                        contentDescription = "Album art",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(200.dp)
                            .clip(RoundedCornerShape(12.dp)),
                        error = {
                            Box(
                                modifier = Modifier
                                    .size(200.dp)
                                    .clip(RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Album,
                                    contentDescription = null,
                                    modifier = Modifier.size(64.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = album.name,
                        style = MaterialTheme.typography.headlineSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    val artistName = album.tracks.firstOrNull()?.artist
                    if (!artistName.isNullOrBlank()) {
                        Text(
                            text = artistName,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "${album.tracks.size} track${if (album.tracks.size != 1) "s" else ""}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Play Album + Add to Queue buttons
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Button(
                            onClick = {
                                onPlayAlbum()
                                scope.launch {
                                    snackbarHostState.showSnackbar("Playing album")
                                }
                            },
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Play")
                        }

                        OutlinedButton(
                            onClick = {
                                onAddAlbumToQueue()
                                scope.launch {
                                    snackbarHostState.showSnackbar("Album added to queue")
                                }
                            },
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Add,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Add to Queue")
                        }
                    }
                }
            }

            // Track list
            items(items = album.tracks, key = { it.id }) { track ->
                ListItem(
                    headlineContent = { Text(track.title ?: "Unknown title") },
                    supportingContent = {
                        Text(
                            listOfNotNull(track.artist)
                                .joinToString(" • ")
                                .ifEmpty { track.album ?: "" },
                        )
                    },
                    trailingContent = {
                        IconButton(
                            onClick = {
                                onTrackAddToQueue(track)
                                scope.launch {
                                    snackbarHostState.showSnackbar("Added to queue")
                                }
                            },
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Add,
                                contentDescription = "Add to queue",
                            )
                        }
                    },
                    modifier = Modifier.combinedClickable(
                        onClick = { onTrackClick(track) },
                        onLongClick = {
                            onTrackAddToQueue(track)
                            scope.launch {
                                snackbarHostState.showSnackbar("Added to queue")
                            }
                        },
                    ),
                )
            }
        }
    }
}
