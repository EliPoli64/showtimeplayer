package com.showtimeplayer.data.repository

import com.showtimeplayer.data.db.dao.MetronomeLayerDao
import com.showtimeplayer.data.db.dao.MetronomeRegionDao
import com.showtimeplayer.data.db.dao.PresetDao
import com.showtimeplayer.data.db.entity.MetronomeLayer
import com.showtimeplayer.data.db.entity.MetronomeRegion
import com.showtimeplayer.data.db.entity.PresetEntity
import kotlinx.coroutines.flow.Flow

data class MetronomeLayerWithRegions(
    val layer: MetronomeLayer,
    val regions: List<MetronomeRegion>,
)

data class PresetWithLayers(
    val preset: PresetEntity,
    val layers: List<MetronomeLayerWithRegions>,
)

class PresetRepositoryImpl(
    private val presetDao: PresetDao,
    private val metronomeLayerDao: MetronomeLayerDao,
    private val metronomeRegionDao: MetronomeRegionDao,
) {
    fun observeAll(): Flow<List<PresetEntity>> = presetDao.observeAll()

    fun observeForTrack(trackId: Long): Flow<List<PresetEntity>> = presetDao.observeForTrack(trackId)

    suspend fun getPreset(id: Long): PresetEntity? = presetDao.getById(id)

    suspend fun insert(preset: PresetEntity): Long = presetDao.insert(preset)

    suspend fun update(preset: PresetEntity) = presetDao.update(preset)

    suspend fun delete(preset: PresetEntity) = presetDao.delete(preset)

    suspend fun deleteById(id: Long) = presetDao.deleteById(id)

    fun observePresetLayers(presetId: Long): Flow<List<MetronomeLayer>> =
        metronomeLayerDao.observeForPreset(presetId)

    fun observeLayerRegions(layerId: Long): Flow<List<MetronomeRegion>> =
        metronomeRegionDao.observeForLayer(layerId)

    fun observeRegionsForPreset(presetId: Long): Flow<List<MetronomeRegion>> =
        metronomeRegionDao.observeForPreset(presetId)

    suspend fun getLayersForPreset(presetId: Long): List<MetronomeLayer> =
        metronomeLayerDao.getForPreset(presetId)

    suspend fun getRegionsForLayer(layerId: Long): List<MetronomeRegion> =
        metronomeRegionDao.getForLayer(layerId)

    suspend fun insertLayer(layer: MetronomeLayer): Long =
        metronomeLayerDao.insert(layer)

    suspend fun updateLayer(layer: MetronomeLayer) =
        metronomeLayerDao.update(layer)

    suspend fun deleteLayer(layer: MetronomeLayer) =
        metronomeLayerDao.delete(layer)

    suspend fun deleteLayerById(id: Long) =
        metronomeLayerDao.deleteById(id)

    suspend fun insertRegion(region: MetronomeRegion): Long =
        metronomeRegionDao.insert(region)

    suspend fun updateRegion(region: MetronomeRegion) =
        metronomeRegionDao.update(region)

    suspend fun deleteRegion(region: MetronomeRegion) =
        metronomeRegionDao.delete(region)

    suspend fun deleteRegionById(id: Long) =
        metronomeRegionDao.deleteById(id)
}
