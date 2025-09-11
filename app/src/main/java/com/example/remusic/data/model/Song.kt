package com.example.remusic.data.model

import android.os.Parcelable
import com.google.firebase.Timestamp
import kotlinx.parcelize.Parcelize

@Parcelize
data class Song(
    // Properti yang sudah cocok
    val id: String = "",
    val title: String = "",
    val lyrics: String = "",
    val audioUrl: String = "",

    // --- Perbaikan ---
    val coverUrl: String = "",             // Diubah dari imageUrl agar cocok
    val artistId: String = "",             // Ini adalah ID, BUKAN nama artis
    val durationMs: Long = 0,              // Gunakan Long untuk angka yang bisa besar
    val likeCount: Long = 0,
    val playCount: Long = 0,
    val moods: List<String> = emptyList(), // Array di Firestore menjadi List di Kotlin
    val createdAt: Timestamp? = null,      // Tipe data khusus untuk timestamp Firestore
    val updatedAt: Timestamp? = null,
    val uploaderUserId: String = ""
): Parcelable