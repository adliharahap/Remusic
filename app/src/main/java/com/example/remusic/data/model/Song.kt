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
    val audioUrl: String? = null, // Di SQL nullable, jadi disini sebaiknya nullable

    @SerialName("cover_url")
    val coverUrl: String? = null,

    @SerialName("artist_id")
    val artistId: String? = null, // UBAH KE NULLABLE biar aman

    @SerialName("duration_ms")
    val durationMs: Long = 0,

    @SerialName("play_count")
    val playCount: Long = 0,

    @SerialName("like_count")
    val likeCount: Long = 0,

    @SerialName("moods")
    val moods: List<String> = emptyList(),

    // --- KOLOM BARU SESUAI SCHEMA ---

    @SerialName("canvas_url")
    val canvasUrl: String? = null,

    @SerialName("telegram_audio_file_id")
    val telegramFileId: String? = null, // INI KUNCI UTAMA STREAMING KITA

    @SerialName("language")
    val language: String? = null,

    // -------------------------------

    @SerialName("created_at")
    val createdAt: String? = null,

    @SerialName("updated_at")
    val updatedAt: String? = null,

    @SerialName("uploader_user_id")
    val uploaderUserId: String? = null
): Parcelable