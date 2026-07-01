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
import io.github.jan.supabase.postgrest.query.filter.*
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
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.SongItem

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

    private val _ytSearchResults = MutableStateFlow<List<SongItem>>(emptyList())
    val ytSearchResults: StateFlow<List<SongItem>> = _ytSearchResults.asStateFlow()

    private val _ytFoundArtists = MutableStateFlow<List<Artist>>(emptyList())
    val ytFoundArtists: StateFlow<List<Artist>> = _ytFoundArtists.asStateFlow()

    // --- COMBINED RESULTS (PRIORITIZED) ---
    private val _combinedSongs = MutableStateFlow<List<SongWithArtist>>(emptyList())
    val combinedSongs: StateFlow<List<SongWithArtist>> = _combinedSongs.asStateFlow()

    private val _combinedVideos = MutableStateFlow<List<SongWithArtist>>(emptyList())
    val combinedVideos: StateFlow<List<SongWithArtist>> = _combinedVideos.asStateFlow()

    private val _combinedArtists = MutableStateFlow<List<Artist>>(emptyList())
    val combinedArtists: StateFlow<List<Artist>> = _combinedArtists.asStateFlow()

    private val _ytIsLoading = MutableStateFlow(false)
    val ytIsLoading: StateFlow<Boolean> = _ytIsLoading.asStateFlow()

    private val _ytError = MutableStateFlow<String?>(null)
    val ytError: StateFlow<String?> = _ytError.asStateFlow()

    // --- SEARCH SUGGESTIONS ---
    private val _searchSuggestions = MutableStateFlow<List<String>>(emptyList())
    val searchSuggestions: StateFlow<List<String>> = _searchSuggestions.asStateFlow()

    private var searchJob: Job? = null
    private var suggestionsJob: Job? = null

    init {
        fetchRecentSongs()
    }

    // --- ACTIONS ---

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
        
        // Debounce Logic
        searchJob?.cancel()
        suggestionsJob?.cancel()
        if (query.isBlank()) {
            _searchResults.value = emptyList()
            _topArtist.value = null
            _ytSearchResults.value = emptyList()
            _ytFoundArtists.value = emptyList()
            _combinedSongs.value = emptyList()
            _combinedVideos.value = emptyList()
            _combinedArtists.value = emptyList()
            _searchSuggestions.value = emptyList()
            return
        }

        // Fetch suggestions fast (150ms debounce)
        suggestionsJob = viewModelScope.launch {
            delay(150)
            fetchSearchSuggestions(query)
        }

        searchJob = viewModelScope.launch {
            delay(300)
            // Lakukan pencarian lokal dan YouTube secara bersamaan
            launch { performSearch(query) }
            launch { searchYouTube(query) }
        }
    }

    private fun fetchSearchSuggestions(query: String) {
        viewModelScope.launch {
            try {
                val result = YouTube.searchSuggestions(query).getOrNull()
                _searchSuggestions.value = result?.queries?.take(8) ?: emptyList()
            } catch (e: Exception) {
                // Suggestions are non-critical, silently ignore
                android.util.Log.w("SearchVM", "⚠️ searchSuggestions failed: ${e.message}")
            }
        }
    }

    fun searchYouTube(query: String) {
        viewModelScope.launch {
            _ytIsLoading.value = true
            _ytError.value = null
            // Reset YT search results
            _ytSearchResults.value = emptyList()
            _ytFoundArtists.value = emptyList()

            try {
                android.util.Log.d("SearchVM", "🎵 [YT SEARCH] query: $query")
                
                // Fetch Songs, Videos, and Artists in parallel
                val songsDeferred = async { YouTube.search(query, YouTube.SearchFilter.FILTER_SONG).getOrNull() }
                val videosDeferred = async { YouTube.search(query, YouTube.SearchFilter.FILTER_VIDEO).getOrNull() }
                val artistsDeferred = async { YouTube.search(query, YouTube.SearchFilter.FILTER_ARTIST).getOrNull() }

                val songsResult = songsDeferred.await()
                val videosResult = videosDeferred.await()
                val artistsResult = artistsDeferred.await()

                val songs = songsResult?.items?.filterIsInstance<SongItem>() ?: emptyList()
                val videos = videosResult?.items?.filterIsInstance<SongItem>() ?: emptyList()
                
                // Combine songs and videos, prioritizing songs
                val combinedYtSongs = (songs + videos).distinctBy { it.id }

                val ytArtists = artistsResult?.items?.filterIsInstance<com.metrolist.innertube.models.ArtistItem>()?.map { item ->
                    Artist(
                        id = item.id,
                        name = item.title,
                        photoUrl = item.thumbnail,
                        source = "youtube"
                    )
                } ?: emptyList()

                android.util.Log.d("SearchVM", "✅ [YT SEARCH] Found ${songs.size} songs, ${videos.size} videos, and ${ytArtists.size} artists")
                
                _ytSearchResults.value = combinedYtSongs
                _ytFoundArtists.value = ytArtists
                
                // Update combined states after YT results are in
                updateCombinedResults(query)
            } catch (e: Exception) {
                android.util.Log.e("SearchVM", "❌ [YT SEARCH ERROR] ${e.message}")
                _ytError.value = "Gagal mencari di YouTube: ${e.message}"
            } finally {
                _ytIsLoading.value = false
            }
        }
    }

    private fun updateCombinedResults(query: String) {
        val q = query.lowercase().trim()
        
        // --- 1. COMBINE ARTISTS ---
        val localArtists = _foundArtists.value
        val youtubeArtists = _ytFoundArtists.value
        
        val allArtists = (localArtists + youtubeArtists).distinctBy { it.id }
        
        val sortedArtists = allArtists.sortedByDescending { artist ->
            val name = artist.name.lowercase()
            when {
                name == q -> 100
                name.startsWith(q) -> 80
                name.contains(q) -> 60
                else -> 40
            }
        }
        
        _combinedArtists.value = sortedArtists
        _topArtist.value = sortedArtists.firstOrNull()

        // --- 2. COMBINE SONGS ---
        val localSongs = _searchResults.value
        val youtubeSongs = _ytSearchResults.value.map { buildYouTubeSongWithArtist(it) }
        
        val allSongs = (localSongs + youtubeSongs)
        

        
        // Sort by accuracy AND source priority
        val sortedSongs = allSongs.sortedWith(
            compareByDescending<SongWithArtist> { songWithArtist ->
                val title = songWithArtist.song.title.lowercase()
                when {
                    title == q -> 100
                    title.startsWith(q) -> 80
                    title.contains(q) -> 60
                    else -> 40
                }
            }.thenByDescending { songWithArtist ->
                // Prioritas: 3 (Uploader), 2 (YouTube Music), 1 (YouTube Video)
                when {
                    songWithArtist.song.source != "youtube" && songWithArtist.song.source != "youtube_video" -> 3
                    songWithArtist.song.source == "youtube" -> 2
                    else -> 1
                }
            }
        )
        
        _combinedSongs.value = sortedSongs.filter { it.song.source != "youtube_video" }
        _combinedVideos.value = sortedSongs.filter { it.song.source == "youtube_video" }
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
                val trimmedQuery = query.trim()
                val isUuid = trimmedQuery.matches(Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"))
                
                SupabaseManager.client
                    .from("users")
                    .select {
                        filter {
                            if (isUuid) {
                                or {
                                    ilike("display_name", "%$query%")
                                    eq("id", trimmedQuery)
                                }
                            } else {
                                ilike("display_name", "%$query%")
                            }
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

            // Convert RPC results & FILTER OUT YouTube Shadow Entries
            // Shadow entries (source=youtube) tidak boleh muncul di pencarian lokal
            // Lagu uploader asli HARUS punya uploader_user_id, telegram_id, atau audio_url
            val songsWithArtists = rpcResults.map { it.toSongWithArtist() }
                .filter { 
                    val s = it.song
                    s.uploaderUserId != null || s.telegramFileId != null || !s.audioUrl.isNullOrBlank()
                }
            
            android.util.Log.d("SearchVM", "Final Mapped Songs: ${songsWithArtists.size}")

            _searchResults.value = songsWithArtists
            
            // Initial combined results from local only
            updateCombinedResults(query)

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
                        filter {
                            or {
                                eq("source", "telegram")
                                eq("source", "uploader")
                                // Cara aman filter NOT NULL tanpa keyword 'not'
                                neq("uploader_user_id", "00000000-0000-0000-0000-000000000000")
                            }
                        }
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
                featuredArtists = song.featuredArtists.ifEmpty { existingSong?.featuredArtists ?: emptyList() },
                
                coverUrl = song.coverUrl ?: existingSong?.coverUrl,
                canvasUrl = song.canvasUrl ?: existingSong?.canvasUrl,
                lyrics = existingSong?.lyrics ?: song.lyrics, // 🔥 KRITIKAL: Jangan timpa lirik lokal!
                lyricsUpdatedAt = existingSong?.lyricsUpdatedAt ?: song.lyricsUpdatedAt,
                uploaderUserId = song.uploaderUserId ?: existingSong?.uploaderUserId,
                telegramFileId = song.telegramFileId ?: existingSong?.telegramFileId,
                telegramDirectUrl = existingSong?.telegramDirectUrl,
                urlExpiryTime = existingSong?.urlExpiryTime ?: 0L,
                lastPlayedAt = System.currentTimeMillis(),
                artistId = song.artistId ?: existingSong?.artistId,
                source = song.source ?: existingSong?.source
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

    // --- Konversi SongItem YouTube → SongWithArtist untuk diputar ---
    fun buildYouTubeSongWithArtist(songItem: SongItem): com.example.remusic.data.model.SongWithArtist {
        val artistName = songItem.artists.firstOrNull()?.name ?: "YouTube Music"
        val featured = songItem.artists.drop(1).map { it.name }
        
        val song = com.example.remusic.data.model.Song(
            id = songItem.id,
            title = songItem.title,
            coverUrl = songItem.thumbnail,
            source = if (songItem.isVideoSong) "youtube_video" else "youtube",
            artistId = songItem.artists.firstOrNull()?.id,
            featuredArtists = listOf(artistName) + featured, // Simpan artist utama di list agar terbawa saat sync
            telegramFileId = null,
            audioUrl = null             // kosong — akan di-resolve MusicService
        )
        val artist = com.example.remusic.data.model.Artist(
            id = songItem.artists.firstOrNull()?.id ?: "",
            name = artistName
        )

        return com.example.remusic.data.model.SongWithArtist(song, artist)
    }
}
