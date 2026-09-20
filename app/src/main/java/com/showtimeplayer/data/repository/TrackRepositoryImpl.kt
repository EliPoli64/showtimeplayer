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
     * Re-scans MediaStore using the selected folders and refreshes the cached track table
     * WITHOUT deleting existing rows. Existing tracks are updated in place (same id, so the
     * presets referencing them stay intact); new tracks are inserted. This prevents a library
     * rescan from cascade-deleting presets.
     */
    suspend fun refreshLibrary(): List<TrackEntity> {
        val folderUris = folderPreferences.folders.first()
        val existingByUri = trackDao.getAll().associateByTo(mutableMapOf()) { it.uri }
        val toUpdate = mutableListOf<TrackEntity>()
        val toInsert = mutableListOf<TrackEntity>()
        var latest: List<TrackEntity> = emptyList()
        scanner.scanLocalAudio(folderUris).collect { batch ->
            batch.forEach { scanned ->
                val existing = existingByUri[scanned.uri]
                if (existing != null) {
                    val merged = existing.copy(
                        title = scanned.title,
                        artist = scanned.artist,
                        album = scanned.album,
                        durationMs = scanned.durationMs,
                        format = scanned.format,
                        albumArtUri = scanned.albumArtUri,
                        trackNumber = scanned.trackNumber,
                        discNumber = scanned.discNumber,
                    )
                    existingByUri[scanned.uri] = merged
                    toUpdate.add(merged)
                } else {
                    toInsert.add(scanned)
                }
            }
            latest = batch
        }
        if (toUpdate.isNotEmpty()) trackDao.updateAll(toUpdate)
        if (toInsert.isNotEmpty()) trackDao.upsertAll(toInsert)
        return latest
    }

    suspend fun clearLibrary() = trackDao.clearAll()
}
