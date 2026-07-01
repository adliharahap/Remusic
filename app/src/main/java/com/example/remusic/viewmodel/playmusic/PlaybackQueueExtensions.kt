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

// --- EXTENSION FUNCTIONS FOR CORE PLAYBACK & QUEUE ---


    fun PlayMusicViewModel.setPlaylist(
            songs: List<SongWithArtist>,
            startIndex: Int = 0,
            overridePlaylistName: String? = null
    ): Job? {
       Timber.tag("DEBUG_PLAYER").d(
                
                "📥 SET PLAYLIST: Menerima ${songs.size} lagu. Start Index: $startIndex"
        )

        // Reset YouTube Radio Pagination Tokens when starting a new playlist
        youtubeRadioEndpoint = null
        youtubeRadioContinuation = null

        // 1. Validasi Awal
        if (songs.isEmpty()) {
            Timber.tag("DEBUG_PLAYER").w( "setPlaylist: songs list kosong!")
            return null
        }

        val controller = mediaController
        if (controller == null) {
            Timber.tag("DEBUG_PLAYER").w( "Controller not ready, saving playlist for later.")
            _uiState.update { it.copy(playlist = songs, currentSong = songs[startIndex]) }
            return null
        }

       Timber.tag("DEBUG_PLAYER").d(
                
                "setPlaylist called with ${songs.size} songs, startIndex=$startIndex"
        )

        val finalPlaylistName = overridePlaylistName ?: _uiState.value.playingMusicFromPlaylist

        // 2. Update UI State Langsung (Biar user lihat judul lagu berubah cepat)
        _uiState.update {
            it.copy(
                    playlist = songs,
                    currentSong = songs[startIndex],
                    playingMusicFromPlaylist = finalPlaylistName
            )
        }

        // SAVE QUEUE TO DB
        viewModelScope.launch(Dispatchers.IO) {
            repository.queue.savePlaybackQueue(songs, finalPlaylistName)
            userPreferencesRepository.saveLastPlaylistName(finalPlaylistName)
        }

        // Reset Smart Retry Flag
        hasRetriedCurrentSong = false

        // 3. MULAI PROSES ASYNC (Resolve URL & Warna)
        // Kita bungkus semua proses mapping & player setup di sini
        // RETURN JOB agar bisa ditunggu (join)
        return viewModelScope.launch(Dispatchers.IO) {

            // A. Ambil Lagu yang mau dimainkan
            val songToPlay = songs.getOrNull(startIndex) ?: return@launch

           Timber.tag("DEBUG_PLAYER").d(
                    
                    "⚡ PROCESS: Resolving URL untuk lagu pertama (Index $startIndex): ${songToPlay.song.title}"
            )
            // B. Resolve URL (Tunggu sebentar request ke Vercel/Cache)
            // Ini langkah kuncinya! Kita putuskan mau pakai link Telegram atau AudioURL biasa
            val resolvedResult = resolveSongUrl(songToPlay)
            val resolvedUrl = resolvedResult.url
           Timber.tag("DEBUG_PLAYER").d( "URL Resolved for first song: $resolvedUrl")

            // Cek Error Network di awal play
            if (resolvedResult.errorType == MusicRepository.ErrorType.NETWORK) {
                withContext(Dispatchers.Main) {
                    _uiState.update {
                        it.copy(
                                hasError = true,
                                errorType = "Gagal memuat: Masalah Koneksi Internet",
                                isPlaying = false
                        )
                    }
                }
                // Masih lanjut coba play (mungkin ada cache parsial?), atau stop?
                // Kita lanjut aja, nanti ExoPlayer bakal error juga kalau URL kosong, tapi minimal
                // UI udah kasih tau.
            }

            // C. Fetch Data Lengkap (Lirik, dll) untuk lagu pertama
            fetchFullSongDetails(songToPlay.song.effectiveId)

            // C. Extract Warna (Sambil jalan - JANGAN BLOCK MEDIA SETUP)
            launch {
                val colors =
                        extractGradientColorsFromImageUrl(
                                context = getApplication(),
                                imageUrl = songToPlay.song.coverUrl ?: ""
                        )
                // Update warna ke UI
                _uiState.update { it.copy(dominantColors = colors) }
                // Save to preferences for HomeScreen
                userPreferencesRepository.saveLastSongColor(colors.getOrElse(0) { Color(0xFF755D8D) })
            }

           Timber.tag("DEBUG_PLAYER").d( "🛠 MAPPING: Membuat MediaItems...")

            // D. Mapping jadi MediaItems (DI DALAM COROUTINE)
            // Kenapa di dalam? Karena kita butuh 'resolvedUrl' untuk index == startIndex
            val mediaItems =
                    songs.mapIndexed { index, s ->
                        val meta =
                                MediaMetadata.Builder()
                                        .setTitle(s.song.title)
                                        .setArtist(s.artist?.name)
                                        .setArtworkUri(
                                                s.song.coverUrl?.takeIf { it.isNotBlank() }?.toUri()
                                        )
                                        .build()

                        // LOGIKA PENTING:
                        // Jika ini lagu yang mau dimainkan (startIndex), pakai 'resolvedUrl' (hasil
                        // Vercel).
                        // Jika lagu lain, pakai 'audioUrl' biasa dulu (nanti di-prefetch sambil
                        // jalan).
                        val urlToUse =
                                if (index == startIndex) resolvedUrl else (s.song.audioUrl ?: "")

                        val item =
                                MediaItem.Builder()
                                        .setMediaId(s.song.id)
                                        .setUri(urlToUse)
                                        .setMediaMetadata(meta)
                                        .build()

                        // Debug log (Sesuai kode Mas)
                        if (index == startIndex) {
                           Timber.tag("DEBUG_PLAYER").d(
                                    
                                    "   🎵 [Index $index] (ACTIVE) ${s.song.title} -> $urlToUse"
                            )
                        } else {
                           Timber.tag("DEBUG_PLAYER").d(
                                    
                                    "   ❓ [Index $index] (QUEUE) URL Masih Mentah: $urlToUse"
                            )
                        }
                        item
                    }

           Timber.tag("DEBUG_PLAYER").d( "Total MediaItems mapped: ${mediaItems.size}")

            // E. Set ke MediaController (Pindah ke Main Thread)
            // Wajib pakai withContext(Main) karena menyentuh UI/Player
            withContext(Dispatchers.Main) {
               Timber.tag("DEBUG_PLAYER").d( "▶ PLAYER: Menyiapkan dan memutar playlist...")
                mediaController?.apply {
                    setMediaItems(mediaItems, startIndex, C.TIME_UNSET)
                   Timber.tag("DEBUG_PLAYER").d(
                            
                            "MediaItems set to controller, playing index=$startIndex"
                    )
                    prepare()
                    play()
                }
                        ?:Timber.tag("DEBUG_PLAYER").e( "mediaController is null inside coroutine!")
            }
        }
    }


    // --- FITUR BARU: PLAY WITH SMART QUEUE (UNTUK HOME SCREEN QUICK PICK) ---
    fun PlayMusicViewModel.playSongWithSmartQueue(seedSong: SongWithArtist, playlistName: String = "Unknown Playlist") {
       Timber.tag("DEBUG_PLAYER").d( "🚀 START SMART QUEUE for: ${seedSong.song.title} in $playlistName")

        // 1. Play lagu yang diklik DULUAN (Single Item Playlist)
        // Set startIndex = 0 karena cuma ada 1 lagu, dan sertakan nama playlist
        val playJob = setPlaylist(listOf(seedSong), 0, playlistName)

        // 2. Fetch Smart Queue di background jangan ganggu UI/Playback
        viewModelScope.launch(Dispatchers.IO) {
            // Tunggu sebentar sampai lagu utama mulai disiapkan
            playJob?.join()

            if (seedSong.song.id.startsWith("offline_")) {
               Timber.tag("DEBUG_PLAYER").d( "Skip Smart Queue untuk offline song")
                return@launch
            }

            // Route: YouTube songs → YouTube recommendations, others → Supabase smart queue
            if (seedSong.song.source == "youtube") {
               Timber.tag("DEBUG_PLAYER").d( "🎵 [YT] Fetching YouTube recommendations for: ${seedSong.song.title}")
                fetchYouTubeRecommendations(seedSong)
                return@launch
            }

            // Fetch Smart Queue (List<Song>)
            val relatedSongs = repository.fetchSmartQueue(seedSong.song)

            if (relatedSongs.isNotEmpty()) {
               Timber.tag("DEBUG_PLAYER").d(
                        
                        "✅ Smart Queue returned ${relatedSongs.size} songs. Fetching artist details..."
                )

                // 3. Kita butuh detail Artist biar nama artis muncul di Queue
                // Kumpulkan semua artistId dari hasil smart queue
                val artistIds = relatedSongs.mapNotNull { it.artistId }

                // Fetch artists in bulk
                val artistsMap = repository.social.fetchArtistsByIds(artistIds).associateBy { it.id }

                // 4. Gabungkan Song + Artist jadi SongWithArtist
                val queueWithArtists =
                        relatedSongs.map { song ->
                            SongWithArtist(
                                    song = song,
                                    artist =
                                            song.artistId?.let {
                                                artistsMap[it]
                                            } // Match artist or null
                            )
                        }

                // --- LOGGING UNTUK ANALISIS USER ---
               Timber.tag("DEBUG_PLAYER").d(
                        
                        "=== 📋 SMART QUEUE RESULT (${queueWithArtists.size} Songs) ==="
                )
                queueWithArtists.forEachIndexed { index, item ->
                    // Log format: [No] Judul - Artist | Lang: .. | Moods: ..
                    val moodsStr = (item.song.moods ?: emptyList()).joinToString(", ")
                   Timber.tag("DEBUG_PLAYER").d(
                            
                            "Queue [#${index + 1}]: ${item.song.title} - ${item.artist?.name ?: "Unknown"} | Lang: ${item.song.language} | Moods: [$moodsStr]"
                    )
                }
               Timber.tag("DEBUG_PLAYER").d(
                        
                        "==========================================================="
                )

                // 5. Append ke Playlist saat ini
                val currentPlaylist = _uiState.value.playlist.toMutableList()

                // Cek duplikasi (jangan add kalau sudah ada)
                val existingIds = currentPlaylist.map { it.song.id }.toSet()
                val uniqueNewSongs = queueWithArtists.filter { !existingIds.contains(it.song.id) }

                currentPlaylist.addAll(uniqueNewSongs)

                // Simpan total playlist baru
                val finalPlaylist = currentPlaylist.toList()

                // FIX: Update Player Queue Persistance to SQLite
                repository.queue.savePlaybackQueue(finalPlaylist, playlistName)

                // Update UI State (Queue terlihat oleh user)
                _uiState.update { it.copy(playlist = finalPlaylist) }

                // 6. Update Player Queue (Diam-diam)
                withContext(Dispatchers.Main) {
                    val controller = mediaController ?: return@withContext
                    // Kita tambahkan lagu-lagu baru ke posisi setelah seed song
                    // Karena seed song ada di index 0 (atau current index),
                    // kita addMediaItems ke akhir queue.

                    val newMediaItems =
                            uniqueNewSongs.map { createMediaItem(it, it.song.audioUrl ?: "") }
                    controller.addMediaItems(newMediaItems)

                   Timber.tag("DEBUG_PLAYER").d(
                            
                            "✅ Added ${newMediaItems.size} songs to MediaController."
                    )
                }

                // 7. Prefetch lagu berikutnya (Index 1) agar siap dimainkan
                prefetchSongAtIndex(1)
            } else {
                Timber.tag("DEBUG_PLAYER").w( "⚠️ Smart Queue returned empty list.")
            }
        }
    }


    /**
     * Fetch 50 YouTube-recommended songs based on [seedSong] using YouTube.next() (automix).
     * Results are appended to the current queue silently, without interrupting playback.
     */
    suspend fun PlayMusicViewModel.fetchYouTubeRecommendations(seedSong: SongWithArtist) {
        val videoId = seedSong.song.id
        var isRetry = false

        while (true) {
            smartQueueMutex.withLock {
                try {
                    var finalUniqueNew = emptyList<SongWithArtist>()
                    var pagesFetched = 0
                    val maxPages = 3

                    while (pagesFetched < maxPages && finalUniqueNew.isEmpty()) {
                        var recommendedSongItems: List<com.metrolist.innertube.models.SongItem> = emptyList()
                        var attempt = 0

                        // Use existing endpoint or create a new one for this seed
                        if (youtubeRadioEndpoint == null) {
                            youtubeRadioEndpoint = com.metrolist.innertube.models.WatchEndpoint(
                                videoId = videoId,
                                playlistId = "RDAMVM$videoId"
                            )
                        }

                        while (attempt < 3 && recommendedSongItems.isEmpty()) {
                           Timber.tag("DEBUG_PLAYER").d( "▶ YouTube.next(endpoint=${youtubeRadioEndpoint?.videoId}, continuation=$youtubeRadioContinuation)")

                            val endpoint = youtubeRadioEndpoint!!
                            val nextResult = com.metrolist.innertube.YouTube.next(endpoint, youtubeRadioContinuation).getOrNull()

                            if (nextResult != null) {
                                youtubeRadioEndpoint = nextResult.endpoint
                                youtubeRadioContinuation = nextResult.continuation
                            }

                            var items = nextResult?.items ?: emptyList()

                            val relEndpoint = nextResult?.relatedEndpoint
                            if (items.size <= 1 && relEndpoint != null) {
                               Timber.tag("DEBUG_PLAYER").d( "⚠️ [YT RECO] Automix empty, falling back to relatedEndpoint")
                                val relatedPage = com.metrolist.innertube.YouTube.related(relEndpoint).getOrNull()
                                if (relatedPage != null && relatedPage.songs.isNotEmpty()) {
                                    items = items + relatedPage.songs
                                }
                            }

                            recommendedSongItems = items
                                .filter { it.id != videoId }
                                .distinctBy { it.id }

                            if (recommendedSongItems.isEmpty()) {
                                if (youtubeRadioContinuation != null) {
                                    Timber.tag("DEBUG_PLAYER").w( "⚠️ [YT RECO] Attempt ${attempt+1}: Empty items with continuation. Clearing continuation and retrying...")
                                    youtubeRadioContinuation = null
                                } else if (youtubeRadioEndpoint?.playlistId != null) {
                                    Timber.tag("DEBUG_PLAYER").w( "⚠️ [YT RECO] Attempt ${attempt+1}: Empty items with playlistId. Retrying without playlistId.")
                                    youtubeRadioEndpoint = com.metrolist.innertube.models.WatchEndpoint(videoId = videoId)
                                }
                            }
                            attempt++
                        }

                        if (recommendedSongItems.isEmpty()) break

                        val converted = recommendedSongItems.map { item ->
                            val primaryArtist = item.artists.firstOrNull()
                            val aId = primaryArtist?.id ?: ""
                            val aName = primaryArtist?.name ?: "YouTube Music"
                            val song = Song(
                                id = item.id,
                                title = item.title,
                                coverUrl = item.thumbnail,
                                source = "youtube",
                                artistId = aId.takeIf { it.isNotBlank() },
                                featuredArtists = item.artists.drop(1).map { it.name },
                                telegramFileId = null,
                                audioUrl = null
                            )
                            val artist = Artist(id = aId, name = aName)
                            SongWithArtist(song, artist)
                        }

                        val musicDao = MusicDatabase.getDatabase(getApplication()).musicDao()
                        converted.forEach { swa ->
                            if (musicDao.getSongById(swa.song.id) == null) {
                                musicDao.insertSong(
                                    com.example.remusic.data.local.entity.CachedSong(
                                        id = swa.song.id,
                                        title = swa.song.title,
                                        artistName = swa.artist?.name ?: "YouTube Music",
                                        coverUrl = swa.song.coverUrl,
                                        lyrics = null,
                                        uploaderUserId = null,
                                        telegramFileId = null,
                                        telegramDirectUrl = null,
                                        source = "youtube",
                                        artistId = swa.song.artistId,
                                        urlExpiryTime = 0L,
                                        lastPlayedAt = 0L
                                    )
                                )
                            }
                        }

                        val existingIds = _uiState.value.playlist.map { it.song.id }.toSet()
                        val uniqueOnThisPage = converted.filter { it.song.id !in existingIds }

                        if (uniqueOnThisPage.isNotEmpty()) {
                            finalUniqueNew = uniqueOnThisPage.take(50)
                           Timber.tag("DEBUG_PLAYER").d( "✅ [YT RECO] Found ${finalUniqueNew.size} unique songs on page ${pagesFetched + 1}")
                        } else {
                            Timber.tag("DEBUG_PLAYER").w( "⚠️ [YT RECO] Page ${pagesFetched + 1} had no unique songs. Advancing to next page via continuation...")
                            if (youtubeRadioContinuation == null) break
                        }
                        pagesFetched++
                    }

                    if (finalUniqueNew.isEmpty()) {
                        if (!isRetry) {
                            Timber.tag("DEBUG_PLAYER").w( "⚠️ [YT RECO] FAILED after $pagesFetched pages. Resetting session and trying fresh seed...")
                            youtubeRadioEndpoint = null
                            youtubeRadioContinuation = null
                            isRetry = true
                            return@withLock // This will exit the lock and loop again because finalUniqueNew is empty
                        } else {
                           Timber.tag("DEBUG_PLAYER").e( "❌ [YT RECO] FAILED even after session reset. Giving up.")
                            return
                        }
                    }

                    val currentPlaylist = _uiState.value.playlist.toMutableList()
                    currentPlaylist.addAll(finalUniqueNew)
                    val finalPlaylist = currentPlaylist.toList()
                    _uiState.update { it.copy(playlist = finalPlaylist) }
                    repository.queue.savePlaybackQueue(finalPlaylist, _uiState.value.playingMusicFromPlaylist)

                    withContext(Dispatchers.Main) {
                        val controller = mediaController ?: return@withContext
                        val newMediaItems = finalUniqueNew.map { createMediaItem(it, "") }
                        controller.addMediaItems(newMediaItems)
                       Timber.tag("DEBUG_PLAYER").d( "✅ [YT RECO] Added ${newMediaItems.size} to MediaController queue")
                    }
                    return // SUCCESS, exit the while(true) loop

                } catch (e: Exception) {
                   Timber.tag("DEBUG_PLAYER").e( "❌ [YT RECO] fetchYouTubeRecommendations failed: ${e.message}")
                    return
                }
            } // end Mutex lock
        } // end while(true) retry loop
    }


    fun PlayMusicViewModel.playAudioFromUri(uri: android.net.Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val retriever = android.media.MediaMetadataRetriever()
            var title = uri.lastPathSegment ?: "Local Audio"
            var artistName = "Local Device"
            var coverUrl: String? = null

            try {
                retriever.setDataSource(getApplication(), uri)
                val extractedTitle = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_TITLE)
                if (!extractedTitle.isNullOrBlank()) title = extractedTitle

                val extractedArtist = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_ARTIST)
                if (!extractedArtist.isNullOrBlank()) artistName = extractedArtist

                val artBytes = retriever.embeddedPicture
                if (artBytes != null) {
                    val cacheDir = getApplication<Application>().cacheDir
                    val artFile = java.io.File(cacheDir, "cover_${System.currentTimeMillis()}.jpg")
                    java.io.FileOutputStream(artFile).use { fos ->
                        fos.write(artBytes)
                    }
                    coverUrl = artFile.toURI().toString()
                }
            } catch (e: Exception) {
                Timber.tag("DEBUG_PLAYER").e("Error extracting metadata from URI: ${e.message}")
            } finally {
                try { retriever.release() } catch (e: Exception) {}
            }

            val song = com.example.remusic.data.model.Song(
                id = "offline_${System.currentTimeMillis()}",
                title = title,
                source = "offline",
                audioUrl = uri.toString(),
                coverUrl = coverUrl
            )
            val songWithArtist = com.example.remusic.data.model.SongWithArtist(
                song = song,
                artist = com.example.remusic.data.model.Artist(id = "local", name = artistName)
            )

            withContext(Dispatchers.Main) {
                setPlaylist(listOf(songWithArtist), 0, "Local Playback")
            }
        }
    }


    // Re-resolve URL lagu saat ini dan langsung play (dipanggil dari onPlayerError 4xx)
    fun PlayMusicViewModel.resolveAndRetryCurrentSong() {
        val currentSong = _uiState.value.currentSong ?: return
        val controller = mediaController ?: return
        val currentIndex = _uiState.value.currentSongIndex

        viewModelScope.launch(Dispatchers.IO) {
            Timber.tag("DEBUG_PLAYER").d("🔄 [RESOLVE RETRY] Re-resolving URL untuk: ${currentSong.song.title}")
            try {
                val result = resolveSongUrl(currentSong)
                if (result.url.isNotBlank()) {
                    val newItem = createMediaItem(currentSong, result.url)
                    withContext(Dispatchers.Main) {
                        if (controller.currentMediaItemIndex == currentIndex) {
                            val resumePos = controller.currentPosition.coerceAtLeast(0L)
                            controller.replaceMediaItem(currentIndex, newItem)
                            if (resumePos > 500L) controller.seekTo(currentIndex, resumePos)
                            controller.prepare()
                            controller.play()
                            _uiState.update { it.copy(hasError = false, errorType = null) }
                            Timber.tag("DEBUG_PLAYER").d("✅ [RESOLVE RETRY] Berhasil. Resume di ${resumePos}ms")
                        }
                    }
                } else {
                    Timber.tag("DEBUG_PLAYER").e("❌ [RESOLVE RETRY] URL kosong. Skip lagu.")
                    withContext(Dispatchers.Main) {
                        if (controller.hasNextMediaItem()) controller.seekToNextMediaItem()
                        else controller.pause()
                    }
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Timber.tag("DEBUG_PLAYER").e("❌ [RESOLVE RETRY] Error: ${e.message}")
            }
        }
    }


    fun PlayMusicViewModel.togglePlayPause() {
        // Mencegah aksi jika fade sedang berjalan
        if (isFading) return

        // Simpan status saat ini sebelum diubah
        val wasPlaying = _uiState.value.isPlaying

        // 2. LANGSUNG UPDATE UI STATE (INI KUNCINYA)
        // Ikon akan berubah seketika karena ini.
        _uiState.update { it.copy(isPlaying = !wasPlaying) }

        if (wasPlaying) {
            fadeOutAndPause()
        } else {
            fadeInAndPlay()
        }
    }


    fun PlayMusicViewModel.stopPlayback() {
        mediaController?.stop()
        _uiState.update { it.copy(isPlaying = false) }
    }


    // --- FUNGSI BARU UNTUK FADE OUT ---
    fun PlayMusicViewModel.fadeOutAndPause() {
        val controller = mediaController ?: return
        volumeFadeJob?.cancel() // Batalkan fade sebelumnya jika ada
        isFading = true

        volumeFadeJob =
                viewModelScope.launch {
                    val startVolume = controller.volume
                    val steps = (PlayMusicViewModel.FADE_DURATION_MS / PlayMusicViewModel.FADE_INTERVAL_MS).toInt()
                    val volumeStep = startVolume / steps

                    for (i in 1..steps) {
                        val newVolume = (startVolume - (volumeStep * i)).coerceIn(0.0f, 1.0f)
                        controller.volume = newVolume
                        delay(PlayMusicViewModel.FADE_INTERVAL_MS)
                    }

                    // Setelah fade selesai, pause dan kembalikan volume ke target volume
                    // agar saat play lagi, fade-in bisa dimulai dari target volume.
                    controller.pause()
                    controller.volume = getTargetVolume()
                    isFading = false
                }
    }


    // --- FUNGSI BARU UNTUK FADE IN ---
    fun PlayMusicViewModel.fadeInAndPlay() {
        val controller = mediaController ?: return
        volumeFadeJob?.cancel()
        isFading = true

        volumeFadeJob =
                viewModelScope.launch {
                    // Set volume ke 0, lalu mulai mainkan (tidak akan terdengar)
                    controller.volume = 0.0f

                    // FIX: Jika Play di-tap saat lagu error/idle (misal internet putus lalu nyambung), re-prepare
                    if (controller.playbackState == Player.STATE_IDLE || controller.playbackState == Player.STATE_ENDED) {
                        controller.prepare()
                    }

                    controller.play()
                    _uiState.update { it.copy(isPlaying = true) } // Update UI secara manual

                    val targetVolume = getTargetVolume()
                    val steps = (PlayMusicViewModel.FADE_DURATION_MS / PlayMusicViewModel.FADE_INTERVAL_MS).toInt()
                    val volumeStep = targetVolume / steps

                    for (i in 1..steps) {
                        val newVolume = (volumeStep * i).coerceIn(0.0f, targetVolume)
                        controller.volume = newVolume
                        delay(PlayMusicViewModel.FADE_INTERVAL_MS)
                    }

                    // Pastikan volume kembali ke target volume
                    controller.volume = targetVolume
                    isFading = false
                    // Panggil listener secara manual jika perlu
                    if (controller.isPlaying != _uiState.value.isPlaying) {
                        _uiState.update { it.copy(isPlaying = controller.isPlaying) }
                    }
                }
    }


    fun PlayMusicViewModel.toggleShuffleMode() {
        // FIX: Jika dari Search, Shuffle dilarang (Smart Queue sudah terurut)
        if (_uiState.value.playlistSubtitle == "Memainkan Dari Pencarian") {
           Timber.tag("DEBUG_PLAYER").d( "Shuffle disabled in Search Context (Smart Queue)")
            return
        }

        val currentShuffleState = _uiState.value.isShuffleModeEnabled
       Timber.tag("DEBUG_PLAYER").d(
                
                "COMMAND: Toggling shuffle. Current state is $currentShuffleState. Setting to ${!currentShuffleState}"
        )
        mediaController?.shuffleModeEnabled = !currentShuffleState
    }


    /**
     * Set shuffle mode secara LANGSUNG tanpa toggle. Digunakan oleh AutoPlaylistHeader agar tidak
     * ada race condition dengan state Compose yang masih stale.
     */
    fun PlayMusicViewModel.forceSetShuffle(enabled: Boolean) {
       Timber.tag("DEBUG_PLAYER").d( "🔀 [FORCE SHUFFLE] Set shuffle to: $enabled")
        mediaController?.shuffleModeEnabled = enabled
    }


    // Fungsi ini akan berputar: OFF -> ONE -> ALL -> OFF
    // FIX: Jika dari Search, hanya OFF -> ONE -> OFF (Skip ALL)
    fun PlayMusicViewModel.cycleRepeatMode() {
        val isSearchContext = _uiState.value.playlistSubtitle == "Memainkan Dari Pencarian"

        val nextRepeatMode =
                when (_uiState.value.repeatMode) {
                    Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ONE
                    Player.REPEAT_MODE_ONE -> Player.REPEAT_MODE_ALL
                    else -> Player.REPEAT_MODE_OFF
                }

        mediaController?.repeatMode = nextRepeatMode
    }


    fun PlayMusicViewModel.seekTo(position: Long) {
        mediaController?.seekTo(position)
    }


    fun PlayMusicViewModel.nextSong() {
        val controller = mediaController ?: return
        val nextIndex = controller.nextMediaItemIndex
        if (nextIndex != C.INDEX_UNSET) {
            _uiState.update { it.copy(animationDirection = AnimationDirection.FORWARD) }
            playSongAt(nextIndex)
        }
    }


    fun PlayMusicViewModel.previousSong() {
        val controller = mediaController ?: return

        // Fitur: Jika lagu sudah main lebih dari 3 detik, restart lagu ini (bukan mundur ke lagu
        // sebelumnya)
        if (controller.currentPosition > 3000) {
            controller.seekTo(0)
            return
        }

        val prevIndex = controller.previousMediaItemIndex
        if (prevIndex != C.INDEX_UNSET) {
            _uiState.update { it.copy(animationDirection = AnimationDirection.BACKWARD) }
            playSongAt(prevIndex)
        }
    }


    fun PlayMusicViewModel.playSongAt(index: Int) {
        val controller = mediaController

        if (controller == null) {
           Timber.tag("DEBUG_PLAYER").e( "❌ ERROR: MediaController is NULL")
            return
        }

        val playlist = _uiState.value.playlist

        // Validasi index
        if (index !in playlist.indices) {
           Timber.tag("DEBUG_PLAYER").e( "❌ ERROR: Index $index di luar jangkauan")
            return
        }

        val targetSong = playlist[index]
       Timber.tag("DEBUG_PLAYER").d( "==================================================")
       Timber.tag("DEBUG_PLAYER").d(
                
                "👇 CLICK MANUAL (FIXED): User ingin memutar index $index: '${targetSong.song.title}'"
        )

        // 1. BATALKAN JOB SEBELUMNYA (Supaya kalau user spam klik, yang terakhir yang menang)
        jumpJob?.cancel()
        setPlaylistJob?.cancel()

        // 🛑 PAUSE SEGERA! (Agar lagu lama berhenti saat buffering lagu baru)
        controller.pause()

        // 2. OPTIMISTIC UI UPDATE (Update Tampilan Dulu Biar Sat-Set!)
        _uiState.update {
            it.copy(
                    currentSong = targetSong,
                    currentSongIndex = index,
                    // Reset loading state sementara
                    isLoadingLyrics = false,
                    isLoadingData = true, // Start loading data (Checking/Resolving)
                    isLiked = false, // Reset like status for new song
                    uploader = null, // Reset uploader for new song
                    // 🔄 RESET SLIDER KE 0 (Biar kelihatan mulai dari awal)
                    currentPosition = 0L
            )
        }

        // Reset Smart Retry Flag
        hasRetriedCurrentSong = false

        jumpJob =
                viewModelScope.launch(Dispatchers.IO) {
                    try {
                        // 3. CEK APAKAH URL SUDAH VALID?
                        val currentUrl = targetSong.song.audioUrl
                        val isUrlValid =
                                !currentUrl.isNullOrBlank() &&
                                        (currentUrl.startsWith("http") ||
                                                currentUrl.startsWith("content"))

                        if (isUrlValid) {
                            // JIKA URL VALID: Play Langsung (Optimistic)
                           Timber.tag("DEBUG_PLAYER").d( "🚀 URL VALID: Play langsung tanpa menunggu.")
                            _uiState.update {
                                it.copy(debugStatus = "Playing from Cache", isLoadingData = false)
                            } // Data ready
                            withContext(Dispatchers.Main) {
                                if (index < controller.mediaItemCount) {
                                    if (controller.currentMediaItemIndex != index) {
                                        controller.seekTo(index, C.TIME_UNSET)
                                        controller.play()
                                    }
                                }
                            }
                        } else {
                            // JIKA URL KOSONG: Tampilkan Loading & Resolve Dulu
                           Timber.tag("DEBUG_PLAYER").d(
                                    
                                    "⏳ URL KOSONG: Resolving URL dulu... (Player dipause sementara)"
                            )
                            withContext(Dispatchers.Main) {
                                _uiState.update {
                                    it.copy(isLoadingLyrics = true, isLoadingData = true)
                                } // Still loading
                            }
                        }

                        // 2. RESOLVE URL (Pastikan dapat yang fresh)
                       Timber.tag("DEBUG_PLAYER").d( "🌐 FETCH: Resolving URL...")
                        // 2. RESOLVE URL (Pastikan dapat yang fresh)
                       Timber.tag("DEBUG_PLAYER").d( "🌐 FETCH: Resolving URL...")
                        val result = resolveSongUrl(targetSong)
                        val newUrl = result.url

                        // 3. UPDATE PLAYER (Hanya update item yang sedang aktif)
                        withContext(Dispatchers.Main) {

                            // VALIDASI URL: Cek Error Network atau URL kosong
                            if (result.errorType == MusicRepository.ErrorType.NETWORK) {
                               Timber.tag("DEBUG_PLAYER").e(
                                        
                                        "❌ NETWORK ERROR during song update. Pausing."
                                )
                                _uiState.update {
                                    it.copy(
                                            hasError = true,
                                            errorType = "Gagal memuat: Masalah Koneksi Internet",
                                            isPlaying = false,
                                            isLoadingData = false // Stop spinner
                                    )
                                }
                                controller.pause()
                                return@withContext
                            }

                            if (newUrl.isBlank()) {
                               Timber.tag("DEBUG_PLAYER").e(
                                        
                                        "❌ ERROR: URL kosong/gagal resolve. Skip playback."
                                )
                                // Kembalikan UI ke state aman atau tampilkan error
                                _uiState.update { it.copy(isLoadingLyrics = false, isLoadingData = false) }
                                // Opsional: Toast error
                                return@withContext
                            }

                            if (index < controller.mediaItemCount) {
                                // Buat MediaItem baru dengan URL valid
                                val newItem = createMediaItem(targetSong, newUrl)

                                // KUNCI: Gunakan setMediaItem di index spesifik untuk update source
                                controller.replaceMediaItem(index, newItem)

                                // Jika tadi kita menunda play (karena URL kosong), sekarang play!
                                if (!isUrlValid ||
                                                controller.playbackState == Player.STATE_IDLE ||
                                                controller.playbackState == Player.STATE_ENDED
                                ) {
                                    // Cek lagi apakah index masih sama (user gak ganti lagu lagi
                                    // pas loading)
                                    // Tapi karena kita replaceMediaItem di index spesifik, aman
                                    // untuk seek ke situ.
                                    if (controller.currentMediaItemIndex != index) {
                                        controller.seekTo(index, C.TIME_UNSET)
                                    }
                                    controller.play()
                                }
                            }
                            _uiState.update {
                                it.copy(isLoadingLyrics = false, isLoadingData = false)
                            }
                        }

                        // 4. UPDATE LIRIK & WARNA (Sekali saja!)
                        // Ambil data detail
                        fetchFullSongDetails(targetSong.song.effectiveId, forceUpdate = true)

                        launch {
                            val colors =
                                    extractGradientColorsFromImageUrl(
                                            getApplication(),
                                            targetSong.song.coverUrl ?: ""
                                    )

                            _uiState.update { it.copy(dominantColors = colors) }
                            // Save to preferences for HomeScreen
                            userPreferencesRepository.saveLastSongColor(
                                    colors.getOrElse(0) { Color(0xFF755D8D) }
                            )
                        }

                        // 5. CEK STATUS LIKE
                        checkIfLiked(targetSong.song.id)
                    } catch (e: Exception) {
                        if (e is kotlinx.coroutines.CancellationException)
                                throw e // Biarkan cancel lewat

                        // TANGKAP ERROR BIAR GAK CRASH
                       Timber.tag("DEBUG_PLAYER").e( "❌ CRASH HANDLED di playSongAt: ${e.message}")
                        e.printStackTrace()

                        withContext(Dispatchers.Main) {
                            _uiState.update {
                                it.copy(isLoadingLyrics = false, isLoadingData = false)
                            }
                        }
                    }
                }
    }


    suspend fun PlayMusicViewModel.generateSmartQueue(seedSong: SongWithArtist) {
        if (isGeneratingSmartQueue) return
        isGeneratingSmartQueue = true
        try {
            if (seedSong.song.id.startsWith("offline_")) {
               Timber.tag("DEBUG_PLAYER").d( "Skip Smart Queue untuk offline song")
                return
            }

            // Route: YouTube songs → YouTube recommendations automix
            val isYouTubeId = seedSong.song.id.matches(Regex("^[a-zA-Z0-9_-]{11}$"))
            if (seedSong.song.source == "youtube" || isYouTubeId) {
               Timber.tag("DEBUG_PLAYER").d( "🎵 [YT] generateSmartQueue → fetchYouTubeRecommendations: ${seedSong.song.title}")
                fetchYouTubeRecommendations(seedSong)
                return
            }

        smartQueueMutex.withLock {
            val fullSongDetails = withContext(Dispatchers.IO) {
                repository.getFullSongDetails(seedSong.song.effectiveId)
            }
            val seedSongWithMeta =
                    fullSongDetails?.let {
                        seedSong.song.copy(
                                lyrics = if (it.lyrics.isNullOrBlank()) seedSong.song.lyrics else it.lyrics,
                                language = it.language ?: seedSong.song.language,
                                moods = if (it.moods.isNullOrEmpty()) seedSong.song.moods else it.moods,
                                artistId = it.artistId ?: seedSong.song.artistId
                        )
                    } ?: seedSong.song

            val currentPlaylistIds = _uiState.value.playlist.map { it.song.id }
            val smartQueue = repository.fetchSmartQueue(
                currentSong = seedSongWithMeta,
                playedIds = currentPlaylistIds
            )
           Timber.tag("DEBUG_PLAYER").d( "📦 Smart Queue Result Size: ${smartQueue.size}")

            // 4. Update Playlist dengan hasil Smart Queue
            if (smartQueue.isNotEmpty()) {
                // Fetch Artist info logic (Existing)
                val artistIds = smartQueue.mapNotNull { it.artistId }.distinct()
                val artistsMap =
                        try {
                            SupabaseManager.client
                                    .from("artists")
                                    .select { filter { isIn("id", artistIds) } }
                                    .decodeList<Artist>()
                                    .associateBy { it.id }
                        } catch (_: Exception) {
                            emptyMap()
                        }

                val queueWithArtists =
                        smartQueue.map { song ->
                            SongWithArtist(song, song.artistId?.let { artistsMap[it] })
                        }

                // --- LOGGING UNTUK ANALISIS USER ---
               Timber.tag("DEBUG_PLAYER").d(
                        
                        "=== 📋 SMART QUEUE RESULT (${queueWithArtists.size} Songs) ==="
                )
                queueWithArtists.forEachIndexed { index, item ->
                    // Log format: [No] Judul - Artist | Lang: .. | Moods: ..
                    val moodsStr = (item.song.moods ?: emptyList()).joinToString(", ")
                   Timber.tag("DEBUG_PLAYER").d(
                            
                            "Queue [#${index + 1}]: ${item.song.title} - ${item.artist?.name ?: "Unknown"} | Lang: ${item.song.language} | Moods: [$moodsStr]"
                    )
                }
               Timber.tag("DEBUG_PLAYER").d(
                        
                        "==========================================================="
                )


                // Update ViewModel State
                // HATI-HATI: Playlist mungkin sudah berubah jika user ganti lagu cepat-cepat.
                // Kita append ke current playlist jika seed song masih ada di sana.
                val currentPlaylist = _uiState.value.playlist.toMutableList()
                val isSeedStillPresent = currentPlaylist.any { it.song.id == seedSong.song.id }

                if (isSeedStillPresent) {
                    // Cek duplikasi (jangan add kalau sudah ada)
                    val existingIds = currentPlaylist.map { it.song.id }.toSet()
                    val uniqueNewSongs =
                            queueWithArtists.filter { !existingIds.contains(it.song.id) }

                    if (uniqueNewSongs.isNotEmpty()) {
                        currentPlaylist.addAll(uniqueNewSongs)

                        _uiState.update { it.copy(playlist = currentPlaylist) }

                        // FIX: Save newly generated smart queue to the database
                        repository.queue.savePlaybackQueue(currentPlaylist, _uiState.value.playingMusicFromPlaylist)

                       Timber.tag("DEBUG_PLAYER").d(
                                
                                "✅ Smart Queue loaded: ${uniqueNewSongs.size} new songs added to UI State."
                        )

                        // 5. Update Player (APPEND NEW SONGS)
                        withContext(Dispatchers.Main) {
                            val controller = mediaController
                            if (controller != null) {
                                // Create MediaItems for the queue (using raw audioUrl, will be
                                // resolved later)
                                val newMediaItems =
                                        uniqueNewSongs.map {
                                            createMediaItem(it, it.song.audioUrl ?: "")
                                        }
                                controller.addMediaItems(newMediaItems)
                               Timber.tag("DEBUG_PLAYER").d(
                                        
                                        "✅ Added ${newMediaItems.size} songs from Smart Queue to Player."
                                )
                            }
                        }
                    } else {
                       Timber.tag("DEBUG_PLAYER").d(
                                
                                "⚠️ Smart Queue fetched but all songs already exist in playlist."
                        )
                    }
                } else {
                    Timber.tag("DEBUG_PLAYER").w(
                            
                            "⚠️ Seed song no longer in playlist. Aborting Smart Queue update."
                    )
                }
            }
        }
        } finally {
            isGeneratingSmartQueue = false
        }
    }


    suspend fun PlayMusicViewModel.resolveSongUrl(song: SongWithArtist): MusicRepository.ResolvedSongUrl {
        // Fitur Offline: Bypass request resolusi url jika lagu berasal dari local storage
        if (song.song.id.startsWith("offline_") || song.song.audioUrl?.startsWith("content://") == true) {
           Timber.tag("DEBUG_PLAYER").d( "✅ RESOLVED OFFLINE URL: ${song.song.audioUrl}")
            return MusicRepository.ResolvedSongUrl(song.song.audioUrl ?: "", "OFFLINE_STORAGE")
        }

        // Logika disederhanakan: Serahkan ke Repository
        // Repository akan otomatis cek SQLite -> Cek Expired -> Request API jika perlu
        val result =
                repository.getPlayableUrl(
                        songId = song.song.effectiveId,
                        title = song.song.title,
                        telegramFileId = song.song.telegramFileId,
                        fallbackUrl = song.song.audioUrl,
                        artistId = song.artist?.id,
                        source = song.song.source
                )

       Timber.tag("DEBUG_PLAYER").d( "✅ RESOLVED URL [${result.source}]: ${result.url}")
        return result
    }


    // --- [TAMBAHKAN 3 FUNGSI INI DI PALING BAWAH] ---

    // 1. Helper buat bikin MediaItem biar gak duplikat kode
    @OptIn(UnstableApi::class)
    fun PlayMusicViewModel.createMediaItem(data: SongWithArtist, streamUrl: String): MediaItem {
        val bundle = android.os.Bundle().apply {
            putString("effectiveId", data.song.effectiveId)
            putString("source", data.song.source)
        }
        val meta =
                MediaMetadata.Builder()
                        .setTitle(data.song.title)
                        .setArtist(data.artist?.name ?: "Unknown")
                        .setArtworkUri(data.song.coverUrl?.takeIf { it.isNotBlank() }?.toUri())
                        .setExtras(bundle)
                        .build()

        return MediaItem.Builder()
                .setMediaId(data.song.id)
                .setUri(streamUrl) // URL bisa dari Telegram atau Supabase
                .setCustomCacheKey(data.song.id)
                .setMediaMetadata(meta)
                .build()
    }


    fun PlayMusicViewModel.createMediaItem(song: Song, url: String): MediaItem {
        val bundle = android.os.Bundle().apply {
            putString("effectiveId", song.effectiveId)
            putString("source", song.source)
        }
        val meta =
                MediaMetadata.Builder()
                        .setTitle(song.title)
                        .setArtworkUri(song.coverUrl?.takeIf { it.isNotBlank() }?.toUri())
                        .setExtras(bundle)
                        .build()

        return MediaItem.Builder().setMediaId(song.id).setUri(url).setMediaMetadata(meta).build()
    }


    // 2. Logic Pre-fetching Background
    fun PlayMusicViewModel.prefetchSongAtIndex(index: Int) {
        val playlist = _uiState.value.playlist

        // Cek validitas index (Harus dalam range 0 s/d size-1)
        if (index < 0 || index >= playlist.size) return

        val targetSong = playlist[index]

        viewModelScope.launch(Dispatchers.IO) {
            // ⏳ DELAY PENTING: Tunggu 2 detik!
            // Biarkan lagu utama loading dulu sampai selesai, baru kita urus background.
            delay(2000)

            // Cek lagi (siapa tau user udah ganti lagu dalam 2 detik tadi)
            if (index == _uiState.value.currentSongIndex) return@launch

            // Cek apakah lagu ini punya Telegram ID dan BELUM ada di Cache?
            if (!targetSong.song.telegramFileId.isNullOrBlank()) {
               Timber.tag("DEBUG_PLAYER").d(
                        
                        "🚀 PRE-FETCH START: Menyiapkan lagu tetangga (Index $index): ${targetSong.song.title}"
                )

                val result = resolveSongUrl(targetSong)
                val newUrl = result.url

                if (newUrl != targetSong.song.audioUrl) {
                    updateMediaItemUrlInPlayer(index, targetSong, newUrl)
                }
            }
        }
    }


    // 3. Update Playlist ExoPlayer Diam-diam
    // 🔥 FIX SHUFFLE CASCADE:
    // DILARANG memanggil replaceMediaItem() untuk lagu yang TIDAK sedang dimainkan!
    // Di mode shuffle, replaceMediaItem() menyebabkan ExoPlayer MERESET internal shuffle order,
    // sehingga player melompat ke index acak lain secara cascade (19→8→4→0→7→...).
    // Strategi yang benar: Pre-fetch hanya MENGHANGATKAN CACHE (simpan URL ke DB),
    // biarkan onMediaItemTransition yang menangani update ExoPlayer saat lagu itu benar-benar
    // diputar.
    suspend fun PlayMusicViewModel.updateMediaItemUrlInPlayer(
            index: Int,
            song: SongWithArtist,
            newUrl: String
    ) {
        // URL sudah disimpan ke DB oleh resolveSongUrl() di atas.
        // Tidak perlu update ExoPlayer MediaItem sekarang!
        // onMediaItemTransition akan re-resolve dari DB cache ketika lagu ini diputar.
       Timber.tag("DEBUG_PLAYER").d(
                
                "✅ PRE-FETCH SELESAI: URL untuk index $index ('${song.song.title}') sudah di-cache. ExoPlayer TIDAK disentuh."
        )
    }


    // --- QUEUE MANAGEMENT ---

    fun PlayMusicViewModel.addToQueue(song: SongWithArtist): String {
        val currentPlaylist = _uiState.value.playlist

        // CASE 1: Queue is empty or nothing playing -> Treat as "Start New Queue"
        if (currentPlaylist.isEmpty() || _uiState.value.currentSong == null) {
           Timber.tag("DEBUG_PLAYER").d(
                    
                    "Queue empty, starting single song queue: ${song.song.title}"
            )

            // 1. Set Playlist with selected song (Just one)
            setPlaylist(listOf(song), 0)

            // 2. Set Playlist Name & Subtitle & Trigger Snackbar
            _uiState.update {
                it.copy(
                        playingMusicFromPlaylist = "Tambahkan Lagu ke Antrean",
                        playlistSubtitle = "Memainkan dari Playlist",
                        showQueueInfoSnackbar = true // Trigger Snackbar!
                )
            }
            return "Memulai antrean dengan ${song.song.title}"
        }

        // CASE 2: Normal Add to Queue (Existing logic)
        val existingIndex = currentPlaylist.indexOfFirst { it.song.id == song.song.id }
        if (existingIndex != -1) {
            return "Lagu ini sudah ada di dalam queue urutan ${existingIndex + 1}"
        }

        val mutableList = currentPlaylist.toMutableList()
        mutableList.add(song)
        _uiState.update { it.copy(playlist = mutableList) }

        // Update Player (Silent)
        mediaController?.addMediaItem(createMediaItem(song.song, song.song.audioUrl ?: ""))
       Timber.tag("DEBUG_PLAYER").d( "Added to queue: ${song.song.title}")

        // Save new queue to DB (Filter offline songs)
        viewModelScope.launch(Dispatchers.IO) {
            repository.queue.savePlaybackQueue(mutableList, _uiState.value.playingMusicFromPlaylist)
        }

        return "${song.song.title} ditambahkan ke antrean"
    }


    fun PlayMusicViewModel.playNext(song: SongWithArtist) {
        val controller = mediaController ?: return
        val currentPlaylist = _uiState.value.playlist.toMutableList()

        // Cek apakah lagu sudah ada di playlist
        val existingIndex = currentPlaylist.indexOfFirst { it.song.id == song.song.id }
        val currentIndex = controller.currentMediaItemIndex

        if (existingIndex != -1) {
            // 🛑 Jika Index sama dengan yang sedang diputar, jangan lakukan apa-apa (handled di UI
            // "Lagu sedang diputar")
            if (existingIndex == currentIndex) return

            // 1. Hapus dari posisi lama
            currentPlaylist.removeAt(existingIndex)
            controller.removeMediaItem(existingIndex)

            // 2. Hitung Index Baru
            // Jika kita menghapus item SEBELUM current index, maka current index akan bergeser
            // mundur -1.
            var actualCurrentIndex = currentIndex
            if (existingIndex < currentIndex) {
                actualCurrentIndex--
            }

            // 3. Masukkan tepat setelah lagu yang sedang diputar
            val targetIndex = actualCurrentIndex + 1

            // Insert di ViewModel list
            currentPlaylist.add(targetIndex, song)
            _uiState.update { it.copy(playlist = currentPlaylist) }

            // Insert di Player
            controller.addMediaItem(
                    targetIndex,
                    createMediaItem(song.song, song.song.audioUrl ?: "")
            )
           Timber.tag("DEBUG_PLAYER").d( "Moved to next: ${song.song.title} to index $targetIndex")

            // Prefetch audio and save to DB
            prefetchSongAtIndex(targetIndex)
            viewModelScope.launch(Dispatchers.IO) {
                repository.queue.savePlaybackQueue(currentPlaylist, _uiState.value.playingMusicFromPlaylist)
            }
        } else {
            // Logic Lama: Insert Baru
            val nextIndex = currentIndex + 1
            currentPlaylist.add(nextIndex, song)
            _uiState.update { it.copy(playlist = currentPlaylist) }

            // Update Player
            controller.addMediaItem(nextIndex, createMediaItem(song.song, song.song.audioUrl ?: ""))
           Timber.tag("DEBUG_PLAYER").d( "Inserted next: ${song.song.title} at index $nextIndex")

            // Prefetch audio and save to DB
            prefetchSongAtIndex(nextIndex)
            viewModelScope.launch(Dispatchers.IO) {
                repository.queue.savePlaybackQueue(currentPlaylist, _uiState.value.playingMusicFromPlaylist)
            }
        }
    }


    fun PlayMusicViewModel.removeFromQueue(songToRemove: SongWithArtist) {
        val currentPlaylist = _uiState.value.playlist.toMutableList()
        val indexToRemove = currentPlaylist.indexOfFirst { it.song.id == songToRemove.song.id }

        if (indexToRemove != -1) {
            // Remove from state
            currentPlaylist.removeAt(indexToRemove)

            // Adjust current song index if needed
            var newCurrentIndex = _uiState.value.currentSongIndex
            if (indexToRemove < newCurrentIndex) {
                 newCurrentIndex -= 1
            } else if (indexToRemove == newCurrentIndex) {
                 // The currrently playing song was removed.
                 // We could potentially skip to the next track here,
                 // but ExoPlayer might handle the jump.
                 // For now, if it's the last song removed, let it reset.
            }

            // Remove from ExoPlayer
            mediaController?.removeMediaItem(indexToRemove)

             _uiState.update {
                 it.copy(
                     playlist = currentPlaylist,
                     currentSongIndex = newCurrentIndex
                 )
             }

             // Save updated queue to SQLite
             viewModelScope.launch(Dispatchers.IO) {
                 repository.queue.savePlaybackQueue(currentPlaylist, _uiState.value.playingMusicFromPlaylist)
             }

            Timber.tag("DEBUG_PLAYER").d( "✅ Removed '${songToRemove.song.title}' from queue at index $indexToRemove.")
        }
    }


    // --- Global Queue Options Helpers ---
    fun PlayMusicViewModel.showQueueOptions(song: SongWithArtist, playlistIdToRemoveFrom: String? = null) {
        _uiState.update { it.copy(
            selectedSongForQueueOptions = song,
            playlistIdToRemoveFromQueueOptions = playlistIdToRemoveFrom
        ) }
    }


    fun PlayMusicViewModel.dismissQueueOptions() {
        _uiState.update { it.copy(
            selectedSongForQueueOptions = null,
            playlistIdToRemoveFromQueueOptions = null
        ) }
    }


    fun PlayMusicViewModel.dismissQueueInfoSnackbar() {
        _uiState.update { it.copy(showQueueInfoSnackbar = false) }
    }


    fun PlayMusicViewModel.refreshCurrentSongData() {
        val currentSong = _uiState.value.currentSong

        // 1. Cek Data Lirik: Kalau lagu ada tapi lirik kosong, paksa ambil dari database
        if (currentSong != null && currentSong.song.lyrics.isNullOrBlank()) {
           Timber.tag("DEBUG_PLAYER").d("🔄 REFRESH: UI baru bangun, lirik kosong. Mengambil ulang detail lagu...")
            fetchFullSongDetails(currentSong.song.effectiveId)
        }

        // 2. Cek Posisi: Pastikan seekbar jalan lagi
        mediaController?.let { if (it.isPlaying) startUpdatingPosition() }

        // 3. Cek Ulang Status Like (Penting kalau user like dari device lain/web)
        if (currentSong != null) {
            checkIfLiked(currentSong.song.id)
        }
    }


    // --- PLAYBACK PERSISTENCE LOGIC ---
    // Dipanggil saat inisialisasi ViewModel
    suspend fun PlayMusicViewModel.restorePlaybackState() {
       Timber.tag("DEBUG_PLAYER").d( "♻️ [RESTORE] Memulai restorasi state...")

        // 1. Ambil Data Terakhir dari Prefs
        val lastSongId = userPreferencesRepository.lastSongIdFlow.first()
        val lastPosition = userPreferencesRepository.lastPositionFlow.first()

        // 2. Ambil Queue dari Room
        val (playlist, playlistName) =
                withContext(Dispatchers.IO) { repository.queue.restorePlaybackQueue() }

        val lastPlaylistName = userPreferencesRepository.lastPlaylistNameFlow.first()
        val finalPlaylistName =
                if (playlistName == "Unknown Playlist") lastPlaylistName else playlistName

        if (playlist.isNotEmpty()) {
           Timber.tag("DEBUG_PLAYER").d(
                    
                    "♻️ [RESTORE] Queue (${playlist.size} lagu) ditemukan. Mengembalikan player..."
            )

            // Cari index lagu terakhir
            val startIndex =
                    if (lastSongId != null) {
                        playlist.indexOfFirst { it.song.id == lastSongId }.coerceAtLeast(0)
                    } else 0

            // Setup Player (TAPI JANGAN AUTO-PLAY)
            _uiState.update {
                it.copy(
                        playlist = playlist,
                        currentSong = playlist[startIndex],
                        currentSongIndex = startIndex,
                        playingMusicFromPlaylist = finalPlaylistName
                )
            }

            val controller = mediaController ?: return

            // 🔥 CRITICAL FIX: JANGAN RESTORE JIKA PLAYER SUDAH JALAN! (Misal: klik notifikasi saat app sudah mati tapi service idup)
            if (controller.mediaItemCount > 0) {
               Timber.tag("DEBUG_PLAYER").d( "♻️ [RESTORE SKIP] ExoPlayer sudah punya ${controller.mediaItemCount} item. Service masih hidup. Sync UI saja.")
                val currentMediaId = controller.currentMediaItem?.mediaId
                val currentIndex = playlist.indexOfFirst { it.song.id == currentMediaId }.coerceAtLeast(0)

                // ⚠️ ANTI-AUTOPLAY: Pastikan player TIDAK auto-play saat user baru buka app.
                // User harus menekan tombol play sendiri secara sadar.
                if (!controller.isPlaying) {
                    controller.pause()
                }

                _uiState.update {
                    it.copy(
                            playlist = playlist,
                            currentSong = if (playlist.isNotEmpty()) playlist[currentIndex] else null,
                            currentSongIndex = currentIndex,
                            playingMusicFromPlaylist = finalPlaylistName,
                            currentPosition = controller.currentPosition,
                            totalDuration = controller.duration.coerceAtLeast(0L),
                            isPlaying = controller.isPlaying
                    )
                }

                if (playlist.isNotEmpty()) {
                    val coverUrl = playlist[currentIndex].song.coverUrl
                    if (coverUrl != null) {
                        val colors = extractGradientColorsFromImageUrl(getApplication(), coverUrl)
                        _uiState.update { it.copy(dominantColors = colors) }
                    }

                    // 🔥 FETCH FULL DETAILS UNTUK MELENGKAPI ARTIST & UPLOADER SELEPAS RESUME!
                    // Ketika resuming dari notifikasi (service hidup), onMediaItemTransition tidak terpanggil,
                    // maka kita harus fetch manual supaya layar tidak menampilkan 'Unknown Artist/Uploader'.
                    if (playlist.indices.contains(currentIndex)) {
                        fetchFullSongDetails(playlist[currentIndex].song.effectiveId)
                    }
                }
                return
            }

            // Setup Media Items
            val mediaItems =
                    playlist.map { s ->
                        val meta =
                                MediaMetadata.Builder()
                                        .setTitle(s.song.title)
                                        .setArtist(s.artist?.name ?: "Unknown")
                                        .setArtworkUri(s.song.coverUrl?.toUri())
                                        .build()

                        MediaItem.Builder()
                                .setMediaId(s.song.id)
                                .setUri(s.song.audioUrl ?: s.song.telegramDirectUrl ?: "")
                                .setMediaMetadata(meta)
                                .build()
                    }

            controller.setMediaItems(mediaItems, startIndex, lastPosition)
            controller.prepare()
            controller.pause() // Pastikan PAUSE saat start

            // Update UI agar bottom player muncul dengan posisi yang benar
            _uiState.update {
                it.copy(
                        currentPosition = lastPosition,
                        totalDuration = controller.duration.coerceAtLeast(0L),
                        isBuffering = false
                )
            }

            // Fetch details for UI (Async) - Biar cover art & lirik muncul
            fetchFullSongDetails(playlist[startIndex].song.effectiveId)

            // Ambil Warna Dominan
            val coverUrl = playlist[startIndex].song.coverUrl
            if (coverUrl != null) {
                val colors = extractGradientColorsFromImageUrl(getApplication(), coverUrl)
                _uiState.update { it.copy(dominantColors = colors) }
            }

           Timber.tag("DEBUG_PLAYER").d( "✅ [RESTORE] Berhasil! Siap di posisi: ${lastPosition}ms")
        } else {
           Timber.tag("DEBUG_PLAYER").d( "⚠️ [RESTORE] Tidak ada history queue.")
        }
    }


    // Dipanggil saat pause, stop, atau ganti lagu
    fun PlayMusicViewModel.savePlaybackState() {
        val currentSong = _uiState.value.currentSong?.song
        val currentPosition = _uiState.value.currentPosition

        if (currentSong != null) {
            viewModelScope.launch(Dispatchers.IO) {
                userPreferencesRepository.saveLastSongId(currentSong.id)
                userPreferencesRepository.saveLastPosition(currentPosition)
            }
        }
    }

