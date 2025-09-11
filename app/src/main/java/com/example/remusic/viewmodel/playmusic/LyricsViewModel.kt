package com.example.remusic.viewmodel.playmusic

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.remusic.data.model.LyricLine
import com.example.remusic.data.model.parseLrc
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class LyricsViewModel : ViewModel() {

    private val _lyrics = MutableStateFlow<List<LyricLine>>(emptyList())
    val lyrics = _lyrics.asStateFlow()

    // posisi playback aktual (ms)
    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition = _currentPosition.asStateFlow()

    // (optional) kalau mau tahu sedang playing bisa ditambahkan later
    // private val _isPlaying = MutableStateFlow(false)
    // val isPlaying = _isPlaying.asStateFlow()

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
}
