package com.showtimeplayer.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import com.showtimeplayer.data.db.entity.PresetEntity
import kotlinx.coroutines.flow.Flow

// Full preset CRUD ships in the Presets feature; minimal query keeps Room valid.
@Dao
interface PresetDao {
    @Query("SELECT * FROM presets WHERE trackId = :trackId ORDER BY updatedAt DESC")
    fun observeForTrack(trackId: Long): Flow<List<PresetEntity>>
}
