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

// Data Models
import com.example.remusic.data.model.ArtistDetails
import com.example.remusic.data.model.SongWithArtist
import kotlinx.coroutines.flow.first
import com.example.remusic.data.local.entity.LikedSong
import com.example.remusic.data.local.entity.CachedFollowedArtist
import com.example.remusic.data.local.entity.PlaybackQueueEntity
import io.github.jan.supabase.postgrest.query.Order

class MusicRepository(private val musicDao: MusicDao) {

    // --- SYNC ENGINE: DOWNLOAD DATA USER (Likes & Follows) ---
    suspend fun synchronizeUserData(userId: String) {
        Log.d("DEBUG_PLAYER", "🔄 [SYNC] Memulai sinkronisasi data user (Likes & Follows)...")
        try {
            // 1. Sync Liked Songs
             val remoteLikes = SupabaseManager.client
                .from("user_song_likes")
                .select(columns = Columns.list("song_id", "created_at")) {
                    filter { eq("user_id", userId) }
                }
                .decodeList<JsonObject>()
            
            // Bulk Insert to SQLite
            remoteLikes.forEach {
                val songId = it["song_id"].toString().replace("\"", "")
                // Simpan as LikedSong
                // Parse timestamp if needed, or just use now/default
                musicDao.insertLikedSong(LikedSong(songId))
            }
            Log.d("DEBUG_PLAYER", "✅ [SYNC] ${remoteLikes.size} liked songs tersimpan di Local.")

            // 2. Sync Followed Artists
            val remoteFollows = SupabaseManager.client
                .from("artist_followers")
                .select(columns = Columns.list("artist_id", "created_at")) {
                     filter { eq("user_id", userId) }
                }
                .decodeList<JsonObject>()
            
            remoteFollows.forEach {
                 val artistId = it["artist_id"].toString().replace("\"", "")
                 musicDao.insertFollowedArtist(CachedFollowedArtist(artistId))
            }
            Log.d("DEBUG_PLAYER", "✅ [SYNC] ${remoteFollows.size} artists followed tersimpan di Local.")

        } catch (e: Exception) {
            Log.e("DEBUG_PLAYER", "❌ [SYNC ERROR] Gagal sinkronisasi data user: ${e.message}")
        }
    }

    // Tag Log biar gampang difilter: ketik "DEBUG_PLAYER" di Logcat
    private val TAG = "DEBUG_PLAYER"

    // Data class helper (bisa di file terpisah atau nested)
    data class ResolvedSongUrl(
        val url: String,
        val source: String,
        val errorType: ErrorType = ErrorType.NONE
    )

    enum class ErrorType {
        NONE, NETWORK, NOT_FOUND, UNKNOWN
    }

    // --- MEMORY CACHE (Hilang saat app close) ---
    private val memoryUserCache = mutableMapOf<String, User>()
    private val memoryArtistCache = mutableMapOf<String, Artist>()
    private val memoryFollowCache = mutableMapOf<String, Boolean>() // Cache untuk status follow artist

    // --- LOGIC 1: RESOLVE URL (DIRECT TELEGRAM WITH RETRY) ---
    suspend fun getPlayableUrl(songId: String, title: String, telegramFileId: String?, fallbackUrl: String?, artistId: String?): ResolvedSongUrl {
        Log.d(TAG, "================================================================")
        Log.d(TAG, "🚀 [DIRECT MODE] Request Lagu: \"$title\"")

        // 1. Validasi File ID
        if (telegramFileId.isNullOrBlank()) {
            Log.i(TAG, "⚠️ [NON-TELEGRAM] Lagu ini pakai Link Direct/Supabase.")

            val directUrl = fallbackUrl ?: ""

            // Jika direct URL kosong, berarti error fatal (data korup)
            if (directUrl.isBlank()) {
                Log.e(TAG, "❌ [ERROR] Fallback URL juga kosong.")
                return ResolvedSongUrl("", "INVALID", ErrorType.NOT_FOUND)
            }

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
                             // Insert minimal logic handled below
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
        var lastException: Exception? = null

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

                        // 4.5 [NEW] Push ke Supabase
                        try {
                            Log.d(TAG, "🌍 [CONTRIBUTE] Upload link baru ke Supabase...")
                            val expiryInstant = Instant.ofEpochMilli(expiryTime)
                            SupabaseManager.client.from("songs").update(
                                mapOf(
                                    "telegram_direct_url" to finalUrl,
                                    "telegram_url_expires_at" to expiryInstant.toString()
                                )
                            ) {
                                filter { eq("id", songId) }
                            }
                            Log.d(TAG, "✅ [CONTRIBUTE] Link berhasil di-share ke user lain.")
                        } catch (e: Exception) {
                            Log.e(TAG, "⚠️ [CONTRIBUTE FAIL] Gagal upload link ke Supabase: ${e.message}")
                        }

                        Log.d(TAG, "   🔗 URL: $finalUrl")
                        break // 🛑 BERHASIL! KELUAR DARI LOOP
                    }
                } else {
                    Log.e(TAG, "❌ [TELEGRAM ERROR] Gagal dapat filePath. Code: ${response.code()}")
                    // Jika 404/400 dari Telegram API, berarti file ID invalid/dihapus -> NOT FOUND
                    if (response.code() == 404 || response.code() == 400) {
                         return ResolvedSongUrl("", "TELEGRAM_API_ERROR", ErrorType.NOT_FOUND)
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "❌ [NETWORK ERROR] Percobaan $attempt Gagal: ${e.message}")
                lastException = e
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

        // 6. Analisis Failure
        Log.e(TAG, "💀 [GIVE UP] Gagal total setelah $maxAttempts percobaan.")
        
        // Cek Fallback URL dulu
        if (!fallbackUrl.isNullOrBlank()) {
             Log.w(TAG, "⚠️ Menggunakan Fallback URL (Kualitas mungkin rendah/cache lama).")
             return ResolvedSongUrl(fallbackUrl, "FALLBACK")
        }

        // Jika tidak ada fallback, tentukan error type
        return if (lastException is java.net.UnknownHostException || 
                   lastException is java.net.SocketTimeoutException ||
                   lastException is java.io.IOException) {
            ResolvedSongUrl("", "NETWORK_ERROR", ErrorType.NETWORK)
        } else {
             ResolvedSongUrl("", "UNKNOWN_ERROR", ErrorType.UNKNOWN)
        }
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
    // --- LOGIC 3: LIKE / UNLIKE SONG (OFFLINE FIRST) ---
    suspend fun isSongLiked(songId: String, userId: String): Boolean {
        // "Single Source of Truth" adalah Local DB
        return musicDao.isSongLiked(songId).first()
    }

    suspend fun toggleLike(songId: String, userId: String, isLiked: Boolean) {
        // 1. Perbarui Local DB (Optimistic Update) - UI langsung berubah!
        if (isLiked) {
             // Kalau user mau UNLIKE -> Hapus dari DB
             musicDao.deleteLikedSong(songId)
             Log.d(TAG, "💔 [OFFLINE] Lagu dihapus dari Liked Songs (Local).")
        } else {
             // Kalau user mau LIKE -> Simpan ke DB
             musicDao.insertLikedSong(LikedSong(songId))
             Log.d(TAG, "❤️ [OFFLINE] Lagu ditambahkan ke Liked Songs (Local).")
        }

        // 2. Sinkron ke Cloud (Best Effort)
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
                Log.d(TAG, "☁️ [SYNC] Unlike berhasil disinkronkan ke server.")
            } else {
                // INSERT (Like)
                val data = mapOf("user_id" to userId, "song_id" to songId)
                SupabaseManager.client
                    .from("user_song_likes")
                    .insert(data)
                Log.d(TAG, "☁️ [SYNC] Like berhasil disinkronkan ke server.")
            }
        } catch (e: Exception) {
            // Jika gagal sync network, biarkan saja. 
            // Data lokal sudah benar. Nanti bisa ada worker sync ulang.
            Log.e(TAG, "⚠️ [SYNC FAIL] Gagal sync ke server (UI aman): ${e.message}")
            // Tidak perlu re-throw karena kita mau UI tetap konsisten dengan Local DB
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

    // --- LOGIC 7: FOLLOW ARTIST (WITH CACHE) ---
    // --- LOGIC 7: FOLLOW ARTIST (OFFLINE FIRST) ---
    suspend fun isArtistFollowed(userId: String, artistId: String): Boolean {
        // "Single Source of Truth" adalah Local DB
        return musicDao.isArtistFollowed(artistId).first()
    }

    suspend fun toggleFollowArtist(userId: String, artistId: String, isFollowed: Boolean) {
         // 1. Perbarui Local DB (Optimistic Update)
         if (isFollowed) {
             musicDao.deleteFollowedArtist(artistId)
             Log.d(TAG, "💔 [OFFLINE] Berhenti mengikuti artist (Local): $artistId")
         } else {
             musicDao.insertFollowedArtist(CachedFollowedArtist(artistId))
             Log.d(TAG, "❤️ [OFFLINE] Mulai mengikuti artist (Local): $artistId")
         }

        // 2. Sync ke Cloud (Best Effort)
        try {
            if (isFollowed) {
                // UNFOLLOW (DELETE)
                SupabaseManager.client
                    .from("artist_followers")
                    .delete {
                        filter {
                            eq("user_id", userId)
                            eq("artist_id", artistId)
                        }
                    }
                Log.d(TAG, "☁️ [SYNC] Unfollow sukses di server.")
            } else {
                // FOLLOW (INSERT)
                val data = mapOf("user_id" to userId, "artist_id" to artistId)
                SupabaseManager.client
                    .from("artist_followers")
                    .insert(data)
                Log.d(TAG, "☁️ [SYNC] Follow sukses di server.")
            }
            
            // Note: Memcache di-handle oleh Repo logic sebelumnya, tapi dengan SQLite kita ga butuh map lagi.
            // memoryFollowCache[artistId] = !isFollowed // (Bisa dihapus jika move total ke SQLite)

        } catch (e: Exception) {
            Log.e(TAG, "⚠️ [SYNC FAIL] Gagal sync follow ke server: ${e.message}")
        }
    }

    // --- LOGIC 8: GET AGGREGATED ARTIST DETAILS (HEADER) ---
    suspend fun getArtistDetails(userId: String, artistId: String): ArtistDetails? {
        Log.d(TAG, "📊 [ARTIST DETAILS] Meminta data lengkap artist: $artistId")

        return try {
            // 1. Ambil Info Artist (Name, Bio, Photo)
            val artistInfo = fetchArtistDetails(artistId) ?: return null

            // 2. Count Followers
            val followersCount = SupabaseManager.client
                .from("artist_followers")
                .select(columns = Columns.list()) {
                    count(io.github.jan.supabase.postgrest.query.Count.EXACT)
                    filter { eq("artist_id", artistId) }
                }.countOrNull() ?: 0L

            // 3. Count Total Songs & Sum Total Plays
            // Karena Supabase free tier ga support sum() langsung via API client mudah,
            // kita ambil kolom play_count semua lagunya lalu sum manual.
            // Jika lagunya ribuan, ini bad practice. Tapi untuk skala kecil ok.
            // Alternative: Buat RPC 'get_artist_stats(artist_id)' di backend.
            val songsStats = SupabaseManager.client
                .from("songs")
                .select(columns = Columns.list("play_count")) {
                    filter { eq("artist_id", artistId) }
                }
                .decodeList<JsonObject>() // Decode partial

            val totalSongs = songsStats.size
            val totalPlays = songsStats.sumOf {
                it["play_count"].toString().toLongOrNull() ?: 0L
            }

            // 4. Check isFollowed
            val isFollowed = isArtistFollowed(userId, artistId)

            Log.d(TAG, "📊 [ARTIST STATS] $followersCount Follows, $totalSongs Songs, $totalPlays Plays")

            ArtistDetails(
                id = artistInfo.id,
                name = artistInfo.name,
                description = artistInfo.description,
                photoUrl = artistInfo.photoUrl,
                followerCount = followersCount,
                totalPlays = totalPlays,
                totalSongs = totalSongs,
                isFollowed = isFollowed
            )

        } catch (e: Exception) {
            Log.e(TAG, "❌ [ARTIST DETAILS ERROR] Gagal ambil aggregated details: ${e.message}")
            null
        }
    }

    // --- LOGIC 9: GET ARTIST SONGS WITH PAGINATION ---
    suspend fun getSongsByArtistPagination(
        artistId: String, 
        limit: Int = 20, 
        offset: Int = 0 
    ): List<SongWithArtist> {
        return try {
            Log.d(TAG, "🎵 [ARTIST SONGS] Fetching songs for $artistId (Limit: $limit, Offset: $offset)")
            
            val songs = SupabaseManager.client
                .from("songs")
                .select(columns = Columns.list(
                    "id", "title", "artist_id", "cover_url", "audio_url", "duration_ms", "created_at", "lyrics", "lyrics_updated_at", "telegram_audio_file_id", "canvas_url"
                )) {
                    filter {
                        eq("artist_id", artistId)
                    }
                    order("created_at", order = Order.DESCENDING) // Lagu terbaru dulu
                    range(offset.toLong(), (offset + limit - 1).toLong())
                }.decodeList<Song>()
            
            // Kita butuh Info Artist untuk setiap SongWithArtist.
            // Efisiensi: Fetch Artist Sekali saja karena sama semua.
            val artist = fetchArtistDetails(artistId) ?: return emptyList()
            
            // Map to SongWithArtist
            songs.map { song ->
                SongWithArtist(song, artist)
            }
        
        } catch (e: Exception) {
            Log.e(TAG, "❌ [ARTIST SONGS ERROR] Gagal ambil lagu artist: ${e.message}")
            emptyList()
        }
    }

    // --- LOGIC 10: PLAYBACK QUEUE PERSISTENCE ---
    suspend fun savePlaybackQueue(songs: List<Song>) {
        try {
            if (songs.isEmpty()) {
                musicDao.clearPlaybackQueue()
                return
            }
            // Konversi ke Entity dengan urutan
            val queueEntities = songs.mapIndexed { index, song ->
                PlaybackQueueEntity(
                    songId = song.id,
                    listOrder = index
                )
            }
            musicDao.insertPlaybackQueue(queueEntities)
            Log.d(TAG, "💾 [QUEUE] Saved ${songs.size} songs to playback queue.")
        } catch (e: Exception) {
             Log.e(TAG, "❌ [QUEUE ERROR] Failed to save queue: ${e.message}")
        }
    }

    suspend fun restorePlaybackQueue(): List<Song> {
        try {
            val queueEntities = musicDao.getPlaybackQueue()
            if (queueEntities.isEmpty()) return emptyList()

            // Ambil ID lagu dari queue
            val songIds = queueEntities.map { it.songId }
            
            // Fetch detail lagu (Coba cache dulu, kalau gak ada fetch dari server)
            // Kalo mau cepet, kita asumsi lagu di queue pasti pernah diputar/dicache
            // Jadi kita ambil dari CachedSong di DAO satu-satu atau bulk
            
            // Optimasi: Bulk Fetch from Cache
            val cachedSongs = songIds.mapNotNull { id ->
                 musicDao.getSongById(id)
            }
            
            // Kembalikan sebagai list Song (convert dari CachedSong)
            // Perhatikan urutannya harus sesuai dengan queueEntities (ORDER BY listOrder)
            // Jadi kita mapping balik berdasarkan ID
            
            val songMap = cachedSongs.associateBy { it.id }
            
            val restoredSongs = queueEntities.mapNotNull { entity ->
                songMap[entity.songId]?.toSong()
            }
            
            Log.d(TAG, "♻️ [QUEUE] Restored ${restoredSongs.size} songs from playback queue.")
            return restoredSongs

        } catch (e: Exception) {
            Log.e(TAG, "❌ [QUEUE ERROR] Failed to restore queue: ${e.message}")
            return emptyList()
        }
    }

    private fun CachedSong.toSong(): Song {
        return Song(
            id = this.id,
            title = this.title,
            lyrics = this.lyrics,
            coverUrl = this.coverUrl,
            canvasUrl = this.canvasUrl,
            uploaderUserId = this.uploaderUserId,
            artistId = this.artistId,
            telegramFileId = this.telegramFileId,
            telegramDirectUrl = this.telegramDirectUrl,
            lyricsUpdatedAt = this.lyricsUpdatedAt,
            language = this.language,
            moods = this.moods,
            featuredArtists = this.featuredArtists
        )
    }
}