package com.example.remusic.services

import android.content.Intent
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

@UnstableApi
class MusicService : MediaSessionService() {
    private var mediaSession: MediaSession? = null

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
    }

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this).build()

        mediaSession = MediaSession.Builder(this, player)
            .setCallback(mediaSessionCallback)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    // Dipanggil ketika user swipe app dari recent
    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)

        // Hentikan playback
        mediaSession?.player?.stop()
        mediaSession?.player?.release()

        // Hentikan service
        stopSelf()
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}
