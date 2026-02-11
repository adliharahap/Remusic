package com.example.remusic.services

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import java.io.IOException

/**
 * Custom Load Error Handling Policy untuk ExoPlayer
 * 
 * Tujuan: Agar ExoPlayer tidak mudah menyerah saat ada masalah jaringan.
 * - Network errors (IOException) akan di-retry unlimited dengan exponential backoff
 * - Non-network errors tetap menggunakan default behavior (stop after 3 retries)
 */
@OptIn(UnstableApi::class)
class CustomLoadErrorHandlingPolicy : DefaultLoadErrorHandlingPolicy() {

    override fun getRetryDelayMsFor(loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo): Long {
        val exception = loadErrorInfo.exception
        
        // PENTING: MalformedURLException = URL formatnya salah (bukan masalah network)
        // Jangan retry unlimited kalau URL-nya memang rusak/kosong
        if (exception is java.net.MalformedURLException) {
            android.util.Log.e(
                "CustomLoadErrorPolicy",
                "❌ URL Format Invalid: ${exception.message}. STOP retry (bukan network issue)."
            )
            // Return C.TIME_UNSET agar ExoPlayer STOP retry
            return androidx.media3.common.C.TIME_UNSET
        }
        
        // Cek apakah ini adalah network error (IOException atau subclass-nya, kecuali MalformedURLException)
        if (exception is IOException) {
            // Network errors: UnknownHostException, SocketException, HttpDataSourceException, dll
            // Exponential backoff: 1s -> 2s -> 4s -> 8s -> max 10s
            val baseDelayMs = 1000L
            val attempt = loadErrorInfo.errorCount
            
            // Formula: baseDelay * 2^attempt, dengan max 10 detik
            // attempt 0: 1s, attempt 1: 2s, attempt 2: 4s, attempt 3: 8s, attempt 4+: 10s
            val calculatedDelay = baseDelayMs * (1 shl attempt.coerceAtMost(4))
            val finalDelay = calculatedDelay.coerceAtMost(10000L)
            
            android.util.Log.d(
                "CustomLoadErrorPolicy",
                "🔄 Network error detected (attempt ${attempt + 1}). Retrying in ${finalDelay}ms. Error: ${exception.message}"
            )
            
            return finalDelay
        }
        
        // Untuk error non-network (format error, decoder error, dll), gunakan default
        android.util.Log.w(
            "CustomLoadErrorPolicy",
            "❌ Non-network error detected: ${exception::class.simpleName}. Using default retry policy."
        )
        return super.getRetryDelayMsFor(loadErrorInfo)
    }

    override fun getMinimumLoadableRetryCount(dataType: Int): Int {
        // Retry unlimited untuk semua data types (audio, video, dll)
        // ExoPlayer akan terus retry selama getRetryDelayMsFor() tidak return C.TIME_UNSET
        return Int.MAX_VALUE
    }
}
