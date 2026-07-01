package com.example.remusic.viewmodel.playmusic

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.remusic.data.model.LyricLine
import com.example.remusic.data.model.parseLrc
import com.example.remusic.data.preferences.UserPreferencesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LyricsViewModel(application: Application) : AndroidViewModel(application) {

    private val _lyrics = MutableStateFlow<List<LyricLine>>(emptyList())
    private val userPreferencesRepository = UserPreferencesRepository(getApplication())
    val lyrics = _lyrics.asStateFlow()

    // posisi playback aktual (ms)
    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition = _currentPosition.asStateFlow()

    private val _lyricsOffset = MutableStateFlow(0L)
    val lyricsOffset = _lyricsOffset.asStateFlow()

    // Cari index terakhir di `lyrics` yang timestamp <= posisi sekarang
    // DEBUG FIX: Tambah offset 150ms agar lirik muncul sedikit lebih cepat (kompensasi lag debug build)
    val activeLyricIndex = combine(lyrics, currentPosition, lyricsOffset) { list, pos, offset ->
        if (list.isEmpty()) return@combine -1
        
        // OFFSET: +50ms (internal lag compensation) + manual user offset
        val adjustedPos = pos + 50 + offset
        
        var idx = -1
        for (i in list.indices) {
            val ts = list[i].timestamp
            if (ts != -1L && ts <= adjustedPos) {
                idx = i
            } else if (ts != -1L && ts > adjustedPos) {
                break
            }
        }
        idx
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), -1)

    fun loadLyrics(lrcString: String) {
        val parsed = parseLrc(lrcString)
        _lyrics.value = parsed
    }

    // dipanggil dari UI tiap kali currentPosition berubah
    fun updatePlaybackPosition(position: Long, offset: Long = 0L) {
        _currentPosition.value = position
        _lyricsOffset.value = offset
    }

    val isTranslateLyrics: StateFlow<Boolean> =
        userPreferencesRepository.translateLyricsFlow.stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            false
        )

    // STATE LIRIK GLOBAL (Agar tidak reset saat swipe screen di Pager)
    private val _hasAutoScrolledToTop = MutableStateFlow(false)
    val hasAutoScrolledToTop = _hasAutoScrolledToTop.asStateFlow()

    fun setAutoScrolledToTop(hasScrolled: Boolean) {
        _hasAutoScrolledToTop.value = hasScrolled
    }

    // Reset state saat ganti lagu
    fun resetScrollState() {
        _hasAutoScrolledToTop.value = false
    }

    fun toggleTranslateLyrics() {
        viewModelScope.launch {
            val current = isTranslateLyrics.value
            userPreferencesRepository.setTranslateLyrics(!current)
        }
    }
}
