package com.example.remusic.utils

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File

@OptIn(UnstableApi::class)
object RemusicCache {
    private var simpleCache: SimpleCache? = null

    // Ubah ke 2GB biar puas (Offline Mode maksimal)
    private const val MAX_CACHE_SIZE: Long = 2L * 1024 * 1024 * 1024

    fun getInstance(context: Context): SimpleCache {
        // Double-check locking supaya thread-safe
        return simpleCache ?: synchronized(this) {
            simpleCache ?: run {
                val cacheFolder = File(context.cacheDir, "media_cache")
                val evictor = LeastRecentlyUsedCacheEvictor(MAX_CACHE_SIZE)
                val databaseProvider = StandaloneDatabaseProvider(context)

                SimpleCache(cacheFolder, evictor, databaseProvider).also {
                    simpleCache = it
                }
            }
        }
    }

    // Fungsi untuk bersih-bersih kalau app mau mati total
    fun release() {
        try {
            simpleCache?.release()
            simpleCache = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}