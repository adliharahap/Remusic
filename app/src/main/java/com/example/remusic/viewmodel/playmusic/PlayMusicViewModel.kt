package com.example.remusic.viewmodel.playmusic

import android.annotation.SuppressLint
import android.app.Application
import android.content.ComponentName
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
import com.example.remusic.data.UserManager
import com.example.remusic.data.local.MusicDatabase
import com.example.remusic.data.model.Artist
import com.example.remusic.data.model.ArtistDetails
import com.example.remusic.data.model.AudioOutputDevice
import com.example.remusic.data.model.Song
import com.example.remusic.data.model.SongWithArtist
import com.example.remusic.data.model.displayArtistName
import com.example.remusic.data.model.User
import com.example.remusic.data.preferences.UserPreferencesRepository
import com.example.remusic.data.repository.MusicRepository
import com.example.remusic.data.network.LrcLibResponse
import com.example.remusic.services.MusicService
import com.example.remusic.utils.NotificationUtils
import com.example.remusic.utils.SongDownloader
import com.example.remusic.utils.extractGradientColorsFromImageUrl
import android.widget.Toast
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import android.media.AudioManager
import android.media.AudioDeviceInfo
import android.media.AudioDeviceCallback
import android.content.Context
import androidx.mediarouter.media.MediaRouter
import androidx.mediarouter.media.MediaRouteSelector
import androidx.mediarouter.media.MediaControlIntent
import timber.log.Timber

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
        val originalPlaylist: List<SongWithArtist> = emptyList(),
        val dominantColors: List<Color> = listOf(Color.DarkGray, Color.Black),
        val currentAudioDevice: AudioOutputDevice? = null,
        val availableAudioDevices: List<AudioOutputDevice> = emptyList(),
        val currentSongIndex: Int = 0,
        val animationDirection: AnimationDirection = AnimationDirection.NONE,
        val isLoadingLyrics: Boolean = false,
        val isFetchingSyncedLyrics: Boolean = false,
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
        
        // --- Alternative Lyrics ---
        val alternativeLyricsResults: List<LrcLibResponse> = emptyList(),
        val isSearchingLyrics: Boolean = false,
        val lyricsOffset: Long = 0L, // ms

        val artistSongsEndReached: Boolean = false,
        val artistSongsPage: Int = 0, // 0-indexed page or use offset directly // New State
        val uploader: User? = null,
        val playlistSubtitle: String = "Memainkan Dari Playlist", // Default

        // Error state tracking
        val hasError: Boolean = false,
        val errorType: String? = null,

        val lyricsConfig: com.example.remusic.ui.screen.playmusic.LyricsConfig =
                com.example.remusic.ui.screen.playmusic.LyricsConfig(),

        // Global Queue Options State
        val selectedSongForQueueOptions: SongWithArtist? = null,
        val playlistIdToRemoveFromQueueOptions: String? = null,

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
        val navigateToUploaderEvent: String? = null, // One-shot event: uploaderId to navigate to
        val navigateToCreatePlaylistEvent: Boolean = false, // One-shot event: to create playlist
        val pendingArtistNavigation: String? = null, // Pending artistId for tab screens to consume
        val pendingUploaderNavigation: String? = null, // Pending uploaderId for tab screens to consume
        val gradientStyle: com.example.remusic.data.preferences.GradientStyle = com.example.remusic.data.preferences.GradientStyle.PRIMARY,
        val gradientTopColorIndex: Int = 0,
        val gradientBottomColorIndex: Int = 1,
        
        // Data Saver
        val isDataSaverModeEnabled: Boolean = false,

        // Add to Playlist State
        val selectedSongForAddToPlaylist: SongWithArtist? = null,
        val userPlaylists: List<com.example.remusic.data.model.Playlist> = emptyList(),
        val isFetchingUserPlaylists: Boolean = false,

        // Background Music
        val backgroundMusicEnabled: Boolean = false,

        // Playback Speed
        val playbackSpeed: Float = 1.0f,

        // Game Mode
        val isGameModeActive: Boolean = false,
        val gameModeVolume: Float = 0.2f,

        // Player Background Style
        val playerBackgroundStyle: com.example.remusic.data.preferences.PlayerBackgroundStyle = com.example.remusic.data.preferences.PlayerBackgroundStyle.LIQUID_GRADIENT,

        // Per-screen theme toggles
        val isThemeAppliedToQueue: Boolean = true,
        val isThemeAppliedToNowPlaying: Boolean = true,
        val isThemeAppliedToLyrics: Boolean = true
)

class PlayMusicViewModel(application: Application) : AndroidViewModel(application) {

    // untuk fitur fade ketika play, pause di klike
    companion object {
        internal const val FADE_DURATION_MS = 500L // Durasi fade dalam milidetik
        internal const val FADE_INTERVAL_MS = 20L // Seberapa sering volume diupdate
    }

    internal var fetchLyricsJob: Job? = null
    internal var searchDebounceJob: Job? = null

    // variabel untuk playsongat
    internal var jumpJob: Job? = null
    internal var setPlaylistJob: Job? = null
    internal var preSearchShuffleState: Boolean =
            false // ← Simpan state shuffle sebelum masuk search
    internal val smartQueueMutex = kotlinx.coroutines.sync.Mutex()

    // --- VARIABEL UNTUK MENGELOLA FADE ---
    internal var volumeFadeJob: Job? = null
    internal var isFading = false

    // Explicit flag to track if we are currently generating a smart queue
    @Volatile
    internal var isGeneratingSmartQueue = false

    // --- BUFFERING WATCHDOG ---
    // Memantau jika player stuck di STATE_BUFFERING terlalu lama
    internal var bufferingWatchdogJob: Job? = null

    internal val _uiState = MutableStateFlow(PlayerUiState())

    // ===================== ARTIST NAVIGATION SHARED FLOW =====================
    // replay=1 ensures late subscribers still receive the last emitted artistId
    internal val _artistNavigationFlow =
        MutableSharedFlow<String?>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val artistNavigationFlow = _artistNavigationFlow.asSharedFlow()

    internal val _uploaderNavigationFlow =
        MutableSharedFlow<String?>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val uploaderNavigationFlow = _uploaderNavigationFlow.asSharedFlow()

    // Variabel untuk melacak lagu terakhir yang play count-nya sudah di-increment
    internal var lastIncrementedSongId: String? = null

    // --- TRACKING REAL LISTENING TIME (ANTI-CHEAT) ---
    internal var actualListeningTimeMs: Long = 0L // Waktu dengar asli (akumulasi)
    internal var lastSongIdForTracking: String? = null // ID lagu terakhir yang sedang dilacak
    internal var lastUpdateTime: Long = System.currentTimeMillis() // Untuk delta time tracking

    // Cache memory: Key = TelegramFileID, Value = Link Streaming Direct
    internal val database = MusicDatabase.getDatabase(application)
    internal val repository = MusicRepository(database.musicDao(), database.localPlayHistoryDao())

    val uiState = _uiState.asStateFlow()

    val lyricsManager by lazy { com.example.remusic.viewmodel.playmusic.managers.LyricsManager(viewModelScope, _uiState, repository, com.example.remusic.data.local.MusicDatabase.getDatabase(getApplication())) { songId -> fetchFullSongDetails(songId, forceUpdate = true) } }
    val playerSettingsManager by lazy { com.example.remusic.viewmodel.playmusic.managers.PlayerSettingsManager(viewModelScope, _uiState, userPreferencesRepository) }


    val audioRouteManager = com.example.remusic.viewmodel.playmusic.managers.AudioRouteManager(getApplication(), viewModelScope, _uiState, { mediaController }, { fadeInAndPlay() })
    val sleepTimerManager by lazy { com.example.remusic.viewmodel.playmusic.managers.SleepTimerManager({ mediaController }, _uiState) }

    fun setAudioOutputDevice(device: com.example.remusic.data.model.AudioOutputDevice) = audioRouteManager.setAudioOutputDevice(device)
    fun setSleepTimer(minutes: Int) = sleepTimerManager.setSleepTimer(minutes)
    fun cancelSleepTimer() = sleepTimerManager.cancelSleepTimer()

    internal var mediaController: MediaController? = null

    internal val userPreferencesRepository = UserPreferencesRepository(getApplication())



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
                        if (colors.isNotEmpty()) {
                            userPreferencesRepository.saveLastSongColor(colors.first())
                        }
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

        viewModelScope.launch {
            userPreferencesRepository.backgroundMusicEnabledFlow.collect { isEnabled ->
                _uiState.update { it.copy(backgroundMusicEnabled = isEnabled) }
            }
        }

        // Collect player background style (restores selection on relaunch)
        viewModelScope.launch {
            userPreferencesRepository.playerBackgroundStyleFlow.collect { style ->
                _uiState.update { it.copy(playerBackgroundStyle = style) }
            }
        }

        // --- SENSOR LIKE OTOMATIS (LIVE MONITORING) ---
        // Memantau status Like lagu yang sedang diputar secara real-time
        viewModelScope.launch {
            _uiState.map { it.currentSong?.song?.id }
                .distinctUntilChanged()
                .collectLatest { songId ->
                    if (songId != null) {
                        repository.social.isSongLikedFlow(songId).collect { isLiked ->
                            _uiState.update { it.copy(isLiked = isLiked) }
                        }
                    } else {
                        _uiState.update { it.copy(isLiked = false) }
                    }
                }
        }

        // Collect per-screen theme toggles
        viewModelScope.launch {
            userPreferencesRepository.themeApplyToQueueFlow.collect { enabled ->
                _uiState.update { it.copy(isThemeAppliedToQueue = enabled) }
            }
        }
        viewModelScope.launch {
            userPreferencesRepository.themeApplyToNowPlayingFlow.collect { enabled ->
                _uiState.update { it.copy(isThemeAppliedToNowPlaying = enabled) }
            }
        }
        viewModelScope.launch {
            userPreferencesRepository.themeApplyToLyricsFlow.collect { enabled ->
                _uiState.update { it.copy(isThemeAppliedToLyrics = enabled) }
            }
        }
    }

    internal var positionJob: Job? = null    
    fun retryFetchLyrics() = lyricsManager.retryFetchLyrics()

    fun searchAlternativeLyrics(query: String) = lyricsManager.searchAlternativeLyrics(query)

    fun applyAlternativeLyrics(lrc: com.example.remusic.data.network.LrcLibResponse) = lyricsManager.applyAlternativeLyrics(lrc)

    fun setLyricsOffset(offset: Long) = lyricsManager.setLyricsOffset(offset)

    fun setPlaybackSpeed(speed: Float) = playerSettingsManager.setPlaybackSpeed(speed)

    fun setPlayerBackgroundStyle(style: com.example.remusic.data.preferences.PlayerBackgroundStyle) = playerSettingsManager.setPlayerBackgroundStyle(style)

    fun setThemeAppliedToQueue(enabled: Boolean) = playerSettingsManager.setThemeAppliedToQueue(enabled)

    fun setThemeAppliedToNowPlaying(enabled: Boolean) = playerSettingsManager.setThemeAppliedToNowPlaying(enabled)

    fun setThemeAppliedToLyrics(enabled: Boolean) = playerSettingsManager.setThemeAppliedToLyrics(enabled)

    // --- GAME MODE ---
    internal fun getTargetVolume(): Float {
        return if (_uiState.value.isGameModeActive) _uiState.value.gameModeVolume else 1.0f
    }

    fun toggleGameMode() = playerSettingsManager.toggleGameMode()

    fun setGameModeVolume(volume: Float) = playerSettingsManager.setGameModeVolume(volume)

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
       Timber.tag("DEBUG_PLAYER").d(
                
                "🚀 playFromSearch CALLED! Seed: ${seedSong.song.title}, Query: $query"
        )

        // --- CONTEXT-AWARE SHUFFLE: Simpan state shuffle sekarang, lalu paksa OFF ---
        preSearchShuffleState = _uiState.value.isShuffleModeEnabled
        if (preSearchShuffleState) {
            Timber.tag("DEBUG_PLAYER").d(
                    "🔀 [SEARCH] Pre-search shuffle ON → disimpan, lalu dimatikan"
            )
            applyShuffleState(false)
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
           Timber.tag("DEBUG_PLAYER").d( "🚦 Waiting for seed song to be ready...")
            initJob?.join()
           Timber.tag("DEBUG_PLAYER").d( "🟢 Seed song ready. Fetching Smart Queue...")

            generateSmartQueue(seedSong)
        }
    }

    // --- SLEEP TIMER ---


    // --- FITUR DRAG AND DROP QUEUE ---
    fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        val currentPlaylist = _uiState.value.playlist.toMutableList()
        if (fromIndex !in currentPlaylist.indices || toIndex !in currentPlaylist.indices) return

        // 1. Pindahkan item di list UI State
        val item = currentPlaylist.removeAt(fromIndex)
        currentPlaylist.add(toIndex, item)

        // Sesuaikan currentSongIndex secara manual jika lagu yang sedang diputar bergeser
        var newCurrentIndex = _uiState.value.currentSongIndex
        if (fromIndex == newCurrentIndex) {
            newCurrentIndex = toIndex
        } else if (fromIndex < newCurrentIndex && toIndex >= newCurrentIndex) {
            newCurrentIndex--
        } else if (fromIndex > newCurrentIndex && toIndex <= newCurrentIndex) {
            newCurrentIndex++
        }

        _uiState.update { 
            it.copy(
                playlist = currentPlaylist,
                currentSongIndex = newCurrentIndex
            )
        }

        // 2. Sinkronkan dengan ExoPlayer
        mediaController?.moveMediaItem(fromIndex, toIndex)

        // 3. Simpan state antrean terbaru ke Room Database di background
        viewModelScope.launch(Dispatchers.IO) {
            repository.queue.savePlaybackQueue(currentPlaylist, _uiState.value.playingMusicFromPlaylist)
        }
    }

    // --- FITUR LIKE / UNLIKE ---
    fun toggleLike() {
        val currentSong = _uiState.value.currentSong?.song ?: return
        val currentIsLiked = _uiState.value.isLiked
        val userId = SupabaseManager.client.auth.currentSessionOrNull()?.user?.id

        if (userId == null) {
           Timber.tag("DEBUG_PLAYER").e( "❌ Toggle Like Failed: User not logged in")
            return
        }

        // Optimistic Update (Langsung ubah UI biar cepat)
        _uiState.update { it.copy(isLiked = !currentIsLiked) }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.social.toggleLike(
                    songId = currentSong.id,
                    userId = userId,
                    isLiked = currentIsLiked,
                    songMetadata = currentSong,
                    artistName = _uiState.value.currentSong?.displayArtistName,
                    artistPhoto = _uiState.value.currentSong?.artist?.photoUrl
                )
            } catch (e: Exception) {
                // Handle Duplicate Key (Data sudah ada, tapi UI telat tau)
                if (e.message?.contains("duplicate key") == true ||
                                e.message?.contains("23505") == true
                ) {
                    Timber.tag("DEBUG_PLAYER").w(
                            
                            "⚠️ Like Conflict: Data sudah ada. Force update UI ke Liked."
                    )
                    _uiState.update { it.copy(isLiked = true) }
                } else {
                    // Revert on real failure
                    _uiState.update { it.copy(isLiked = currentIsLiked) }
                    Timber.tag("DEBUG_PLAYER").e( "❌ Toggle Like Failed: ${e.message}")
                }
            }
        }
    }

    internal fun checkIfLiked(songId: String) {
        if (songId.startsWith("offline_")) {
            _uiState.update { it.copy(isLiked = false) }
            return
        }
        val userId = SupabaseManager.client.auth.currentSessionOrNull()?.user?.id
        if (userId == null) {
            _uiState.update { it.copy(isLiked = false) }
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            val isLiked = repository.social.isSongLiked(songId, userId)
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

    fun downloadSong(songWithArtist: SongWithArtist) {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()

           Timber.tag("DEBUG_PLAYER").d( "📥 [DOWNLOAD] Memulai proses download untuk: ${songWithArtist.song.title}")

            // 1. Resolve URL (Biasanya Telegram URL expired, jadi kita refresh)
            val resolved = resolveSongUrl(songWithArtist)
            val url = resolved.url

            if (url.isBlank()) {
               Timber.tag("DEBUG_PLAYER").e( "❌ [DOWNLOAD] Gagal mendapatkan URL. Reason: ${resolved.errorType}")
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Gagal mendapatkan link download: ${resolved.errorType}", Toast.LENGTH_SHORT).show()
                }
                return@launch
            }

            // 2. Start Download with Management Notification
            val notifId = songWithArtist.song.id.hashCode()
            val songTitle = songWithArtist.song.title

            NotificationUtils.showDownloadNotification(context, songTitle, 0, false, notifId)

            SongDownloader.downloadSong(
                context = context,
                songWithArtist = songWithArtist,
                url = url,
                onProgress = { progress ->
                    NotificationUtils.showDownloadNotification(context, songTitle, progress, false, notifId)
                },
                onComplete = { success, message ->
                    // Membatalkan notifikasi progress bar (yang silent)
                    androidx.core.app.NotificationManagerCompat.from(context).cancel(notifId)

                    // Menampilkan notifikasi popup (Importance High)
                    if (success) {
                        NotificationUtils.showNotification(context, "Unduh Berhasil", "download lagu $songTitle has completed", notifId + 1)
                    } else {
                        NotificationUtils.showNotification(context, "Unduh Gagal", message ?: "Terjadi kesalahan", notifId + 1)
                    }

                    viewModelScope.launch(Dispatchers.Main) {
                        Toast.makeText(context, message ?: (if (success) "Selesai" else "Gagal"), Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }
    }

    override fun onCleared() {
        audioRouteManager.cleanup()
        sleepTimerManager.cancelSleepTimer()
        super.onCleared()
        mediaController?.release()
    }



    fun updateLyricsConfig(config: com.example.remusic.ui.screen.playmusic.LyricsConfig) {
        lyricsManager.updateLyricsConfig(config)
        viewModelScope.launch {
            userPreferencesRepository.saveLyricsConfig(config)
        }
    }

    // Overload for specific song (e.g. from Queue/Search)
    fun toggleLike(songWithArtist: SongWithArtist) {
        val songId = songWithArtist.song.id
        viewModelScope.launch {
            val user = SupabaseManager.client.auth.currentUserOrNull()
            val userId = user?.id

            if (userId == null) {
               Timber.tag("DEBUG_PLAYER").e( "Cannot toggle like: User not logged in")
                return@launch
            }

            if (repository.social.isSongLiked(songId, userId)) {
                repository.social.toggleLike(
                        songId,
                        userId,
                        true,
                        null,
                        null,
                        null
                )

                // If current song is the one being unliked, update UI state
                if (_uiState.value.currentSong?.song?.id == songId) {
                    _uiState.update { it.copy(isLiked = false) }
                }
            } else {
                repository.social.toggleLike(
                        songId,
                        userId,
                        false,
                        songWithArtist.song,
                    songWithArtist.displayArtistName,
                        songWithArtist.artist?.photoUrl ?: _uiState.value.currentSong?.artist?.photoUrl
                )
                if (_uiState.value.currentSong?.song?.id == songId) {
                    _uiState.update { it.copy(isLiked = true) }
                }
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

    /** Called by PlayMusicScreen when user clicks "Buat Playlist Baru" */
    fun requestNavigateToCreatePlaylist() {
        _uiState.update { it.copy(navigateToCreatePlaylistEvent = true) }
    }

    /** Called by MainScreen after consuming the create playlist navigation event */
    fun consumeNavigateToCreatePlaylistEvent() {
        _uiState.update { it.copy(navigateToCreatePlaylistEvent = false) }
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

    /** Set pending uploader navigation - also emits to SharedFlow for reliable delivery */
    fun setPendingUploaderNavigation(uploaderId: String) {
        _uiState.update { it.copy(pendingUploaderNavigation = uploaderId) }
        viewModelScope.launch { _uploaderNavigationFlow.emit(uploaderId) }
    }

    /** Called by HomeScreen/SearchScreen after consuming the pending uploader navigation */
    fun consumePendingUploaderNavigation() {
        _uiState.update { it.copy(pendingUploaderNavigation = null) }
        viewModelScope.launch {
            _uploaderNavigationFlow.emit(null) // Reset replay cache
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
                repository.social.toggleFollowArtist(user.id, artistId, isCurrentlyFollowed)
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

    // --- LOGIC: ARTIST FOLLOW ---
    internal fun checkIfArtistFollowed(artistId: String) {
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
               Timber.tag("DEBUG_PLAYER").e( "❌ Gagal cek follow status: ${e.message}")
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

                    repository.social.toggleFollowArtist(user.id, currentArtist.id, isCurrentlyFollowed)

                    // Re-check for consistency (optional, but good for sync)
                    // checkIfArtistFollowed(currentArtist.id)
                } else {
                    Timber.tag("DEBUG_PLAYER").w( "User belum login, tidak bisa follow artist.")
                    // TODO: Show login snackbar?
                }
            } catch (e: Exception) {
               Timber.tag("DEBUG_PLAYER").e( "❌ Gagal toggle follow: ${e.message}")
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

    @kotlinx.serialization.Serializable// --- SMART CONNECTIVITY RETRY ---
    internal var hasRetriedCurrentSong = false

    fun onConnectivityRestored() {
        val currentState = _uiState.value
        // Cek apakah sedang error karena network?
        if (currentState.hasError &&
                        currentState.errorType?.contains("Koneksi", ignoreCase = true) == true
        ) {
            // Cek apakah sudah pernah retry untuk lagu ini?
            if (!hasRetriedCurrentSong) {
               Timber.tag("DEBUG_PLAYER").d(
                        
                        "🌐 CONNECTIVITY RESTORED: Retrying playback for '${currentState.currentSong?.song?.title}'"
                )
                hasRetriedCurrentSong = true
                retryPlayback()
            } else {
               Timber.tag("DEBUG_PLAYER").d(
                        
                        "🌐 CONNECTIVITY RESTORED: Already retried this song once. Skipping."
                )
            }
        }
    }
    // Dipanggil saat Log Out
    fun stopPlaybackOnLogout() {
       Timber.tag("DEBUG_PLAYER").d( "🛑 stopPlaybackOnLogout: Membersihkan state & player...")

        // 1. Matikan ExoPlayer
        mediaController?.stop()
        mediaController?.clearMediaItems()

        // 2. Clear UI State
        _uiState.update {
            PlayerUiState() // Reset to default state
        }

        // 3. Clear Room/SQLite and Preferences
        viewModelScope.launch(Dispatchers.IO) {
            repository.queue.savePlaybackQueue(emptyList(), "Unknown Playlist")
            userPreferencesRepository.saveLastSongId("")
            userPreferencesRepository.saveLastPosition(0L)
            userPreferencesRepository.saveLastPlaylistName("")
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

    // =========================================================================
    //                      UPDATE PLAYLIST/LIKED SONGS ORDER
    // =========================================================================

    fun updatePlaylistOrder(playlistId: String, newOrderedSongs: List<SongWithArtist>) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                kotlinx.coroutines.coroutineScope {
                    val deferreds = newOrderedSongs.mapIndexed { index, songWithArtist ->
                        async {
                            com.example.remusic.data.SupabaseManager.client.from("playlist_songs")
                                .update({ set("position", index) }) {
                                    filter {
                                        eq("playlist_id", playlistId)
                                        eq("song_id", songWithArtist.song.id)
                                    }
                                }
                        }
                    }
                    kotlinx.coroutines.awaitAll(*deferreds.toTypedArray())
                }
                Timber.tag("DEBUG_PLAYER").d("Successfully updated playlist order in DB.")
                _uiState.update { state ->
                    val currentPlaylistId = state.currentPlaylistDetails?.id
                    if (currentPlaylistId == playlistId) {
                        state.copy(artistSongs = newOrderedSongs)
                    } else {
                        state
                    }
                }
            } catch (e: Exception) {
                Timber.tag("DEBUG_PLAYER").e("Failed to update playlist order: ${e.message}", e)
            }
        }
    }

    fun updateLikedSongsOrder(newOrderedSongs: List<SongWithArtist>) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val userId = com.example.remusic.data.UserManager.currentUser?.uid ?: return@launch
                Timber.tag("DEBUG_PLAYER").d("Updating liked songs order for $userId. Total: ${newOrderedSongs.size}")
                kotlinx.coroutines.coroutineScope {
                    val deferreds = newOrderedSongs.mapIndexed { index, songWithArtist ->
                        async {
                            Timber.tag("DEBUG_PLAYER").d("Updating liked song ${songWithArtist.song.id} to position $index")
                            com.example.remusic.data.SupabaseManager.client.from("user_song_likes")
                                .update({ set("position", index) }) {
                                    filter {
                                        eq("user_id", userId)
                                        eq("song_id", songWithArtist.song.id)
                                    }
                                }
                        }
                    }
                    kotlinx.coroutines.awaitAll(*deferreds.toTypedArray())
                }
                Timber.tag("DEBUG_PLAYER").d("Successfully updated liked songs order in DB.")
                _uiState.update { state ->
                    state.copy(artistSongs = newOrderedSongs)
                }
            } catch (e: Exception) {
                Timber.tag("DEBUG_PLAYER").e(e, "Failed to update liked songs order: ${e.message}")
            }
        }
    }

    // =========================================================================
    //                      PLAYLIST CRUD
    // =========================================================================

    fun updatePlaylistVisibility(playlistId: String, newVisibility: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                com.example.remusic.data.SupabaseManager.client.from("playlists")
                    .update({ set("visibility", newVisibility.lowercase()) }) {
                        filter { eq("id", playlistId) }
                    }
                
                // Update UI state if this is the currently viewed playlist
                _uiState.update { state ->
                    val currentPlaylist = state.currentPlaylistDetails
                    if (currentPlaylist?.id == playlistId) {
                        state.copy(
                            currentPlaylistDetails = currentPlaylist.copy(visibility = newVisibility.lowercase())
                        )
                    } else {
                        state
                    }
                }
                Timber.tag("DEBUG_PLAYER").d("Successfully updated playlist visibility.")
            } catch (e: Exception) {
                Timber.tag("DEBUG_PLAYER").e("Failed to update playlist visibility: ${e.message}", e)
            }
        }
    }

    fun updatePlaylistProfile(playlistId: String, newTitle: String, newDescription: String, newCoverUrl: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                com.example.remusic.data.SupabaseManager.client.from("playlists")
                    .update(
                        {
                            set("title", newTitle)
                            set("description", newDescription)
                            if (newCoverUrl.isNotBlank()) {
                                set("cover_url", newCoverUrl)
                            }
                        }
                    ) {
                        filter { eq("id", playlistId) }
                    }
                
                // Update UI state
                _uiState.update { state ->
                    val currentPlaylist = state.currentPlaylistDetails
                    if (currentPlaylist?.id == playlistId) {
                        state.copy(
                            currentPlaylistDetails = currentPlaylist.copy(
                                title = newTitle,
                                description = newDescription,
                                coverUrl = if (newCoverUrl.isNotBlank()) newCoverUrl else currentPlaylist.coverUrl
                            )
                        )
                    } else {
                        state
                    }
                }
                Timber.tag("DEBUG_PLAYER").d("Successfully updated playlist profile.")
            } catch (e: Exception) {
                Timber.tag("DEBUG_PLAYER").e("Failed to update playlist profile: ${e.message}", e)
            }
        }
    }

    fun deletePlaylist(playlistId: String, onSuccess: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Delete playlist from Supabase
                com.example.remusic.data.SupabaseManager.client.from("playlists")
                    .delete {
                        filter { eq("id", playlistId) }
                    }
                
                Timber.tag("DEBUG_PLAYER").d("Successfully deleted playlist.")
                
                // Invoke callback on main thread to navigate back
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    onSuccess()
                }
            } catch (e: Exception) {
                Timber.tag("DEBUG_PLAYER").e("Failed to delete playlist: ${e.message}", e)
            }
        }
    }
}
