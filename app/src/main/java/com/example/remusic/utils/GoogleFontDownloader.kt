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

    // Google Fonts CSS API v2 — mengambil URL file TTF dari CSS yang direturn
    private const val GOOGLE_FONTS_CSS_BASE =
        "https://fonts.googleapis.com/css2?family=%s&display=swap"

    // Fallback: langsung ke file TTF dari GitHub Google Fonts mirror
    // Format: https://github.com/google/fonts/raw/main/ofl/<lowercaseName>/<FileName>-Regular.ttf
    // Kita gunakan fonts.gstatic.com yang lebih andal
    private const val GSTATIC_BASE =
        "https://fonts.gstatic.com/s/%s/v%s/%s.ttf"

    /**
     * Mapping dari googleFontName ke URL langsung file TTF Regular.
     * URL ini stabil dan merupakan CDN Google (gstatic).
     */
    private val FONT_TTF_URLS = mapOf(
        "Nunito" to "https://fonts.gstatic.com/s/nunito/v26/XRXI3I6Li01BKofiOc5wtlZ2di8HDFwmdTo3jQ.ttf",
        "Lato" to "https://fonts.gstatic.com/s/lato/v24/S6uyw4BMUTPHjx4wXg.ttf",
        "Montserrat" to "https://fonts.gstatic.com/s/montserrat/v29/JTUHjIg1_i6t8kCHKm4532VJOt5-QNFgpCtr6Hw9aXp-p7K4KLjztg.ttf",
        "Inter" to "https://fonts.gstatic.com/s/inter/v18/UcCO3FwrK3iLTeHuS_nVMrMxCp50SjIw2boKoduKmMEVuLyfAZ9hiJ-Ek-_EeA.ttf",
        "Playfair_Display" to "https://fonts.gstatic.com/s/playfairdisplay/v37/nuFvD-vYSZviVYUb_rj3ij__anPXJzDwcbmjWBN2PKdFvUDQ5Zgo_g.ttf",
        "Raleway" to "https://fonts.gstatic.com/s/raleway/v34/1Ptug8zYS_SKggPNyC0IT4ttDfA.ttf",
        "Josefin_Sans" to "https://fonts.gstatic.com/s/josefinsans/v32/Qw3PZQNVED7rKGKxtqIqX5E-AVSJrOCfjY46_GbQXMFrLgTsQV0.ttf",
        "Pacifico" to "https://fonts.gstatic.com/s/pacifico/v22/FwZY7-Qmy14u9lezJ96A4sijpFu_.ttf",
    )

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

            val url = FONT_TTF_URLS[googleFontName]
            if (url == null) {
                Log.e(TAG, "No URL configured for font '$googleFontName'")
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
