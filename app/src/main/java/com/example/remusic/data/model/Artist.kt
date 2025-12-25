package com.example.remusic.data.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable // <--- WAJIB UNTUK SUPABASE
@Parcelize
data class Artist(
    @SerialName("id")
    val id: String = "",

    @SerialName("name")
    val name: String = "",

    @SerialName("photo_url") // Nama kolom di DB: photo_url
    val photoUrl: String? = null,

    @SerialName("description")
    val description: String? = null,

    @SerialName("normalized_name") // Nama kolom di DB: normalized_name
    val normalizedName: String? = null,

    @SerialName("created_by") // Nama kolom di DB: created_by
    val createdBy: String? = null,

    @SerialName("created_at")
    val createdAt: String? = null,

    @SerialName("updated_at")
    val updatedAt: String? = null
): Parcelable