package com.showtimeplayer.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.showtimeplayer.data.db.entity.MetronomeRegion
import kotlinx.coroutines.flow.Flow

@Dao
interface MetronomeRegionDao {
    @Query("SELECT * FROM metronome_regions WHERE layerId = :layerId ORDER BY sortOrder ASC")
    fun observeForLayer(layerId: Long): Flow<List<MetronomeRegion>>

    @Query("SELECT * FROM metronome_regions WHERE layerId = :layerId ORDER BY sortOrder ASC")
    suspend fun getForLayer(layerId: Long): List<MetronomeRegion>

    @Query(
        """
        SELECT r.* FROM metronome_regions r
        INNER JOIN metronome_layers l ON r.layerId = l.id
        WHERE l.presetId = :presetId
        ORDER BY l.sortOrder ASC, r.sortOrder ASC
        """
    )
    fun observeForPreset(presetId: Long): Flow<List<MetronomeRegion>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(region: MetronomeRegion): Long

    @Update
    suspend fun update(region: MetronomeRegion)

    @Delete
    suspend fun delete(region: MetronomeRegion)

    @Query("DELETE FROM metronome_regions WHERE id = :id")
    suspend fun deleteById(id: Long)
}
