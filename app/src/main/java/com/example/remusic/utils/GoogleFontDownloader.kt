package com.example.remusic.utils

import android.content.Context
import android.graphics.Typeface
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

/**
 * Utility untuk mengunduh dan meng-cache font dari Google Fonts API.
 * Font disimpan di [Context.filesDir]/fonts/<fontName>.ttf
 * sehingga tersedia secara offline setelah pertama kali diunduh.
 */
object GoogleFontDownloader {

    private const val TAG = "GoogleFontDownloader"
    private const val FONTS_DIR = "fonts"

    // Google Fonts CSS API v1 — mengambil URL file TTF dari CSS yang direturn
    // Kita gunakan v1 dengan User-Agent jadul agar mendapatkan link .ttf (bukan .woff2)
    private const val GOOGLE_FONTS_CSS_BASE =
        "https://fonts.googleapis.com/css?family=%s"

    // Fallback: langsung ke file TTF dari GitHub Google Fonts mirror
    private const val GSTATIC_BASE =
        "https://fonts.gstatic.com/s/%s/v%s/%s.ttf"

    /**
     * Mengambil URL langsung file TTF.
     * Menggunakan API GWFH (Google Webfonts Helper) yang lebih andal memberikan link .ttf langsung.
     */
    private suspend fun resolveTtfUrl(googleFontName: String): String? = withContext(Dispatchers.IO) {
        try {
            // GWFH menggunakan format lowercase dengan tanda hubung (e.g., 'fjalla-one')
            val fontId = googleFontName.lowercase().replace("_", "-")
            val apiUrl = "https://gwfh.mranftl.com/api/fonts/$fontId"
            
            Log.d(TAG, "Resolving TTF URL via GWFH API: $apiUrl")
            
            val request = Request.Builder().url(apiUrl).build()
            val response = httpClient.newCall(request).execute()
            
            if (response.isSuccessful) {
                val json = response.body?.string() ?: ""
                
                // Cari variant 'regular' lalu ambil link 'ttf'
                val regularMatch = """(?s)"id"\s*:\s*"regular".*?"ttf"\s*:\s*"([^"]+)"""".toRegex().find(json)
                var ttfUrl = regularMatch?.groupValues?.get(1)
                
                // Jika tidak ada 'regular', ambil link ttf pertama yang ketemu
                if (ttfUrl == null) {
                    ttfUrl = """ "ttf"\s*:\s*"([^"]+)"""".toRegex().find(json)?.groupValues?.get(1)
                }
                
                if (ttfUrl != null) {
                    Log.d(TAG, "Resolved TTF URL from GWFH: $ttfUrl")
                    return@withContext ttfUrl
                }
            }
            
            // Jika GWFH gagal, gunakan fallback ke CSS API
            Log.w(TAG, "GWFH failed, falling back to CSS API for $googleFontName")
            resolveTtfUrlFallback(googleFontName)
        } catch (e: Exception) {
            Log.e(TAG, "Error in resolveTtfUrl: ${e.message}")
            resolveTtfUrlFallback(googleFontName)
        }
    }

    private suspend fun resolveTtfUrlFallback(googleFontName: String): String? = withContext(Dispatchers.IO) {
        try {
            val apiName = googleFontName.replace("_", "+")
            val cssUrl = "https://fonts.googleapis.com/css?family=$apiName"
            
            val request = Request.Builder()
                .url(cssUrl)
                .header("User-Agent", "Mozilla/5.0 (compatible; MSIE 9.0; Windows NT 6.1; Trident/5.0)")
                .build()
            
            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) return@withContext null
            
            val cssContent = response.body?.string() ?: ""
            val regex = """url\(['"]?(https://[^'"]+?\.ttf[^'"]*?)['"]?\)""".toRegex()
            val match = regex.find(cssContent)
            
            return@withContext match?.groupValues?.get(1)
        } catch (e: Exception) {
            null
        }
    }

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    /** Folder penyimpanan font di internal storage APK */
    private fun fontsDir(context: Context): File {
        return File(context.filesDir, FONTS_DIR).also { it.mkdirs() }
    }

    /** File path untuk font yang di-cache */
    private fun fontFile(context: Context, googleFontName: String): File {
        return File(fontsDir(context), "$googleFontName.ttf")
    }

    /**
     * Cek apakah font sudah tersimpan di cache lokal.
     * @param googleFontName Nama font sesuai field `googleFontName` di enum (e.g., "Nunito")
     */
    fun isFontCached(context: Context, googleFontName: String): Boolean {
        val file = fontFile(context, googleFontName)
        return file.exists() && file.length() > 0
    }

    /**
     * Download font dari Google Fonts dan simpan ke cache lokal.
     * @return `true` jika berhasil, `false` jika gagal
     */
    suspend fun downloadFont(context: Context, googleFontName: String): Boolean =
        withContext(Dispatchers.IO) {
            val outputFile = fontFile(context, googleFontName)

            // Sudah ada di cache, skip download
            if (outputFile.exists() && outputFile.length() > 0) {
                Log.d(TAG, "Font '$googleFontName' already cached at ${outputFile.absolutePath}")
                return@withContext true
            }

            // --- DYNAMIC RESOLUTION ---
            val url = resolveTtfUrl(googleFontName)
            if (url == null) {
                Log.e(TAG, "Could not resolve TTF URL for font '$googleFontName'")
                return@withContext false
            }

            return@withContext try {
                Log.d(TAG, "Downloading font '$googleFontName' from: $url")
                val request = Request.Builder().url(url).build()
                val response = httpClient.newCall(request).execute()

                if (!response.isSuccessful) {
                    Log.e(TAG, "Download failed for '$googleFontName': HTTP ${response.code}")
                    return@withContext false
                }

                val body = response.body
                if (body == null) {
                    Log.e(TAG, "Empty response body for '$googleFontName'")
                    return@withContext false
                }

                // Tulis ke file sementara dulu, baru rename (atomic write)
                val tempFile = File(fontsDir(context), "${googleFontName}_temp.ttf")
                FileOutputStream(tempFile).use { fos ->
                    body.byteStream().copyTo(fos)
                }

                // Validasi: file TTF minimal harus ada beberapa KB
                if (tempFile.length() < 1024) {
                    Log.e(TAG, "Downloaded file too small for '$googleFontName': ${tempFile.length()} bytes")
                    tempFile.delete()
                    return@withContext false
                }

                // Rename ke file final
                tempFile.renameTo(outputFile)
                Log.d(TAG, "Font '$googleFontName' saved to: ${outputFile.absolutePath} (${outputFile.length()} bytes)")
                true
            } catch (e: Exception) {
                Log.e(TAG, "Exception downloading font '$googleFontName': ${e.message}", e)
                // Bersihkan file yang gagal
                fontFile(context, googleFontName).delete()
                false
            }
        }

    /**
     * Load font yang sudah di-cache sebagai [Typeface].
     * @return [Typeface] jika font ada di cache, `null` jika belum diunduh
     */
    fun loadCachedTypeface(context: Context, googleFontName: String): Typeface? {
        val file = fontFile(context, googleFontName)
        if (!file.exists() || file.length() == 0L) return null

        return try {
            Typeface.createFromFile(file)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load typeface from file for '$googleFontName': ${e.message}", e)
            null
        }
    }

    /**
     * Hapus font dari cache (untuk keperluan debug / reset).
     */
    fun deleteCachedFont(context: Context, googleFontName: String) {
        fontFile(context, googleFontName).delete()
    }
}
