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

    // Tag Log biar gampang difilter: ketik "FLOW_LOG" di Logcat
    private val TAG = "FLOW_LOG"

    // --- LOGIC 1: RESOLVE URL (SMART CACHE) ---
    suspend fun getPlayableUrl(songId: String, title: String, telegramFileId: String?, fallbackUrl: String?): String {
        Log.d(TAG, "================================================================")
        Log.d(TAG, "1️⃣ [REPO START] Request Lagu: \"$title\"")
        Log.d(TAG, "1️⃣ [REPO START] Meminta URL Playable untuk ID: $songId")

        // 1. Cek Telegram ID. Kalau kosong, pakai link Supabase (fallback)
        if (telegramFileId.isNullOrBlank()) {
            Log.i(TAG, "⚠️ [NON-TELEGRAM] Lagu ini pakai Link Direct/Supabase.")

            val directUrl = fallbackUrl ?: ""

            // Cek dulu apakah data lama sudah ada?
            val existingData = musicDao.getSongById(songId)

            val cacheDataToSave = existingData?.copy(
                lastPlayedAt = System.currentTimeMillis() // Update waktu putar
            ) ?: CachedSong(
                id = songId,
                title = title, // Judul dari parameter
                uploaderUserId = null,
                artistName = "Unknown", // Nanti diupdate getFullDetails
                coverUrl = null,
                lyrics = null,
                telegramFileId = null, // Kosong karena bukan Telegram
                telegramDirectUrl = directUrl, // Simpan URL aslinya disini
                urlExpiryTime = Long.MAX_VALUE, // Set abadi (karena link Supabase biasanya statis/tahan lama)
                lastPlayedAt = System.currentTimeMillis()
            )

            musicDao.insertSong(cacheDataToSave)
            Log.d(TAG, "💾 [SUCCESS] Lagu Non-Telegram berhasil dicatat di Database (Untuk History/Lirik).")
            // ---------------------------------------------

            return directUrl
        }

        // 2. Cek Cache Lokal (SQLite)
        Log.d(TAG, "2️⃣ [SQLITE] Mengecek database lokal...")
        val cachedSong = musicDao.getSongById(songId)
        val currentTime = System.currentTimeMillis()

        // 3. Validasi Cache: Ada? Punya Direct URL? Belum Expired (1 Jam)?
        if (cachedSong != null) {
            // Data lagu ditemukan di DB
            if (!cachedSong.telegramDirectUrl.isNullOrBlank()) {
                // Hitung sisa waktu expired (dalam menit)
                val timeLeft = (cachedSong.urlExpiryTime - currentTime) / 1000 / 60

                if (currentTime < cachedSong.urlExpiryTime) {
                    // KONDISI A: CACHE HIT (Bagus!)
                    Log.d(TAG, "lagu yang di play: \"$title\"")
                    Log.d(TAG, "✅ [CACHE HIT] URL Valid ditemukan!")
                    Log.d(TAG, "   ⏳ Status: Masih berlaku (Sisa $timeLeft menit)")
                    Log.d(TAG, "   🔗 Link: ${cachedSong.telegramDirectUrl}")
//                    Log.d(TAG, "   🔗 Data: ${cachedSong}")
                    Log.d(TAG, "================================================================")
                    return cachedSong.telegramDirectUrl
                } else {
                    // KONDISI B: CACHE EXPIRED (Basi)
                    Log.w(TAG, "⏰ [CACHE EXPIRED] Data ada, tapi URL sudah basi ($timeLeft menit yang lalu).")
                }
            } else {
                Log.w(TAG, "⚠️ [CACHE PARTIAL] Data lagu ada, tapi kolom URL Direct masih kosong.")
            }
        } else {
            // KONDISI C: CACHE MISS (Belum pernah disimpan)
            Log.d(TAG, "💨 [CACHE MISS] Data lagu ini belum ada sama sekali di SQLite.")
        }

        // 4. Jika Cache Miss/Expired -> Request Baru ke Vercel/API
        Log.d(TAG, "3️⃣ [NETWORK] Membuka koneksi ke API Vercel/Telegram...")
        try {
            val response = RetrofitInstance.api.getStreamUrl(telegramFileId)

            if (response.success && !response.url.isNullOrBlank()) {
                val newUrl = response.url
                // Set Expired 1 jam dari sekarang
                val expiryTime = System.currentTimeMillis() + (60 * 60 * 1000)

                // 5. Simpan/Update ke SQLite
                Log.d(TAG, "4️⃣ [DB SAVE] Menyimpan data baru ke SQLite...")

                // Kita gunakan copy() kalau data sudah ada, atau buat baru kalau belum
                val newCacheData = cachedSong?.copy(
                    telegramDirectUrl = newUrl,
                    urlExpiryTime = expiryTime,
                    lastPlayedAt = System.currentTimeMillis()
                ) ?: CachedSong(
                    id = songId,
                    title = "Loading...", // Placeholder, nanti diupdate fetchFullDetails
                    uploaderUserId = null,
                    artistName = "Unknown",
                    coverUrl = null,
                    lyrics = null,
                    telegramFileId = telegramFileId,
                    telegramDirectUrl = newUrl,
                    urlExpiryTime = expiryTime
                )

                musicDao.insertSong(newCacheData)
                Log.d(TAG, "💾 [SUCCESS] URL Streaming berhasil diperbarui di Database!")
                Log.d(TAG, "   🔗 New URL: $newUrl")
                Log.d(TAG, "================================================================")

                return newUrl
            } else {
                Log.e(TAG, "❌ [API FAIL] Server merespon success=false atau URL kosong.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ [API ERROR] Gagal koneksi: ${e.message}")
        }

        // 6. Jika semua gagal, kembalikan fallback
        Log.e(TAG, "💀 [FALLBACK] Semua metode gagal. Menggunakan URL asli dari Supabase.")
        return fallbackUrl ?: ""
    }

    // --- LOGIC 2: FETCH & SAVE DETAILS (LYRICS) ---
    suspend fun getFullSongDetails(songId: String): CachedSong? {
        Log.d(TAG, "🔍 [DETAILS] Meminta detail lengkap (Lirik, dll) untuk ID: $songId")

        // 1. Cek di SQLite dulu
        val cached = musicDao.getSongById(songId)

        // Kalau sudah ada liriknya, return langsung (OFFLINE MODE)
        if (cached != null && !cached.lyrics.isNullOrBlank()) {
            Log.d(TAG, "📜 [LYRICS CACHE] Lirik ditemukan di SQLite (Mode Offline). Tidak perlu internet.")
            return cached
        }

        // 2. Kalau lirik kosong, ambil dari Supabase
        Log.d(TAG, "☁️ [SUPABASE] Lirik kosong/hilang. Mengambil dari Cloud Database...")
        try {
            val supabaseSong = SupabaseManager.client
                .from("songs")
                .select(columns = Columns.list("id", "title", "lyrics", "cover_url", "uploader_user_id")) {
                    filter { eq("id", songId) }
                }
                .decodeSingleOrNull<Song>()

            if (supabaseSong != null) {
                // 3. Update SQLite dengan data lengkap
                Log.d(TAG, "📥 [DATA RECEIVED] Data diterima dari Supabase. Merging ke SQLite...")

                val oldData = musicDao.getSongById(songId)

                val updatedData = oldData?.copy(
                    title = supabaseSong.title,
                    lyrics = supabaseSong.lyrics,
                    coverUrl = supabaseSong.coverUrl
                ) ?: CachedSong(
                    id = songId,
                    title = supabaseSong.title,
                    uploaderUserId = supabaseSong.uploaderUserId,
                    artistName = "Unknown",
                    coverUrl = supabaseSong.coverUrl,
                    lyrics = supabaseSong.lyrics,
                    telegramFileId = supabaseSong.telegramFileId,
                    telegramDirectUrl = null // Jangan timpa URL streaming kalau ini object baru
                )

                musicDao.insertSong(updatedData)
                Log.d(TAG, "💾 [DETAILS SAVED] Lirik & Detail berhasil disimpan ke cache lokal.")
                return updatedData
            } else {
                Log.w(TAG, "⚠️ [SUPABASE EMPTY] Lagu tidak ditemukan di server.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ [SUPABASE ERROR] Gagal ambil detail: ${e.message}")
        }

        return cached // Return apa adanya (mungkin null atau data parsial)
    }
}