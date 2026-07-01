package com.example.remusic.viewmodel.playmusic.managers

import com.example.remusic.data.local.MusicDatabase
import com.example.remusic.data.local.entity.CachedSong
import com.example.remusic.data.model.displayArtistName
import com.example.remusic.data.network.LrcLibResponse
import com.example.remusic.data.repository.MusicRepository
import com.example.remusic.ui.screen.playmusic.LyricsConfig
import com.example.remusic.viewmodel.playmusic.PlayerUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

class LyricsManager(
    private val scope: CoroutineScope,
    private val uiState: MutableStateFlow<PlayerUiState>,
    private val repository: MusicRepository,
    private val database: MusicDatabase,
    private val retryFetchAction: (String) -> Unit
) {
    fun updateLyricsConfig(config: LyricsConfig) {
        uiState.update { it.copy(lyricsConfig = config) }
    }

    fun setLyricsOffset(offset: Long) {
        uiState.update { it.copy(lyricsOffset = offset) }
    }

    fun retryFetchLyrics() {
        val currentSongId = uiState.value.currentSong?.song?.effectiveId
        if (currentSongId != null) {
            Timber.tag("DEBUG_PLAYER").d("🔄 [RETRY CLICK] User memicu retry lirik untuk: $currentSongId")
            retryFetchAction(currentSongId)
        } else {
            Timber.tag("DEBUG_PLAYER").w("⚠️ [RETRY CLICK] Gagal retry: currentSongId null")
        }
    }

    fun searchAlternativeLyrics(query: String) {
        if (uiState.value.alternativeLyricsResults.isNotEmpty()) {
            Timber.tag("DEBUG_PLAYER").d("🧠 [CACHE] Menggunakan hasil pencarian lirik dari memory.")
            uiState.update { it.copy(isSearchingLyrics = false) }
            return
        }

        scope.launch(Dispatchers.IO) {
            uiState.update { it.copy(isSearchingLyrics = true) }
            try {
                Timber.tag("DEBUG_PLAYER").d("🔍 [NETWORK] Mencari lirik alternatif: $query")
                val results = repository.lyrics.searchAlternativeLyrics(query)
                uiState.update { it.copy(alternativeLyricsResults = results, isSearchingLyrics = false) }
            } catch (e: Throwable) {
                Timber.tag("DEBUG_PLAYER").e("Search alternative lyrics failed: ${e.message}")
                uiState.update { it.copy(isSearchingLyrics = false) }
            }
        }
    }

    fun applyAlternativeLyrics(lrc: LrcLibResponse) {
        val currentSong = uiState.value.currentSong ?: return
        val songId = currentSong.song.effectiveId 
        val lyrics = lrc.syncedLyrics ?: lrc.plainLyrics ?: return
        val finalLrc = if (lrc.syncedLyrics != null) lyrics + "\n\nlyrics from lrclib (manual)" else lyrics

        scope.launch(Dispatchers.IO) {
            uiState.update { state ->
                state.currentSong?.let { current ->
                    state.copy(currentSong = current.copy(song = current.song.copy(lyrics = finalLrc)))
                } ?: state
            }
            
            try {
                val dao = database.musicDao()
                val cachedSong = CachedSong(
                    id = songId,
                    title = currentSong.song.title,
                    artistName = currentSong.displayArtistName,
                    lyrics = finalLrc,
                    source = if (songId.length == 11) "youtube" else "telegram",
                    coverUrl = currentSong.song.coverUrl,
                    uploaderUserId = currentSong.song.uploaderUserId,
                    telegramFileId = currentSong.song.telegramFileId,
                    telegramDirectUrl = currentSong.song.telegramDirectUrl,
                    youtubeId = currentSong.song.youtubeId ?: songId.takeIf { it.length == 11 }
                )
                dao.insertSong(cachedSong)
                Timber.tag("DEBUG_PLAYER").d("💾 [SQLITE] Lirik alternatif BERHASIL disimpan untuk ID: $songId")
            } catch (e: Exception) {
                Timber.tag("DEBUG_PLAYER").e("❌ [SQLITE] Gagal menyimpan lirik alternatif: ${e.message}")
            }
        }
    }
}
