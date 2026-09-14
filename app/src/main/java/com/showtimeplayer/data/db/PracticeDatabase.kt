package com.showtimeplayer.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.showtimeplayer.data.db.dao.MetronomeLayerDao
import com.showtimeplayer.data.db.dao.MetronomeRegionDao
import com.showtimeplayer.data.db.dao.PresetDao
import com.showtimeplayer.data.db.dao.TrackDao
import com.showtimeplayer.data.db.entity.MetronomeLayer
import com.showtimeplayer.data.db.entity.MetronomeRegion
import com.showtimeplayer.data.db.entity.PresetEntity
import com.showtimeplayer.data.db.entity.TrackEntity

@Database(
    entities = [
        TrackEntity::class,
        PresetEntity::class,
        MetronomeLayer::class,
        MetronomeRegion::class,
    ],
    version = 6,
    exportSchema = false,
)
abstract class PracticeDatabase : RoomDatabase() {
    abstract fun trackDao(): TrackDao

    abstract fun presetDao(): PresetDao

    abstract fun metronomeLayerDao(): MetronomeLayerDao

    abstract fun metronomeRegionDao(): MetronomeRegionDao

    companion object {
        @Volatile
        private var INSTANCE: PracticeDatabase? = null

        // v2 -> v3: drop the per-preset metronome columns and add the layered metronome tables.
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `metronome_layers` (
                        `id` INTEGER NOT NULL,
                        `presetId` INTEGER NOT NULL,
                        `name` TEXT NOT NULL,
                        `enabled` INTEGER NOT NULL,
                        `sortOrder` INTEGER NOT NULL,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`presetId`) REFERENCES `presets`(`id`)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_metronome_layers_presetId` " +
                        "ON `metronome_layers` (`presetId`)"
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `metronome_regions` (
                        `id` INTEGER NOT NULL,
                        `layerId` INTEGER NOT NULL,
                        `startMs` INTEGER NOT NULL,
                        `endMs` INTEGER,
                        `bpm` INTEGER NOT NULL,
                        `timeSignatureNum` INTEGER NOT NULL,
                        `timeSignatureDenom` INTEGER NOT NULL,
                        `countInBars` INTEGER NOT NULL,
                        `sortOrder` INTEGER NOT NULL,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`layerId`) REFERENCES `metronome_layers`(`id`)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_metronome_regions_layerId` " +
                        "ON `metronome_regions` (`layerId`)"
                )
                // Rebuild `presets` without the five metronome columns (SQLite can't drop
                // columns on older Android), copying over the preserved rows.
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `presets_new` (
                        `id` INTEGER NOT NULL,
                        `trackId` INTEGER NOT NULL,
                        `name` TEXT NOT NULL,
                        `playbackRate` REAL NOT NULL,
                        `pitchOffsetSemitones` INTEGER NOT NULL,
                        `loopStartMs` INTEGER,
                        `loopEndMs` INTEGER,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`trackId`) REFERENCES `tracks`(`id`)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO `presets_new`
                        (`id`, `trackId`, `name`, `playbackRate`, `pitchOffsetSemitones`,
                         `loopStartMs`, `loopEndMs`, `updatedAt`)
                    SELECT `id`, `trackId`, `name`, `playbackRate`, `pitchOffsetSemitones`,
                           `loopStartMs`, `loopEndMs`, `updatedAt`
                    FROM `presets`
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE `presets`")
                db.execSQL("ALTER TABLE `presets_new` RENAME TO `presets`")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_presets_trackId` ON `presets` (`trackId`)"
                )
            }
        }

        // v3 -> v4: add the song lead-in offset.
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `presets` ADD COLUMN `songOffsetMs` INTEGER NOT NULL DEFAULT 0")
            }
        }

        // v4 -> v5: add per-count-in volume.
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `metronome_regions` ADD COLUMN `volume` REAL NOT NULL DEFAULT 1")
            }
        }

        // v5 -> v6: add the pitch-follows-speed (tape) flag.
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `presets` ADD COLUMN `pitchFollowsSpeed` INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun getInstance(context: Context): PracticeDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    PracticeDatabase::class.java,
                    "practice.db",
                ).addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                    // Last resort only for unknown version gaps; known upgrades above preserve data.
                    .fallbackToDestructiveMigration()
                    .build().also { INSTANCE = it }
            }
    }
}