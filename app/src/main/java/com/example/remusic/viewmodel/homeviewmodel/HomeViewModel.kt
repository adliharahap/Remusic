package com.example.remusic.viewmodel.homeviewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.remusic.data.model.Artist
import com.example.remusic.data.model.Song
import com.example.remusic.data.model.SongWithArtist
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await


sealed class HomeUiState {
    object Loading : HomeUiState()
    data class Success(val songsWithArtists: List<SongWithArtist>) : HomeUiState()
    data class Error(val message: String) : HomeUiState()
}

class HomeViewModel : ViewModel() {

    private val db = Firebase.firestore
    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState

    // --- OPTIMISASI 1: BUAT CACHE UNTUK ARTIS ---
    // Cache ini akan menyimpan data artis yang sudah di-fetch.
    // Key: artistId (String), Value: Objek Artist.
    // Cache akan hidup selama ViewModel ini ada (selama layar aktif).
    private val artistCache = mutableMapOf<String, Artist>()

    init {
        fetchSongsAndArtists()
    }

    private fun fetchSongsAndArtists() {
        viewModelScope.launch {
            _uiState.value = HomeUiState.Loading
            try {
                val songs = getSongs()
                if (songs.isEmpty()) {
                    _uiState.value = HomeUiState.Success(emptyList())
                    return@launch
                }

                val artistIds = songs.map { it.artistId }.distinct().filter { it.isNotBlank() }

                // Panggilan ke getArtists sekarang sudah aman dan menggunakan cache
                val artists = getArtists(artistIds)

                val songsWithArtists = songs.map { song ->
                    SongWithArtist(
                        song = song,
                        artist = artists[song.artistId]
                    )
                }

                _uiState.value = HomeUiState.Success(songsWithArtists)

            } catch (e: Exception) {
                _uiState.value = HomeUiState.Error(e.localizedMessage ?: "An unknown error occurred")
            }
        }
    }

    private suspend fun getSongs(): List<Song> {
        return db.collection("songs")
            .get()
            .await()
            .toObjects(Song::class.java)
    }

    // --- PERBAIKAN: FUNGSI getArtists DENGAN LOGIKA CACHE DAN CHUNKING ---
    private suspend fun getArtists(artistIds: List<String>): Map<String, Artist> {
        if (artistIds.isEmpty()) return emptyMap()

        // 1. Pisahkan ID: mana yang sudah ada di cache, dan mana yang perlu di-fetch dari Firebase
        val (idsFromCache, idsToFetch) = artistIds.distinct().partition { artistCache.containsKey(it) }

        // 2. Ambil data yang sudah ada langsung dari cache
        val cachedArtists = idsFromCache.mapNotNull { artistCache[it] }.associateBy { it.id }.toMutableMap()

        // 3. Hanya fetch ID yang BELUM ada di cache
        if (idsToFetch.isNotEmpty()) {
            // PERBAIKAN ERROR 'IN': Pecah list `idsToFetch` menjadi beberapa bagian (chunks)
            // masing-masing maksimal 30 item.
            idsToFetch.chunked(30).forEach { chunk ->
                val fetchedArtists = db.collection("artists")
                    .whereIn("id", chunk) // Query sekarang aman, hanya berisi maksimal 30 ID
                    .get()
                    .await()
                    .toObjects(Artist::class.java)

                // 4. Simpan hasil baru ke cache DAN ke map hasil gabungan
                fetchedArtists.forEach { artist ->
                    artistCache[artist.id] = artist       // Simpan ke cache untuk penggunaan berikutnya
                    cachedArtists[artist.id] = artist   // Tambahkan ke hasil yang akan dikembalikan
                }
            }
        }

        // 5. Kembalikan map gabungan dari cache dan hasil fetch baru
        return cachedArtists
    }
}