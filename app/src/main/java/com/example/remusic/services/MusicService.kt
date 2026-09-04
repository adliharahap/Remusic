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
import kotlinx.coroutines.withContext
import android.util.Log
import com.example.remusic.data.local.MusicDatabase
import com.example.remusic.data.repository.MusicRepository
import androidx.media3.common.Player

@UnstableApi
class MusicService : MediaLibraryService() {
    private var mediaSession: MediaLibrarySession? = null
    private lateinit var player: ExoPlayer
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
                            player.setPreferredAudioDevice(null)
                        } else {
                            val devices = audioManager.getDevices(android.media.AudioManager.GET_DEVICES_OUTPUTS)
                            val targetDevice = devices.firstOrNull { it.id == deviceId }
                            if (targetDevice != null) {
                                android.util.Log.d("MusicService", "🎧 Setting preferred audio device to: ${targetDevice.productName} (ID: $deviceId)")
                                player.setPreferredAudioDevice(targetDevice)
                            } else {
                                android.util.Log.w("MusicService", "⚠️ Target device ID $deviceId not found in available outputs!")
                                player.setPreferredAudioDevice(null)
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
                    player.setPreferredAudioDevice(null)
                }
                return Futures.immediateFuture(androidx.media3.session.SessionResult(androidx.media3.session.SessionResult.RESULT_SUCCESS))
            }
            return super.onCustomCommand(session, controller, customCommand, args)
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
        val httpDataSourceFactory = OkHttpDataSource.Factory(streamingOkHttpClient)
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

        // 4. Pasang ke ExoPlayer
        player = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(cacheDataSourceFactory, androidx.media3.extractor.DefaultExtractorsFactory())
                    .setLoadErrorHandlingPolicy(CustomLoadErrorHandlingPolicy() as LoadErrorHandlingPolicy)
            )
            .build()
            
        val database = MusicDatabase.getDatabase(this)
        val repository = MusicRepository(database.musicDao(), database.localPlayHistoryDao())

        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                super.onMediaItemTransition(mediaItem, reason)
                if (mediaItem == null) return

                // ── GUARD: Jangan re-resolve saat PLAYLIST_CHANGED ──────────────
                // PLAYLIST_CHANGED terjadi karena replaceMediaItem() — sudah ada URL baru,
                // tidak perlu resolve lagi (akan menyebabkan loop tak terbatas)
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED) return

                val currentUrl = mediaItem.localConfiguration?.uri?.toString() ?: ""
                val isUrlValid = currentUrl.isNotBlank() && (currentUrl.startsWith("http") || currentUrl.startsWith("content"))
                
                // Jika URL berasal dari local storage "offline_", URL tersebut akan diproses terpisah.
                // Jika tidak valid, kita harus meresolve URI yang kosong / kadaluarsa ini.
                if (!isUrlValid && !mediaItem.mediaId.startsWith("offline_")) {
                    Log.w("MusicService", "⚠️ URL KOSONG/INVALID di MusicService. Resolving URL dulu untuk ${mediaItem.mediaId}...")
                    // Simpan state wasPlaying sebelum pause
                    val wasPlaying = player.playWhenReady
                    // Pause player sementara agar tidak error saat mencoba memutar url kosong
                    player.pause()

                    serviceScope.launch {
                        try {
                            val songId = mediaItem.mediaId
                            val songTitle = mediaItem.mediaMetadata.title?.toString() ?: "Unknown"
                            val artistName = mediaItem.mediaMetadata.artist?.toString() ?: "Unknown"

                            val extras = mediaItem.mediaMetadata.extras
                            val effectiveId = extras?.getString("effectiveId") ?: songId
                            val sourceFromExtras = extras?.getString("source")

                            // 1. Cek Cache Lokal dari Download DULU (Bypass API sepenuhnya)
                            val localUri = com.example.remusic.utils.SongDownloader.getLocalFileUri(
                                this@MusicService, 
                                songTitle, 
                                artistName
                            )
                            
                            if (localUri != null) {
                                Log.d("MusicService", "✅ LOCAL CACHE FOUND for $songId: $localUri")
                                val newItem = mediaItem.buildUpon()
                                    .setUri(localUri)
                                    .build()

                                withContext(Dispatchers.Main) {
                                    player.replaceMediaItem(player.currentMediaItemIndex, newItem)
                                    player.prepare()
                                    if (wasPlaying) player.play()
                                }
                                return@launch
                            }

                            // 2. Ambil detail song dari local DB (Jika tidak di-download)
                            val localSong = database.musicDao().getSongById(songId)
                            val artistId = localSong?.artistId
                            val telegramFileId = localSong?.telegramFileId
                            val fallbackUrl = localSong?.telegramDirectUrl
                            val source = sourceFromExtras ?: localSong?.source

                            // Pastikan visitorData YouTube sudah siap sebelum resolve
                            if (com.metrolist.innertube.YouTube.visitorData == null) {
                                try {
                                    val vd = com.metrolist.innertube.YouTube.visitorData().getOrNull()
                                    if (vd != null) com.metrolist.innertube.YouTube.visitorData = vd
                                } catch (_: Exception) {}
                            }
                            
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

                                // Set User-Agent dan header YouTube sesuai client jika sumber YouTube
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

                                // Rebuild MediaItem dengan URL yang valid
                                val newItem = mediaItem.buildUpon()
                                    .setUri(newUrl)
                                    .build()
                                
                                withContext(Dispatchers.Main) {
                                    val newIndex = player.currentMediaItemIndex
                                    // Simpan posisi sekarang — mungkin sudah ada buffering
                                    // sedikit tapi kalau masih 0, mulai dari awal
                                    val resumePos = player.currentPosition.coerceAtLeast(0L)
                                    
                                    player.replaceMediaItem(newIndex, newItem)
                                    // Jika sudah ada posisi bermakna, lanjutkan dari sana
                                    // Jika belum (auto-next baru saja terjadi), mulai dari 0
                                    if (resumePos > 500L) {
                                        player.seekTo(newIndex, resumePos)
                                    }
                                    
                                    if (wasPlaying) {
                                        player.play()
                                    }
                                }
                            } else {
                                Log.e("MusicService", "❌ Gagal resolve URL. Memberhentikan pemutaran.")
                                withContext(Dispatchers.Main) {
                                    // Jangan skip otomatis karena bisa menyebabkan infinite loop jika offline
                                    player.pause()
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("MusicService", "❌ Error resolving URL in Service: ${e.message}")
                            withContext(Dispatchers.Main) {
                                player.pause()
                            }
                        }
                    }
                }
            }
        })


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

        mediaSession = MediaLibrarySession.Builder(this, player, mediaSessionCallback)
            .setSessionActivity(pendingIntent)
            .build()
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
            player.release()
            release()
            mediaSession = null
        }
        sleepTimerManager.cancelTimer()
        serviceScope.cancel()
        super.onDestroy()
    }
}