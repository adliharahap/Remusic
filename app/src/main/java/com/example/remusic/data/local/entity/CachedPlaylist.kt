package com.example.remusic.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cached_playlists")
data class CachedPlaylist(
    @PrimaryKey val id: String,
    val title: String,
    val description: String?,
    val coverUrl: String?,
    val ownerUserId: String?,
    val isOfficial: Boolean,
    val visibility: String?,
    val createdAt: String?,
    val cachedAt: Long = System.currentTimeMillis()
)
