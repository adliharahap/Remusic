package com.example.remusic.data.repository

// Data Models
import android.util.Log
import com.example.remusic.data.SupabaseManager
import com.example.remusic.data.repository.lyrics.LyricsRepository
import com.example.remusic.data.repository.queue.QueueRepository

import com.example.remusic.data.local.MusicDao
import com.example.remusic.data.local.entity.CachedFollowedArtist
import com.example.remusic.data.local.entity.CachedSong
import com.example.remusic.data.local.entity.LikedSong
import com.example.remusic.data.local.entity.PlaybackQueueEntity
import com.example.remusic.data.model.Artist
import com.example.remusic.data.model.ArtistDetails
import com.example.remusic.data.model.Song
import com.example.remusic.data.model.SongWithArtist
import com.example.remusic.data.model.User
import com.example.remusic.data.model.Playlist
import com.example.remusic.data.network.TelegramRetrofit
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.query.filter.*
import java.time.Instant
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

class MusicRepository(private val musicDao: MusicDao) {

val lyrics = com.example.remusic.data.repository.lyrics.LyricsRepository(musicDao)
val queue = com.example.remusic.data.repository.queue.QueueRepository(musicDao)
val social = com.example.remusic.data.repository.social.UserSocialRepository(musicDao, ::upsertYouTubeSong)

    // Proxy for fetchArtistsByIds to avoid breaking other ViewModels
    suspend fun fetchArtistsByIds(artistIds: List<String>): List<Artist> = social.fetchArtistsByIds(artistIds)


    // --- SYNC ENGINE: DOWNLOAD DATA USER (Likes & Follows) ---
    suspend fun synchronizeUserData(userId: String) {
        Log.d("DEBUG_PLAYER", "🔄 [SYNC] Memulai sinkronisasi data user (Likes & Follows)...")
        try {
            // 1. Sync Liked Songs
            val remoteLikes =
                    SupabaseManager.client
                            .from("user_song_likes")
                            .select(columns = Columns.list("song_id", "created_at")) {
                                filter { eq("user_id", userId) }
                            }
                            .decodeList<JsonObject>()

            val songIds = remoteLikes.map { it["song_id"].toString().replace("\"", "") }

            // Ambil mapping UUID -> youtube_id dari tabel songs untuk translate balik ke ID YouTube (11 char)
            val youtubeMapping = if (songIds.isNotEmpty()) {
                try {
                    SupabaseManager.client.from("songs")
                        .select(columns = Columns.list("id", "youtube_id")) {
                            filter { isIn("id", songIds) }
                        }
                        .decodeList<JsonObject>()
                        .associate {
                            val id = it["id"]?.jsonPrimitive?.content ?: ""
                            val ytId = it["youtube_id"]?.jsonPrimitive?.contentOrNull
                            id to ytId
                        }
                } catch (e: Exception) {
                    Log.w("DEBUG_PLAYER", "⚠️ [SYNC] Gagal ambil mapping youtube_id: ${e.message}")
                    emptyMap<String, String?>()
                }
            } else emptyMap()

            // Clear local likes first for a fresh sync
            musicDao.clearLikedSongs()

            // Bulk Insert to SQLite
            remoteLikes.forEach {
                val uuid = it["song_id"]?.jsonPrimitive?.content ?: ""
                if (uuid.isBlank()) return@forEach
                
                // Jika UUID ini adalah shadow entry untuk YouTube, gunakan youtube_id aslinya untuk Local DB
                val effectiveId = youtubeMapping[uuid] ?: uuid
                musicDao.insertLikedSong(LikedSong(effectiveId))
            }
            Log.d("DEBUG_PLAYER", "✅ [SYNC] ${remoteLikes.size} liked songs tersimpan di Local (dengan YouTube ID translation).")

            // 2. Sync Followed Artists
            val remoteFollows =
                    SupabaseManager.client
                            .from("artist_followers")
                            .select(columns = Columns.list("artist_id", "created_at")) {
                                filter { eq("user_id", userId) }
                            }
                            .decodeList<JsonObject>()

            remoteFollows.forEach {
                val artistId = it["artist_id"].toString().replace("\"", "")
                musicDao.insertFollowedArtist(CachedFollowedArtist(artistId))
            }
            Log.d(
                    "DEBUG_PLAYER",
                    "✅ [SYNC] ${remoteFollows.size} artists followed tersimpan di Local."
            )
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
        NONE,
        NETWORK,
        NOT_FOUND,
        UNKNOWN
    }

    // --- MEMORY CACHE (Hilang saat app close) ---
    private val memoryFollowCache =
            mutableMapOf<String, Boolean>() // Cache untuk status follow artist
            

    fun getAllLikedSongs(): kotlinx.coroutines.flow.Flow<List<CachedSong>> {
        return musicDao.getAllLikedSongs()
    }

    // --- LOGIC 1: RESOLVE URL (MULTI-SOURCE: youtube / telegram / mp3 / offline) ---
    suspend fun getPlayableUrl(
            songId: String,
            title: String,
            telegramFileId: String?,
            fallbackUrl: String?,
            artistId: String?,
            source: String? = null   // "youtube" | "telegram" | "mp3" | "offline" | null (auto)
    ): ResolvedSongUrl {
        Log.d(TAG, "================================================================")
        Log.d(TAG, "🚀 [RESOLVE] \"$title\" | source=$source | telegramFileId=$telegramFileId")

        // Deteksi source secara otomatis jika tidak eksplisit
        val resolvedSource = when {
            source == "offline" -> "offline"
            source == "youtube" || source == "youtube_video" -> "youtube"
            source == "mp3" -> "mp3"
            source == "telegram" -> "telegram"
            // Auto-detect (backward compatible):
            !telegramFileId.isNullOrBlank() -> "telegram"       // Ada telegram file ID → telegram
            fallbackUrl?.endsWith(".mp3") == true -> "mp3"      // URL berakhiran .mp3
            else -> "youtube"                                    // Default: coba YouTube
        }
        Log.d(TAG, "📍 Resolved source: $resolvedSource")

        // ── OFFLINE ──────────────────────────────────────────────────────────────
        if (resolvedSource == "offline") {
            val cached = musicDao.getSongById(songId)
            val offlineUrl = cached?.telegramDirectUrl ?: fallbackUrl ?: ""
            if (offlineUrl.isNotBlank()) {
                Log.d(TAG, "✅ [OFFLINE] Menggunakan file lokal: $offlineUrl")
                return ResolvedSongUrl(offlineUrl, "OFFLINE")
            }
            Log.w(TAG, "⚠️ [OFFLINE] File lokal tidak ditemukan, fallback ke youtube")
            // fallthrough ke youtube
        }

        // ── MP3 LINK ─────────────────────────────────────────────────────────────
        if (resolvedSource == "mp3" && !fallbackUrl.isNullOrBlank()) {
            Log.d(TAG, "✅ [MP3] Menggunakan direct MP3 link: $fallbackUrl")
            return ResolvedSongUrl(fallbackUrl, "MP3_LINK")
        }

        // ── TELEGRAM ─────────────────────────────────────────────────────────────
        if (resolvedSource == "telegram" && !telegramFileId.isNullOrBlank()) {
            return resolveTelegramUrl(songId, title, telegramFileId, fallbackUrl, artistId)
        }

        // ── YOUTUBE ──────────────────────────────────────────────────────────────
        Log.i(TAG, "🎵 [YOUTUBE] Mencoba stream YouTube untuk videoId: $songId")
        var ytStreamUrl = ""
        var ytExpiresInSeconds = 0

        try {
            val playbackResult = com.example.remusic.utils.YTPlayerUtils.playerResponseForPlayback(
                videoId = songId,
                playlistId = null,
                audioQuality = com.example.remusic.utils.AudioQuality.AUTO,
                connectivityManager = null
            )
            val playbackData = playbackResult.getOrNull()
            ytStreamUrl = playbackData?.streamUrl ?: ""
            ytExpiresInSeconds = playbackData?.streamExpiresInSeconds ?: 0
            if (ytStreamUrl.isNotBlank()) {
                Log.d(TAG, "✅ [YOUTUBE] Berhasil mendapatkan stream URL!")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ [YOUTUBE ERROR] Gagal fetch dari YouTube: ${e.message}")
        }

        val finalUrl = ytStreamUrl.ifBlank { fallbackUrl ?: "" }

        if (finalUrl.isBlank()) {
            Log.e(TAG, "❌ [ERROR] Semua sumber gagal untuk lagu: $title")
            return ResolvedSongUrl("", "INVALID", ErrorType.NOT_FOUND)
        }

        // Cache URL hasil YouTube ke DB
        val expiryTime = if (ytStreamUrl.isNotBlank() && ytExpiresInSeconds > 0)
            System.currentTimeMillis() + (ytExpiresInSeconds * 1000L)
        else Long.MAX_VALUE

        val existingData = musicDao.getSongById(songId)
        if (existingData != null) {
            musicDao.updateSongUrl(songId, finalUrl, expiryTime, System.currentTimeMillis())
        } else {
            musicDao.insertSong(CachedSong(
                id = songId, title = title, uploaderUserId = null, artistName = "Unknown",
                coverUrl = null, lyrics = null, telegramFileId = null,
                telegramDirectUrl = finalUrl, urlExpiryTime = expiryTime,
                lastPlayedAt = System.currentTimeMillis(), artistId = artistId,
                source = "youtube"
            ))
        }

        val sourceLabel = if (ytStreamUrl.isNotBlank()) "YOUTUBE" else "FALLBACK"
        return ResolvedSongUrl(finalUrl, sourceLabel)
    }

    // ── Helper: Resolve Telegram URL dengan retry ─────────────────────────────
    private suspend fun resolveTelegramUrl(
            songId: String,
            title: String,
            telegramFileId: String,
            fallbackUrl: String?,
            artistId: String?
    ): ResolvedSongUrl {

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
            val remoteSong =
                    SupabaseManager.client
                            .from("songs")
                            .select(
                                    columns =
                                            Columns.list(
                                                    "telegram_direct_url",
                                                    "telegram_url_expires_at"
                                            )
                            ) { filter { eq("id", songId) } }
                            .decodeSingleOrNull<Song>()

            if (remoteSong != null && !remoteSong.telegramDirectUrl.isNullOrBlank()) {
                val remoteExpiry = remoteSong.telegramUrlExpiresAt

                // Parse expires_at (String ISO-8601) to Millis
                if (remoteExpiry != null) {
                    val remoteExpiryMs =
                            try {
                                Instant.parse(remoteExpiry).toEpochMilli()
                            } catch (_: Exception) {
                                0L
                            }
                    if (currentTime < remoteExpiryMs) {
                        Log.d(
                                TAG,
                                "⚡ [SUPABASE HIT] Link Fresh ditemukan di server! (Valid sampai: $remoteExpiry)"
                        )

                        // Update Local Cache
                        if (cachedSong != null) {
                            musicDao.updateSongUrl(
                                    songId,
                                    remoteSong.telegramDirectUrl,
                                    remoteExpiryMs,
                                    currentTime
                            )
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
                            musicDao.updateSongUrl(
                                    songId,
                                    finalUrl,
                                    expiryTime,
                                    System.currentTimeMillis()
                            )
                        } else {
                            val newCacheData =
                                    CachedSong(
                                            id = songId,
                                            title = title,
                                            uploaderUserId = null,
                                            artistName = "Unknown",
                                            coverUrl = null,
                                            lyrics = null,
                                            telegramFileId = telegramFileId,
                                            telegramDirectUrl = finalUrl,
                                            urlExpiryTime = expiryTime,
                                            lastPlayedAt = System.currentTimeMillis(),
                                            artistId = artistId
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
                                                    "telegram_url_expires_at" to
                                                            expiryInstant.toString()
                                            )
                                    ) { filter { eq("id", songId) } }
                            Log.d(TAG, "✅ [CONTRIBUTE] Link berhasil di-share ke user lain.")
                        } catch (e: Exception) {
                            Log.e(
                                    TAG,
                                    "⚠️ [CONTRIBUTE FAIL] Gagal upload link ke Supabase: ${e.message}"
                            )
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
                        lastException is java.io.IOException
        ) {
            ResolvedSongUrl("", "NETWORK_ERROR", ErrorType.NETWORK)
        } else {
            ResolvedSongUrl("", "UNKNOWN_ERROR", ErrorType.UNKNOWN)
        }
    }

    // --- LOGIC 2: FETCH & SAVE DETAILS (LYRICS) ---
    suspend fun getFullSongDetails(songId: String, forceFetch: Boolean = false, initialTitle: String? = null, initialArtist: String? = null): CachedSong? {
        Log.d(TAG, "🔍 [DETAILS] Meminta detail lengkap. ID: $songId, Title: $initialTitle, Artist: $initialArtist, Force: $forceFetch")

        // 1. Cek di SQLite dulu
        val cached = musicDao.getSongById(songId)
        
        val isTelegramSong = cached?.source == "telegram" || cached?.telegramFileId != null
        val hasMissingMetadata = isTelegramSong && cached?.language.isNullOrBlank()
        
        // --- PRIORITAS: Jika sudah ada lirik SYNCED di local dan METADATA LENGKAP, LANGSUNG PAKAI! ---
        // Ini mengatasi latency (ngak perlu nunggu network) dan mencegah lirik tertimpa plain text.
        if (!forceFetch && cached != null && lyrics.isLyricsSynced(cached.lyrics) && !hasMissingMetadata) {
            Log.d(TAG, "🚀 [LOCAL] Lirik SYNCED & Metadata lengkap ditemukan. Mengembalikan instan.")
            return cached
        }

        // 1.5 Handle lagu YouTube khusus: tidak perlu Supabase, fetch lirik via Innertube
        val isYouTubeId = songId.matches(Regex("^[a-zA-Z0-9_-]{11}$"))
        if (cached?.source == "youtube" || isYouTubeId) {
            var title = initialTitle ?: cached?.title ?: ""
            var artist = initialArtist ?: cached?.artistName ?: ""

            // --- BARU: Resolusi Metadata jika masih kosong (Lagu baru dari YouTube) ---
            if (title.isBlank() || artist.isBlank()) {
                try {
                    Log.d(TAG, "🔍 [METADATA] Metadata kosong, fetch dari Innertube...")
                    val endpoint = com.metrolist.innertube.models.WatchEndpoint(videoId = songId)
                    val nextResult = com.metrolist.innertube.YouTube.next(endpoint).getOrNull()
                    title = nextResult?.title ?: title
                    artist = nextResult?.items?.firstOrNull()?.artists?.firstOrNull()?.name ?: artist
                    Log.d(TAG, "✅ [METADATA] Resolved: $title - $artist")
                } catch (e: Exception) {
                    Log.e(TAG, "❌ [METADATA] Gagal resolve metadata: ${e.message}")
                }
            }

            val lyricsInCache = cached?.lyrics
            val needsSyncedLyrics = lyricsInCache.isNullOrBlank() || !lyrics.isLyricsSynced(lyricsInCache) || lyricsInCache.contains("tidak tersedia", ignoreCase = true)

            if (needsSyncedLyrics) {
                try {
                    // --- STEP A: SUPABASE HUB (Internal / Community Database) ---
                    // Prioritas 1 karena paling cepat
                    try {
                        val cleanTitle = sanitizeSearchQuery(title)
                        val cleanArtist = sanitizeSearchQuery(artist)
                        Log.d(TAG, "☁️ [SUPABASE HUB] Mencari di lyrics_hub untuk: $cleanTitle - $cleanArtist")
                        val hubData = SupabaseManager.client.from("lyrics_hub")
                            .select() { filter { eq("id", songId) } }
                            .decodeSingleOrNull<Map<String, String>>()
                        
                        val hubLyrics = hubData?.get("lyrics")
                        if (!hubLyrics.isNullOrBlank()) {
                            Log.d(TAG, "✅ [SUPABASE HUB] Lirik ditemukan di database bersama.")
                            
                            // Simpan ke local cache jika belum ada
                            if (cached?.lyrics.isNullOrBlank()) {
                                musicDao.updateLyrics(songId, hubLyrics)
                            }

                            return if (cached != null) {
                                cached.copy(lyrics = hubLyrics)
                            } else {
                                CachedSong(id = songId, title = title, artistName = artist, lyrics = hubLyrics, source = "youtube", uploaderUserId = null, telegramFileId = null, telegramDirectUrl = null, coverUrl = null)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "⚠️ [SUPABASE HUB FAIL] ${e.message}")
                    }

                    // --- STEP B: LRCLIB (Fallback untuk lirik SYNCED) ---
                    if (title.isNotBlank() && artist.isNotBlank()) {
                        var lrcAttempt = 1
                        val maxLrcAttempts = 2
                        var lrcSuccess = false
                        
                        while (lrcAttempt <= maxLrcAttempts && !lrcSuccess) {
                            try {
                                val cleanTitle = sanitizeSearchQuery(title)
                                val cleanArtist = sanitizeSearchQuery(artist)
                                Log.d(TAG, "🔍 [LRCLIB] Mencari lirik (Percobaan ke-$lrcAttempt): $cleanTitle - $cleanArtist")
                                val lrcResults = com.example.remusic.data.network.LrcLibRetrofit.api.searchLyrics(cleanTitle, cleanArtist)
                                val bestMatch = lrcResults.firstOrNull()
                                
                                val syncedLyrics = bestMatch?.syncedLyrics
                                val plainLyrics = bestMatch?.plainLyrics
                                val isInstrumental = bestMatch?.instrumental == true
                                
                                if (isInstrumental || !syncedLyrics.isNullOrBlank()) {
                                    val finalLrc = if (isInstrumental) "[Instrumental]" else syncedLyrics!! + "\n\nlyrics from lrclib"
                                    Log.d(TAG, "✅ [LRCLIB] Berhasil mendapatkan lirik SYNCED.")
                                    
                                    val currentLyrics = cached?.lyrics
                                    val shouldSave = when {
                                        isInstrumental -> true
                                        lyrics.isLyricsSynced(finalLrc) -> true
                                        currentLyrics.isNullOrBlank() -> true
                                        !lyrics.isLyricsSynced(currentLyrics) -> true
                                        else -> false
                                    }

                                    if (shouldSave) {
                                        musicDao.updateLyrics(songId, finalLrc)
                                        lyrics.pushLyricsToLyricsHub(songId, title, artist, finalLrc, "lrclib")
                                    }
                                    
                                    return if (cached != null) {
                                        cached.copy(lyrics = finalLrc)
                                    } else {
                                        CachedSong(id = songId, title = title, artistName = artist, lyrics = finalLrc, source = "youtube", uploaderUserId = null, telegramFileId = null, telegramDirectUrl = null, coverUrl = null)
                                    }
                                } else if (!plainLyrics.isNullOrBlank()) {
                                    Log.w(TAG, "⚠️ [LRCLIB] Hanya ketemu PLAIN lyrics. Melanjutkan ke fallback lain.")
                                    lrcSuccess = true 
                                } else {
                                    Log.w(TAG, "⚠️ [LRCLIB] Tidak ditemukan lirik di LRCLIB.")
                                    lrcSuccess = true
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "❌ [LRCLIB] Percobaan ke-$lrcAttempt Gagal: ${e.message}")
                                if (lrcAttempt < maxLrcAttempts) delay(2000)
                                lrcAttempt++
                            }
                        }
                    }

                    // --- STEP C: FALLBACK KE YOUTUBE API ---
                    Log.d(TAG, "🎵 [YOUTUBE] Meminta lirik via YouTube API untuk ID: $songId")
                    // 1. Cek Synced Lyrics (Transcript)
                    var ytLyrics = com.metrolist.innertube.YouTube.transcript(songId).getOrNull()
                    val lyricsType = if (!ytLyrics.isNullOrBlank()) "SYNCED (Transcript)" else "None"
                    
                    // 2. Cek Plain Lyrics jika Synced gagal
                    if (ytLyrics.isNullOrBlank()) {
                        val endpoint = com.metrolist.innertube.models.WatchEndpoint(videoId = songId)
                        val nextResult = com.metrolist.innertube.YouTube.next(endpoint).getOrNull()
                        val lyricsEndpoint = nextResult?.lyricsEndpoint
                        if (lyricsEndpoint != null) {
                            ytLyrics = com.metrolist.innertube.YouTube.lyrics(lyricsEndpoint).getOrNull()
                            if (!ytLyrics.isNullOrBlank()) Log.d(TAG, "📜 [YT LYRICS] Synced gagal, menggunakan PLAIN lyrics.")
                        }
                    }

                    // Filter pesan "tidak tersedia" dari YouTube
                    val invalidKeywords = listOf("tidak tersedia", "not available", "no lyrics", "lirik belum ada")
                    val finalLyrics = if (!ytLyrics.isNullOrBlank() && invalidKeywords.none { ytLyrics!!.contains(it, ignoreCase = true) }) {
                        ytLyrics
                    } else null

                    if (finalLyrics != null) {
                        Log.d(TAG, "✅ [YT LYRICS] Berhasil! Type: $lyricsType")
                        
                        val currentLyrics = cached?.lyrics
                        val shouldSave = when {
                            lyrics.isLyricsSynced(finalLyrics) -> true
                            currentLyrics.isNullOrBlank() -> true
                            !lyrics.isLyricsSynced(currentLyrics) -> true
                            else -> false
                        }

                        if (shouldSave) {
                            Log.d(TAG, "💾 [YT LYRICS] Menyimpan lirik ke DB.")
                            musicDao.updateLyrics(songId, finalLyrics)
                        }
                        
                        return if (cached != null) {
                            cached.copy(lyrics = finalLyrics)
                        } else {
                            CachedSong(id = songId, title = title, artistName = artist, lyrics = finalLyrics, source = "youtube", uploaderUserId = null, telegramFileId = null, telegramDirectUrl = null, coverUrl = null)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "❌ [FALLBACKS ERROR] Gagal fetch lirik: ${e.message}")
                }
            }
            if (cached != null) return cached
        }


        // Kita tidak langsung percaya cache. Kita cek dulu ke Supabase:
        // "Apakah lirik di server lebih baru dari yang saya punya?"

        var shouldFetchFullDetails = false

        try {
            // A. Ambil HANYA timestamp dari server (Hemat Kuota)
            Log.d(TAG, "☁️ [VERSION CHECK] Mengecek versi lirik dan metadata ke Supabase untuk ID: $songId")
            val remoteVersion =
                    SupabaseManager.client
                            .from("songs")
                            .select(columns = Columns.list("lyrics_updated_at", "language", "moods")) {
                                filter { 
                                    if (isYouTubeId) eq("youtube_id", songId)
                                    else eq("id", songId)
                                }
                            }
                            .decodeSingleOrNull<Song>()

            val remoteTimestamp = remoteVersion?.lyricsUpdatedAt
            val localTimestamp = cached?.lyricsUpdatedAt
            val remoteLanguage = remoteVersion?.language
            val missingLocalLanguage = cached?.language.isNullOrBlank()

            if (remoteTimestamp != null || remoteLanguage != null) {
                if (remoteTimestamp != null && localTimestamp == null) {
                    // KASUS 1: Lokal belum ada timestamp (lagu baru di-cache)
                    Log.d(TAG, "🆕 [NEW LYRICS] Lirik lokal belum ada versinya. Download...")
                    shouldFetchFullDetails = true
                } else if (remoteTimestamp != null && localTimestamp != remoteTimestamp) {
                    // KASUS 2: Remote beda (lebih baru)
                    Log.d(
                            TAG,
                            "♻️ [UPDATE DETECTED] Server: $remoteTimestamp vs Lokal: $localTimestamp"
                    )
                    shouldFetchFullDetails = true
                } else if (missingLocalLanguage && remoteLanguage != null && cached != null) {
                    // KASUS 3: Lirik up-to-date, TAPI metadata bahasa kosong. Kita tambal!
                    Log.d(TAG, "🔄 [MISSING META] Lirik up-to-date tapi language bolong. Menyimpan metadata baru: $remoteLanguage")
                    musicDao.updateSongDetails(
                        id = songId,
                        title = cached.title,
                        lyrics = cached.lyrics,
                        lyricsUpdatedAt = cached.lyricsUpdatedAt,
                        cover = cached.coverUrl,
                        canvas = cached.canvasUrl,
                        uploaderId = cached.uploaderUserId,
                        language = remoteLanguage,
                        moods = remoteVersion.moods ?: emptyList(),
                        artistId = cached.artistId,
                        featuredArtists = cached.featuredArtists
                    )
                    val patchedCache = cached.copy(language = remoteLanguage, moods = remoteVersion.moods ?: emptyList())
                    return patchedCache
                } else {
                    // KASUS 4: Timestamp sama dan metadata aman -> AMAN!
                    Log.d(
                            TAG,
                            "✅ [FRESH] Lirik lokal sudah paling update ($localTimestamp) dan metadata lengkap. Skip download."
                    )
                    shouldFetchFullDetails = false
                }
            } else {
                // Remote ga punya timestamp (karena null/query salah?), fallback logic lama
                Log.w(TAG, "⚠️ [NO VERSION] Server tidak kirim timestamp. Cek manual.")
                if (cached?.lyrics.isNullOrBlank()) shouldFetchFullDetails = true
            }
        } catch (e: Exception) {
            Log.e(
                    TAG,
                    "⚠️ [VERSION CHECK FAIL] Gagal cek versi: ${e.message}. Fallback ke logic lama."
            )
            // Kalau gagal cek versi (internet putus?), gunakan logic lama:
            // Fetch kalau lirik di cache kosong.
            if (cached?.lyrics.isNullOrBlank()) shouldFetchFullDetails = true
        }

        // B. Keputusan Akhir
        if (!shouldFetchFullDetails && cached != null && !cached.lyrics.isNullOrBlank()) {
            return cached
        }

        // 2. Fetch Full Data dari Supabase (Jika Perlu)
        Log.d(TAG, "☁️ [SUPABASE] Mengambil Data Lengkap (Lirik & Metadata)...")
        try {
            val supabaseSong =
                    SupabaseManager.client
                            .from("songs")
                            .select(
                                    columns =
                                            Columns.list(
                                                    "id",
                                                    "title",
                                                    "lyrics",
                                                    "lyrics_updated_at",
                                                    "cover_url",
                                                    "canvas_url",
                                                    "uploader_user_id",
                                                    "language",
                                                    "moods",
                                                    "artist_id",
                                                    "featured_artists",
                                                    "youtube_id",
                                                    "source"
                                            )
                            ) { 
                                filter { 
                                    if (isYouTubeId) eq("youtube_id", songId)
                                    else eq("id", songId)
                                }
                            }
                            .decodeSingleOrNull<Song>()

            if (supabaseSong != null) {
                // 3. Update SQLite dengan data lengkap
                Log.d(TAG, "📥 [DATA RECEIVED] Data diterima. Merging ke SQLite...")
                Log.d(TAG, "   -> Featured Artists (Server): ${supabaseSong.featuredArtists}")
                Log.d(TAG, "   -> Artist ID: ${supabaseSong.artistId}")

                val oldData = musicDao.getSongById(songId)

                if (oldData != null) {
                    // 🔥 JANGAN TIMPA lirik lokal jika yang lokal sudah SYNCED tapi server cuma PLAIN/NULL
                    val finalLyricsToSave = if (lyrics.isLyricsSynced(oldData.lyrics) && !lyrics.isLyricsSynced(supabaseSong.lyrics)) {
                        Log.d(TAG, "⚠️ [MERGE] Menjaga lirik SYNCED lokal. Skip timpa dengan lirik server.")
                        oldData.lyrics
                    } else {
                        supabaseSong.lyrics
                    }

                    // PARTIAL UPDATE
                    musicDao.updateSongDetails(
                            id = songId,
                            title = supabaseSong.title,
                            lyrics = finalLyricsToSave,
                            lyricsUpdatedAt = supabaseSong.lyricsUpdatedAt, // Simpan versi baru
                            cover = supabaseSong.coverUrl,
                            canvas = supabaseSong.canvasUrl,
                            uploaderId = supabaseSong.uploaderUserId,
                            language = supabaseSong.language,
                            moods = supabaseSong.moods ?: emptyList(),
                            artistId = supabaseSong.artistId,
                            featuredArtists = supabaseSong.featuredArtists
                    )
                    Log.d(TAG, "💾 [DETAILS SAVED] Detail & Lirik diperbarui.")

                    // Return object gabungan
                    return oldData.copy(
                            title = supabaseSong.title,
                            lyrics = finalLyricsToSave,
                            lyricsUpdatedAt = supabaseSong.lyricsUpdatedAt,
                            coverUrl = supabaseSong.coverUrl,
                            canvasUrl = supabaseSong.canvasUrl,
                            uploaderUserId = supabaseSong.uploaderUserId,
                            language = supabaseSong.language,
                            moods = supabaseSong.moods ?: emptyList(),
                            artistId = supabaseSong.artistId,
                            featuredArtists = supabaseSong.featuredArtists
                    )
                } else {
                    // INSERT BARU
                    val newData =
                            CachedSong(
                                    id = songId,
                                    title = supabaseSong.title,
                                    uploaderUserId = supabaseSong.uploaderUserId,
                                    artistName = "Unknown",
                                    featuredArtists = supabaseSong.featuredArtists,
                                    coverUrl = supabaseSong.coverUrl,
                                    canvasUrl = supabaseSong.canvasUrl,
                                    lyrics = supabaseSong.lyrics,
                                    lyricsUpdatedAt = supabaseSong.lyricsUpdatedAt,
                                    telegramFileId = supabaseSong.telegramFileId,
                                    telegramDirectUrl = null,
                                    language = supabaseSong.language,
                                    moods = supabaseSong.moods ?: emptyList(),
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








    // --- LOGIC 3: LIKE / UNLIKE SONG (OFFLINE FIRST) ---






    /**
     * [SOLUTION 1] Shadow Entry:
     * Menyimpan metadata lagu YouTube ke tabel 'songs' agar mendapatkan UUID.
     * UUID ini nantinya digunakan untuk relasi di tabel user_song_likes dan playlist_songs.
     */
    /**
     * Membuat atau mengambil UUID Artist YouTube di tabel 'artists'
     */
    suspend fun upsertYouTubeArtist(name: String, youtubeId: String, photoUrl: String? = null): String? {
        return try {
            val data = buildJsonObject {
                put("name", name)
                put("youtube_id", youtubeId)
                if (photoUrl != null) put("photo_url", photoUrl)
                put("source", "youtube")
            }

            val response = SupabaseManager.client.from("artists")
                .upsert(data) {
                    onConflict = "youtube_id"
                    select(Columns.list("id"))
                }
                .decodeSingle<JsonObject>()
            
            response["id"]?.jsonPrimitive?.content
        } catch (e: Exception) {
            Log.e(TAG, "❌ [UPSERT ARTIST YT] Gagal: ${e.message}")
            null
        }
    }

    suspend fun upsertYouTubeSong(song: Song, artistName: String? = null, artistPhoto: String? = null): String? {
        return try {
            // 1. Pastikan Artist YouTube tersimpan di tabel 'artists' dulu
            var finalArtistId: String? = null
            if (!song.artistId.isNullOrBlank()) {
                finalArtistId = upsertYouTubeArtist(
                    name = artistName ?: song.featuredArtists.firstOrNull() ?: "Unknown Artist",
                    youtubeId = song.artistId,
                    photoUrl = artistPhoto
                )
            }

            // 2. Simpan data lagu dengan artist_id yang baru didapat
            val data = buildJsonObject {
                put("title", song.title)
                put("cover_url", song.coverUrl)
                put("source", song.source ?: "youtube")
                put("youtube_id", song.id)
                put("duration_ms", song.durationMs)
                if (finalArtistId != null) {
                    put("artist_id", finalArtistId)
                }
                
                // Simpan featured artists (penyanyi tambahan)
                // Jika artistName sama dengan artis utama, jangan masukkan ke featured_artists
                val additionalArtists = if (artistName != null) {
                    song.featuredArtists.filter { it.trim().lowercase() != artistName.trim().lowercase() }
                } else {
                    song.featuredArtists
                }
                
                put("featured_artists", JsonArray(additionalArtists.map { JsonPrimitive(it) }))
            }
            
            val response = SupabaseManager.client.from("songs")
                .upsert(data) {
                    onConflict = "youtube_id"
                    select(Columns.list("id"))
                }
                .decodeSingle<JsonObject>()
            
            val uuid = response["id"]?.jsonPrimitive?.content
            Log.d(TAG, "✅ [UPSERT YT] Berhasil. YT_ID: ${song.id} -> SongUUID: $uuid, ArtistUUID: $finalArtistId")
            uuid
        } catch (e: Exception) {
            Log.e(TAG, "❌ [UPSERT YT ERROR] ${e.message}")
            null
        }
    }

    // --- LOGIC 4: FETCH USER DETAILS (WITH RETRY) ---


    // --- LOGIC 4.5: FETCH ARTIST DETAILS (WITH RETRY) ---


// --- LOGIC 5: SMART QUEUE V4 (Final Boss - Hardened) ---
    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    suspend fun fetchSmartQueue(
            currentSong: Song, // Seed Song
            playedIds: List<String> = emptyList() // Daftar ID yang sudah ada di antrean
    ): List<Song> {
        Log.d("MusicRepository", "🔄 calling get_smart_queue RPC...")

        // ... di dalam fetchSmartQueue ...
        val targetLang = currentSong.language ?: "en" // Ini tersangkanya
        Log.e(
                "DEBUG_SMART_QUEUE",
                "🚨 SEED CHECK: Judul=${currentSong.title}, Lang di DB=${currentSong.language}, Lang dikirim=$targetLang"
        )
        Log.e("DEBUG_SMART_QUEUE", "🚨 ARTIST CHECK: ID=${currentSong.artistId}")

        return try {
            // 🛡️ 1. SAFEGUARD MOODS
            // Kalau mood null, kita kasih list kosong biar map gak crash
            val safeMoods = currentSong.moods ?: emptyList()

            // 🛡️ 2. SAFEGUARD ARTIST ID
            // Kalau artistId null/kosong, jangan kirim string kosong "", nanti error UUID invalid.
            // Kirim null saja (JsonPrimitive(null) atau abaikan).
            // Tapi biar aman di logika SQL, kita pastikan kirim string valid atau null json.
            val safeArtistId =
                    if (currentSong.artistId.isNullOrBlank()) {
                        null // Kirim null beneran ke JSON
                    } else {
                        currentSong.artistId
                    }

            val results =
                    SupabaseManager.client
                            .postgrest
                            .rpc(
                                    "get_smart_queue",
                                    parameters =
                                            buildJsonObject {
                                                // Array ID yang sudah diputar (GABUNGAN seed + antrean)
                                                val allPlayedIds = (playedIds + currentSong.id).distinct()
                                                put(
                                                        "p_played_ids",
                                                        JsonArray(
                                                                allPlayedIds.map { JsonPrimitive(it) }
                                                        )
                                                )

                                                // Bahasa Target (Fallback ke 'en' jika null)
                                                put("p_target_lang", currentSong.language ?: "en")

                                                // Mood Target (Ambil dari safeMoods)
                                                put(
                                                        "p_target_moods",
                                                        JsonArray(
                                                                safeMoods.map { JsonPrimitive(it) }
                                                        )
                                                )

                                                // ID Artis (Bisa null)
                                                put(
                                                        "p_seed_artist_id",
                                                        safeArtistId?.let { JsonPrimitive(it) }
                                                                ?: JsonPrimitive(null)
                                                )

                                                // Limit
                                                put("p_limit", 49)
                                            }
                            )
                            .decodeList<Song>()

            Log.d("MusicRepository", "✅ get_smart_queue returned ${results.size} songs")
            results
        } catch (e: Exception) {
            Log.e(TAG, "❌ [SMART QUEUE ERROR]: ${e.message}")
            // Fallback: Kembalikan list kosong agar player tidak error, nanti ViewModel handle
            // logic fallbacknya
            emptyList()
        }
    }

    // --- LOGIC 6: INCREMENT PLAY COUNT ---
    suspend fun incrementPlayCount(
        songId: String,
        songMetadata: Song? = null,
        artistName: String? = null,
        artistPhoto: String? = null
    ) {
        try {
            val isYouTubeId = songId.length == 11 || songMetadata?.source?.startsWith("youtube") == true
            if (isYouTubeId) {
                Log.d(TAG, "⏭️ [PLAY COUNT] Lagu YouTube terdeteksi. Lewati (skip) seluruh increment untuk semua lagu YouTube: $songId")
                return
            }

            // Untuk lagu non-YouTube (Telegram/Uploader), play count diincrement langsung menggunakan songId (UUID)
            SupabaseManager.client.postgrest.rpc(
                "increment_play_count",
                parameters = buildJsonObject { put("song_uuid", songId) }
            )
            Log.d(TAG, "🆙 [PLAY COUNT] Berhasil increment play count untuk lagu: $songId")
        } catch (e: Exception) {
            Log.e(TAG, "❌ [PLAY COUNT ERROR] Gagal increment play count: ${e.message}")
        }
    }

    // --- LOGIC 7: FOLLOW ARTIST (OFFLINE FIRST) ---
    suspend fun isArtistFollowed(userId: String, artistId: String): Boolean {
        // "Single Source of Truth" adalah Local DB
        return musicDao.isArtistFollowed(artistId).first()
    }



    // --- LOGIC 8: GET AGGREGATED ARTIST DETAILS (HEADER) ---
    suspend fun getArtistDetails(userId: String, artistId: String): ArtistDetails? {
        Log.d(TAG, "📊 [ARTIST DETAILS] Meminta data lengkap artist: $artistId")

        return try {
            // 1. Ambil Info Artist (Name, Bio, Photo)
            val artistInfo = social.fetchArtistDetails(artistId) ?: return null

            // 2. Count Followers
            val followersCount =
                    SupabaseManager.client
                            .from("artist_followers")
                            .select(columns = Columns.list()) {
                                count(io.github.jan.supabase.postgrest.query.Count.EXACT)
                                filter { eq("artist_id", artistId) }
                            }
                            .countOrNull()
                            ?: 0L

            // 3. Count Total Songs & Sum Total Plays
            // Karena Supabase free tier ga support sum() langsung via API client mudah,
            // kita ambil kolom play_count semua lagunya lalu sum manual.
            // Jika lagunya ribuan, ini bad practice. Tapi untuk skala kecil ok.
            // Alternative: Buat RPC 'get_artist_stats(artist_id)' di backend.
            val songsStats =
                    SupabaseManager.client
                            .from("songs")
                            .select(columns = Columns.list("play_count")) {
                                filter { eq("artist_id", artistId) }
                            }
                            .decodeList<JsonObject>() // Decode partial

            val totalSongs = songsStats.size
            val totalPlays = songsStats.sumOf { it["play_count"].toString().toLongOrNull() ?: 0L }

            // 4. Check isFollowed
            val isFollowed = isArtistFollowed(userId, artistId)

            Log.d(
                    TAG,
                    "📊 [ARTIST STATS] $followersCount Follows, $totalSongs Songs, $totalPlays Plays"
            )

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
            Log.d(
                    TAG,
                    "🎵 [ARTIST SONGS] Fetching songs for $artistId (Limit: $limit, Offset: $offset)"
            )

            val songs =
                    SupabaseManager.client
                            .from("songs")
                            .select(
                                    columns =
                                            Columns.list(
                                                    "id",
                                                    "title",
                                                    "artist_id",
                                                    "cover_url",
                                                    "audio_url",
                                                    "duration_ms",
                                                    "created_at",
                                                    "lyrics",
                                                    "lyrics_updated_at",
                                                    "telegram_audio_file_id",
                                                    "canvas_url",
                                                    "featured_artists"
                                            )
                            ) {
                                filter { eq("artist_id", artistId) }
                                order("created_at", order = Order.DESCENDING) // Lagu terbaru dulu
                                range(offset.toLong(), (offset + limit - 1).toLong())
                            }
                            .decodeList<Song>()

            // Kita butuh Info Artist untuk setiap SongWithArtist.
            // Efisiensi: Fetch Artist Sekali saja karena sama semua.
            val artist = social.fetchArtistDetails(artistId) ?: return emptyList()

            // Map to SongWithArtist
            songs.map { song -> SongWithArtist(song, artist) }
        } catch (e: Exception) {
            Log.e(TAG, "❌ [ARTIST SONGS ERROR] Gagal ambil lagu artist: ${e.message}")
            emptyList()
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
                moods = this.moods ?: emptyList(),
                featuredArtists = this.featuredArtists
        )
    }



    suspend fun getSongsByUploaderPagination(
        uploaderId: String,
        limit: Int = 20,
        offset: Int = 0
    ): List<SongWithArtist> {
        return try {
            Log.d(TAG, "📤 [UPLOADER SONGS] Fetching songs for $uploaderId (Limit: $limit, Offset: $offset)")

            val results = SupabaseManager.client
                .from("songs")
                .select(columns = Columns.raw("*, artists(*)")) {
                    filter {
                        eq("uploader_user_id", uploaderId)
                        eq("source", "telegram")
                    }
                    order("created_at", Order.DESCENDING)
                    range(offset.toLong(), (offset + limit - 1).toLong())
                }
                .decodeList<JsonObject>()

            results.map { json ->
                val songId = json["id"]?.jsonPrimitive?.content ?: ""
                val title = json["title"]?.jsonPrimitive?.content ?: "Unknown"
                val artistId = json["artist_id"]?.jsonPrimitive?.contentOrNull
                val coverUrl = json["cover_url"]?.jsonPrimitive?.contentOrNull
                val audioUrl = json["audio_url"]?.jsonPrimitive?.contentOrNull
                val durationMs = json["duration_ms"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L
                val telegramFileId = json["telegram_audio_file_id"]?.jsonPrimitive?.contentOrNull
                val canvasUrl = json["canvas_url"]?.jsonPrimitive?.contentOrNull
                val source = json["source"]?.jsonPrimitive?.content ?: "telegram"
                val lyrics = json["lyrics"]?.jsonPrimitive?.contentOrNull
                val lyricsUpdate = json["lyrics_updated_at"]?.jsonPrimitive?.contentOrNull
                
                val song = Song(
                    id = songId,
                    title = title,
                    artistId = artistId,
                    coverUrl = coverUrl,
                    audioUrl = audioUrl,
                    durationMs = durationMs,
                    telegramFileId = telegramFileId,
                    canvasUrl = canvasUrl,
                    source = source,
                    lyrics = lyrics,
                    lyricsUpdatedAt = lyricsUpdate
                )

                val artistJson = json["artists"] as? JsonObject
                val artist = if (artistJson != null) {
                    Artist(
                        id = artistJson["id"]?.jsonPrimitive?.content ?: "",
                        name = artistJson["name"]?.jsonPrimitive?.content ?: "Unknown",
                        photoUrl = artistJson["photo_url"]?.jsonPrimitive?.contentOrNull
                    )
                } else {
                    Artist(id = "", name = "Unknown")
                }
                
                SongWithArtist(song, artist)
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ [UPLOADER SONGS ERROR] ${e.message}")
            emptyList()
        }
    }

    private fun sanitizeSearchQuery(text: String): String {
        return text
            .replace(Regex("(?i) - Topic"), "")
            .replace(Regex("(?i)\\(Official.*?\\)"), "")
            .replace(Regex("(?i)\\[Official.*?\\]"), "")
            .replace(Regex("(?i)\\(Live.*?\\)"), "")
            .replace(Regex("(?i)\\[Live.*?\\]"), "")
            .replace(Regex("(?i)\\(Music Video\\)"), "")
            .replace(Regex("(?i)\\[Music Video\\]"), "")
            .replace(Regex("(?i)\\(Lyric.*?\\)"), "")
            .replace(Regex("(?i)\\[Lyric.*?\\]"), "")
            .replace(Regex("(?i)MV$"), "")
            .replace(Regex("(?i)\\(.*?Hz.*?\\)"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
