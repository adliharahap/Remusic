package com.example.remusic.services

import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.example.remusic.utils.RemusicCache // Import file utils tadi
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

@UnstableApi
class MusicService : MediaSessionService() {
    private var mediaSession: MediaSession? = null

    private lateinit var sleepTimerManager: SleepTimerManager

    // Callback session tetap sama...
    private val mediaSessionCallback = object : MediaSession.Callback {
        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            val player = mediaSession.player
            val mediaItems = mutableListOf<MediaItem>()
            for (i in 0 until player.mediaItemCount) {
                mediaItems.add(player.getMediaItemAt(i))
            }
            val startPosition = MediaSession.MediaItemsWithStartPosition(
                mediaItems,
                player.currentMediaItemIndex,
                player.currentPosition
            )
            return Futures.immediateFuture(startPosition)
        }

        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                .add(androidx.media3.session.SessionCommand("START_SLEEP_TIMER", android.os.Bundle.EMPTY))
                .add(androidx.media3.session.SessionCommand("STOP_SLEEP_TIMER", android.os.Bundle.EMPTY))
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

        // 2. Siapkan HTTP Source (Internet)
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)

        // 3. Siapkan Cache Source (Gabungan Cache + Internet)
        val cacheDataSourceFactory = CacheDataSource.Factory()
            .setCache(simpleCache)
            .setUpstreamDataSourceFactory(httpDataSourceFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

        // 4. Pasang ke ExoPlayer
        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(AudioAttributes.DEFAULT, true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(this)
                    .setDataSourceFactory(cacheDataSourceFactory) // <--- POWER OF CACHE
            )
            .build()

        mediaSession = MediaSession.Builder(this, player)
            .setCallback(mediaSessionCallback)
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "ACTION_STOP_SLEEP_TIMER") {
            sleepTimerManager.cancelTimer()
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        mediaSession?.player?.stop()
        mediaSession?.player?.release()
        sleepTimerManager.cancelTimer()

        // Jangan release cache di sini jika kamu pakai Singleton,
        // biarkan dia hidup selama Application hidup.
        // Kecuali kamu yakin app benar-benar mati.

        stopSelf()
    }

    private fun broadcastSleepTimerState(isActive: Boolean) {
        val session = mediaSession ?: return
        val newExtras = session.sessionExtras.deepCopy().apply {
            putBoolean("IS_SLEEP_TIMER_ACTIVE", isActive)
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
        super.onDestroy()
    }
}