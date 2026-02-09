package com.example.remusic.viewmodel.homeviewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.remusic.data.SupabaseManager
import com.example.remusic.data.model.Artist
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
    data class Success(val songsWithArtists: List<SongWithArtist>) : HomeUiState()
    data class Error(val message: String) : HomeUiState()
}

class HomeViewModel : ViewModel() {

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState

    init {
        fetchSongsAndArtists()
    }

    private fun fetchSongsAndArtists() {
        viewModelScope.launch {
            _uiState.value = HomeUiState.Loading
            try {
                // 1. Ambil data lagu
                val songs = SupabaseManager.client
                    .from("songs")
                    .select(
                        columns = Columns.list("id", "title", "audio_url", "cover_url", "artist_id", "canvas_url", "duration_ms", "telegram_audio_file_id", "featured_artists")
                    ) {
                        order(column = "created_at", order = Order.DESCENDING)
                    }
                    .decodeList<Song>()

                if (songs.isEmpty()) {
                    _uiState.value = HomeUiState.Success(emptyList())
                    return@launch
                }

                // 2. Kumpulkan ID Artis Utama
                val artistIds = songs.mapNotNull { it.artistId }
                    .filter { it.isNotBlank() }
                    .distinct()

                // 3. Ambil data Artis Utama
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

                // 4. Gabungkan
                // (Kita tidak menggabungkan string di sini agar data mentah tetap terjaga)
                val songsWithArtists = songs.map { song ->
                    val artist = song.artistId?.let { id -> artistsMap[id] }
                    SongWithArtist(
                        song = song,
                        artist = artist
                    )
                }

                _uiState.value = HomeUiState.Success(songsWithArtists)

            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.value = HomeUiState.Error(e.message ?: "Terjadi kesalahan saat memuat data.")
            }
        }
    }
}