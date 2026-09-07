package com.showtimeplayer.ui.screens.library

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.showtimeplayer.data.db.entity.TrackEntity
import com.showtimeplayer.util.formatDurationMs
import kotlinx.coroutines.launch

private fun audioPermission(): String =
    if (Build.VERSION.SDK_INT >= 33) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    onTrackClick: (TrackEntity) -> Unit = {},
    onTrackAddToQueue: (TrackEntity) -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val permission = audioPermission()
    val hasPermission = ContextCompat.checkSelfPermission(context, permission) ==
        PackageManager.PERMISSION_GRANTED
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) viewModel.refresh()
    }

    LaunchedEffect(hasPermission) {
        if (hasPermission) viewModel.refresh()
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Library") }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!hasPermission) {
                Text(
                    "Grant audio access to index your local music library.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(onClick = { permissionLauncher.launch(permission) }) {
                    Text("Grant access")
                }
                return@Column
            }

            OutlinedTextField(
                value = uiState.query,
                onValueChange = viewModel::onQueryChange,
                label = { Text("Search tracks") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            if (uiState.isLoading && uiState.tracks.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                return@Column
            }

            if (uiState.tracks.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No tracks found. Add music to your device, then refresh.")
                }
                return@Column
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(items = uiState.tracks, key = { it.id }) { track ->
                    ListItem(
                        headlineContent = { Text(track.title ?: "Unknown title") },
                        supportingContent = {
                            Text(
                                listOfNotNull(track.artist, track.album)
                                    .joinToString(" • ").ifEmpty { "Unknown artist" },
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
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
}
