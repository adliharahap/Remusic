package com.example.remusic.data.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable // <--- WAJIB UNTUK SUPABASE
@Parcelize
data class Song(
    @SerialName("id")
    val id: String = "",

    @SerialName("title")
    val title: String = "",

    @SerialName("lyrics")
    val lyrics: String? = null, // Pakai nullable biar aman kalau kosong

    @SerialName("audio_url") // Nama kolom di DB: audio_url
    val audioUrl: String = "",

    @SerialName("cover_url") // Nama kolom di DB: cover_url
    val coverUrl: String? = null,

    @SerialName("artist_id") // Nama kolom di DB: artist_id
    val artistId: String = "",

    @SerialName("duration_ms") // Nama kolom di DB: duration_ms
    val durationMs: Long = 0,

    @SerialName("like_count") // Nama kolom di DB: like_count
    val likeCount: Long = 0,

    @SerialName("play_count") // Nama kolom di DB: play_count
    val playCount: Long = 0,

    @SerialName("moods")
    val moods: List<String> = emptyList(),

    // Supabase kirim tanggal sebagai String (ISO 8601)
    @SerialName("created_at")
    val createdAt: String? = null,

    @SerialName("updated_at")
    val updatedAt: String? = null,

    @SerialName("uploader_user_id") // Nama kolom di DB: uploader_user_id
    val uploaderUserId: String? = ""
): Parcelable