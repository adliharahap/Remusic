package com.example.remusic.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.remusic.data.local.entity.CachedArtist
import com.example.remusic.data.local.entity.CachedSong
import com.example.remusic.data.local.entity.LikedSong
import kotlinx.coroutines.flow.Flow

@Dao
interface MusicDao {

    // --- LAGU & CACHE ---

    // Insert atau Update lagu (kalau ID sama, timpa data lama)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSong(song: CachedSong)

    // Ambil 1 lagu untuk dicek expired-nya
    @Query("SELECT * FROM cached_songs WHERE id = :songId")
    suspend fun getSongById(songId: String): CachedSong?

    // Ambil History (Lagu yang pernah diplay, urut dari yang terbaru)
    @Query("SELECT * FROM cached_songs ORDER BY lastPlayedAt DESC LIMIT 50")
    fun getRecentSongs(): Flow<List<CachedSong>>

    // --- LIKED SONGS ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLikedSong(likedSong: LikedSong)

    @Query("DELETE FROM liked_songs WHERE songId = :songId")
    suspend fun deleteLikedSong(songId: String)

    // Cek apakah lagu ini di-like?
    @Query("SELECT EXISTS(SELECT 1 FROM liked_songs WHERE songId = :songId)")
    fun isSongLiked(songId: String): Flow<Boolean>

    // Ambil semua lagu yang di-like (Join dengan tabel lagu biar dapat judul/cover)
    @Query("""
        SELECT * FROM cached_songs 
        INNER JOIN liked_songs ON cached_songs.id = liked_songs.songId 
        ORDER BY liked_songs.likedAt DESC
    """)
    fun getAllLikedSongs(): Flow<List<CachedSong>>
}