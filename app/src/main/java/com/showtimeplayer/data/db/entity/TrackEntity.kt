package com.showtimeplayer.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tracks")
data class TrackEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uri: String,
    val title: String?,
    val artist: String?,
    val album: String?,
    val durationMs: Long,
    val format: String?,
    val albumArtUri: String?,
    val dateAdded: Long,
    val trackNumber: Int = 0,
    val discNumber: Int = 0,
)
