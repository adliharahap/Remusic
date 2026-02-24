package com.example.remusic.data.model

import androidx.annotation.Keep
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Keep
@Serializable
data class Playlist(
    @SerialName("id")
    val id: String = "",
    
    @SerialName("title")
    val title: String = "",
    
    @SerialName("description")
    val description: String? = null,
    
    @SerialName("cover_url")
    val coverUrl: String? = null,
    
    @SerialName("owner_user_id")
    val ownerUserId: String? = null,
    @SerialName("is_official")
    val isOfficial: Boolean = false,
    
    @SerialName("visibility")
    val visibility: String? = null,
    
    @SerialName("created_at")
    val createdAt: String? = null
)
