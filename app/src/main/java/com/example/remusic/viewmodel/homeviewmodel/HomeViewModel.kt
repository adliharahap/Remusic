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

                // 1. Fetch Songs
                val songs = SupabaseManager.client
                    .from("songs")
                    .select(
                        columns = Columns.list(
                            "id", "title", "audio_url", "cover_url", "artist_id", 
                            "canvas_url", "duration_ms", "telegram_audio_file_id", 
                            "featured_artists", "play_count", "like_count", "created_at"
                        )
                    ) {
                        order(column = "created_at", order = Order.DESCENDING)
                        limit(100) // Limit untuk performa
                    }
                    .decodeList<Song>()

                Log.d(TAG, "✅ [SONGS] Fetched ${songs.size} songs")

                // 2. Fetch Official Playlists
                val officialPlaylists = SupabaseManager.client
                    .from("playlists")
                    .select {
                        filter {
                            eq("is_official", true)
                        }
                        order(column = "created_at", order = Order.DESCENDING)
                        limit(10)
                    }
                    .decodeList<Playlist>()

                Log.d(TAG, "✅ [PLAYLISTS] Fetched ${officialPlaylists.size} official playlists")

                // 3. Fetch Followed Artists (if user logged in)
                val followedArtists = try {
                    val currentUserId = UserManager.currentUser?.uid
                    if (currentUserId != null) {
                        // Get artist IDs from artist_followers
                        val followerRecords = SupabaseManager.client
                            .from("artist_followers")
                            .select(columns = Columns.list("artist_id")) {
                                filter {
                                    eq("user_id", currentUserId)
                                }
                                limit(20)
                            }
                            .decodeList<Map<String, String>>()

                        val artistIds = followerRecords.mapNotNull { it["artist_id"] }

                        if (artistIds.isNotEmpty()) {
                            SupabaseManager.client
                                .from("artists")
                                .select {
                                    filter {
                                        isIn("id", artistIds)
                                    }
                                }
                                .decodeList<Artist>()
                        } else {
                            emptyList()
                        }
                    } else {
                        emptyList()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "⚠️ [ARTISTS] Error fetching followed artists: ${e.message}")
                    emptyList()
                }

                Log.d(TAG, "✅ [ARTISTS] Fetched ${followedArtists.size} followed artists")

                // 4. Fetch Artist Details for Songs
                val artistIds = songs.mapNotNull { it.artistId }
                    .filter { it.isNotBlank() }
                    .distinct()

                val artistsMap = if (artistIds.isNotEmpty()) {
                    SupabaseManager.client
                        .from("artists")
                        .select {
                            filter {
                                isIn("id", artistIds)
                            }
                        }
                        .decodeList<Artist>()
                        .associateBy { it.id }
                } else {
                    emptyMap()
                }

                // 5. Combine Songs with Artists
                val songsWithArtists = songs.map { song ->
                    val artist = song.artistId?.let { id -> artistsMap[id] }
                    SongWithArtist(song = song, artist = artist)
                }

                // 6. Fetch Most Loved Songs (like_count > 0, limit 50)
                val mostLovedSongs = SupabaseManager.client
                    .from("songs")
                    .select(
                        columns = Columns.list(
                            "id", "title", "artist_id", "cover_url", "audio_url", 
                            "canvas_url", "duration_ms", "telegram_audio_file_id", 
                            "featured_artists", "play_count", "like_count", "created_at"
                        )
                    ) {
                        filter {
                            gt("like_count", 0) // Only songs with likes
                        }
                        order(column = "like_count", order = Order.DESCENDING)
                        limit(50)
                    }
                    .decodeList<Song>()

                val mostLoved = mostLovedSongs.map { song ->
                    val artist = artistsMap[song.artistId]
                    SongWithArtist(song = song, artist = artist)
                }

                Log.d(TAG, "✅ [MOST LOVED] Fetched ${mostLoved.size} songs with likes")

                // 7. Fetch Top Trending Songs (play_count > 0, limit 50)
                val topTrendingSongs = SupabaseManager.client
                    .from("songs")
                    .select(
                        columns = Columns.list(
                            "id", "title", "artist_id", "cover_url", "audio_url", 
                            "canvas_url", "duration_ms", "telegram_audio_file_id", 
                            "featured_artists", "play_count", "like_count", "created_at"
                        )
                    ) {
                        filter {
                            gt("play_count", 0) // Only songs that have been played
                        }
                        order(column = "play_count", order = Order.DESCENDING)
                        limit(50)
                    }
                    .decodeList<Song>()

                val topTrending = topTrendingSongs.map { song ->
                    val artist = artistsMap[song.artistId]
                    SongWithArtist(song = song, artist = artist)
                }

                Log.d(TAG, "✅ [TOP TRENDING] Fetched ${topTrending.size} songs with plays")

                // 8. Recently Played - just first 20 from recent songs
                val recentlyPlayed = songsWithArtists.take(20)

                // 9. Quick Pick - shuffle and take 20 (persisted in state)
                val quickPickSongs = songsWithArtists.shuffled().take(20)

                Log.d(TAG, "✅ [SUCCESS] All home data loaded successfully")

                _uiState.value = HomeUiState.Success(
                    allSongsWithArtists = songsWithArtists,
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

    fun refresh() {
        fetchAllHomeData()
    }
}