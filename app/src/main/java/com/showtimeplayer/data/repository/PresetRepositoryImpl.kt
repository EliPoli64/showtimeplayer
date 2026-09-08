package com.showtimeplayer.data.repository

import com.showtimeplayer.data.db.dao.PresetDao
import com.showtimeplayer.data.db.entity.PresetEntity
import kotlinx.coroutines.flow.Flow

class PresetRepositoryImpl(
    private val presetDao: PresetDao,
) {
    fun observeAll(): Flow<List<PresetEntity>> = presetDao.observeAll()

    fun observeForTrack(trackId: Long): Flow<List<PresetEntity>> = presetDao.observeForTrack(trackId)

    suspend fun getPreset(id: Long): PresetEntity? = presetDao.getById(id)

    suspend fun insert(preset: PresetEntity): Long = presetDao.insert(preset)

    suspend fun delete(preset: PresetEntity) = presetDao.delete(preset)

    suspend fun deleteById(id: Long) = presetDao.deleteById(id)
}
