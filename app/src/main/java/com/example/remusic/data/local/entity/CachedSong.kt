package com.example.remusic.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cached_songs")
data class CachedSong(
    @PrimaryKey
    val id: String, // Gunakan UUID dari Supabase
    val title: String,
    val artistName: String,
    val featuredArtists: List<String> = emptyList(),
    val coverUrl: String?,
    val canvasUrl: String? = null, // Video Canvas
    val lyrics: String?, // Lirik disimpan offline
    val lyricsUpdatedAt: String? = null, // Versi lirik (Timestamp dari Supabase)
    val uploaderUserId: String?,
    // --- TAMBAHAN BARU UNTUK SMART QUEUE ---
    val language: String? = null,
    val moods: List<String> = emptyList(),
    val artistId: String? = null,
    // --- KHUSUS LOGIC TELEGRAM ---
    val telegramFileId: String?, // ID asli file Telegram (buat request ulang)
    val telegramDirectUrl: String?, // URL direct (yang ada tokennya)
    val urlExpiryTime: Long = 0, // Kapan URL ini basi (Epoch timestamp)

    // --- TAMBAHAN UTK HISTORY ---
    val lastPlayedAt: Long = System.currentTimeMillis() // Untuk sorting "Recently Played"
)