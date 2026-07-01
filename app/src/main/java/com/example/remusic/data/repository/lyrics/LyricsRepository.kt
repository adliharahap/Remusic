package com.example.remusic.data.repository.lyrics

import android.util.Log
import com.example.remusic.data.SupabaseManager
import com.example.remusic.data.local.MusicDao
import com.example.remusic.data.network.LrcLibResponse
import com.example.remusic.data.network.LrcLibRetrofit
import io.github.jan.supabase.postgrest.from
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant

class LyricsRepository(private val musicDao: MusicDao) {
    private val TAG = "DEBUG_PLAYER_LYRICS"

    fun isLyricsSynced(lyrics: String?): Boolean {
        if (lyrics.isNullOrBlank()) return false
        if (lyrics == "[Instrumental]") return true
        // Regex: Matches [mm:ss] or [m:ss] or [mm:ss.xx] etc.
        return lyrics.contains(Regex("\\[\\d+:\\d{2}"))
    }

    suspend fun searchAlternativeLyrics(query: String): List<LrcLibResponse> {
        return try {
            LrcLibRetrofit.api.searchLyrics(query = query)
        } catch (e: Exception) {
            Log.e(TAG, "❌ [LRCLIB SEARCH] Failed: ${e.message}")
            emptyList()
        }
    }

    suspend fun searchAlternativeLyrics(track: String, artist: String): List<LrcLibResponse> {
        return try {
            LrcLibRetrofit.api.searchLyrics(trackName = track, artistName = artist)
        } catch (e: Exception) {
            Log.e(TAG, "❌ [LRCLIB SEARCH] Failed: ${e.message}")
            emptyList()
        }
    }

    suspend fun pushLyricsToLyricsHub(songId: String, title: String, artist: String, lyrics: String, source: String) {
        try {
            Log.d(TAG, "🌍 [CONTRIBUTE] Upserting lyrics to lyrics_hub for ID: $songId")
            val now = Instant.now().toString()

            val data = buildJsonObject {
                put("id", songId)
                put("title", title)
                put("artist", artist)
                put("lyrics", lyrics)
                put("source", source)
                put("updated_at", now)
            }

            // Menggunakan upsert agar tidak error jika ID sudah ada
            SupabaseManager.client.from("lyrics_hub").upsert(data)
            Log.d(TAG, "✅ [CONTRIBUTE] Lyrics Hub updated successfully!")
        } catch (e: Exception) {
            Log.e(TAG, "⚠️ [CONTRIBUTE HUB FAIL] Gagal upload lirik: ${e.message}")
        }
    }

    suspend fun updateLyrics(songId: String, lyrics: String) {
        musicDao.updateLyrics(songId, lyrics)
    }
}
