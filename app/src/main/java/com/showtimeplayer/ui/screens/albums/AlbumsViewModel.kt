package com.showtimeplayer.ui.screens.albums

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.showtimeplayer.data.db.entity.TrackEntity
import com.showtimeplayer.data.repository.TrackRepositoryImpl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class Album(
    val name: String,
    val tracks: List<TrackEntity>,
    val albumArtUri: String?,
)

data class AlbumsUiState(
    val albums: List<Album> = emptyList(),
    val selectedAlbum: Album? = null,
)

class AlbumsViewModel(
    repository: TrackRepositoryImpl,
) : ViewModel() {

    private val selectedAlbum = MutableStateFlow<Album?>(null)

    val uiState: StateFlow<AlbumsUiState> =
        combine(repository.observeTracks(), selectedAlbum) { tracks, selected ->
            val albums = tracks
                .groupBy { it.album?.ifBlank { null } ?: "Unknown Album" }
                .map { (albumName, albumTracks) ->
                    Album(
                        name = albumName,
                        tracks = albumTracks.sortedWith(
                            compareBy<TrackEntity> { it.discNumber }
                                .thenBy { it.trackNumber }
                                .thenBy { it.title.orEmpty() },
                        ),
                        albumArtUri = albumTracks.firstOrNull()?.albumArtUri,
                    )
                }
                .sortedBy { it.name }

            AlbumsUiState(
                albums = albums,
                selectedAlbum = selected?.let { sel ->
                    albums.find { it.name == sel.name }
                },
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AlbumsUiState())

    fun selectAlbum(album: Album) {
        selectedAlbum.value = album
    }

    fun clearSelection() {
        selectedAlbum.value = null
    }

    class Factory(private val repository: TrackRepositoryImpl) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return AlbumsViewModel(repository) as T
        }
    }
}
