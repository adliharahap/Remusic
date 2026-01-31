package com.example.remusic.data.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class SongWithArtist(
    val song: Song,
    val artist: Artist? // Bisa null kalau data artis tidak ditemukan
): Parcelable

val SongWithArtist.displayArtistName: String
    get() {
        // 1. Nama Artis Utama (Default "Unknown" jika null)
        val mainName = artist?.name ?: "Unknown Artist"

        // 2. List Featured Artists (Dari tabel Songs)
        val featuredNames = song.featuredArtists // Ini List<String>

        // 3. Jika tidak ada featured, langsung return main name
        if (featuredNames.isEmpty()) {
            return mainName
        }

        // 4. Jika ada, gabungkan: "Main, Feat1, Feat2"
        return "$mainName, ${featuredNames.joinToString(", ")}"
    }