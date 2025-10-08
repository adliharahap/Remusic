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

    // Cari index terakhir di `lyrics` yang timestamp <= posisi sekarang
    val activeLyricIndex = lyrics.combine(currentPosition) { list, pos ->
        if (list.isEmpty()) return@combine -1
        var idx = -1
        for (i in list.indices) {
            val ts = list[i].timestamp
            if (ts != -1L && ts <= pos) {
                idx = i
            } else if (ts != -1L && ts > pos) {
                // sudah melewati timestamp yang lebih besar -> stop
                break
            }
            // baris tanpa timestamp (ts == -1) diabaikan untuk index aktif
        }
        idx
    }.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), -1)

    fun loadLyrics(lrcString: String) {
        val parsed = parseLrc(lrcString)
        _lyrics.value = parsed
        // debug
        println("Loaded ${parsed.size} lyric lines")
    }

    // dipanggil dari UI tiap kali currentPosition berubah
    fun updatePlaybackPosition(position: Long) {
        _currentPosition.value = position
    }

    val isTranslateLyrics: StateFlow<Boolean> =
        userPreferencesRepository.translateLyricsFlow.stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            false
        )

    fun toggleTranslateLyrics() {
        viewModelScope.launch {
            val current = isTranslateLyrics.value
            userPreferencesRepository.setTranslateLyrics(!current)
        }
    }
}
