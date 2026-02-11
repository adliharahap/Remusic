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
            delay(1000) // Tunggu 1 detik sebelum request
            performSearch(query)
        }
    }

    private suspend fun performSearch(query: String) {
        try {
            // Reset state
            _topArtist.value = null
            _searchResults.value = emptyList()

            // Parallel Execution using async
            val artistDeferred = viewModelScope.async {
                SupabaseManager.client
                    .from("artists")
                    .select {
                        filter {
                            ilike("name", "%$query%")
                        }
                        limit(1)
                    }
                    .decodeList<Artist>()
                    .firstOrNull()
            }

            val songsByTitleDeferred = viewModelScope.async {
                SupabaseManager.client
                    .from("songs")
                    .select(
                        columns = Columns.list(
                            "id", "title", "audio_url", "cover_url", "artist_id", 
                            "canvas_url", "duration_ms", "telegram_audio_file_id", "created_at", "featured_artists"
                        )
                    ) {
                        filter {
                            ilike("title", "%$query%")
                        }
                        limit(20)
                    }
                    .decodeList<Song>()
            }

            val topArtistFound = artistDeferred.await()
            val songsByTitle = songsByTitleDeferred.await()

            _topArtist.value = topArtistFound

            // If Artist found, fetch their top 10 songs
            val artistSongs = if (topArtistFound != null) {
                SupabaseManager.client
                    .from("songs")
                    .select(
                        columns = Columns.list(
                            "id", "title", "audio_url", "cover_url", "artist_id", 
                            "canvas_url", "duration_ms", "telegram_audio_file_id", "created_at", "featured_artists"
                        )
                    ) {
                        filter {
                            eq("artist_id", topArtistFound.id)
                        }
                        limit(10)
                    }
                    .decodeList<Song>()
            } else {
                emptyList()
            }

            // Combine songs (Artist songs first, then Title matches, remove duplicates)
            val combinedSongs = (artistSongs + songsByTitle).distinctBy { it.id }

            // Fetch Artist info for ALL songs
            val artistIds = combinedSongs.mapNotNull { it.artistId }.distinct()
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

            val songsWithArtists = combinedSongs.map { song ->
                SongWithArtist(song, song.artistId?.let { artistsMap[it] })
            }

            _searchResults.value = songsWithArtists

        } catch (e: Exception) {
            e.printStackTrace()
            // Handle error (bisa tambah state Error di UI)
        }
    }

    private fun fetchRecentSongs() {
        viewModelScope.launch {
            try {
                // Ambil 10 lagu terbaru
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
