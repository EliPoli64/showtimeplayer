package com.showtimeplayer.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

/**
 * The last playback session, so reopening the app returns to where the user left off.
 *
 * The queue is stored as track ids rather than URIs: ids are stable across a library rescan
 * (tracks merge by URI and rows are never deleted), and because they are plain Longs the
 * whole list serialises to a comma-separated string with no escaping to get wrong.
 */
data class SavedPlaybackState(
    val trackIds: List<Long>,
    val currentIndex: Int,
    val currentTrackId: Long,
    val positionMs: Long,
)

private val Context.playbackStateDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "playback_state",
)

class PlaybackStatePreferences(private val context: Context) {

    private object Keys {
        val QUEUE_TRACK_IDS = stringPreferencesKey("queue_track_ids")
        val CURRENT_INDEX = intPreferencesKey("current_index")
        val CURRENT_TRACK_ID = longPreferencesKey("current_track_id")
        val POSITION_MS = longPreferencesKey("position_ms")
    }

    /** Null when nothing has been saved yet, or the stored queue no longer parses. */
    val state: Flow<SavedPlaybackState?> = context.playbackStateDataStore.data
        .catch { throwable ->
            // A corrupt store must not take the player down on launch; report "nothing saved".
            if (throwable is IOException) emit(emptyPreferences()) else throw throwable
        }
        .map { prefs -> prefs.toSavedState() }

    private fun Preferences.toSavedState(): SavedPlaybackState? {
        val trackIds = this[Keys.QUEUE_TRACK_IDS]
            ?.split(',')
            ?.mapNotNull { it.trim().toLongOrNull() }
            .orEmpty()
        if (trackIds.isEmpty()) return null

        val currentIndex = (this[Keys.CURRENT_INDEX] ?: 0).coerceIn(0, trackIds.lastIndex)
        return SavedPlaybackState(
            trackIds = trackIds,
            currentIndex = currentIndex,
            currentTrackId = this[Keys.CURRENT_TRACK_ID] ?: trackIds[currentIndex],
            positionMs = (this[Keys.POSITION_MS] ?: 0L).coerceAtLeast(0L),
        )
    }

    /** Saving an empty queue clears the record instead of persisting nothing meaningful. */
    suspend fun save(state: SavedPlaybackState) {
        if (state.trackIds.isEmpty()) {
            clear()
            return
        }

        val currentIndex = state.currentIndex.coerceIn(0, state.trackIds.lastIndex)
        context.playbackStateDataStore.edit { prefs ->
            prefs[Keys.QUEUE_TRACK_IDS] = state.trackIds.joinToString(",")
            prefs[Keys.CURRENT_INDEX] = currentIndex
            prefs[Keys.CURRENT_TRACK_ID] = state.currentTrackId
            prefs[Keys.POSITION_MS] = state.positionMs.coerceAtLeast(0L)
        }
    }

    suspend fun clear() {
        context.playbackStateDataStore.edit { prefs ->
            prefs.remove(Keys.QUEUE_TRACK_IDS)
            prefs.remove(Keys.CURRENT_INDEX)
            prefs.remove(Keys.CURRENT_TRACK_ID)
            prefs.remove(Keys.POSITION_MS)
        }
    }
}
