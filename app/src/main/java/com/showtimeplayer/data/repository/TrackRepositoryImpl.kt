package com.showtimeplayer.data.repository

import com.showtimeplayer.data.db.dao.TrackDao
import com.showtimeplayer.data.db.entity.TrackEntity
import com.showtimeplayer.data.scanner.FolderPreferences
import com.showtimeplayer.data.scanner.MediaStoreScanner
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class TrackRepositoryImpl(
    private val trackDao: TrackDao,
    private val scanner: MediaStoreScanner,
    private val folderPreferences: FolderPreferences,
) {
    fun observeTracks(): Flow<List<TrackEntity>> = trackDao.observeAll()

    suspend fun getTrack(id: Long): TrackEntity? = trackDao.getById(id)

    /**
     * Re-scans MediaStore using the selected folders, replaces the cached track table,
     * and returns the fresh list.
     */
    suspend fun refreshLibrary(): List<TrackEntity> {
        val folderUris = folderPreferences.folders.first()
        trackDao.clearAll()
        var latest: List<TrackEntity> = emptyList()
        scanner.scanLocalAudio(folderUris).collect { batch ->
            latest = batch
            if (batch.isNotEmpty()) {
                trackDao.upsertAll(batch)
            }
        }
        return latest
    }

    suspend fun clearLibrary() = trackDao.clearAll()
}
