package com.example.remusic.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.remusic.data.local.entity.LocalPlayHistory
import kotlinx.coroutines.flow.Flow

@Dao
interface LocalPlayHistoryDao {

    @Query("SELECT * FROM local_play_history WHERE songId = :songId LIMIT 1")
    suspend fun getHistoryById(songId: String): LocalPlayHistory?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(history: LocalPlayHistory)

    // Delete rows exceeding the limit of 50, keeping the 50 most recently played
    @Query("""
        DELETE FROM local_play_history 
        WHERE songId NOT IN (
            SELECT songId FROM local_play_history 
            ORDER BY lastPlayedAt DESC 
            LIMIT 50
        )
    """)
    suspend fun enforceLimit()

    @Query("SELECT * FROM local_play_history ORDER BY lastPlayedAt DESC LIMIT 50")
    fun getRecentHistory(): Flow<List<LocalPlayHistory>>

    // Get the top 10 most played songs (excluding offline songs)
    @Query("SELECT * FROM local_play_history WHERE songId NOT LIKE 'offline_%' ORDER BY playCount DESC LIMIT 10")
    suspend fun getTopPlayed(): List<LocalPlayHistory>

    // Get up to 10 random songs from the rest of the history (excluding the top 10 and offline songs)
    @Query("""
        SELECT * FROM local_play_history 
        WHERE songId NOT LIKE 'offline_%'
        AND songId NOT IN (
            SELECT songId FROM local_play_history
            WHERE songId NOT LIKE 'offline_%'
            ORDER BY playCount DESC LIMIT 10
        )
        ORDER BY RANDOM() LIMIT 10
    """)
    suspend fun getRandomHistory(): List<LocalPlayHistory>

    @Query("DELETE FROM local_play_history")
    suspend fun clearAll()
}
