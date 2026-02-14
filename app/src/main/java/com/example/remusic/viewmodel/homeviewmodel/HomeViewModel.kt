package com.example.remusic.viewmodel.homeviewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.remusic.data.SupabaseManager
import com.example.remusic.data.UserManager
import com.example.remusic.data.model.Artist
import com.example.remusic.data.model.Playlist
import com.example.remusic.data.model.Song
import com.example.remusic.data.model.SongWithArtist
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.async // IMPORT ASYNC

sealed class HomeUiState {
    object Loading : HomeUiState()
    data class Success(
        val allSongsWithArtists: List<SongWithArtist>,
        val officialPlaylists: List<Playlist>,
        val recentlyPlayed: List<SongWithArtist>,
        val followedArtists: List<Artist>,
        val mostLoved: List<SongWithArtist>,
        val topTrending: List<SongWithArtist>,
        val quickPickSongs: List<SongWithArtist> // NEW: Persisted random songs
    ) : HomeUiState()
    data class Error(val message: String) : HomeUiState()
}

class HomeViewModel : ViewModel() {

    private val TAG = "HomeViewModel"
    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState

    init {
        fetchAllHomeData()
    }

    private fun fetchAllHomeData() {
        viewModelScope.launch {
            _uiState.value = HomeUiState.Loading
            try {
                Log.d(TAG, "🔄 [HOME] Fetching all home data...")
                val startTime = System.currentTimeMillis()

                // 1. Parallel Fetching using async
                val quickPickDeferred = async { fetchQuickPickSongs() }
                val recentlyPlayedDeferred = async { fetchRecentlyPlayedSongs() }
                val mostLovedDeferred = async { fetchMostLovedSongs() }
                val topTrendingDeferred = async { fetchTopTrendingSongs() }
                val officialPlaylistsDeferred = async { fetchOfficialPlaylists() }
                val followedArtistsDeferred = async { fetchFollowedArtists() }

                // 2. Await all results
                val quickPickRaw = quickPickDeferred.await()
                val recentlyPlayedRaw = recentlyPlayedDeferred.await()
                val mostLovedRaw = mostLovedDeferred.await()
                val topTrendingRaw = topTrendingDeferred.await()
                val officialPlaylists = officialPlaylistsDeferred.await()
                val followedArtists = followedArtistsDeferred.await()

                // 3. Collection all songs for Bulk Artist Fetch
                // Combine all raw songs to get unique artist IDs
                val allRawSongs = (quickPickRaw + recentlyPlayedRaw + mostLovedRaw + topTrendingRaw)
                val artistIds = allRawSongs.mapNotNull { it.artistId }
                    .filter { it.isNotBlank() }
                    .distinct()

                // 4. Bulk Fetch Artists
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

                // 5. Helper to Map Songs
                fun mapToSongWithArtist(songs: List<Song>): List<SongWithArtist> {
                    return songs.map { song ->
                        val artist = song.artistId?.let { id -> artistsMap[id] }
                        SongWithArtist(song = song, artist = artist)
                    }
                }

                // 6. Map all lists
                val quickPickSongs = mapToSongWithArtist(quickPickRaw)
                val recentlyPlayed = mapToSongWithArtist(recentlyPlayedRaw)
                val mostLoved = mapToSongWithArtist(mostLovedRaw)
                val topTrending = mapToSongWithArtist(topTrendingRaw)

                // Combine for "All Songs" (optional, for other usages)
                val allSongsWithArtists =
                    (quickPickSongs + recentlyPlayed + mostLoved + topTrending)
                        .distinctBy { it.song.id }
                        .shuffled()

                val endTime = System.currentTimeMillis()
                Log.d(TAG, "✅ [SUCCESS] Home data loaded in ${endTime - startTime}ms")

                _uiState.value = HomeUiState.Success(
                    allSongsWithArtists = allSongsWithArtists,
                    officialPlaylists = officialPlaylists,
                    recentlyPlayed = recentlyPlayed,
                    followedArtists = followedArtists,
                    mostLoved = mostLoved,
                    topTrending = topTrending,
                    quickPickSongs = quickPickSongs
                )

            } catch (e: Exception) {
                Log.e(TAG, "❌ [ERROR] Failed to load home data: ${e.message}")
                e.printStackTrace()
                _uiState.value = HomeUiState.Error(
                    e.message ?: "Terjadi kesalahan saat memuat data."
                )
            }
        }
    }

    // --- Modular Fetch Functions ---

    private suspend fun fetchQuickPickSongs(): List<Song> {
        // Ambil 50 lagu terbaru, lalu acak dan ambil 20
        return try {
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

            songs.shuffled().take(20)
        } catch (e: Exception) {
            Log.e(TAG, "⚠️ [QUICK PICK] Error: ${e.message}")
            emptyList()
        }
    }

    private suspend fun fetchRecentlyPlayedSongs(): List<Song> {
        // Placeholder: Ambil 20 lagu terbaru (karena tabel history belum ada/siap)
        return try {
            SupabaseManager.client
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
        } catch (e: Exception) {
            Log.e(TAG, "⚠️ [RECENTLY PLAYED] Error: ${e.message}")
            emptyList()
        }
    }

    private suspend fun fetchMostLovedSongs(): List<Song> {
        return try {
            SupabaseManager.client
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
        } catch (e: Exception) {
            Log.e(TAG, "⚠️ [MOST LOVED] Error: ${e.message}")
            emptyList()
        }
    }

    private suspend fun fetchTopTrendingSongs(): List<Song> {
        return try {
            SupabaseManager.client
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
        } catch (e: Exception) {
            Log.e(TAG, "⚠️ [TOP TRENDING] Error: ${e.message}")
            emptyList()
        }
    }

    private suspend fun fetchOfficialPlaylists(): List<Playlist> {
        return try {
            SupabaseManager.client
                .from("playlists")
                .select {
                    filter { eq("is_official", true) }
                    order(column = "created_at", order = Order.DESCENDING)
                    limit(10)
                }
                .decodeList<Playlist>()
        } catch (e: Exception) {
            Log.e(TAG, "⚠️ [OFFICIAL PLAYLISTS] Error: ${e.message}")
            emptyList()
        }
    }

    private suspend fun fetchFollowedArtists(): List<Artist> {
        return try {
            val currentUserId = UserManager.currentUser?.uid ?: return emptyList()

            // Get artist IDs from artist_followers
            val followerRecords = SupabaseManager.client
                .from("artist_followers")
                .select(columns = Columns.list("artist_id")) {
                    filter { eq("user_id", currentUserId) }
                    limit(20)
                }
                .decodeList<Map<String, String>>()

            val artistIds = followerRecords.mapNotNull { it["artist_id"] }

            if (artistIds.isNotEmpty()) {
                SupabaseManager.client
                    .from("artists")
                    .select {
                        filter { isIn("id", artistIds) }
                    }
                    .decodeList<Artist>()
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "⚠️ [FOLLOWED ARTISTS] Error: ${e.message}")
            emptyList()
        }
    }
}