package com.example.remusic.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "playback_queue")
data class PlaybackQueueEntity(
        @PrimaryKey val songId: String, // ID Lagu
        val listOrder: Int, // Urutan dalam playlist
        val artistName: String = "Unknown", // Nama Artist
        val playlistName: String = "Unknown Playlist" // Nama Playlist
)
