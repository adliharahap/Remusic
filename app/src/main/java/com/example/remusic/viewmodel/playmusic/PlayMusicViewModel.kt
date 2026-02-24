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
import com.example.remusic.data.SupabaseManager
import com.example.remusic.data.local.MusicDatabase
import com.example.remusic.data.model.Artist
import com.example.remusic.data.model.ArtistDetails
import com.example.remusic.data.model.AudioOutputDevice
import com.example.remusic.data.model.Song
import com.example.remusic.data.model.SongWithArtist
import com.example.remusic.data.model.User
import com.example.remusic.data.preferences.UserPreferencesRepository
import com.example.remusic.data.repository.MusicRepository
import com.example.remusic.services.MusicService
import com.example.remusic.utils.extractGradientColorsFromImageUrl
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
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
        val repeatMode: Int =
                Player.REPEAT_MODE_OFF, // REPEAT_MODE_OFF, REPEAT_MODE_ONE, REPEAT_MODE_ALL
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
        val isArtistFollowed: Boolean = false, // New State

        // --- Artist Songs Pagination ---
        val artistSongs: List<SongWithArtist> = emptyList(),
        val isArtistSongsLoading: Boolean = false,
        val artistSongsEndReached: Boolean = false,
        val artistSongsPage: Int = 0, // 0-indexed page or use offset directly // New State
        val uploader: User? = null,
        val playlistSubtitle: String = "Memainkan Dari Playlist", // Default

        // Error state tracking
        val hasError: Boolean = false,
        val errorType: String? = null,

        // Lyrics Config
        // Lyrics Config
        val lyricsConfig: com.example.remusic.ui.screen.playmusic.LyricsConfig =
                com.example.remusic.ui.screen.playmusic.LyricsConfig(),

        // Global Queue Options State
        val selectedSongForQueueOptions: SongWithArtist? = null,

        // Snackbar State for Queue Info
        val showQueueInfoSnackbar: Boolean = false,

        // Playlist Details State (For Playlist Header)
        val currentPlaylistDetails: com.example.remusic.data.model.Playlist? = null,
        val playlistOwner: User? = null,

        // Artist Details State (For Playlist Header)
        val artistDetails: ArtistDetails? = null,
        val isLoadingArtistDetails: Boolean = false,

        // --- Tab Tracking for "Lihat Playlist" Navigation ---
        val previousTab: String = "home", // Default to Home tab route
        val navigateToArtistEvent: String? = null, // One-shot event: artistId to navigate to
        val pendingArtistNavigation: String? = null, // Pending artistId for tab screens to consume
        val gradientStyle: com.example.remusic.data.preferences.GradientStyle = com.example.remusic.data.preferences.GradientStyle.PRIMARY,
        val gradientTopColorIndex: Int = 0,
        val gradientBottomColorIndex: Int = 1,
        
        // Data Saver
        val isDataSaverModeEnabled: Boolean = false,

        // Add to Playlist State
        val selectedSongForAddToPlaylist: SongWithArtist? = null,
        val userPlaylists: List<com.example.remusic.data.model.Playlist> = emptyList(),
        val isFetchingUserPlaylists: Boolean = false
)

class PlayMusicViewModel(application: Application) : AndroidViewModel(application) {

    // untuk fitur fade ketika play, pause di klik
    companion object {
        private const val FADE_DURATION_MS = 500L // Durasi fade dalam milidetik
        private const val FADE_INTERVAL_MS = 20L // Seberapa sering volume diupdate
    }

    private var fetchLyricsJob: Job? = null
    private var searchDebounceJob: Job? = null

    // variabel untuk playsongat
    private var jumpJob: Job? = null
    private var setPlaylistJob: Job? = null
    private var preSearchShuffleState: Boolean =
            false // ← Simpan state shuffle sebelum masuk search

    // --- VARIABEL UNTUK MENGELOLA FADE ---
    private var volumeFadeJob: Job? = null
    private var isFading = false

    private val _uiState = MutableStateFlow(PlayerUiState())

    // ===================== ARTIST NAVIGATION SHARED FLOW =====================
    // replay=1 ensures late subscribers still receive the last emitted artistId
    private val _artistNavigationFlow =
            MutableSharedFlow<String?>(
                    replay = 1,
                    extraBufferCapacity = 1,
                    onBufferOverflow = BufferOverflow.DROP_OLDEST
            )
    val artistNavigationFlow = _artistNavigationFlow.asSharedFlow()

    // Variabel untuk melacak lagu terakhir yang play count-nya sudah di-increment
    private var lastIncrementedSongId: String? = null

    // --- TRACKING REAL LISTENING TIME (ANTI-CHEAT) ---
    private var actualListeningTimeMs: Long = 0L // Waktu dengar asli (akumulasi)
    private var lastSongIdForTracking: String? = null // ID lagu terakhir yang sedang dilacak
    private var lastUpdateTime: Long = System.currentTimeMillis() // Untuk delta time tracking

    // Cache memory: Key = TelegramFileID, Value = Link Streaming Direct
    private val database = MusicDatabase.getDatabase(application)
    private val repository = MusicRepository(database.musicDao())

    val uiState = _uiState.asStateFlow()

    private var mediaController: MediaController? = null

    private val userPreferencesRepository = UserPreferencesRepository(getApplication())

    init {
        connectToService()

        viewModelScope.launch {
            userPreferencesRepository.isDataSaverModeEnabled.collect { isEnabled ->
                _uiState.update { it.copy(isDataSaverModeEnabled = isEnabled) }
            }
        }

        // Collect lyrics config from DataStore
        viewModelScope.launch {
            userPreferencesRepository.lyricsConfigFlow.collect { config ->
                _uiState.update { it.copy(lyricsConfig = config) }
            }
        }

        // Collect gradient style config from DataStore
        viewModelScope.launch {
            userPreferencesRepository.gradientStyleFlow.collect { style ->
                _uiState.update { it.copy(gradientStyle = style) }
                // Re-extract color if song is currently loaded
                _uiState.value.currentSong?.song?.coverUrl?.let { url ->
                    viewModelScope.launch {
                        val colors = extractGradientColorsFromImageUrl(
                            getApplication(), url, gradientStyle = style
                        )
                        _uiState.update { it.copy(dominantColors = colors) }
                    }
                }
            }
        }

        // Collect top and bottom color indices
        viewModelScope.launch {
            userPreferencesRepository.gradientTopColorIndexFlow.collect { index ->
                _uiState.update { it.copy(gradientTopColorIndex = index) }
            }
        }
        viewModelScope.launch {
            userPreferencesRepository.gradientBottomColorIndexFlow.collect { index ->
                _uiState.update { it.copy(gradientBottomColorIndex = index) }
            }
        }
    }

    @OptIn(UnstableApi::class)
    private fun connectToService() {
        val sessionToken =
                SessionToken(
                        getApplication(),
                        ComponentName(getApplication(), MusicService::class.java)
                )

        // Listener khusus untuk MediaController (Session Extras / Sleep Timer)
        val controllerListener =
                object : MediaController.Listener {
                    override fun onExtrasChanged(
                            controller: MediaController,
                            extras: android.os.Bundle
                    ) {
                        super.onExtrasChanged(controller, extras)
                        val isTimerActive = extras.getBoolean("IS_SLEEP_TIMER_ACTIVE", false)
                        val timerEndTime = extras.getLong("SLEEP_TIMER_END_TIME", 0L)

                        Log.d(
                                "PlayMusicViewModel",
                                "🔔 EXTRAS CHANGED: Sleep Timer Active = $isTimerActive, End = $timerEndTime"
                        )

                        // Update UI State (Hanya jika berbeda biar gak loop)
                        if (_uiState.value.isSleepTimerActive != isTimerActive ||
                                        _uiState.value.sleepTimerEndTime != timerEndTime
                        ) {
                            _uiState.update {
                                it.copy(
                                        isSleepTimerActive = isTimerActive,
                                        sleepTimerEndTime =
                                                if (isTimerActive) timerEndTime else null
                                )
                            }
                        }
                    }
                }

        val controllerFuture =
                MediaController.Builder(getApplication(), sessionToken)
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

                    // --- RESTORE PLAYBACK QUEUE & STATE ---
                    viewModelScope.launch { restorePlaybackState() }

                    val lastIndex =
                            _uiState.value.playlist.indexOfFirst {
                                it.song.id == _uiState.value.currentSong?.song?.id
                            }
                    if (lastIndex >= 0) {
                        setPlaylist(_uiState.value.playlist, lastIndex)
                    }
                },
                ContextCompat.getMainExecutor(getApplication())
        )
    }

    private fun setupPlayerListener() {
        mediaController?.addListener(
                object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        super.onPlaybackStateChanged(playbackState)
                        _uiState.update {
                            it.copy(isBuffering = playbackState == Player.STATE_BUFFERING)
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

                        // Save state on pause or specific events
                        if (events.contains(Player.EVENT_PLAY_WHEN_READY_CHANGED) &&
                                        !player.playWhenReady
                        ) {
                            savePlaybackState()
                        }
                        if (events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION)) {
                            savePlaybackState()
                        }
                        // Save state on Seek
                        if (events.contains(Player.EVENT_POSITION_DISCONTINUITY)) {
                            savePlaybackState()
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
                        if (error.errorCode ==
                                        androidx.media3.common.PlaybackException
                                                .ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
                                        error.errorCode ==
                                                androidx.media3.common.PlaybackException
                                                        .ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ||
                                        error.errorCode ==
                                                androidx.media3.common.PlaybackException
                                                        .ERROR_CODE_BEHIND_LIVE_WINDOW
                        ) {

                            Log.w(
                                    "PlayMusicViewModel",
                                    "🔄 Network error detected, attempting auto-recovery..."
                            )
                            handleAutoRecovery()
                        }
                    }

                    // Listener ini akan update UI jika lagu di service berubah (misal dari
                    // notifikasi)
                    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                        super.onMediaItemTransition(mediaItem, reason)

                        val reasonStr =
                                when (reason) {
                                    Player.MEDIA_ITEM_TRANSITION_REASON_AUTO ->
                                            "AUTO (Next Otomatis)"
                                    Player.MEDIA_ITEM_TRANSITION_REASON_SEEK ->
                                            "SEEK (Manual Klik/Geser)"
                                    Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED ->
                                            "PLAYLIST CHANGED"
                                    else -> "UNKNOWN"
                                }

                        Log.d("DEBUG_PLAYER", "🔀 TRANSISI: Ganti lagu karena $reasonStr")

                        val player = mediaController ?: return

                        val newIndex = player.currentMediaItemIndex
                        val oldIndex = _uiState.value.currentSongIndex

                        Log.d("DEBUG_PLAYER", "   📍 Perubahan Index: $oldIndex -> $newIndex")

                        // Tentukan arah berdasarkan perubahan indeks
                        val direction =
                                when {
                                    newIndex > oldIndex -> AnimationDirection.FORWARD
                                    newIndex < oldIndex -> AnimationDirection.BACKWARD
                                    else ->
                                            _uiState.value
                                                    .animationDirection // pertahankan arah jika
                                // sama
                                }

                        if (mediaItem != null) {
                            val playlist = _uiState.value.playlist
                            val matchedSong = playlist.find { it.song.id == mediaItem.mediaId }

                            // 🔥 FIX: Pre-fetch dipindah ke bawah SETELAH matchedSong check
                            // dan HANYA untuk transisi AUTO/SEEK, bukan PLAYLIST_CHANGED!
                            // Memanggil pre-fetch di sini (sebelum check) menyebabkan feedback
                            // loop.

                            if (matchedSong != null) {
                                // 1. UPDATE UI STATE IMMEDIATELY (Agar judul lagu berubah instan)
                                _uiState.update {
                                    it.copy(
                                            currentSong = matchedSong,
                                            currentSongIndex = newIndex,
                                            animationDirection = direction,
                                            uploader = null, // Reset uploader for new song
                                            hasError = false, // Reset error state on new song
                                            errorType = null
                                    )
                                }

                                // Reset Smart Retry Flag on transition
                                hasRetriedCurrentSong = false

                                Log.d(
                                        "DEBUG_PLAYER",
                                        "🎵 Lagu Terdeteksi: ${matchedSong.song.title}"
                                )

                                // 🔍 CRITICAL FIX: Cek URL sebelum play (ASYNC)
                                // Jika URL kosong/invalid, resolve dulu sebelum ExoPlayer coba load
                                val currentUrl = mediaItem.localConfiguration?.uri?.toString() ?: ""
                                val isUrlValid =
                                        currentUrl.isNotBlank() &&
                                                (currentUrl.startsWith("http") ||
                                                        currentUrl.startsWith("content"))

                                if (!isUrlValid) {
                                    Log.w(
                                            "DEBUG_PLAYER",
                                            "⚠️ URL KOSONG/INVALID di index $newIndex. Resolving URL dulu..."
                                    )

                                    // Pause player sementara
                                    player.pause()

                                    // Resolve URL di background
                                    viewModelScope.launch(Dispatchers.IO) {
                                        try {
                                            val result = resolveSongUrl(matchedSong)
                                            val newUrl = result.url

                                            withContext(Dispatchers.Main) {
                                                // 🔥 CRITICAL FIX: STOP AUTO-SKIP ON NETWORK ERROR
                                                // 🔥
                                                if (result.errorType ==
                                                                MusicRepository.ErrorType.NETWORK &&
                                                                newUrl.isEmpty()
                                                ) {
                                                    Log.e(
                                                            "DEBUG_PLAYER",
                                                            "❌ NETWORK ERROR on Auto-Next. PAUSING PLAYER."
                                                    )
                                                    player.pause()
                                                    _uiState.update {
                                                        it.copy(
                                                                hasError = true,
                                                                errorType =
                                                                        "Koneksi Terputus. Playback dipause.",
                                                                isPlaying = false,
                                                                isLoadingData = false // Stop infinite loading spinner
                                                        )
                                                    }
                                                } else if (newUrl.isNotBlank()) {
                                                    Log.d("DEBUG_PLAYER", "✅ URL Resolved: $newUrl")

                                                    // Update MediaItem dengan URL valid
                                                    val newItem =
                                                            createMediaItem(matchedSong, newUrl)
                                                    player.replaceMediaItem(newIndex, newItem)
                                                    // 🔥 FIX SHUFFLE CASCADE:
                                                    // seekTo() memaksa ExoPlayer kembali ke index
                                                    // yang benar
                                                    // sebelum play(). Tanpa ini, ExoPlayer (shuffle
                                                    // mode) bisa
                                                    // melompat ke item acak lain setelah
                                                    // replaceMediaItem().
                                                    player.seekTo(newIndex, 0)
                                                    player.play()
                                                } else {
                                                    Log.e(
                                                            "DEBUG_PLAYER",
                                                            "❌ Gagal resolve URL (Fatal/Not Found). Skip lagu ini.")
                                                    
                                                    // Stop infinite loading spinner since there is no data to load
                                                    _uiState.update { it.copy(isLoadingData = false) }

                                                    // Skip ke lagu berikutnya
                                                    if (player.hasNextMediaItem()) {
                                                        player.seekToNextMediaItem()
                                                    } else {
                                                        // Stop gracefully if it was the last song
                                                        player.pause()
                                                    }
                                                }
                                            }
                                        } catch (e: Exception) {
                                            Log.e(
                                                    "DEBUG_PLAYER",
                                                    "❌ Error resolving URL: ${e.message}"
                                            )
                                            _uiState.update { it.copy(isLoadingData = false) } // Pastikan spinner berhenti saat exception
                                        }
                                    }
                                } else {
                                    // URL is already valid. If the player was previously stuck/paused due to an error, we need to make sure it plays.
                                    if (!player.isPlaying && player.playbackState != Player.STATE_IDLE) {
                                        player.play()
                                    }
                                }

                                // PRE-FETCH: Siapkan lagu tetangga HANYA untuk AUTO/SEEK.
                                // PLAYLIST_CHANGED (dari replaceMediaItem) DILARANG mem-prefetch
                                // karena menyebabkan feedback loop yaitu:
                                // replaceMediaItem → PLAYLIST_CHANGED → prefetch → replaceMediaItem
                                // → ...
                                if (reason != Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED
                                ) {
                                    prefetchSongAtIndex(newIndex + 1)
                                    prefetchSongAtIndex(newIndex - 1)
                                }

                                // PRE-FETCH Data Lengkap (Lirik, dll)
                                fetchFullSongDetails(matchedSong.song.id)

                                // --- LOGIKA WARNA DITAMBAHKAN DI SINI JUGA ---
                                // Saat lagu berganti, ekstrak warna dari lagu yang baru.
                                viewModelScope.launch {
                                    val colors =
                                            extractGradientColorsFromImageUrl(
                                                    context = getApplication(),
                                                    imageUrl = matchedSong.song.coverUrl ?: "",
                                                    gradientStyle = _uiState.value.gradientStyle
                                            )
                                    _uiState.update { it.copy(dominantColors = colors) }
                                    // Save to preferences for HomeScreen
                                    userPreferencesRepository.saveLastSongColor(
                                            colors.getOrElse(0) { Color(0xFF755D8D) }
                                    )
                                }

                                // 3. CEK STATUS LIKE (PENTING: Biar UI update saat ganti lagu
                                // otomatis)
                                checkIfLiked(matchedSong.song.id)
                            } else {
                                Log.w(
                                        "DEBUG_PLAYER",
                                        "   ⚠️ FALLBACK: Menggunakan metadata dari MediaItem (Lagu tidak ada di list local)"
                                )
                                // fallback kalau nggak ketemu di playlist
                                _uiState.update {
                                    it.copy(
                                            currentSong =
                                                    SongWithArtist(
                                                            song =
                                                                    Song(
                                                                            id = mediaItem.mediaId,
                                                                            title =
                                                                                    mediaItem
                                                                                            .mediaMetadata
                                                                                            .title
                                                                                            ?.toString()
                                                                                            ?: "Unknown",
                                                                            audioUrl =
                                                                                    mediaItem
                                                                                            .localConfiguration
                                                                                            ?.uri
                                                                                            .toString(),
                                                                            coverUrl =
                                                                                    mediaItem
                                                                                            .mediaMetadata
                                                                                            .artworkUri
                                                                                            .toString(),
                                                                            lyrics = "" // default
                                                                            // kosong
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
                        Log.d(
                                "PlayMusicViewModel",
                                "LISTENER: Shuffle mode changed to: $shuffleModeEnabled"
                        )
                        _uiState.update { it.copy(isShuffleModeEnabled = shuffleModeEnabled) }

                        viewModelScope.launch {
                            userPreferencesRepository.setShuffleEnabled(shuffleModeEnabled)
                            Log.d(
                                    "PlayMusicViewModel",
                                    "Saved new shuffle mode: $shuffleModeEnabled"
                            )
                        }
                    }

                    override fun onRepeatModeChanged(repeatMode: Int) {
                        _uiState.update { it.copy(repeatMode = repeatMode) }

                        viewModelScope.launch {
                            userPreferencesRepository.setRepeatMode(repeatMode)
                            Log.d("PlayMusicViewModel", "Saved new repeat mode: $repeatMode")
                        }
                    }
                }
        )
    }

    /**
     * Auto-recovery mechanism untuk handle playback errors Akan mencoba resume playback di posisi
     * terakhir setelah 2 detik
     */
    private fun handleAutoRecovery() {
        viewModelScope.launch {
            val controller = mediaController ?: return@launch
            val currentPosition = controller.currentPosition
            val currentMediaItem = controller.currentMediaItem

            if (currentMediaItem != null && currentPosition >= 0) {
                delay(2000) // Wait 2s sebelum retry (beri waktu network pulih)

                Log.d(
                        "PlayMusicViewModel",
                        "↩️ Recovering playback at ${currentPosition}ms for: ${currentMediaItem.mediaMetadata.title}"
                )

                // FIX: JANGAN gunakan setMediaItem di sini karena akan menghapus seluruh antrean (playlist) ExoPlayer!
                // ExoPlayer tetap menyimpan playlist meskipun error. Cukup panggil prepare() untuk retry lagu saat ini.
                if (controller.playbackState == Player.STATE_IDLE) {
                    controller.prepare()
                }
                controller.seekTo(currentPosition)
                controller.playWhenReady = true

                // Clear error state
                _uiState.update { it.copy(hasError = false, errorType = null) }
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
        positionJob =
                viewModelScope.launch { // Launch di Main Thread (Default)
                    val cache = com.example.remusic.utils.RemusicCache.getInstance(getApplication())
                    var loopCounter = 0

                    while (currentCoroutineContext().isActive) {
                        val controller = mediaController
                        if (controller != null && controller.isPlaying) {
                            // 1. Ambil data Player di Main Thread (Safe)
                            val pos = controller.currentPosition
                            val dur = controller.duration
                            val currentUri =
                                    controller.currentMediaItem?.localConfiguration?.uri?.toString()

                            // 2. Cek Cache di IO Thread (Hanya setiap 2 detik = 4x loop)
                            // Optimasi untuk "HP Kentang" agar tidak boros resource context switch
                            val statusText =
                                    if (loopCounter % 4 == 0) {
                                        withContext(Dispatchers.IO) {
                                            // Priority: Error > Buffering > Loading > Cache Status
                                            if (_uiState.value.hasError) {
                                                "Error: ${_uiState.value.errorType ?: "Unknown"}"
                                            } else if (_uiState.value.isBuffering) {
                                                "Buffering..."
                                            } else if (_uiState.value.isLoadingLyrics ||
                                                            _uiState.value.isLoadingData
                                            ) {
                                                "Mendapatkan Data..."
                                            } else if (currentUri != null) {
                                                val isCached =
                                                        try {
                                                            cache.isCached(currentUri, pos, 1024)
                                                        } catch (_: Exception) {
                                                            false
                                                        }
                                                if (isCached) "Playing music offline"
                                                else "Playing music online"
                                            } else {
                                                "Menyiapkan..."
                                            }
                                        }
                                    } else {
                                        // Gunakan status terakhir (biar hemat CPU)
                                        _uiState.value.debugStatus
                                    }

                            // --- LOGIC PLAY COUNT V2 (REAL LISTENING TIME with Delta Time) ---
                            val currentSongId = _uiState.value.currentSong?.song?.id

                            // 1. Reset jika ganti lagu
                            if (currentSongId != lastSongIdForTracking) {
                                actualListeningTimeMs = 0L
                                lastIncrementedSongId = null
                                lastSongIdForTracking = currentSongId
                                lastUpdateTime =
                                        System.currentTimeMillis() // Reset delta time tracker
                                Log.d(
                                        "PlayMusicViewModel",
                                        "🔄 [TRACKING RESET] New Song Detected: $currentSongId"
                                )
                            }

                            if (currentSongId != null && dur > 0) {
                                // 2. Calculate delta time (ACCURATE tracking instead of fixed
                                // 1000ms)
                                val now = System.currentTimeMillis()
                                val deltaTime = now - lastUpdateTime
                                lastUpdateTime = now

                                // 3. Akumulasi waktu berdasarkan delta time REAL
                                actualListeningTimeMs += deltaTime

                                // 4. Hitung Threshold (60% Durasi Total)
                                val thresholdMs = (dur * 0.6).toLong()

                                // 5. Cek Threshold Waktu Asli (Bukan Posisi Seekbar)
                                if (actualListeningTimeMs >= thresholdMs &&
                                                currentSongId != lastIncrementedSongId
                                ) {
                                    Log.d(
                                            "PlayMusicViewModel",
                                            "[VALID PLAY] User listening for ${actualListeningTimeMs/1000}s. Incrementing..."
                                    )
                                    viewModelScope.launch(Dispatchers.IO) {
                                        repository.incrementPlayCount(currentSongId)
                                    }
                                    lastIncrementedSongId =
                                            currentSongId // Lock agar tidak double count
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

                            // 4. PERIODIC SAVE (Setiap 3 Detik = 60 loop * 50ms)
                            if (loopCounter > 0 && loopCounter % 60 == 0) {
                                savePlaybackState()
                                Log.d(
                                        "PlayMusicViewModel",
                                        "💾 [PERIODIC SAVE] Saved position: ${pos}ms"
                                )
                            }

                            loopCounter++
                        }
                        // 🔥 CRITICAL FIX: Changed from delay(1000) to delay(50)
                        // This gives us 20 position updates per second instead of 1
                        // Result: Lyric sync accuracy improved from ±1000ms to ±50ms
                        delay(50)
                    }
                }
    }

    private fun stopUpdatingPosition() {
        positionJob?.cancel()
        positionJob = null
    }

    private fun fetchFullSongDetails(songId: String, forceUpdate: Boolean = false) {
        fetchLyricsJob?.cancel()
        searchDebounceJob?.cancel()
        searchDebounceJob =
                viewModelScope.launch(Dispatchers.IO) {
                    delay(1000)
                    fetchLyricsJob = launch {
                        _uiState.update { it.copy(isLoadingLyrics = true) }
                        try {
                            var attempt = 1
                            val maxAttempts = 2
                            var isSuccess = false

                            while (attempt <= maxAttempts && !isSuccess) {

                                if (!isActive) break

                                try {
                                    Log.d(
                                            "DEBUG_PLAYER",
                                            "🔄 FETCH Attempt #$attempt untuk ID: $songId"
                                    )
                                    val timeoutMs = if (attempt == 1) 5000L else 10000L
                                    val fullSongData =
                                            withTimeout(timeoutMs) {
                                                repository.getFullSongDetails(songId)
                                            }
                                    ensureActive()

                                    if (fullSongData != null) {

                                        _uiState.update { state ->
                                            if (state.currentSong?.song?.id == songId) {
                                                state.copy(
                                                        currentSong =
                                                                state.currentSong.copy(
                                                                        song =
                                                                                state.currentSong
                                                                                        .song.copy(
                                                                                        lyrics =
                                                                                                fullSongData
                                                                                                        .lyrics,
                                                                                        uploaderUserId =
                                                                                                fullSongData
                                                                                                        .uploaderUserId,
                                                                                        artistId =
                                                                                                fullSongData
                                                                                                        .artistId
                                                                                )
                                                                )
                                                )
                                            } else state
                                        }

                                        // FETCH ARTIST
                                        fullSongData.artistId?.let { artistId ->
                                            val artistDetails =
                                                    repository.fetchArtistDetails(artistId)
                                            if (artistDetails != null) {
                                                _uiState.update { state ->
                                                    if (state.currentSong?.song?.id == songId) {
                                                        state.copy(
                                                                currentSong =
                                                                        state.currentSong.copy(
                                                                                artist =
                                                                                        artistDetails
                                                                        )
                                                        )
                                                    } else state
                                                }
                                                Log.d(
                                                        "DEBUG_PLAYER",
                                                        "✅ Artist Fetched: ${artistDetails.name}"
                                                )
                                                checkIfArtistFollowed(artistDetails.id)
                                            }
                                            isSuccess = true
                                        }
                                    } else {
                                        Log.w("DEBUG_PLAYER", "⚠️ Data server null. Stop retry.")
                                        break
                                    }
                                } catch (e: Exception) {
                                    if (e is kotlinx.coroutines.CancellationException) throw e
                                    Log.w(
                                            "DEBUG_PLAYER",
                                            "❌ Gagal Percobaan #$attempt: ${e.message}"
                                    )
                                    if (attempt < maxAttempts) delay(2000)
                                }
                                attempt++
                            }
                        } catch (e: Exception) {
                            if (e !is kotlinx.coroutines.CancellationException) {
                                Log.e("DEBUG_PLAYER", "❌ CRASH FETCH LIRIK: ${e.message}")
                            }
                        } finally {
                            _uiState.update { state ->
                                if (state.currentSong?.song?.id == songId) {
                                    Log.d(
                                            "DEBUG_PLAYER",
                                            "🏁 FETCH SELESAI/CANCEL: Matikan Loading untuk $songId"
                                    )
                                    state.copy(isLoadingLyrics = false)
                                } else state
                            }
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

    fun setPlaylist(
            songs: List<SongWithArtist>,
            startIndex: Int = 0,
            overridePlaylistName: String? = null
    ): Job? {
        Log.d(
                "DEBUG_PLAYER",
                "📥 SET PLAYLIST: Menerima ${songs.size} lagu. Start Index: $startIndex"
        )

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

        Log.d(
                "PlayMusicViewModel",
                "setPlaylist called with ${songs.size} songs, startIndex=$startIndex"
        )

        val finalPlaylistName = overridePlaylistName ?: _uiState.value.playingMusicFromPlaylist

        // 2. Update UI State Langsung (Biar user lihat judul lagu berubah cepat)
        _uiState.update {
            it.copy(
                    playlist = songs,
                    currentSong = songs[startIndex],
                    playingMusicFromPlaylist = finalPlaylistName
            )
        }

        // SAVE QUEUE TO DB
        viewModelScope.launch(Dispatchers.IO) {
            repository.savePlaybackQueue(songs, finalPlaylistName)
            userPreferencesRepository.saveLastPlaylistName(finalPlaylistName)
        }

        // Reset Smart Retry Flag
        hasRetriedCurrentSong = false

        // 3. MULAI PROSES ASYNC (Resolve URL & Warna)
        // Kita bungkus semua proses mapping & player setup di sini
        // RETURN JOB agar bisa ditunggu (join)
        return viewModelScope.launch(Dispatchers.IO) {

            // A. Ambil Lagu yang mau dimainkan
            val songToPlay = songs.getOrNull(startIndex) ?: return@launch

            Log.d(
                    "DEBUG_PLAYER",
                    "⚡ PROCESS: Resolving URL untuk lagu pertama (Index $startIndex): ${songToPlay.song.title}"
            )
            // B. Resolve URL (Tunggu sebentar request ke Vercel/Cache)
            // Ini langkah kuncinya! Kita putuskan mau pakai link Telegram atau AudioURL biasa
            val resolvedResult = resolveSongUrl(songToPlay)
            val resolvedUrl = resolvedResult.url
            Log.d("PlayMusicViewModel", "URL Resolved for first song: $resolvedUrl")

            // Cek Error Network di awal play
            if (resolvedResult.errorType == MusicRepository.ErrorType.NETWORK) {
                withContext(Dispatchers.Main) {
                    _uiState.update {
                        it.copy(
                                hasError = true,
                                errorType = "Gagal memuat: Masalah Koneksi Internet",
                                isPlaying = false
                        )
                    }
                }
                // Masih lanjut coba play (mungkin ada cache parsial?), atau stop?
                // Kita lanjut aja, nanti ExoPlayer bakal error juga kalau URL kosong, tapi minimal
                // UI udah kasih tau.
            }

            // C. Fetch Data Lengkap (Lirik, dll) untuk lagu pertama
            fetchFullSongDetails(songToPlay.song.id)

            // C. Extract Warna (Sambil jalan)
            val colors =
                    extractGradientColorsFromImageUrl(
                            context = getApplication(),
                            imageUrl = songToPlay.song.coverUrl ?: ""
                    )
            // Update warna ke UI
            _uiState.update { it.copy(dominantColors = colors) }
            // Save to preferences for HomeScreen
            userPreferencesRepository.saveLastSongColor(colors.getOrElse(0) { Color(0xFF755D8D) })

            Log.d("DEBUG_PLAYER", "🛠 MAPPING: Membuat MediaItems...")

            // D. Mapping jadi MediaItems (DI DALAM COROUTINE)
            // Kenapa di dalam? Karena kita butuh 'resolvedUrl' untuk index == startIndex
            val mediaItems =
                    songs.mapIndexed { index, s ->
                        val meta =
                                MediaMetadata.Builder()
                                        .setTitle(s.song.title)
                                        .setArtist(s.artist?.name)
                                        .setArtworkUri(
                                                s.song.coverUrl?.takeIf { it.isNotBlank() }?.toUri()
                                        )
                                        .build()

                        // LOGIKA PENTING:
                        // Jika ini lagu yang mau dimainkan (startIndex), pakai 'resolvedUrl' (hasil
                        // Vercel).
                        // Jika lagu lain, pakai 'audioUrl' biasa dulu (nanti di-prefetch sambil
                        // jalan).
                        val urlToUse =
                                if (index == startIndex) resolvedUrl else (s.song.audioUrl ?: "")

                        val item =
                                MediaItem.Builder()
                                        .setMediaId(s.song.id)
                                        .setUri(urlToUse)
                                        .setMediaMetadata(meta)
                                        .build()

                        // Debug log (Sesuai kode Mas)
                        if (index == startIndex) {
                            Log.d(
                                    "DEBUG_PLAYER",
                                    "   🎵 [Index $index] (ACTIVE) ${s.song.title} -> $urlToUse"
                            )
                        } else {
                            Log.d(
                                    "DEBUG_PLAYER",
                                    "   ❓ [Index $index] (QUEUE) URL Masih Mentah: $urlToUse"
                            )
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
                    Log.d(
                            "PlayMusicViewModel",
                            "MediaItems set to controller, playing index=$startIndex"
                    )
                    prepare()
                    play()
                }
                        ?: Log.e("PlayMusicViewModel", "mediaController is null inside coroutine!")
            }
        }
    }

    // --- FITUR BARU: PLAY WITH SMART QUEUE (UNTUK HOME SCREEN QUICK PICK) ---
    fun playSongWithSmartQueue(seedSong: SongWithArtist) {
        Log.d("PlayMusicViewModel", "🚀 START SMART QUEUE for: ${seedSong.song.title}")

        // 1. Play lagu yang diklik DULUAN (Single Item Playlist)
        // Set startIndex = 0 karena cuma ada 1 lagu
        val playJob = setPlaylist(listOf(seedSong), 0)

        // 2. Fetch Smart Queue di background jangan ganggu UI/Playback
        viewModelScope.launch(Dispatchers.IO) {
            // Tunggu sebentar sampai lagu utama mulai disiapkan
            playJob?.join()

            // Fetch Smart Queue (List<Song>)
            val relatedSongs = repository.fetchSmartQueue(seedSong.song)

            if (relatedSongs.isNotEmpty()) {
                Log.d(
                        "PlayMusicViewModel",
                        "✅ Smart Queue returned ${relatedSongs.size} songs. Fetching artist details..."
                )

                // 3. Kita butuh detail Artist biar nama artis muncul di Queue
                // Kumpulkan semua artistId dari hasil smart queue
                val artistIds = relatedSongs.mapNotNull { it.artistId }

                // Fetch artists in bulk
                val artistsMap = repository.fetchArtistsByIds(artistIds).associateBy { it.id }

                // 4. Gabungkan Song + Artist jadi SongWithArtist
                val queueWithArtists =
                        relatedSongs.map { song ->
                            SongWithArtist(
                                    song = song,
                                    artist =
                                            song.artistId?.let {
                                                artistsMap[it]
                                            } // Match artist or null
                            )
                        }

                // 5. Append ke Playlist saat ini
                val currentPlaylist = _uiState.value.playlist.toMutableList()
                currentPlaylist.addAll(queueWithArtists)

                // Simpan total playlist baru
                val finalPlaylist = currentPlaylist.toList()

                // Update UI State (Queue terlihat oleh user)
                _uiState.update { it.copy(playlist = finalPlaylist) }

                // 6. Update Player Queue (Diam-diam)
                withContext(Dispatchers.Main) {
                    val controller = mediaController ?: return@withContext
                    // Kita tambahkan lagu-lagu baru ke posisi setelah seed song
                    // Karena seed song ada di index 0 (atau current index),
                    // kita addMediaItems ke akhir queue.

                    val newMediaItems =
                            queueWithArtists.map { createMediaItem(it, it.song.audioUrl ?: "") }
                    controller.addMediaItems(newMediaItems)

                    Log.d(
                            "PlayMusicViewModel",
                            "✅ Added ${newMediaItems.size} songs to MediaController."
                    )
                }

                // 7. Prefetch lagu berikutnya (Index 1) agar siap dimainkan
                prefetchSongAtIndex(1)
            } else {
                Log.w("PlayMusicViewModel", "⚠️ Smart Queue returned empty list.")
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

        volumeFadeJob =
                viewModelScope.launch {
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

        volumeFadeJob =
                viewModelScope.launch {
                    // Set volume ke 0, lalu mulai mainkan (tidak akan terdengar)
                    controller.volume = 0.0f
                    
                    // FIX: Jika Play di-tap saat lagu error/idle (misal internet putus lalu nyambung), re-prepare
                    if (controller.playbackState == Player.STATE_IDLE || controller.playbackState == Player.STATE_ENDED) {
                        controller.prepare()
                    }
                    
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
        Log.d(
                "PlayMusicViewModel",
                "COMMAND: Toggling shuffle. Current state is $currentShuffleState. Setting to ${!currentShuffleState}"
        )
        mediaController?.shuffleModeEnabled = !currentShuffleState
    }

    /**
     * Set shuffle mode secara LANGSUNG tanpa toggle. Digunakan oleh AutoPlaylistHeader agar tidak
     * ada race condition dengan state Compose yang masih stale.
     */
    fun forceSetShuffle(enabled: Boolean) {
        Log.d("PlayMusicViewModel", "🔀 [FORCE SHUFFLE] Set shuffle to: $enabled")
        mediaController?.shuffleModeEnabled = enabled
    }

    // Fungsi ini akan berputar: OFF -> ONE -> ALL -> OFF
    // FIX: Jika dari Search, hanya OFF -> ONE -> OFF (Skip ALL)
    fun cycleRepeatMode() {
        val isSearchContext = _uiState.value.playlistSubtitle == "Memainkan Dari Pencarian"

        val nextRepeatMode =
                when (_uiState.value.repeatMode) {
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
        val controller = mediaController ?: return
        val nextIndex = controller.nextMediaItemIndex
        if (nextIndex != C.INDEX_UNSET) {
            _uiState.update { it.copy(animationDirection = AnimationDirection.FORWARD) }
            playSongAt(nextIndex)
        }
    }

    fun previousSong() {
        val controller = mediaController ?: return

        // Fitur: Jika lagu sudah main lebih dari 3 detik, restart lagu ini (bukan mundur ke lagu
        // sebelumnya)
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
        Log.d(
                "DEBUG_PLAYER",
                "👇 CLICK MANUAL (FIXED): User ingin memutar index $index: '${targetSong.song.title}'"
        )

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

        // Reset Smart Retry Flag
        hasRetriedCurrentSong = false

        jumpJob =
                viewModelScope.launch(Dispatchers.IO) {
                    try {
                        // 3. CEK APAKAH URL SUDAH VALID?
                        val currentUrl = targetSong.song.audioUrl
                        val isUrlValid =
                                !currentUrl.isNullOrBlank() &&
                                        (currentUrl.startsWith("http") ||
                                                currentUrl.startsWith("content"))

                        if (isUrlValid) {
                            // JIKA URL VALID: Play Langsung (Optimistic)
                            Log.d("DEBUG_PLAYER", "🚀 URL VALID: Play langsung tanpa menunggu.")
                            _uiState.update {
                                it.copy(debugStatus = "Playing from Cache", isLoadingData = false)
                            } // Data ready
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
                            Log.d(
                                    "DEBUG_PLAYER",
                                    "⏳ URL KOSONG: Resolving URL dulu... (Player dipause sementara)"
                            )
                            withContext(Dispatchers.Main) {
                                _uiState.update {
                                    it.copy(isLoadingLyrics = true, isLoadingData = true)
                                } // Still loading
                            }
                        }

                        // 2. RESOLVE URL (Pastikan dapat yang fresh)
                        Log.d("DEBUG_PLAYER", "🌐 FETCH: Resolving URL...")
                        // 2. RESOLVE URL (Pastikan dapat yang fresh)
                        Log.d("DEBUG_PLAYER", "🌐 FETCH: Resolving URL...")
                        val result = resolveSongUrl(targetSong)
                        val newUrl = result.url

                        // 3. UPDATE PLAYER (Hanya update item yang sedang aktif)
                        withContext(Dispatchers.Main) {

                            // VALIDASI URL: Cek Error Network atau URL kosong
                            if (result.errorType == MusicRepository.ErrorType.NETWORK) {
                                Log.e(
                                        "DEBUG_PLAYER",
                                        "❌ NETWORK ERROR during song update. Pausing."
                                )
                                _uiState.update {
                                    it.copy(
                                            hasError = true,
                                            errorType = "Gagal memuat: Masalah Koneksi Internet",
                                            isPlaying = false,
                                            isLoadingData = false // Stop spinner
                                    )
                                }
                                controller.pause()
                                return@withContext
                            }

                            if (newUrl.isBlank()) {
                                Log.e(
                                        "DEBUG_PLAYER",
                                        "❌ ERROR: URL kosong/gagal resolve. Skip playback."
                                )
                                // Kembalikan UI ke state aman atau tampilkan error
                                _uiState.update { it.copy(isLoadingLyrics = false, isLoadingData = false) }
                                // Opsional: Toast error
                                return@withContext
                            }

                            if (index < controller.mediaItemCount) {
                                // Buat MediaItem baru dengan URL valid
                                val newItem = createMediaItem(targetSong, newUrl)

                                // KUNCI: Gunakan setMediaItem di index spesifik untuk update source
                                controller.replaceMediaItem(index, newItem)

                                // Jika tadi kita menunda play (karena URL kosong), sekarang play!
                                if (!isUrlValid ||
                                                controller.playbackState == Player.STATE_IDLE ||
                                                controller.playbackState == Player.STATE_ENDED
                                ) {
                                    // Cek lagi apakah index masih sama (user gak ganti lagu lagi
                                    // pas loading)
                                    // Tapi karena kita replaceMediaItem di index spesifik, aman
                                    // untuk seek ke situ.
                                    if (controller.currentMediaItemIndex != index) {
                                        controller.seekTo(index, C.TIME_UNSET)
                                    }
                                    controller.play()
                                }
                            }
                            _uiState.update {
                                it.copy(isLoadingLyrics = false, isLoadingData = false)
                            }
                        }

                        // 4. UPDATE LIRIK & WARNA (Sekali saja!)
                        // Ambil data detail
                        fetchFullSongDetails(targetSong.song.id, forceUpdate = true)

                        val colors =
                                extractGradientColorsFromImageUrl(
                                        getApplication(),
                                        targetSong.song.coverUrl ?: ""
                                )

                        _uiState.update { it.copy(dominantColors = colors) }
                        // Save to preferences for HomeScreen
                        userPreferencesRepository.saveLastSongColor(
                                colors.getOrElse(0) { Color(0xFF755D8D) }
                        )

                        // 5. CEK STATUS LIKE
                        checkIfLiked(targetSong.song.id)
                    } catch (e: Exception) {
                        if (e is kotlinx.coroutines.CancellationException)
                                throw e // Biarkan cancel lewat

                        // TANGKAP ERROR BIAR GAK CRASH
                        Log.e("DEBUG_PLAYER", "❌ CRASH HANDLED di playSongAt: ${e.message}")
                        e.printStackTrace()

                        withContext(Dispatchers.Main) {
                            _uiState.update {
                                it.copy(isLoadingLyrics = false, isLoadingData = false)
                            }
                        }
                    }
                }
    }

    fun playingMusicFromPlaylist(playlistName: String) {
        viewModelScope.launch { userPreferencesRepository.saveLastPlaylistName(playlistName) }
        _uiState.update {
            it.copy(
                    playingMusicFromPlaylist = playlistName,
                    playlistSubtitle = "Memainkan Dari Playlist" // Reset subtitle
            )
        }
        // NOTE: Shuffle restoration from search context is NOT done here.
        // AutoPlaylistHeader uses forceSetShuffle(true) explicitly.
        // Restoring here caused a race condition (stale Compose state + delayed toggle).
        preSearchShuffleState = false // Selalu reset flag agar tidak menumpuk
    }

    // --- LOGIC BARU V4: PLAY FROM SEARCH ---
    fun playFromSearch(seedSong: SongWithArtist, query: String) {
        Log.d(
                "PlayMusicViewModel",
                "🚀 playFromSearch CALLED! Seed: ${seedSong.song.title}, Query: $query"
        )

        // --- CONTEXT-AWARE SHUFFLE: Simpan state shuffle sekarang, lalu paksa OFF ---
        preSearchShuffleState = _uiState.value.isShuffleModeEnabled
        if (preSearchShuffleState) {
            Log.d(
                    "PlayMusicViewModel",
                    "🔀 [SEARCH] Pre-search shuffle ON → disimpan, lalu dimatikan"
            )
            mediaController?.shuffleModeEnabled = false
            // Jangan simpan ke prefs agar ketika balik ke playlist, state asli tetap ada
        }

        // 1. SET PLAYLIST dengan LAGU INI
        // FIX RACE CONDITION: Tunggu setPlaylist selesai dulu sebelum fetch queue!
        val initJob = setPlaylist(listOf(seedSong), 0, query)

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

            generateSmartQueue(seedSong)
        }
    }

    private suspend fun generateSmartQueue(seedSong: SongWithArtist) {
        withContext(Dispatchers.IO) {
            val fullSongDetails =
                    repository.getFullSongDetails(
                            seedSong.song.id
                    ) // Pastikan metadata lengkap (moods/language)
            val seedSongWithMeta =
                    fullSongDetails?.let {
                        seedSong.song.copy(
                                lyrics = it.lyrics,
                                language = it.language,
                                moods = it.moods,
                                artistId = it.artistId
                        )
                    }
                            ?: seedSong.song

            val smartQueue = repository.fetchSmartQueue(currentSong = seedSongWithMeta)
            Log.d("PlayMusicViewModel", "📦 Smart Queue Result Size: ${smartQueue.size}")

            // 4. Update Playlist dengan hasil Smart Queue
            if (smartQueue.isNotEmpty()) {
                // Fetch Artist info logic (Existing)
                val artistIds = smartQueue.mapNotNull { it.artistId }.distinct()
                val artistsMap =
                        try {
                            SupabaseManager.client
                                    .from("artists")
                                    .select { filter { isIn("id", artistIds) } }
                                    .decodeList<Artist>()
                                    .associateBy { it.id }
                        } catch (_: Exception) {
                            emptyMap()
                        }

                val queueWithArtists =
                        smartQueue.map { song ->
                            SongWithArtist(song, song.artistId?.let { artistsMap[it] })
                        }

                // --- LOGGING UNTUK ANALISIS USER ---
                Log.d(
                        "PlayMusicViewModel",
                        "=== 📋 SMART QUEUE RESULT (${queueWithArtists.size} Songs) ==="
                )
                queueWithArtists.forEachIndexed { index, item ->
                    // Log format: [No] Judul - Artist | Lang: .. | Moods: ..
                    val moodsStr = item.song.moods.joinToString(", ")
                    Log.d(
                            "PlayMusicViewModel",
                            "Queue [#${index + 1}]: ${item.song.title} - ${item.artist?.name ?: "Unknown"} | Lang: ${item.song.language} | Moods: [$moodsStr]"
                    )
                }
                Log.d(
                        "PlayMusicViewModel",
                        "==========================================================="
                )


                // Update ViewModel State
                // HATI-HATI: Playlist mungkin sudah berubah jika user ganti lagu cepat-cepat.
                // Kita append ke current playlist jika seed song masih ada di sana.
                val currentPlaylist = _uiState.value.playlist.toMutableList()
                val isSeedStillPresent = currentPlaylist.any { it.song.id == seedSong.song.id }

                if (isSeedStillPresent) {
                    // Cek duplikasi (jangan add kalau sudah ada)
                    val existingIds = currentPlaylist.map { it.song.id }.toSet()
                    val uniqueNewSongs =
                            queueWithArtists.filter { !existingIds.contains(it.song.id) }

                    if (uniqueNewSongs.isNotEmpty()) {
                        currentPlaylist.addAll(uniqueNewSongs)

                        _uiState.update { it.copy(playlist = currentPlaylist) }
                        
                        // FIX: Save newly generated smart queue to the database
                        repository.savePlaybackQueue(currentPlaylist, _uiState.value.playingMusicFromPlaylist)

                        Log.d(
                                "PlayMusicViewModel",
                                "✅ Smart Queue loaded: ${uniqueNewSongs.size} new songs added to UI State."
                        )

                        // 5. Update Player (APPEND NEW SONGS)
                        withContext(Dispatchers.Main) {
                            val controller = mediaController
                            if (controller != null) {
                                // Create MediaItems for the queue (using raw audioUrl, will be
                                // resolved later)
                                val newMediaItems =
                                        uniqueNewSongs.map {
                                            createMediaItem(it, it.song.audioUrl ?: "")
                                        }
                                controller.addMediaItems(newMediaItems)
                                Log.d(
                                        "PlayMusicViewModel",
                                        "✅ Added ${newMediaItems.size} songs from Smart Queue to Player."
                                )
                            }
                        }
                    } else {
                        Log.d(
                                "PlayMusicViewModel",
                                "⚠️ Smart Queue fetched but all songs already exist in playlist."
                        )
                    }
                } else {
                    Log.w(
                            "PlayMusicViewModel",
                            "⚠️ Seed song no longer in playlist. Aborting Smart Queue update."
                    )
                }
            }
        }
    }

    private suspend fun resolveSongUrl(song: SongWithArtist): MusicRepository.ResolvedSongUrl {
        // Logika disederhanakan: Serahkan ke Repository
        // Repository akan otomatis cek SQLite -> Cek Expired -> Request API jika perlu
        val result =
                repository.getPlayableUrl(
                        songId = song.song.id,
                        title = song.song.title,
                        telegramFileId = song.song.telegramFileId,
                        fallbackUrl = song.song.audioUrl,
                        artistId = song.artist?.id
                )

        Log.d("PlayMusicViewModel", "✅ RESOLVED URL [${result.source}]: ${result.url}")
        return result
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
        val command =
                androidx.media3.session.SessionCommand("START_SLEEP_TIMER", android.os.Bundle.EMPTY)

        val args = android.os.Bundle().apply { putLong("DURATION", durationMs) }

        val result = controller.sendCustomCommand(command, args)
        Log.d(
                "PlayMusicViewModel",
                "🚀 Command START_SLEEP_TIMER sent with duration: $durationMs ms"
        )

        _uiState.update { it.copy(isSleepTimerActive = true) }
    }

    fun cancelSleepTimer() {
        Log.d("PlayMusicViewModel", "⏰ cancelSleepTimer called")
        val controller = mediaController
        if (controller == null) {
            Log.e("PlayMusicViewModel", "❌ ERROR: MediaController is NULL.")
            return
        }

        val command =
                androidx.media3.session.SessionCommand("STOP_SLEEP_TIMER", android.os.Bundle.EMPTY)
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
                if (e.message?.contains("duplicate key") == true ||
                                e.message?.contains("23505") == true
                ) {
                    Log.w(
                            "PlayMusicViewModel",
                            "⚠️ Like Conflict: Data sudah ada. Force update UI ke Liked."
                    )
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
        val meta =
                MediaMetadata.Builder()
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
                Log.d(
                        "PlayMusicViewModel",
                        "🚀 PRE-FETCH START: Menyiapkan lagu tetangga (Index $index): ${targetSong.song.title}"
                )

                val result = resolveSongUrl(targetSong)
                val newUrl = result.url

                if (newUrl != targetSong.song.audioUrl) {
                    updateMediaItemUrlInPlayer(index, targetSong, newUrl)
                }
            }
        }
    }

    // 3. Update Playlist ExoPlayer Diam-diam
    // 🔥 FIX SHUFFLE CASCADE:
    // DILARANG memanggil replaceMediaItem() untuk lagu yang TIDAK sedang dimainkan!
    // Di mode shuffle, replaceMediaItem() menyebabkan ExoPlayer MERESET internal shuffle order,
    // sehingga player melompat ke index acak lain secara cascade (19→8→4→0→7→...).
    // Strategi yang benar: Pre-fetch hanya MENGHANGATKAN CACHE (simpan URL ke DB),
    // biarkan onMediaItemTransition yang menangani update ExoPlayer saat lagu itu benar-benar
    // diputar.
    private suspend fun updateMediaItemUrlInPlayer(
            index: Int,
            song: SongWithArtist,
            newUrl: String
    ) {
        // URL sudah disimpan ke DB oleh resolveSongUrl() di atas.
        // Tidak perlu update ExoPlayer MediaItem sekarang!
        // onMediaItemTransition akan re-resolve dari DB cache ketika lagu ini diputar.
        Log.d(
                "PlayMusicViewModel",
                "✅ PRE-FETCH SELESAI: URL untuk index $index ('${song.song.title}') sudah di-cache. ExoPlayer TIDAK disentuh."
        )
    }

    fun refreshCurrentSongData() {
        val currentSong = _uiState.value.currentSong

        // 1. Cek Data Lirik: Kalau lagu ada tapi lirik kosong, paksa ambil dari database
        if (currentSong != null && currentSong.song.lyrics.isNullOrBlank()) {
            Log.d(
                    "DEBUG_PLAYER",
                    "🔄 REFRESH: UI baru bangun, lirik kosong. Mengambil ulang detail lagu..."
            )
            fetchFullSongDetails(currentSong.song.id)
        }

        // 2. Cek Posisi: Pastikan seekbar jalan lagi
        mediaController?.let { if (it.isPlaying) startUpdatingPosition() }

        // 3. Cek Ulang Status Like (Penting kalau user like dari device lain/web)
        if (currentSong != null) {
            checkIfLiked(currentSong.song.id)
        }
    }

    override fun onCleared() {
        super.onCleared()
        mediaController?.release()
    }

    fun updateLyricsConfig(config: com.example.remusic.ui.screen.playmusic.LyricsConfig) {
        viewModelScope.launch(Dispatchers.IO) { userPreferencesRepository.saveLyricsConfig(config) }
    }

    // --- QUEUE MANAGEMENT ---

    fun addToQueue(song: SongWithArtist): String {
        val currentPlaylist = _uiState.value.playlist

        // CASE 1: Queue is empty or nothing playing -> Treat as "Start New Queue"
        if (currentPlaylist.isEmpty() || _uiState.value.currentSong == null) {
            Log.d(
                    "PlayMusicViewModel",
                    "Queue empty, starting single song queue: ${song.song.title}"
            )

            // 1. Set Playlist with selected song (Just one)
            setPlaylist(listOf(song), 0)

            // 2. Set Playlist Name & Subtitle & Trigger Snackbar
            _uiState.update {
                it.copy(
                        playingMusicFromPlaylist = "Tambahkan Lagu ke Antrean",
                        playlistSubtitle = "Memainkan dari Playlist",
                        showQueueInfoSnackbar = true // Trigger Snackbar!
                )
            }
            return "Memulai antrean dengan ${song.song.title}"
        }

        // CASE 2: Normal Add to Queue (Existing logic)
        val existingIndex = currentPlaylist.indexOfFirst { it.song.id == song.song.id }
        if (existingIndex != -1) {
            return "Lagu ini sudah ada di dalam queue urutan ${existingIndex + 1}"
        }

        val mutableList = currentPlaylist.toMutableList()
        mutableList.add(song)
        _uiState.update { it.copy(playlist = mutableList) }

        // Update Player (Silent)
        mediaController?.addMediaItem(createMediaItem(song.song, song.song.audioUrl ?: ""))
        Log.d("PlayMusicViewModel", "Added to queue: ${song.song.title}")

        // Save new queue to DB
        viewModelScope.launch(Dispatchers.IO) {
            repository.savePlaybackQueue(mutableList, _uiState.value.playingMusicFromPlaylist)
        }

        return "${song.song.title} ditambahkan ke antrean"
    }

    fun playNext(song: SongWithArtist) {
        val controller = mediaController ?: return
        val currentPlaylist = _uiState.value.playlist.toMutableList()

        // Cek apakah lagu sudah ada di playlist
        val existingIndex = currentPlaylist.indexOfFirst { it.song.id == song.song.id }
        val currentIndex = controller.currentMediaItemIndex

        if (existingIndex != -1) {
            // 🛑 Jika Index sama dengan yang sedang diputar, jangan lakukan apa-apa (handled di UI
            // "Lagu sedang diputar")
            if (existingIndex == currentIndex) return

            // 1. Hapus dari posisi lama
            currentPlaylist.removeAt(existingIndex)
            controller.removeMediaItem(existingIndex)

            // 2. Hitung Index Baru
            // Jika kita menghapus item SEBELUM current index, maka current index akan bergeser
            // mundur -1.
            var actualCurrentIndex = currentIndex
            if (existingIndex < currentIndex) {
                actualCurrentIndex--
            }

            // 3. Masukkan tepat setelah lagu yang sedang diputar
            val targetIndex = actualCurrentIndex + 1

            // Insert di ViewModel list
            currentPlaylist.add(targetIndex, song)
            _uiState.update { it.copy(playlist = currentPlaylist) }

            // Insert di Player
            controller.addMediaItem(
                    targetIndex,
                    createMediaItem(song.song, song.song.audioUrl ?: "")
            )
            Log.d("PlayMusicViewModel", "Moved to next: ${song.song.title} to index $targetIndex")

            // Prefetch audio and save to DB
            prefetchSongAtIndex(targetIndex)
            viewModelScope.launch(Dispatchers.IO) {
                repository.savePlaybackQueue(currentPlaylist, _uiState.value.playingMusicFromPlaylist)
            }
        } else {
            // Logic Lama: Insert Baru
            val nextIndex = currentIndex + 1
            currentPlaylist.add(nextIndex, song)
            _uiState.update { it.copy(playlist = currentPlaylist) }

            // Update Player
            controller.addMediaItem(nextIndex, createMediaItem(song.song, song.song.audioUrl ?: ""))
            Log.d("PlayMusicViewModel", "Inserted next: ${song.song.title} at index $nextIndex")

            // Prefetch audio and save to DB
            prefetchSongAtIndex(nextIndex)
            viewModelScope.launch(Dispatchers.IO) {
                repository.savePlaybackQueue(currentPlaylist, _uiState.value.playingMusicFromPlaylist)
            }
        }
    }

    private fun createMediaItem(song: com.example.remusic.data.model.Song, url: String): MediaItem {
        val meta =
                MediaMetadata.Builder()
                        .setTitle(song.title)
                        .setArtworkUri(song.coverUrl?.takeIf { it.isNotBlank() }?.toUri())
                        .build()

        return MediaItem.Builder().setMediaId(song.id).setUri(url).setMediaMetadata(meta).build()
    }

    // Overload for specific song (e.g. from Queue)
    fun toggleLike(songId: String) {
        viewModelScope.launch {
            val user = SupabaseManager.client.auth.currentUserOrNull()
            val userId = user?.id

            if (userId == null) {
                Log.e("PlayMusicViewModel", "Cannot toggle like: User not logged in")
                return@launch
            }

            if (repository.isSongLiked(songId, userId)) {
                repository.toggleLike(
                        songId,
                        userId,
                        true
                ) // Pass true to 'isLiked' means 'currently liked, so unlike it'?
                // Wait, let's check repo logic:
                // if (isLiked) { DELETE } else { INSERT }
                // So if isSongLiked returns true, we pass true to toggleLike to delete it.

                // If current song is the one being unliked, update UI state
                if (_uiState.value.currentSong?.song?.id == songId) {
                    _uiState.update { it.copy(isLiked = false) }
                }
            } else {
                repository.toggleLike(
                        songId,
                        userId,
                        false
                ) // Fail to delete? No, false means Insert.
                if (_uiState.value.currentSong?.song?.id == songId) {
                    _uiState.update { it.copy(isLiked = true) }
                }
            }
        }
    }

    // --- ARTIST DETAILS FETCHING (For Playlist Header) ---
    fun fetchArtistDetailsForHeader(artistId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoadingArtistDetails = true, artistDetails = null) }

            // Get Current User ID from Supabase (Single Source of Truth)
            val user = SupabaseManager.client.auth.currentUserOrNull()
            val userId = user?.id

            if (userId != null) {
                val details = repository.getArtistDetails(userId, artistId)
                _uiState.update { it.copy(isLoadingArtistDetails = false, artistDetails = details) }
            } else {
                _uiState.update { it.copy(isLoadingArtistDetails = false) }
            }
        }
    }

    // ===================== TAB TRACKING & ARTIST NAVIGATION =====================

    /** Called by MainScreen whenever the user switches tabs */
    fun setPreviousTab(route: String) {
        _uiState.update { it.copy(previousTab = route) }
    }

    /** Called by PlayMusicScreen when user clicks "Lihat Playlist" or "Semua Lagu" */
    fun requestNavigateToArtist(artistId: String) {
        _uiState.update { it.copy(navigateToArtistEvent = artistId) }
    }

    /** Called by MainScreen after consuming the navigation event */
    fun consumeNavigateToArtistEvent() {
        _uiState.update { it.copy(navigateToArtistEvent = null) }
    }

    /** Set pending artist navigation - also emits to SharedFlow for reliable delivery */
    fun setPendingArtistNavigation(artistId: String) {
        _uiState.update { it.copy(pendingArtistNavigation = artistId) }
        viewModelScope.launch { _artistNavigationFlow.emit(artistId) }
    }

    /** Called by HomeScreen/SearchScreen after consuming the pending navigation */
    fun consumePendingArtistNavigation() {
        _uiState.update { it.copy(pendingArtistNavigation = null) }
        viewModelScope.launch {
            _artistNavigationFlow.emit(null) // Reset replay cache
        }
    }

    // Toggle Follow from Header
    fun toggleFollowFromHeader(artistId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val user = SupabaseManager.client.auth.currentUserOrNull() ?: return@launch
            val currentDetails = _uiState.value.artistDetails ?: return@launch

            val isCurrentlyFollowed = currentDetails.isFollowed // Status SAAT INI

            // 1. Optimistic Update (Header)
            _uiState.update {
                it.copy(
                        artistDetails =
                                currentDetails.copy(
                                        isFollowed = !isCurrentlyFollowed,
                                        followerCount =
                                                if (!isCurrentlyFollowed)
                                                        currentDetails.followerCount + 1
                                                else currentDetails.followerCount - 1
                                )
                )
            }

            // 2. Sync Player State (Jika artist yang di-follow === artist yang sedang diputar)
            val currentPlayingArtistId = _uiState.value.currentSong?.artist?.id
            if (currentPlayingArtistId == artistId) {
                _uiState.update { it.copy(isArtistFollowed = !isCurrentlyFollowed) }
            }

            try {
                // 3. Call Repo (Pass Current Status, Repo will Toggle)
                repository.toggleFollowArtist(user.id, artistId, isCurrentlyFollowed)
            } catch (e: Exception) {
                // Rollback if failed
                _uiState.update { state ->
                    state.copy(
                            artistDetails = currentDetails,
                            // Rollback Player State too if synced
                            isArtistFollowed =
                                    if (state.currentSong?.artist?.id == artistId)
                                            isCurrentlyFollowed
                                    else state.isArtistFollowed
                    )
                }
            }
        }
    }

    // --- Global Queue Options Helpers ---
    fun showQueueOptions(song: SongWithArtist) {
        _uiState.update { it.copy(selectedSongForQueueOptions = song) }
    }

    fun dismissQueueOptions() {
        _uiState.update { it.copy(selectedSongForQueueOptions = null) }
    }

    fun dismissQueueInfoSnackbar() {
        _uiState.update { it.copy(showQueueInfoSnackbar = false) }
    }

    // --- LOGIC: ARTIST FOLLOW ---
    private fun checkIfArtistFollowed(artistId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Ambil User ID dari sesi (butuh login)
                val user = SupabaseManager.client.auth.currentUserOrNull()
                if (user != null) {
                    val isFollowed = repository.isArtistFollowed(user.id, artistId)
                    _uiState.update { it.copy(isArtistFollowed = isFollowed) }
                } else {
                    _uiState.update { it.copy(isArtistFollowed = false) }
                }
            } catch (e: Exception) {
                Log.e("PlayMusicViewModel", "❌ Gagal cek follow status: ${e.message}")
            }
        }
    }

    fun toggleFollowArtist() {
        val currentArtist = _uiState.value.currentSong?.artist ?: return
        val isCurrentlyFollowed = _uiState.value.isArtistFollowed

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val user = SupabaseManager.client.auth.currentUserOrNull()
                if (user != null) {
                    // Optimistic Update (UI duluan biar cepet)
                    _uiState.update { it.copy(isArtistFollowed = !isCurrentlyFollowed) }

                    repository.toggleFollowArtist(user.id, currentArtist.id, isCurrentlyFollowed)

                    // Re-check for consistency (optional, but good for sync)
                    // checkIfArtistFollowed(currentArtist.id)
                } else {
                    Log.w("PlayMusicViewModel", "User belum login, tidak bisa follow artist.")
                    // TODO: Show login snackbar?
                }
            } catch (e: Exception) {
                Log.e("PlayMusicViewModel", "❌ Gagal toggle follow: ${e.message}")
                // Rollback UI jika gagal
                _uiState.update { it.copy(isArtistFollowed = isCurrentlyFollowed) }
            }
        }
    }

    // --- GRADIENT STYLE PREFERENCE ---
    fun setGradientStyle(style: com.example.remusic.data.preferences.GradientStyle) {
        viewModelScope.launch {
            userPreferencesRepository.saveGradientStyle(style)
        }
    }

    fun setGradientTopColorIndex(index: Int) {
        viewModelScope.launch {
            userPreferencesRepository.saveGradientTopColorIndex(index)
        }
    }

    fun setGradientBottomColorIndex(index: Int) {
        viewModelScope.launch {
            userPreferencesRepository.saveGradientBottomColorIndex(index)
        }
    }

    // --- DATA SAVER MODE ---
    fun toggleDataSaverMode(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.saveDataSaverMode(enabled)
        }
    }

    // --- Artist Songs Pagination Logic ---

    // =========================================================================
    //                            PLAYLIST FETCHING
    // =========================================================================

    @kotlinx.serialization.Serializable
    private data class PlaylistSongRelation(
        @kotlinx.serialization.SerialName("song_id") val songId: String,
        @kotlinx.serialization.SerialName("added_at") val addedAt: String? = null
    )
    fun fetchPlaylistDetails(playlistId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { 
                it.copy(
                    isLoadingArtistDetails = true,
                    isArtistSongsLoading = true, // Set to true here to trigger skeleton
                    currentPlaylistDetails = null,
                    playlistOwner = null,
                    artistSongs = emptyList() // clear previous songs
                ) 
            }
            try {
                // Fetch Playlist
                val playlist = SupabaseManager.client.from("playlists")
                    .select {
                        filter {
                            eq("id", playlistId)
                        }
                    }.decodeSingleOrNull<com.example.remusic.data.model.Playlist>()

                // Fetch Owner if exists
                var owner: User? = null
                val ownerId = playlist?.ownerUserId
                if (ownerId != null) {
                    owner = SupabaseManager.client.from("users")
                        .select {
                            filter {
                                eq("id", ownerId)
                            }
                        }.decodeSingleOrNull<User>()
                }

                // Fetch User Playlist Songs
                val playlistSongsRelation = SupabaseManager.client.from("playlist_songs")
                    .select {
                        filter { eq("playlist_id", playlistId) }
                    }.decodeList<PlaylistSongRelation>()

                val songIds = playlistSongsRelation.map { it.songId }.distinct()
                var finalSongsWithArtist: List<SongWithArtist> = emptyList()

                if (songIds.isNotEmpty()) {
                    // Fetch real songs
                    val songsData = SupabaseManager.client.from("songs")
                        .select {
                            filter { isIn("id", songIds) }
                        }.decodeList<Song>()

                    // Fetch associated artists
                    val artistIds = songsData.mapNotNull { it.artistId }.distinct()
                    val artistsData = if (artistIds.isNotEmpty()) {
                        SupabaseManager.client.from("artists")
                            .select {
                                filter { isIn("id", artistIds) }
                            }.decodeList<Artist>().associateBy { it.id }
                    } else {
                        emptyMap()
                    }

                    // Map songs into SongWithArtist and maintain playlist order (or added_at order)
                    // We'll preserve the order from `playlistSongsRelation`
                    val songMap = songsData.associateBy { it.id }
                    finalSongsWithArtist = playlistSongsRelation.mapNotNull { rel ->
                        val song = songMap[rel.songId]
                        if (song != null) {
                            SongWithArtist(song = song, artist = song.artistId?.let { artistsData[it] })
                        } else null
                    }
                }

                _uiState.update {
                    it.copy(
                        currentPlaylistDetails = playlist,
                        playlistOwner = owner,
                        isLoadingArtistDetails = false,
                        artistSongs = finalSongsWithArtist,
                        isArtistSongsLoading = false,
                        artistSongsEndReached = true // Pagination not supported yet for user playlists
                    )
                }
            } catch (e: Exception) {
                Log.e("PlayMusicViewModel", "Error fetching playlist details: ${e.message}")
                _uiState.update {
                    it.copy(
                        currentPlaylistDetails = null,
                        playlistOwner = null,
                        isLoadingArtistDetails = false,
                        isArtistSongsLoading = false
                    )
                }
            }
        }
    }

    fun fetchArtistSongs(artistId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            // Reset State for new artist
            _uiState.update {
                it.copy(
                        artistSongs = emptyList(),
                        isArtistSongsLoading = true,
                        artistSongsEndReached = false,
                        artistSongsPage = 0
                )
            }

            val limit = 100
            val newSongs =
                    repository.getSongsByArtistPagination(artistId, limit = limit, offset = 0)

            _uiState.update {
                it.copy(
                        artistSongs = newSongs,
                        isArtistSongsLoading = false,
                        artistSongsEndReached = newSongs.size < limit,
                        artistSongsPage = 1 // Next page index
                )
            }
        }
    }

    fun loadMoreArtistSongs(artistId: String) {
        if (_uiState.value.isArtistSongsLoading || _uiState.value.artistSongsEndReached) return

        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isArtistSongsLoading = true) }

            val limit = 100
            val currentPage = _uiState.value.artistSongsPage
            val offset = currentPage * limit

            val newSongs =
                    repository.getSongsByArtistPagination(artistId, limit = limit, offset = offset)

            _uiState.update { state ->
                state.copy(
                        artistSongs = state.artistSongs + newSongs, // Append info
                        isArtistSongsLoading = false,
                        artistSongsEndReached = newSongs.size < limit,
                        artistSongsPage = currentPage + 1
                )
            }
        }
    }

    // --- SMART CONNECTIVITY RETRY ---
    private var hasRetriedCurrentSong = false

    fun onConnectivityRestored() {
        val currentState = _uiState.value
        // Cek apakah sedang error karena network?
        if (currentState.hasError &&
                        currentState.errorType?.contains("Koneksi", ignoreCase = true) == true
        ) {
            // Cek apakah sudah pernah retry untuk lagu ini?
            if (!hasRetriedCurrentSong) {
                Log.d(
                        "PlayMusicViewModel",
                        "🌐 CONNECTIVITY RESTORED: Retrying playback for '${currentState.currentSong?.song?.title}'"
                )
                hasRetriedCurrentSong = true
                retryPlayback()
            } else {
                Log.d(
                        "PlayMusicViewModel",
                        "🌐 CONNECTIVITY RESTORED: Already retried this song once. Skipping."
                )
            }
        }
    }

    private fun retryPlayback() {
        val currentSong = _uiState.value.currentSong ?: return
        val controller = mediaController ?: return
        val currentIndex = _uiState.value.currentSongIndex

        viewModelScope.launch(Dispatchers.IO) {
            // Set Loading State
            withContext(Dispatchers.Main) {
                _uiState.update {
                    it.copy(
                            hasError = false,
                            errorType = null,
                            isLoadingData = true,
                            debugStatus = "Retrying connection..."
                    )
                }
            }

            // Resolve URL ulang
            val result = resolveSongUrl(currentSong)

            withContext(Dispatchers.Main) {
                _uiState.update { it.copy(isLoadingData = false) }

                if (result.errorType == MusicRepository.ErrorType.NONE && result.url.isNotBlank()) {
                    Log.d(
                            "PlayMusicViewModel",
                            "✅ RETRY SUCCESS for '${currentSong.song.title}': URL resolved."
                    )

                    // 1. Recover Metadata (Lyrics, etc)
                    fetchFullSongDetails(currentSong.song.id, forceUpdate = true)

                    // 2. Replace Media Item & Play
                    val newItem = createMediaItem(currentSong, result.url)

                    if (currentIndex < controller.mediaItemCount) {
                        // Cek apakah user masih menunggu lagu yang sama?
                        if (_uiState.value.currentSongIndex == currentIndex) {
                            Log.d("PlayMusicViewModel", "▶ RESUMING PLAYBACK...")
                            controller.replaceMediaItem(currentIndex, newItem)
                            
                            // Jika ExoPlayer index tertinggal gara-gara jumpToSong sebelumnya error, paksakan seek
                            if (controller.currentMediaItemIndex != currentIndex) {
                                controller.seekTo(currentIndex, C.TIME_UNSET)
                            }
                            
                            controller.prepare() // Wajib prepare ulang jika sebelumnya error
                            controller.playWhenReady = true // Force play
                            controller.play()
                        } else {
                            Log.w(
                                    "PlayMusicViewModel",
                                    "⚠️ User moved to another song. Retry result ignored."
                            )
                        }
                    }

                    // 3. RECOVER SMART QUEUE (If only 1 song exists - e.g. played from search/quick
                    // pick)
                    val currentPlaylistSize = _uiState.value.playlist.size
                    if (currentPlaylistSize == 1) {
                        Log.d(
                                "PlayMusicViewModel",
                                "♻️ RESTORING QUEUE: Only 1 song in playlist. Generating Smart Queue..."
                        )
                        viewModelScope.launch { generateSmartQueue(currentSong) }
                    }
                } else {
                    Log.e(
                            "PlayMusicViewModel",
                            "❌ RETRY FAILED: Error=${result.errorType}, URL Empty?=${result.url.isBlank()}"
                    )
                    // Tetap di error state, user bisa coba manual lagi nanti
                    _uiState.update {
                        it.copy(
                                hasError = true,
                                errorType = "Gagal memuat ulang (${result.errorType}). Coba lagi.",
                                isLoadingData = false
                        )
                    }
                }
            }
        }
    }

    // --- PLAYBACK PERSISTENCE LOGIC ---
    // Dipanggil saat inisialisasi ViewModel
    private suspend fun restorePlaybackState() {
        Log.d("PlayMusicViewModel", "♻️ [RESTORE] Memulai restorasi state...")

        // 1. Ambil Data Terakhir dari Prefs
        val lastSongId = userPreferencesRepository.lastSongIdFlow.first()
        val lastPosition = userPreferencesRepository.lastPositionFlow.first()

        // 2. Ambil Queue dari Room
        val (playlist, playlistName) =
                withContext(Dispatchers.IO) { repository.restorePlaybackQueue() }

        val lastPlaylistName = userPreferencesRepository.lastPlaylistNameFlow.first()
        val finalPlaylistName =
                if (playlistName == "Unknown Playlist") lastPlaylistName else playlistName

        if (playlist.isNotEmpty()) {
            Log.d(
                    "PlayMusicViewModel",
                    "♻️ [RESTORE] Queue (${playlist.size} lagu) ditemukan. Mengembalikan player..."
            )

            // Cari index lagu terakhir
            val startIndex =
                    if (lastSongId != null) {
                        playlist.indexOfFirst { it.song.id == lastSongId }.coerceAtLeast(0)
                    } else 0

            // Setup Player (TAPI JANGAN AUTO-PLAY)
            _uiState.update {
                it.copy(
                        playlist = playlist,
                        currentSong = playlist[startIndex],
                        currentSongIndex = startIndex,
                        playingMusicFromPlaylist = finalPlaylistName
                )
            }

            val controller = mediaController ?: return

            // Setup Media Items
            val mediaItems =
                    playlist.map { s ->
                        val meta =
                                MediaMetadata.Builder()
                                        .setTitle(s.song.title)
                                        .setArtist(s.artist?.name ?: "Unknown")
                                        .setArtworkUri(s.song.coverUrl?.toUri())
                                        .build()

                        MediaItem.Builder()
                                .setMediaId(s.song.id)
                                .setUri(s.song.audioUrl ?: s.song.telegramDirectUrl ?: "")
                                .setMediaMetadata(meta)
                                .build()
                    }

            controller.setMediaItems(mediaItems, startIndex, lastPosition)
            controller.prepare()
            controller.pause() // Pastikan PAUSE saat start

            // Update UI agar bottom player muncul dengan posisi yang benar
            _uiState.update {
                it.copy(
                        currentPosition = lastPosition,
                        totalDuration = controller.duration.coerceAtLeast(0L),
                        isBuffering = false
                )
            }

            // Fetch details for UI (Async) - Biar cover art & lirik muncul
            fetchFullSongDetails(playlist[startIndex].song.id)

            // Ambil Warna Dominan
            val coverUrl = playlist[startIndex].song.coverUrl
            if (coverUrl != null) {
                val colors = extractGradientColorsFromImageUrl(getApplication(), coverUrl)
                _uiState.update { it.copy(dominantColors = colors) }
            }

            Log.d("PlayMusicViewModel", "✅ [RESTORE] Berhasil! Siap di posisi: ${lastPosition}ms")
        } else {
            Log.d("PlayMusicViewModel", "⚠️ [RESTORE] Tidak ada history queue.")
        }
    }

    // Dipanggil saat pause, stop, atau ganti lagu
    private fun savePlaybackState() {
        val currentSong = _uiState.value.currentSong?.song
        val currentPosition = _uiState.value.currentPosition

        if (currentSong != null) {
            viewModelScope.launch(Dispatchers.IO) {
                userPreferencesRepository.saveLastSongId(currentSong.id)
                userPreferencesRepository.saveLastPosition(currentPosition)
            }
        }
    }

    // =========================================================================
    //                      ADD SONG TO PLAYLIST (GLOBAL)
    // =========================================================================

    fun showAddToPlaylistSheet(song: SongWithArtist) {
        _uiState.update { it.copy(selectedSongForAddToPlaylist = song) }
        fetchUserPlaylists()
    }

    fun dismissAddToPlaylistSheet() {
        _uiState.update { it.copy(selectedSongForAddToPlaylist = null) }
    }

    private fun fetchUserPlaylists() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isFetchingUserPlaylists = true) }
            try {
                val userId = SupabaseManager.client
                    .auth
                    .currentUserOrNull()?.id ?: return@launch
                val playlists = SupabaseManager.client.from("playlists")
                    .select {
                        filter {
                            eq("owner_user_id", userId)
                        }
                    }.decodeList<com.example.remusic.data.model.Playlist>()
                
                _uiState.update { 
                    it.copy(
                        userPlaylists = playlists,
                        isFetchingUserPlaylists = false
                    ) 
                }
            } catch (e: Exception) {
                Log.e("PlayMusicViewModel", "Error fetching user playlists: ${e.message}")
                _uiState.update { it.copy(isFetchingUserPlaylists = false) }
            }
        }
    }

    @kotlinx.serialization.Serializable
    private data class PlaylistSongInsertReq(
        @kotlinx.serialization.SerialName("playlist_id") val playlistId: String,
        @kotlinx.serialization.SerialName("song_id") val songId: String,
        @kotlinx.serialization.SerialName("added_by") val addedBy: String
    )

    fun addSongToPlaylist(playlistId: String, songId: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val userId = SupabaseManager.client
                    .auth
                    .currentUserOrNull()?.id ?: return@launch
                // Cek apakah lagu sudah ada di playlist
                val existing = SupabaseManager.client.from("playlist_songs")
                    .select {
                        filter {
                            eq("playlist_id", playlistId)
                            eq("song_id", songId)
                        }
                    }.decodeList<kotlinx.serialization.json.JsonObject>()
                    
                if (existing.isNotEmpty()) {
                    withContext(Dispatchers.Main) { onResult(false, "Lagu sudah ada di playlist ini") }
                    return@launch
                }
                
                // Tambahkan lagu
                val insertData = PlaylistSongInsertReq(
                    playlistId = playlistId,
                    songId = songId,
                    addedBy = userId
                )
                SupabaseManager.client.from("playlist_songs").insert(insertData)
                withContext(Dispatchers.Main) { onResult(true, "Berhasil menambahkan lagu ke playlist") }
            } catch (e: Exception) {
                Log.e("PlayMusicViewModel", "Error adding song to playlist: ${e.message}", e)
                withContext(Dispatchers.Main) { onResult(false, "Gagal menambahkan lagu: ${e.message}") }
            }
        }
    }
}
