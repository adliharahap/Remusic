package com.example.remusic.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.remusic.data.local.entity.CachedArtist
import com.example.remusic.data.local.entity.CachedSong
import com.example.remusic.data.local.entity.LikedSong
import com.example.remusic.data.local.entity.CachedFollowedArtist
import com.example.remusic.data.local.entity.PlaybackQueueEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MusicDao {

    // --- LAGU & CACHE ---

    // Insert atau Update lagu (kalau ID sama, timpa data lama)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSong(song: CachedSong)

    // Partial Update: URL & Expiry (Audio Fetch)
    @Query("UPDATE cached_songs SET telegramDirectUrl = :url, urlExpiryTime = :expiry, lastPlayedAt = :lastPlayed WHERE id = :id")
    suspend fun updateSongUrl(id: String, url: String, expiry: Long, lastPlayed: Long)

    // Partial Update: Details (Lyrics Fetch)
    @Query("UPDATE cached_songs SET title = :title, lyrics = :lyrics, lyricsUpdatedAt = :lyricsUpdatedAt, coverUrl = :cover, canvasUrl = :canvas, uploaderUserId = :uploaderId, language = :language, moods = :moods, artistId = :artistId, featuredArtists = :featuredArtists WHERE id = :id")
    suspend fun updateSongDetails(
        id: String, 
        title: String, 
        lyrics: String?, 
        lyricsUpdatedAt: String?, 
        cover: String?,
        canvas: String?, // Tambahan baru
        uploaderId: String?,
        language: String?,
        moods: List<String>,
        artistId: String?,
        featuredArtists: List<String>
    )

    // Ambil 1 lagu untuk dicek expired-nya
    @Query("SELECT * FROM cached_songs WHERE id = :songId")
    suspend fun getSongById(songId: String): CachedSong?

    // Ambil History (Lagu yang pernah diplay, urut dari yang terbaru)
    @Query("SELECT * FROM cached_songs ORDER BY lastPlayedAt DESC LIMIT 50")
    fun getRecentSongs(): Flow<List<CachedSong>>

    // Helper untuk Smart Queue (Ambil ID saja)
    @Query("SELECT id FROM cached_songs ORDER BY lastPlayedAt DESC LIMIT :limit")
    suspend fun getRecentlyPlayedIds(limit: Int): List<String>

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

    // --- FOLLOWED ARTISTS ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFollowedArtist(artist: CachedFollowedArtist)

    @Query("DELETE FROM cached_followed_artists WHERE artistId = :artistId")
    suspend fun deleteFollowedArtist(artistId: String)

    @Query("DELETE FROM cached_followed_artists")
    suspend fun clearFollowedArtists()

    @Query("SELECT EXISTS(SELECT 1 FROM cached_followed_artists WHERE artistId = :artistId)")
    fun isArtistFollowed(artistId: String): Flow<Boolean>

    @Query("SELECT * FROM cached_followed_artists")
    fun getAllFollowedArtists(): Flow<List<CachedFollowedArtist>>

    // --- SEARCH HISTORY ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSearchHistory(history: com.example.remusic.data.local.entity.SearchHistoryEntity)

    // Ambil History Pencarian (Join dengan tabel lagu)
    @Query("""
        SELECT * FROM cached_songs 
        INNER JOIN search_history ON cached_songs.id = search_history.songId 
        ORDER BY search_history.searchedAt DESC
        LIMIT 20
    """)
    fun getSearchHistory(): Flow<List<CachedSong>>
    // --- PLAYBACK QUEUE ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaybackQueue(queue: List<PlaybackQueueEntity>)

    @Query("DELETE FROM playback_queue")
    suspend fun clearPlaybackQueue()

    @Query("SELECT * FROM playback_queue ORDER BY listOrder ASC")
    suspend fun getPlaybackQueue(): List<PlaybackQueueEntity>

    // --- USER PLAYLISTS CACHE ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylists(playlists: List<com.example.remusic.data.local.entity.CachedPlaylist>)

    @Query("SELECT * FROM cached_playlists WHERE ownerUserId = :userId ORDER BY cachedAt DESC")
    fun getUserPlaylists(userId: String): Flow<List<com.example.remusic.data.local.entity.CachedPlaylist>>

    @Query("DELETE FROM cached_playlists WHERE ownerUserId = :userId")
    suspend fun clearUserPlaylists(userId: String)
}