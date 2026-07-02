package com.example.remusic.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.remusic.data.local.entity.LocalPlayHistory

@Dao
interface LocalPlayHistoryDao {

    @Query("SELECT * FROM local_play_history WHERE songId = :songId LIMIT 1")
    suspend fun getHistoryById(songId: String): LocalPlayHistory?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(history: LocalPlayHistory)

    // Delete rows exceeding the limit of 40, keeping the 40 most recently played
    @Query("""
        DELETE FROM local_play_history 
        WHERE songId NOT IN (
            SELECT songId FROM local_play_history 
            ORDER BY lastPlayedAt DESC 
            LIMIT 40
        )
    """)
    suspend fun enforceLimit()

    // Get the top 10 most played songs
    @Query("SELECT * FROM local_play_history ORDER BY playCount DESC LIMIT 10")
    suspend fun getTopPlayed(): List<LocalPlayHistory>

    // Get up to 10 random songs from the rest of the history (excluding the top 10)
    @Query("""
        SELECT * FROM local_play_history 
        WHERE songId NOT IN (
            SELECT songId FROM local_play_history ORDER BY playCount DESC LIMIT 10
        )
        ORDER BY RANDOM() LIMIT 10
    """)
    suspend fun getRandomHistory(): List<LocalPlayHistory>
}
