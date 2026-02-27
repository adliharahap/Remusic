package com.example.remusic.data.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@Parcelize
data class Notification(
    @SerialName("id") val id: String,
    @SerialName("user_id") val userId: String? = null, // NULL means it's a global broadcast
    @SerialName("title") val title: String,
    @SerialName("message") val message: String,
    @SerialName("type") val type: String, // 'system', 'promo', 'song_request', 'welcome'
    @SerialName("reference_id") val referenceId: String? = null,
    @SerialName("image_url") val imageUrl: String? = null,
    @SerialName("is_read") val isReadSupabase: Boolean? = false,
    @SerialName("created_at") val createdAt: String? = null,
    
    // UI Only field (not in DB)
    @kotlinx.serialization.Transient var isReadLocally: Boolean = false
) : Parcelable {
    // Helper to determine if it's read based on whether it's global or personal
    val isRead: Boolean
        get() = if (userId == null) isReadLocally else (isReadSupabase ?: false)
}
