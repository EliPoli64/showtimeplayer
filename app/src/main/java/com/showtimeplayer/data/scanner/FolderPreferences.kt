package com.showtimeplayer.data.scanner

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.folderDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "scan_folders",
)

class FolderPreferences(private val context: Context) {

    private object Keys {
        val FOLDER_URIS = stringSetPreferencesKey("folder_uris")
    }

    val folders: Flow<Set<String>> = context.folderDataStore.data.map { prefs ->
        prefs[Keys.FOLDER_URIS] ?: emptySet()
    }

    suspend fun addFolder(uri: String) {
        context.folderDataStore.edit { prefs ->
            val current = prefs[Keys.FOLDER_URIS] ?: emptySet()
            prefs[Keys.FOLDER_URIS] = current + uri
        }
    }

    suspend fun removeFolder(uri: String) {
        context.folderDataStore.edit { prefs ->
            val current = prefs[Keys.FOLDER_URIS] ?: emptySet()
            prefs[Keys.FOLDER_URIS] = current - uri
        }
    }
}
