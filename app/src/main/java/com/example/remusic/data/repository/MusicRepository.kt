package com.example.remusic.data.repository

import android.util.Log
import com.example.remusic.data.local.MusicDao
import com.example.remusic.data.local.entity.CachedSong
import com.example.remusic.data.model.Song
import com.example.remusic.data.model.User
import com.example.remusic.data.network.TelegramRetrofit
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import com.example.remusic.data.SupabaseManager
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive

class MusicRepository(private val musicDao: MusicDao) {

    // Tag Log biar gampang difilter: ketik "DEBUG_PLAYER" di Logcat
    private val TAG = "DEBUG_PLAYER"

    // --- LOGIC 1: RESOLVE URL (DIRECT TELEGRAM WITH RETRY) ---
    suspend fun getPlayableUrl(songId: String, title: String, telegramFileId: String?, fallbackUrl: String?): String {
        Log.d(TAG, "================================================================")
        Log.d(TAG, "🚀 [DIRECT MODE] Request Lagu: \"$title\"")

        // 1. Validasi File ID
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
            return directUrl
        }

        // 2. Cek Cache Lokal (SQLite)
        val cachedSong = musicDao.getSongById(songId)
        val currentTime = System.currentTimeMillis()

        if (cachedSong != null && !cachedSong.telegramDirectUrl.isNullOrBlank()) {
            // Link Telegram biasanya valid 1 jam (3600 detik). Kita kasih buffer aman.
            if (currentTime < cachedSong.urlExpiryTime) {
                Log.d(TAG, "✅ [CACHE HIT] URL Telegram Masih Valid!")
                return cachedSong.telegramDirectUrl
            } else {
                Log.w(TAG, "⏰ [CACHE EXPIRED] URL Telegram sudah basi. Request ulang...")
            }
        }

        // 3. REQUEST LANGSUNG KE TELEGRAM (LOOP RETRY 4X)
        Log.d(TAG, "⚡ [NETWORK] Menembak API Telegram Langsung...")

        var attempt = 1
        val maxAttempts = 4
        var finalUrl = ""

        // 🔥 MULAI LOOP RETRY DI SINI 🔥
        while (attempt <= maxAttempts) {
            try {
                if (attempt > 1) Log.d(TAG, "🔄 RETRY NETWORK: Percobaan ke-$attempt...")

                // Panggil API getFile
                val response = TelegramRetrofit.api.getFile(telegramFileId)

                if (response.isSuccessful && response.body()?.ok == true) {
                    val filePath = response.body()?.result?.filePath

                    if (!filePath.isNullOrBlank()) {
                        // SUKSES DAPAT FILE PATH!
                        finalUrl = TelegramRetrofit.getFinalDownloadUrl(filePath)

                        // Set Expiry (55 Menit)
                        val expiryTime = System.currentTimeMillis() + (55 * 60 * 1000)

                        // 4. Simpan ke Database
                        Log.d(TAG, "💾 [DB SAVE] Menyimpan URL Telegram baru untuk: $title")
                        if (cachedSong != null) {
                            musicDao.updateSongUrl(songId, finalUrl, expiryTime, System.currentTimeMillis())
                        } else {
                            val newCacheData = CachedSong(
                                id = songId, title = title, uploaderUserId = null, artistName = "Unknown",
                                coverUrl = null, lyrics = null, telegramFileId = telegramFileId,
                                telegramDirectUrl = finalUrl, urlExpiryTime = expiryTime,
                                lastPlayedAt = System.currentTimeMillis()
                            )
                            musicDao.insertSong(newCacheData)
                        }

                        Log.d(TAG, "   🔗 URL: $finalUrl")
                        break // 🛑 BERHASIL! KELUAR DARI LOOP (PENTING)
                    }
                } else {
                    Log.e(TAG, "❌ [TELEGRAM ERROR] Gagal dapat filePath. Code: ${response.code()}")
                }

            } catch (e: Exception) {
                Log.e(TAG, "❌ [NETWORK ERROR] Percobaan $attempt Gagal: ${e.message}")
            }

            // Jika gagal, dan belum mencapai batas maksimal, tunggu 1 detik
            if (attempt < maxAttempts) {
                Log.d(TAG, "⏳ Menunggu 1 detik sebelum coba lagi...")
                delay(1000) // Delay 1 Detik
            }
            attempt++
        }

        // 5. Cek Hasil Akhir Loop
        if (finalUrl.isNotBlank()) {
            return finalUrl
        }

        // 6. Fallback jika gagal total setelah 4x coba
        Log.e(TAG, "💀 [GIVE UP] Gagal total setelah $maxAttempts percobaan.")
        return fallbackUrl ?: ""
    }

    // --- LOGIC 2: FETCH & SAVE DETAILS (LYRICS) ---
    suspend fun getFullSongDetails(songId: String): CachedSong? {
        Log.d(TAG, "🔍 [DETAILS] Meminta detail lengkap (Lirik, dll) untuk ID: $songId")

        // 1. Cek di SQLite dulu
        val cached = musicDao.getSongById(songId)

        // ----------------------------------------------------
        // 🔥 LOGIC SINKRONISASI LIRIK (VERSIONING CHECK) 🔥
        // ----------------------------------------------------
        // Kita tidak langsung percaya cache. Kita cek dulu ke Supabase:
        // "Apakah lirik di server lebih baru dari yang saya punya?"

        var shouldFetchFullDetails = false

        try {
            // A. Ambil HANYA timestamp dari server (Hemat Kuota)
            Log.d(TAG, "☁️ [VERSION CHECK] Mengecek versi lirik ke Supabase...")
            val remoteVersion = SupabaseManager.client
                .from("songs")
                .select(columns = Columns.list("lyrics_updated_at")) {
                    filter { eq("id", songId) }
                }
                .decodeSingleOrNull<Song>()

            val remoteTimestamp = remoteVersion?.lyricsUpdatedAt
            val localTimestamp = cached?.lyricsUpdatedAt

            if (remoteTimestamp != null) {
                if (localTimestamp == null) {
                    // KASUS 1: Lokal belum ada timestamp (lagu baru di-cache)
                    Log.d(TAG, "🆕 [NEW LYRICS] Lirik lokal belum ada versinya. Download...")
                    shouldFetchFullDetails = true
                } else if (localTimestamp != remoteTimestamp) {
                    // KASUS 2: Remote beda (lebih baru)
                    Log.d(TAG, "♻️ [UPDATE DETECTED] Server: $remoteTimestamp vs Lokal: $localTimestamp")
                    shouldFetchFullDetails = true
                } else {
                    // KASUS 3: Timestamp sama -> AMAN!
                    Log.d(TAG, "✅ [FRESH] Lirik lokal sudah paling update ($localTimestamp). Skip download.")
                    shouldFetchFullDetails = false
                }
            } else {
                // Remote ga punya timestamp (karena null/query salah?), fallback logic lama
                Log.w(TAG, "⚠️ [NO VERSION] Server tidak kirim timestamp. Cek manual.")
                if (cached?.lyrics.isNullOrBlank()) shouldFetchFullDetails = true
            }

        } catch (e: Exception) {
            Log.e(TAG, "⚠️ [VERSION CHECK FAIL] Gagal cek versi: ${e.message}. Fallback ke logic lama.")
            // Kalau gagal cek versi (internet putus?), gunakan logic lama:
            // Fetch kalau lirik di cache kosong.
            if (cached?.lyrics.isNullOrBlank()) shouldFetchFullDetails = true
        }

        // B. Keputusan Akhir
        if (!shouldFetchFullDetails && cached != null && !cached.lyrics.isNullOrBlank()) {
            return cached
        }

        // ----------------------------------------------------
        // END LOGIC SINKRONISASI
        // ----------------------------------------------------

        // 2. Fetch Full Data dari Supabase (Jika Perlu)
        Log.d(TAG, "☁️ [SUPABASE] Mengambil Data Lengkap (Lirik & Metadata)...")
        try {
            val supabaseSong = SupabaseManager.client
                .from("songs")
                .select(columns = Columns.list("id", "title", "lyrics", "lyrics_updated_at", "cover_url", "canvas_url", "uploader_user_id", "language", "moods", "artist_id")) {
                    filter { eq("id", songId) }
                }
                .decodeSingleOrNull<Song>()

            if (supabaseSong != null) {
                // 3. Update SQLite dengan data lengkap
                Log.d(TAG, "📥 [DATA RECEIVED] Data diterima. Merging ke SQLite...")

                val oldData = musicDao.getSongById(songId)

                if (oldData != null) {
                    // PARTIAL UPDATE
                    musicDao.updateSongDetails(
                        id = songId,
                        title = supabaseSong.title,
                        lyrics = supabaseSong.lyrics,
                        lyricsUpdatedAt = supabaseSong.lyricsUpdatedAt, // Simpan versi baru
                        cover = supabaseSong.coverUrl,
                        canvas = supabaseSong.canvasUrl,
                        uploaderId = supabaseSong.uploaderUserId,
                        language = supabaseSong.language,
                        moods = supabaseSong.moods,
                        artistId = supabaseSong.artistId
                    )
                    Log.d(TAG, "💾 [DETAILS SAVED] Detail & Lirik diperbarui.")

                    // Return object gabungan
                    return oldData.copy(
                        title = supabaseSong.title,
                        lyrics = supabaseSong.lyrics,
                        lyricsUpdatedAt = supabaseSong.lyricsUpdatedAt,
                        coverUrl = supabaseSong.coverUrl,
                        canvasUrl = supabaseSong.canvasUrl,
                        uploaderUserId = supabaseSong.uploaderUserId,
                        language = supabaseSong.language,
                        moods = supabaseSong.moods,
                        artistId = supabaseSong.artistId
                    )
                } else {
                    // INSERT BARU
                    val newData = CachedSong(
                        id = songId,
                        title = supabaseSong.title,
                        uploaderUserId = supabaseSong.uploaderUserId,
                        artistName = "Unknown",
                        coverUrl = supabaseSong.coverUrl,
                        canvasUrl = supabaseSong.canvasUrl,
                        lyrics = supabaseSong.lyrics,
                        lyricsUpdatedAt = supabaseSong.lyricsUpdatedAt,
                        telegramFileId = supabaseSong.telegramFileId,
                        telegramDirectUrl = null,
                        language = supabaseSong.language,
                        moods = supabaseSong.moods,
                        artistId = supabaseSong.artistId
                    )
                    musicDao.insertSong(newData)
                    Log.d(TAG, "💾 [NEW DATA] Lagu baru disimpan ke cache.")
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

    // --- LOGIC 4: FETCH USER DETAILS ---
    suspend fun fetchUserDetails(userId: String): User? {
        Log.d(TAG, "🔍 [USER] Meminta detail user untuk ID: $userId")
        return try {
            SupabaseManager.client
                .from("users")
                .select {
                    filter { eq("id", userId) }
                }
                .decodeSingleOrNull<User>()
        } catch (e: Exception) {
            Log.e(TAG, "❌ [USER ERROR] Gagal ambil detail user: ${e.message}")
            null
        }
    }

    // --- LOGIC 5: SMART QUEUE V4 (Final Boss - Hardened) ---
    suspend fun fetchSmartQueue(
        playedSongIds: List<String>,
        currentSong: Song // Seed Song
    ): List<Song> {
        Log.d("MusicRepository", "🔄 calling get_smart_queue RPC...")
        
        // ... di dalam fetchSmartQueue ...
        val targetLang = currentSong.language ?: "en" // Ini tersangkanya
        Log.e("DEBUG_SMART_QUEUE", "🚨 SEED CHECK: Judul=${currentSong.title}, Lang di DB=${currentSong.language}, Lang dikirim=$targetLang")
        Log.e("DEBUG_SMART_QUEUE", "🚨 ARTIST CHECK: ID=${currentSong.artistId}")

        return try {
            // 🛡️ 1. SAFEGUARD MOODS
            // Kalau mood null, kita kasih list kosong biar map gak crash
            val safeMoods = currentSong.moods ?: emptyList()

            // 🛡️ 2. SAFEGUARD ARTIST ID
            // Kalau artistId null/kosong, jangan kirim string kosong "", nanti error UUID invalid.
            // Kirim null saja (JsonPrimitive(null) atau abaikan).
            // Tapi biar aman di logika SQL, kita pastikan kirim string valid atau null json.
            val safeArtistId = if (currentSong.artistId.isNullOrBlank()) {
                null // Kirim null beneran ke JSON
            } else {
                currentSong.artistId
            }

            val results = SupabaseManager.client.postgrest.rpc(
                "get_smart_queue",
                parameters = buildJsonObject {
                    // Array ID yang sudah diputar
                    put("p_played_ids", JsonArray(playedSongIds.map { JsonPrimitive(it) }))

                    // Bahasa Target (Fallback ke 'en' jika null)
                    put("p_target_lang", currentSong.language ?: "en")

                    // Mood Target (Ambil dari safeMoods)
                    put("p_target_moods", JsonArray(safeMoods.map { JsonPrimitive(it) }))

                    // ID Artis (Bisa null)
                    put("p_seed_artist_id", safeArtistId?.let { JsonPrimitive(it) } ?: JsonPrimitive(null))

                    // Limit
                    put("p_limit", 49)
                }
            ).decodeList<Song>()

            Log.d("MusicRepository", "✅ get_smart_queue returned ${results.size} songs")
            results

        } catch (e: Exception) {
            Log.e(TAG, "❌ [SMART QUEUE ERROR]: ${e.message}")
            // Fallback: Kembalikan list kosong agar player tidak error, nanti ViewModel handle logic fallbacknya
            emptyList()
        }
    }
}