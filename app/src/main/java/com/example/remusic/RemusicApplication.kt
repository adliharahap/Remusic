package com.example.remusic

import android.app.Application
import android.util.Log
import com.example.remusic.data.SessionExpiredHandler
import com.example.remusic.data.SupabaseManager
import com.metrolist.innertube.YouTube
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner

class RemusicApplication : Application() {

    private val appScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var lastRefreshTime = 0L

    override fun onCreate() {
        super.onCreate()

        // Tanam pohon Timber untuk logging di mode debug!
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        // Inisialisasi YouTube visitor session di background saat app start
        // Ini WAJIB dilakukan sebelum pemutaran YouTube agar:
        // 1. spc parameter di stream URL di-sign dengan visitorData yang benar
        // 2. X-Goog-Visitor-Id header bisa disertakan saat streaming
        initYouTubeSession()
        com.example.remusic.utils.InnerTubeXPlayer.initialize(this)

        // Monitor session Supabase secara global.
        // Jika refresh_token expired atau dicabut, Supabase akan emit SessionStatus.NotAuthenticated.
        // Kita tangkap ini dan trigger forced logout via SessionExpiredHandler.
        monitorSupabaseSession()
        
        // Atur lifecycle untuk mengelola Supabase auto-refresh
        // Ini MENCEGAH bug "Refresh Token Reuse" (400 Invalid Grant) karena background timeout
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                // Aplikasi kembali ke layar (Foreground)
                val now = System.currentTimeMillis()
                // Refresh jika sudah lewat 45 menit (2700000 ms) dari refresh terakhir
                if (now - lastRefreshTime > 45 * 60 * 1000L) {
                    appScope.launch {
                        try {
                            // Tunggu Supabase init
                            kotlinx.coroutines.delay(1500)
                            val session = SupabaseManager.client.auth.currentSessionOrNull()
                            if (session != null) {
                                SupabaseManager.client.auth.refreshCurrentSession()
                                lastRefreshTime = System.currentTimeMillis()
                                Log.d("RemusicApp", "▶️ Supabase Session Refreshed (Foreground)")
                            }
                        } catch (e: Exception) {
                            Log.e("RemusicApp", "❌ Error refreshing Supabase session", e)
                        }
                    }
                }
            }
        })
    }

    private fun initYouTubeSession() {
        appScope.launch {
            try {
                if (YouTube.visitorData == null) {
                    Log.d("RemusicApp", "🔄 Menginisialisasi YouTube visitor session...")
                    val visitorData = YouTube.visitorData().getOrNull()
                    if (visitorData != null) {
                        YouTube.visitorData = visitorData
                        Log.d("RemusicApp", "✅ YouTube visitor session siap: ${visitorData.take(20)}...")
                    } else {
                        Log.w("RemusicApp", "⚠️ Gagal mendapat visitorData, streaming YouTube mungkin terbatas")
                    }
                }
            } catch (e: Exception) {
                Log.e("RemusicApp", "❌ Error inisialisasi YouTube session: ${e.message}")
                // Tidak fatal — app tetap jalan, streaming akan retry saat play
            }
        }
    }

    private fun monitorSupabaseSession() {
        appScope.launch {
            try {
                // Tunggu sampai SupabaseManager.client sudah di-initialize
                // (diinit di MainActivity.onCreate sebelum setContent)
                // Kita delay sedikit untuk memastikan ini
                kotlinx.coroutines.delay(2000)

                SupabaseManager.client.auth.sessionStatus.collect { status ->
                    Log.d("RemusicApp", "🔐 Supabase Session Status: $status")
                    when (status) {
                        is SessionStatus.NotAuthenticated -> {
                            // Ini dipanggil ketika:
                            // 1. User explicit logout (isSignOut = true)
                            // 2. refresh_token expired/revoked dan auto-refresh gagal (isSignOut = false)
                            if (!status.isSignOut) {
                                // Bukan explicit signOut → session expired paksa
                                Log.w("RemusicApp", "⚠️ Session expired secara paksa (bukan explicit logout)! Memaksa re-login...")
                                SessionExpiredHandler.notifySessionExpired()
                            } else {
                                Log.d("RemusicApp", "✅ User melakukan explicit logout — tidak perlu forced re-login.")
                            }
                        }
                        is SessionStatus.Authenticated -> {
                            Log.d("RemusicApp", "✅ Supabase session aktif.")
                            SessionExpiredHandler.reset() // Reset flag jika session kembali valid
                        }
                        is SessionStatus.Initializing -> {
                            Log.d("RemusicApp", "⏳ Supabase Auth sedang inisialisasi session dari storage...")
                        }
                        is SessionStatus.RefreshFailure -> {
                            // Auto-refresh gagal (kemungkinan offline atau refresh token hangus)
                            // Jangan langsung logout — tunggu status berikutnya (NotAuthenticated)
                            // yang akan dikonfirmasi oleh library jika refresh token memang invalid
                            Log.w("RemusicApp", "⚠️ Gagal refresh session: ${status.cause}. Menunggu konfirmasi...")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("RemusicApp", "❌ Error monitoring Supabase session: ${e.message}")
            }
        }
    }
}

