package com.showtimeplayer.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "metronome_layers",
    foreignKeys = [
        ForeignKey(
            entity = PresetEntity::class,
            parentColumns = ["id"],
            childColumns = ["presetId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["presetId"])],
)
data class MetronomeLayer(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val presetId: Long,
    val name: String,
    val enabled: Boolean = true,
    val sortOrder: Int = 0,
)
