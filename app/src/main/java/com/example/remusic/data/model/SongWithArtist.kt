package com.example.remusic.data.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

@Serializable
@Parcelize
data class SongWithArtist(
    val song: Song,
    val artist: Artist?, // Bisa null kalau data artis tidak ditemukan
    val addedAt: String? = null // Timestamp when added to playlist/likes
): Parcelable

val SongWithArtist.displayArtistName: String
    get() {
        // 1. Coba ambil nama dari objek Artist (jika ada relasi di DB)
        val mainName = artist?.name

        // 2. Jika tidak ada objek Artist, coba ambil dari featuredArtists (biasanya shadow entry YouTube simpan di sini)
        val fallbackName = if (mainName == null) song.featuredArtists.firstOrNull() else null
        
        // Final name untuk artist utama
        val artistTitle = mainName ?: fallbackName ?: "Unknown Artist"

        // 3. Ambil featured artists lainnya
        // Kita filter agar nama artis utama tidak muncul lagi di list featured
        val otherFeatured = song.featuredArtists.filter { it.trim().lowercase() != artistTitle.trim().lowercase() }
        
        if (otherFeatured.isEmpty()) {
            return artistTitle
        }

        return "$artistTitle, ${otherFeatured.joinToString(", ")}"
    }