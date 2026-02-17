package com.example.remusic.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ArtistDetails(
    val id: String,
    val name: String,
    val description: String? = null,
    
    @SerialName("photo_url")
    val photoUrl: String? = null,
    
    val followerCount: Long = 0,
    val totalPlays: Long = 0,
    val totalSongs: Int = 0,
    val isFollowed: Boolean = false // User specific
)
