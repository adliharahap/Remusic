package com.example.remusic.viewmodel.playmusic

import android.app.Application
import android.content.ComponentName
import android.util.Log
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
import com.example.remusic.data.model.AudioOutputDevice
import com.example.remusic.data.model.Song
import com.example.remusic.data.model.SongWithArtist
import com.example.remusic.data.network.RetrofitInstance
import com.example.remusic.data.preferences.UserPreferencesRepository
import com.example.remusic.services.MusicService
import com.example.remusic.utils.extractGradientColorsFromImageUrl
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable.isActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class AnimationDirection {
    FORWARD, // Maju
    BACKWARD, // Mundur
    NONE // Tidak ada animasi (misal saat pertama kali memuat)
}
// PlayerUiState juga didefinisikan di sini atau diimpor
data class PlayerUiState(
    val isPlaying: Boolean = false,
    val currentSong: SongWithArtist? = null,
    val currentPosition: Long = 0,
    val totalDuration: Long = 0,
    val isShuffleModeEnabled: Boolean = false,
    val playingMusicFromPlaylist: String = "Unknown Playlist",
    val repeatMode: Int = Player.REPEAT_MODE_OFF, // REPEAT_MODE_OFF, REPEAT_MODE_ONE, REPEAT_MODE_ALL
    val playlist: List<SongWithArtist> = emptyList(),
    val dominantColors: List<Color> = listOf(Color.DarkGray, Color.Black),
    val currentAudioDevice: AudioOutputDevice? = null,
    val currentSongIndex: Int = 0,
    val animationDirection: AnimationDirection = AnimationDirection.NONE
)


class PlayMusicViewModel(application: Application) : AndroidViewModel(application) {

    //untuk fitur fade ketika play, pause di klik
    companion object {
        private const val FADE_DURATION_MS = 500L // Durasi fade dalam milidetik
        private const val FADE_INTERVAL_MS = 20L  // Seberapa sering volume diupdate
    }

    // variabel untuk playsongat
    private var jumpJob: Job? = null
    private var setPlaylistJob: Job? = null

    // --- VARIABEL UNTUK MENGELOLA FADE ---
    private var volumeFadeJob: Job? = null
    private var isFading = false

    private val _uiState = MutableStateFlow(PlayerUiState())

    // Cache memory: Key = TelegramFileID, Value = Link Streaming Direct
    private val urlCache = mutableMapOf<String, String>()

    val uiState = _uiState.asStateFlow()

    private var mediaController: MediaController? = null

    private val userPreferencesRepository = UserPreferencesRepository(getApplication())

    init {
        connectToService()
    }

    @OptIn(UnstableApi::class)
    private fun connectToService() {
        val sessionToken = SessionToken(getApplication(), ComponentName(getApplication(), MusicService::class.java))
        val controllerFuture = MediaController.Builder(getApplication(), sessionToken).buildAsync()
        controllerFuture.addListener(
            {
                mediaController = controllerFuture.get()
                setupPlayerListener()
                updatePlayerState()

                viewModelScope.launch {
                    val savedShuffleMode = userPreferencesRepository.shuffleEnabledFlow.first()
                    mediaController?.shuffleModeEnabled = savedShuffleMode
                    // Update UI state juga agar konsisten saat awal
                    _uiState.update { it.copy(isShuffleModeEnabled = savedShuffleMode) }
                    Log.d("PlayMusicViewModel", "Applied saved shuffle mode: $savedShuffleMode")
                }
                viewModelScope.launch {
                    val savedRepeatMode = userPreferencesRepository.repeatModeFlow.first()
                    mediaController?.repeatMode = savedRepeatMode
                    _uiState.update { it.copy(repeatMode = savedRepeatMode) }
                    Log.d("PlayMusicViewModel", "Applied saved repeat mode: $savedRepeatMode")
                }

                val lastIndex = _uiState.value.playlist
                    .indexOfFirst { it.song.id == _uiState.value.currentSong?.song?.id }
                if (lastIndex >= 0) {
                    setPlaylist(_uiState.value.playlist, lastIndex)
                }
            },
            ContextCompat.getMainExecutor(getApplication())
        )
    }

    private fun setupPlayerListener() {
        mediaController?.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {

                if (!isFading) {
                    _uiState.update { it.copy(isPlaying = isPlaying) }
                }

                if (isPlaying) {
                    startUpdatingPosition()
                } else {
                    stopUpdatingPosition()
                }
            }

            override fun onEvents(player: Player, events: Player.Events) {
                super.onEvents(player, events)
                _uiState.update {
                    it.copy(
                        totalDuration = player.duration.coerceAtLeast(0L),
                        currentPosition = player.currentPosition.coerceAtLeast(0L)
                    )
                }
            }

            // Listener ini akan update UI jika lagu di service berubah (misal dari notifikasi)
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                super.onMediaItemTransition(mediaItem, reason)

                val reasonStr = when(reason) {
                    Player.MEDIA_ITEM_TRANSITION_REASON_AUTO -> "AUTO (Next Otomatis)"
                    Player.MEDIA_ITEM_TRANSITION_REASON_SEEK -> "SEEK (Manual Klik/Geser)"
                    Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED -> "PLAYLIST CHANGED"
                    else -> "UNKNOWN"
                }

                Log.d("DEBUG_PLAYER", "🔀 TRANSISI: Ganti lagu karena $reasonStr")

                val player = mediaController ?: return

                val newIndex = player.currentMediaItemIndex
                val oldIndex = _uiState.value.currentSongIndex

                Log.d("DEBUG_PLAYER", "   📍 Perubahan Index: $oldIndex -> $newIndex")

                // Tentukan arah berdasarkan perubahan indeks
                val direction = when {
                    newIndex > oldIndex -> AnimationDirection.FORWARD
                    newIndex < oldIndex -> AnimationDirection.BACKWARD
                    else -> _uiState.value.animationDirection // pertahankan arah jika sama
                }

                if (mediaItem != null) {
                    val playlist = _uiState.value.playlist
                    val matchedSong = playlist.find { it.song.id == mediaItem.mediaId }

                    // PRE-FETCH: Siapkan lagu BERIKUTNYA secara diam-diam
                    prefetchSongAtIndex(newIndex + 1)

                    // 2. Siapkan lagu SEBELUMNYA (Untuk tombol Prev)
                    prefetchSongAtIndex(newIndex - 1)

                    if (matchedSong != null) {
                        _uiState.update {
                            it.copy(
                                currentSong = matchedSong,
                                currentSongIndex = newIndex,
                                animationDirection = direction
                            )
                        }

                        val teleId = matchedSong.song.telegramFileId
                        val isCached = urlCache.containsKey(teleId)
                        val currentUrl = urlCache[teleId]

                        Log.d("DEBUG_PLAYER", "   🎵 Lagu Terdeteksi: ${matchedSong.song.title}")
                        Log.d("DEBUG_PLAYER", "   🆔 Telegram ID: $teleId")

                        if (isCached) {
                            Log.d("DEBUG_PLAYER", "   ✅ CACHE HIT: URL Stream sudah siap! ($currentUrl)")
                        } else {
                            Log.w("DEBUG_PLAYER", "   ⚠️ CACHE MISS: URL Stream BELUM ADA di cache!")
                            // Jika ini muncul saat klik manual, itulah penyebab macetnya.
                            // Player mencoba memutar URL kosong/mentah dari database.
                        }

                        // 2. Fetch Detail Lengkap (Lirik, Uploader, dll)
                        // Karena matchedSong dari playlist datanya masih "Parsial"
                        fetchFullSongDetails(matchedSong.song.id)

                        // --- LOGIKA WARNA DITAMBAHKAN DI SINI JUGA ---
                        // Saat lagu berganti, ekstrak warna dari lagu yang baru.
                        viewModelScope.launch {
                            val colors = extractGradientColorsFromImageUrl(
                                context = getApplication(),
                                imageUrl = matchedSong.song.coverUrl ?: ""
                            )
                            _uiState.update { it.copy(dominantColors = colors) }
                        }

                    } else {
                        Log.w("DEBUG_PLAYER", "   ⚠️ FALLBACK: Menggunakan metadata dari MediaItem (Lagu tidak ada di list local)")
                        // fallback kalau nggak ketemu di playlist
                        _uiState.update {
                            it.copy(
                                currentSong = SongWithArtist(
                                    song = com.example.remusic.data.model.Song(
                                        id = mediaItem.mediaId,
                                        title = mediaItem.mediaMetadata.title?.toString() ?: "Unknown",
                                        audioUrl = mediaItem.localConfiguration?.uri.toString(),
                                        coverUrl = mediaItem.mediaMetadata.artworkUri.toString(),
                                        lyrics = "" // default kosong
                                    ),
                                    artist = null
                                )
                            )
                        }
                    }
                }
            }


            // Listener untuk update shuffle dan repeat mode
            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                Log.d("PlayMusicViewModel", "LISTENER: Shuffle mode changed to: $shuffleModeEnabled")
                _uiState.update { it.copy(isShuffleModeEnabled = shuffleModeEnabled) }

                viewModelScope.launch {
                    userPreferencesRepository.setShuffleEnabled(shuffleModeEnabled)
                    Log.d("PlayMusicViewModel", "Saved new shuffle mode: $shuffleModeEnabled")
                }
            }

            override fun onRepeatModeChanged(repeatMode: Int) {
                _uiState.update { it.copy(repeatMode = repeatMode) }

                viewModelScope.launch {
                    userPreferencesRepository.setRepeatMode(repeatMode)
                    Log.d("PlayMusicViewModel", "Saved new repeat mode: $repeatMode")
                }
            }
        })
    }

    private fun updatePlayerState() {
        mediaController?.let { player ->
            _uiState.update {
                it.copy(
                    isPlaying = player.isPlaying,
                    totalDuration = player.duration.coerceAtLeast(0L),
                    currentPosition = player.currentPosition.coerceAtLeast(0L)
                )
            }
        }
    }

    private var positionJob: Job? = null

    private fun startUpdatingPosition() {
        positionJob?.cancel()
        positionJob = viewModelScope.launch {
            while (isActive) {
                val controller = mediaController
                if (controller != null && controller.isPlaying) {
                    val pos = controller.currentPosition
                    val dur = controller.duration
                    _uiState.update { state ->
                        state.copy(
                            currentPosition = pos,
                            totalDuration = if (dur > 0) dur else state.totalDuration
                        )
                    }
                }
                delay(500)
            }
        }
    }

    private fun stopUpdatingPosition() {
        positionJob?.cancel()
        positionJob = null
    }

    private fun fetchFullSongDetails(songId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Cek dulu, apakah data sekarang liriknya sudah ada?
                // Kalau sudah ada (misal hasil fetch sebelumnya), gak usah fetch lagi biar hemat.
                val currentSongData = _uiState.value.currentSong?.song
                if (currentSongData?.id == songId && !currentSongData.lyrics.isNullOrBlank()) {
                    Log.d("PlayMusicViewModel", "Lirik sudah ada di cache memory, skip fetch.")
                    return@launch
                }

                Log.d("PlayMusicViewModel", "Fetching FULL details for song ID: $songId")

                // Request ke Supabase: Ambil satu baris penuh berdasarkan ID
                val fullSongData = SupabaseManager.client
                    .from("songs")
                    .select {
                        filter {
                            eq("id", songId)
                        }
                    }
                    .decodeSingle<Song>() // Ini akan mengisi lyrics, uploader_id, created_at, dll

                // Update UI State dengan data lengkap
                _uiState.update { state ->
                    // Pastikan lagu yang sedang diputar MASIH lagu yang sama dengan yang kita fetch
                    // (Menghindari race condition kalau user ganti lagu cepat banget)
                    if (state.currentSong?.song?.id == songId) {
                        state.copy(
                            currentSong = state.currentSong.copy(song = fullSongData)
                        )
                    } else {
                        state
                    }
                }
                Log.d("PlayMusicViewModel", "Full details updated (Lyrics: ${fullSongData.lyrics?.take(20)}...)")

            } catch (e: Exception) {
                Log.e("PlayMusicViewModel", "Failed to fetch full song details: ${e.message}")
            }
        }
    }

    fun setPlaylist(songs: List<SongWithArtist>, startIndex: Int = 0) {
        Log.d("DEBUG_PLAYER", "📥 SET PLAYLIST: Menerima ${songs.size} lagu. Start Index: $startIndex")

        // 1. Validasi Awal
        if (songs.isEmpty()) {
            Log.w("PlayMusicViewModel", "setPlaylist: songs list kosong!")
            return
        }

        val controller = mediaController
        if (controller == null) {
            Log.w("PlayMusicViewModel", "Controller not ready, saving playlist for later.")
            _uiState.update { it.copy(playlist = songs, currentSong = songs[startIndex]) }
            return
        }

        Log.d("PlayMusicViewModel", "setPlaylist called with ${songs.size} songs, startIndex=$startIndex")

        // 2. Update UI State Langsung (Biar user lihat judul lagu berubah cepat)
        _uiState.update { it.copy(playlist = songs, currentSong = songs[startIndex]) }

        // 3. MULAI PROSES ASYNC (Resolve URL & Warna)
        // Kita bungkus semua proses mapping & player setup di sini
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {

            // A. Ambil Lagu yang mau dimainkan
            val songToPlay = songs.getOrNull(startIndex) ?: return@launch

            Log.d("DEBUG_PLAYER", "⚡ PROCESS: Resolving URL untuk lagu pertama (Index $startIndex): ${songToPlay.song.title}")
            // B. Resolve URL (Tunggu sebentar request ke Vercel/Cache)
            // Ini langkah kuncinya! Kita putuskan mau pakai link Telegram atau AudioURL biasa
            val resolvedUrl = resolveSongUrl(songToPlay)
            Log.d("PlayMusicViewModel", "URL Resolved for first song: $resolvedUrl")

            // C. Fetch Data Lengkap (Lirik, dll) untuk lagu pertama
            fetchFullSongDetails(songToPlay.song.id)

            // C. Extract Warna (Sambil jalan)
            val colors = extractGradientColorsFromImageUrl(
                context = getApplication(),
                imageUrl = songToPlay.song.coverUrl ?: ""
            )
            // Update warna ke UI
            _uiState.update { it.copy(dominantColors = colors) }

            Log.d("DEBUG_PLAYER", "🛠 MAPPING: Membuat MediaItems...")

            // D. Mapping jadi MediaItems (DI DALAM COROUTINE)
            // Kenapa di dalam? Karena kita butuh 'resolvedUrl' untuk index == startIndex
            val mediaItems = songs.mapIndexed { index, s ->
                val meta = MediaMetadata.Builder()
                    .setTitle(s.song.title)
                    .setArtist(s.artist?.name)
                    .setArtworkUri(s.song.coverUrl?.takeIf { it.isNotBlank() }?.toUri())
                    .build()

                // LOGIKA PENTING:
                // Jika ini lagu yang mau dimainkan (startIndex), pakai 'resolvedUrl' (hasil Vercel).
                // Jika lagu lain, pakai 'audioUrl' biasa dulu (nanti di-prefetch sambil jalan).
                val urlToUse = if (index == startIndex) resolvedUrl else (s.song.audioUrl ?: "")

                val item = MediaItem.Builder()
                    .setMediaId(s.song.id)
                    .setUri(urlToUse)
                    .setMediaMetadata(meta)
                    .build()

                // Debug log (Sesuai kode Mas)
                if (index == startIndex) {
                    Log.d("DEBUG_PLAYER", "   🎵 [Index $index] (ACTIVE) ${s.song.title} -> $urlToUse")
                }else {
                    Log.d("DEBUG_PLAYER", "   ❓ [Index $index] (QUEUE) URL Masih Mentah: $urlToUse")
                }
                item
            }

            Log.d("PlayMusicViewModel", "Total MediaItems mapped: ${mediaItems.size}")

            // E. Set ke MediaController (Pindah ke Main Thread)
            // Wajib pakai withContext(Main) karena menyentuh UI/Player
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                Log.d("DEBUG_PLAYER", "▶ PLAYER: Menyiapkan dan memutar playlist...")
                mediaController?.apply {
                    setMediaItems(mediaItems, startIndex, C.TIME_UNSET)
                    Log.d("PlayMusicViewModel", "MediaItems set to controller, playing index=$startIndex")
                    prepare()
                    play()
                } ?: Log.e("PlayMusicViewModel", "mediaController is null inside coroutine!")
            }
        }
    }



    fun togglePlayPause() {
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

    // --- FUNGSI BARU UNTUK FADE OUT ---
    private fun fadeOutAndPause() {
        val controller = mediaController ?: return
        volumeFadeJob?.cancel() // Batalkan fade sebelumnya jika ada
        isFading = true

        volumeFadeJob = viewModelScope.launch {
            val startVolume = controller.volume
            val steps = (FADE_DURATION_MS / FADE_INTERVAL_MS).toInt()
            val volumeStep = startVolume / steps

            for (i in 1..steps) {
                val newVolume = (startVolume - (volumeStep * i)).coerceIn(0.0f, 1.0f)
                controller.volume = newVolume
                delay(FADE_INTERVAL_MS)
            }

            // Setelah fade selesai, pause dan kembalikan volume ke 1.0f
            // agar saat play lagi, fade-in bisa dimulai dari volume penuh.
            controller.pause()
            controller.volume = 1.0f
            isFading = false
        }
    }

    // --- FUNGSI BARU UNTUK FADE IN ---
    private fun fadeInAndPlay() {
        val controller = mediaController ?: return
        volumeFadeJob?.cancel()
        isFading = true

        volumeFadeJob = viewModelScope.launch {
            // Set volume ke 0, lalu mulai mainkan (tidak akan terdengar)
            controller.volume = 0.0f
            controller.play()
            _uiState.update { it.copy(isPlaying = true) } // Update UI secara manual

            val targetVolume = 1.0f
            val steps = (FADE_DURATION_MS / FADE_INTERVAL_MS).toInt()
            val volumeStep = targetVolume / steps

            for (i in 1..steps) {
                val newVolume = (volumeStep * i).coerceIn(0.0f, 1.0f)
                controller.volume = newVolume
                delay(FADE_INTERVAL_MS)
            }

            // Pastikan volume kembali penuh
            controller.volume = 1.0f
            isFading = false
            // Panggil listener secara manual jika perlu
            if (controller.isPlaying != _uiState.value.isPlaying) {
                _uiState.update { it.copy(isPlaying = controller.isPlaying) }
            }
        }
    }

    fun toggleShuffleMode() {
        val currentShuffleState = _uiState.value.isShuffleModeEnabled
        Log.d("PlayMusicViewModel", "COMMAND: Toggling shuffle. Current state is $currentShuffleState. Setting to ${!currentShuffleState}")
        mediaController?.shuffleModeEnabled = !currentShuffleState
    }

    // Fungsi ini akan berputar: OFF -> ONE -> ALL -> OFF
    fun cycleRepeatMode() {
        val nextRepeatMode = when (_uiState.value.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ONE
            Player.REPEAT_MODE_ONE -> Player.REPEAT_MODE_ALL
            else -> Player.REPEAT_MODE_OFF
        }
        mediaController?.repeatMode = nextRepeatMode
    }

    fun seekTo(position: Long) {
        mediaController?.seekTo(position)
    }

    fun nextSong() {
        mediaController?.seekToNext()
        _uiState.update { it.copy(animationDirection = AnimationDirection.FORWARD) }
    }

    fun previousSong() {
        mediaController?.seekToPrevious()
        _uiState.update { it.copy(animationDirection = AnimationDirection.BACKWARD) }
    }

    fun playSongAt(index: Int) {
        val controller = mediaController

        if (controller == null) {
            Log.e("DEBUG_PLAYER", "❌ ERROR: MediaController is NULL")
            return
        }

        val playlist = _uiState.value.playlist

        // Validasi index
        if (index !in playlist.indices) {
            Log.e("DEBUG_PLAYER", "❌ ERROR: Index $index di luar jangkauan")
            return
        }

        val targetSong = playlist[index]
        Log.d("DEBUG_PLAYER", "==================================================")
        Log.d("DEBUG_PLAYER", "👇 CLICK MANUAL (FIXED): User ingin memutar index $index: '${targetSong.song.title}'")

        // 1. BATALKAN JOB SEBELUMNYA (Supaya kalau user spam klik, yang terakhir yang menang)
        jumpJob?.cancel()
        setPlaylistJob?.cancel()

        // 2. MULAI JOB BARU
        jumpJob = viewModelScope.launch(Dispatchers.IO) {

            // Cek apakah URL valid sudah ada di Cache?
            val telegramId = targetSong.song.telegramFileId
            val isCached = !telegramId.isNullOrBlank() && urlCache.containsKey(telegramId)

            Log.d("DEBUG_PLAYER", "   ❓ Status Cache: $isCached")

            if (!isCached) {
                // JIKA BELUM ADA DI CACHE -> REQUEST API DULU!
                Log.d("DEBUG_PLAYER", "   ⏳ WAITING: URL belum siap, meminta link stream baru ke API...")

                // Resolve URL (Request ke Vercel/Telegram)
                val newUrl = resolveSongUrl(targetSong)

                Log.d("DEBUG_PLAYER", "   ✅ URL RESOLVED: $newUrl")
                Log.d("DEBUG_PLAYER", "   🔄 UPDATE PLAYER: Mengganti URL dummy dengan URL asli...")

                // Update URL di Playlist Player agar saat di-seek nanti tidak error
                updateMediaItemUrlInPlayer(index, targetSong, newUrl)
            } else {
                Log.d("DEBUG_PLAYER", "   🚀 READY: URL sudah ada di cache, langsung gas!")
            }

            // 3. SETELAH URL SIAP, BARU SURUH PLAYER PINDAH
            withContext(Dispatchers.Main) {
                // Cek lagi apakah index masih valid (siapa tau playlist berubah saat loading)
                if (index < controller.mediaItemCount) {
                    Log.d("DEBUG_PLAYER", "   ▶ SEEK & PLAY: Melompat ke index $index sekarang.")
                    controller.seekTo(index, C.TIME_UNSET)
                    controller.play()
                } else {
                    Log.e("DEBUG_PLAYER", "   ❌ ERROR: Index $index sudah tidak valid saat mau seek.")
                }
            }

            // 4. Update data tambahan (Lirik & Warna) sambil jalan
            fetchFullSongDetails(targetSong.song.id)
            val colors = extractGradientColorsFromImageUrl(getApplication(), targetSong.song.coverUrl ?: "")
            _uiState.update { it.copy(dominantColors = colors) }

            Log.d("DEBUG_PLAYER", "==================================================")
        }
    }

    fun playingMusicFromPlaylist(playlistName: String) {
        _uiState.update {
            it.copy(
                playingMusicFromPlaylist = playlistName
            )
        }
    }

    private suspend fun resolveSongUrl(song: SongWithArtist): String {
        val title = song.song.title
        val telegramId = song.song.telegramFileId
        val originalUrl = song.song.audioUrl

        Log.d("DEBUG_PLAYER", "🔍 RESOLVE: Mulai mencari link untuk lagu: '$title'")
        Log.d("DEBUG_PLAYER", "   - ID Telegram: $telegramId")
        Log.d("DEBUG_PLAYER", "   - URL Database (Fallback): $originalUrl")

        // 1. Cek: Apakah lagu ini punya ID Telegram?
        if (!telegramId.isNullOrBlank()) {

            // 2. Cek Cache: Udah pernah request belum?
            if (urlCache.containsKey(telegramId)) {
                val cachedUrl = urlCache[telegramId]!!

                // LOG INI MEMBUKTIKAN KAMU HEMAT KUOTA & WAKTU
                Log.d("DEBUG_PLAYER", "   🧠 MEMORY CHECK: Data ditemukan di RAM Cache!")
                Log.d("DEBUG_PLAYER", "   🛑 API CALL: SKIPPED (Tidak ada request ke server)")
                Log.d("DEBUG_PLAYER", "   ✅ CACHE HIT: Menggunakan URL yang sudah tersimpan.")
                Log.d("DEBUG_PLAYER", "   -> URL: $cachedUrl")
                Log.d("DEBUG_PLAYER", "--------------------------------------------------")
                return cachedUrl
            }

            Log.d("DEBUG_PLAYER", "   💨 MEMORY CHECK: Kosong/Belum ada.")
            Log.d("DEBUG_PLAYER", "   🌐 NETWORK: Membuka koneksi ke API Vercel...")

            // 3. Kalau belum, Request ke Vercel (Panggil Retrofit)
            try {
                Log.d("DEBUG_PLAYER", "   🌐 NETWORK: Requesting new link from Vercel API...")
                // Pastikan RetrofitInstance sudah dibuat sesuai langkah sebelumnya
                val response = RetrofitInstance.api.getStreamUrl(telegramId)

                if (response.success && !response.url.isNullOrBlank()) {
                    urlCache[telegramId] = response.url

                    Log.d("DEBUG_PLAYER", "   ✅ API SUKSES: Link baru didapatkan.")
                    Log.d("DEBUG_PLAYER", "   💾 CACHING: Menyimpan URL ke RAM agar request berikutnya instan.")
                    Log.d("DEBUG_PLAYER", "   -> URL: ${response.url}")
                    Log.d("DEBUG_PLAYER", "--------------------------------------------------")

                    return response.url
                }else {
                    Log.e("DEBUG_PLAYER", "   ❌ API GAGAL: Response success=false atau url kosong.")
                }
            } catch (e: Exception) {
                Log.e("DEBUG_PLAYER", "   ❌ API ERROR: ${e.message}")
            }
        }else {
            Log.w("DEBUG_PLAYER", "   ⚠️ SKIP: Tidak ada Telegram ID.")
        }

        // Fallback
        Log.w("DEBUG_PLAYER", "   ⚠️ FALLBACK: Menggunakan URL asli dari database Supabase.")
        Log.d("DEBUG_PLAYER", "   -> URL PLAY: $originalUrl")
        Log.d("DEBUG_PLAYER", "--------------------------------------------------")
        return song.song.audioUrl ?: ""
    }

    // --- [TAMBAHKAN 3 FUNGSI INI DI PALING BAWAH] ---

    // 1. Helper buat bikin MediaItem biar gak duplikat kode
    private fun createMediaItem(data: SongWithArtist, streamUrl: String): MediaItem {
        val meta = MediaMetadata.Builder()
            .setTitle(data.song.title)
            .setArtist(data.artist?.name ?: "Unknown")
            .setArtworkUri(data.song.coverUrl?.takeIf { it.isNotBlank() }?.toUri())
            .build()

        return MediaItem.Builder()
            .setMediaId(data.song.id)
            .setUri(streamUrl) // URL bisa dari Telegram atau Supabase
            .setMediaMetadata(meta)
            .build()
    }

    // 2. Logic Pre-fetching Background
    private fun prefetchSongAtIndex(index: Int) {
        val playlist = _uiState.value.playlist

        // Cek validitas index (Harus dalam range 0 s/d size-1)
        if (index < 0 || index >= playlist.size) return

        val targetSong = playlist[index]

        viewModelScope.launch(Dispatchers.IO) {
            // Cek apakah lagu ini punya Telegram ID dan BELUM ada di Cache?
            if (!targetSong.song.telegramFileId.isNullOrBlank() &&
                !urlCache.containsKey(targetSong.song.telegramFileId)) {

                Log.d("DEBUG_PLAYER", "🔮 PRE-FETCH: Menyiapkan lagu index $index ('${targetSong.song.title}')...")

                val newUrl = resolveSongUrl(targetSong) // Request API Vercel

                // Kalau URL berhasil didapat, update player diam-diam
                if (newUrl != targetSong.song.audioUrl) {
                    updateMediaItemUrlInPlayer(index, targetSong, newUrl)
                }
            }
        }
    }

    // 3. Update Playlist ExoPlayer Diam-diam
    private suspend fun updateMediaItemUrlInPlayer(index: Int, song: SongWithArtist, newUrl: String) {
        withContext(kotlinx.coroutines.Dispatchers.Main) {
            val controller = mediaController ?: return@withContext
            if (index >= controller.mediaItemCount) return@withContext

            val newItem = createMediaItem(song, newUrl)
            controller.replaceMediaItem(index, newItem)
            Log.d("Remusic", "Berhasil update link streaming untuk lagu index: $index")
        }
    }


    override fun onCleared() {
        super.onCleared()
        mediaController?.release()
    }
}