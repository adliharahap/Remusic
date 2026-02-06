package com.example.remusic.data.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@Parcelize
data class Song(
    @SerialName("id")
    val id: String = "",

    @SerialName("title")
    val title: String = "",

    @SerialName("lyrics")
    val lyrics: String? = null,

    @SerialName("audio_url")
    val audioUrl: String? = null,

    @SerialName("cover_url")
    val coverUrl: String? = null,

    @SerialName("artist_id")
    val artistId: String? = null,

    @SerialName("uploader_user_id")
    val uploaderUserId: String? = null,

    @SerialName("duration_ms")
    val durationMs: Long = 0,

    @SerialName("play_count")
    val playCount: Long = 0,

    @SerialName("like_count")
    val likeCount: Long = 0,

    // Menggunakan List<String> karena tipe di DB adalah ARRAY
    @SerialName("moods")
    val moods: List<String> = emptyList(),

    // --- KOLOM STREAMING & MEDIA ---

    @SerialName("canvas_url")
    val canvasUrl: String? = null,

    @SerialName("telegram_audio_file_id")
    val telegramFileId: String? = null,

    @SerialName("telegram_direct_url")
    val telegramDirectUrl: String? = null, // 🔥 BARU: Untuk URL streaming direct

    @SerialName("telegram_url_expires_at")
    val telegramUrlExpiresAt: String? = null, // 🔥 BARU: Timestamp expired link

    // --- METADATA TAMBAHAN ---

    @SerialName("language")
    val language: String? = null,

    @SerialName("featured_artists")
    val featuredArtists: List<String> = emptyList(), // 🔥 BARU: Array featured artists

    // --- TIMESTAMPS ---

    @SerialName("created_at")
    val createdAt: String? = null,

    @SerialName("updated_at")
    val updatedAt: String? = null,

    @SerialName("lyrics_updated_at")
    val lyricsUpdatedAt: String? = null
): Parcelable