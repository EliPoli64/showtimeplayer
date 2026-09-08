package com.showtimeplayer.ui.screens.player

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.showtimeplayer.data.db.entity.TrackEntity
import com.showtimeplayer.player.service.PlaybackService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max

data class PlayerUiState(
    val currentTrack: TrackEntity? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val isBuffering: Boolean = false,
    val error: String? = null,
    val queue: List<TrackEntity> = emptyList(),
    val currentQueueIndex: Int = -1,
)

class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    private lateinit var mediaController: MediaController
    private var listener: PlayerListener? = null
    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    // Mirror of Media3 playlist since we can't serialize TrackEntity into MediaItem
    private val queueTracks = mutableListOf<TrackEntity>()

    init {
        val sessionToken = SessionToken(
            application,
            android.content.ComponentName(application, PlaybackService::class.java),
        )

        val controllerFuture = MediaController.Builder(application, sessionToken)
            .buildAsync()

        viewModelScope.launch {
            mediaController = withContext(Dispatchers.IO) {
                controllerFuture.get()
            }
            listener = PlayerListener()
            mediaController.addListener(listener!!)
            startProgressUpdates()
        }
    }

    fun playTrack(track: TrackEntity) {
        playTrackAsQueue(listOf(track), 0)
    }

    fun playTrackAsQueue(tracks: List<TrackEntity>, startIndex: Int) {
        if (!::mediaController.isInitialized || tracks.isEmpty()) return

        val mediaItems = tracks.map { trackToMediaItem(it) }

        queueTracks.clear()
        queueTracks.addAll(tracks)

        _uiState.update {
            it.copy(
                currentTrack = tracks[startIndex],
                error = null,
                queue = tracks.toList(),
                currentQueueIndex = startIndex,
            )
        }

        mediaController.setMediaItems(mediaItems, startIndex, 0L)
        mediaController.prepare()
        mediaController.play()
    }

    fun addToQueue(track: TrackEntity) {
        if (!::mediaController.isInitialized) return

        val mediaItem = trackToMediaItem(track)
        mediaController.addMediaItem(mediaItem)
        queueTracks.add(track)
        syncQueueFromController()
    }

    fun removeFromQueue(index: Int) {
        if (!::mediaController.isInitialized) return
        if (index < 0 || index >= mediaController.mediaItemCount) return

        mediaController.removeMediaItem(index)
        if (index < queueTracks.size) {
            queueTracks.removeAt(index)
        }

        if (mediaController.mediaItemCount == 0) {
            queueTracks.clear()
            _uiState.update {
                it.copy(
                    currentTrack = null,
                    queue = emptyList(),
                    currentQueueIndex = -1,
                    isPlaying = false,
                )
            }
            return
        }

        syncQueueFromController()
    }

    fun clearQueue() {
        if (!::mediaController.isInitialized) return
        mediaController.clearMediaItems()
        queueTracks.clear()
        _uiState.update {
            it.copy(
                currentTrack = null,
                queue = emptyList(),
                currentQueueIndex = -1,
                isPlaying = false,
                positionMs = 0L,
                durationMs = 0L,
            )
        }
    }

    fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        if (!::mediaController.isInitialized) return
        if (fromIndex == toIndex) return
        if (fromIndex < 0 || fromIndex >= mediaController.mediaItemCount) return
        if (toIndex < 0 || toIndex >= mediaController.mediaItemCount) return

        // Move in mirror list
        val item = queueTracks.removeAt(fromIndex)
        queueTracks.add(toIndex, item)

        // Move in Media3 player
        mediaController.moveMediaItem(fromIndex, toIndex)

        syncQueueFromController()
    }

    fun togglePlayPause() {
        if (!::mediaController.isInitialized) return
        if (mediaController.isPlaying) {
            mediaController.pause()
        } else {
            mediaController.play()
        }
    }

    fun seekTo(positionMs: Long) {
        if (!::mediaController.isInitialized) return
        mediaController.seekTo(positionMs)
        _uiState.update { it.copy(positionMs = positionMs) }
    }

    fun skipToPrevious() {
        if (!::mediaController.isInitialized) return
        mediaController.seekToPrevious()
    }

    fun skipToNext() {
        if (!::mediaController.isInitialized) return
        mediaController.seekToNext()
    }

    fun seekToQueueItem(index: Int) {
        if (!::mediaController.isInitialized) return
        if (index < 0 || index >= mediaController.mediaItemCount) return
        mediaController.seekTo(index, 0L)
        syncQueueFromController()
    }

    private fun syncQueueFromController() {
        if (!::mediaController.isInitialized) return
        val currentIdx = mediaController.currentMediaItemIndex
        val count = mediaController.mediaItemCount

        // Rebuild queue from our mirror list, clamping to available count
        val tracks = queueTracks.take(count)
        val currentTrack = tracks.getOrNull(currentIdx)

        _uiState.update {
            it.copy(
                queue = tracks,
                currentQueueIndex = currentIdx,
                currentTrack = currentTrack,
            )
        }
    }

    private fun trackToMediaItem(track: TrackEntity): MediaItem {
        return MediaItem.Builder()
            .setMediaId(track.uri)
            .setUri(Uri.parse(track.uri))
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(track.title)
                    .setArtist(track.artist)
                    .setAlbumTitle(track.album)
                    .build(),
            )
            .build()
    }

    private fun startProgressUpdates() {
        viewModelScope.launch {
            while (true) {
                if (::mediaController.isInitialized && mediaController.isPlaying) {
                    _uiState.update {
                        it.copy(
                            positionMs = max(0, mediaController.currentPosition),
                            durationMs = max(0, mediaController.duration),
                            isPlaying = true,
                            isBuffering = false,
                        )
                    }
                }
                delay(200)
            }
        }
    }

    private inner class PlayerListener : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _uiState.update { it.copy(isPlaying = isPlaying) }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_BUFFERING -> {
                    _uiState.update { it.copy(isBuffering = true) }
                }
                Player.STATE_READY -> {
                    _uiState.update {
                        it.copy(
                            isBuffering = false,
                            durationMs = if (::mediaController.isInitialized) max(0, mediaController.duration) else 0L,
                        )
                    }
                }
                Player.STATE_ENDED -> {
                    _uiState.update {
                        it.copy(isPlaying = false, positionMs = 0L)
                    }
                }
                Player.STATE_IDLE -> {
                    _uiState.update { it.copy(isBuffering = false) }
                }
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            syncQueueFromController()
        }

        override fun onPlayerError(error: PlaybackException) {
            _uiState.update {
                it.copy(error = error.message ?: "Playback error", isPlaying = false)
            }
        }
    }

    override fun onCleared() {
        listener?.let { mediaController.removeListener(it) }
        mediaController.release()
        super.onCleared()
    }

    class Factory(private val application: Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
            return PlayerViewModel(application) as T
        }
    }
}
