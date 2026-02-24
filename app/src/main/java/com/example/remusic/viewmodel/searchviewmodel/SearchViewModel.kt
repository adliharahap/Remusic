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
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

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

            // 4. Search Songs (By Title OR Lyrics) - Run in Parallel
            val songsByQueryDeferred = viewModelScope.async {
                SupabaseManager.client
                    .from("songs")
                    .select(
                        columns = Columns.list(
                            "id", "title", "audio_url", "cover_url", "artist_id", 
                            "canvas_url", "duration_ms", "telegram_audio_file_id", "created_at", "featured_artists", "lyrics"
                        )
                    ) {
                        filter {
                            or {
                                ilike("title", "%$query%")
                                ilike("lyrics", "%$query%")
                            }
                        }
                        limit(30)
                    }
                    .decodeList<Song>()
            }

            // Await Initial Results
            val foundArtistsList = artistsDeferred.await()
            val foundPlaylistsList = playlistsDeferred.await()
            val foundUsersList = usersDeferred.await()
            val foundSongsByQueryList = songsByQueryDeferred.await()

            // 5. NEW: Search Songs by Artist ID (from found artists)
            val artistIds = foundArtistsList.map { it.id }
            val songsByArtistList = if (artistIds.isNotEmpty()) {
                SupabaseManager.client
                    .from("songs")
                    .select(
                        columns = Columns.list(
                            "id", "title", "audio_url", "cover_url", "artist_id", 
                            "canvas_url", "duration_ms", "telegram_audio_file_id", "created_at", "featured_artists", "lyrics"
                        )
                    ) {
                        filter {
                            isIn("artist_id", artistIds)
                        }
                        limit(20) // Limit per artist search
                    }
                    .decodeList<Song>()
            } else {
                emptyList()
            }

            // Merge Songs (ByQuery + ByArtist) & Remove Duplicates
            val uniqueSongs = (songsByArtistList + foundSongsByQueryList).distinctBy { it.id }

            // Update States
            _foundArtists.value = foundArtistsList
            _foundPlaylists.value = foundPlaylistsList
            _foundUsers.value = foundUsersList
            _topArtist.value = foundArtistsList.firstOrNull() // Keep top artist logic if needed for header

            // Process Songs (Fetch Artist Info for ALL unique songs)
            val allArtistIds = uniqueSongs.mapNotNull { it.artistId }.distinct()
            
            // Optimize: Reuse foundArtistsList if possible to reduce fetches
            val existingArtistsMap = foundArtistsList.associateBy { it.id }
            val missingArtistIds = allArtistIds.filter { !existingArtistsMap.containsKey(it) }

            val additionalArtistsMap = if (missingArtistIds.isNotEmpty()) {
                SupabaseManager.client
                    .from("artists")
                    .select {
                        filter { isIn("id", missingArtistIds) }
                    }
                    .decodeList<Artist>()
                    .associateBy { it.id }
            } else {
                emptyMap()
            }
            
            val fullArtistsMap = existingArtistsMap + additionalArtistsMap

            val songsWithArtists = uniqueSongs.map { song ->
                SongWithArtist(song, song.artistId?.let { fullArtistsMap[it] })
            }

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
