package com.showtimeplayer.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.showtimeplayer.data.db.dao.PresetDao
import com.showtimeplayer.data.db.dao.TrackDao
import com.showtimeplayer.data.db.entity.PresetEntity
import com.showtimeplayer.data.db.entity.TrackEntity

@Database(
    entities = [TrackEntity::class, PresetEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class PracticeDatabase : RoomDatabase() {
    abstract fun trackDao(): TrackDao

    abstract fun presetDao(): PresetDao

    companion object {
        @Volatile
        private var INSTANCE: PracticeDatabase? = null

        fun getInstance(context: Context): PracticeDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    PracticeDatabase::class.java,
                    "practice.db",
                ).fallbackToDestructiveMigration().build().also { INSTANCE = it }
            }
    }
}
