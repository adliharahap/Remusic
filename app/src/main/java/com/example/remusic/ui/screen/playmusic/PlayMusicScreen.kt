package com.example.remusic.ui.screen.playmusic

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
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
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayMusicScreen(
    playMusicViewModel: PlayMusicViewModel,
    navController: NavController
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
    LaunchedEffect(uiState.currentSong?.song?.id, uiState.currentSong?.song?.lyrics) {
        val rawLyrics = uiState.currentSong?.song?.lyrics
        if (!rawLyrics.isNullOrBlank()) {
            lyricsViewModel.loadLyrics(rawLyrics)
        } else {
            lyricsViewModel.loadLyrics("")
        }
    }

    // 3. Sync Position: Pass playback position to LyricsVM
    LaunchedEffect(uiState.currentPosition) {
        lyricsViewModel.updatePlaybackPosition(uiState.currentPosition)
    }

    // 2. ANIMASIKAN setiap warna secara terpisah
    // Animasi untuk warna atas (top color)
    val animatedTopColor by animateColorAsState(
        targetValue = uiState.dominantColors.getOrElse(0) { Color.DarkGray },
        animationSpec = tween(1000),
        label = "topColorAnimation"
    )

    // Animasi untuk warna bawah (bottom color)
    val animatedBottomColor by animateColorAsState(
        targetValue = uiState.dominantColors.getOrElse(1) { Color.Black },
        animationSpec = tween(1000),
        label = "bottomColorAnimation"
    )

    // 3. Buat Brush menggunakan WARNA YANG SUDAH DIANIMASIKAN
    val animatedGradientBrush = Brush.verticalGradient(
        colors = listOf(animatedTopColor, animatedBottomColor)
    )

    // Scroll state untuk halaman NowPlaying (agar Header bisa baca posisi scroll)
    val nowPlayingScrollState = rememberScrollState()

    // 1. BUAT STATE UNTUK MENYIMPAN TINGGI HEADER
    val density = LocalDensity.current
    var headerHeight by remember { mutableStateOf(120.dp) }

    // --- SLEEP TIMER STATE ---
    var showSleepTimerSheet by remember { mutableStateOf(false) }
    val sleepTimerSheetState = androidx.compose.material3.rememberModalBottomSheetState()

    // Gunakan BoxWithConstraints di root untuk mendapatkan tinggi layar (maxHeight)
    BoxWithConstraints (
        modifier = Modifier
            .fillMaxSize()
            .background(brush = animatedGradientBrush)
    ) {
        val screenHeight = maxHeight

        // ==========================================
        // LAYER 1 (BELAKANG): PAGER FULL SCREEN
        // ==========================================
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize() // Full layar sampai mentok atas
        ) { page ->
            when (page) {
                0 -> QueueScreen(
                    songWithArtist = uiState.currentSong,
                    playlistQueue = uiState.playlist,
                    playMusicFromPlaylist = uiState.playingMusicFromPlaylist,
                    onClickListener = { playMusicViewModel.playSongAt(it) }
                )
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

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Back icon
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }

                // TABS
                Row(
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
                                fontFamily = if (isSelected) AppFont.RobotoBold else AppFont.RobotoMedium,
                                fontSize = if (isSelected) 17.sp else 15.sp,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                            if (isSelected) {
                                Box(
                                    modifier = Modifier
                                        .height(2.dp)
                                        .width(40.dp)
                                        .background(Color.White, shape = RoundedCornerShape(1.dp))
                                )
                            } else {
                                Spacer(modifier = Modifier.height(2.dp))
                            }
                        }
                    }
                }

                // Menu Icon
                IconButton(onClick = { /* TODO */ }) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = "More",
                        tint = Color.White
                    )
                }
            }
        }
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
}