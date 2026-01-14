package com.example.remusic.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cached_artists")
data class CachedArtist(
    @PrimaryKey
    val id: String,
    val name: String,
    val bio: String?,
    val photoUrl: String?
)