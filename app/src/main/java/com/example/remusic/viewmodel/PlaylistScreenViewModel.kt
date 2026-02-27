package com.example.remusic.viewmodel

import android.app.Application
import android.util.Log
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.remusic.data.UserManager
import com.example.remusic.data.local.MusicDatabase
import com.example.remusic.data.repository.MusicRepository
import com.example.remusic.ui.screen.FilterType
import com.example.remusic.ui.screen.PlaylistItem
import com.example.remusic.ui.screen.PlaylistPrivacy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.example.remusic.data.SupabaseManager
import com.example.remusic.data.local.entity.CachedFollowedArtist
import com.example.remusic.data.local.entity.CachedSong
import com.example.remusic.data.model.Playlist
import com.example.remusic.data.model.Song
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.serialization.json.JsonObject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.room.withTransaction

class PlaylistScreenViewModel(application: Application) : AndroidViewModel(application) {

    private val TAG = "PlaylistScreenVM"

    private val _playlistsAndArtists = MutableStateFlow<List<PlaylistItem>>(emptyList())
    val playlistsAndArtists: StateFlow<List<PlaylistItem>> = _playlistsAndArtists.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val repository: MusicRepository
    private var dbJob: Job? = null
    private var isDataLoaded = false

    init {
        val database = MusicDatabase.getDatabase(application)
        repository = MusicRepository(database.musicDao())
        startSessionObservation()
    }

    private fun startSessionObservation() {
        viewModelScope.launch {
            snapshotFlow { UserManager.currentUser }
                .distinctUntilChanged()
                .collect { user ->
                    dbJob?.cancel()
                    if (user != null) {
                        Log.d(TAG, "👤 [SESSION] User ready: ${user.uid}. Starting sync and observation...")
                        loadData() // Trigger Sync
                        dbJob = viewModelScope.launch {
                            observeDatabase(user.uid)
                        }
                    } else {
                        Log.d(TAG, "👤 [SESSION] User is null. Clearing UI data.")
                        _playlistsAndArtists.value = emptyList()
                    }
                }
        }
    }

    private suspend fun observeDatabase(userId: String) {
        val musicDao = MusicDatabase.getDatabase(getApplication()).musicDao()
        
        // Combine flows from Room
        combine(
            musicDao.getUserPlaylists(userId).distinctUntilChanged(),
            musicDao.getAllFollowedArtists().distinctUntilChanged()
        ) { cachedPlaylists, cachedArtists ->
            Pair(cachedPlaylists, cachedArtists)
        }.collect { (cachedPlaylists, cachedArtists) ->
                Log.d(TAG, "📦 [DB OBSERVE] Received ${cachedPlaylists.size} playlists and ${cachedArtists.size} artists from Room.")
                
                // Map CachedPlaylist to Playlist UI model
                val playlistItems = cachedPlaylists.map { it.toPlaylist() }.map { playlist ->
                    val visibilityString = playlist.visibility?.lowercase() ?: "private"
                    val privacy = when (visibilityString) {
                        "public" -> PlaylistPrivacy.PUBLIC
                        "friends" -> PlaylistPrivacy.FRIENDS
                        else -> PlaylistPrivacy.PRIVATE
                    }

                    val ownerName = if (playlist.ownerUserId == userId) {
                        UserManager.currentUser?.displayName ?: "Unknown Owner"
                    } else {
                        "System" // Or fetch owner name if needed, but mostly they are user's playlists
                    }

                    PlaylistItem(
                        id = playlist.id,
                        title = playlist.title,
                        subtitle = "Playlist • $ownerName",
                        imageUrl = playlist.coverUrl ?: "https://community.spotify.com/t5/image/serverpage/image-id/25294i2836BD1C1A31BDF2?v=v2",
                        type = FilterType.PLAYLIST,
                        privacy = privacy
                    )
                }

                // For artists, we might need to fetch full details if not fully cached
                // For now, use cached ID info and placeholder or available details
                val artistIds = cachedArtists.map { it.artistId }
                val artists = if (artistIds.isNotEmpty()) {
                    // This is a suspend call, so we do it in this coroutine
                    repository.fetchArtistsByIds(artistIds)
                } else emptyList()

                val artistItems = artists.map { artist ->
                    PlaylistItem(
                        id = artist.id,
                        title = artist.name,
                        subtitle = "Artist",
                        imageUrl = artist.photoUrl ?: "https://community.spotify.com/t5/image/serverpage/image-id/25294i2836BD1C1A31BDF2?v=v2",
                        type = FilterType.ARTIST,
                        privacy = PlaylistPrivacy.PUBLIC
                    )
                }

                _playlistsAndArtists.value = playlistItems + artistItems
                Log.d(TAG, "✅ [UI UPDATE] StateFlow updated with ${playlistItems.size + artistItems.size} items.")
            }
    }


    fun loadData(forceRefresh: Boolean = false) {
        val currentUser = UserManager.currentUser
        if (currentUser == null) {
            Log.e(TAG, "User not logged in, cannot load playlists")
            return
        }

        if (isDataLoaded && !forceRefresh) {
             Log.d(TAG, "Data already synced for session, skipping sync.")
             return
        }

        viewModelScope.launch {
            _isLoading.value = true
            isDataLoaded = true
            Log.d(TAG, "� loadData() triggered sync for user: ${currentUser.uid}")

            try {
                // Now loadData only triggers the SYNC process
                // Parallelize the syncing to be faster
                val syncPlaylistsJob = launch { syncPlaylists(currentUser.uid) }
                val syncArtistsJob = launch { syncFollowedArtists(currentUser.uid) }
                syncPlaylistsJob.join()
                syncArtistsJob.join()
            } catch (e: Exception) {
                Log.e(TAG, "Error during data synchronization", e)
            } finally {
                // We keep a small delay to ensure loading state is visible if transition is too fast
                _isLoading.value = false
            }
        }
    }

    private suspend fun syncPlaylists(userId: String) {
        try {
            val db = MusicDatabase.getDatabase(getApplication())
            val musicDao = db.musicDao()
            val remotePlaylists = SupabaseManager.client
                .from("playlists")
                .select {
                    filter { 
                        eq("owner_user_id", userId) 
                        eq("is_official", false)
                    }
                }
                .decodeList<Playlist>()
            
            db.withTransaction {
                musicDao.clearUserPlaylists(userId)
                musicDao.insertPlaylists(remotePlaylists.map { it.toCachedPlaylist() })
            }
            Log.d(TAG, "🔄 [SYNC] Synced ${remotePlaylists.size} playlists from Supabase.")
        } catch (e: Exception) {
            Log.e(TAG, "⚠️ [SYNC ERROR] Playlists: ${e.message}")
        }
    }

    private suspend fun syncFollowedArtists(userId: String) {
        try {
            val db = MusicDatabase.getDatabase(getApplication())
            val musicDao = db.musicDao()
            val remoteFollows = SupabaseManager.client
                .from("artist_followers")
                .select(columns = Columns.list("artist_id")) {
                    filter { eq("user_id", userId) }
                }
                .decodeList<JsonObject>()
            
            val remoteArtistIds = remoteFollows.map { it["artist_id"].toString().replace("\"", "") }
            
            db.withTransaction {
                musicDao.clearFollowedArtists()
                remoteArtistIds.forEach { id ->
                    musicDao.insertFollowedArtist(CachedFollowedArtist(artistId = id))
                }
            }
            Log.d(TAG, "🔄 [SYNC] Synced ${remoteArtistIds.size} followed artists.")
        } catch (e: Exception) {
            Log.e(TAG, "⚠️ [SYNC ERROR] Artist sync failed: ${e.message}")
        }
    }

    // Removing the old internal methods as they are replaced by sync + observe
    // getUserPlaylistsInternal and getFollowedArtistsInternal are no longer needed


    // --- MAPPING HELPERS ---

    private fun com.example.remusic.data.local.entity.CachedPlaylist.toPlaylist(): Playlist {
        return Playlist(
            id = this.id,
            title = this.title,
            description = this.description,
            coverUrl = this.coverUrl,
            ownerUserId = this.ownerUserId,
            isOfficial = this.isOfficial,
            visibility = this.visibility,
            createdAt = this.createdAt
        )
    }

    private fun Playlist.toCachedPlaylist(): com.example.remusic.data.local.entity.CachedPlaylist {
        return com.example.remusic.data.local.entity.CachedPlaylist(
            id = this.id,
            title = this.title,
            description = this.description,
            coverUrl = this.coverUrl,
            ownerUserId = this.ownerUserId,
            isOfficial = this.isOfficial,
            visibility = this.visibility,
            createdAt = this.createdAt
        )
    }

    private fun CachedSong.toSong(): Song {
        return Song(
            id = this.id,
            title = this.title,
            lyrics = this.lyrics,
            coverUrl = this.coverUrl,
            canvasUrl = this.canvasUrl,
            uploaderUserId = this.uploaderUserId,
            artistId = this.artistId,
            telegramFileId = this.telegramFileId,
            telegramDirectUrl = this.telegramDirectUrl,
            lyricsUpdatedAt = this.lyricsUpdatedAt,
            language = this.language,
            moods = this.moods,
            featuredArtists = this.featuredArtists
        )
    }
}
