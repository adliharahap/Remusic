package com.example.remusic.data.repository.queue

import android.util.Log
import com.example.remusic.data.SupabaseManager
import com.example.remusic.data.local.MusicDao
import com.example.remusic.data.local.entity.CachedSong
import com.example.remusic.data.local.entity.PlaybackQueueEntity
import com.example.remusic.data.model.Artist
import com.example.remusic.data.model.Song
import com.example.remusic.data.model.SongWithArtist
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.filter.*

class QueueRepository(private val musicDao: MusicDao) {
    private val TAG = "DEBUG_PLAYER_QUEUE"

    suspend fun savePlaybackQueue(songs: List<SongWithArtist>, playlistName: String) {
        try {
            musicDao.clearPlaybackQueue() // Wajib kosongin tabel dulu
            if (songs.isEmpty()) {
                return
            }
            // Konversi ke Entity dengan urutan
            val queueEntities =
                    songs.mapIndexed { index, songWA ->
                        PlaybackQueueEntity(
                                songId = songWA.song.id,
                                listOrder = index,
                                artistName = songWA.artist?.name ?: "Unknown",
                                playlistName = playlistName
                        )
                    }
            musicDao.insertPlaybackQueue(queueEntities)

            // 🔥 FIX: Aktivasi Caching SongDetails Langsung Saat Menyimpan Antrean
            for (songWA in songs) {
                val song = songWA.song
                val existing = musicDao.getSongById(song.id)
                if (existing != null) {
                    val needsUpdate = existing.coverUrl == null || existing.canvasUrl == null || 
                                      (song.lyricsUpdatedAt != null && song.lyricsUpdatedAt != existing.lyricsUpdatedAt)
                    if (needsUpdate) {
                       musicDao.updateSongDetails(
                           id = song.id,
                           title = song.title,
                           lyrics = song.lyrics ?: existing.lyrics,
                           lyricsUpdatedAt = song.lyricsUpdatedAt ?: existing.lyricsUpdatedAt,
                           cover = song.coverUrl ?: existing.coverUrl,
                           canvas = song.canvasUrl ?: existing.canvasUrl,
                           uploaderId = song.uploaderUserId ?: existing.uploaderUserId,
                           language = song.language ?: existing.language,
                           moods = (song.moods ?: emptyList()).ifEmpty { existing.moods },
                           artistId = song.artistId ?: existing.artistId,
                           featuredArtists = song.featuredArtists.ifEmpty { existing.featuredArtists }
                       )
                    }
                } else {
                    musicDao.insertSong(CachedSong(
                        id = song.id,
                        title = song.title,
                        uploaderUserId = song.uploaderUserId,
                        artistName = songWA.artist?.name ?: "Unknown",
                        coverUrl = song.coverUrl,
                        canvasUrl = song.canvasUrl,
                        lyrics = song.lyrics,
                        lyricsUpdatedAt = song.lyricsUpdatedAt,
                        telegramFileId = song.telegramFileId,
                        telegramDirectUrl = song.audioUrl,
                        language = song.language,
                        moods = song.moods ?: emptyList(),
                        artistId = song.artistId,
                        featuredArtists = song.featuredArtists
                    ))
                }
            }

            Log.d(TAG, "💾 [QUEUE] Saved ${songs.size} songs to playback queue.")
        } catch (e: Exception) {
            Log.e(TAG, "❌ [QUEUE ERROR] Failed to save queue: ${e.message}")
        }
    }

    suspend fun restorePlaybackQueue(): Pair<List<SongWithArtist>, String> {
        try {
            val queueEntities = musicDao.getPlaybackQueue()
            if (queueEntities.isEmpty()) return Pair(emptyList(), "Unknown Playlist")

            val playlistName = queueEntities.first().playlistName

            // Ambil ID lagu dari queue
            val songIds = queueEntities.map { it.songId }

            // Fetch detail lagu (Coba cache dulu, kalau gak ada fetch dari server)
            val cachedSongs = songIds.mapNotNull { id -> musicDao.getSongById(id) }
            val cachedSongMap = cachedSongs.associateBy { it.id }.toMutableMap()

            val missingSongIds = songIds.filter { 
                if (it.startsWith("offline_")) false 
                else {
                    val cached = cachedSongMap[it]
                    cached == null || cached.coverUrl == null 
                }
            }

            // Jika ada lagu yang belum di-cache, fetch dari Supabase secara bulk
            if (missingSongIds.isNotEmpty()) {
                Log.d(TAG, "☁️ [QUEUE] Fetching ${missingSongIds.size} missing/incomplete songs from Supabase...")
                try {
                    val remoteSongs = SupabaseManager.client.from("songs")
                        .select { filter { isIn("id", missingSongIds) } }
                        .decodeList<Song>()

                    val newCacheEntries = remoteSongs.map { song ->
                        val existing = cachedSongMap[song.id]
                        CachedSong(
                            id = song.id,
                            title = song.title,
                            uploaderUserId = song.uploaderUserId,
                            artistName = "Unknown",
                            coverUrl = song.coverUrl,
                            canvasUrl = song.canvasUrl,
                            lyrics = song.lyrics ?: existing?.lyrics,
                            lyricsUpdatedAt = song.lyricsUpdatedAt ?: existing?.lyricsUpdatedAt,
                            telegramFileId = song.telegramFileId ?: existing?.telegramFileId,
                            telegramDirectUrl = existing?.telegramDirectUrl, // 🔥 PERTAHANKAN DIRECT URL!
                            language = song.language ?: existing?.language,
                            moods = (song.moods ?: emptyList()).ifEmpty { existing?.moods ?: emptyList() },
                            artistId = song.artistId ?: existing?.artistId
                        )
                    }

                    // Insert ke cache HANYA DARI DISPATCHER IO
                    newCacheEntries.forEach { musicDao.insertSong(it) }
                    
                    // Tambahkan ke map
                    newCacheEntries.forEach { cachedSongMap[it.id] = it }
                } catch (e: Exception) {
                    Log.e(TAG, "❌ [QUEUE] Failed to fetch missing songs: ${e.message}")
                }
            }

            // Definisi judul/artist yang dianggap "tidak valid" (corrupt/placeholder)
            val invalidTitles = setOf("unknown song", "unknown title", "")
            val invalidArtists = setOf("unknown artist", "unknown")

            val restoredSongs = mutableListOf<SongWithArtist>()

            queueEntities.forEach { entity ->
                val cachedSong = cachedSongMap[entity.songId]
                val songTitle = cachedSong?.title?.trim()?.lowercase() ?: "unknown song"
                val artistNameLower = entity.artistName.trim().lowercase()

                // Cek apakah lagu ini tidak valid
                if (songTitle in invalidTitles || artistNameLower in invalidArtists) {
                    // Hapus entry corrupt ini dari database agar tidak muncul lagi
                    Log.w(TAG, "🗑️ [QUEUE FILTER] Skipping & deleting corrupt entry: songId=${entity.songId}, title='${cachedSong?.title}', artist='${entity.artistName}'")
                    return@forEach // skip, jangan masukkan ke restoredSongs
                }

                val song = cachedSong?.toSong() ?: Song(
                    id = entity.songId,
                    title = "Unknown Song",
                    audioUrl = null,
                    coverUrl = null,
                    lyrics = null
                )

                val fallbackArtist = Artist(
                    id = song.artistId ?: "",
                    name = entity.artistName,
                    photoUrl = null,
                    description = null,
                    normalizedName = null,
                    createdBy = null,
                    createdAt = null,
                    updatedAt = null
                )
                restoredSongs.add(SongWithArtist(song, fallbackArtist))
            }

            // Bersihkan seluruh queue lama, lalu simpan kembali HANYA lagu yang valid
            // Ini memastikan entry corrupt tidak menumpuk di database
            val validSongIds = restoredSongs.map { it.song.id }.toSet()
            val removedCount = queueEntities.count { it.songId !in validSongIds }
            if (removedCount > 0) {
                Log.d(TAG, "🧹 [QUEUE CLEAN] Menghapus $removedCount entry tidak valid dari database.")
                musicDao.clearPlaybackQueue()
                val cleanEntities = queueEntities
                    .filter { it.songId in validSongIds }
                    .mapIndexed { idx, e -> e.copy(listOrder = idx) }
                musicDao.insertPlaybackQueue(cleanEntities)
            }

            Log.d(TAG, "♻️ [QUEUE] Restored ${restoredSongs.size} songs from playback queue.")
            return Pair(restoredSongs, playlistName)
        } catch (e: Exception) {
            Log.e(TAG, "❌ [QUEUE ERROR] Failed to restore queue: ${e.message}")
            return Pair(emptyList(), "Unknown Playlist")
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
}
