package com.example.remusic.viewmodel.playmusic

import android.annotation.SuppressLint
import android.app.Application
import android.content.ComponentName
import androidx.annotation.OptIn
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.remusic.data.SupabaseManager
import com.example.remusic.data.UserManager
import com.example.remusic.data.local.MusicDatabase
import com.example.remusic.data.model.Artist
import com.example.remusic.data.model.ArtistDetails
import com.example.remusic.data.model.AudioOutputDevice
import com.example.remusic.data.model.Song
import com.example.remusic.data.model.SongWithArtist
import com.example.remusic.data.model.displayArtistName
import com.example.remusic.data.model.User
import com.example.remusic.data.preferences.UserPreferencesRepository
import com.example.remusic.data.repository.MusicRepository
import com.example.remusic.data.network.LrcLibResponse
import com.example.remusic.services.MusicService
import com.example.remusic.utils.NotificationUtils
import com.example.remusic.utils.SongDownloader
import com.example.remusic.utils.extractGradientColorsFromImageUrl
import android.widget.Toast
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import android.media.AudioManager
import android.media.AudioDeviceInfo
import android.media.AudioDeviceCallback
import android.content.Context
import androidx.mediarouter.media.MediaRouter
import androidx.mediarouter.media.MediaRouteSelector
import androidx.mediarouter.media.MediaControlIntent
import timber.log.Timber

// --- EXTENSION FUNCTIONS FOR DATA FETCHING ---



    fun PlayMusicViewModel.fetchFullSongDetails(songId: String, forceUpdate: Boolean = false) {
        Timber.tag("DEBUG_PLAYER").d("🚀 [LYRICS FETCH] Menjalankan fetchFullSongDetails untuk: $songId (Force: $forceUpdate)")
        if (songId.isBlank() || songId.startsWith("offline_")) return
        fetchLyricsJob?.cancel()
        searchDebounceJob?.cancel()
        
        // Segera beri tahu UI bahwa lirik sedang di-fetch untuk lagu ini agar tidak menampilkan state kosong/lama
        _uiState.update { state ->
            if (state.currentSong?.song?.effectiveId == songId || state.currentSong?.song?.id == songId) {
                state.copy(isLoadingLyrics = true, isFetchingSyncedLyrics = true)
            } else state
        }

        searchDebounceJob = viewModelScope.launch(Dispatchers.IO) {
            delay(500) // Small debounce
            fetchLyricsJob = launch {
                _uiState.update { state ->
                    if (state.currentSong?.song?.effectiveId == songId || state.currentSong?.song?.id == songId) {
                        state.copy(isLoadingLyrics = true, isFetchingSyncedLyrics = true)
                    } else state
                }
                try {
                    val currentSongInState = _uiState.value.currentSong?.takeIf { it.song.effectiveId == songId }
                    val title = currentSongInState?.song?.title ?: ""
                    val artist = currentSongInState?.artist?.name ?: ""

                    // Helper to detect synced lyrics
                    fun isLyricsSynced(lyrics: String?): Boolean {
                        if (lyrics.isNullOrBlank()) return false
                        if (lyrics == "[Instrumental]") return true
                        return lyrics.contains(Regex("\\[\\d+:\\d{2}"))
                    }

                    // Helper to sanitize search query
                    fun sanitizeQuery(text: String): String {
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

                    // 1. Check local cache first (cek ID dan YouTube ID)
                    val altId = currentSongInState?.song?.id?.takeIf { it != songId }
                        ?: currentSongInState?.song?.effectiveId?.takeIf { it != songId }
                    val cached = database.musicDao().getSongByIdOrYoutubeId(songId)
                        ?: (if (altId != null) database.musicDao().getSongByIdOrYoutubeId(altId) else null)
                    val lyricsInCache = cached?.lyrics
                    
                    val isTelegramSong = currentSongInState?.song?.source == "telegram" || currentSongInState?.song?.telegramFileId != null
                    val isYoutubeSong = currentSongInState?.song?.source == "youtube" || currentSongInState?.song?.id?.matches(Regex("^[a-zA-Z0-9_-]{11}$")) == true

                    var skipLyricsFetch = false

                    if (isTelegramSong) {
                        Timber.tag("DEBUG_PLAYER").d("🚀 [TELEGRAM LYRICS] Lagu dari telegram, memuat lirik dari database uploader...")
                        skipLyricsFetch = true
                        
                        var finalLyrics = lyricsInCache ?: currentSongInState?.song?.lyrics
                        var bestUploaderId = currentSongInState?.song?.uploaderUserId ?: cached?.uploaderUserId
                        
                        // Jika lirik masih kosong, uploaderId kosong, atau user memaksa update (retry)
                        if (forceUpdate || finalLyrics.isNullOrBlank() || bestUploaderId == null) {
                            try {
                                Timber.tag("DEBUG_PLAYER").d("☁️ [TELEGRAM LYRICS] Fetching lirik & uploader_user_id terbaru dari Supabase songs table...")
                                val supabaseSong = SupabaseManager.client.from("songs")
                                    .select(columns = io.github.jan.supabase.postgrest.query.Columns.list("lyrics", "uploader_user_id")) {
                                        filter { eq("id", songId) }
                                    }.decodeSingleOrNull<com.example.remusic.data.model.Song>()
                                
                                if (supabaseSong != null) {
                                    // Update Lirik
                                    if (!supabaseSong.lyrics.isNullOrBlank()) {
                                        finalLyrics = supabaseSong.lyrics
                                        database.musicDao().updateLyrics(songId, finalLyrics!!)
                                        Timber.tag("DEBUG_PLAYER").d("✅ [TELEGRAM LYRICS] Berhasil mendapatkan lirik dari database songs!")
                                    }
                                    
                                    // Update Uploader ID
                                    if (supabaseSong.uploaderUserId != null) {
                                        bestUploaderId = supabaseSong.uploaderUserId
                                        Timber.tag("DEBUG_UPLOADER").d("✅ Berhasil mendapatkan uploaderUserId yang hilang: $bestUploaderId")
                                    }
                                }
                                
                                if (finalLyrics.isNullOrBlank()) {
                                    // Fallback ke lyrics_hub
                                    val hubData = SupabaseManager.client.from("lyrics_hub")
                                        .select(columns = io.github.jan.supabase.postgrest.query.Columns.list("lyrics")) { filter { eq("id", songId) } }
                                        .decodeSingleOrNull<Map<String, String>>()
                                    val hubLyrics = hubData?.get("lyrics")
                                    if (!hubLyrics.isNullOrBlank()) {
                                        finalLyrics = hubLyrics
                                        database.musicDao().updateLyrics(songId, finalLyrics!!)
                                        Timber.tag("DEBUG_PLAYER").d("✅ [TELEGRAM LYRICS] Berhasil mendapatkan lirik dari lyrics_hub!")
                                    } else {
                                        Timber.tag("DEBUG_PLAYER").d("⚠️ [TELEGRAM LYRICS] Lirik di database memang kosong.")
                                    }
                                }
                            } catch(e: Exception) {
                                Timber.tag("DEBUG_PLAYER").e("❌ [TELEGRAM LYRICS ERROR] ${e.message}")
                            }
                        }

                        _uiState.update { state ->
                            if (state.currentSong?.song?.effectiveId == songId) {
                                val updatedSong = state.currentSong.song.copy(
                                    lyrics = finalLyrics ?: "",
                                    uploaderUserId = bestUploaderId ?: state.currentSong.song.uploaderUserId
                                )
                                val updatedPlaylist = state.playlist.map { item ->
                                    if (item.song.effectiveId == songId || item.song.id == state.currentSong.song.id) {
                                        item.copy(song = item.song.copy(lyrics = finalLyrics ?: ""))
                                    } else item
                                }
                                state.copy(
                                    currentSong = state.currentSong.copy(song = updatedSong),
                                    playlist = updatedPlaylist,
                                    isLoadingLyrics = false,
                                    isFetchingSyncedLyrics = false
                                )
                            } else state
                        }
                    } else if (!lyricsInCache.isNullOrBlank() && isLyricsSynced(lyricsInCache) && (!forceUpdate || lyricsInCache.contains("|"))) {
                        val hasTranslated = lyricsInCache.contains("|")
                        Timber.tag("DEBUG_PLAYER").d("🚀 [LYRICS CACHE HIT] Lirik ditemukan di database (Translated=$hasTranslated). Mengembalikan secara instan.")
                        skipLyricsFetch = true
                        _uiState.update { state ->
                            if (state.currentSong?.song?.effectiveId == songId || state.currentSong?.song?.id == songId) {
                                val updatedPlaylist = state.playlist.map { item ->
                                    if (item.song.effectiveId == songId || item.song.id == state.currentSong.song.id) {
                                        item.copy(song = item.song.copy(lyrics = lyricsInCache))
                                    } else item
                                }
                                state.copy(
                                    currentSong = state.currentSong.copy(song = state.currentSong.song.copy(lyrics = lyricsInCache)),
                                    playlist = updatedPlaylist,
                                    isLoadingLyrics = false,
                                    isFetchingSyncedLyrics = false
                                )
                            } else state
                        }
                    }

                    // Flag to track if synced lyrics are successfully loaded from lrclib/hub
                    var isSyncedLoaded = false

                    if (!skipLyricsFetch) {
                        // If cached lyrics exist (could be plain), we can load them immediately to reduce waiting
                        if (!lyricsInCache.isNullOrBlank()) {
                            Timber.tag("DEBUG_PLAYER").d("📜 [LYRICS CACHE PRE-LOAD] Loading cached lyrics to UI first: ${lyricsInCache.take(30)}...")
                            _uiState.update { state ->
                                if (state.currentSong?.song?.effectiveId == songId) {
                                    val updatedPlaylist = state.playlist.map { item ->
                                        if (item.song.effectiveId == songId || item.song.id == state.currentSong.song.id) {
                                            item.copy(song = item.song.copy(lyrics = lyricsInCache))
                                        } else item
                                    }
                                    state.copy(
                                        currentSong = state.currentSong.copy(song = state.currentSong.song.copy(lyrics = lyricsInCache)),
                                        playlist = updatedPlaylist,
                                        isLoadingLyrics = false
                                    )
                                } else state
                            }
                            if (isLyricsSynced(lyricsInCache)) {
                                isSyncedLoaded = true
                                _uiState.update { state ->
                                    if (state.currentSong?.song?.effectiveId == songId) {
                                        state.copy(isFetchingSyncedLyrics = false)
                                    } else state
                                }
                            }
                        }
                    }

                    if (!skipLyricsFetch) {
                        // Launch concurrent fetches
                        coroutineScope {
                            // Task A: LrcLib / Synced Lyrics Fetching (Can take up to 6 seconds)
                            launch {
                                try {
                                    var finalSyncedLyrics: String? = null
                                    
                                    // Step A.1: Check Supabase Lyrics Hub
                                    try {
                                        val cleanTitle = sanitizeQuery(title)
                                        val cleanArtist = sanitizeQuery(artist)
                                        Timber.tag("DEBUG_PLAYER").d("☁️ [SUPABASE HUB] Mencari lirik synced di lyrics_hub...")
                                        val hubData = SupabaseManager.client.from("lyrics_hub")
                                            .select() { filter { eq("id", songId) } }
                                            .decodeSingleOrNull<Map<String, String>>()
                                        val hubLyrics = hubData?.get("lyrics")
                                        if (!hubLyrics.isNullOrBlank() && isLyricsSynced(hubLyrics)) {
                                            finalSyncedLyrics = hubLyrics
                                            Timber.tag("DEBUG_PLAYER").d("✅ [SUPABASE HUB HIT] Lirik synced ditemukan!")
                                        }
                                    } catch (e: Exception) {
                                        Timber.tag("DEBUG_PLAYER").e("⚠️ [SUPABASE HUB ERROR] ${e.message}")
                                    }

                                    // Step A.2: Check LRCLIB Api
                                    if (isYoutubeSong && finalSyncedLyrics == null && title.isNotBlank() && artist.isNotBlank()) {
                                        val cleanTitle = sanitizeQuery(title)
                                        val cleanArtist = sanitizeQuery(artist)
                                        Timber.tag("DEBUG_PLAYER").d("🔍 [LRCLIB FETCH] Menembak API LrcLib untuk lirik synced...")
                                        val lrcResults = com.example.remusic.data.network.LrcLibRetrofit.api.searchLyrics(cleanTitle, cleanArtist)
                                        val bestMatch = lrcResults.firstOrNull()
                                        val syncedLyrics = bestMatch?.syncedLyrics
                                        val isInstrumental = bestMatch?.instrumental == true
                                        
                                        if (isInstrumental || !syncedLyrics.isNullOrBlank()) {
                                            finalSyncedLyrics = if (isInstrumental) "[Instrumental]" else syncedLyrics!! + "\n\nlyrics from lrclib"
                                            Timber.tag("DEBUG_PLAYER").d("✅ [LRCLIB HIT] Lirik synced ditemukan!")
                                        }
                                    }

                                    if (!finalSyncedLyrics.isNullOrBlank()) {
                                        isSyncedLoaded = true
                                        // Save to local cache
                                        database.musicDao().updateLyrics(songId, finalSyncedLyrics)
                                        // Push to Supabase Hub
                                        launch(Dispatchers.IO) {
                                            try {
                                                repository.lyrics.pushLyricsToLyricsHub(songId, title, artist, finalSyncedLyrics, "lrclib")
                                            } catch (e: Exception) {}
                                        }
                                        // Update state
                                        _uiState.update { state ->
                                            if (state.currentSong?.song?.effectiveId == songId) {
                                                val updatedPlaylist = state.playlist.map { item ->
                                                    if (item.song.effectiveId == songId || item.song.id == state.currentSong.song.id) {
                                                        item.copy(song = item.song.copy(lyrics = finalSyncedLyrics))
                                                    } else item
                                                }
                                                state.copy(
                                                    currentSong = state.currentSong.copy(song = state.currentSong.song.copy(lyrics = finalSyncedLyrics)),
                                                    playlist = updatedPlaylist,
                                                    isLoadingLyrics = false,
                                                    isFetchingSyncedLyrics = false
                                                )
                                            } else state
                                        }
                                    }
                                } catch (e: Exception) {
                                    if (e is kotlinx.coroutines.CancellationException) throw e
                                    Timber.tag("DEBUG_PLAYER").e("❌ [SYNCED FETCH FAIL] ${e.message}")
                                } finally {
                                    _uiState.update { state ->
                                        if (state.currentSong?.song?.effectiveId == songId) {
                                            state.copy(isFetchingSyncedLyrics = false)
                                        } else state
                                    }
                                }
                            }

                            // Task B: YouTube Plain Text Lyrics Fetching (Super Fast)
                            launch {
                                if (!isYoutubeSong) return@launch
                                try {
                                    var finalPlainLyrics: String? = null
                                    
                                    // Step B.1: Check YouTube Transcript (Synced)
                                    try {
                                        Timber.tag("DEBUG_PLAYER").d("🎵 [YT TRANSCRIPT] Meminta transcript YouTube...")
                                        val ytTranscript = com.metrolist.innertube.YouTube.transcript(songId).getOrNull()
                                        if (!ytTranscript.isNullOrBlank()) {
                                            finalPlainLyrics = ytTranscript
                                        }
                                    } catch (e: Exception) {}

                                    // Step B.2: Check YouTube Next & Lyrics endpoint (Plain)
                                    if (finalPlainLyrics == null) {
                                        Timber.tag("DEBUG_PLAYER").d("📜 [YT PLAIN] Meminta plain lyrics YouTube...")
                                        val endpoint = com.metrolist.innertube.models.WatchEndpoint(videoId = songId)
                                        val nextResult = com.metrolist.innertube.YouTube.next(endpoint).getOrNull()
                                        val lyricsEndpoint = nextResult?.lyricsEndpoint
                                        if (lyricsEndpoint != null) {
                                            val ytPlain = com.metrolist.innertube.YouTube.lyrics(lyricsEndpoint).getOrNull()
                                            if (!ytPlain.isNullOrBlank()) {
                                                finalPlainLyrics = ytPlain
                                            }
                                        }
                                    }

                                    val invalidKeywords = listOf("tidak tersedia", "not available", "no lyrics", "lirik belum ada")
                                    val sanitizedPlain = if (!finalPlainLyrics.isNullOrBlank() && invalidKeywords.none { finalPlainLyrics!!.contains(it, ignoreCase = true) }) {
                                        finalPlainLyrics
                                    } else null

                                    if (!sanitizedPlain.isNullOrBlank()) {
                                        // GUARD: If synced lyrics were already loaded, IGNORE plain lyrics entirely!
                                        if (isSyncedLoaded) {
                                            Timber.tag("DEBUG_PLAYER").d("⚠️ [LYRICS RIVALRY] Lirik plain YouTube diabaikan karena lirik synced sudah terisi.")
                                            return@launch
                                        }

                                        // Save to local cache if DB doesn't already have synced lyrics
                                        val existing = database.musicDao().getSongById(songId)
                                        if (existing == null || !isLyricsSynced(existing.lyrics)) {
                                            database.musicDao().updateLyrics(songId, sanitizedPlain)
                                        }

                                        // Update state
                                        _uiState.update { state ->
                                            if (state.currentSong?.song?.effectiveId == songId && !isSyncedLoaded) {
                                                val updatedPlaylist = state.playlist.map { item ->
                                                    if (item.song.effectiveId == songId || item.song.id == state.currentSong.song.id) {
                                                        item.copy(song = item.song.copy(lyrics = sanitizedPlain))
                                                    } else item
                                                }
                                                state.copy(
                                                    currentSong = state.currentSong.copy(song = state.currentSong.song.copy(lyrics = sanitizedPlain)),
                                                    playlist = updatedPlaylist,
                                                    isLoadingLyrics = false
                                                )
                                            } else state
                                        }
                                    }
                                } catch (e: Exception) {
                                    if (e is kotlinx.coroutines.CancellationException) throw e
                                    Timber.tag("DEBUG_PLAYER").e("❌ [PLAIN FETCH FAIL] ${e.message}")
                                }
                            }
                        }
                    }

                    // 2. Fetch Artist Details if missing (original code flow)
                    val artistId = currentSongInState?.song?.artistId ?: currentSongInState?.artist?.id
                    if (artistId != null) {
                        val currentArtist = _uiState.value.currentSong?.artist
                        if (currentArtist == null || currentArtist.photoUrl == null) {
                            if (artistId.startsWith("UC") || artistId.startsWith("U")) {
                                try {
                                    val artistPage = com.metrolist.innertube.YouTube.artist(artistId).getOrNull()
                                    val ytArtist = Artist(
                                        id = artistId,
                                        name = artistPage?.artist?.title ?: (currentSongInState?.artist?.name ?: "YouTube Artist"),
                                        photoUrl = artistPage?.artist?.thumbnail,
                                        description = artistPage?.description
                                    )
                                    _uiState.update { state ->
                                        if (state.currentSong?.song?.effectiveId == songId) state.copy(currentSong = state.currentSong.copy(artist = ytArtist)) else state
                                    }
                                } catch (e: Exception) {
                                    Timber.tag("DEBUG_PLAYER").e("Failed to fetch YouTube artist details: ${e.message}")
                                }
                            } else if (artistId.isNotBlank()) {
                                val artistDetails = repository.social.fetchArtistDetails(artistId)
                                if (artistDetails != null) {
                                    val detailedArtist = Artist(
                                        id = artistId,
                                        name = artistDetails.name,
                                        photoUrl = artistDetails.photoUrl,
                                        description = artistDetails.description
                                    )
                                    _uiState.update { state ->
                                        if (state.currentSong?.song?.effectiveId == songId) state.copy(currentSong = state.currentSong.copy(artist = detailedArtist)) else state
                                    }
                                    checkIfArtistFollowed(artistId)
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    if (e !is kotlinx.coroutines.CancellationException)Timber.tag("DEBUG_PLAYER").e( "❌ CRASH FETCH LIRIK: ${e.message}")
                } finally {
                    _uiState.update { state -> if (state.currentSong?.song?.effectiveId == songId) state.copy(isLoadingLyrics = false, isFetchingSyncedLyrics = false) else state }
                    val currentSongData = _uiState.value.currentSong?.song
                    val isYouTubeId = currentSongData?.id?.matches(Regex("^[a-zA-Z0-9_-]{11}$")) == true
                    if (currentSongData?.source == "youtube" || (isYouTubeId && currentSongData.telegramFileId == null)) {
                        _uiState.update { it.copy(uploader = User(uid = "youtube", displayName = "Extended YouTube Music", role = "extended songs")) }
                    } else {
                        val uId = currentSongData?.uploaderUserId
                        Timber.tag("DEBUG_UPLOADER").d("ℹ️ currentSongData: id=${currentSongData?.id}, source=${currentSongData?.source}, uploaderUserId=$uId")
                        if (uId != null) {
                            fetchUploaderDetails(uId)
                        } else {
                            Timber.tag("DEBUG_UPLOADER").w("⚠️ uploaderUserId bernilai null! Tidak bisa mengambil data uploader.")
                        }
                    }
                }
            }
        }
    }




    fun PlayMusicViewModel.fetchUploaderDetails(uploaderId: String) {
        Timber.tag("DEBUG_UPLOADER").d("🚀 Memulai fetchUploaderDetails untuk ID: $uploaderId")
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val uploader = repository.social.fetchUserDetails(uploaderId)
                Timber.tag("DEBUG_UPLOADER").d("🏁 Hasil fetchUploaderDetails untuk $uploaderId: $uploader")
                _uiState.update { it.copy(uploader = uploader) }
            } catch (e: Exception) {
                Timber.tag("DEBUG_UPLOADER").e(e, "❌ Exception saat memanggil repository.social.fetchUserDetails")
            }
        }
    }


    // --- ARTIST DETAILS FETCHING (For Playlist Header) ---
    fun PlayMusicViewModel.fetchArtistDetailsForHeader(artistId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            // Only set null if current details are different to avoid flicker
            if (_uiState.value.artistDetails?.id != artistId) {
                _uiState.update { it.copy(isLoadingArtistDetails = true, artistDetails = null) }
            } else {
                _uiState.update { it.copy(isLoadingArtistDetails = true) }
            }

            if (artistId.startsWith("UC") || artistId.startsWith("U")) {
                // YOUTUBE ARTIST DETAILS FETCH
                try {
                    val artistPage = com.metrolist.innertube.YouTube.artist(artistId).getOrNull()

                    // Count total songs from all sections
                    val songCount = artistPage?.sections?.sumOf { section ->
                        section.items.count { it is com.metrolist.innertube.models.SongItem }
                    } ?: 0

                    // Helper to parse subscriber count string (e.g. "1.2M subscribers")
                    val subText = artistPage?.subscriberCountText?.split(" ")?.firstOrNull() ?: ""
                    val subCount = try {
                        val cleanSubText = subText.replace(",", ".").uppercase()
                        when {
                            cleanSubText.endsWith("M") -> (cleanSubText.dropLast(1).toDouble() * 1_000_000).toLong()
                            cleanSubText.endsWith("K") -> (cleanSubText.dropLast(1).toDouble() * 1_000).toLong()
                            cleanSubText.endsWith("B") -> (cleanSubText.dropLast(1).toDouble() * 1_000_000_000).toLong()
                            else -> cleanSubText.filter { it.isDigit() || it == '.' }.toDouble().toLong()
                        }
                    } catch (e: Exception) { 0L }

                    val details = ArtistDetails(
                        id = artistId,
                        name = artistPage?.artist?.title ?: "YouTube Artist",
                        photoUrl = artistPage?.artist?.thumbnail,
                        description = artistPage?.description,
                        totalSongs = songCount,
                        followerCount = subCount,
                        totalPlays = 0,
                        isFollowed = false
                    )
                    _uiState.update { it.copy(isLoadingArtistDetails = false, artistDetails = details) }
                } catch (e: Exception) {
                   Timber.tag("DEBUG_PLAYER").e( "Failed to fetch YouTube artist details: ${e.message}")
                    _uiState.update { it.copy(isLoadingArtistDetails = false) }
                }
                return@launch
            }

            // Get Current User ID from Supabase (Single Source of Truth)
            val user = SupabaseManager.client.auth.currentUserOrNull()
            val userId = user?.id

            if (userId != null) {
                val details = repository.getArtistDetails(userId, artistId)
                _uiState.update { it.copy(isLoadingArtistDetails = false, artistDetails = details) }
            } else {
                _uiState.update { it.copy(isLoadingArtistDetails = false) }
            }
        }
    }


    fun PlayMusicViewModel.fetchPlaylistDetails(playlistId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update {
                it.copy(
                    isLoadingArtistDetails = true,
                    isArtistSongsLoading = true, // Set to true here to trigger skeleton
                    currentPlaylistDetails = null,
                    playlistOwner = null,
                    artistSongs = emptyList(), // clear previous songs
                    artistPage = null // Clear Innertube artist page leak
                )
            }
            try {
                if (playlistId == "LIKED_SONGS") {
                    val userId = UserManager.currentUser?.uid
                    if (userId == null) {
                       Timber.tag("DEBUG_PLAYER").e( "Error: User ID is null when fetching Liked Songs")
                        _uiState.update { it.copy(isLoadingArtistDetails = false, isArtistSongsLoading = false) }
                        return@launch
                    }

                    try {
                       Timber.tag("DEBUG_PLAYER").d( "Fetching Liked Songs for user: $userId")
                        val likedSongsRel = SupabaseManager.client.from("user_song_likes")
                            .select { 
                                filter { eq("user_id", userId) } 
                                order("position", io.github.jan.supabase.postgrest.query.Order.DESCENDING)
                                order("created_at", io.github.jan.supabase.postgrest.query.Order.DESCENDING)
                            }
                            .decodeList<PlaylistSongRelation>()

                       Timber.tag("DEBUG_PLAYER").d( "Found ${likedSongsRel.size} liked songs relations")

                        val likedSongIds = likedSongsRel.map { it.songId }
                        if (likedSongIds.isEmpty()) {
                           Timber.tag("DEBUG_PLAYER").d( "Liked songs relation is empty.")
                            _uiState.update {
                                it.copy(
                                    currentPlaylistDetails = null,
                                    playlistOwner = null,
                                    isLoadingArtistDetails = false,
                                    artistSongs = emptyList(),
                                    isArtistSongsLoading = false,
                                    artistSongsEndReached = true
                                )
                            }
                            return@launch
                        }

                       Timber.tag("DEBUG_PLAYER").d( "Fetching song details for IDs: $likedSongIds")
                        // Request real songs from Supabase based on ID
                        val songs = SupabaseManager.client.from("songs")
                            .select { filter { isIn("id", likedSongIds) } }
                            .decodeList<Song>()

                       Timber.tag("DEBUG_PLAYER").d( "Found ${songs.size} actual songs")

                        val artistIds = songs.mapNotNull { it.artistId }.distinct()
                       Timber.tag("DEBUG_PLAYER").d( "Fetching ${artistIds.size} distinct artists")
                        val artistsData = if (artistIds.isNotEmpty()) {
                            SupabaseManager.client.from("artists")
                                .select { filter { isIn("id", artistIds) } }
                                .decodeList<Artist>().associateBy { it.id }
                        } else emptyMap()

                        // Match songs to artist and preserve order from user_song_likes if possible
                        val songMap = songs.associateBy { it.id }
                        val finalSongsWithArtist = likedSongsRel.mapNotNull { rel ->
                            val song = songMap[rel.songId]
                            if (song != null) {
                                SongWithArtist(
                                    song = song,
                                    artist = song.artistId?.let { artistsData[it] },
                                    addedAt = rel.createdAt ?: rel.addedAt ?: song.createdAt
                                )
                            } else null
                        }

                       Timber.tag("DEBUG_PLAYER").d( "Successfully constructed ${finalSongsWithArtist.size} SongWithArtist objects")

                        _uiState.update {
                            it.copy(
                                currentPlaylistDetails = null, // No playlist details for Liked Songs
                                playlistOwner = null,
                                isLoadingArtistDetails = false,
                                artistSongs = finalSongsWithArtist,
                                isArtistSongsLoading = false,
                                artistSongsEndReached = true
                            )
                        }
                    } catch (e: Exception) {
                       Timber.tag("DEBUG_PLAYER").e( "Error fetching liked songs: ${e.message}", e)
                        _uiState.update {
                            it.copy(
                                currentPlaylistDetails = null,
                                playlistOwner = null,
                                isLoadingArtistDetails = false,
                                artistSongs = emptyList(),
                                isArtistSongsLoading = false,
                                artistSongsEndReached = true
                            )
                        }
                    }
                    return@launch
                }

                // Normal Playlist Fetching
                // Fetch Playlist
                val playlist = SupabaseManager.client.from("playlists")
                    .select {
                        filter {
                            eq("id", playlistId)
                            // If we want to strictly enforce it's public (though for owner it shouldn't matter, but official is public)
                        }
                    }
                    .decodeSingleOrNull<com.example.remusic.data.model.Playlist>()

                // Fetch Owner if exists
                var owner: User? = null
                val ownerId = playlist?.ownerUserId
                if (ownerId != null) {
                    owner = SupabaseManager.client.from("users")
                        .select { filter { eq("id", ownerId) } }
                        .decodeSingleOrNull<User>()
                }

                // Fetch User Playlist Songs
                val playlistSongsRelation = SupabaseManager.client.from("playlist_songs")
                    .select { 
                        filter { eq("playlist_id", playlistId) } 
                        order("position", io.github.jan.supabase.postgrest.query.Order.DESCENDING)
                        order("added_at", io.github.jan.supabase.postgrest.query.Order.DESCENDING)
                    }
                    .decodeList<PlaylistSongRelation>()

                val songIds = playlistSongsRelation.map { it.songId }.distinct()
                var finalSongsWithArtist: List<SongWithArtist> = emptyList()

                if (songIds.isNotEmpty()) {
                    // Fetch real songs
                    val songsData = SupabaseManager.client.from("songs")
                        .select {
                            filter { isIn("id", songIds) }
                        }.decodeList<Song>()

                    // Fetch associated artists
                    val artistIds = songsData.mapNotNull { it.artistId }.distinct()
                    val artistsData = if (artistIds.isNotEmpty()) {
                        SupabaseManager.client.from("artists")
                            .select {
                                filter { isIn("id", artistIds) }
                            }.decodeList<Artist>().associateBy { it.id }
                    } else {
                        emptyMap()
                    }

                    // Map songs into SongWithArtist and maintain playlist order (or added_at order)
                    val songMap = songsData.associateBy { it.id }
                    finalSongsWithArtist = playlistSongsRelation.mapNotNull { rel ->
                        val song = songMap[rel.songId]
                        if (song != null) {
                            SongWithArtist(
                                song = song,
                                artist = song.artistId?.let { artistsData[it] },
                                addedAt = rel.addedAt ?: rel.createdAt ?: song.createdAt
                            )
                        } else null
                    }
                }

                _uiState.update {
                    it.copy(
                        currentPlaylistDetails = playlist,
                        playlistOwner = owner,
                        isLoadingArtistDetails = false,
                        artistSongs = finalSongsWithArtist,
                        isArtistSongsLoading = false,
                        artistSongsEndReached = true // Pagination not supported yet for user playlists
                    )
                }
            } catch (e: Exception) {
               Timber.tag("DEBUG_PLAYER").e( "Error fetching playlist details: ${e.message}")
                _uiState.update {
                    it.copy(
                        currentPlaylistDetails = null,
                        playlistOwner = null,
                        isLoadingArtistDetails = false,
                        isArtistSongsLoading = false
                    )
                }
            }
        }
    }


    fun PlayMusicViewModel.fetchArtistSongs(artistId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            // Check memory cache first
            artistCache[artistId]?.let { cachedData ->
                _uiState.update {
                    it.copy(
                        artistSongs = cachedData.artistSongs,
                        artistPage = cachedData.artistPage,
                        artistDetails = cachedData.artistDetails,
                        isArtistSongsLoading = false,
                        isLoadingArtistDetails = false,
                        artistSongsEndReached = true,
                        artistSongsPage = 0
                    )
                }
                return@launch
            }

            // Reset State for new artist
            _uiState.update {
                it.copy(
                        artistSongs = emptyList(),
                        isArtistSongsLoading = true,
                        isLoadingArtistDetails = true,
                        artistSongsEndReached = false,
                        artistSongsPage = 0
                )
            }

            // --- YOUTUBE ARTIST INTERCEPTOR ---
            if (artistId.startsWith("UC") || artistId.startsWith("U")) {
                try {
                    val artistPage = com.metrolist.innertube.YouTube.artist(artistId).getOrNull()
                    val ytSongs = mutableListOf<SongWithArtist>()

                    val artistName = artistPage?.artist?.title ?: "YouTube Artist"
                    val artistPhoto = artistPage?.artist?.thumbnail

                    artistPage?.sections?.forEach { section ->
                        section.items.forEach { item ->
                            if (item is com.metrolist.innertube.models.SongItem) {
                                val ytSong = SongWithArtist(
                                    song = Song(
                                        id = item.id,
                                        title = item.title,
                                        coverUrl = item.thumbnail,
                                        source = "youtube",
                                        featuredArtists = item.artists.drop(1).map { it.name }
                                    ),
                                    artist = Artist(
                                        id = artistId,
                                        name = artistName
                                    )
                                )
                                if (ytSongs.none { it.song.id == ytSong.song.id }) {
                                    ytSongs.add(ytSong)
                                }
                            }
                        }
                    }

                    // --- FETCH MORE SONGS IF AVAILABLE (Pagination/See All) ---
                    val songsSection = artistPage?.sections?.find {
                        it.title.contains("Song", ignoreCase = true) ||
                        it.title.contains("Lagu", ignoreCase = true)
                    }

                    val moreEndpoint = songsSection?.moreEndpoint
                    if (moreEndpoint != null) {
                        try {
                            val moreSongsPage = com.metrolist.innertube.YouTube.artistItems(moreEndpoint).getOrNull()
                            moreSongsPage?.items?.filterIsInstance<com.metrolist.innertube.models.SongItem>()?.forEach { item ->
                                val ytSong = SongWithArtist(
                                    song = Song(
                                        id = item.id,
                                        title = item.title,
                                        coverUrl = item.thumbnail,
                                        source = "youtube",
                                        featuredArtists = item.artists.drop(1).map { it.name }
                                    ),
                                    artist = Artist(
                                        id = artistId,
                                        name = artistName
                                    )
                                )
                                if (ytSongs.none { it.song.id == ytSong.song.id }) {
                                    ytSongs.add(ytSong)
                                }
                            }
                        } catch (e: Exception) {
                           Timber.tag("DEBUG_PLAYER").e( "Failed to fetch more YouTube songs: ${e.message}")
                        }
                    }

                    // Count total songs
                    val songCount = ytSongs.size

                    // Parse subscriber count for fetchArtistSongs path too
                    val subText = artistPage?.subscriberCountText?.split(" ")?.firstOrNull() ?: ""
                    val subCount = try {
                        val cleanSubText = subText.replace(",", ".").uppercase()
                        when {
                            cleanSubText.endsWith("M") -> (cleanSubText.dropLast(1).toDouble() * 1_000_000).toLong()
                            cleanSubText.endsWith("K") -> (cleanSubText.dropLast(1).toDouble() * 1_000).toLong()
                            cleanSubText.endsWith("B") -> (cleanSubText.dropLast(1).toDouble() * 1_000_000_000).toLong()
                            else -> cleanSubText.filter { it.isDigit() || it == '.' }.toDouble().toLong()
                        }
                    } catch (e: Exception) { 0L }

                    val finalSongs = ytSongs.distinctBy { song -> song.song.id }
                    val finalDetails = ArtistDetails(
                        id = artistId,
                        name = artistName,
                        photoUrl = artistPhoto,
                        description = artistPage?.description,
                        totalSongs = songCount,
                        followerCount = subCount
                    )

                    // Save to cache
                    artistCache[artistId] = ArtistCacheData(
                        artistSongs = finalSongs,
                        artistPage = artistPage,
                        artistDetails = finalDetails
                    )

                    _uiState.update {
                        it.copy(
                                artistSongs = finalSongs,
                                isArtistSongsLoading = false,
                                isLoadingArtistDetails = false, // Stop the skeleton loading!
                                artistSongsEndReached = true, // No pagination for now
                                artistPage = artistPage, // Save raw artist page for advanced UI
                                artistDetails = finalDetails
                        )
                    }
                } catch (e: Exception) {
                   Timber.tag("DEBUG_PLAYER").e( "Failed to fetch YouTube artist: ${e.message}")
                    _uiState.update { it.copy(isArtistSongsLoading = false) }
                }
                return@launch
            }
            // --- END YOUTUBE INTERCEPTOR ---

            val limit = 100
            val newSongs =
                    repository.getSongsByArtistPagination(artistId, limit = limit, offset = 0)

            _uiState.update {
                it.copy(
                        artistSongs = newSongs,
                        isArtistSongsLoading = false,
                        artistSongsEndReached = newSongs.size < limit,
                        artistSongsPage = 1, // Next page index
                        artistPage = null // Clear Innertube artist page leak
                )
            }
        }
    }


    fun PlayMusicViewModel.loadMoreArtistSongs(artistId: String) {
        if (_uiState.value.isArtistSongsLoading || _uiState.value.artistSongsEndReached) return

        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isArtistSongsLoading = true) }

            val limit = 100
            val currentPage = _uiState.value.artistSongsPage
            val offset = currentPage * limit

            val newSongs =
                    repository.getSongsByArtistPagination(artistId, limit = limit, offset = offset)

            _uiState.update { state ->
                state.copy(
                        artistSongs = state.artistSongs + newSongs, // Append info
                        isArtistSongsLoading = false,
                        artistSongsEndReached = newSongs.size < limit,
                        artistSongsPage = currentPage + 1
                )
            }
        }
    }


    fun PlayMusicViewModel.fetchUploaderSongs(uploaderId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update {
                it.copy(
                    artistSongs = emptyList(),
                    isArtistSongsLoading = true,
                    artistSongsEndReached = false,
                    artistSongsPage = 0,
                    isLoadingArtistDetails = true,
                    artistPage = null // Clear Innertube artist page leak
                )
            }

            try {
                // Fetch public playlists
                val publicPlaylists = try {
                    SupabaseManager.client.from("playlists")
                        .select {
                            filter {
                                eq("owner_user_id", uploaderId)
                                eq("visibility", "public")
                            }
                            order("created_at", io.github.jan.supabase.postgrest.query.Order.DESCENDING)
                        }.decodeList<com.example.remusic.data.model.Playlist>()
                } catch (e: Exception) {
                    emptyList()
                }

                // Fetch uploader details to show in header (reusing ArtistDetails for UI compatibility)
                val user = SupabaseManager.client.from("users").select {
                    filter { eq("id", uploaderId) }
                }.decodeSingleOrNull<User>()

                if (user != null) {
                    _uiState.update {
                        it.copy(
                            artistDetails = ArtistDetails(
                                id = user.uid,
                                name = user.displayName ?: "Uploader",
                                photoUrl = user.photoUrl,
                                description = "Lagu yang diunggah oleh ${user.displayName}"
                            ),
                            isLoadingArtistDetails = false,
                            userPublicPlaylists = publicPlaylists
                        )
                    }
                }

                val limit = 50
                val songs = repository.getSongsByUploaderPagination(uploaderId, limit = limit, offset = 0)

                _uiState.update {
                    it.copy(
                        artistSongs = songs,
                        isArtistSongsLoading = false,
                        artistSongsEndReached = songs.size < limit,
                        artistSongsPage = 1
                    )
                }
            } catch (e: Exception) {
                Timber.tag("DEBUG_PLAYER").e("Failed to fetch uploader songs: ${e.message}")
                _uiState.update { it.copy(isArtistSongsLoading = false, isLoadingArtistDetails = false) }
            }
        }
    }


    fun PlayMusicViewModel.loadMoreUploaderSongs(uploaderId: String) {
        if (_uiState.value.isArtistSongsLoading || _uiState.value.artistSongsEndReached) return

        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isArtistSongsLoading = true) }

            val limit = 50
            val currentPage = _uiState.value.artistSongsPage
            val offset = currentPage * limit

            val newSongs = repository.getSongsByUploaderPagination(uploaderId, limit = limit, offset = offset)

            _uiState.update { state ->
                state.copy(
                    artistSongs = state.artistSongs + newSongs,
                    isArtistSongsLoading = false,
                    artistSongsEndReached = newSongs.size < limit,
                    artistSongsPage = currentPage + 1
                )
            }
        }
    }



    fun PlayMusicViewModel.fetchUserPlaylists() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isFetchingUserPlaylists = true) }
            try {
                val userId = SupabaseManager.client
                    .auth
                    .currentUserOrNull()?.id ?: return@launch
                val playlists = SupabaseManager.client.from("playlists")
                    .select {
                        filter {
                            eq("owner_user_id", userId)
                        }
                    }.decodeList<com.example.remusic.data.model.Playlist>()

                _uiState.update {
                    it.copy(
                        userPlaylists = playlists,
                        isFetchingUserPlaylists = false
                    )
                }
            } catch (e: Exception) {
               Timber.tag("DEBUG_PLAYER").e( "Error fetching user playlists: ${e.message}")
                _uiState.update { it.copy(isFetchingUserPlaylists = false) }
            }
        }
    }



    fun PlayMusicViewModel.addSongToPlaylist(playlistId: String, songId: String, songWithArtist: SongWithArtist? = null, onResult: (Boolean, String) -> Unit) {
        val songMetadata = songWithArtist?.song
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val userId = SupabaseManager.client
                    .auth
                    .currentUserOrNull()?.id ?: return@launch

                // --- SHADOW ENTRY LOGIC ---
                val isYouTubeId = songId.length == 11 || songMetadata?.source?.startsWith("youtube") == true
                val effectiveSongId = if (isYouTubeId && songMetadata != null) {
                   Timber.tag("DEBUG_PLAYER").d( "🔍 [PLAYLIST] YouTube song, ensuring shadow entry exists...")
                    try {
                        val uuid = repository.upsertYouTubeSong(
                            song = songMetadata,
                            artistName = songWithArtist.artist?.name,
                            artistPhoto = songWithArtist.artist?.photoUrl
                        )
                        if (uuid == null) {
                            withContext(Dispatchers.Main) { onResult(false, "Gagal menambahkan referensi lagu ke server.") }
                            return@launch
                        }
                        uuid
                    } catch (e: Exception) {
                        Timber.tag("DEBUG_PLAYER").e(e, "Error adding shadow entry: %s", e.message)
                        withContext(Dispatchers.Main) { onResult(false, "Error Database: ${e.message}") }
                        return@launch
                    }
                } else {
                    songId
                }

                // Cek apakah lagu sudah ada di playlist
                val existing = SupabaseManager.client.from("playlist_songs")
                    .select {
                        filter {
                            eq("playlist_id", playlistId)
                            eq("song_id", effectiveSongId)
                        }
                    }.decodeList<kotlinx.serialization.json.JsonObject>()
                    
                if (existing.isNotEmpty()) {
                    withContext(Dispatchers.Main) { onResult(false, "Lagu sudah ada di playlist ini") }
                    return@launch
                }
                // Cari max position saat ini agar lagu baru selalu di atas
                val maxPosResult = SupabaseManager.client.from("playlist_songs")
                    .select(columns = io.github.jan.supabase.postgrest.query.Columns.list("position")) {
                        filter { eq("playlist_id", playlistId) }
                        order("position", io.github.jan.supabase.postgrest.query.Order.DESCENDING)
                        limit(1)
                    }.decodeList<kotlinx.serialization.json.JsonObject>()
                
                var maxPos = 0L
                if (maxPosResult.isNotEmpty()) {
                    maxPos = maxPosResult.first()["position"]?.jsonPrimitive?.longOrNull ?: 0L
                }

                val insertData = PlaylistSongInsertReq(
                    playlistId = playlistId,
                    songId = effectiveSongId,
                    addedBy = userId,
                    position = maxPos + 1L
                )
                SupabaseManager.client.from("playlist_songs").insert(insertData)
                withContext(Dispatchers.Main) { onResult(true, "Berhasil menambahkan lagu ke playlist") }
            } catch (e: Exception) {
               Timber.tag("DEBUG_PLAYER").e(e, "Error adding song to playlist: %s", e.message)
                withContext(Dispatchers.Main) { onResult(false, "Gagal menambahkan lagu: ${e.message}") }
            }
        }
    }

    @SuppressLint("LogNotTimber")
    fun PlayMusicViewModel.removeSongFromPlaylist(playlistId: String, songId: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val userId = SupabaseManager.client.auth.currentUserOrNull()?.id ?: return@launch
                
                SupabaseManager.client.from("playlist_songs")
                    .delete {
                        filter {
                            eq("playlist_id", playlistId)
                            eq("song_id", songId)
                        }
                    }
                
                // Update State Secara Optimistic/Local
                val currentSongs = _uiState.value.artistSongs
                val updatedSongs = currentSongs.filter { it.song.id != songId }
                _uiState.update { it.copy(artistSongs = updatedSongs) }

                withContext(Dispatchers.Main) { onResult(true, "Lagu dihapus dari playlist") }
            } catch (e: Exception) {
                Timber.tag("DEBUG_PLAYER").e(e, "Error removing song from playlist")
                withContext(Dispatchers.Main) { onResult(false, "Gagal menghapus lagu: ${e.message}") }
            }
        }
    }



// --- DATA CLASSES FOR FETCHING ---

@kotlinx.serialization.Serializable
internal data class PlaylistSongRelation(
        @kotlinx.serialization.SerialName("song_id") val songId: String,
        @kotlinx.serialization.SerialName("added_at") val addedAt: String? = null,
        @kotlinx.serialization.SerialName("created_at") val createdAt: String? = null
    )

@kotlinx.serialization.Serializable
internal data class PlaylistSongInsertReq(
        @kotlinx.serialization.SerialName("playlist_id") val playlistId: String,
        @kotlinx.serialization.SerialName("song_id") val songId: String,
        @kotlinx.serialization.SerialName("added_by") val addedBy: String,
        @kotlinx.serialization.SerialName("position") val position: Long? = null
    )

