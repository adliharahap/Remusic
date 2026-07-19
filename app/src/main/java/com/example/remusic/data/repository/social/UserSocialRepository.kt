package com.example.remusic.data.repository.social

import android.util.Log
import com.example.remusic.data.SupabaseManager
import com.example.remusic.data.local.MusicDao
import com.example.remusic.data.local.entity.LikedSong
import com.example.remusic.data.model.Artist
import com.example.remusic.data.model.Song
import com.example.remusic.data.model.User
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import kotlinx.coroutines.flow.first
import java.util.UUID

import kotlinx.serialization.json.JsonObject
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.delay
import kotlinx.serialization.json.jsonPrimitive
import com.example.remusic.data.local.entity.CachedFollowedArtist

class UserSocialRepository(
    private val musicDao: MusicDao,
    private val upsertYouTubeSongAction: suspend (Song, String?, String?) -> String?
) {
    private val TAG = "DEBUG_SOCIAL_REPO"
    
    private val memoryUserCache = mutableMapOf<String, com.example.remusic.data.model.User>()
    private val memoryArtistCache = mutableMapOf<String, com.example.remusic.data.model.Artist>()

    fun isSongLikedFlow(songId: String): kotlinx.coroutines.flow.Flow<Boolean> {
        return musicDao.isSongLiked(songId)
    }

    suspend fun isSongLiked(songId: String, userId: String): Boolean {
        // "Single Source of Truth" adalah Local DB
        return musicDao.isSongLiked(songId).first()
    }

    suspend fun toggleLike(
        songId: String,
        userId: String,
        isLiked: Boolean,
        songMetadata: Song? = null,
        artistName: String? = null,
        artistPhoto: String? = null
    ) {
        // 1. Perbarui Local DB (Optimistic Update) - UI langsung berubah!
        // Local DB selalu pakai songId asli (bisa YT ID 11 char atau UUID Telegram)
        if (isLiked) {
            musicDao.deleteLikedSong(songId)
            Log.d(TAG, "💔 [OFFLINE] Lagu dihapus dari Liked Songs (Local).")
        } else {
            musicDao.insertLikedSong(LikedSong(songId))
            Log.d(TAG, "❤️ [OFFLINE] Lagu ditambahkan ke Liked Songs (Local).")
        }

        // 2. Sinkron ke Cloud (Best Effort)
        try {
            // Jika ini lagu YouTube, kita harus cari/buat UUID-nya dulu di tabel 'songs'
            val isYouTubeId = songId.length == 11 || songMetadata?.source?.startsWith("youtube") == true
            val effectiveSongId = if (isYouTubeId && songMetadata != null) {
                Log.d(TAG, "🔍 [SYNC] Lagu YouTube terdeteksi, mencari/membuat UUID shadow...")
                upsertYouTubeSongAction(songMetadata, artistName ?: songMetadata.featuredArtists.firstOrNull(), artistPhoto) ?: songId
            } else {
                songId
            }

            if (isLiked) {
                // Resolusi UUID jika songId adalah ID YouTube
                var finalSongId = songId
                if (!songId.contains("-")) { // Bukan UUID (Biasanya ID YouTube tidak pakai strip)
                        val songResult = SupabaseManager.client.from("songs")
                        .select(columns = io.github.jan.supabase.postgrest.query.Columns.list("id")) {
                            filter { eq("youtube_id", songId) }
                        }
                        .decodeSingleOrNull<JsonObject>()
                    finalSongId = songResult?.get("id")?.jsonPrimitive?.content ?: songId
                }

                // DELETE (Unlike)
                SupabaseManager.client.from("user_song_likes").delete {
                    filter {
                        eq("user_id", userId)
                        eq("song_id", finalSongId)
                    }
                }
                Log.d(TAG, "☁️ [SYNC] Unlike berhasil disinkronkan ke server.")
            } else {
                // INSERT (Like), biarkan position default (0) dari DB agar saat DESCENDING lagu baru ada di atas berdasarkan created_at
                val data = mapOf(
                    "user_id" to userId, 
                    "song_id" to effectiveSongId
                )
                SupabaseManager.client.from("user_song_likes").insert(data)
                Log.d(TAG, "☁️ [SYNC] Like berhasil disinkronkan ke server.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "⚠️ [SYNC FAIL] Gagal sync ke server (UI aman): ${e.message}")
        }
    }

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
                if (attempt > 1) Log.d("DEBUG_UPLOADER", "🔄 RETRY USER: Percobaan ke-$attempt...")

                // Ambil data mentah dulu untuk logging
                val result = SupabaseManager.client
                                .from("users")
                                .select { filter { eq("id", userId) } }
                
                Log.d("DEBUG_UPLOADER", "☁️ [SUPABASE USER RAW] Result data: ${result.data}")

                val user = result.decodeSingleOrNull<User>()

                if (user != null) {
                    Log.d("DEBUG_UPLOADER", "✅ [USER DECODED] Berhasil parse: $user")
                    memoryUserCache[userId] = user // Simpan ke cache
                    return user
                } else {
                    Log.w("DEBUG_UPLOADER", "⚠️ [USER NULL] decodeSingleOrNull menghasilkan null! (Mungkin ID tidak ada di tabel, atau error parsing silent)")
                }
            } catch (e: Exception) {
                Log.e("DEBUG_UPLOADER", "❌ [USER ERROR] Percobaan $attempt Gagal: ${e.message}")
            }

            if (attempt < maxAttempts) delay(1000)
            attempt++
        }

        Log.e(TAG, "[USER GIVE UP] Gagal ambil detail user setelah $maxAttempts percobaan.")
        return null
    }

    suspend fun fetchArtistDetails(artistId: String): Artist? {
        if (artistId.isBlank()) {
            Log.w(TAG, "[ARTIST] artistId is blank, skipping fetch.")
            return null
        }
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

                val artist =
                        SupabaseManager.client
                                .from("artists")
                                .select { filter { eq("id", artistId) } }
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
                SupabaseManager.client.from("artist_followers").delete {
                    filter {
                        eq("user_id", userId)
                        eq("artist_id", artistId)
                    }
                }
                Log.d(TAG, "☁️ [SYNC] Unfollow sukses di server.")
            } else {
                // FOLLOW (INSERT)
                val data = mapOf("user_id" to userId, "artist_id" to artistId)
                SupabaseManager.client.from("artist_followers").insert(data)
                Log.d(TAG, "☁️ [SYNC] Follow sukses di server.")
            }

            // Note: Memcache di-handle oleh Repo logic sebelumnya, tapi dengan SQLite kita ga butuh
            // map lagi.
            // memoryFollowCache[artistId] = !isFollowed // (Bisa dihapus jika move total ke SQLite)

        } catch (e: Exception) {
            Log.e(TAG, "⚠️ [SYNC FAIL] Gagal sync follow ke server: ${e.message}")
        }
    }
    // --- LOGIC 4.6: BULK FETCH ARTISTS ---
    suspend fun fetchArtistsByIds(artistIds: List<String>): List<Artist> {
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
            val fetchedArtists =
                    SupabaseManager.client
                            .from("artists")
                            .select { filter { isIn("id", missingIds) } }
                            .decodeList<Artist>()

            // Update Cache
            fetchedArtists.forEach { artist -> memoryArtistCache[artist.id] = artist }

            return cachedArtists + fetchedArtists
        } catch (e: Exception) {
            Log.e(TAG, "❌ [ARTIST BULK ERROR]: ${e.message}")
            // Return whatever we have in cache
            return cachedArtists
        }
    }
}