package com.showtimeplayer.data.scanner

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.provider.DocumentsContract
import com.showtimeplayer.data.db.entity.TrackEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

class MediaStoreScanner(private val context: Context) {

    fun scanLocalAudio(folderUris: Set<String>): Flow<List<TrackEntity>> = flow {
        if (folderUris.isEmpty()) {
            emit(emptyList())
            return@flow
        }

        val paths = folderUris.mapNotNull { treeUriToPath(it) }
        if (paths.isEmpty()) {
            emit(emptyList())
            return@flow
        }

        val trackList = mutableListOf<TrackEntity>()
        var lastEmittedCount = 0
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.DATA,
        )

        val pathClauses = paths.joinToString(" OR ") {
            "${MediaStore.Audio.Media.DATA} LIKE ?"
        }
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ($pathClauses)"
        val selectionArgs = paths.map { "$it%" }.toTypedArray()

        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            "${MediaStore.Audio.Media.TITLE} ASC",
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val trackCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val contentUri = ContentUris.withAppendedId(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    id,
                ).toString()

                val albumId = cursor.getLong(albumIdCol)
                val albumArtUri = ContentUris.withAppendedId(
                    Uri.parse("content://media/external/audio/albumart"),
                    albumId,
                ).toString()

                val trackRaw = cursor.getInt(trackCol)
                val trackNumber = trackRaw % 10000
                val discNumber = trackRaw / 10000

                trackList.add(
                    TrackEntity(
                        uri = contentUri,
                        title = cursor.getString(titleCol),
                        artist = cursor.getString(artistCol),
                        album = cursor.getString(albumCol),
                        durationMs = cursor.getLong(durationCol),
                        format = "Audio",
                        albumArtUri = albumArtUri,
                        dateAdded = System.currentTimeMillis(),
                        trackNumber = trackNumber,
                        discNumber = discNumber,
                    ),
                )

                if (trackList.size % 50 == 0) {
                    emit(trackList.subList(lastEmittedCount, trackList.size).toList())
                    lastEmittedCount = trackList.size
                }
            }
        }
        if (lastEmittedCount < trackList.size) {
            emit(trackList.subList(lastEmittedCount, trackList.size).toList())
        }
    }.flowOn(Dispatchers.IO)

    private fun treeUriToPath(treeUri: String): String? {
        val uri = Uri.parse(treeUri)
        val docId = DocumentsContract.getTreeDocumentId(uri) ?: return null

        // Internal storage (primary)
        if (docId.startsWith("primary:")) {
            val subPath = docId.removePrefix("primary:")
            return if (subPath.isEmpty()) {
                Environment.getExternalStorageDirectory().absolutePath
            } else {
                "${Environment.getExternalStorageDirectory().absolutePath}/$subPath"
            }
        }

        // External SD card / USB storage (e.g. "F123-4567:Music")
        val colonIndex = docId.indexOf(':')
        if (colonIndex > 0) {
            val volumeId = docId.substring(0, colonIndex)
            val subPath = docId.substring(colonIndex + 1)
            val volumePath = "/storage/$volumeId"
            return if (subPath.isEmpty()) {
                volumePath
            } else {
                "$volumePath/$subPath"
            }
        }

        return null
    }
}
