package com.example.remusic.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AppUpdate(
    @SerialName("id")
    val id: String = "",
    
    @SerialName("version_code")
    val versionCode: Int = 0,
    
    @SerialName("version_name")
    val versionName: String = "",
    
    @SerialName("release_notes")
    val releaseNotes: String? = null,
    
    @SerialName("download_url")
    val downloadUrl: String = "",
    
    @SerialName("is_mandatory")
    val isMandatory: Boolean = false,
    
    @SerialName("created_at")
    val createdAt: String? = null
)
