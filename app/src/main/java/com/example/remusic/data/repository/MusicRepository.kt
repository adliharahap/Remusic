package com.example.remusic.data.repository

import android.util.Log
import com.example.remusic.data.local.MusicDao
import com.example.remusic.data.local.entity.CachedSong
import com.example.remusic.data.model.Song
import com.example.remusic.data.network.RetrofitInstance
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import com.example.remusic.data.SupabaseManager
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonObject

class MusicRepository(private val musicDao: MusicDao) {

    // Tag Log biar gampang difilter: ketik "DEBUG_PLAYER" di Logcat
    private val TAG = "DEBUG_PLAYER"

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
            Log.d(TAG, "🎵 [AUDIO SOURCE] CACHE (Offline/Direct) - Non Telegram")
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
                    Log.d(TAG, "🎵 [AUDIO SOURCE] CACHE (Offline/Direct)")
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
        Log.d(TAG, "[NETWORK] Membuka koneksi ke API Vercel/Telegram...")

        var attempt = 1
        val maxAttempts = 4
        var finalUrl = ""

        while (attempt <= maxAttempts) {
            try {
                if (attempt > 1) Log.d(TAG, "🔄 RETRY NETWORK: Percobaan ke-$attempt...")

                // --- REQUEST START ---
                val response = RetrofitInstance.api.getStreamUrl(songId, telegramFileId)
                // --- REQUEST END ---

                if (response.success && !response.url.isNullOrBlank()) {
                    val newUrl = response.url
                    val expiryTime = try {
                        if (response.expires_at != null) {
                            java.time.Instant.parse(response.expires_at).toEpochMilli()
                        } else {
                            System.currentTimeMillis() + (60 * 60 * 1000)
                        }
                    } catch (e: Exception) {
                        System.currentTimeMillis() + (60 * 60 * 1000)
                    }

                    // 5. Simpan/Update ke SQLite
                    Log.d(TAG, "4️⃣ [DB SAVE] Menyimpan data baru ke SQLite...")
                    if (cachedSong != null) {
                        musicDao.updateSongUrl(songId, newUrl, expiryTime, System.currentTimeMillis())
                        Log.d(TAG, "💾 [SUCCESS] URL Streaming diperbarui (Partial Update).")
                    } else {
                        val newCacheData = CachedSong(
                            id = songId, title = title, uploaderUserId = null, artistName = "Unknown",
                            coverUrl = null, lyrics = null, telegramFileId = telegramFileId,
                            telegramDirectUrl = newUrl, urlExpiryTime = expiryTime
                        )
                        musicDao.insertSong(newCacheData)
                        Log.d(TAG, "💾 [SUCCESS] Lagu baru disimpan ke Database.")
                    }

                    finalUrl = newUrl
                    break // 🛑 SUKSES! KELUAR DARI LOOP
                } else {
                    Log.e(TAG, "❌ [API FAIL] Server merespon success=false.")
                }

            } catch (e: Exception) {
                Log.e(TAG, "❌ [API ERROR] Gagal koneksi (Attempt $attempt): ${e.message}")

                // Jika ini percobaan pertama dan gagal, tunggu 1.5 detik sebelum coba lagi
                if (attempt < maxAttempts) {
                    Log.d(TAG, "⏳ Menunggu 1.5 detik untuk retry...")
                    delay(1500)
                }
            }
            attempt++
        }

        if (finalUrl.isNotBlank()) {
            Log.d(TAG, "   🔗 New URL: $finalUrl")
            Log.d(TAG, "   🌐 SOURCE: NETWORK REQUEST (Vercel)")
            Log.d(TAG, "📡 [AUDIO SOURCE] STREAMING (Network)")
            Log.d(TAG, "================================================================")
            return finalUrl
        }

        // 6. Jika semua gagal, kembalikan fallback
        Log.e(TAG, "💀 [FALLBACK] Semua metode gagal. Menggunakan URL asli dari Supabase.")
        Log.d(TAG, "🎵 [AUDIO SOURCE] CACHE (Offline/Direct) - Fallback")
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
            Log.d(TAG, "📜 [LYRICS SOURCE] CACHE (Offline)")
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

                if (oldData != null) {
                    // PARTIAL UPDATE: Hanya update Detail (Judul, Lirik, Cover)
                    musicDao.updateSongDetails(
                        id = songId,
                        title = supabaseSong.title,
                        lyrics = supabaseSong.lyrics,
                        cover = supabaseSong.coverUrl,
                        uploaderId = supabaseSong.uploaderUserId
                    )
                    Log.d(TAG, "💾 [DETAILS SAVED] Detail lagu diperbarui (Partial Update).")
                    
                    // Return object gabungan untuk UI
                    return oldData.copy(
                        title = supabaseSong.title,
                        lyrics = supabaseSong.lyrics,
                        coverUrl = supabaseSong.coverUrl,
                        uploaderUserId = supabaseSong.uploaderUserId
                    )
                } else {
                    // INSERT BARU (Jarang terjadi kalau flow benar)
                    val newData = CachedSong(
                        id = songId,
                        title = supabaseSong.title,
                        uploaderUserId = supabaseSong.uploaderUserId,
                        artistName = "Unknown",
                        coverUrl = supabaseSong.coverUrl,
                        lyrics = supabaseSong.lyrics,
                        telegramFileId = supabaseSong.telegramFileId,
                        telegramDirectUrl = null
                    )
                    musicDao.insertSong(newData)
                    Log.d(TAG, "💾 [DETAILS SAVED] Detail lagu baru disimpan.")
                    Log.d(TAG, "☁️ [LYRICS SOURCE] VERCEL/SUPABASE")
                    return newData
                }
            } else {
                Log.w(TAG, "⚠️ [SUPABASE EMPTY] Lagu tidak ditemukan di server.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ [SUPABASE ERROR] Gagal ambil detail: ${e.message}")
        }

        return cached // Return apa adanya (mungkin null atau data parsial)
    }

    // --- LOGIC 3: LIKE / UNLIKE SONG ---
    suspend fun isSongLiked(songId: String, userId: String): Boolean {
        return try {
            val result = SupabaseManager.client
                .from("user_song_likes")
                .select(columns = Columns.list("user_id")) {
                    filter {
                        eq("user_id", userId)
                        eq("song_id", songId)
                    }
                    limit(1) // Cukup ambil 1 saja untuk verifikasi
                }

            // Decode hasilnya menjadi List. Jika list tidak kosong, berarti sudah di-like.
            val data = result.decodeList<JsonObject>()
            data.isNotEmpty()

        } catch (e: Exception) {
            Log.e(TAG, "❌ [LIKE CHECK ERROR] Gagal cek status like: ${e.message}")
            false
        }
    }

    suspend fun toggleLike(songId: String, userId: String, isLiked: Boolean) {
        try {
            if (isLiked) {
                // DELETE (Unlike)
                SupabaseManager.client
                    .from("user_song_likes")
                    .delete {
                        filter {
                            eq("user_id", userId)
                            eq("song_id", songId)
                        }
                    }
                Log.d(TAG, "💔 [UNLIKE] Lagu dihapus dari Liked Songs.")
            } else {
                // INSERT (Like)
                val data = mapOf("user_id" to userId, "song_id" to songId)
                SupabaseManager.client
                    .from("user_song_likes")
                    .insert(data)
                Log.d(TAG, "❤️ [LIKE] Lagu ditambahkan ke Liked Songs.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ [LIKE ERROR] Gagal toggle like: ${e.message}")
            throw e // Re-throw biar ViewModel tau kalau gagal
        }
    }
}