package com.example.remusic.data.model

// User.kt
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

data class User(
    val uid: String = "",
    val displayName: String? = null,
    val email: String? = null,
    val photoUrl: String? = null,
    val role: String = "listener", // Nilai default untuk role
    @ServerTimestamp // Otomatis mengisi waktu server saat dibuat
    val createdAt: Date? = null
)