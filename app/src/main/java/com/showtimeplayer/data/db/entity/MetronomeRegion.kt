package com.showtimeplayer.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "metronome_regions",
    foreignKeys = [
        ForeignKey(
            entity = MetronomeLayer::class,
            parentColumns = ["id"],
            childColumns = ["layerId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["layerId"])],
)
data class MetronomeRegion(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val layerId: Long,
    val startMs: Long,
    val endMs: Long? = null,
    val bpm: Int = 120,
    val timeSignatureNum: Int = 4,
    val timeSignatureDenom: Int = 4,
    val countInBars: Int = 0,
    val volume: Float = 1.0f,
    val sortOrder: Int = 0,
)
