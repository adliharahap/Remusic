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

// --- EXTENSION FUNCTIONS FOR PLAYER CONNECTION ---


    @OptIn(UnstableApi::class)
    fun PlayMusicViewModel.connectToService() {
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

                        Timber.tag("DEBUG_PLAYER").d("🔔 EXTRAS CHANGED: Sleep Timer Active = $isTimerActive, End = $timerEndTime")

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
                    playerSettingsManager.mediaController = mediaController
                    setupPlayerListener()
                    updatePlayerState()
                    
                    // Sync routing once connected
                    syncRoutingToSystem()
                                viewModelScope.launch {
                        val savedShuffleMode = userPreferencesRepository.shuffleEnabledFlow.first()
                        mediaController?.shuffleModeEnabled = false // MUST ALWAYS BE FALSE FOR MANUAL SHUFFLE
                        // Update UI state juga agar konsisten saat awal
                        _uiState.update { it.copy(isShuffleModeEnabled = savedShuffleMode) }
                       Timber.tag("DEBUG_PLAYER").d( "Applied saved shuffle mode (to UI state): $savedShuffleMode")
                    }
                    viewModelScope.launch {
                        val savedRepeatMode = userPreferencesRepository.repeatModeFlow.first()
                        mediaController?.repeatMode = savedRepeatMode
                        _uiState.update { it.copy(repeatMode = savedRepeatMode) }
                        Timber.tag("DEBUG_PLAYER").d( "Applied saved repeat mode: $savedRepeatMode")
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



    fun PlayMusicViewModel.syncRoutingToSystem() {
        try {
            val command = androidx.media3.session.SessionCommand("SYNC_ROUTING", android.os.Bundle.EMPTY)
            mediaController?.sendCustomCommand(command, android.os.Bundle.EMPTY)
        } catch (e: Exception) {
            Timber.tag("DEBUG_PLAYER").e(e, "Failed to send SYNC_ROUTING")
        }
    }


    fun PlayMusicViewModel.setupPlayerListener() {
        mediaController?.addListener(
                object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        super.onPlaybackStateChanged(playbackState)
                        _uiState.update {
                            it.copy(isBuffering = playbackState == Player.STATE_BUFFERING)
                        }

                        when (playbackState) {
                            Player.STATE_BUFFERING -> startBufferingWatchdog()
                            Player.STATE_READY, Player.STATE_ENDED, Player.STATE_IDLE -> {
                                stopBufferingWatchdog()
                            }
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
                        Timber.tag("DEBUG_PLAYER").e("🔴 Player Error [$errorCode]: $errorMsg")

                        // Update UI state dengan error info
                        _uiState.update {
                            it.copy(
                                    hasError = true,
                                    errorType = "Error Code: $errorCode",
                                    isBuffering = false,
                                    isPlaying = false
                            )
                        }

                        // ── ERROR_CODE_IO_BAD_HTTP_STATUS = 4xx response (URL expired/invalid)
                        // CustomLoadErrorHandlingPolicy sudah stop retry untuk 404/403.
                        // Sekarang kita harus re-resolve URL fresh dari app level.
                        if (errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS) {
                            Timber.tag("DEBUG_PLAYER").w("🔄 HTTP 4xx detected (URL expired). Re-resolving URL fresh...")
                            stopBufferingWatchdog()
                            resolveAndRetryCurrentSong()
                            return
                        }

                        // Auto-recovery untuk network/stream issues:
                        // ERROR_CODE_IO_NETWORK_CONNECTION_FAILED (2001)
                        // ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT (2002)
                        // ERROR_CODE_BEHIND_LIVE_WINDOW (1002)
                        if (errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
                                errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ||
                                errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW
                        ) {
                            Timber.tag("DEBUG_PLAYER").w("🔄 Network error. Attempting auto-recovery...")
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

                        Timber.tag("DEBUG_PLAYER").d( "🔀 TRANSISI: Ganti lagu karena $reasonStr")

                        val player = mediaController ?: return

                        val newIndex = player.currentMediaItemIndex
                        val oldIndex = _uiState.value.currentSongIndex

                       Timber.tag("DEBUG_PLAYER").d( "   📍 Perubahan Index: $oldIndex -> $newIndex")

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
                                            errorType = null,
                                            lyricsOffset = 0L, // Reset offset on transition
                                            temporaryTranslatedLyrics = null, // Clear MLKit translated lyrics
                                            alternativeLyricsResults = emptyList() // Clear cached search results for new song
                                    )
                                }

                                // Reset Smart Retry Flag on transition
                                hasRetriedCurrentSong = false

                               Timber.tag("DEBUG_PLAYER").d(
                                        
                                        "🎵 Lagu Terdeteksi: ${matchedSong.song.title}"
                                )

                                // URL resolution is now handled by MusicService.kt directly.
                                // We rely on MusicService's resume logic to maintain playback state.

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
                                fetchFullSongDetails(matchedSong.song.effectiveId)

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

                                // --- AUTO FETCH SMART QUEUE (5 LAGU TERAKHIR) ---
                                if (newIndex >= playlist.size - 5 && playlist.size >= 5) {
                                    if (!isGeneratingSmartQueue) {
                                       Timber.tag("DEBUG_PLAYER").d( "🔄 Antrean tinggal sedikit. Auto-fetching smart queue berikutnya...")
                                        viewModelScope.launch {
                                            generateSmartQueue(matchedSong)
                                        }
                                    } else {
                                       Timber.tag("DEBUG_PLAYER").d( "⏳ isGeneratingSmartQueue masih true, skip auto-fetch.")
                                    }
                                }
                            } else {
                                Timber.tag("DEBUG_PLAYER").w("   ⚠️ FALLBACK: Menggunakan metadata dari MediaItem (Lagu tidak ada di list local)")
                                // fallback kalau nggak ketemu di playlist
                                _uiState.update {
                                    it.copy(currentSong = SongWithArtist(song = Song(id = mediaItem.mediaId,title = mediaItem.mediaMetadata.title?.toString() ?: "Unknown",audioUrl = mediaItem.localConfiguration?.uri.toString(),coverUrl = mediaItem.mediaMetadata.artworkUri.toString(),lyrics = ""),artist = null),
                                            lyricsOffset = 0L) // Reset offset on transition
                                }
                            }
                        }
                    }

                    // Listener untuk update shuffle dan repeat mode
                    override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                        super.onShuffleModeEnabledChanged(shuffleModeEnabled)
                        // Karena kita pakai manual shuffle, kita hiraukan perubahan dari ExoPlayer
                        // kecuali kalau dipaksa true oleh suatu hal, kita kembalikan ke false.
                        if (shuffleModeEnabled) {
                            mediaController?.shuffleModeEnabled = false
                        }
                    }

                    override fun onRepeatModeChanged(repeatMode: Int) {
                        _uiState.update { it.copy(repeatMode = repeatMode) }

                        viewModelScope.launch {
                            userPreferencesRepository.setRepeatMode(repeatMode)
                           Timber.tag("DEBUG_PLAYER").d( "Saved new repeat mode: $repeatMode")
                        }
                    }
                }
        )
    }

    /**
     * Auto-recovery mechanism untuk handle playback errors Akan mencoba resume playback di posisi
     * terakhir setelah 2 detik
     */
    fun PlayMusicViewModel.handleAutoRecovery() {
        viewModelScope.launch {
            val controller = mediaController ?: return@launch
            val currentPosition = controller.currentPosition
            val currentMediaItem = controller.currentMediaItem

            if (currentMediaItem != null && currentPosition >= 0) {
                delay(2000) // Wait 2s sebelum retry (beri waktu network pulih)

               Timber.tag("DEBUG_PLAYER").d(
                        
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
                Timber.tag("DEBUG_PLAYER").w( "⚠️ Cannot recover: MediaItem or position invalid")
            }
        }
    }



    fun PlayMusicViewModel.updatePlayerState() {
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

    // ─────────────────────────────────────────────────────────────
    // BUFFERING WATCHDOG — Pantang Menyerah (No Skip)
    // Loop terus sampai lagu berhasil diputar atau user ganti lagu sendiri.
    // TIDAK pernah skip otomatis — selagi masalahnya network, terus coba.
    // ─────────────────────────────────────────────────────────────
    fun PlayMusicViewModel.startBufferingWatchdog() {
        // Jangan start watchdog baru jika sudah jalan
        if (bufferingWatchdogJob?.isActive == true) return

        bufferingWatchdogJob = viewModelScope.launch {
            val watchdogTag = "BufferingWatchdog"
            var retryCount = 0

            // Loop selamanya — hanya berhenti jika:
            // 1. Lagu berhasil diputar (tidak buffering lagi)
            // 2. User manual ganti lagu (song ID berubah)
            // 3. watchdog di-cancel dari luar (stopBufferingWatchdog)
            while (isActive) {
                val songIdAtStart = _uiState.value.currentSong?.song?.id

                // Tunggu 3 detik sebelum cek
                delay(3000L)

                val controller = mediaController ?: break

                // Sudah tidak buffering → selesai, lagu berhasil diputar
                if (controller.playbackState != Player.STATE_BUFFERING) {
                    Timber.tag(watchdogTag).d("✅ Sudah tidak buffering. Watchdog selesai.")
                    break
                }
                // Lagu sudah ganti (user klik next/prev) → selesai
                if (_uiState.value.currentSong?.song?.id != songIdAtStart) {
                    Timber.tag(watchdogTag).d("✅ Lagu sudah ganti. Watchdog selesai.")
                    break
                }

                retryCount++
                Timber.tag(watchdogTag).w("🔴 STUCK BUFFERING retry #$retryCount. Re-resolving URL fresh...")

                // SELALU re-resolve URL fresh — karena URL bisa expired (404)
                val currentSong = _uiState.value.currentSong ?: break
                val currentIndex = _uiState.value.currentSongIndex

                val result = withContext(Dispatchers.IO) {
                    try { resolveSongUrl(currentSong) } catch (e: Exception) {
                        if (e is kotlinx.coroutines.CancellationException) throw e
                        Timber.tag(watchdogTag).w("⚠️ Exception resolve: ${e.message}. Loop lagi...")
                        null
                    }
                } ?: continue // Gagal resolve → loop lagi 3 detik kemudian

                if (result.url.isNotBlank()) {
                    Timber.tag(watchdogTag).d("✅ URL fresh retry #$retryCount: ${result.url.takeLast(40)}")
                    val newItem = createMediaItem(currentSong, result.url)
                    withContext(Dispatchers.Main) {
                        if (isActive && controller.currentMediaItemIndex == currentIndex) {
                            val resumePos = controller.currentPosition.coerceAtLeast(0L)
                            controller.stop()
                            controller.replaceMediaItem(currentIndex, newItem)
                            if (resumePos > 500L) controller.seekTo(currentIndex, resumePos)
                            controller.prepare()
                            controller.play()
                            Timber.tag(watchdogTag).d("▶ Resume di ${resumePos}ms dengan URL baru.")
                        }
                    }
                    // Tidak break di sini — biarkan loop cek lagi di iterasi berikutnya
                    // apakah player benar-benar sudah tidak buffering
                } else {
                    Timber.tag(watchdogTag).w("⚠️ URL kosong. Loop lagi dalam 3 detik...")
                    // Loop terus
                }
            }
        }
    }




    fun PlayMusicViewModel.stopBufferingWatchdog() {
        if (bufferingWatchdogJob?.isActive == true) {
            bufferingWatchdogJob?.cancel()
            Timber.tag("BufferingWatchdog").d("🛑 Watchdog STOPPED")
        }
        bufferingWatchdogJob = null
    }


    @OptIn(UnstableApi::class)
    fun PlayMusicViewModel.startUpdatingPosition() {
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
                               Timber.tag("DEBUG_PLAYER").d(
                                        
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
                                   Timber.tag("DEBUG_PLAYER").d(
                                            
                                            "[VALID PLAY] User listening for ${actualListeningTimeMs/1000}s. Incrementing..."
                                    )
                                    viewModelScope.launch(Dispatchers.IO) {
                                        val currentSongWA = _uiState.value.currentSong
                                        val songMetadata = currentSongWA?.song
                                        repository.incrementPlayCount(
                                            songId = currentSongId,
                                            songMetadata = songMetadata,
                                            artistName = currentSongWA?.displayArtistName,
                                            artistPhoto = currentSongWA?.artist?.photoUrl
                                        )
                                        repository.addToPlayHistory(currentSongId)
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
                               Timber.tag("DEBUG_PLAYER").d(
                                        
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



    fun PlayMusicViewModel.stopUpdatingPosition() {
        positionJob?.cancel()
        positionJob = null
    }



    fun PlayMusicViewModel.retryPlayback() {
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
                   Timber.tag("DEBUG_PLAYER").d(
                            
                            "✅ RETRY SUCCESS for '${currentSong.song.title}': URL resolved."
                    )

                    // 1. Recover Metadata (Lyrics, etc)
                    if (currentSong != null) {
                        fetchFullSongDetails(currentSong.song.effectiveId, forceUpdate = true)
                    }

                    // 2. Replace Media Item & Play
                    val newItem = createMediaItem(currentSong, result.url)

                    if (currentIndex < controller.mediaItemCount) {
                        // Cek apakah user masih menunggu lagu yang sama?
                        if (_uiState.value.currentSongIndex == currentIndex) {
                           Timber.tag("DEBUG_PLAYER").d( "▶ RESUMING PLAYBACK...")
                            controller.replaceMediaItem(currentIndex, newItem)

                            // Jika ExoPlayer index tertinggal gara-gara jumpToSong sebelumnya error, paksakan seek
                            if (controller.currentMediaItemIndex != currentIndex) {
                                controller.seekTo(currentIndex, C.TIME_UNSET)
                            }

                            controller.prepare() // Wajib prepare ulang jika sebelumnya error
                            controller.playWhenReady = true // Force play
                            controller.play()
                        } else {
                            Timber.tag("DEBUG_PLAYER").w(
                                    
                                    "⚠️ User moved to another song. Retry result ignored."
                            )
                        }
                    }

                    // 3. RECOVER SMART QUEUE (If only 1 song exists - e.g. played from search/quick
                    // pick)
                    val currentPlaylistSize = _uiState.value.playlist.size
                    if (currentPlaylistSize == 1) {
                       Timber.tag("DEBUG_PLAYER").d(
                                
                                "♻️ RESTORING QUEUE: Only 1 song in playlist. Generating Smart Queue..."
                        )
                        viewModelScope.launch { generateSmartQueue(currentSong) }
                    }
                } else {
                   Timber.tag("DEBUG_PLAYER").e(
                            
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

