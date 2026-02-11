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
import com.example.remusic.data.local.MusicDatabase
import com.example.remusic.data.model.AudioOutputDevice
import com.example.remusic.data.model.Song
import com.example.remusic.data.model.SongWithArtist
import com.example.remusic.data.model.User
import com.example.remusic.data.preferences.UserPreferencesRepository
import com.example.remusic.data.repository.MusicRepository
import com.example.remusic.services.MusicService
import com.example.remusic.utils.extractGradientColorsFromImageUrl
import io.github.jan.supabase.auth.auth
import com.example.remusic.data.SupabaseManager
import com.example.remusic.data.model.Artist
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

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
    val animationDirection: AnimationDirection = AnimationDirection.NONE,
    val isLoadingLyrics: Boolean = false,
    val isSleepTimerActive: Boolean = false,
    val sleepTimerEndTime: Long? = null,
    val isBuffering: Boolean = false,
    val isLoadingData: Boolean = false,

    val debugStatus: String = "Preparing...",
    val errorMessage: String? = null,
    val isLiked: Boolean = false,
    val uploader: User? = null,
    val playlistSubtitle: String = "Memainkan Dari Playlist", // Default
    
    // Error state tracking
    val hasError: Boolean = false,
    val errorType: String? = null
)


class PlayMusicViewModel(application: Application) : AndroidViewModel(application) {

    //untuk fitur fade ketika play, pause di klik
    companion object {
        private const val FADE_DURATION_MS = 500L // Durasi fade dalam milidetik
        private const val FADE_INTERVAL_MS = 20L  // Seberapa sering volume diupdate
    }

    private var fetchLyricsJob: Job? = null
    private var searchDebounceJob: Job? = null

    // variabel untuk playsongat
    private var jumpJob: Job? = null
    private var setPlaylistJob: Job? = null

    // --- VARIABEL UNTUK MENGELOLA FADE ---
    private var volumeFadeJob: Job? = null
    private var isFading = false

    private val _uiState = MutableStateFlow(PlayerUiState())
    
    // Variabel untuk melacak lagu terakhir yang play count-nya sudah di-increment
    private var lastIncrementedSongId: String? = null
    
    // --- TRACKING REAL LISTENING TIME (ANTI-CHEAT) ---
    private var actualListeningTimeMs: Long = 0L // Waktu dengar asli (akumulasi)
    private var lastSongIdForTracking: String? = null // ID lagu terakhir yang sedang dilacak

    // Cache memory: Key = TelegramFileID, Value = Link Streaming Direct
    private val database = MusicDatabase.getDatabase(application)
    private val repository = MusicRepository(database.musicDao())

    val uiState = _uiState.asStateFlow()

    private var mediaController: MediaController? = null

    private val userPreferencesRepository = UserPreferencesRepository(getApplication())

    init {
        connectToService()
    }

    @OptIn(UnstableApi::class)
    private fun connectToService() {
        val sessionToken = SessionToken(getApplication(), ComponentName(getApplication(), MusicService::class.java))
        
        // Listener khusus untuk MediaController (Session Extras / Sleep Timer)
        val controllerListener = object : MediaController.Listener {
            override fun onExtrasChanged(controller: MediaController, extras: android.os.Bundle) {
                super.onExtrasChanged(controller, extras)
                val isTimerActive = extras.getBoolean("IS_SLEEP_TIMER_ACTIVE", false)
                val timerEndTime = extras.getLong("SLEEP_TIMER_END_TIME", 0L)

                Log.d("PlayMusicViewModel", "🔔 EXTRAS CHANGED: Sleep Timer Active = $isTimerActive, End = $timerEndTime")
                
                // Update UI State (Hanya jika berbeda biar gak loop)
                if (_uiState.value.isSleepTimerActive != isTimerActive || _uiState.value.sleepTimerEndTime != timerEndTime) {
                    _uiState.update { 
                        it.copy(
                            isSleepTimerActive = isTimerActive,
                            sleepTimerEndTime = if(isTimerActive) timerEndTime else null
                         ) 
                    }
                }
            }
        }

        val controllerFuture = MediaController.Builder(getApplication(), sessionToken)
            .setListener(controllerListener)
            .buildAsync()

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
            override fun onPlaybackStateChanged(playbackState: Int) {
                super.onPlaybackStateChanged(playbackState)
                _uiState.update {
                    it.copy(
                        isBuffering = playbackState == Player.STATE_BUFFERING
                    )
                }
            }

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

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                super.onPlayerError(error)
                
                val errorCode = error.errorCode
                val errorMsg = error.message
                Log.e("PlayMusicViewModel", "🔴 Player Error [$errorCode]: $errorMsg")
                
                // Update UI state dengan error info
                _uiState.update { 
                    it.copy(
                        hasError = true,
                        errorType = "Error Code: $errorCode",
                        isBuffering = false,
                        isPlaying = false
                    )
                }
                
                // Auto-recovery untuk SOURCE_ERROR (network/stream issues)
                // Error codes untuk network/IO issues:
                // - ERROR_CODE_IO_NETWORK_CONNECTION_FAILED (2001)
                // - ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT (2002)
                // - ERROR_CODE_BEHIND_LIVE_WINDOW (1002)
                if (error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
                    error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ||
                    error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                    
                    Log.w("PlayMusicViewModel", "🔄 Network error detected, attempting auto-recovery...")
                    handleAutoRecovery()
                }
            }

            // Listener ini akan update UI jika lagu di service berubah (misal dari notifikasi)
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                super.onMediaItemTransition(mediaItem, reason)

                val reasonStr = when(reason) {
                    Player.MEDIA_ITEM_TRANSITION_REASON_AUTO -> "AUTO (Next Otomatis)"
                    Player.MEDIA_ITEM_TRANSITION_REASON_SEEK -> "SEEK (Manual Klik/Geser)"
                    Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED -> "PLAYLIST CHANGED"
                    else -> "UNKNOWN"
                }

                Log.d("DEBUG_PLAYER", "🔀 TRANSISI: Ganti lagu karena $reasonStr")

                val player = mediaController ?: return

                val newIndex = player.currentMediaItemIndex
                val oldIndex = _uiState.value.currentSongIndex

                Log.d("DEBUG_PLAYER", "   📍 Perubahan Index: $oldIndex -> $newIndex")

                // Tentukan arah berdasarkan perubahan indeks
                val direction = when {
                    newIndex > oldIndex -> AnimationDirection.FORWARD
                    newIndex < oldIndex -> AnimationDirection.BACKWARD
                    else -> _uiState.value.animationDirection // pertahankan arah jika sama
                }

                if (mediaItem != null) {
                    val playlist = _uiState.value.playlist
                    val matchedSong = playlist.find { it.song.id == mediaItem.mediaId }

                    // PRE-FETCH: Siapkan lagu BERIKUTNYA secara diam-diam
                    prefetchSongAtIndex(newIndex + 1)

                    // 2. Siapkan lagu SEBELUMNYA (Untuk tombol Prev)
                    prefetchSongAtIndex(newIndex - 1)

                    if (matchedSong != null) {
                        // 🔍 CRITICAL FIX: Cek URL sebelum play
                        // Jika URL kosong/invalid, resolve dulu sebelum ExoPlayer coba load
                        val currentUrl = mediaItem.localConfiguration?.uri?.toString() ?: ""
                        val isUrlValid = currentUrl.isNotBlank() && 
                                        (currentUrl.startsWith("http") || currentUrl.startsWith("content"))
                        
                        if (!isUrlValid) {
                            Log.w("DEBUG_PLAYER", "⚠️ URL KOSONG/INVALID di index $newIndex. Resolving URL dulu...")
                            
                            // Pause player sementara
                            player.pause()
                            
                            // Resolve URL di background
                            viewModelScope.launch(Dispatchers.IO) {
                                try {
                                    val newUrl = resolveSongUrl(matchedSong)
                                    
                                    withContext(Dispatchers.Main) {
                                        if (newUrl.isNotBlank()) {
                                            Log.d("DEBUG_PLAYER", "✅ URL Resolved: $newUrl")
                                            
                                            // Update MediaItem dengan URL valid
                                            val newItem = createMediaItem(matchedSong, newUrl)
                                            player.replaceMediaItem(newIndex, newItem)
                                            player.prepare()
                                            player.play()
                                        } else {
                                            Log.e("DEBUG_PLAYER", "❌ Gagal resolve URL. Skip lagu ini.")
                                            // Skip ke lagu berikutnya
                                            if (player.hasNextMediaItem()) {
                                                player.seekToNextMediaItem()
                                            }
                                        }
                                    }
                                } catch (e: Exception) {
                                    Log.e("DEBUG_PLAYER", "❌ Error resolving URL: ${e.message}")
                                }
                            }
                        }
                        
                        _uiState.update {
                            it.copy(
                                currentSong = matchedSong,
                                currentSongIndex = newIndex,
                                animationDirection = direction,
                                uploader = null // Reset uploader for new song
                            )
                        }
                        Log.d("DEBUG_PLAYER", "🎵 Lagu Terdeteksi: ${matchedSong.song.title}")
                        fetchFullSongDetails(matchedSong.song.id)

                        // --- LOGIKA WARNA DITAMBAHKAN DI SINI JUGA ---
                        // Saat lagu berganti, ekstrak warna dari lagu yang baru.
                        viewModelScope.launch {
                            val colors = extractGradientColorsFromImageUrl(
                                context = getApplication(),
                                imageUrl = matchedSong.song.coverUrl ?: ""
                            )
                            _uiState.update { it.copy(dominantColors = colors) }
                        }

                        // 3. CEK STATUS LIKE (PENTING: Biar UI update saat ganti lagu otomatis)
                        checkIfLiked(matchedSong.song.id)

                    } else {
                        Log.w("DEBUG_PLAYER", "   ⚠️ FALLBACK: Menggunakan metadata dari MediaItem (Lagu tidak ada di list local)")
                        // fallback kalau nggak ketemu di playlist
                        _uiState.update {
                            it.copy(
                                currentSong = SongWithArtist(
                                    song = Song(
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

    /**
     * Auto-recovery mechanism untuk handle playback errors
     * Akan mencoba resume playback di posisi terakhir setelah 2 detik
     */
    private fun handleAutoRecovery() {
        viewModelScope.launch {
            val controller = mediaController ?: return@launch
            val currentPosition = controller.currentPosition
            val currentMediaItem = controller.currentMediaItem
            
            if (currentMediaItem != null && currentPosition >= 0) {
                delay(2000) // Wait 2s sebelum retry (beri waktu network pulih)
                
                Log.d("PlayMusicViewModel", "↩️ Recovering playback at ${currentPosition}ms for: ${currentMediaItem.mediaMetadata.title}")
                
                // Re-prepare dengan media item yang sama
                controller.setMediaItem(currentMediaItem)
                controller.prepare()
                controller.seekTo(currentPosition)
                controller.playWhenReady = true
                
                // Clear error state
                _uiState.update { 
                    it.copy(
                        hasError = false,
                        errorType = null
                    )
                }
            } else {
                Log.w("PlayMusicViewModel", "⚠️ Cannot recover: MediaItem or position invalid")
            }
        }
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

    @OptIn(UnstableApi::class)
    private fun startUpdatingPosition() {
        positionJob?.cancel()
        positionJob = viewModelScope.launch { // Launch di Main Thread (Default)
            val cache = com.example.remusic.utils.RemusicCache.getInstance(getApplication())
            var loopCounter = 0
            
            while (currentCoroutineContext().isActive) {
                val controller = mediaController
                if (controller != null && controller.isPlaying) {
                    // 1. Ambil data Player di Main Thread (Safe)
                    val pos = controller.currentPosition
                    val dur = controller.duration
                    val currentUri = controller.currentMediaItem?.localConfiguration?.uri?.toString()
                    
                    // 2. Cek Cache di IO Thread (Hanya setiap 2 detik = 4x loop)
                    // Optimasi untuk "HP Kentang" agar tidak boros resource context switch
                    val statusText = if (loopCounter % 4 == 0) {
                        withContext(Dispatchers.IO) {
                            // Priority: Error > Buffering > Loading > Cache Status
                            if (_uiState.value.hasError) {
                                "Error: ${_uiState.value.errorType ?: "Unknown"}"
                            } else if (_uiState.value.isBuffering) {
                                "Buffering..."
                            } else if (_uiState.value.isLoadingLyrics || _uiState.value.isLoadingData) {
                                "Mendapatkan Data..."
                            } else if (currentUri != null) {
                                val isCached = try {
                                     cache.isCached(currentUri, pos, 1024) 
                                } catch (_: Exception) {
                                    false
                                }
                                if (isCached) "Playing music offline" else "Playing music online"
                            } else {
                                "Menyiapkan..."
                            }
                        }
                    } else {
                        // Gunakan status terakhir (biar hemat CPU)
                        _uiState.value.debugStatus
                    }

                    // --- LOGIC PLAY COUNT V2 (REAL LISTENING TIME) ---
                    val currentSongId = _uiState.value.currentSong?.song?.id
                    
                    // 1. Reset jika ganti lagu
                    if (currentSongId != lastSongIdForTracking) {
                        actualListeningTimeMs = 0L
                        lastIncrementedSongId = null
                        lastSongIdForTracking = currentSongId
                        Log.d("PlayMusicViewModel", "🔄 [TRACKING RESET] New Song Detected: $currentSongId")
                    }

                    if (currentSongId != null && dur > 0) {
                        // 2. Akumulasi Waktu (Hanya nambah 1000ms setiap loop karena interval delay = 1000)
                        actualListeningTimeMs += 1000L
                        
                        // 3. Hitung Threshold (60% Durasi Total)
                        val thresholdMs = (dur * 0.6).toLong()

                        // 4. Cek Threshold Waktu Asli (Bukan Posisi Seekbar)
                        if (actualListeningTimeMs >= thresholdMs && currentSongId != lastIncrementedSongId) {
                            Log.d("PlayMusicViewModel", "[VALID PLAY] User listening for ${actualListeningTimeMs/1000}s. Incrementing...")
                            viewModelScope.launch(Dispatchers.IO) {
                                repository.incrementPlayCount(currentSongId)
                            }
                            lastIncrementedSongId = currentSongId // Lock agar tidak double count
                        }
                    }

                    // 3. Update UI
                    _uiState.update { state ->
                        state.copy(
                            currentPosition = pos,
                            totalDuration = if (dur > 0) dur else state.totalDuration,
                            debugStatus = statusText
                        )
                    }
                    
                    loopCounter++
                }
                delay(1000)
            }
        }
    }

    private fun stopUpdatingPosition() {
        positionJob?.cancel()
        positionJob = null
    }

    private fun fetchFullSongDetails(songId: String, forceUpdate: Boolean = false) {
        // 1. CLEANUP JOB LAMA
        fetchLyricsJob?.cancel()
        searchDebounceJob?.cancel()

        // 2. DEBOUNCE
        searchDebounceJob = viewModelScope.launch(Dispatchers.IO) {
            delay(1000)

            fetchLyricsJob = launch {
                // Nyalakan Loading
                _uiState.update { it.copy(isLoadingLyrics = true) }

                try {
                    // --- RETRY LOOP LOGIC ---
                    var attempt = 1
                    val maxAttempts = 2
                    var isSuccess = false

                    while (attempt <= maxAttempts && !isSuccess) {
                        if (!isActive) break

                        try {
                            Log.d("DEBUG_PLAYER", "🔄 FETCH Attempt #$attempt untuk ID: $songId")

                            val timeoutMs = if (attempt == 1) 5000L else 10000L
                            val fullSongData = withTimeout(timeoutMs) {
                                repository.getFullSongDetails(songId)
                            }

                            ensureActive()

                            if (fullSongData != null) {
                                _uiState.update { state ->
                                    if (state.currentSong?.song?.id == songId) {
                                        state.copy(
                                            // Loading dimatikan di finally, jadi disini update data aja
                                            currentSong = state.currentSong.copy(
                                                song = state.currentSong.song.copy(
                                                    lyrics = fullSongData.lyrics,
                                                    uploaderUserId = fullSongData.uploaderUserId,
                                                    artistId = fullSongData.artistId
                                                )
                                            )
                                        )
                                    } else {
                                        state
                                    } 
                                }

                                // --- BARU: Fetch Artist Details ---
                                fullSongData.artistId?.let { artistId ->
                                    val artistDetails = repository.fetchArtistDetails(artistId)
                                    if (artistDetails != null) {
                                        _uiState.update { state ->
                                            if (state.currentSong?.song?.id == songId) {
                                                state.copy(
                                                    currentSong = state.currentSong.copy(artist = artistDetails)
                                                )
                                            } else {
                                                state
                                            }
                                        }
                                        Log.d("DEBUG_PLAYER", "✅ Artist Fetched: ${artistDetails.name}")
                                    }
                                }
                                // ----------------------------------
                                isSuccess = true
                            } else {
                                Log.w("DEBUG_PLAYER", "⚠️ Data server null. Stop retry.")
                                break
                            }
                        } catch (e: Exception) {
                            if (e is kotlinx.coroutines.CancellationException) throw e // Lempar ke blok finally luar
                            Log.w("DEBUG_PLAYER", "❌ Gagal Percobaan #$attempt: ${e.message}")
                            if (attempt < maxAttempts) delay(2000)
                        }
                        attempt++
                    }
                } catch (e: Exception) {
                    // Log error global jika ada (selain cancel)
                    if (e !is kotlinx.coroutines.CancellationException) {
                        Log.e("DEBUG_PLAYER", "❌ CRASH FETCH LIRIK: ${e.message}")
                    }
                } finally {
                    _uiState.update { state ->
                        // Cek Cerdas: Hanya matikan loading jika lagu yang aktif ADALAH lagu request ini.
                        // Jangan sampai kita matikan loading punya lagu selanjutnya (Song B) karena Song A dicancel.
                        if (state.currentSong?.song?.id == songId) {
                            Log.d("DEBUG_PLAYER", "🏁 FETCH SELESAI/CANCEL: Matikan Loading untuk $songId")
                            state.copy(isLoadingLyrics = false)
                        } else {
                            // Kalau lagunya udah beda, jangan sentuh state loadingnya.
                            state
                        }
                    }

                    // Setelah dapat detail lagu (termasuk uploaderUserId), ambil detail uploader
                    _uiState.value.currentSong?.song?.uploaderUserId?.let { uploaderId ->
                        fetchUploaderDetails(uploaderId)
                    }
                }
            }
        }
    }

    private fun fetchUploaderDetails(uploaderId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val uploader = repository.fetchUserDetails(uploaderId)
            _uiState.update { it.copy(uploader = uploader) }
        }
    }

    fun setPlaylist(songs: List<SongWithArtist>, startIndex: Int = 0): Job? {
        Log.d("DEBUG_PLAYER", "📥 SET PLAYLIST: Menerima ${songs.size} lagu. Start Index: $startIndex")

        // 1. Validasi Awal
        if (songs.isEmpty()) {
            Log.w("PlayMusicViewModel", "setPlaylist: songs list kosong!")
            return null
        }

        val controller = mediaController
        if (controller == null) {
            Log.w("PlayMusicViewModel", "Controller not ready, saving playlist for later.")
            _uiState.update { it.copy(playlist = songs, currentSong = songs[startIndex]) }
            return null
        }

        Log.d("PlayMusicViewModel", "setPlaylist called with ${songs.size} songs, startIndex=$startIndex")

        // 2. Update UI State Langsung (Biar user lihat judul lagu berubah cepat)
        _uiState.update { it.copy(playlist = songs, currentSong = songs[startIndex]) }

        // 3. MULAI PROSES ASYNC (Resolve URL & Warna)
        // Kita bungkus semua proses mapping & player setup di sini
        // RETURN JOB agar bisa ditunggu (join)
        return viewModelScope.launch(Dispatchers.IO) {

            // A. Ambil Lagu yang mau dimainkan
            val songToPlay = songs.getOrNull(startIndex) ?: return@launch

            Log.d("DEBUG_PLAYER", "⚡ PROCESS: Resolving URL untuk lagu pertama (Index $startIndex): ${songToPlay.song.title}")
            // B. Resolve URL (Tunggu sebentar request ke Vercel/Cache)
            // Ini langkah kuncinya! Kita putuskan mau pakai link Telegram atau AudioURL biasa
            val resolvedUrl = resolveSongUrl(songToPlay)
            Log.d("PlayMusicViewModel", "URL Resolved for first song: $resolvedUrl")

            // C. Fetch Data Lengkap (Lirik, dll) untuk lagu pertama
            fetchFullSongDetails(songToPlay.song.id)

            // C. Extract Warna (Sambil jalan)
            val colors = extractGradientColorsFromImageUrl(
                context = getApplication(),
                imageUrl = songToPlay.song.coverUrl ?: ""
            )
            // Update warna ke UI
            _uiState.update { it.copy(dominantColors = colors) }

            Log.d("DEBUG_PLAYER", "🛠 MAPPING: Membuat MediaItems...")

            // D. Mapping jadi MediaItems (DI DALAM COROUTINE)
            // Kenapa di dalam? Karena kita butuh 'resolvedUrl' untuk index == startIndex
            val mediaItems = songs.mapIndexed { index, s ->
                val meta = MediaMetadata.Builder()
                    .setTitle(s.song.title)
                    .setArtist(s.artist?.name)
                    .setArtworkUri(s.song.coverUrl?.takeIf { it.isNotBlank() }?.toUri())
                    .build()

                // LOGIKA PENTING:
                // Jika ini lagu yang mau dimainkan (startIndex), pakai 'resolvedUrl' (hasil Vercel).
                // Jika lagu lain, pakai 'audioUrl' biasa dulu (nanti di-prefetch sambil jalan).
                val urlToUse = if (index == startIndex) resolvedUrl else (s.song.audioUrl ?: "")

                val item = MediaItem.Builder()
                    .setMediaId(s.song.id)
                    .setUri(urlToUse)
                    .setMediaMetadata(meta)
                    .build()

                // Debug log (Sesuai kode Mas)
                if (index == startIndex) {
                    Log.d("DEBUG_PLAYER", "   🎵 [Index $index] (ACTIVE) ${s.song.title} -> $urlToUse")
                }else {
                    Log.d("DEBUG_PLAYER", "   ❓ [Index $index] (QUEUE) URL Masih Mentah: $urlToUse")
                }
                item
            }

            Log.d("PlayMusicViewModel", "Total MediaItems mapped: ${mediaItems.size}")

            // E. Set ke MediaController (Pindah ke Main Thread)
            // Wajib pakai withContext(Main) karena menyentuh UI/Player
            withContext(Dispatchers.Main) {
                Log.d("DEBUG_PLAYER", "▶ PLAYER: Menyiapkan dan memutar playlist...")
                mediaController?.apply {
                    setMediaItems(mediaItems, startIndex, C.TIME_UNSET)
                    Log.d("PlayMusicViewModel", "MediaItems set to controller, playing index=$startIndex")
                    prepare()
                    play()
                } ?: Log.e("PlayMusicViewModel", "mediaController is null inside coroutine!")
            }
        }
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
        // FIX: Jika dari Search, Shuffle dilarang (Smart Queue sudah terurut)
        if (_uiState.value.playlistSubtitle == "Memainkan Dari Pencarian") {
             Log.d("PlayMusicViewModel", "Shuffle disabled in Search Context (Smart Queue)")
             return
        }

        val currentShuffleState = _uiState.value.isShuffleModeEnabled
        Log.d("PlayMusicViewModel", "COMMAND: Toggling shuffle. Current state is $currentShuffleState. Setting to ${!currentShuffleState}")
        mediaController?.shuffleModeEnabled = !currentShuffleState
    }

    // Fungsi ini akan berputar: OFF -> ONE -> ALL -> OFF
    // FIX: Jika dari Search, hanya OFF -> ONE -> OFF (Skip ALL)
    fun cycleRepeatMode() {
        val isSearchContext = _uiState.value.playlistSubtitle == "Memainkan Dari Pencarian"
        
        val nextRepeatMode = if (isSearchContext) {
             // Search Context: Toggle OFF <-> ONE only
             if (_uiState.value.repeatMode == Player.REPEAT_MODE_ONE) {
                 Player.REPEAT_MODE_OFF
             } else {
                 Player.REPEAT_MODE_ONE
             }
        } else {
             // Normal Context: Cycle OFF -> ONE -> ALL -> OFF
             when (_uiState.value.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ONE
                Player.REPEAT_MODE_ONE -> Player.REPEAT_MODE_ALL
                else -> Player.REPEAT_MODE_OFF
            }
        }
        mediaController?.repeatMode = nextRepeatMode
    }

    fun seekTo(position: Long) {
        mediaController?.seekTo(position)
    }

    fun nextSong() {
        val controller = mediaController ?: return
        val nextIndex = controller.nextMediaItemIndex
        if (nextIndex != C.INDEX_UNSET) {
            _uiState.update { it.copy(animationDirection = AnimationDirection.FORWARD) }
            playSongAt(nextIndex)
        }
    }

    fun previousSong() {
        val controller = mediaController ?: return

        // Fitur: Jika lagu sudah main lebih dari 3 detik, restart lagu ini (bukan mundur ke lagu sebelumnya)
        if (controller.currentPosition > 3000) {
            controller.seekTo(0)
            return
        }

        val prevIndex = controller.previousMediaItemIndex
        if (prevIndex != C.INDEX_UNSET) {
            _uiState.update { it.copy(animationDirection = AnimationDirection.BACKWARD) }
            playSongAt(prevIndex)
        }
    }

    fun playSongAt(index: Int) {
        val controller = mediaController

        if (controller == null) {
            Log.e("DEBUG_PLAYER", "❌ ERROR: MediaController is NULL")
            return
        }

        val playlist = _uiState.value.playlist

        // Validasi index
        if (index !in playlist.indices) {
            Log.e("DEBUG_PLAYER", "❌ ERROR: Index $index di luar jangkauan")
            return
        }

        val targetSong = playlist[index]
        Log.d("DEBUG_PLAYER", "==================================================")
        Log.d("DEBUG_PLAYER", "👇 CLICK MANUAL (FIXED): User ingin memutar index $index: '${targetSong.song.title}'")

        // 1. BATALKAN JOB SEBELUMNYA (Supaya kalau user spam klik, yang terakhir yang menang)
        jumpJob?.cancel()
        setPlaylistJob?.cancel()

        // 🛑 PAUSE SEGERA! (Agar lagu lama berhenti saat buffering lagu baru)
        controller.pause()

        // 2. OPTIMISTIC UI UPDATE (Update Tampilan Dulu Biar Sat-Set!)
        _uiState.update {
            it.copy(
                currentSong = targetSong,
                currentSongIndex = index,
                // Reset loading state sementara
                isLoadingLyrics = false,

                isLoadingData = true, // Start loading data (Checking/Resolving)
                isLiked = false, // Reset like status for new song
                uploader = null, // Reset uploader for new song
                // 🔄 RESET SLIDER KE 0 (Biar kelihatan mulai dari awal)
                currentPosition = 0L
            )
        }

        jumpJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                // 3. CEK APAKAH URL SUDAH VALID?
                val currentUrl = targetSong.song.audioUrl
                val isUrlValid = !currentUrl.isNullOrBlank() && (currentUrl.startsWith("http") || currentUrl.startsWith("content"))

                if (isUrlValid) {
                    // JIKA URL VALID: Play Langsung (Optimistic)
                    Log.d("DEBUG_PLAYER", "🚀 URL VALID: Play langsung tanpa menunggu.")
                    _uiState.update { it.copy(debugStatus = "Playing from Cache", isLoadingData = false) } // Data ready
                    withContext(Dispatchers.Main) {
                        if (index < controller.mediaItemCount) {
                            if (controller.currentMediaItemIndex != index) {
                                controller.seekTo(index, C.TIME_UNSET)
                                controller.play()
                            }
                        }
                    }
                } else {
                    // JIKA URL KOSONG: Tampilkan Loading & Resolve Dulu
                    Log.d("DEBUG_PLAYER", "⏳ URL KOSONG: Resolving URL dulu... (Player dipause sementara)")
                    withContext(Dispatchers.Main) {
                         _uiState.update { it.copy(isLoadingLyrics = true, isLoadingData = true) } // Still loading
                    }
                }

                // 2. RESOLVE URL (Pastikan dapat yang fresh)
                Log.d("DEBUG_PLAYER", "🌐 FETCH: Resolving URL...")
                val newUrl = resolveSongUrl(targetSong)

                // 3. UPDATE PLAYER (Hanya update item yang sedang aktif)
                withContext(Dispatchers.Main) {
                    // VALIDASI URL: Jangan play kalau URL kosong/gagal resolve
                    if (newUrl.isBlank()) {
                        Log.e("DEBUG_PLAYER", "❌ ERROR: URL kosong/gagal resolve. Skip playback.")
                        // Kembalikan UI ke state aman atau tampilkan error
                        _uiState.update { it.copy(isLoadingLyrics = false) }
                        // Opsional: Toast error
                        return@withContext
                    }

                    if (index < controller.mediaItemCount) {
                        // Buat MediaItem baru dengan URL valid
                        val newItem = createMediaItem(targetSong, newUrl)

                        // KUNCI: Gunakan setMediaItem di index spesifik untuk update source
                        controller.replaceMediaItem(index, newItem)

                        // Jika tadi kita menunda play (karena URL kosong), sekarang play!
                        if (!isUrlValid || controller.playbackState == Player.STATE_IDLE || controller.playbackState == Player.STATE_ENDED) {
                             // Cek lagi apakah index masih sama (user gak ganti lagu lagi pas loading)
                             // Tapi karena kita replaceMediaItem di index spesifik, aman untuk seek ke situ.
                             if (controller.currentMediaItemIndex != index) {
                                 controller.seekTo(index, C.TIME_UNSET)
                             }
                             controller.prepare()
                             controller.play()
                        }
                    }
                    _uiState.update { it.copy(isLoadingLyrics = false, isLoadingData = false) }
                }

                // 4. UPDATE LIRIK & WARNA (Sekali saja!)
                // Ambil data detail
                fetchFullSongDetails(targetSong.song.id, forceUpdate = true)

                val colors = extractGradientColorsFromImageUrl(getApplication(), targetSong.song.coverUrl ?: "")

                _uiState.update { it.copy(dominantColors = colors) }
                
                // 5. CEK STATUS LIKE
                checkIfLiked(targetSong.song.id)

            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e // Biarkan cancel lewat
                
                // TANGKAP ERROR BIAR GAK CRASH
                Log.e("DEBUG_PLAYER", "❌ CRASH HANDLED di playSongAt: ${e.message}")
                e.printStackTrace()

                withContext(Dispatchers.Main) {
                     _uiState.update { it.copy(isLoadingLyrics = false, isLoadingData = false) }
                }
            }
        }
    }

    fun playingMusicFromPlaylist(playlistName: String) {
        _uiState.update {
            it.copy(
                playingMusicFromPlaylist = playlistName,
                playlistSubtitle = "Memainkan Dari Playlist" // Reset subtitle
            )
        }
    }

    // --- LOGIC BARU V4: PLAY FROM SEARCH ---
    fun playFromSearch(seedSong: SongWithArtist, query: String) {
        Log.d("PlayMusicViewModel", "🚀 playFromSearch CALLED! Seed: ${seedSong.song.title}, Query: $query")
        
        // 1. SET PLAYLIST dengan LAGU INI
        // FIX RACE CONDITION: Tunggu setPlaylist selesai dulu sebelum fetch queue!
        val initJob = setPlaylist(listOf(seedSong), 0)

        // 1.5 FORCE RESET MODES (Fix Bug: Looping di Index 0)
        // Saat play dari search (Radio Mode), kita matikan Repeat One & Shuffle biar alurnya benar
        viewModelScope.launch {
             mediaController?.let {
                 it.repeatMode = Player.REPEAT_MODE_OFF
                 it.shuffleModeEnabled = false
             }
             _uiState.update { 
                 it.copy(
                     repeatMode = Player.REPEAT_MODE_OFF,
                     isShuffleModeEnabled = false
                 ) 
             }
             // Save to Prefs (Supaya sinkron saat restart app)
             userPreferencesRepository.setRepeatMode(Player.REPEAT_MODE_OFF)
             userPreferencesRepository.setShuffleEnabled(false)
        }

        // 2. Override UI State for Search Context
        // setPlaylist resets subtitle, so we set it back to "Memainkan Dari Pencarian"
        _uiState.update {
            it.copy(
                playingMusicFromPlaylist = query, // Tampilkan Query di teks besar
                playlistSubtitle = "Memainkan Dari Pencarian" // Teks kecil khusus
            )
        }

        // 2. FETCH SMART QUEUE (Background)
        // PENTING: Gunakan IO Dispatcher & Join initJob
        viewModelScope.launch { // Launch di Main scope dulu, nanti withContext IO
            
            // TUNGGU LAGU PERTAMA SIAP (Metadata & URL)
            Log.d("PlayMusicViewModel", "🚦 Waiting for seed song to be ready...")
            initJob?.join()
            Log.d("PlayMusicViewModel", "🟢 Seed song ready. Fetching Smart Queue...")

            val fullSongDetails = repository.getFullSongDetails(seedSong.song.id) // Pastikan metadata lengkap (moods/language)
            val seedSongWithMeta = fullSongDetails?.let { 
                 seedSong.song.copy(
                     lyrics = it.lyrics,
                     language = it.language,
                     moods = it.moods,
                     artistId = it.artistId
                 )
            } ?: seedSong.song 

            val smartQueue = repository.fetchSmartQueue(
                currentSong = seedSongWithMeta
            )
            Log.d("PlayMusicViewModel", "📦 Smart Queue Result Size: ${smartQueue.size}")

            // 4. Update Playlist dengan hasil Smart Queue
            if (smartQueue.isNotEmpty()) {
                // Fetch Artist info logic (Existing)
                val artistIds = smartQueue.mapNotNull { it.artistId }.distinct()
                val artistsMap = try {
                     SupabaseManager.client.from("artists").select {
                        filter { isIn("id", artistIds) }
                     }.decodeList<Artist>().associateBy { it.id }
                } catch (_: Exception) {
                    emptyMap()
                }

                val queueWithArtists = smartQueue.map { song ->
                    SongWithArtist(song, song.artistId?.let { artistsMap[it] })
                }
                
                // --- LOGGING UNTUK ANALISIS USER ---
                Log.d("PlayMusicViewModel", "=== 📋 SMART QUEUE RESULT (${queueWithArtists.size} Songs) ===")
                queueWithArtists.forEachIndexed { index, item ->
                    // Log format: [No] Judul - Artist | Lang: .. | Moods: ..
                    val moodsStr = item.song.moods.joinToString(", ")
                    Log.d("PlayMusicViewModel", "Queue [#${index + 1}]: ${item.song.title} - ${item.artist?.name ?: "Unknown"} | Lang: ${item.song.language} | Moods: [$moodsStr]")
                }
                Log.d("PlayMusicViewModel", "===========================================================")

                // Update ViewModel State
                val fullPlaylist = mutableListOf(seedSong)
                fullPlaylist.addAll(queueWithArtists)

                _uiState.update { 
                    it.copy(
                        playlist = fullPlaylist
                    )
                }
                Log.d("PlayMusicViewModel", "✅ Smart Queue loaded: ${smartQueue.size} songs added to UI State.")
                
                // 5. Update Player (APPEND NEW SONGS)
                withContext(Dispatchers.Main) {
                    val controller = mediaController
                    if (controller != null) {
                        // Create MediaItems for the queue (using raw audioUrl, will be resolved later)
                        val newMediaItems = queueWithArtists.map { 
                            createMediaItem(it, it.song.audioUrl ?: "") 
                        }
                        controller.addMediaItems(newMediaItems)
                        Log.d("PlayMusicViewModel", "✅ Added ${newMediaItems.size} songs from Smart Queue to Player.")
                    }
                }
            }
        }
    }

    private suspend fun resolveSongUrl(song: SongWithArtist): String {
        // Logika disederhanakan: Serahkan ke Repository
        // Repository akan otomatis cek SQLite -> Cek Expired -> Request API jika perlu
        val result = repository.getPlayableUrl(
            songId = song.song.id,
            title = song.song.title,
            telegramFileId = song.song.telegramFileId,
            fallbackUrl = song.song.audioUrl,
            artistId = song.artist?.id
        )

        Log.d("PlayMusicViewModel", "✅ RESOLVED URL [${result.source}]: ${result.url}")
        return result.url
    }

    // --- SLEEP TIMER ---
    fun setSleepTimer(minutes: Int) {
        Log.d("PlayMusicViewModel", "⏰ setSleepTimer called: $minutes minutes")
        val controller = mediaController
        if (controller == null) {
            Log.e("PlayMusicViewModel", "❌ ERROR: MediaController is NULL. Cannot set timer.")
            return
        }

        val durationMs = minutes * 60 * 1000L
        val command = androidx.media3.session.SessionCommand("START_SLEEP_TIMER", android.os.Bundle.EMPTY)
        
        val args = android.os.Bundle().apply {
            putLong("DURATION", durationMs)
        }

        val result = controller.sendCustomCommand(command, args)
        Log.d("PlayMusicViewModel", "🚀 Command START_SLEEP_TIMER sent with duration: $durationMs ms")
        
        _uiState.update { it.copy(isSleepTimerActive = true) }
    }

    fun cancelSleepTimer() {
        Log.d("PlayMusicViewModel", "⏰ cancelSleepTimer called")
        val controller = mediaController
        if (controller == null) {
             Log.e("PlayMusicViewModel", "❌ ERROR: MediaController is NULL.")
             return
        }

        val command = androidx.media3.session.SessionCommand("STOP_SLEEP_TIMER", android.os.Bundle.EMPTY)
        controller.sendCustomCommand(command, android.os.Bundle.EMPTY)
        
        _uiState.update { it.copy(isSleepTimerActive = false) }
        Log.d("PlayMusicViewModel", "🚀 Command STOP_SLEEP_TIMER sent")
        Log.d("PlayMusicViewModel", "🚀 Command STOP_SLEEP_TIMER sent")
    }

    // --- FITUR LIKE / UNLIKE ---
    fun toggleLike() {
        val currentSong = _uiState.value.currentSong?.song ?: return
        val currentIsLiked = _uiState.value.isLiked
        val userId = SupabaseManager.client.auth.currentSessionOrNull()?.user?.id

        if (userId == null) {
            Log.e("PlayMusicViewModel", "❌ Toggle Like Failed: User not logged in")
            return
        }

        // Optimistic Update (Langsung ubah UI biar cepat)
        _uiState.update { it.copy(isLiked = !currentIsLiked) }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.toggleLike(currentSong.id, userId, currentIsLiked)
            } catch (e: Exception) {
                // Handle Duplicate Key (Data sudah ada, tapi UI telat tau)
                if (e.message?.contains("duplicate key") == true || e.message?.contains("23505") == true) {
                    Log.w("PlayMusicViewModel", "⚠️ Like Conflict: Data sudah ada. Force update UI ke Liked.")
                    _uiState.update { it.copy(isLiked = true) }
                } else {
                    // Revert on real failure
                     _uiState.update { it.copy(isLiked = currentIsLiked) }
                     Log.e("PlayMusicViewModel", "❌ Toggle Like Failed: ${e.message}")
                }
            }
        }
    }

    private fun checkIfLiked(songId: String) {
        val userId = SupabaseManager.client.auth.currentSessionOrNull()?.user?.id
        if (userId == null) {
            _uiState.update { it.copy(isLiked = false) }
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            val isLiked = repository.isSongLiked(songId, userId)
            _uiState.update {
                // Cek apalah lagu yang aktif masih sama?
                if (it.currentSong?.song?.id == songId) {
                    it.copy(isLiked = isLiked)
                } else {
                    it
                }
            }
        }
    }

    // --- [TAMBAHKAN 3 FUNGSI INI DI PALING BAWAH] ---

    // 1. Helper buat bikin MediaItem biar gak duplikat kode
    @OptIn(UnstableApi::class)
    private fun createMediaItem(data: SongWithArtist, streamUrl: String): MediaItem {
        val meta = MediaMetadata.Builder()
            .setTitle(data.song.title)
            .setArtist(data.artist?.name ?: "Unknown")
            .setArtworkUri(data.song.coverUrl?.takeIf { it.isNotBlank() }?.toUri())
            .build()

        return MediaItem.Builder()
            .setMediaId(data.song.id)
            .setUri(streamUrl) // URL bisa dari Telegram atau Supabase
            .setCustomCacheKey(data.song.id)
            .setMediaMetadata(meta)
            .build()
    }

    // 2. Logic Pre-fetching Background
    private fun prefetchSongAtIndex(index: Int) {
        val playlist = _uiState.value.playlist

        // Cek validitas index (Harus dalam range 0 s/d size-1)
        if (index < 0 || index >= playlist.size) return

        val targetSong = playlist[index]

        viewModelScope.launch(Dispatchers.IO) {
            // ⏳ DELAY PENTING: Tunggu 2 detik!
            // Biarkan lagu utama loading dulu sampai selesai, baru kita urus background.
            delay(2000)

            // Cek lagi (siapa tau user udah ganti lagu dalam 2 detik tadi)
            if (index == _uiState.value.currentSongIndex) return@launch

            // Cek apakah lagu ini punya Telegram ID dan BELUM ada di Cache?
            if (!targetSong.song.telegramFileId.isNullOrBlank()) {
                Log.d("PlayMusicViewModel", "🚀 PRE-FETCH START: Menyiapkan lagu tetangga (Index $index): ${targetSong.song.title}")

                val newUrl = resolveSongUrl(targetSong)

                if (newUrl != targetSong.song.audioUrl) {
                    updateMediaItemUrlInPlayer(index, targetSong, newUrl)
                }
            }
        }
    }

    // 3. Update Playlist ExoPlayer Diam-diam
    private suspend fun updateMediaItemUrlInPlayer(index: Int, song: SongWithArtist, newUrl: String) {
        withContext(Dispatchers.Main) {
            val controller = mediaController ?: return@withContext
            if (index >= controller.mediaItemCount) return@withContext

            val newItem = createMediaItem(song, newUrl)
            controller.replaceMediaItem(index, newItem)
            Log.d("Remusic", "Berhasil update link streaming untuk lagu index: $index")
        }
    }

    fun refreshCurrentSongData() {
        val currentSong = _uiState.value.currentSong

        // 1. Cek Data Lirik: Kalau lagu ada tapi lirik kosong, paksa ambil dari database
        if (currentSong != null && currentSong.song.lyrics.isNullOrBlank()) {
            Log.d("DEBUG_PLAYER","🔄 REFRESH: UI baru bangun, lirik kosong. Mengambil ulang detail lagu...")
            fetchFullSongDetails(currentSong.song.id)
        }

        // 2. Cek Posisi: Pastikan seekbar jalan lagi
        mediaController?.let {
            if (it.isPlaying) startUpdatingPosition()
        }

        // 3. Cek Ulang Status Like (Penting kalau user like dari device lain/web)
        if (currentSong != null) {
            checkIfLiked(currentSong.song.id)
        }
    }

    override fun onCleared() {
        super.onCleared()
        mediaController?.release()
    }
}