package com.example.remusic.services

import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.LibraryResult
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaSession.ControllerInfo
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import com.example.remusic.utils.RemusicCache
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import com.example.remusic.utils.NotificationUtils
import com.example.remusic.utils.toHdUrl
import okhttp3.OkHttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import android.util.Log
import com.example.remusic.data.local.MusicDatabase
import com.example.remusic.data.repository.MusicRepository
import androidx.media3.common.Player

@UnstableApi
class MusicService : MediaLibraryService() {
    private var mediaSession: MediaLibrarySession? = null
    private lateinit var playerA: ExoPlayer
    private lateinit var playerB: ExoPlayer
    private lateinit var crossfadeManager: ServiceCrossfadeManager
    
    // HTTP Data Source Factory (Made class-level so resolveMediaItemUrl can update headers)
    private lateinit var httpDataSourceFactory: androidx.media3.datasource.okhttp.OkHttpDataSource.Factory
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private lateinit var sleepTimerManager: SleepTimerManager

    // Callback session tetap sama...
    private val mediaSessionCallback = object : MediaLibrarySession.Callback {
        
        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: ControllerInfo,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val isRecent = params?.isRecent ?: false
            val rootId = if (isRecent) "recent_root" else "root"
            
            val rootMediaItem = MediaItem.Builder()
                .setMediaId(rootId)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setIsBrowsable(true)
                        .setIsPlayable(false)
                        .build()
                )
                .build()
            
            return Futures.immediateFuture(LibraryResult.ofItem(rootMediaItem, LibraryParams.Builder().setRecent(isRecent).build()))
        }

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<com.google.common.collect.ImmutableList<MediaItem>>> {
            if (parentId == "recent_root") {
                val future = com.google.common.util.concurrent.SettableFuture.create<LibraryResult<com.google.common.collect.ImmutableList<MediaItem>>>()
                serviceScope.launch {
                    try {
                        val database = MusicDatabase.getDatabase(this@MusicService)
                        val repository = MusicRepository(database.musicDao(), database.localPlayHistoryDao())
                        val (queue, _) = repository.queue.restorePlaybackQueue()
                        
                        val userPrefs = com.example.remusic.data.preferences.UserPreferencesRepository(this@MusicService)
                        val lastIndex = userPrefs.lastPositionFlow.first()
                        val savedSongId = userPrefs.lastSongIdFlow.first()
                        
                        // Find the actual item based on lastSongId or fallback to the first item
                        val indexToPlay = queue.indexOfFirst { it.song.id == savedSongId }.coerceAtLeast(0)
                        
                        if (queue.isNotEmpty()) {
                            val lastSong = queue[indexToPlay]
                            
                            val mediaItem = MediaItem.Builder()
                                .setMediaId(lastSong.song.id)
                                .setUri(lastSong.song.audioUrl ?: "")
                                .setMediaMetadata(
                                    MediaMetadata.Builder()
                                        .setTitle(lastSong.song.title)
                                        .setArtist(lastSong.artist?.name ?: "Unknown")
                                        .setArtworkUri(android.net.Uri.parse(lastSong.song.coverUrl?.toHdUrl() ?: ""))
                                        .setIsPlayable(true)
                                        .setIsBrowsable(false)
                                        .build()
                                )
                                .build()
                            
                            future.set(LibraryResult.ofItemList(listOf(mediaItem), params))
                        } else {
                            future.set(LibraryResult.ofItemList(emptyList(), params))
                        }
                    } catch (e: Exception) {
                        Log.e("MusicService", "Failed to load recent item", e)
                        future.set(LibraryResult.ofItemList(emptyList(), params))
                    }
                }
                return future
            }
            
            return Futures.immediateFuture(LibraryResult.ofItemList(emptyList(), params))
        }

        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: ControllerInfo
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            val future = com.google.common.util.concurrent.SettableFuture.create<MediaSession.MediaItemsWithStartPosition>()
            serviceScope.launch {
                try {
                    val database = MusicDatabase.getDatabase(this@MusicService)
                    val repository = MusicRepository(database.musicDao(), database.localPlayHistoryDao())
                    val (queue, _) = repository.queue.restorePlaybackQueue()
                    
                    if (queue.isEmpty()) {
                        withContext(Dispatchers.Main) {
                            val emptyList = mutableListOf<MediaItem>()
                            future.set(MediaSession.MediaItemsWithStartPosition(emptyList, 0, 0L))
                        }
                        return@launch
                    }
                    
                    val userPrefs = com.example.remusic.data.preferences.UserPreferencesRepository(this@MusicService)
                    val savedSongId = userPrefs.lastSongIdFlow.first()
                    val lastPositionMs = userPrefs.lastPositionFlow.first()
                    
                    val mediaItems = queue.map { item ->
                        MediaItem.Builder()
                            .setMediaId(item.song.id)
                            .setUri(item.song.audioUrl ?: "") // It's fine if empty, the player handles it
                            .setMediaMetadata(
                                MediaMetadata.Builder()
                                    .setTitle(item.song.title)
                                    .setArtist(item.artist?.name ?: "Unknown")
                                    .setArtworkUri(android.net.Uri.parse(item.song.coverUrl?.toHdUrl() ?: ""))
                                    .build()
                            )
                            .build()
                    }
                    
                    val startIndex = mediaItems.indexOfFirst { it.mediaId == savedSongId }.coerceAtLeast(0)
                    
                    withContext(Dispatchers.Main) {
                        val player = mediaSession.player
                        player.playWhenReady = false // Prevent auto-play immediately on bound
                        
                        future.set(MediaSession.MediaItemsWithStartPosition(
                            mediaItems,
                            startIndex,
                            lastPositionMs
                        ))
                    }
                } catch (e: Exception) {
                    Log.e("MusicService", "Failed to resume playback", e)
                    withContext(Dispatchers.Main) {
                        future.set(MediaSession.MediaItemsWithStartPosition(emptyList(), 0, 0L))
                    }
                }
            }
            return future
        }

        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                .add(androidx.media3.session.SessionCommand("START_SLEEP_TIMER", android.os.Bundle.EMPTY))
                .add(androidx.media3.session.SessionCommand("STOP_SLEEP_TIMER", android.os.Bundle.EMPTY))
                .add(androidx.media3.session.SessionCommand("SET_PREFERRED_AUDIO_DEVICE", android.os.Bundle.EMPTY))
                .add(androidx.media3.session.SessionCommand("SYNC_ROUTING", android.os.Bundle.EMPTY))
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(sessionCommands)
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: androidx.media3.session.SessionCommand,
            args: android.os.Bundle
        ): ListenableFuture<androidx.media3.session.SessionResult> {
            android.util.Log.d("MusicService", "📩 COMMAND RECEIVED: ${customCommand.customAction}")
            if (customCommand.customAction == "START_SLEEP_TIMER") {
                val duration = args.getLong("DURATION", 0)
                android.util.Log.d("MusicService", "⏳ STARTING SLEEP TIMER: $duration ms")
                sleepTimerManager.startTimer(duration)
                broadcastSleepTimerState(true)
                return Futures.immediateFuture(androidx.media3.session.SessionResult(androidx.media3.session.SessionResult.RESULT_SUCCESS))
            } else if (customCommand.customAction == "STOP_SLEEP_TIMER") {
                sleepTimerManager.cancelTimer()
                broadcastSleepTimerState(false)
                return Futures.immediateFuture(androidx.media3.session.SessionResult(androidx.media3.session.SessionResult.RESULT_SUCCESS))
            } else if (customCommand.customAction == "SET_PREFERRED_AUDIO_DEVICE") {
                val deviceId = args.getInt("DEVICE_ID", -1)
                val audioManager = getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    try {
                        if (deviceId == -1) {
                            android.util.Log.d("MusicService", "🎧 Clearing preferred audio device (system default)")
                            crossfadeManager.activePlayer.setPreferredAudioDevice(null)
                        } else {
                            val devices = audioManager.getDevices(android.media.AudioManager.GET_DEVICES_OUTPUTS)
                            val targetDevice = devices.firstOrNull { it.id == deviceId }
                            if (targetDevice != null) {
                                android.util.Log.d("MusicService", "🎧 Setting preferred audio device to: ${targetDevice.productName} (ID: $deviceId)")
                                crossfadeManager.activePlayer.setPreferredAudioDevice(targetDevice)
                            } else {
                                android.util.Log.w("MusicService", "⚠️ Target device ID $deviceId not found in available outputs!")
                                crossfadeManager.activePlayer.setPreferredAudioDevice(null)
                            }
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("MusicService", "❌ Error setting preferred audio device", e)
                    }
                }
                return Futures.immediateFuture(androidx.media3.session.SessionResult(androidx.media3.session.SessionResult.RESULT_SUCCESS))
            } else if (customCommand.customAction == "SYNC_ROUTING") {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    android.util.Log.d("MusicService", "🎧 SYNC_ROUTING: Resetting to system default")
                    crossfadeManager.activePlayer.setPreferredAudioDevice(null)
                }
                return Futures.immediateFuture(androidx.media3.session.SessionResult(androidx.media3.session.SessionResult.RESULT_SUCCESS))
            }
            return super.onCustomCommand(session, controller, customCommand, args)
        }
    }



    private fun prefetchNextSong() {
        serviceScope.launch {
            try {
                var nextIndex = -1
                var nextItem: MediaItem? = null
                withContext(Dispatchers.Main) {
                    val currentPlayer = crossfadeManager.activePlayer
                    nextIndex = currentPlayer.currentMediaItemIndex + 1
                    if (nextIndex < currentPlayer.mediaItemCount) {
                        nextItem = currentPlayer.getMediaItemAt(nextIndex)
                    }
                }
                
                if (nextItem != null) {
                    val nextResolved = resolveMediaItemUrl(nextItem!!)
                    if (nextResolved != null) {
                        withContext(Dispatchers.Main) {
                            playerA.replaceMediaItem(nextIndex, nextResolved)
                            playerB.replaceMediaItem(nextIndex, nextResolved)
                        }
                    }
                }
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) {
                    Log.e("MusicService", "❌ Error prefetching next URL", e)
                }
            }
        }
    }

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()

        sleepTimerManager = SleepTimerManager(this) {
            // Callback saat timer habis: Pause Musik & Update State
            mediaSession?.player?.pause()
            broadcastSleepTimerState(false)
        }

        // 1. Ambil Cache dari Singleton
        val simpleCache = RemusicCache.getInstance(this)

        // 2. Buat OkHttpClient untuk streaming (tanpa User-Agent hardcoded — akan di-set dinamis)
        val streamingOkHttpClient = OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .build()

        // 3. OkHttpDataSource.Factory — User-Agent & X-Goog-Visitor-Id di-set dinamis per lagu
        httpDataSourceFactory = OkHttpDataSource.Factory(streamingOkHttpClient)
            .setDefaultRequestProperties(mapOf(
                "Origin" to "https://www.youtube.com",
                "Referer" to "https://www.youtube.com/"
            ))

        // 4. Siapkan Default Data Source (Mendukung Content, File, HTTP, dll)
        val defaultDataSourceFactory = androidx.media3.datasource.DefaultDataSource.Factory(this, httpDataSourceFactory)

        // 5. Siapkan Cache Source (Gabungan Cache + Local + Internet)
        val cacheDataSourceFactory = CacheDataSource.Factory()
            .setCache(simpleCache)
            .setUpstreamDataSourceFactory(defaultDataSourceFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()
            
        fun createExoPlayer(): ExoPlayer {
            return ExoPlayer.Builder(this)
                .setAudioAttributes(audioAttributes, true)
                .setHandleAudioBecomingNoisy(true)
                .setWakeMode(C.WAKE_MODE_NETWORK)
                .setMediaSourceFactory(
                    DefaultMediaSourceFactory(cacheDataSourceFactory, androidx.media3.extractor.DefaultExtractorsFactory())
                        .setLoadErrorHandlingPolicy(CustomLoadErrorHandlingPolicy() as LoadErrorHandlingPolicy)
                )
                .build()
        }

        // 4. Pasang ke ExoPlayer
        playerA = createExoPlayer()
        playerB = createExoPlayer()
        
        crossfadeManager = ServiceCrossfadeManager(playerA, playerB, serviceScope)
            
        val database = MusicDatabase.getDatabase(this)
        val repository = MusicRepository(database.musicDao(), database.localPlayHistoryDao())

        val listener = object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                super.onMediaItemTransition(mediaItem, reason)
                if (mediaItem == null) return

                val currentUrl = mediaItem.localConfiguration?.uri?.toString() ?: ""
                val isUrlValid = currentUrl.isNotBlank() && (currentUrl.startsWith("http") || currentUrl.startsWith("content"))
                
                // Jika URL berasal dari local storage "offline_", URL tersebut akan diproses terpisah.
                // Jika tidak valid, kita harus meresolve URI yang kosong / kadaluarsa ini.
                if (!isUrlValid && !mediaItem.mediaId.startsWith("offline_") && reason != Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED) {
                    Log.w("MusicService", "⚠️ URL KOSONG/INVALID di MusicService. Resolving URL dulu untuk ${mediaItem.mediaId}...")
                    val currentPlayer = crossfadeManager.activePlayer
                    // Simpan state wasPlaying sebelum pause
                    val wasPlaying = currentPlayer.playWhenReady
                    // Pause player sementara agar tidak error saat mencoba memutar url kosong
                    currentPlayer.pause()

                    serviceScope.launch {
                        try {
                            val resolvedItem = resolveMediaItemUrl(mediaItem)
                            
                            if (resolvedItem != null) {
                                withContext(Dispatchers.Main) {
                                    val currentActive = crossfadeManager.activePlayer
                                    val newIndex = currentActive.currentMediaItemIndex
                                    val resumePos = currentActive.currentPosition.coerceAtLeast(0L)
                                    
                                    currentActive.replaceMediaItem(newIndex, resolvedItem)
                                    if (resumePos > 500L) {
                                        currentActive.seekTo(newIndex, resumePos)
                                    }
                                    if (wasPlaying) {
                                        currentActive.play()
                                    }
                                }
                            } else {
                                Log.e("MusicService", "❌ Gagal resolve URL. Memberhentikan pemutaran.")
                                withContext(Dispatchers.Main) {
                                    crossfadeManager.activePlayer.pause()
                                }
                            }
                        } catch (e: Exception) {
                            if (e !is kotlinx.coroutines.CancellationException) {
                                Log.e("MusicService", "❌ Error Resolving URL", e)
                            }
                            withContext(Dispatchers.Main) {
                                crossfadeManager.activePlayer.pause()
                            }
                        }
                    }
                }
                
                prefetchNextSong()
            }

            override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
                super.onTimelineChanged(timeline, reason)
                if (reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) {
                    prefetchNextSong()
                }
            }
        }
        
        playerA.addListener(listener)
        playerB.addListener(listener)

        // Create an intent to launch MainActivity
        val sessionActivityIntent = Intent(this, com.example.remusic.MainActivity::class.java).apply {
            putExtra("destination_route", "playmusic")
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = android.app.PendingIntent.getActivity(
            this,
            0,
            sessionActivityIntent,
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
        )

        mediaSession = MediaLibrarySession.Builder(this, playerA, mediaSessionCallback)
            .setSessionActivity(pendingIntent)
            .build()
            
        crossfadeManager.attachSession(mediaSession!!)
        
        // Loop polling untuk check crossfade 
        serviceScope.launch {
            val userPrefs = com.example.remusic.data.preferences.UserPreferencesRepository(this@MusicService)
            while (isActive) {
                val isCrossfadeEnabled = userPrefs.crossfadeEnabledFlow.first()
                if (isCrossfadeEnabled) {
                    val durationSec = userPrefs.crossfadeDurationFlow.first()
                    val curve = userPrefs.crossfadeCurveFlow.first()
                    withContext(Dispatchers.Main) {
                        crossfadeManager.checkAndTriggerCrossfade(durationSec * 1000L, curve)
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        crossfadeManager.cancelCrossfade()
                    }
                }
                delay(500)
            }
        }
    }
    
    private suspend fun resolveMediaItemUrl(mediaItem: MediaItem): MediaItem? {
        val currentUrl = mediaItem.localConfiguration?.uri?.toString() ?: ""
        val isUrlValid = currentUrl.isNotBlank() && (currentUrl.startsWith("http") || currentUrl.startsWith("content"))
        if (isUrlValid || mediaItem.mediaId.startsWith("offline_")) {
            return null // No need to resolve
        }

        val songId = mediaItem.mediaId
        val songTitle = mediaItem.mediaMetadata.title?.toString() ?: "Unknown"
        val artistName = mediaItem.mediaMetadata.artist?.toString() ?: "Unknown"

        val extras = mediaItem.mediaMetadata.extras
        val effectiveId = extras?.getString("effectiveId") ?: songId
        val sourceFromExtras = extras?.getString("source")

        // 1. Cek Cache Lokal dari Download
        val localUri = com.example.remusic.utils.SongDownloader.getLocalFileUri(
            this@MusicService, 
            songTitle, 
            artistName
        )
        if (localUri != null) {
            Log.d("MusicService", "✅ LOCAL CACHE FOUND for $songId: $localUri")
            return mediaItem.buildUpon().setUri(localUri).build()
        }

        // 2. Resolve via Repository (Database & API)
        val database = MusicDatabase.getDatabase(this)
        val localSong = database.musicDao().getSongById(songId)
        val artistId = localSong?.artistId
        val telegramFileId = localSong?.telegramFileId
        val fallbackUrl = localSong?.telegramDirectUrl
        val source = sourceFromExtras ?: localSong?.source

        if (com.metrolist.innertube.YouTube.visitorData == null) {
            try {
                val vd = com.metrolist.innertube.YouTube.visitorData().getOrNull()
                if (vd != null) com.metrolist.innertube.YouTube.visitorData = vd
            } catch (_: Exception) {}
        }
        
        val repository = MusicRepository(database.musicDao(), database.localPlayHistoryDao())
        val result = repository.getPlayableUrl(
            songId = effectiveId,
            title = songTitle,
            telegramFileId = telegramFileId,
            fallbackUrl = fallbackUrl,
            artistId = artistId,
            source = source
        )
        
        val newUrl = result.url
        if (newUrl.isNotBlank()) {
            Log.d("MusicService", "✅ URL Resolved: $newUrl (source=${result.source})")
            if (result.source.startsWith("YOUTUBE")) {
                val userAgent = when {
                    newUrl.contains("&c=IOS") || newUrl.contains("c=IOS&") ->
                        "com.google.ios.youtube/21.03.1 (iPhone16,2; U; CPU iOS 18_2 like Mac OS X;)"
                    newUrl.contains("&c=ANDROID_VR") ->
                        "com.google.android.apps.youtube.vr.oculus/1.43.32 (Linux; U; Android 12; en_US; Quest 3; Build/SQ3A.220605.009.A1; Cronet/107.0.5284.2)"
                    newUrl.contains("&c=ANDROID") ->
                        "com.google.android.youtube/21.03.38 (Linux; U; Android 14) gzip"
                    else -> "Mozilla/5.0"
                }
                val headers = mutableMapOf(
                    "Origin" to "https://www.youtube.com",
                    "Referer" to "https://www.youtube.com/"
                )
                com.metrolist.innertube.YouTube.visitorData?.let {
                    headers["X-Goog-Visitor-Id"] = it
                }
                httpDataSourceFactory.setUserAgent(userAgent)
                httpDataSourceFactory.setDefaultRequestProperties(headers)
            }
            return mediaItem.buildUpon().setUri(newUrl).build()
        }
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "ACTION_STOP_SLEEP_TIMER") {
            sleepTimerManager.cancelTimer()
            broadcastSleepTimerState(false)
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? {
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val userPrefs = com.example.remusic.data.preferences.UserPreferencesRepository(this)
        var isBackgroundEnabled = false
        runBlocking {
            isBackgroundEnabled = userPrefs.backgroundMusicEnabledFlow.first()
        }

        if (!isBackgroundEnabled) {
            mediaSession?.player?.pause()
            sleepTimerManager.cancelTimer()
            // 1. Lepas dari Foreground seketika agar notifikasi aktif (drawer) langsung hilang.
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
        }
    }

    private fun broadcastSleepTimerState(isActive: Boolean) {
        val session = mediaSession ?: return
        val newExtras = session.sessionExtras.deepCopy().apply {
            putBoolean("IS_SLEEP_TIMER_ACTIVE", isActive)
            putLong("SLEEP_TIMER_END_TIME", if(isActive) sleepTimerManager.endTimeMs else 0L)
        }
        session.sessionExtras = newExtras
    }

    override fun onDestroy() {
        mediaSession?.run {
            playerA.release()
            playerB.release()
            release()
            mediaSession = null
        }
        sleepTimerManager.cancelTimer()
        serviceScope.cancel()
        super.onDestroy()
    }
}