package com.example.remusic.viewmodel.playmusic.managers

import android.content.Context
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheWriter
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaController
import com.example.remusic.data.model.SongWithArtist
import com.example.remusic.data.repository.MusicRepository
import com.example.remusic.utils.RemusicCache
import com.example.remusic.viewmodel.playmusic.AnimationDirection
import com.example.remusic.viewmodel.playmusic.PlayerUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import timber.log.Timber

@OptIn(UnstableApi::class)
class CrossfadeManager(
    private val context: Context,
    private val scope: CoroutineScope,
    private val uiState: MutableStateFlow<PlayerUiState>,
    private val getMediaController: () -> MediaController?,
    private val getTargetVolume: () -> Float,
    private val onTransitionToNextSong: (nextIndex: Int, nextSong: SongWithArtist, startPositionMs: Long) -> Unit,
    private val onExtractColor: (coverUrl: String) -> Unit,
    private val onFetchDetails: (effectiveId: String) -> Unit,
    private val onCheckIfLiked: (songId: String) -> Unit,
    private val onPrefetchNeighbor: (index: Int) -> Unit,
    private val onResolveSongUrl: suspend (SongWithArtist) -> MusicRepository.ResolvedSongUrl,
    private val onUpdatePlaylistSongUrl: (index: Int, song: SongWithArtist, url: String) -> Unit
) {
    companion object {
        private const val TAG = "CrossfadeManager"
        private const val STEP_INTERVAL_MS = 30L
        private const val MIN_TRACK_DURATION_FOR_CROSSFADE_MS = 10_000L
        private const val PRECACHE_BYTES = 1024 * 1024L // 1 MB (~60 detik audio 128kbps)
    }

    @Volatile
    var isCrossfading: Boolean = false
        private set

    @Volatile
    var isShowingNextSongInCrossfade: Boolean = false
        private set

    @Volatile
    var shouldSuppressPositionUpdate: Boolean = false
        private set

    private var crossfadeJob: Job? = null
    private var preloadJob: Job? = null
    private var fadePlayer: ExoPlayer? = null
    private var hasTriggeredForCurrentSong: Boolean = false
    private var currentTrackingSongId: String? = null
    private var targetNextSongId: String? = null
    private var preloadedSongId: String? = null
    private var isPreloading: Boolean = false
    private var crossfadeStartTime: Long = 0L

    /**
     * Dipanggil berkala dari loop posisi pemutaran (startUpdatingPosition)
     * untuk mem-preload & mem-precache lagu berikutnya di pertengahan lagu.
     */
    fun checkAndTriggerPreload(currentPositionMs: Long, totalDurationMs: Long) {
        val state = uiState.value
        if (!state.isCrossfadeEnabled) return
        if (isCrossfading) return
        if (totalDurationMs < MIN_TRACK_DURATION_FOR_CROSSFADE_MS) return

        // Pemicu: Di pertengahan lagu atau minimal 15 detik sebelum durasi crossfade
        val crossfadeMs = state.crossfadeDurationSeconds * 1000L
        val halfwayMs = totalDurationMs / 2
        val triggerThreshold = halfwayMs.coerceAtMost(
            (totalDurationMs - crossfadeMs - 10_000L).coerceAtLeast(5_000L)
        )

        if (currentPositionMs < triggerThreshold) return

        val playlist = state.playlist
        if (playlist.isEmpty()) return

        val currentIndex = state.currentSongIndex
        val repeatMode = state.repeatMode
        val nextIndex = when {
            repeatMode == Player.REPEAT_MODE_ONE -> currentIndex
            currentIndex + 1 < playlist.size -> currentIndex + 1
            repeatMode == Player.REPEAT_MODE_ALL && playlist.isNotEmpty() -> 0
            else -> -1
        }

        if (nextIndex == -1 || nextIndex !in playlist.indices) return
        val nextSong = playlist[nextIndex]

        if (preloadedSongId == nextSong.song.id) return
        if (isPreloading) return

        isPreloading = true
        preloadJob = scope.launch(Dispatchers.IO) {
            try {
                Timber.tag(TAG).d("🔄 [PRELOAD] Memulai resolusi lagu berikutnya '${nextSong.song.title}' (Index $nextIndex)")
                val result = onResolveSongUrl(nextSong)
                val resolvedUrl = result.url

                if (resolvedUrl.isNotBlank()) {
                    withContext(Dispatchers.Main) {
                        onUpdatePlaylistSongUrl(nextIndex, nextSong, resolvedUrl)
                    }

                    // Pre-cache 1MB (~60 detik awal) jika URL online streaming
                    if (!resolvedUrl.startsWith("content://") && !resolvedUrl.startsWith("file://") && result.source != "LOCAL_CACHE") {
                        precacheAudio(resolvedUrl, nextSong.song.effectiveId)
                    }
                    preloadedSongId = nextSong.song.id
                    Timber.tag(TAG).d("✅ [PRELOAD] Lagu '${nextSong.song.title}' selesai di-precache!")
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Timber.tag(TAG).w("⚠️ [PRELOAD] Gagal preload lagu berikutnya: ${e.message}")
            } finally {
                isPreloading = false
            }
        }
    }

    /**
     * Cache 1MB awal lagu ke SimpleCache secara background
     */
    private suspend fun precacheAudio(url: String, cacheKey: String) = withContext(Dispatchers.IO) {
        try {
            val simpleCache = RemusicCache.getInstance(context)
            // Cek jika sudah ter-cache minimal 300KB
            if (simpleCache.isCached(cacheKey, 0, 300 * 1024L)) {
                Timber.tag(TAG).d("⚡ [PRECACHE] Audio sudah ada di SimpleCache untuk key: $cacheKey")
                return@withContext
            }

            val streamingOkHttpClient = OkHttpClient.Builder()
                .followRedirects(true)
                .followSslRedirects(true)
                .build()
            val httpDataSourceFactory = OkHttpDataSource.Factory(streamingOkHttpClient)
                .setDefaultRequestProperties(mapOf(
                    "Origin" to "https://www.youtube.com",
                    "Referer" to "https://www.youtube.com/"
                ))
            val defaultDataSourceFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)
            val upstreamDataSource = defaultDataSourceFactory.createDataSource()
            val cacheDataSource = CacheDataSource(
                simpleCache,
                upstreamDataSource,
                CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR
            )

            val dataSpec = DataSpec.Builder()
                .setUri(url.toUri())
                .setKey(cacheKey)
                .setPosition(0)
                .setLength(PRECACHE_BYTES)
                .build()

            Timber.tag(TAG).d("📥 [PRECACHE] Men-download 1MB awal lagu berikutnya (key: $cacheKey)...")
            val cacheWriter = CacheWriter(
                cacheDataSource,
                dataSpec,
                null,
                null
            )
            cacheWriter.cache()
            Timber.tag(TAG).d("✅ [PRECACHE] Berhasil men-cache 1MB awal (key: $cacheKey)!")
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Timber.tag(TAG).w("⚠️ [PRECACHE] Peringatan caching: ${e.message}")
        }
    }

    /**
     * Dipanggil berkala dari loop posisi pemutaran (startUpdatingPosition)
     */
    fun checkAndTriggerCrossfade(currentPositionMs: Long, totalDurationMs: Long) {
        val state = uiState.value
        if (!state.isCrossfadeEnabled) return
        if (isCrossfading) return
        if (hasTriggeredForCurrentSong) return

        val crossfadeSeconds = state.crossfadeDurationSeconds
        if (crossfadeSeconds <= 0) return

        val crossfadeDurationMs = crossfadeSeconds * 1000L

        // Jangan crossfade jika durasi lagu terlalu pendek atau belum valid
        if (totalDurationMs < MIN_TRACK_DURATION_FOR_CROSSFADE_MS || totalDurationMs <= crossfadeDurationMs + 2000L) {
            return
        }

        val remainingMs = totalDurationMs - currentPositionMs
        if (remainingMs in 1L..crossfadeDurationMs) {
            val effectiveFadeMs = remainingMs.coerceAtLeast(1000L)
            triggerCrossfade(effectiveFadeMs)
        }
    }

    private fun triggerCrossfade(crossfadeDurationMs: Long) {
        val controller = getMediaController() ?: return
        if (!controller.isPlaying) return

        val state = uiState.value
        val playlist = state.playlist
        if (playlist.isEmpty()) return

        val currentIndex = state.currentSongIndex
        val repeatMode = state.repeatMode

        // 1. Pastikan lagu next nya ada
        val nextIndex = when {
            repeatMode == Player.REPEAT_MODE_ONE -> currentIndex
            currentIndex + 1 < playlist.size -> currentIndex + 1
            repeatMode == Player.REPEAT_MODE_ALL && playlist.isNotEmpty() -> 0
            else -> -1
        }

        hasTriggeredForCurrentSong = true

        if (nextIndex != -1 && nextIndex in playlist.indices) {
            val nextSong = playlist[nextIndex]
            isCrossfading = true
            uiState.update { it.copy(isCrossfading = true) }
            executeCrossfadeToNextSong(controller, nextIndex, nextSong, crossfadeDurationMs)
        } else {
            // Akhir antrean (tidak ada lagu berikutnya): fade out perlahan hingga berhenti
            isCrossfading = true
            uiState.update { it.copy(isCrossfading = true) }
            executeFadeOutAtEnd(controller, crossfadeDurationMs)
        }
    }

    private fun createFadePlayer(): ExoPlayer {
        val simpleCache = RemusicCache.getInstance(context)
        val streamingOkHttpClient = OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
        val httpDataSourceFactory = OkHttpDataSource.Factory(streamingOkHttpClient)
            .setDefaultRequestProperties(mapOf(
                "Origin" to "https://www.youtube.com",
                "Referer" to "https://www.youtube.com/"
            ))
        val defaultDataSourceFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)
        val cacheDataSourceFactory = CacheDataSource.Factory()
            .setCache(simpleCache)
            .setUpstreamDataSourceFactory(defaultDataSourceFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

        return ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(cacheDataSourceFactory))
            .build()
    }

    private fun releaseFadePlayer() {
        fadePlayer?.let { fp ->
            try {
                fp.stop()
                fp.release()
            } catch (e: Exception) {
                Timber.tag(TAG).w("Gagal release fadePlayer: ${e.message}")
            }
        }
        fadePlayer = null
    }

    fun getNextSongPosition(): Long? {
        return if (isCrossfading && isShowingNextSongInCrossfade) {
            fadePlayer?.currentPosition
        } else {
            null
        }
    }

    private fun executeCrossfadeToNextSong(
        controller: MediaController,
        nextIndex: Int,
        nextSong: SongWithArtist,
        crossfadeDurationMs: Long
    ) {
        crossfadeJob?.cancel()
        releaseFadePlayer()

        crossfadeJob = scope.launch(Dispatchers.Main) {
            try {
                // 1. Pastikan URL Lagu 2 sudah siap
                val resolvedUrl = if (!nextSong.song.audioUrl.isNullOrBlank()) {
                    nextSong.song.audioUrl
                } else {
                    Timber.tag(TAG).d("⏳ URL lagu berikutnya belum ada, resolving segera...")
                    val res = withContext(Dispatchers.IO) { onResolveSongUrl(nextSong) }
                    if (res.url.isNotBlank()) {
                        withContext(Dispatchers.Main) {
                            onUpdatePlaylistSongUrl(nextIndex, nextSong, res.url)
                        }
                    }
                    res.url
                }

                if (resolvedUrl.isBlank()) {
                    Timber.tag(TAG).w("⚠️ Gagal resolve URL lagu berikutnya untuk crossfade")
                    return@launch
                }

                val resolvedNextSong = nextSong.copy(song = nextSong.song.copy(audioUrl = resolvedUrl))
                targetNextSongId = resolvedNextSong.song.id

                Timber.tag(TAG).d("🔀 Memulai Crossfade Spotify-style ke lagu '${resolvedNextSong.song.title}' (Index $nextIndex) | Durasi Total: ${crossfadeDurationMs}ms")

                // 2. Mulai Lagu 2 di fadePlayer dengan volume 0 (silent playback dari 0:00)
                try {
                    val fp = createFadePlayer()
                    val mediaItem = MediaItem.Builder()
                        .setUri(resolvedUrl)
                        .setCustomCacheKey(resolvedNextSong.song.effectiveId)
                        .build()
                    fp.setMediaItem(mediaItem)
                    fp.seekTo(0L)
                    fp.volume = 0f
                    fp.prepare()
                    fp.play()
                    fadePlayer = fp
                    Timber.tag(TAG).d("🔇 [FADE_PLAYER] Lagu 2 '${resolvedNextSong.song.title}' mulai diputar diam-diam (volume 0f) dari 0:00")
                } catch (e: Exception) {
                    Timber.tag(TAG).w("⚠️ [FADE_PLAYER] Gagal inisialisasi fade player Lagu 2: ${e.message}")
                    fadePlayer = null
                    return@launch
                }

                // 3. FASE 1: Paruh Pertama (Lagu 1 tetap bersuara normal, Lagu 2 jalan tanpa suara dari 0s)
                val halfDurationMs = (crossfadeDurationMs / 2).coerceAtLeast(1000L)
                val fadeDurationMs = (crossfadeDurationMs - halfDurationMs).coerceAtLeast(1000L)

                Timber.tag(TAG).d("⏳ Fase 1: Silent play Lagu 2 selama ${halfDurationMs}ms (Lagu 1 tetap main penuh)...")
                val phase1Start = System.currentTimeMillis()
                while (isActive && System.currentTimeMillis() - phase1Start < halfDurationMs) {
                    if (!controller.isPlaying) {
                        if (controller.currentMediaItemIndex == nextIndex) {
                            Timber.tag(TAG).d("🎵 [FASE 1] ExoPlayer auto-advance ke Lagu 2 lebih awal. Lanjut ke Fase 2/3.")
                            break
                        } else {
                            Timber.tag(TAG).d("🛑 [FASE 1] Controller di-pause oleh user. Batalkan crossfade.")
                            cancelCrossfade("Paused by user in Phase 1")
                            return@launch
                        }
                    }
                    delay(50L)
                }

                if (!isActive) return@launch

                // 4. FASE 2: Paruh Kedua (Pertengahan Crossfade)
                // Di pertengahan crossfade:
                // - Update UI ke Lagu 2 (posisi Lagu 2 sudah di ~halfDurationMs, misal 5s)
                // - Naikkan volume Lagu 2 ke targetVolume
                // - Turunkan volume Lagu 1 ke 0f
                val song2CurrentPos = (fadePlayer?.currentPosition ?: halfDurationMs).coerceAtLeast(0L)
                Timber.tag(TAG).d("🎚️ Fase 2: Mulai Crossfade Audio! Lagu 2 saat ini sudah di ${song2CurrentPos}ms. Fading over ${fadeDurationMs}ms...")

                currentTrackingSongId = resolvedNextSong.song.id
                isShowingNextSongInCrossfade = true

                uiState.update { current ->
                    current.copy(
                        currentSong = resolvedNextSong,
                        currentSongIndex = nextIndex,
                        animationDirection = AnimationDirection.FORWARD,
                        currentPosition = song2CurrentPos,
                        totalDuration = if (resolvedNextSong.song.durationMs > 0) resolvedNextSong.song.durationMs else 0L,
                        lyricsOffset = 0L,
                        temporaryTranslatedLyrics = null,
                        alternativeLyricsResults = emptyList(),
                        uploader = null,
                        hasError = false,
                        errorType = null,
                        isCrossfading = true
                    )
                }

                onExtractColor(resolvedNextSong.song.coverUrl ?: "")
                onFetchDetails(resolvedNextSong.song.effectiveId)
                onCheckIfLiked(resolvedNextSong.song.id)
                onPrefetchNeighbor(nextIndex + 1)

                val targetVolume = getTargetVolume().coerceIn(0.1f, 1f)
                val song1StartVol = controller.volume.coerceIn(0.1f, 1f)
                val steps = (fadeDurationMs / STEP_INTERVAL_MS).toInt().coerceAtLeast(1)
                val fadeInStepVol = targetVolume / steps
                val fadeOutStepVol = song1StartVol / steps

                for (i in 1..steps) {
                    if (!isActive) break
                    if (!controller.isPlaying && controller.currentMediaItemIndex == nextIndex) {
                        Timber.tag(TAG).d("🎵 [FASE 2] ExoPlayer auto-advance ke Lagu 2 saat fading out Lagu 1. Lanjut ke Fase 3.")
                        break
                    }
                    val newInVol = (fadeInStepVol * i).coerceIn(0f, targetVolume)
                    val newOutVol = (song1StartVol - (fadeOutStepVol * i)).coerceIn(0f, song1StartVol)

                    fadePlayer?.volume = newInVol
                    // Hanya ubah volume controller jika masih di Lagu 1
                    if (controller.currentMediaItemIndex != nextIndex) {
                        controller.volume = newOutVol
                    }
                    delay(STEP_INTERVAL_MS)
                }

                // Jika controller masih di Lagu 1, mutekan sebelum dipause
                if (controller.currentMediaItemIndex != nextIndex) {
                    controller.volume = 0f
                }
                // Hentikan Lagu 1 agar tidak mencapai EOF dan memicu auto-advance default ExoPlayer
                controller.pause()
                fadePlayer?.volume = targetVolume

                // 5. FASE 3: Seamless Hand-off ke Controller Utama
                // Lagu 1 sudah habis (volume 0f), Lagu 2 sudah full volume
                val finalSong2Pos = fadePlayer?.currentPosition ?: 0L
                Timber.tag(TAG).d("🔄 Fase 3: Handoff ke controller utama pada posisi ${finalSong2Pos}ms...")
                
                controller.volume = targetVolume

                // Minta UI dan ViewModel untuk pindah ke lagu berikutnya di controller
                onTransitionToNextSong(nextIndex, resolvedNextSong, finalSong2Pos)

                // TUNGGU controller selesai buffering dan mulai memutar (untuk mencegah jedug/gap audio)
                withContext(Dispatchers.Main) {
                    kotlinx.coroutines.withTimeoutOrNull(2000L) {
                        while (isActive) {
                            if (controller.currentMediaItemIndex == nextIndex && controller.isPlaying) {
                                Timber.tag(TAG).d("✅ Controller sudah siap dan memutar Lagu 2! Handoff mulus.")
                                break
                            }
                            kotlinx.coroutines.delay(20L)
                        }
                    }
                }

                // Jeda toleransi 150ms agar controller mulai output audio sebelum fadePlayer dimatikan
                delay(150L)

                Timber.tag(TAG).d("✅ Crossfade selesai! Lagu 2 melanjutkan playback di controller dari ${finalSong2Pos}ms.")
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Timber.tag(TAG).e(e, "❌ Kesalahan selama proses crossfade")
                controller.volume = getTargetVolume()
            } finally {
                releaseFadePlayer()
                targetNextSongId = null
                isShowingNextSongInCrossfade = false
                isCrossfading = false
                uiState.update { it.copy(isCrossfading = false) }
            }
        }
    }

    private fun executeFadeOutAtEnd(
        controller: MediaController,
        crossfadeDurationMs: Long
    ) {
        crossfadeJob?.cancel()
        crossfadeJob = scope.launch(Dispatchers.Main) {
            try {
                Timber.tag(TAG).d("🔀 Akhir playlist: Fade out lagu terakhir hingga selesai (${crossfadeDurationMs}ms)")
                val startVolume = controller.volume
                val steps = (crossfadeDurationMs / STEP_INTERVAL_MS).toInt().coerceAtLeast(1)
                val stepVol = startVolume / steps

                for (i in 1..steps) {
                    if (!isActive) return@launch
                    val newVol = (startVolume - (stepVol * i)).coerceIn(0f, 1f)
                    controller.volume = newVol
                    delay(STEP_INTERVAL_MS)
                }
                controller.volume = 0f
                controller.pause()
                controller.volume = getTargetVolume()
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                controller.volume = getTargetVolume()
            } finally {
                isCrossfading = false
                uiState.update { it.copy(isCrossfading = false) }
            }
        }
    }

    fun getSimulatedCrossfadePosition(): Long = 0L

    fun onSongChanged(newSongId: String?) {
        if (newSongId != null) {
            if (isCrossfading && newSongId == targetNextSongId) {
                Timber.tag(TAG).d("🎵 onSongChanged: Transisi terencana ke target crossfade '$newSongId'")
                currentTrackingSongId = newSongId
                return
            }
            if (newSongId != currentTrackingSongId) {
                currentTrackingSongId = newSongId
                hasTriggeredForCurrentSong = false
                preloadedSongId = null
                if (isCrossfading) {
                    cancelCrossfade("Song changed to unexpected song")
                }
            }
        }
    }

    fun onSeek(newPositionMs: Long, totalDurationMs: Long) {
        cancelCrossfade("Seek triggered")
        val crossfadeDurationMs = uiState.value.crossfadeDurationSeconds * 1000L
        if (totalDurationMs - newPositionMs > crossfadeDurationMs + 1500L) {
            hasTriggeredForCurrentSong = false
        }
    }

    fun cancelCrossfade(reason: String = "Manual") {
        if (isCrossfading || crossfadeJob?.isActive == true || fadePlayer != null) {
            Timber.tag(TAG).d("🛑 Crossfade dibatalkan: $reason")
            crossfadeJob?.cancel()
            crossfadeJob = null
            targetNextSongId = null
            releaseFadePlayer()
            isShowingNextSongInCrossfade = false
            isCrossfading = false
            shouldSuppressPositionUpdate = false
            uiState.update { it.copy(isCrossfading = false) }
            try {
                getMediaController()?.volume = getTargetVolume()
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Gagal mengembalikan volume setelah cancel crossfade")
            }
        }
    }

    fun release() {
        cancelCrossfade("Release")
        preloadJob?.cancel()
        preloadJob = null
    }
}
