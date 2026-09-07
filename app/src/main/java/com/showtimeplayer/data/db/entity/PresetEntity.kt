package com.showtimeplayer.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

// Full preset logic (repository, auto-save) ships in the Presets feature.
// This entity exists now so PracticeDatabase compiles.
@Entity(
    tableName = "presets",
    foreignKeys = [
        ForeignKey(
            entity = TrackEntity::class,
            parentColumns = ["id"],
            childColumns = ["trackId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["trackId"])],
)
data class PresetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trackId: Long,
    val name: String,
    val playbackRate: Float = 1.0f,
    val pitchOffsetSemitones: Int = 0,
    val loopStartMs: Long? = null,
    val loopEndMs: Long? = null,
    val metronomeEnabled: Boolean = false,
    val metronomeBpm: Int = 120,
    val metronomeTimeSignatureNum: Int = 4,
    val metronomeTimeSignatureDenom: Int = 4,
    val metronomeCountInBars: Int = 0,
    val updatedAt: Long = System.currentTimeMillis(),
)
