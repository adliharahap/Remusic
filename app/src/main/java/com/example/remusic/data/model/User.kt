package com.example.remusic.data.model

import androidx.annotation.Keep
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Keep // Prevent ProGuard from obfuscating this class in release builds
@Serializable // Wajib agar bisa dibaca dari JSON Supabase
data class User(
    // Petakan kolom 'id' di database ke variabel 'uid'
    @SerialName("id")
    val uid: String = "",

    // Petakan kolom 'display_name' (snake_case) ke variabel 'displayName' (camelCase)
    @SerialName("display_name")
    val displayName: String? = null,

    @SerialName("email")
    val email: String? = null,

    @SerialName("photo_url")
    val photoUrl: String? = null,

    @SerialName("role")
    val role: String = "listener",

    // Ubah Date ke String. Supabase mengirim format: "2025-12-25T10:00:00+00:00"
    @SerialName("created_at")
    val createdAt: String? = null
)