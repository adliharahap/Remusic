package com.example.remusic.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "local_play_history")
data class LocalPlayHistory(
    @PrimaryKey val songId: String,
    val playCount: Int = 1,
    val lastPlayedAt: Long = System.currentTimeMillis()
)
