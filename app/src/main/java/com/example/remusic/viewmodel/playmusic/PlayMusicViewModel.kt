package com.example.remusic.viewmodel.playmusic

import android.app.Application
import android.content.ComponentName
import android.util.Log
import androidx.annotation.OptIn
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.remusic.data.model.AudioOutputDevice
import com.example.remusic.data.model.SongWithArtist
import com.example.remusic.data.preferences.UserPreferencesRepository
import com.example.remusic.services.MusicService
import com.example.remusic.utils.extractGradientColorsFromImageUrl
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable.isActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AnimationDirection {
    FORWARD, // Maju
    BACKWARD, // Mundur
    NONE // Tidak ada animasi (misal saat pertama kali memuat)
}
// PlayerUiState juga didefinisikan di sini atau diimpor
data class PlayerUiState(
    val isPlaying: Boolean = false,
    val currentSong: SongWithArtist? = null,
    val currentPosition: Long = 0,
    val totalDuration: Long = 0,
    val isShuffleModeEnabled: Boolean = false,
    val playingMusicFromPlaylist: String = "Unknown Playlist",
    val repeatMode: Int = Player.REPEAT_MODE_OFF, // REPEAT_MODE_OFF, REPEAT_MODE_ONE, REPEAT_MODE_ALL
    val playlist: List<SongWithArtist> = emptyList(),
    val dominantColors: List<Color> = listOf(Color.DarkGray, Color.Black),
    val currentAudioDevice: AudioOutputDevice? = null,
    val currentSongIndex: Int = 0,
    val animationDirection: AnimationDirection = AnimationDirection.NONE
)


class PlayMusicViewModel(application: Application) : AndroidViewModel(application) {

    //untuk fitur fade ketika play, pause di klik
    companion object {
        private const val FADE_DURATION_MS = 500L // Durasi fade dalam milidetik
        private const val FADE_INTERVAL_MS = 20L  // Seberapa sering volume diupdate
    }

    // --- VARIABEL UNTUK MENGELOLA FADE ---
    private var volumeFadeJob: Job? = null
    private var isFading = false

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState = _uiState.asStateFlow()

    private var mediaController: MediaController? = null

    private val userPreferencesRepository = UserPreferencesRepository(getApplication())

    init {
        connectToService()
    }

    @OptIn(UnstableApi::class)
    private fun connectToService() {
        val sessionToken = SessionToken(getApplication(), ComponentName(getApplication(), MusicService::class.java))
        val controllerFuture = MediaController.Builder(getApplication(), sessionToken).buildAsync()
        controllerFuture.addListener(
            {
                mediaController = controllerFuture.get()
                setupPlayerListener()
                updatePlayerState()

                viewModelScope.launch {
                    val savedShuffleMode = userPreferencesRepository.shuffleEnabledFlow.first()
                    mediaController?.shuffleModeEnabled = savedShuffleMode
                    // Update UI state juga agar konsisten saat awal
                    _uiState.update { it.copy(isShuffleModeEnabled = savedShuffleMode) }
                    Log.d("PlayMusicViewModel", "Applied saved shuffle mode: $savedShuffleMode")
                }
                viewModelScope.launch {
                    val savedRepeatMode = userPreferencesRepository.repeatModeFlow.first()
                    mediaController?.repeatMode = savedRepeatMode
                    _uiState.update { it.copy(repeatMode = savedRepeatMode) }
                    Log.d("PlayMusicViewModel", "Applied saved repeat mode: $savedRepeatMode")
                }

                val lastIndex = _uiState.value.playlist
                    .indexOfFirst { it.song.id == _uiState.value.currentSong?.song?.id }
                if (lastIndex >= 0) {
                    setPlaylist(_uiState.value.playlist, lastIndex)
                }
            },
            ContextCompat.getMainExecutor(getApplication())
        )
    }

    private fun setupPlayerListener() {
        mediaController?.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {

                if (!isFading) {
                    _uiState.update { it.copy(isPlaying = isPlaying) }
                }

                if (isPlaying) {
                    startUpdatingPosition()
                } else {
                    stopUpdatingPosition()
                }
            }

            override fun onEvents(player: Player, events: Player.Events) {
                super.onEvents(player, events)
                _uiState.update {
                    it.copy(
                        totalDuration = player.duration.coerceAtLeast(0L),
                        currentPosition = player.currentPosition.coerceAtLeast(0L)
                    )
                }
            }

            // Listener ini akan update UI jika lagu di service berubah (misal dari notifikasi)
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                super.onMediaItemTransition(mediaItem, reason)
                val player = mediaController ?: return

                val newIndex = player.currentMediaItemIndex
                val oldIndex = _uiState.value.currentSongIndex

                // Tentukan arah berdasarkan perubahan indeks
                val direction = when {
                    newIndex > oldIndex -> AnimationDirection.FORWARD
                    newIndex < oldIndex -> AnimationDirection.BACKWARD
                    else -> _uiState.value.animationDirection // pertahankan arah jika sama
                }

                if (mediaItem != null) {
                    val playlist = _uiState.value.playlist
                    val matchedSong = playlist.find { it.song.id == mediaItem.mediaId }

                    if (matchedSong != null) {
                        _uiState.update {
                            it.copy(
                                currentSong = matchedSong,
                                currentSongIndex = newIndex,
                                animationDirection = direction
                            )
                        }
                        // --- LOGIKA WARNA DITAMBAHKAN DI SINI JUGA ---
                        // Saat lagu berganti, ekstrak warna dari lagu yang baru.
                        viewModelScope.launch {
                            val colors = extractGradientColorsFromImageUrl(
                                context = getApplication(),
                                imageUrl = matchedSong.song.coverUrl ?: ""
                            )
                            _uiState.update { it.copy(dominantColors = colors) }
                        }

                    } else {
                        // fallback kalau nggak ketemu di playlist
                        _uiState.update {
                            it.copy(
                                currentSong = SongWithArtist(
                                    song = com.example.remusic.data.model.Song(
                                        id = mediaItem.mediaId,
                                        title = mediaItem.mediaMetadata.title?.toString() ?: "Unknown",
                                        audioUrl = mediaItem.localConfiguration?.uri.toString(),
                                        coverUrl = mediaItem.mediaMetadata.artworkUri.toString(),
                                        lyrics = "" // default kosong
                                    ),
                                    artist = null
                                )
                            )
                        }
                    }
                }
            }


            // Listener untuk update shuffle dan repeat mode
            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                Log.d("PlayMusicViewModel", "LISTENER: Shuffle mode changed to: $shuffleModeEnabled")
                _uiState.update { it.copy(isShuffleModeEnabled = shuffleModeEnabled) }

                viewModelScope.launch {
                    userPreferencesRepository.setShuffleEnabled(shuffleModeEnabled)
                    Log.d("PlayMusicViewModel", "Saved new shuffle mode: $shuffleModeEnabled")
                }
            }

            override fun onRepeatModeChanged(repeatMode: Int) {
                _uiState.update { it.copy(repeatMode = repeatMode) }

                viewModelScope.launch {
                    userPreferencesRepository.setRepeatMode(repeatMode)
                    Log.d("PlayMusicViewModel", "Saved new repeat mode: $repeatMode")
                }
            }
        })
    }

    private fun updatePlayerState() {
        mediaController?.let { player ->
            _uiState.update {
                it.copy(
                    isPlaying = player.isPlaying,
                    totalDuration = player.duration.coerceAtLeast(0L),
                    currentPosition = player.currentPosition.coerceAtLeast(0L)
                )
            }
        }
    }

    private var positionJob: Job? = null

    private fun startUpdatingPosition() {
        positionJob?.cancel()
        positionJob = viewModelScope.launch {
            while (isActive) {
                val controller = mediaController
                if (controller != null && controller.isPlaying) {
                    val pos = controller.currentPosition
                    val dur = controller.duration
                    _uiState.update { state ->
                        state.copy(
                            currentPosition = pos,
                            totalDuration = if (dur > 0) dur else state.totalDuration
                        )
                    }
                }
                delay(500)
            }
        }
    }

    private fun stopUpdatingPosition() {
        positionJob?.cancel()
        positionJob = null
    }

    fun setPlaylist(songs: List<SongWithArtist>, startIndex: Int = 0) {
        if (songs.isEmpty()) {
            Log.w("PlayMusicViewModel", "setPlaylist: songs list kosong!")
            return
        }

        val controller = mediaController
        if (controller == null) {
            Log.w("PlayMusicViewModel", "Controller not ready, saving playlist for later.")
            _uiState.update { it.copy(playlist = songs, currentSong = songs[startIndex]) }
            return
        }

        // Log jumlah lagu dan startIndex
        Log.d("PlayMusicViewModel", "setPlaylist called with ${songs.size} songs, startIndex=$startIndex")

        // Update UI state
        _uiState.update { it.copy(playlist = songs, currentSong = songs[startIndex]) }

        // --- LOGIKA WARNA DITAMBAHKAN DI SINI ---
        val songToPlay = songs.getOrNull(startIndex)
        if (songToPlay != null) {
            viewModelScope.launch {
                val colors = extractGradientColorsFromImageUrl(
                    context = getApplication(),
                    imageUrl = songToPlay.song.coverUrl ?: ""
                )
                _uiState.update { it.copy(dominantColors = colors) }
            }
        }

        // Mapping jadi MediaItems
        val mediaItems = songs.mapIndexed { index, s ->
            val meta = MediaMetadata.Builder()
                .setTitle(s.song.title)
                .setArtist(s.artist?.name)
                .setArtworkUri(s.song.coverUrl?.takeIf { it.isNotBlank() }?.toUri())
                .build()

            val item = MediaItem.Builder()
                .setMediaId(s.song.id)
                .setUri(s.song.audioUrl)
                .setMediaMetadata(meta)
                .build()

            // Debug detail tiap lagu
            Log.d(
                "PlayMusicViewModel",
                "MediaItem[$index]: id=${s.song.id}, title=${s.song.title}, " +
                        "artist=${s.artist?.name}, url=${s.song.audioUrl}, cover=${s.song.coverUrl}"
            )
            item
        }

        // Log hasil akhir mediaItems
        Log.d("PlayMusicViewModel", "Total MediaItems after mapping: ${mediaItems.size}")

        // Set ke MediaController
        mediaController?.apply {
            setMediaItems(mediaItems, startIndex, C.TIME_UNSET)
            Log.d("PlayMusicViewModel", "MediaItems set to controller, will start at index=$startIndex")
            prepare()
            play()
        } ?: Log.e("PlayMusicViewModel", "mediaController is null! Playlist not set.")
    }



    fun togglePlayPause() {
        // Mencegah aksi jika fade sedang berjalan
        if (isFading) return

        // Simpan status saat ini sebelum diubah
        val wasPlaying = _uiState.value.isPlaying

        // 2. LANGSUNG UPDATE UI STATE (INI KUNCINYA)
        // Ikon akan berubah seketika karena ini.
        _uiState.update { it.copy(isPlaying = !wasPlaying) }

        if (wasPlaying) {
            fadeOutAndPause()
        } else {
            fadeInAndPlay()
        }
    }

    // --- FUNGSI BARU UNTUK FADE OUT ---
    private fun fadeOutAndPause() {
        val controller = mediaController ?: return
        volumeFadeJob?.cancel() // Batalkan fade sebelumnya jika ada
        isFading = true

        volumeFadeJob = viewModelScope.launch {
            val startVolume = controller.volume
            val steps = (FADE_DURATION_MS / FADE_INTERVAL_MS).toInt()
            val volumeStep = startVolume / steps

            for (i in 1..steps) {
                val newVolume = (startVolume - (volumeStep * i)).coerceIn(0.0f, 1.0f)
                controller.volume = newVolume
                delay(FADE_INTERVAL_MS)
            }

            // Setelah fade selesai, pause dan kembalikan volume ke 1.0f
            // agar saat play lagi, fade-in bisa dimulai dari volume penuh.
            controller.pause()
            controller.volume = 1.0f
            isFading = false
        }
    }

    // --- FUNGSI BARU UNTUK FADE IN ---
    private fun fadeInAndPlay() {
        val controller = mediaController ?: return
        volumeFadeJob?.cancel()
        isFading = true

        volumeFadeJob = viewModelScope.launch {
            // Set volume ke 0, lalu mulai mainkan (tidak akan terdengar)
            controller.volume = 0.0f
            controller.play()
            _uiState.update { it.copy(isPlaying = true) } // Update UI secara manual

            val targetVolume = 1.0f
            val steps = (FADE_DURATION_MS / FADE_INTERVAL_MS).toInt()
            val volumeStep = targetVolume / steps

            for (i in 1..steps) {
                val newVolume = (volumeStep * i).coerceIn(0.0f, 1.0f)
                controller.volume = newVolume
                delay(FADE_INTERVAL_MS)
            }

            // Pastikan volume kembali penuh
            controller.volume = 1.0f
            isFading = false
            // Panggil listener secara manual jika perlu
            if (controller.isPlaying != _uiState.value.isPlaying) {
                _uiState.update { it.copy(isPlaying = controller.isPlaying) }
            }
        }
    }

    fun toggleShuffleMode() {
        val currentShuffleState = _uiState.value.isShuffleModeEnabled
        Log.d("PlayMusicViewModel", "COMMAND: Toggling shuffle. Current state is $currentShuffleState. Setting to ${!currentShuffleState}")
        mediaController?.shuffleModeEnabled = !currentShuffleState
    }

    // Fungsi ini akan berputar: OFF -> ONE -> ALL -> OFF
    fun cycleRepeatMode() {
        val nextRepeatMode = when (_uiState.value.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ONE
            Player.REPEAT_MODE_ONE -> Player.REPEAT_MODE_ALL
            else -> Player.REPEAT_MODE_OFF
        }
        mediaController?.repeatMode = nextRepeatMode
    }

    fun seekTo(position: Long) {
        mediaController?.seekTo(position)
    }

    fun nextSong() {
        mediaController?.seekToNext()
        _uiState.update { it.copy(animationDirection = AnimationDirection.FORWARD) }
    }

    fun previousSong() {
        mediaController?.seekToPrevious()
        _uiState.update { it.copy(animationDirection = AnimationDirection.BACKWARD) }
    }

    fun playSongAt(index: Int) {
        val controller = mediaController ?: return
        if (index in 0 until controller.mediaItemCount) {
            controller.seekTo(index, C.TIME_UNSET)
            controller.play()
        }
    }

    fun playingMusicFromPlaylist(playlistName: String) {
        _uiState.update {
            it.copy(
                playingMusicFromPlaylist = playlistName
            )
        }
    }


    override fun onCleared() {
        super.onCleared()
        mediaController?.release()
    }
}