package com.showtimeplayer.ui.screens.settings

import android.app.Application
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.showtimeplayer.data.repository.TrackRepositoryImpl
import com.showtimeplayer.data.scanner.FolderPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class FolderEntry(
    val uri: String,
    val name: String,
)

data class SettingsUiState(
    val folders: List<FolderEntry> = emptyList(),
    val isScanning: Boolean = false,
    val scanResultMessage: String? = null,
)

class SettingsViewModel(
    private val application: Application,
    private val folderPreferences: FolderPreferences,
    private val trackRepository: TrackRepositoryImpl,
) : ViewModel() {

    private val isScanning = MutableStateFlow(false)
    private val scanResultMessage = MutableStateFlow<String?>(null)

    val uiState: StateFlow<SettingsUiState> =
        combine(folderPreferences.folders, isScanning, scanResultMessage) { uris, scanning, message ->
            val entries = uris.map { uri ->
                FolderEntry(
                    uri = uri,
                    name = resolveFolderName(uri),
                )
            }
            SettingsUiState(folders = entries, isScanning = scanning, scanResultMessage = message)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun addFolder(uri: String) {
        viewModelScope.launch {
            folderPreferences.addFolder(uri)
        }
    }

    fun removeFolder(uri: String) {
        viewModelScope.launch {
            folderPreferences.removeFolder(uri)
        }
    }

    fun rescan() {
        if (isScanning.value) return
        viewModelScope.launch {
            isScanning.value = true
            scanResultMessage.value = null
            try {
                val tracks = trackRepository.refreshLibrary()
                scanResultMessage.value = "${tracks.size} tracks found"
            } finally {
                isScanning.value = false
            }
        }
    }

    fun dismissScanResult() {
        scanResultMessage.value = null
    }

    private fun resolveFolderName(uri: String): String {
        return try {
            DocumentFile.fromTreeUri(application, Uri.parse(uri))?.name ?: "Unknown folder"
        } catch (_: Exception) {
            "Unknown folder"
        }
    }

    class Factory(
        private val application: Application,
        private val folderPreferences: FolderPreferences,
        private val trackRepository: TrackRepositoryImpl,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SettingsViewModel(application, folderPreferences, trackRepository) as T
        }
    }
}
