package com.showtimeplayer.ui.screens.library

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
import kotlinx.coroutines.launch

data class LibraryUiState(
    val tracks: List<TrackEntity> = emptyList(),
    val isLoading: Boolean = false,
    val query: String = "",
)

class LibraryViewModel(
    private val repository: TrackRepositoryImpl,
) : ViewModel() {
    private val query = MutableStateFlow("")
    private val isLoading = MutableStateFlow(false)

    val uiState: StateFlow<LibraryUiState> =
        combine(repository.observeTracks(), query, isLoading) { tracks, q, loading ->
            val filtered = if (q.isBlank()) {
                tracks
            } else {
                tracks.filter {
                    it.title.orEmpty().contains(q, ignoreCase = true) ||
                        it.artist.orEmpty().contains(q, ignoreCase = true) ||
                        it.album.orEmpty().contains(q, ignoreCase = true)
                }
            }
            LibraryUiState(tracks = filtered, isLoading = loading, query = q)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState())

    fun onQueryChange(value: String) {
        query.value = value
    }

    fun refresh() {
        if (isLoading.value) return
        viewModelScope.launch {
            isLoading.value = true
            try {
                repository.refreshLibrary()
            } finally {
                isLoading.value = false
            }
        }
    }

    class Factory(private val repository: TrackRepositoryImpl) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return LibraryViewModel(repository) as T
        }
    }
}
