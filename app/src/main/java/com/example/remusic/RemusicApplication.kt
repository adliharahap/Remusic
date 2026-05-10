package com.example.remusic

import android.app.Application
import android.util.Log
import com.metrolist.innertube.YouTube
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class RemusicApplication : Application() {

    private val appScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onCreate() {
        super.onCreate()

        // Inisialisasi YouTube visitor session di background saat app start
        // Ini WAJIB dilakukan sebelum pemutaran YouTube agar:
        // 1. spc parameter di stream URL di-sign dengan visitorData yang benar
        // 2. X-Goog-Visitor-Id header bisa disertakan saat streaming
        initYouTubeSession()
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
}
