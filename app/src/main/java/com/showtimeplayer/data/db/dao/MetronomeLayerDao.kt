package com.showtimeplayer.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.showtimeplayer.data.db.entity.MetronomeLayer
import kotlinx.coroutines.flow.Flow

@Dao
interface MetronomeLayerDao {
    @Query("SELECT * FROM metronome_layers WHERE presetId = :presetId ORDER BY sortOrder ASC")
    fun observeForPreset(presetId: Long): Flow<List<MetronomeLayer>>

    @Query("SELECT * FROM metronome_layers WHERE presetId = :presetId ORDER BY sortOrder ASC")
    suspend fun getForPreset(presetId: Long): List<MetronomeLayer>

    @Insert
    suspend fun insert(layer: MetronomeLayer): Long

    @Update
    suspend fun update(layer: MetronomeLayer)

    @Delete
    suspend fun delete(layer: MetronomeLayer)

    @Query("DELETE FROM metronome_layers WHERE id = :id")
    suspend fun deleteById(id: Long)
}
