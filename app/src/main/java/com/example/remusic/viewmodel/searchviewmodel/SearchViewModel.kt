package com.example.remusic.viewmodel.searchviewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.remusic.data.SupabaseManager
import com.example.remusic.data.local.MusicDatabase
import com.example.remusic.data.local.entity.CachedSong
import com.example.remusic.data.local.entity.SearchHistoryEntity
import com.example.remusic.data.model.Artist
import com.example.remusic.data.model.Song
import com.example.remusic.data.model.SongWithArtist
import com.example.remusic.data.model.Playlist
import com.example.remusic.data.model.User
import com.example.remusic.data.UserManager
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.rpc
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import com.example.remusic.data.model.SongWithArtistRpcResult
import io.github.jan.supabase.postgrest.postgrest

class SearchViewModel(application: Application) : AndroidViewModel(application) {

    private val musicDao = MusicDatabase.getDatabase(application).musicDao()

    // --- STATE ---
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<SongWithArtist>>(emptyList())
    val searchResults: StateFlow<List<SongWithArtist>> = _searchResults.asStateFlow()

    private val _recentSongs = MutableStateFlow<List<SongWithArtist>>(emptyList())
    val recentSongs: StateFlow<List<SongWithArtist>> = _recentSongs.asStateFlow()

    // --- NEW STATES ---
    private val _foundArtists = MutableStateFlow<List<Artist>>(emptyList())
    val foundArtists: StateFlow<List<Artist>> = _foundArtists.asStateFlow()

    private val _foundPlaylists = MutableStateFlow<List<Playlist>>(emptyList())
    val foundPlaylists: StateFlow<List<Playlist>> = _foundPlaylists.asStateFlow()

    private val _foundUsers = MutableStateFlow<List<User>>(emptyList())
    val foundUsers: StateFlow<List<User>> = _foundUsers.asStateFlow()

    // Deprecated? Keeping simple topArtist for now, but UI might prefer list
    private val _topArtist = MutableStateFlow<Artist?>(null)
    val topArtist: StateFlow<Artist?> = _topArtist.asStateFlow()

    // History dari Room (Flow dikonversi ke StateFlow di UI atau collect langsung)
    val searchHistory = musicDao.getSearchHistory()

    private var searchJob: Job? = null

    init {
        fetchRecentSongs()
    }

    // --- ACTIONS ---

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
        
        // Debounce Logic (1000ms)
        searchJob?.cancel()
        if (query.isBlank()) {
            _searchResults.value = emptyList()
            _topArtist.value = null
            return
        }

        searchJob = viewModelScope.launch {
            delay(300) // Reduced delay for faster results
            performSearch(query)
        }
    }

    private suspend fun performSearch(query: String) {
        try {
            // Reset state
            _topArtist.value = null
            _searchResults.value = emptyList()
            _foundArtists.value = emptyList()
            _foundPlaylists.value = emptyList()
            _foundUsers.value = emptyList()

            val currentUserId = UserManager.currentUser?.uid ?: ""

            // 1. Search Artists (ByName)
            val artistsDeferred = viewModelScope.async {
                SupabaseManager.client
                    .from("artists")
                    .select {
                        filter {
                            ilike("name", "%$query%")
                        }
                        limit(5) // Keep limits reasonable for initial fetch
                    }
                    .decodeList<Artist>()
            }

            // 2. Search Playlists (ByTitle)
            val playlistsDeferred = viewModelScope.async {
                SupabaseManager.client
                    .from("playlists")
                    .select {
                        filter {
                            ilike("title", "%$query%")
                            or {
                                eq("visibility", "public")
                                eq("is_official", true)
                            }
                        }
                        limit(20)
                    }
                    .decodeList<Playlist>()
            }
            
            // 3. Search Users (ByName) - Exclude Self
            val usersDeferred = viewModelScope.async {
                SupabaseManager.client
                    .from("users")
                    .select {
                        filter {
                            ilike("display_name", "%$query%")
                            neq("id", currentUserId)
                            // eq("role", "uploader") // Optional: if want to restrict to uploader role
                        }
                        limit(5)
                    }
                    .decodeList<User>()
            }

            // 4. Smart Search Songs (RPC Call)
            val smartSongsDeferred = viewModelScope.async {
                val cleanQuery = query.trim()
                android.util.Log.d("SearchVM", "Executing RPC search_songs_smart with query: '$cleanQuery'")
                try {
                    val result = SupabaseManager.client.postgrest.rpc(
                        function = "search_songs_smart",
                        parameters = buildJsonObject {
                            put("search_query", cleanQuery)
                            put("max_limit", 30)
                        }
                    ).decodeList<SongWithArtistRpcResult>()
                    android.util.Log.d("SearchVM", "RPC returned ${result.size} songs")
                    result
                } catch (e: Exception) {
                    android.util.Log.e("SearchVM", "RPC Exception: ${e.message}", e)
                    emptyList()
                }
            }

            // Await Initial Results
            val foundArtistsList = artistsDeferred.await()
            val foundPlaylistsList = playlistsDeferred.await()
            val foundUsersList = usersDeferred.await()
            val rpcResults = smartSongsDeferred.await()
            
            android.util.Log.d("SearchVM", "Found Artists: ${foundArtistsList.size}, Playlists: ${foundPlaylistsList.size}, RPC Songs: ${rpcResults.size}")

            // Update States
            _foundArtists.value = foundArtistsList
            _foundPlaylists.value = foundPlaylistsList
            _foundUsers.value = foundUsersList
            _topArtist.value = foundArtistsList.firstOrNull() // Keep top artist logic if needed for header

            // Convert RPC results
            val songsWithArtists = rpcResults.map { it.toSongWithArtist() }
            
            android.util.Log.d("SearchVM", "Final Mapped Songs: ${songsWithArtists.size}")

            _searchResults.value = songsWithArtists

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun fetchRecentSongs() {
        viewModelScope.launch {
            try {
                // Ambil 40 lagu terbaru
                val songs = SupabaseManager.client
                    .from("songs")
                    .select(
                        columns = Columns.list(
                            "id", "title", "audio_url", "cover_url", "artist_id", 
                            "canvas_url", "duration_ms", "telegram_audio_file_id", "created_at", "featured_artists"
                        )
                    ) {
                        order("created_at", Order.DESCENDING)
                        limit(10)
                    }
                    .decodeList<Song>()

                // Fetch Artist info
                val artistIds = songs.mapNotNull { it.artistId }.distinct()
                val artistsMap = if (artistIds.isNotEmpty()) {
                    SupabaseManager.client
                        .from("artists")
                        .select {
                            filter { isIn("id", artistIds) }
                        }
                        .decodeList<Artist>()
                        .associateBy { it.id }
                } else {
                    emptyMap()
                }

                val songsWithArtists = songs.map { song ->
                    SongWithArtist(song, song.artistId?.let { artistsMap[it] })
                }

                _recentSongs.value = songsWithArtists

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun onSongPlayed(songWithArtist: SongWithArtist) {
        viewModelScope.launch {
            val song = songWithArtist.song
            val artist = songWithArtist.artist
            
            // --- 🛑 DEBUG LOG START (DATA MASUK) 🛑 ---
            android.util.Log.e("DEBUG_DATA", "========================================")
            android.util.Log.e("DEBUG_DATA", "1. [SAVE] Mencoba menyimpan lagu: ${song.title}")
            android.util.Log.d("DEBUG_DATA", "   -> ID Lagu: ${song.id}")
            android.util.Log.d("DEBUG_DATA", "   -> Artist ID (Source Song): ${song.artistId}")
            android.util.Log.d("DEBUG_DATA", "   -> Artist Name (Source Obj): ${artist?.name}")
            
            // Cek List Featured
            val featList = song.featuredArtists
            android.util.Log.d("DEBUG_DATA", "   -> Featured List (Raw): $featList")
            android.util.Log.d("DEBUG_DATA", "   -> Featured Size: ${featList.size}")
            
            if (featList.isEmpty()) {
                 android.util.Log.w("DEBUG_DATA", "   ⚠️ WARNING: Featured Artist KOSONG dari server/model!")
            }
            // --- 🛑 DEBUG LOG END 🛑 ---

            // ... Logic existingSong ...
            val existingSong = musicDao.getSongById(song.id)

            val cachedSong = CachedSong(
                id = song.id,
                title = song.title,
                artistName = artist?.name ?: "Unknown Artist",
                
                // Pasang log di sini untuk memastikan assignment
                featuredArtists = song.featuredArtists,
                
                coverUrl = song.coverUrl,
                canvasUrl = song.canvasUrl,
                lyrics = song.lyrics,
                uploaderUserId = song.uploaderUserId,
                telegramFileId = song.telegramFileId,
                telegramDirectUrl = existingSong?.telegramDirectUrl,
                urlExpiryTime = existingSong?.urlExpiryTime ?: 0L,
                lastPlayedAt = System.currentTimeMillis(),
                artistId = song.artistId,
            )

            // --- 🛑 DEBUG LOG START (DATA KELUAR KE DB) 🛑 ---
            android.util.Log.d("DEBUG_DATA", "2. [ENTITY] Object CachedSong terbentuk:")
            android.util.Log.d("DEBUG_DATA", "   -> DB Artist ID: ${cachedSong.artistId}")
            android.util.Log.d("DEBUG_DATA", "   -> DB Featured: ${cachedSong.featuredArtists}")
            // --- 🛑 DEBUG LOG END 🛑 ---

            musicDao.insertSong(cachedSong)
            musicDao.insertSearchHistory(SearchHistoryEntity(songId = song.id))
        }
    }
}
