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
import com.example.remusic.data.model.Artist
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import java.time.Instant

class MusicRepository(private val musicDao: MusicDao) {

    // Tag Log biar gampang difilter: ketik "DEBUG_PLAYER" di Logcat
    private val TAG = "DEBUG_PLAYER"

    // Data class helper (bisa di file terpisah atau nested)
    data class ResolvedSongUrl(val url: String, val source: String)

    // --- MEMORY CACHE (Hilang saat app close) ---
    private val memoryUserCache = mutableMapOf<String, User>()
    private val memoryArtistCache = mutableMapOf<String, Artist>()

    // --- LOGIC 1: RESOLVE URL (DIRECT TELEGRAM WITH RETRY) ---
    suspend fun getPlayableUrl(songId: String, title: String, telegramFileId: String?, fallbackUrl: String?, artistId: String?): ResolvedSongUrl {
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
                lastPlayedAt = System.currentTimeMillis(),
                artistId = artistId
            )

            musicDao.insertSong(cacheDataToSave)
            Log.d(TAG, "💾 [SUCCESS] Lagu Non-Telegram berhasil dicatat di Database (Untuk History/Lirik).")
            Log.d(TAG, "🎵 [AUDIO SOURCE] CACHE (Offline/Direct) - Non Telegram")
            return ResolvedSongUrl(directUrl, "CACHE (Non-Telegram)")
        }

        // 2. Cek Cache Lokal (SQLite)
        val cachedSong = musicDao.getSongById(songId)
        val currentTime = System.currentTimeMillis()

        if (cachedSong != null && !cachedSong.telegramDirectUrl.isNullOrBlank()) {
            // Link Telegram biasanya valid 1 jam (3600 detik). Kita kasih buffer aman.
            if (currentTime < cachedSong.urlExpiryTime) {
                Log.d(TAG, "✅ [CACHE HIT] URL Telegram Masih Valid!")
                return ResolvedSongUrl(cachedSong.telegramDirectUrl, "CACHE")
            } else {
                Log.w(TAG, "⏰ [CACHE EXPIRED] URL Telegram sudah basi. Request ulang...")
            }
        }

        // 2.5 [NEW] Cek Supabase (Mungkin user lain sudah refresh linknya?)
        Log.d(TAG, "☁️ [SUPABASE CHECK] Mencari link fresh di server...")
        try {
            val remoteSong = SupabaseManager.client
                .from("songs")
                .select(columns = Columns.list("telegram_direct_url", "telegram_url_expires_at")) {
                    filter { eq("id", songId) }
                }
                .decodeSingleOrNull<Song>()

            if (remoteSong != null && !remoteSong.telegramDirectUrl.isNullOrBlank()) {
                val remoteExpiry = remoteSong.telegramUrlExpiresAt

                // Parse expires_at (String ISO-8601) to Millis
                if (remoteExpiry != null) {
                    val remoteExpiryMs = try {
                        Instant.parse(remoteExpiry).toEpochMilli()
                    } catch (_: Exception) {
                         0L
                    }
                    if (currentTime < remoteExpiryMs) {
                        Log.d(TAG, "⚡ [SUPABASE HIT] Link Fresh ditemukan di server! (Valid sampai: $remoteExpiry)")

                        // Update Local Cache
                        if (cachedSong != null) {
                             musicDao.updateSongUrl(songId, remoteSong.telegramDirectUrl, remoteExpiryMs, currentTime)
                        } else {
                             // Insert minimal cache
                             // ... (logic insert similar to below)
                        }

                        return ResolvedSongUrl(remoteSong.telegramDirectUrl, "SUPABASE")
                    } else {
                         Log.d(TAG, "⚠️ [SUPABASE STALE] Link di server juga basi.")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "⚠️ [SUPABASE CHECK FAIL] Gagal cek link server: ${e.message}")
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
                                lastPlayedAt = System.currentTimeMillis(), artistId = artistId
                            )
                            musicDao.insertSong(newCacheData)
                        }

                        // 4.5 [NEW] Push ke Supabase (Donasi Link ke Komunitas)
                        // Fire & Forget (Launch di scope global/IO agar tidak block return)
                        try {
                            // Hati-hati: Function suspend harus dipanggil di scope.
                            // Kita pakai logic sederhana: update langsung (blocking sebentar gpp demi konsistensi)
                            Log.d(TAG, "🌍 [CONTRIBUTE] Upload link baru ke Supabase...")

                            // Perlu konversi expiryTime (Long millis) ke Instant
                            val expiryInstant = Instant.ofEpochMilli(expiryTime)

                            SupabaseManager.client.from("songs").update(
                                mapOf(
                                    "telegram_direct_url" to finalUrl,
                                    "telegram_url_expires_at" to expiryInstant.toString() // ISO String
                                )
                            ) {
                                filter { eq("id", songId) }
                            }
                            Log.d(TAG, "✅ [CONTRIBUTE] Link berhasil di-share ke user lain.")
                        } catch (e: Exception) {
                            Log.e(TAG, "⚠️ [CONTRIBUTE FAIL] Gagal upload link ke Supabase: ${e.message}")
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
            return ResolvedSongUrl(finalUrl, "NETWORK")
        }

        // 6. Fallback jika gagal total setelah 4x coba
        Log.e(TAG, "💀 [GIVE UP] Gagal total setelah $maxAttempts percobaan.")
        return ResolvedSongUrl(fallbackUrl ?: "", "FALLBACK")
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
                .select(columns = Columns.list("id", "title", "lyrics", "lyrics_updated_at", "cover_url", "canvas_url", "uploader_user_id", "language", "moods", "artist_id", "featured_artists")) {
                    filter { eq("id", songId) }
                }
                .decodeSingleOrNull<Song>()

            if (supabaseSong != null) {
                // 3. Update SQLite dengan data lengkap
                Log.d(TAG, "📥 [DATA RECEIVED] Data diterima. Merging ke SQLite...")
                Log.d(TAG, "   -> Featured Artists (Server): ${supabaseSong.featuredArtists}")
                Log.d(TAG, "   -> Artist ID: ${supabaseSong.artistId}")

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
                        artistId = supabaseSong.artistId,
                        featuredArtists = supabaseSong.featuredArtists
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
            Log.e(TAG, "[LIKE CHECK ERROR] Gagal cek status like: ${e.message}")
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

    // --- LOGIC 4: FETCH USER DETAILS (WITH RETRY) ---
    suspend fun fetchUserDetails(userId: String): User? {
        Log.d(TAG, "[USER] Meminta detail user untuk ID: $userId")

        // 1. Cek Memory Cache
        if (memoryUserCache.containsKey(userId)) {
            Log.d(TAG, "⚡ [USER CACHE HIT] User ditemukan di memory cache.")
            return memoryUserCache[userId]
        }

        var attempt = 1
        val maxAttempts = 4

        while (attempt <= maxAttempts) {
            try {
                if (attempt > 1) Log.d(TAG, "🔄 RETRY USER: Percobaan ke-$attempt...")

                val user = SupabaseManager.client
                    .from("users")
                    .select {
                        filter { eq("id", userId) }
                    }
                    .decodeSingleOrNull<User>()

                if (user != null) {
                    memoryUserCache[userId] = user // Simpan ke cache
                    return user
                }

            } catch (e: Exception) {
                Log.e(TAG, "[USER ERROR] Percobaan $attempt Gagal: ${e.message}")
            }

            if (attempt < maxAttempts) delay(1000)
            attempt++
        }

        Log.e(TAG, "[USER GIVE UP] Gagal ambil detail user setelah $maxAttempts percobaan.")
        return null
    }

    // --- LOGIC 4.5: FETCH ARTIST DETAILS (WITH RETRY) ---
    suspend fun fetchArtistDetails(artistId: String): Artist? {
        Log.d(TAG, "[ARTIST] Meminta detail artist untuk ID: $artistId")

        // 1. Cek Memory Cache
        if (memoryArtistCache.containsKey(artistId)) {
            Log.d(TAG, "⚡ [ARTIST CACHE HIT] Artist ditemukan di memory cache.")
            return memoryArtistCache[artistId]
        }

        var attempt = 1
        val maxAttempts = 4

        while (attempt <= maxAttempts) {
            try {
                if (attempt > 1) Log.d(TAG, "🔄 RETRY ARTIST: Percobaan ke-$attempt...")

                val artist = SupabaseManager.client
                    .from("artists")
                    .select {
                        filter { eq("id", artistId) }
                    }
                    .decodeSingleOrNull<Artist>()

                if (artist != null) {
                    memoryArtistCache[artistId] = artist // Simpan ke cache
                    return artist
                }

            } catch (e: Exception) {
                Log.e(TAG, "[ARTIST ERROR] Percobaan $attempt Gagal: ${e.message}")
            }

            if (attempt < maxAttempts) delay(1000)
            attempt++
        }

        Log.e(TAG, "[ARTIST GIVE UP] Gagal ambil detail artist setelah $maxAttempts percobaan.")
        return null
    }

    // --- LOGIC 4.6: BULK FETCH ARTISTS (FOR SMART QUEUE) ---
    suspend fun fetchArtistsByIds(artistIds: List<String>): List<Artist> {
        if (artistIds.isEmpty()) return emptyList()

        // Filter valid IDs and remove duplicates
        val uniqueIds = artistIds.filter { it.isNotBlank() }.distinct()
        if (uniqueIds.isEmpty()) return emptyList()

        Log.d(TAG, "[ARTIST BULK] Meminta ${uniqueIds.size} artists...")

        // 1. Check Cache first
        val cachedArtists = uniqueIds.mapNotNull { memoryArtistCache[it] }
        val missingIds = uniqueIds.filter { !memoryArtistCache.containsKey(it) }

        if (missingIds.isEmpty()) {
            Log.d(TAG, "⚡ [ARTIST BULK CACHE HIT] Semua artist ditemukan di cache.")
            return cachedArtists
        }

        try {
            Log.d(TAG, "🌍 [ARTIST BULK FETCH] Mengambil ${missingIds.size} artist dari DB...")
            val fetchedArtists = SupabaseManager.client
                .from("artists")
                .select {
                    filter { isIn("id", missingIds) }
                }
                .decodeList<Artist>()

            // Update Cache
            fetchedArtists.forEach { artist ->
                memoryArtistCache[artist.id] = artist
            }

            return cachedArtists + fetchedArtists

        } catch (e: Exception) {
            Log.e(TAG, "❌ [ARTIST BULK ERROR]: ${e.message}")
            // Return whatever we have in cache
            return cachedArtists
        }
    }

    // --- LOGIC 5: SMART QUEUE V4 (Final Boss - Hardened) ---
    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    suspend fun fetchSmartQueue(
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
            val safeMoods = currentSong.moods

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
                    put("p_played_ids", JsonArray(listOf(JsonPrimitive(currentSong.id))))

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

    // --- LOGIC 6: INCREMENT PLAY COUNT ---
    suspend fun incrementPlayCount(songId: String) {
        try {
            SupabaseManager.client.postgrest.rpc(
                "increment_play_count",
                parameters = buildJsonObject {
                    put("song_uuid", songId)
                }
            )
            Log.d(TAG, "🆙 [PLAY COUNT] Berhasil increment play count untuk lagu: $songId")
        } catch (e: Exception) {
            Log.e(TAG, "❌ [PLAY COUNT ERROR] Gagal increment play count: ${e.message}")
        }
    }
}