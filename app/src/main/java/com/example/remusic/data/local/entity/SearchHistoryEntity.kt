package com.example.remusic.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "search_history",
    primaryKeys = ["songId", "userId"],
    foreignKeys = [
        ForeignKey(
            entity = CachedSong::class,
            parentColumns = ["id"],
            childColumns = ["songId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class SearchHistoryEntity(
    val songId: String,
    val userId: String, // ID dari Supabase user
    val searchedAt: Long = System.currentTimeMillis()
)
