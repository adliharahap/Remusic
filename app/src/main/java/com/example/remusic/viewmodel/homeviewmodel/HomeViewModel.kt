package com.example.remusic.viewmodel.homeviewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.remusic.data.local.MusicDatabase
import com.example.remusic.data.repository.MusicRepository
import com.example.remusic.data.HomeCacheManager
import com.example.remusic.data.SupabaseManager
import com.example.remusic.data.UserManager
import com.example.remusic.data.model.Artist
import com.example.remusic.data.model.Playlist
import com.example.remusic.data.model.Song
import com.example.remusic.data.model.SongWithArtist
import com.example.remusic.utils.ConnectivityObserver
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.pages.HomePage
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.rpc
import io.github.jan.supabase.postgrest.query.filter.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed class HomeUiState {
    object Loading : HomeUiState()
    data class Success(
        val allSongsWithArtists: List<SongWithArtist>,
        val officialPlaylists: List<Playlist>,
        val recentlyPlayed: List<SongWithArtist>,
        val followedArtists: List<Artist>,
        val mostLoved: List<SongWithArtist>,
        val topTrending: List<SongWithArtist>,
        val quickPickSongs: List<SongWithArtist>,
        val isRefreshing: Boolean = false,
        val isFetchingYouTube: Boolean = false,
        val homePage: HomePage? = null,
        val communityAlbums: List<com.example.remusic.ui.components.homecomponents.CommunityAlbumData> = emptyList(),
        val activeMoodParams: String? = null
    ) : HomeUiState()
    data class Error(val message: String) : HomeUiState()
}

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val TAG = "HomeViewModel"
    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState

    private val repository: MusicRepository
    private var latestHistory: List<SongWithArtist> = emptyList()

    init {
        val database = MusicDatabase.getDatabase(application)
        repository = MusicRepository(database.musicDao(), database.localPlayHistoryDao())

        // Cek cache dan masa berlaku (misal 6 jam)
        val expirationMs = 6 * 60 * 60 * 1000L
        val cached = HomeCacheManager.load()
        val isCacheValid = HomeCacheManager.isCacheValid(expirationMs)

        if (cached != null) {
            _uiState.value = cached
            Log.d(TAG, "📦 [INIT] Cache tersedia, tampilkan dulu.")
            
            if (!isCacheValid) {
                Log.d(TAG, "🔄 [INIT] Cache kedaluwarsa. Fetching ulang semua data...")
                fetchAllHomeData()
            } else {
                Log.d(TAG, "✅ [INIT] Cache masih valid. Memuat bagian YouTube yang tidak dicache...")
                fetchUncachedYouTubeData()
            }
        } else {
            fetchAllHomeData()
        }

        viewModelScope.launch {
            repository.getRecentlyPlayedLocal().collect { history ->
                latestHistory = history
                _uiState.update { currentState ->
                    if (currentState is HomeUiState.Success) {
                        currentState.copy(recentlyPlayed = history)
                    } else currentState
                }
            }
        }
    }

    private var hasRefreshedInBackground = false

    /**
     * Dipanggil dari HomeScreen saat status koneksi berubah menjadi Available.
     * Hanya fetch ulang jika state saat ini masih kosong atau error.
     */
    fun onConnectivityRestored() {
        val current = _uiState.value
        val shouldRefetch = current is HomeUiState.Loading ||
                current is HomeUiState.Error ||
                (current is HomeUiState.Success && current.quickPickSongs.isEmpty())
        
        if (shouldRefetch) {
            Log.d(TAG, "🌐 Koneksi kembali, fetch ulang home data...")
            fetchAllHomeData()
        } else {
            Log.d(TAG, "🌐 Koneksi kembali, data sudah ada. Skip refresh untuk menjaga konsistensi UI.")
        }
    }

    private fun refreshInBackground() {
        viewModelScope.launch {
            val currentState = _uiState.value
            if (currentState is HomeUiState.Success) {
                _uiState.value = currentState.copy(isRefreshing = true)
            }
            try {
                val newState = buildHomeData()
                if (newState == null) {
                    // Reset isRefreshing jika fetch gagal
                    if (_uiState.value is HomeUiState.Success) {
                        _uiState.value = (_uiState.value as HomeUiState.Success).copy(isRefreshing = false)
                    }
                    return@launch
                }
                
                _uiState.value = newState
                HomeCacheManager.save(newState)
                
                // Trigger Sync User Data (Likes & Follows) safely
                // Kita jalankan di background, fire-and-forget
                val userId = UserManager.currentUser?.uid
                if (userId != null) {
                    repository.synchronizeUserData(userId)
                }
                
                Log.d(TAG, "✅ [BG REFRESH] Data diperbarui di background")
            } catch (e: Exception) {
                Log.w(TAG, "⚠️ [BG REFRESH] Gagal: ${e.message}")
                if (_uiState.value is HomeUiState.Success) {
                    _uiState.value = (_uiState.value as HomeUiState.Success).copy(isRefreshing = false)
                }
            }
        }
    }

    fun fetchAllHomeData(params: String? = null) {
        viewModelScope.launch {
            // Jika tidak ada cache atau mengganti mood (params != null), tampilkan Loading
            val currentState = _uiState.value
            if (currentState !is HomeUiState.Success || params != null) {
                _uiState.value = HomeUiState.Loading
            } else {
                _uiState.value = currentState.copy(isRefreshing = true)
            }
            try {
                Log.d(TAG, "🔄 [HOME] Fetching all home data (params=$params)...")
                val startTime = System.currentTimeMillis()

                val newState = buildHomeData(params)
                if (newState != null) {
                    val endTime = System.currentTimeMillis()
                    Log.d(TAG, "✅ [SUCCESS] Home data loaded in ${endTime - startTime}ms")
                    _uiState.value = newState
                    // Hanya save ke cache jika ini bukan hasil filter mood (params == null)
                    if (params == null) {
                        HomeCacheManager.save(newState)
                    }
                    
                    // Trigger Sync User Data (Likes & Follows) - Initial Load case
                    val userId = UserManager.currentUser?.uid
                    if (userId != null) {
                         // Jangan di-await agar tidak memblokir UI Home
                         launch { repository.synchronizeUserData(userId) }
                    }
                } else {
                    if (currentState !is HomeUiState.Success) {
                        _uiState.value = HomeUiState.Error("Gagal memuat data")
                    } else {
                        _uiState.value = currentState.copy(isRefreshing = false)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ [ERROR] fetchAllHomeData: ${e.message}")
                if (currentState !is HomeUiState.Success) {
                    _uiState.value = HomeUiState.Error(e.message ?: "Unknown error")
                } else {
                    _uiState.value = currentState.copy(isRefreshing = false)
                }
            }
        }
    }

    private fun fetchUncachedYouTubeData() {
        viewModelScope.launch {
            try {
                Log.d(TAG, "🔄 [HOME] Fetching Uncached YouTube Data (HomePage & Community Albums)...")
                
                _uiState.update { currentState ->
                    if (currentState is HomeUiState.Success) {
                        currentState.copy(isFetchingYouTube = true)
                    } else currentState
                }

                val homePage = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { 
                    var finalPage: com.metrolist.innertube.pages.HomePage? = null
                    var attempt = 1
                    val maxAttempts = 3
                    
                    while (attempt <= maxAttempts && finalPage == null) {
                        val firstPage = YouTube.home(params = null).getOrNull()
                        if (firstPage != null) {
                            var mergedPage: com.metrolist.innertube.pages.HomePage = firstPage
                            var continuation = firstPage.continuation
                            var loads = 0
                            while (continuation != null && loads < 5) {
                                val nextPage = YouTube.home(continuation = continuation).getOrNull()
                                if (nextPage != null) {
                                    mergedPage = mergedPage.copy(
                                        sections = mergedPage.sections + nextPage.sections,
                                        continuation = nextPage.continuation
                                    )
                                    continuation = nextPage.continuation
                                    loads++
                                } else break
                            }
                            finalPage = mergedPage
                        }
                        attempt++
                    }
                    finalPage
                }

                val communityAlbumsDeferred = homePage?.sections
                    ?.filter { it.title?.contains("Komunitas", ignoreCase = true) == true || it.title?.contains("Community", ignoreCase = true) == true }
                    ?.flatMap { it.items }
                    ?.mapNotNull { item ->
                        val (id, title, thumb) = when (item) {
                            is com.metrolist.innertube.models.PlaylistItem -> Triple(item.id, item.title, item.thumbnail ?: "")
                            is com.metrolist.innertube.models.AlbumItem -> Triple(item.playlistId, item.title, item.thumbnail)
                            is com.metrolist.innertube.models.PodcastItem -> Triple(item.id, item.title, item.thumbnail ?: "")
                            else -> null
                        } ?: return@mapNotNull null
                        
                        val type = if (item is com.metrolist.innertube.models.AlbumItem) "YOUTUBE_ALBUM" else "YOUTUBE_PLAYLIST"
                        
                        async {
                            val result = YouTube.playlist(id).getOrNull()
                            val songs = result?.songs?.mapNotNull { songItem ->
                                val song = Song(
                                    id = songItem.id,
                                    title = songItem.title,
                                    artistId = songItem.artists?.firstOrNull()?.id,
                                    coverUrl = songItem.thumbnail,
                                    durationMs = (songItem.duration ?: 0) * 1000L
                                )
                                val artist = songItem.artists?.firstOrNull()?.let { a -> Artist(id = a.id ?: "", name = a.name) }
                                SongWithArtist(song, artist)
                            } ?: emptyList()
                            
                            com.example.remusic.ui.components.homecomponents.CommunityAlbumData(
                                title = title,
                                coverUrl = thumb,
                                playlistId = id,
                                songs = songs,
                                playlistType = type
                            )
                        }
                    } ?: emptyList()

                val fetchedCommunityAlbums = kotlinx.coroutines.awaitAll(*communityAlbumsDeferred.toTypedArray()).toList()
                
                _uiState.update { currentState ->
                    if (currentState is HomeUiState.Success) {
                        currentState.copy(
                            homePage = homePage, 
                            communityAlbums = fetchedCommunityAlbums,
                            isFetchingYouTube = false
                        )
                    } else currentState
                }
                
                // Sync user data
                val userId = UserManager.currentUser?.uid
                if (userId != null) {
                    repository.synchronizeUserData(userId)
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ [HOME] Error fetchUncachedYouTubeData: ${e.message}")
                _uiState.update { currentState ->
                    if (currentState is HomeUiState.Success) {
                        currentState.copy(isFetchingYouTube = false)
                    } else currentState
                }
            }
        }
    }

    /** Fetch semua data dan return sebagai Success state, atau null jika gagal total */
    private suspend fun buildHomeData(params: String? = null): HomeUiState.Success? {
        return try {
            val quickPickDeferred = viewModelScope.async { try { fetchQuickPickSongs() } catch (e: Exception) { Log.e(TAG, "QuickPick error", e); emptyList() } }
            val mostLovedDeferred = viewModelScope.async { try { fetchMostLovedSongs() } catch (e: Exception) { Log.e(TAG, "MostLoved error", e); emptyList() } }
            val topTrendingDeferred = viewModelScope.async { try { fetchTopTrendingSongs() } catch (e: Exception) { Log.e(TAG, "TopTrending error", e); emptyList() } }
            val officialPlaylistsDeferred = viewModelScope.async { try { fetchOfficialPlaylists() } catch (e: Exception) { Log.e(TAG, "Playlists error", e); emptyList() } }
            val followedArtistsDeferred = viewModelScope.async { try { fetchFollowedArtists() } catch (e: Exception) { Log.e(TAG, "FollowedArtists error", e); emptyList() } }
            val homePageDeferred = viewModelScope.async {
                var finalPage: com.metrolist.innertube.pages.HomePage? = null
                var attempt = 1
                val maxAttempts = 3
                
                while (attempt <= maxAttempts && finalPage == null) {
                    Log.d(TAG, "🔍 [HOME] Mengambil data Innertube... (Percobaan $attempt/$maxAttempts)")
                    
                    val firstPage = YouTube.home(params = params).onFailure { 
                        Log.e(TAG, "❌ [HOME] Gagal memuat Innertube pada percobaan $attempt: ", it) 
                    }.getOrNull()
                    
                    if (firstPage != null) {
                        var mergedPage: com.metrolist.innertube.pages.HomePage = firstPage
                        var continuation = firstPage.continuation
                        var loads = 0
                        
                        // Ambil semua data semaksimal mungkin (up to 10 continuations)
                        while (continuation != null && loads < 10) {
                            val nextPage = YouTube.home(continuation = continuation).getOrNull()
                            if (nextPage != null) {
                                mergedPage = mergedPage.copy(
                                    sections = mergedPage.sections + nextPage.sections,
                                    continuation = nextPage.continuation
                                )
                                continuation = nextPage.continuation
                                loads++
                            } else {
                                break
                            }
                        }
                        
                        if (mergedPage.sections.size >= 4 || params != null) {
                            finalPage = mergedPage
                            Log.d(TAG, "✅ [HOME] Berhasil memuat Innertube dengan ${mergedPage.sections.size} seksi!")
                            
                            val hasCommunity = mergedPage.sections.any { it.title?.contains("Komunitas", ignoreCase = true) == true || it.title?.contains("Community", ignoreCase = true) == true }
                            if (hasCommunity) {
                                Log.d(TAG, "✅ [HOME] Data 'Dari Komunitas' berhasil ditemukan!")
                            } else {
                                Log.w(TAG, "⚠️ [HOME] Data 'Dari Komunitas' tidak ditemukan di batch ini.")
                            }
                            
                            mergedPage.sections.forEach {
                                Log.d(TAG, "✅ [HOME] Berhasil memuat: ${it.title ?: "Seksi Tanpa Nama"}")
                            }
                        } else {
                            Log.w(TAG, "⚠️ [HOME] Percobaan $attempt hanya mendapat ${mergedPage.sections.size} seksi. Mengulang...")
                            kotlinx.coroutines.delay(1000)
                        }
                    } else {
                        kotlinx.coroutines.delay(1500)
                    }
                    attempt++
                }
                finalPage
            }

            val quickPickRaw = quickPickDeferred.await()
            val mostLovedRaw = mostLovedDeferred.await()
            val topTrendingRaw = topTrendingDeferred.await()
            val officialPlaylists = officialPlaylistsDeferred.await()
            val followedArtists = followedArtistsDeferred.await()
            val homePage = homePageDeferred.await()

            val allRawSongs = (quickPickRaw + mostLovedRaw + topTrendingRaw)
            val uuidRegex = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
            val artistIds = allRawSongs.mapNotNull { it.artistId }
                .filter { it.isNotBlank() && it.matches(uuidRegex) }
                .distinct()

            val artistsMap = if (artistIds.isNotEmpty()) {
                SupabaseManager.client
                    .from("artists")
                    .select { filter { isIn("id", artistIds) } }
                    .decodeList<Artist>()
                    .associateBy { it.id }
            } else emptyMap()

            fun mapToSongWithArtist(songs: List<Song>): List<SongWithArtist> =
                songs.map { song ->
                    val artist = song.artistId?.let { id -> artistsMap[id] }
                    SongWithArtist(song = song, artist = artist)
                }

            val quickPickSongs = mapToSongWithArtist(quickPickRaw)
            val mostLoved = mapToSongWithArtist(mostLovedRaw)
            val topTrending = mapToSongWithArtist(topTrendingRaw)
            val allSongsWithArtists = (quickPickSongs + mostLoved + topTrending)
                .distinctBy { it.song.id }

            val communityAlbumsDeferred = homePage?.sections
                ?.filter { it.title?.contains("Komunitas", ignoreCase = true) == true || it.title?.contains("Community", ignoreCase = true) == true }
                ?.flatMap { it.items }
                ?.mapNotNull { item ->
                    val (id, title, thumb) = when (item) {
                        is com.metrolist.innertube.models.PlaylistItem -> Triple(item.id, item.title, item.thumbnail ?: "")
                        is com.metrolist.innertube.models.AlbumItem -> Triple(item.playlistId, item.title, item.thumbnail)
                        is com.metrolist.innertube.models.PodcastItem -> Triple(item.id, item.title, item.thumbnail ?: "")
                        else -> null
                    } ?: return@mapNotNull null
                    
                    val type = if (item is com.metrolist.innertube.models.AlbumItem) "YOUTUBE_ALBUM" else "YOUTUBE_PLAYLIST"
                    
                    viewModelScope.async {
                        val result = YouTube.playlist(id).getOrNull()
                        val songs = result?.songs?.mapNotNull { songItem ->
                            val song = Song(
                                id = songItem.id,
                                title = songItem.title,
                                artistId = songItem.artists?.firstOrNull()?.id,
                                coverUrl = songItem.thumbnail,
                                durationMs = (songItem.duration ?: 0) * 1000L
                            )
                            val artist = songItem.artists?.firstOrNull()?.let { a -> Artist(id = a.id ?: "", name = a.name) }
                            SongWithArtist(song, artist)
                        } ?: emptyList()
                        
                        com.example.remusic.ui.components.homecomponents.CommunityAlbumData(
                            title = title,
                            coverUrl = thumb,
                            playlistId = id,
                            songs = songs,
                            playlistType = type
                        )
                    }
                } ?: emptyList()

            val fetchedCommunityAlbums = communityAlbumsDeferred.awaitAll()

            HomeUiState.Success(
                allSongsWithArtists = allSongsWithArtists,
                officialPlaylists = officialPlaylists,
                recentlyPlayed = latestHistory,
                followedArtists = followedArtists,
                mostLoved = mostLoved,
                topTrending = topTrending,
                quickPickSongs = quickPickSongs,
                homePage = homePage,
                communityAlbums = fetchedCommunityAlbums,
                activeMoodParams = params
            )
        } catch (e: Exception) {
            Log.e(TAG, "❌ [BUILD] buildHomeData error: ${e.message}")
            null
        }
    }

    // --- Modular Fetch Functions ---

    private suspend fun fetchQuickPickSongs(): List<Song> {
        val historyIds = repository.getQuickPicks()
        val localSongs = mutableListOf<Song>()
        val db = MusicDatabase.getDatabase(getApplication())
        
        if (historyIds.isNotEmpty()) {
            historyIds.forEach { id ->
                db.musicDao().getSongById(id)?.let {
                    localSongs.add(it.toSong())
                }
            }
        }
        
        // If we don't have 20 songs, fill the rest from Supabase
        if (localSongs.size < 20) {
            val needed = 20 - localSongs.size
            Log.d(TAG, "🌐 [NETWORK] Local history has ${localSongs.size} songs. Fetching $needed Quick Picks dari RPC...")
            val moreSongs = SupabaseManager.client.postgrest.rpc(
                function = "get_random_quick_picks",
                parameters = buildJsonObject {
                    put("max_limit", needed + 20) // Fetch extra to handle duplicates/filters
                }
            ).decodeList<Song>()
            
            // Avoid duplicates
            val existingIds = localSongs.map { it.id }.toSet()
            moreSongs.forEach { s ->
                if (s.id !in existingIds && localSongs.size < 20) {
                    localSongs.add(s)
                }
            }
        }

        return localSongs.shuffled().take(20)
    }

    private suspend fun fetchRecentlyPlayedSongs(): List<Song> {
        return SupabaseManager.client
            .from("songs")
            .select(
                columns = Columns.list(
                    "id", "title", "audio_url", "cover_url", "artist_id",
                    "canvas_url", "duration_ms", "telegram_audio_file_id",
                    "featured_artists", "play_count", "like_count", "created_at",
                    "language", "moods"
                )
            ) {
                filter {
                    eq("source", "telegram")
                }
                order(column = "created_at", order = Order.DESCENDING)
                limit(20)
            }
            .decodeList<Song>()
    }

    private suspend fun fetchMostLovedSongs(): List<Song> {
        return SupabaseManager.client
            .from("songs")
            .select(
                columns = Columns.list(
                    "id", "title", "artist_id", "cover_url", "audio_url",
                    "canvas_url", "duration_ms", "telegram_audio_file_id",
                    "featured_artists", "play_count", "like_count", "created_at",
                    "language", "moods"
                )
            ) {
                filter {
                    gt("like_count", 0)
                    eq("source", "telegram")
                }
                order(column = "like_count", order = Order.DESCENDING)
                limit(50)
            }
            .decodeList<Song>()
    }

    private suspend fun fetchTopTrendingSongs(): List<Song> {
        return SupabaseManager.client
            .from("songs")
            .select(
                columns = Columns.list(
                    "id", "title", "artist_id", "cover_url", "audio_url",
                    "canvas_url", "duration_ms", "telegram_audio_file_id",
                    "featured_artists", "play_count", "like_count", "created_at",
                    "language", "moods"
                )
            ) {
                filter {
                    gt("play_count", 0)
                    eq("source", "telegram")
                }
                order(column = "play_count", order = Order.DESCENDING)
                limit(50)
            }
            .decodeList<Song>()
    }

    private suspend fun fetchOfficialPlaylists(): List<Playlist> {
        return SupabaseManager.client
            .from("playlists")
            .select {
                filter { eq("is_official", true) }
                order(column = "created_at", order = Order.DESCENDING)
                limit(20)
            }
            .decodeList<Playlist>()
    }

    private suspend fun fetchFollowedArtists(): List<Artist> {
        val currentUserId = UserManager.currentUser?.uid ?: return emptyList()

        val followerRecords = SupabaseManager.client
            .from("artist_followers")
            .select(columns = Columns.list("artist_id")) {
                filter { eq("user_id", currentUserId) }
                limit(20)
            }
            .decodeList<Map<String, String>>()

        val uuidRegex = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
        val artistIds = followerRecords.mapNotNull { it["artist_id"] }.filter { it.matches(uuidRegex) }
        return if (artistIds.isNotEmpty()) {
            SupabaseManager.client
                .from("artists")
                .select { filter { isIn("id", artistIds) } }
                .decodeList<Artist>()
        } else {
            emptyList()
        }
    }
}