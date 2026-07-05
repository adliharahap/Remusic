package com.example.remusic.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LyricsHub(
    @SerialName("id") val id: String,
    @SerialName("title") val title: String? = null,
    @SerialName("artist") val artist: String? = null,
    @SerialName("lyrics") val lyrics: String? = null,
    @SerialName("source") val source: String? = null
)
