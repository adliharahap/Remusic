package com.example.remusic.ui.screen.playmusic

import com.example.remusic.viewmodel.playmusic.*
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.remusic.ui.theme.AppFont
import com.example.remusic.utils.LockScreenOrientationPortrait
import com.example.remusic.viewmodel.playmusic.LyricsViewModel
import com.example.remusic.viewmodel.playmusic.PlayMusicViewModel
import com.example.remusic.utils.toHdUrl
import com.example.remusic.ui.components.AddToPlaylistBottomSheet
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayMusicScreen(
    playMusicViewModel: PlayMusicViewModel,
    navController: NavController,
    onNavigateToArtist: (artistId: String) -> Unit = {}, // Callback: navigate to artist detail
    onNavigateToUploader: (uploaderId: String) -> Unit = {} // Callback: navigate to uploader playlist
) {
    LockScreenOrientationPortrait()
    val pagerState = rememberPagerState(initialPage = 1, pageCount = { 3 })
    // Langsung gunakan uiState dari ViewModel yang sudah berjalan
    val uiState by playMusicViewModel.uiState.collectAsState()

    val lyricsViewModel: LyricsViewModel = viewModel()

    // --- FIX: Gunakan Lifecycle Observer untuk mendeteksi layar nyala (ON_RESUME) ---
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                // 1. Cek database lagi (siapa tau fetch saat layar mati gagal)
                playMusicViewModel.refreshCurrentSongData()

                // 2. Paksa sync lirik ke LyricsViewModel jika data sudah ada
                val currentLyrics = uiState.currentSong?.song?.lyrics
                if (!currentLyrics.isNullOrBlank()) {
                    lyricsViewModel.loadLyrics(currentLyrics)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // 1. Refresh Data on Screen Wake/Entry
    LaunchedEffect(Unit) {
        playMusicViewModel.refreshCurrentSongData()
    }

    // 2. Monitor Lyrics: Pass raw lyrics from PlayMusicVM to LyricsVM
    LaunchedEffect(uiState.currentSong?.song?.id, uiState.currentSong?.song?.lyrics, uiState.temporaryTranslatedLyrics) {
        val finalLyricsToLoad = uiState.temporaryTranslatedLyrics ?: uiState.currentSong?.song?.lyrics
        // RESET STATE SCROLL SETIAP GANTI LAGU
        lyricsViewModel.resetScrollState()
        
        if (!finalLyricsToLoad.isNullOrBlank()) {
            lyricsViewModel.loadLyrics(finalLyricsToLoad)
        } else {
            lyricsViewModel.loadLyrics("")
        }
    }

    // 3. Sync Position: Pass playback position to LyricsVM
    LaunchedEffect(uiState.currentPosition, uiState.lyricsOffset) {
        lyricsViewModel.updatePlaybackPosition(uiState.currentPosition, uiState.lyricsOffset)
    }

    // 1. Logika Overriding Index Warna jika lirik Inactive adalah Hitam
    val topIndex = uiState.gradientTopColorIndex
    val bottomIndex = uiState.gradientBottomColorIndex
    val inactiveColor = uiState.lyricsConfig.inactiveLyricsColor

    val (finalTopIndex, finalBottomIndex) = if (inactiveColor == InactiveLyricsColor.BLACK) {
        // Jika di 3 (index 2) -> turunkan ke 1 (index 0)
        // Jika di 4 (index 3) -> turunkan ke 2 (index 1)
        val t = when (topIndex) {
            2 -> 0
            3 -> 1
            else -> topIndex
        }
        val b = when (bottomIndex) {
            2 -> 0
            3 -> 1
            else -> bottomIndex
        }
        t to b
    } else {
        topIndex to bottomIndex
    }

    // 2. ANIMASIKAN setiap warna secara terpisah
    // Animasi untuk warna atas (top color)
    val animatedTopColor by animateColorAsState(
        targetValue = uiState.dominantColors.getOrElse(finalTopIndex) { Color.DarkGray },
        animationSpec = tween(1000),
        label = "topColorAnimation"
    )

    // Animasi untuk warna bawah (bottom color)
    val animatedBottomColor by animateColorAsState(
        targetValue = uiState.dominantColors.getOrElse(finalBottomIndex) { Color.Black },
        animationSpec = tween(1000),
        label = "bottomColorAnimation"
    )

    // 3. Buat Brush menggunakan WARNA YANG SUDAH DIANIMASIKAN
    val animatedGradientBrush = Brush.verticalGradient(
        colors = listOf(animatedTopColor, animatedBottomColor)
    )

    // --- EXIT FLAG ---
    var isExiting by remember { mutableStateOf(false) }

    LaunchedEffect(isExiting) {
        if (isExiting) {
            kotlinx.coroutines.delay(50)
            navController.popBackStack()
        }
    }

    androidx.activity.compose.BackHandler(enabled = !isExiting) {
        isExiting = true
    }

    // Scroll state untuk halaman NowPlaying (agar Header bisa baca posisi scroll)
    val nowPlayingScrollState = rememberScrollState()

    // 1. BUAT STATE UNTUK MENYIMPAN TINGGI HEADER
    val density = LocalDensity.current
    var headerHeight by remember { mutableStateOf(120.dp) }

    // --- SLEEP TIMER STATE ---
    var showSleepTimerSheet by remember { mutableStateOf(false) }
    val sleepTimerSheetState = androidx.compose.material3.rememberModalBottomSheetState()

    // --- MORE OPTIONS SHEET STATE ---
    var showMoreOptionsSheet by remember { mutableStateOf(false) }
    val moreOptionsSheetState = androidx.compose.material3.rememberModalBottomSheetState()

    // --- FONT PICKER SHEET STATE ---
    var showFontPickerSheet by remember { mutableStateOf(false) }
    val fontPickerSheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // --- GAME MODE SHEET STATE ---
    var showGameModeSheet by remember { mutableStateOf(false) }
    val gameModeSheetState = androidx.compose.material3.rememberModalBottomSheetState()
    
    // --- AUDIO SWITCH SHEET STATE ---
    var showAudioSwitchSheet by remember { mutableStateOf(false) }
    // --- SNACKBAR LOGIC (Moved here for scope) ---
    val snackbarHostState = remember { androidx.compose.material3.SnackbarHostState() }
    
    // 1. Show Snackbar Logic
    LaunchedEffect(uiState.showQueueInfoSnackbar) {
        if (uiState.showQueueInfoSnackbar) {
            val result = snackbarHostState.showSnackbar(
                message = """
                    Antrean dibuat otomatis dari tambahkan ke antrean.
                    Untuk menambahkan lagu berikutnya, gunakan "Tambahkan ke Antrean" atau "Putar Setelah Ini".
                """.trimIndent(),
                actionLabel = "Oke",
                duration = androidx.compose.material3.SnackbarDuration.Indefinite
            )
            if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                playMusicViewModel.dismissQueueInfoSnackbar()
            }
        }
    }

    // 2. Auto-Dismiss Logic if Playlist Changes
    LaunchedEffect(uiState.playingMusicFromPlaylist) {
        // Jika snackbar aktif TAPI playlist ganti judul (artinya user memutar playlist lain),
        // maka hilangkan snackbar.
        if (uiState.showQueueInfoSnackbar && uiState.playingMusicFromPlaylist != "Tambahkan Lagu ke Antrean") {
             // Dismiss secara internal di VM agar UI update
             playMusicViewModel.dismissQueueInfoSnackbar()
             // Secara visual side-effect di hostState mungkin butuh manual dismiss, 
             // tapi karena showQueueInfoSnackbar jadi false, effect di atas akan recompose/batal?
             // SnackbarHostState.currentSnackbarData?.dismiss() bisa dipanggil jika perlu immediate.
             snackbarHostState.currentSnackbarData?.dismiss()
        }
    }

    // Gunakan BoxWithConstraints di root untuk mendapatkan tinggi layar (maxHeight)
    BoxWithConstraints (
        modifier = Modifier
            .fillMaxSize()
            .background(brush = animatedGradientBrush)
    ) {
        PlayerBackground(
            style = uiState.playerBackgroundStyle,
            topColor = animatedTopColor,
            bottomColor = animatedBottomColor,
            gradientBrush = animatedGradientBrush,
            coverUrl = uiState.currentSong?.song?.coverUrl?.toHdUrl(),
            meshColors = uiState.dominantColors
        )

        // Overlay Putih Halus untuk Lirik Hitam agar mata tidak sakit (mengurangi saturasi background)
        if (inactiveColor == InactiveLyricsColor.BLACK) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White.copy(alpha = 0.35f))
            )
        }
        val screenHeight = maxHeight

        // ==========================================
        // LAYER 1 (BELAKANG): PAGER FULL SCREEN
        // ==========================================
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize() // Full layar sampai mentok atas
        ) { page ->
            when (page) {
                0 -> {
                    val context = LocalContext.current

                    QueueScreen(
                        songWithArtist = uiState.currentSong,
                        playlistQueue = uiState.playlist,
                        playMusicFromPlaylist = uiState.playingMusicFromPlaylist,
                        playlistSubtitle = uiState.playlistSubtitle, // Pass subtitle
                        onMoveQueueItem = { from, to -> playMusicViewModel.moveQueueItem(from, to) },
                        onClickListener = { playMusicViewModel.playSongAt(it) },
                        onAddToQueue = { song ->
                            val message = playMusicViewModel.addToQueue(song)
                            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                        },
                        onPlayNext = { song ->
                            if (uiState.currentSong?.song?.id == song.song.id) {
                                Toast.makeText(context, "Lagu ini sedang diputar", Toast.LENGTH_SHORT).show()
                            } else {
                                playMusicViewModel.playNext(song)
                                Toast.makeText(context, "${song.song.title} akan diputar setelah ini", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onAddToLiked = { song ->
                            playMusicViewModel.toggleLike(song)
                        },
                        onDownload = { song ->
                            playMusicViewModel.downloadSong(song)
                        },
                        onAddToPlaylist = { song ->
                            playMusicViewModel.showAddToPlaylistSheet(song)
                        },
                        onRemoveFromQueue = { song ->
                            playMusicViewModel.removeFromQueue(song)
                        },
                        topPlayerColor = animatedTopColor,
                        bottomPlayerColor = animatedBottomColor,
                        gradientBrush = animatedGradientBrush,
                        playerBackgroundStyle = if (uiState.isThemeAppliedToQueue) uiState.playerBackgroundStyle
                            else com.example.remusic.data.preferences.PlayerBackgroundStyle.LIQUID_GRADIENT,
                        headerHeight = headerHeight,
                        meshColors = uiState.dominantColors
                    )
                }
                1 -> NowPlaying(
                    songWithArtist = uiState.currentSong,
                    isPlaying = uiState.isPlaying,
                    isBuffering = uiState.isBuffering,
                    isLoadingData = uiState.isLoadingData,
                    debugStatus = uiState.debugStatus,
                    errorMsg = uiState.errorMessage,
                    currentPosition = uiState.currentPosition,
                    totalDuration = uiState.totalDuration,
                    isShuffleEnabled = uiState.isShuffleModeEnabled,
                    repeatMode = uiState.repeatMode,
                    // PASSING SCROLL STATE KE NOWPLAYING
                    scrollState = nowPlayingScrollState,
                    onPlayPauseClick = { playMusicViewModel.togglePlayPause() },
                    onNextClick = { playMusicViewModel.nextSong() },
                    onPrevClick = { playMusicViewModel.previousSong() },
                    onShuffleClick = { playMusicViewModel.toggleShuffleMode() },
                    onRepeatClick = { playMusicViewModel.cycleRepeatMode() },
                    onTimerClick = { showSleepTimerSheet = true },
                    isLiked = uiState.isLiked,
                    onLikeClick = { playMusicViewModel.toggleLike() },
                    posterAnimation = uiState.currentSongIndex,
                    posterAnimationDirection = uiState.animationDirection,
                    onSeek = { positionFraction ->
                        val newPosition = (uiState.totalDuration * positionFraction).toLong()
                        playMusicViewModel.seekTo(newPosition)
                    },
                    uploader = uiState.uploader,
                    isSearchContext = uiState.playlistSubtitle == "Memainkan Dari Pencarian",
                    isArtistFollowed = uiState.isArtistFollowed,
                    onToggleFollowArtist = { playMusicViewModel.toggleFollowArtist() },
                    onLihatPlaylistClick = {
                        val artist = uiState.currentSong?.artist
                        if (artist != null) {
                            if (!artist.youtubeId.isNullOrBlank()) {
                                onNavigateToArtist(artist.youtubeId)
                            } else {
                                onNavigateToArtist(artist.id)
                            }
                        }
                    },
                    onUploaderClick = {
                        val uploaderId = uiState.uploader?.uid
                        if (!uploaderId.isNullOrBlank() && uploaderId != "youtube") {
                            onNavigateToUploader(uploaderId)
                        }
                    },
                    isDataSaverModeEnabled = uiState.isDataSaverModeEnabled,
                    topPlayerColor = animatedTopColor,
                    bottomPlayerColor = animatedBottomColor,
                    gradientBrush = animatedGradientBrush,
                    headerHeight = headerHeight,
                    isExiting = isExiting,
                    playbackSpeed = uiState.playbackSpeed,
                    playerBackgroundStyle = if (uiState.isThemeAppliedToNowPlaying) uiState.playerBackgroundStyle
                        else com.example.remusic.data.preferences.PlayerBackgroundStyle.LIQUID_GRADIENT,
                    currentAudioDevice = uiState.currentAudioDevice,
                    onAddToPlaylistClick = { 
                        uiState.currentSong?.let { playMusicViewModel.showAddToPlaylistSheet(it) } 
                    },
                    onGameModeClick = { showGameModeSheet = true },
                    isGameModeEnabled = uiState.isGameModeActive,
                    gameModeVolume = uiState.gameModeVolume,
                    onAudioDeviceClick = { showAudioSwitchSheet = true },
                    meshColors = uiState.dominantColors,
                    isCrossfadeEnabled = uiState.isCrossfadeEnabled,
                    isCrossfading = uiState.isCrossfading,
                    shouldShowCrossfadeBadge = uiState.shouldShowCrossfadeBadge,
                    crossfadeDurationSeconds = uiState.crossfadeDurationSeconds
                )
                2 -> LyricsScreen(
                    lyricsViewModel = lyricsViewModel,
                    topPlayerColor = animatedTopColor,
                    bottomPlayerColor = animatedBottomColor,
                    songWithArtist = uiState.currentSong,
                    isPlaying = uiState.isPlaying,
                    totalDuration = uiState.totalDuration,
                    isLoading = uiState.isLoadingLyrics,
                    headerHeight = headerHeight,
                    onSeek = { positionFraction ->
                        val newPosition = (uiState.totalDuration * positionFraction).toLong()
                        playMusicViewModel.seekTo(newPosition)
                    },
                    onPlayPauseClick = { playMusicViewModel.togglePlayPause() },
                    lyricsConfig = uiState.lyricsConfig,
                    onSeekToMs = { ms -> playMusicViewModel.seekTo(ms) },
                    playbackSpeed = uiState.playbackSpeed,
                    playerBackgroundStyle = if (uiState.isThemeAppliedToLyrics) uiState.playerBackgroundStyle
                        else com.example.remusic.data.preferences.PlayerBackgroundStyle.LIQUID_GRADIENT,
                    gradientBrush = animatedGradientBrush,
                    onRetryClick = { playMusicViewModel.retryFetchLyrics() },
                    lyricsOffset = uiState.lyricsOffset,
                    onOffsetChange = { playMusicViewModel.setLyricsOffset(it) },
                    onSearchLyrics = { playMusicViewModel.searchAlternativeLyrics(it) },
                    isSearching = uiState.isSearchingLyrics,
                    searchResults = uiState.alternativeLyricsResults,
                    onApplyLyrics = { playMusicViewModel.applyAlternativeLyrics(it) },
                    isFetchingSyncedLyrics = uiState.isFetchingSyncedLyrics,
                    meshColors = uiState.dominantColors,
                    onTranslateLyrics = { playMusicViewModel.setTemporaryTranslatedLyrics(it) }
                )
            }
        }

        // ==========================================
        // LAYER 2 (DEPAN): HEADER FLOATING
        // ==========================================
        // Kita bungkus Header dalam Column biar Spacer tetap jalan dorong ke bawah
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter) // Paksa nempel di atas tengah
                .fillMaxWidth()
                // --- UKUR TINGGI DI SINI ---
                .onGloballyPositioned { coordinates ->
                    // Konversi pixel ke dp
                    val heightInDp = with(density) { coordinates.size.height.toDp() }

                    // Update state (hanya jika berubah biar gak recomposition loop)
                    if (headerHeight != heightInDp) {
                        headerHeight = heightInDp
                    }
                }
        ) {

            // Spacer Status Bar
            Spacer(modifier = Modifier.height(30.dp).fillMaxWidth())

            val tabs = listOf("Queue", "Playing", "Lyrics")
            val coroutineScope = rememberCoroutineScope()

            // Simpan lebar dan offset pos X (lokal ke row) tiap ukuran text tab
            var tabWidths by remember { mutableStateOf(List(tabs.size) { 0.dp }) }
            var tabOffsets by remember { mutableStateOf(List(tabs.size) { 0.dp }) }

            val indicatorOffset by animateDpAsState(
                targetValue = tabOffsets.getOrElse(pagerState.currentPage) { 0.dp },
                animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
                label = "indicatorOffset"
            )
            val indicatorWidth by animateDpAsState(
                targetValue = tabWidths.getOrElse(pagerState.currentPage) { 0.dp },
                animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
                label = "indicatorWidth"
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Back icon
                IconButton(onClick = { isExiting = true }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }

                // TABS
                Box(contentAlignment = Alignment.BottomStart) {
                    var rowX by remember { mutableStateOf(0f) }
                    Row(
                        modifier = Modifier.onGloballyPositioned { rowX = it.positionInWindow().x },
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        tabs.forEachIndexed { index, title ->
                            val isSelected = pagerState.currentPage == index
                            Column(
                                modifier = Modifier
                                    .padding(horizontal = 8.dp)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) {
                                        coroutineScope.launch {
                                            pagerState.animateScrollToPage(index)
                                        }
                                    },
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = title,
                                    color = if (isSelected) Color.White else Color.White.copy(0.8f),
                                    fontFamily = AppFont.HelveticaRoundedBold,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = if (isSelected) 17.sp else 15.sp,
                                    modifier = Modifier
                                        .padding(bottom = 6.dp) // Beri ruang untuk garis animasi di bawah
                                        .onGloballyPositioned { coordinates ->
                                            val textPositionX = coordinates.positionInWindow().x
                                            val localX = textPositionX - rowX
                                            val currentWidth = with(density) { coordinates.size.width.toDp() }
                                            val currentOffset = with(density) { localX.toDp() }

                                            if (tabWidths[index] != currentWidth || tabOffsets[index] != currentOffset) {
                                                val newWidths = tabWidths.toMutableList()
                                                newWidths[index] = currentWidth
                                                tabWidths = newWidths

                                                val newOffsets = tabOffsets.toMutableList()
                                                newOffsets[index] = currentOffset
                                                tabOffsets = newOffsets
                                            }
                                        }
                                )
                            }
                        }
                    }

                    // Garis Animasi (Width mengikuti teks, dan bergeser secara halus)
                    if (indicatorWidth > 0.dp) {
                        Box(
                            modifier = Modifier
                                .offset(x = indicatorOffset)
                                .width(indicatorWidth)
                                .height(2.dp)
                                .background(Color.White, shape = RoundedCornerShape(1.dp))
                        )
                    }
                }

                // Menu Icon
                IconButton(onClick = { showMoreOptionsSheet = true }) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = "More",
                        tint = Color.White
                    )
                }
            }
        }
         // ==========================================
        // LAYER 3 (DEPAN LAGI): SNACKBAR HOST
        // ==========================================
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 20.dp) // Berjarak sedikit dari bawah
        )
    }

    if (showSleepTimerSheet) {
        SleepTimerBottomSheet(
            sheetState = sleepTimerSheetState,
            isTimerActive = uiState.isSleepTimerActive,
            dominantColors = uiState.dominantColors,
            onDismiss = { showSleepTimerSheet = false },
            onSetTimer = { minutes ->
                playMusicViewModel.setSleepTimer(minutes)
                // Sheet will be dismissed by onDismiss call inside the sheet logic or user action
            },
            onCancelTimer = {
                playMusicViewModel.cancelSleepTimer()
            },
            timerEndTime = uiState.sleepTimerEndTime // Pass end time
        )
    }

    if (showGameModeSheet) {
        GameModeBottomSheet(
            sheetState = gameModeSheetState,
            isGameModeActive = uiState.isGameModeActive,
            gameModeVolume = uiState.gameModeVolume,
            dominantColors = uiState.dominantColors,
            onDismiss = { showGameModeSheet = false },
            onToggleGameMode = { playMusicViewModel.toggleGameMode() },
            onVolumeChange = { volume -> playMusicViewModel.setGameModeVolume(volume) }
        )
    }

    if (showAudioSwitchSheet) {
        AudioSwitchBottomSheet(
            availableDevices = uiState.availableAudioDevices,
            currentDevice = uiState.currentAudioDevice,
            onDeviceSelected = { selectedDevice ->
                playMusicViewModel.setAudioOutputDevice(selectedDevice)
            },
            onDismiss = { showAudioSwitchSheet = false }
        )
    }

    if (showMoreOptionsSheet) {
        MoreOptionsBottomSheet(
            sheetState = moreOptionsSheetState,
            songWithArtist = uiState.currentSong,
            onDismiss = { showMoreOptionsSheet = false },
            onShare = { /* TODO: Implement Share */ },
            onAddToPlaylist = {
                uiState.currentSong?.let { song ->
                    playMusicViewModel.showAddToPlaylistSheet(song)
                }
                showMoreOptionsSheet = false
            },
            onAddToLiked = {
                playMusicViewModel.toggleLike()
                showMoreOptionsSheet = false
            },
            onDownload = {
                uiState.currentSong?.let { song ->
                    playMusicViewModel.downloadSong(song)
                }
                showMoreOptionsSheet = false
            },
            onSetSleepTimer = {
                showMoreOptionsSheet = false
                showSleepTimerSheet = true
            },
            lyricsConfig = uiState.lyricsConfig,
            onLyricsConfigChange = { playMusicViewModel.updateLyricsConfig(it) },
            gradientStyle = uiState.gradientStyle,
            onGradientStyleChange = { playMusicViewModel.setGradientStyle(it) },
            gradientTopColorIndex = uiState.gradientTopColorIndex,
            onGradientTopColorIndexChange = { playMusicViewModel.setGradientTopColorIndex(it) },
            gradientBottomColorIndex = uiState.gradientBottomColorIndex,
            onGradientBottomColorIndexChange = { playMusicViewModel.setGradientBottomColorIndex(it) },
            isDataSaverModeEnabled = uiState.isDataSaverModeEnabled,
            onDataSaverModeChange = { playMusicViewModel.toggleDataSaverMode(it) },
            isCrossfadeEnabled = uiState.isCrossfadeEnabled,
            onCrossfadeEnabledChange = { playMusicViewModel.setCrossfadeEnabled(it) },
            crossfadeDuration = uiState.crossfadeDurationSeconds,
            onCrossfadeDurationChange = { playMusicViewModel.setCrossfadeDuration(it) },
            crossfadeCurve = uiState.crossfadeCurve,
            onCrossfadeCurveChange = { playMusicViewModel.setCrossfadeCurve(it) },
            playbackSpeed = uiState.playbackSpeed,
            onPlaybackSpeedChange = { playMusicViewModel.setPlaybackSpeed(it) },
            playerBackgroundStyle = uiState.playerBackgroundStyle,
            onPlayerBackgroundStyleChange = { playMusicViewModel.setPlayerBackgroundStyle(it) },
            isThemeAppliedToQueue = uiState.isThemeAppliedToQueue,
            onThemeAppliedToQueueChange = { playMusicViewModel.setThemeAppliedToQueue(it) },
            isThemeAppliedToNowPlaying = uiState.isThemeAppliedToNowPlaying,
            onThemeAppliedToNowPlayingChange = { playMusicViewModel.setThemeAppliedToNowPlaying(it) },
            isThemeAppliedToLyrics = uiState.isThemeAppliedToLyrics,
            onThemeAppliedToLyricsChange = { playMusicViewModel.setThemeAppliedToLyrics(it) },
            onOpenFontPicker = { showFontPickerSheet = true }
        )
    }

    // --- Font Picker Sheet ---
    if (showFontPickerSheet) {
        LyricsFontBottomSheet(
            sheetState = fontPickerSheetState,
            selectedFont = uiState.lyricsConfig.fontFamily,
            onFontSelected = { font ->
                playMusicViewModel.updateLyricsConfig(uiState.lyricsConfig.copy(fontFamily = font))
            },
            onDismiss = { showFontPickerSheet = false }
        )
    }

    // --- Global Add To Playlist Bottom Sheet ---

    val songToAdd = uiState.selectedSongForAddToPlaylist
    if (songToAdd != null) {
        val addToPlaylistSheetState = androidx.compose.material3.rememberModalBottomSheetState()
        val context = LocalContext.current

        AddToPlaylistBottomSheet(
            sheetState = addToPlaylistSheetState,
            onDismissRequest = { playMusicViewModel.dismissAddToPlaylistSheet() },
            playlists = uiState.userPlaylists,
            isLoading = uiState.isFetchingUserPlaylists,
            onPlaylistSelected = { playlist ->
                playMusicViewModel.addSongToPlaylist(playlist.id, songToAdd.song.id, songToAdd) { success, message ->
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                    if (success) {
                        playMusicViewModel.dismissAddToPlaylistSheet()
                    }
                }
            },
            onCreateNewPlaylistClick = {
                playMusicViewModel.dismissAddToPlaylistSheet()
                navController.popBackStack()
                playMusicViewModel.requestNavigateToCreatePlaylist()
            }
        )
    }
}