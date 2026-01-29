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
                // 1. Ambil semua lagu dari tabel 'songs'
                // decodeList<Song>() otomatis mengubah JSON Supabase jadi List<Song>
                val songs = SupabaseManager.client
                    .from("songs")
                    .select(
                        columns = Columns.list(
                            "id",
                            "title",
                            "audio_url",
                            "cover_url",
                            "artist_id",
                            "canvas_url",
                            "duration_ms",
                            "telegram_audio_file_id",
                        )
                    ) {
                        order(column = "created_at", order = Order.DESCENDING)
                    }
                    .decodeList<Song>()

                if (songs.isEmpty()) {
                    _uiState.value = HomeUiState.Success(emptyList())
                    return@launch
                }

                // 2. Kumpulkan semua Artist ID yang unik
                val artistIds = songs.mapNotNull { it.artistId }
                    .filter { it.isNotBlank() }
                    .distinct()

                // 3. Ambil data Artis (Bulk Fetch)
                // Di Supabase/Postgres, kita tidak perlu 'chunking' (pecah 30-30)
                // Filter 'isIn' bisa menangani ribuan ID sekaligus dengan cepat.
                val artistsMap = if (artistIds.isNotEmpty()) {
                    SupabaseManager.client
                        .from("artists")
                        .select {
                            filter {
                                // Ambil artis yang ID-nya ada di dalam list artistIds
                                isIn("id", artistIds)
                            }
                        }
                        .decodeList<Artist>()
                        .associateBy { it.id } // Ubah jadi Map biar pencarian cepat: [id -> Artist]
                } else {
                    emptyMap()
                }

                // 4. Gabungkan Lagu dengan Artisnya
                val songsWithArtists = songs.map { song ->
                    val artist = song.artistId?.let { id -> artistsMap[id] }
                    SongWithArtist(
                        song = song,
                        artist = artist
                    )
                }

                _uiState.value = HomeUiState.Success(songsWithArtists)

            } catch (e: Exception) {
                // Log error untuk debugging
                e.printStackTrace()
                _uiState.value = HomeUiState.Error(e.message ?: "Terjadi kesalahan saat memuat data.")
            }
        }
    }
}