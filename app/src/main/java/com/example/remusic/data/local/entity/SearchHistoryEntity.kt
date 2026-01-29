package com.example.remusic.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "search_history",
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
    @PrimaryKey
    val songId: String, // Kita gunakan songId sebagai PK karena 1 lagu cuma boleh muncul 1x di history (paling baru)
    val searchedAt: Long = System.currentTimeMillis()
)
