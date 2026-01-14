package com.example.remusic.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "liked_songs")
data class LikedSong(
    @PrimaryKey
    val songId: String, // Foreign Key ke CachedSong sebenarnya, tapi manual aja biar simpel
    val likedAt: Long = System.currentTimeMillis()
)