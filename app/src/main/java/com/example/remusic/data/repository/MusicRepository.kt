package com.example.remusic.data.repository

import android.util.Log
import com.example.remusic.data.local.MusicDao
import com.example.remusic.data.local.entity.CachedSong
import com.example.remusic.data.model.Song
import com.example.remusic.data.network.RetrofitInstance
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import com.example.remusic.data.SupabaseManager

class MusicRepository(private val musicDao: MusicDao) {

    // --- LOGIC 1: RESOLVE URL (SMART CACHE) ---
    suspend fun getPlayableUrl(songId: String, telegramFileId: String?, fallbackUrl: String?): String {
        // 1. Cek Telegram ID. Kalau kosong, pakai link Supabase (fallback)
        if (telegramFileId.isNullOrBlank()) {
            return fallbackUrl ?: ""
        }

        // 2. Cek Cache Lokal (SQLite)
        val cachedSong = musicDao.getSongById(songId)
        val currentTime = System.currentTimeMillis()

        // 3. Validasi Cache: Ada? Punya Direct URL? Belum Expired (1 Jam)?
        if (cachedSong != null &&
            !cachedSong.telegramDirectUrl.isNullOrBlank() &&
            currentTime < cachedSong.urlExpiryTime) {

            Log.d("MusicRepo", "✅ CACHE HIT: Menggunakan URL lokal untuk $songId")
            return cachedSong.telegramDirectUrl
        }

        // 4. Jika Cache Miss/Expired -> Request Baru ke Vercel/API
        Log.d("MusicRepo", "🌐 API CALL: Request link baru ke Vercel...")
        try {
            val response = RetrofitInstance.api.getStreamUrl(telegramFileId)

            if (response.success && !response.url.isNullOrBlank()) {
                val newUrl = response.url
                val expiryTime = System.currentTimeMillis() + (60 * 60 * 1000) // +1 Jam

                // 5. Simpan/Update ke SQLite
                // Kita gunakan copy() kalau data sudah ada, atau buat baru kalau belum
                val newCacheData = cachedSong?.copy(
                    telegramDirectUrl = newUrl,
                    urlExpiryTime = expiryTime,
                    lastPlayedAt = System.currentTimeMillis()
                )
                    ?: // Kalau lagu belum pernah disimpan, buat object baru
                    // Note: Idealnya kita butuh title/artist di sini, tapi untuk update URL saja cukup ID
                    // Nanti fetchFullDetails akan melengkapi datanya
                    CachedSong(
                        id = songId,
                        title = "Loading...", // Placeholder, akan diupdate fetchFullDetails
                        artistName = "Unknown",
                        coverUrl = null,
                        lyrics = null,
                        telegramFileId = telegramFileId,
                        telegramDirectUrl = newUrl,
                        urlExpiryTime = expiryTime
                    )

                musicDao.insertSong(newCacheData)
                return newUrl
            }
        } catch (e: Exception) {
            Log.e("MusicRepo", "❌ API ERROR: ${e.message}")
        }

        // 6. Jika semua gagal, kembalikan fallback
        return fallbackUrl ?: ""
    }

    // --- LOGIC 2: FETCH & SAVE DETAILS (LYRICS) ---
    suspend fun getFullSongDetails(songId: String): CachedSong? {
        // 1. Cek di SQLite dulu
        val cached = musicDao.getSongById(songId)

        // Kalau sudah ada liriknya, return langsung (OFFLINE MODE)
        if (cached != null && !cached.lyrics.isNullOrBlank()) {
            Log.d("MusicRepo", "📜 LYRICS CACHE: Lirik ditemukan di lokal.")
            return cached
        }

        // 2. Kalau lirik kosong, ambil dari Supabase
        Log.d("MusicRepo", "☁️ SUPABASE: Mengambil detail lagu & lirik...")
        try {
            val supabaseSong = SupabaseManager.client
                .from("songs")
                .select(columns = Columns.list("id", "title", "lyrics", "cover_url")) {
                    filter { eq("id", songId) }
                }
                .decodeSingleOrNull<Song>()

            if (supabaseSong != null) {
                // 3. Update SQLite dengan data lengkap
                // Kita ambil data lama (mungkin berisi URL streaming) dan gabungkan
                val oldData = musicDao.getSongById(songId)

                val updatedData = oldData?.copy(
                    title = supabaseSong.title,
                    lyrics = supabaseSong.lyrics,
                    coverUrl = supabaseSong.coverUrl
                )
                    ?: CachedSong(
                        id = songId,
                        title = supabaseSong.title,
                        artistName = "Unknown", // Perlu query artist terpisah kalau mau perfect
                        coverUrl = supabaseSong.coverUrl,
                        lyrics = supabaseSong.lyrics,
                        telegramFileId = supabaseSong.telegramFileId,
                        telegramDirectUrl = null
                    )

                musicDao.insertSong(updatedData)
                return updatedData
            }
        } catch (e: Exception) {
            Log.e("MusicRepo", "❌ SUPABASE ERROR: ${e.message}")
        }

        return cached // Return apa adanya (mungkin null atau data parsial)
    }
}