package com.example.remusic.services

import android.content.Context
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import java.io.File

@UnstableApi
class MusicService : MediaSessionService() {
    private var mediaSession: MediaSession? = null

    // --- INI ADALAH LOGIKA CACHE (BARU DITAMBAHKAN) ---
    companion object {
        @Volatile
        private var simpleCache: SimpleCache? = null
        private const val cacheSize: Long = 100 * 1024 * 1024 // 100MB
    }
    // --------------------------------------------------

    // --- FUNGSI HELPER CACHE (BARU DITAMBAHKAN) ---
    @OptIn(UnstableApi::class)
    private fun getCache(context: Context): SimpleCache {
        // Gunakan 'synchronized' untuk mencegah 'race condition'
        return simpleCache ?: synchronized(this) {
            simpleCache ?: run {
                val cacheFolder = File(context.cacheDir, "media")
                val databaseProvider = StandaloneDatabaseProvider(context)
                SimpleCache(
                    cacheFolder,
                    LeastRecentlyUsedCacheEvictor(cacheSize), // Aturan: Hapus yang terlama jika penuh
                    databaseProvider
                ).also {
                    simpleCache = it
                }
            }
        }
    }
    // --------------------------------------------------


    // --- Callback milik Anda, tidak diubah ---
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

    @OptIn(UnstableApi::class) // Ditambahkan @OptIn di sini
    override fun onCreate() {
        super.onCreate()

        // --- INI BAGIAN YANG DIUBAH ---
        // Kode lama Anda: val player = ExoPlayer.Builder(this).build()

        // Kode BARU dengan Caching:
        val context: Context = this
        val cache = getCache(context)

        // Buat "Pabrik" (Factory) yang bisa membaca/menulis ke cache
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
        val cacheDataSourceFactory = CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(httpDataSourceFactory) // Jika tdk ada di cache, ambil dari internet
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR) // Jika ada error di cache, ambil dari internet

        // Bangun ExoPlayer dan beri tahu untuk menggunakan "pabrik" cache kita
        val player = ExoPlayer.Builder(context)
            .setAudioAttributes(AudioAttributes.DEFAULT, true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .setMediaSourceFactory( // <-- INI KUNCINYA
                DefaultMediaSourceFactory(context)
                    .setDataSourceFactory(cacheDataSourceFactory)
            )
            .build()
        // --- AKHIR BAGIAN YANG DIUBAH ---

        // Kode Anda selanjutnya, tidak diubah
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

        // (Opsional tapi direkomendasikan) Bersihkan cache saat aplikasi ditutup
        try {
            simpleCache?.release()
            simpleCache = null
        } catch (e: Exception) {
            e.printStackTrace()
        }

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

