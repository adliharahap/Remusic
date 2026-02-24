package com.example.remusic.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.imageLoader
import com.example.remusic.data.local.MusicDatabase
import com.example.remusic.utils.RemusicCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.ln
import kotlin.math.pow

class StorageCacheViewModel : ViewModel() {

    private val _audioCacheSizeMB = MutableStateFlow(0f)
    val audioCacheSizeMB: StateFlow<Float> = _audioCacheSizeMB.asStateFlow()

    private val _dataCacheSizeMB = MutableStateFlow(0f)
    val dataCacheSizeMB: StateFlow<Float> = _dataCacheSizeMB.asStateFlow()

    private val _totalCacheSizeMB = MutableStateFlow(0f)
    val totalCacheSizeMB: StateFlow<Float> = _totalCacheSizeMB.asStateFlow()

    fun loadCacheSizes(context: Context) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                // Audio Cache (ExoPlayer media_cache)
                val audioCacheDir = File(context.cacheDir, "media_cache")
                val audioSizeBytes = getFolderSize(audioCacheDir)
                val audioMB = audioSizeBytes / (1024f * 1024f)
                _audioCacheSizeMB.value = audioMB

                // Data Cache (Coil image_cache + Room DB)
                val imageCacheDir = File(context.cacheDir, "image_cache")
                val imageSizeBytes = getFolderSize(imageCacheDir)

                val dbFile = context.getDatabasePath("music_database")
                val dbWalFile = context.getDatabasePath("music_database-wal")
                val dbShmFile = context.getDatabasePath("music_database-shm")

                var dbSizeBytes = 0L
                if (dbFile.exists()) dbSizeBytes += dbFile.length()
                if (dbWalFile.exists()) dbSizeBytes += dbWalFile.length()
                if (dbShmFile.exists()) dbSizeBytes += dbShmFile.length()

                val dataMB = (imageSizeBytes + dbSizeBytes) / (1024f * 1024f)
                _dataCacheSizeMB.value = dataMB

                _totalCacheSizeMB.value = audioMB + dataMB
            }
        }
    }

    fun clearAudioCache(context: Context) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                // Lepas instance cache ExoPlayer
                RemusicCache.release()
                
                // Hapus folder media_cache secara fisik
                val audioCacheDir = File(context.cacheDir, "media_cache")
                if (audioCacheDir.exists()) {
                    audioCacheDir.deleteRecursively()
                }

                // Hitung ulang size
                loadCacheSizes(context)
            }
        }
    }

    fun clearDataCache(context: Context) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                // 1. Bersihkan tabel database (Tanpa menyentuh SharedPreferences / session)
                try {
                    val db = MusicDatabase.getDatabase(context)
                    db.clearAllTables()
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                // 2. Bersihkan disk cache Coil
                try {
                    context.imageLoader.diskCache?.clear()
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                // Opsional: Hapus manual folder image_cache jika Coil tidak menghafus semuanya
                val imageCacheDir = File(context.cacheDir, "image_cache")
                if (imageCacheDir.exists()) {
                    imageCacheDir.deleteRecursively()
                }

                // Hitung ulang size
                loadCacheSizes(context)
            }
        }
    }

    private fun getFolderSize(file: File): Long {
        var size: Long = 0
        if (file.isDirectory) {
            val children = file.listFiles()
            if (children != null) {
                for (child in children) {
                    size += getFolderSize(child)
                }
            }
        } else {
            size = file.length()
        }
        return size
    }

    // Helper formatter for the UI to display MB or GB
    companion object {
        fun formatSize(sizeInMB: Float): String {
            if (sizeInMB <= 0f) return "0 MB"
            
            return if (sizeInMB >= 1024f) {
                val sizeInGB = sizeInMB / 1024f
                String.format("%.2f GB", sizeInGB)
            } else {
                String.format("%.1f MB", sizeInMB)
            }
        }
    }
}
