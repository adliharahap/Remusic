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

    // Gunakan StateFlow untuk menampung state yang akan diobservasi oleh UI
    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState

    init {
        fetchSongsAndArtists()
    }

    private fun fetchSongsAndArtists() {
        viewModelScope.launch {
            _uiState.value = HomeUiState.Loading
            try {
                // Langkah A: Ambil semua lagu
                val songs = getSongs()
                if (songs.isEmpty()) {
                    _uiState.value = HomeUiState.Success(emptyList())
                    return@launch
                }

                // Langkah B: Ambil semua ID artis yang unik dari daftar lagu
                val artistIds = songs.map { it.artistId }.distinct().filter { it.isNotBlank() }

                // Langkah C: Ambil semua data artis berdasarkan ID dalam satu query
                val artists = getArtists(artistIds)

                // Langkah D: Gabungkan data lagu dan artis
                val songsWithArtists = songs.map { song ->
                    SongWithArtist(
                        song = song,
                        artist = artists[song.artistId] // Ambil artis dari Map
                    )
                }

                _uiState.value = HomeUiState.Success(songsWithArtists)

            } catch (e: Exception) {
                _uiState.value = HomeUiState.Error(e.localizedMessage ?: "An unknown error occurred")
            }
        }
    }

    // Fungsi suspend untuk mengambil lagu
    private suspend fun getSongs(): List<Song> {
        return db.collection("songs")
            .get()
            .await() // .await() mengubah Task menjadi hasil suspend function
            .toObjects(Song::class.java)
    }

    // Fungsi suspend untuk mengambil artis
    private suspend fun getArtists(artistIds: List<String>): Map<String, Artist> {
        if (artistIds.isEmpty()) return emptyMap()

        return db.collection("artists")
            .whereIn("id", artistIds) // Query efisien untuk mengambil banyak dokumen berdasarkan ID
            .get()
            .await()
            .toObjects(Artist::class.java)
            .associateBy { it.id } // Ubah List<Artist> menjadi Map<String, Artist> untuk pencarian cepat
    }
}
