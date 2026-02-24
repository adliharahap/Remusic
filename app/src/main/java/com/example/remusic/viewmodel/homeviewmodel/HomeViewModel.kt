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
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
        val quickPickSongs: List<SongWithArtist>
    ) : HomeUiState()
    data class Error(val message: String) : HomeUiState()
}

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val TAG = "HomeViewModel"
    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState

    // Manual Dependency Injection (Simple version)
    private val repository: MusicRepository

    init {
        val database = MusicDatabase.getDatabase(application)
        repository = MusicRepository(database.musicDao())

        // Langsung load dari cache dulu agar UI tidak kosong
        val cached = HomeCacheManager.load()

        if (cached != null) {
            _uiState.value = cached
            Log.d(TAG, "📦 [INIT] Cache tersedia, tampilkan dulu.")
        }
        // Tetap fetch online (jika ada internet)
        fetchAllHomeData()
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
            // Smart Retry: Hanya refresh background SEKALI per sesi aplikasi saat koneksi kembali
            if (!hasRefreshedInBackground) {
                Log.d(TAG, "🌐 Koneksi kembali, refresh data di background...")
                hasRefreshedInBackground = true
                refreshInBackground()
            } else {
                Log.d(TAG, "🌐 Koneksi kembali, tapi sudah pernah refresh background. Skip.")
            }
        }
    }

    private fun refreshInBackground() {
        viewModelScope.launch {
            try {
                val newState = buildHomeData() ?: return@launch
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
            }
        }
    }

    fun fetchAllHomeData() {
        viewModelScope.launch {
            // Jika tidak ada cache, tampilkan Loading
            if (_uiState.value !is HomeUiState.Success) {
                _uiState.value = HomeUiState.Loading
            }
            try {
                Log.d(TAG, "🔄 [HOME] Fetching all home data...")
                val startTime = System.currentTimeMillis()

                val newState = buildHomeData()
                if (newState != null) {
                    val endTime = System.currentTimeMillis()
                    Log.d(TAG, "✅ [SUCCESS] Home data loaded in ${endTime - startTime}ms")
                    _uiState.value = newState
                    HomeCacheManager.save(newState) // Simpan ke cache
                    
                    // Trigger Sync User Data (Likes & Follows) - Initial Load case
                    val userId = UserManager.currentUser?.uid
                    if (userId != null) {
                         // Jangan di-await agar tidak memblokir UI Home
                         launch { repository.synchronizeUserData(userId) }
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "❌ [ERROR] Failed to load home data: ${e.message}")
                // Jika gagal dan ada cache, tetap tampilkan cache (jangan tampilkan Error)
                if (_uiState.value !is HomeUiState.Success) {
                    _uiState.value = HomeUiState.Error(e.message ?: "Terjadi kesalahan saat memuat data.")
                }
            }
        }
    }

    /** Fetch semua data dan return sebagai Success state, atau null jika gagal total */
    private suspend fun buildHomeData(): HomeUiState.Success? {
        return try {
            val quickPickDeferred = viewModelScope.async { fetchQuickPickSongs() }
            val recentlyPlayedDeferred = viewModelScope.async { fetchRecentlyPlayedSongs() }
            val mostLovedDeferred = viewModelScope.async { fetchMostLovedSongs() }
            val topTrendingDeferred = viewModelScope.async { fetchTopTrendingSongs() }
            val officialPlaylistsDeferred = viewModelScope.async { fetchOfficialPlaylists() }
            val followedArtistsDeferred = viewModelScope.async { fetchFollowedArtists() }

            val quickPickRaw = quickPickDeferred.await()
            val recentlyPlayedRaw = recentlyPlayedDeferred.await()
            val mostLovedRaw = mostLovedDeferred.await()
            val topTrendingRaw = topTrendingDeferred.await()
            val officialPlaylists = officialPlaylistsDeferred.await()
            val followedArtists = followedArtistsDeferred.await()

            val allRawSongs = (quickPickRaw + recentlyPlayedRaw + mostLovedRaw + topTrendingRaw)
            val artistIds = allRawSongs.mapNotNull { it.artistId }
                .filter { it.isNotBlank() }
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
            val recentlyPlayed = mapToSongWithArtist(recentlyPlayedRaw)
            val mostLoved = mapToSongWithArtist(mostLovedRaw)
            val topTrending = mapToSongWithArtist(topTrendingRaw)
            val allSongsWithArtists = (quickPickSongs + recentlyPlayed + mostLoved + topTrending)
                .distinctBy { it.song.id }
                .shuffled()

            HomeUiState.Success(
                allSongsWithArtists = allSongsWithArtists,
                officialPlaylists = officialPlaylists,
                recentlyPlayed = recentlyPlayed,
                followedArtists = followedArtists,
                mostLoved = mostLoved,
                topTrending = topTrending,
                quickPickSongs = quickPickSongs
            )
        } catch (e: Exception) {
            Log.e(TAG, "❌ [BUILD] buildHomeData error: ${e.message}")
            null
        }
    }

    // --- Modular Fetch Functions ---

    private suspend fun fetchQuickPickSongs(): List<Song> {
        val songs = SupabaseManager.client
            .from("songs")
            .select(
                columns = Columns.list(
                    "id", "title", "audio_url", "cover_url", "artist_id",
                    "canvas_url", "duration_ms", "telegram_audio_file_id",
                    "featured_artists", "play_count", "like_count", "created_at",
                    "language", "moods"
                )
            ) {
                order(column = "created_at", order = Order.DESCENDING)
                limit(50)
            }
            .decodeList<Song>()
        return songs.shuffled().take(20)
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
                filter { gt("like_count", 0) }
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
                filter { gt("play_count", 0) }
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

        val artistIds = followerRecords.mapNotNull { it["artist_id"] }
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