package com.showtimeplayer.ui.screens.presets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.showtimeplayer.data.db.entity.TrackEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddPresetTrackScreen(
    tracks: List<TrackEntity>,
    selectedTrack: TrackEntity?,
    onTrackSelected: (TrackEntity) -> Unit,
    onBack: () -> Unit,
    onNext: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val filteredTracks = if (query.isBlank()) {
        tracks
    } else {
        tracks.filter {
            it.title.orEmpty().contains(query, ignoreCase = true) ||
                it.artist.orEmpty().contains(query, ignoreCase = true)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Select Track") },
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
                .padding(horizontal = 16.dp),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Search tracks") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
            ) {
                items(items = filteredTracks, key = { it.id }) { track ->
                    val isSelected = track.id == selectedTrack?.id
                    ListItem(
                        headlineContent = { Text(track.title ?: "Unknown title") },
                        supportingContent = { Text(track.artist ?: "") },
                        modifier = Modifier.clickable {
                            onTrackSelected(track)
                            onNext()
                        },
                    )
                }
            }
        }
    }
}
