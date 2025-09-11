package com.example.remusic.utils

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.text.format.DateFormat
import java.util.Date

// ✅ FIX 1: Membuat data class AudioFile yang belum ada
data class AudioFile(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val audioUrl: String, // Path ke file
    val imageUrl: String, // Path ke album art
    val duration: Int,
    val size: String,
    val addedDate: String
)

fun getAllAudioFiles(context: Context): List<AudioFile> {
    val audioList = ArrayList<AudioFile>()

    val projection = arrayOf(
        MediaStore.Audio.Media._ID,
        MediaStore.Audio.Media.TITLE,
        MediaStore.Audio.Media.ARTIST,
        MediaStore.Audio.Media.ALBUM,
        MediaStore.Audio.Media.DATA, // Path
        MediaStore.Audio.Media.DURATION,
        MediaStore.Audio.Media.DATE_ADDED,
        MediaStore.Audio.Media.SIZE,
        MediaStore.Audio.Media.ALBUM_ID
    )

    // Hanya ambil file musik (bukan rekaman suara atau notifikasi)
    val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
    val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

    val cursor = context.contentResolver.query(
        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
        projection,
        selection,
        null,
        sortOrder
    )

    cursor?.use {
        val idColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
        val titleColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
        val artistColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
        val albumColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
        val dataColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
        val durationColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
        val dateAddedColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
        val sizeColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
        val albumIdColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)

        while (it.moveToNext()) {
            val id = it.getLong(idColumn).toString()
            val title = it.getString(titleColumn) ?: "Unknown Title"
            val artist = it.getString(artistColumn) ?: "<unknown>"
            val displayArtist = if (artist == "<unknown>") "Unknown Artist" else artist
            val album = it.getString(albumColumn) ?: "Unknown Album"
            val path = it.getString(dataColumn)
            val duration = it.getInt(durationColumn)

            // Skip file audio yang terlalu pendek (misal: notifikasi)
            if (duration < 10000) continue // kurang dari 10 detik

            val dateAddedSec = it.getLong(dateAddedColumn) * 1000
            val size = it.getLong(sizeColumn)

            // Format tanggal menggunakan import yang benar
            val addedDate = DateFormat.format("yyyy-MM-dd HH:mm:ss", Date(dateAddedSec)).toString()

            // Dapatkan URI untuk album art
            val albumId = it.getLong(albumIdColumn)
            val sArtworkUri: Uri = Uri.parse("content://media/external/audio/albumart")
            val imageUri = Uri.withAppendedPath(sArtworkUri, albumId.toString()).toString()

            audioList.add(
                AudioFile(
                    id = id,
                    title = title,
                    artist = displayArtist,
                    album = album,
                    audioUrl = path,
                    imageUrl = imageUri,
                    duration = duration,
                    size = size.toString(),
                    addedDate = addedDate
                )
            )
        }
    }

    return audioList
}