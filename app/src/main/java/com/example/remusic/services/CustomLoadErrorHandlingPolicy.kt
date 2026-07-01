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
 * - 4xx HTTP errors (404, 403, 410): STOP retry segera → trigger onPlayerError → app re-resolve URL
 * - Network errors (IOException) akan di-retry dengan exponential backoff cepat
 * - Non-network errors tetap menggunakan default behavior
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
            return androidx.media3.common.C.TIME_UNSET
        }
        
        // KRITIS: Deteksi 4xx HTTP errors (404, 403, 410, dll)
        // Ini artinya URL sudah EXPIRED atau TIDAK VALID — retrying dengan URL yang sama sia-sia!
        // Return C.TIME_UNSET agar ExoPlayer STOP retry dan throw PlaybackException
        // sehingga onPlayerError di ViewModel bisa re-resolve URL fresh.
        if (exception is androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException) {
            val responseCode = exception.responseCode
            if (responseCode in 400..499) {
                android.util.Log.e(
                    "CustomLoadErrorPolicy",
                    "❌ HTTP $responseCode: URL expired/invalid. STOP retry — app harus re-resolve URL!"
                )
                return androidx.media3.common.C.TIME_UNSET
            }
        }
        // Cek apakah ini adalah network error (IOException atau subclass-nya, kecuali yang sudah ditangani di atas)
        if (exception is IOException) {
            // Network errors: UnknownHostException, SocketException, dll
            // Exponential backoff cepat: 500ms -> 1s -> 2s -> 4s -> max 5s
            val baseDelayMs = 500L
            val attempt = loadErrorInfo.errorCount
            val calculatedDelay = baseDelayMs * (1 shl attempt.coerceAtMost(4))
            val finalDelay = calculatedDelay.coerceAtMost(5000L)
            
            android.util.Log.d(
                "CustomLoadErrorPolicy",
                "🔄 Network error (attempt ${attempt + 1}). Retry in ${finalDelay}ms. Error: ${exception.message}"
            )
            
            return finalDelay
        }
        
        android.util.Log.w(
            "CustomLoadErrorPolicy",
            "❌ Non-network error: ${exception::class.simpleName}. Using default retry policy."
        )
        return super.getRetryDelayMsFor(loadErrorInfo)
    }

    override fun getMinimumLoadableRetryCount(dataType: Int): Int {
        return Int.MAX_VALUE
    }
}
