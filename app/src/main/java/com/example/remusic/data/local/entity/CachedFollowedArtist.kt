package com.example.remusic.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cached_followed_artists")
data class CachedFollowedArtist(
    @PrimaryKey
    val artistId: String,
    val followedAt: Long = System.currentTimeMillis()
)
