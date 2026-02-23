package com.example.remusic.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SongRequestInsert(
    @SerialName("requester_id") val requesterId: String,
    @SerialName("song_title") val songTitle: String,
    @SerialName("artist_name") val artistName: String,
    @SerialName("note") val note: String? = null,
    @SerialName("reference_url") val referenceUrl: String? = null,
    @SerialName("preview_url") val previewUrl: String? = null,
    @SerialName("cover_url") val coverUrl: String? = null,
    @SerialName("artist_photo_url") val artistPhotoUrl: String? = null
)
