package com.example.remusic.ui.screen.playmusic

import android.annotation.SuppressLint
import android.widget.Toast
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.MarqueeAnimationMode
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import com.example.remusic.data.network.LrcLibResponse


import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.remusic.R
import com.example.remusic.data.model.LyricLine
import com.example.remusic.data.model.SongWithArtist
import com.example.remusic.data.model.displayArtistName
import com.example.remusic.ui.theme.AppFont
import com.example.remusic.utils.GoogleFontDownloader
import com.example.remusic.utils.formatDuration
import com.example.remusic.data.preferences.PlayerBackgroundStyle
import com.example.remusic.viewmodel.playmusic.LyricsViewModel
import com.example.remusic.utils.toHdUrl
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

@SuppressLint("ConfigurationScreenWidthHeight")
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LyricsScreen(
    lyricsViewModel: LyricsViewModel,
    topPlayerColor: Color,
    bottomPlayerColor: Color,
    songWithArtist: SongWithArtist?,
    isPlaying: Boolean,
    totalDuration: Long,
    isLoading: Boolean,
    headerHeight: Dp,
    onPlayPauseClick: () -> Unit,
    onSeek: (Float) -> Unit,
    onRetryClick: () -> Unit,
    lyricsOffset: Long,
    onOffsetChange: (Long) -> Unit,
    onSearchLyrics: (String) -> Unit,
    isSearching: Boolean = false,
    searchResults: List<LrcLibResponse> = emptyList(),
    onApplyLyrics: (LrcLibResponse) -> Unit = {},
    lyricsConfig: LyricsConfig = LyricsConfig(),
    onSeekToMs: (Long) -> Unit = {},
    playbackSpeed: Float = 1.0f,
    playerBackgroundStyle: PlayerBackgroundStyle = PlayerBackgroundStyle.LIQUID_GRADIENT,
    gradientBrush: Brush = Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent)),
    isFetchingSyncedLyrics: Boolean = false,
    meshColors: List<Color> = emptyList(),
    onTranslateLyrics: (String) -> Unit = {}
) {
    // 1. Ambil State dari ViewModel
    val lyrics by lyricsViewModel.lyrics.collectAsState()
    val activeIndex by lyricsViewModel.activeLyricIndex.collectAsState()
    val isTranslateLyrics by lyricsViewModel.isTranslateLyrics.collectAsState()
    val isAutoTranslated by lyricsViewModel.isAutoTranslated.collectAsState()
    val translationProgressText by lyricsViewModel.translationProgress.collectAsState()
    val isTranslating by lyricsViewModel.isTranslating.collectAsState()
    val currentPosition by lyricsViewModel.currentPosition.collectAsState()
    val hasAutoScrolledToTop by lyricsViewModel.hasAutoScrolledToTop.collectAsState()

    // Variable UI
    var previousSongId by remember { mutableStateOf(songWithArtist?.song?.id) }
    val currentSongId = songWithArtist?.song?.id

    val lazyListState = rememberLazyListState()
    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()
    
    val isBlurredOrMesh = playerBackgroundStyle == PlayerBackgroundStyle.BLURRED_COVER || playerBackgroundStyle == PlayerBackgroundStyle.MESH_GRADIENT || playerBackgroundStyle == PlayerBackgroundStyle.LIQUID_GRADIENT || playerBackgroundStyle == PlayerBackgroundStyle.LINEAR_GRADIENT
    
    var showLyricsOptions by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // 🔥 2. PERBAIKAN OFFSET TENGAH (Mencegah bug lirik terlalu ke bawah/atas)
    val topPadding = 200.dp
    val centerOffsetPx: Int = with(density) {
        val centerOfScreen = screenHeight.toPx() / 2f
        val estimatedItemHalfHeight = 30.dp.toPx() // Perkiraan setengah tinggi teks lirik

        val shiftUp = 60.dp.toPx()
        
        // Offset = (Jarak padding atas) - (Tengah layar) + (Setengah tinggi font)
        // Dibuat negatif karena di Compose negatif berarti mendorong item ke bawah dari batas atas
        (topPadding.toPx() - centerOfScreen + estimatedItemHalfHeight + shiftUp).toInt()
    }

    // 🎯 STATE: Apakah auto-scroll sedang aktif
    var isAutoScrollEnabled by remember { mutableStateOf(true) }
    var isProgrammaticScroll by remember { mutableStateOf(false) }

    // 🎯 STATE: Pelacakan Sync Lyrics Gagal / Tidak Ditemukan
    var wasFetching by remember { mutableStateOf(false) }
    var showUnavailableBanner by remember { mutableStateOf(false) }

    // Reset UI dan sheet ketika lagu berganti
    LaunchedEffect(currentSongId) {
        if (currentSongId != previousSongId) {
            previousSongId = currentSongId
            showLyricsOptions = false
            showUnavailableBanner = false
            isAutoScrollEnabled = true
            isProgrammaticScroll = true
            try {
                lazyListState.scrollToItem(0)
            } finally {
                isProgrammaticScroll = false
            }
        }
    }

    LaunchedEffect(isFetchingSyncedLyrics) {
        if (isFetchingSyncedLyrics) {
            wasFetching = true
        } else if (wasFetching) {
            wasFetching = false
            val snapSongId = currentSongId
            // Beri jeda 600ms agar LyricsViewModel selesai mem-parsing lirik synced yang baru
            delay(600)
            if (currentSongId == snapSongId && !lyrics.isSynced() && lyrics.isNotEmpty() && !isLoading) {
                showUnavailableBanner = true
                delay(3000)
                showUnavailableBanner = false
            }
        }
    }

    // 🔝 SMART AUTO-SCROLL TO TOP
    LaunchedEffect(currentPosition, lyrics, hasAutoScrolledToTop) {
        if (!hasAutoScrolledToTop && lyrics.isNotEmpty()) {
            val firstLyricTimestamp = lyrics.firstOrNull { it.timestamp != -1L }?.timestamp ?: 0L
            if (currentPosition < firstLyricTimestamp) {
                isProgrammaticScroll = true
                try {
                    lazyListState.scrollToItem(0) 
                } finally {
                    isProgrammaticScroll = false
                    lyricsViewModel.setAutoScrolledToTop(true)
                }
            }
        }
    }

    // 🛑 Deteksi USER MANUAL SCROLL → matikan auto-scroll
    LaunchedEffect(lazyListState) {
        snapshotFlow { Pair(lazyListState.isScrollInProgress, isProgrammaticScroll) }
            .collect { (isScrolling, isProgrammatic) ->
                if (isScrolling && !isProgrammatic) {
                    isAutoScrollEnabled = false
                }
            }
    }

    // 🔄 🔥 PERBAIKAN RE-ENGAGE AUTO SCROLL (Menggunakan visibleItemsInfo seperti IntersectionObserver di Web)
    LaunchedEffect(lazyListState) {
        snapshotFlow { Pair(lazyListState.isScrollInProgress, activeIndex) }
            .collectLatest { (isScrolling, currentIndex) ->
                if (!isScrolling && !isAutoScrollEnabled && !isProgrammaticScroll) {
                    delay(600) 
                    if (!lazyListState.isScrollInProgress && !isAutoScrollEnabled) {
                        val isTargetVisible = lazyListState.layoutInfo.visibleItemsInfo.any { it.index == currentIndex }
                        if (isTargetVisible) {
                            isAutoScrollEnabled = true
                        }
                    }
                }
            }
    }

    // 📜 Auto Scroll Logic untuk follow active lyric 
    LaunchedEffect(lazyListState) {
        snapshotFlow { Pair(activeIndex, isAutoScrollEnabled) }
            .collectLatest { (index, autoScroll) ->
                if (autoScroll && index in lyrics.indices) {
                    delay(100) 
                    isProgrammaticScroll = true
                    try {
                        lazyListState.animateScrollToItem(
                            index = index,
                            scrollOffset = centerOffsetPx 
                        )
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } finally {
                        isProgrammaticScroll = false
                    }
                }
            }
    }

    Box(modifier = Modifier.fillMaxSize()) {        // --- LOGIKA UTAMA TAMPILAN (CONTENT / EMPTY / LOADING) ---
        Crossfade(
            targetState = when {
                isLoading -> "LOADING"
                lyrics.isEmpty() -> "EMPTY"
                else -> "CONTENT"
            },
            animationSpec = tween(500),
            label = "LyricsStateAnimation"
        ) { state ->

            when (state) {
                "LOADING" -> {
                    LyricsSkeletonLoader()
                }
                "EMPTY" -> {
                    EmptyLyricsView(onRetryClick = onRetryClick)
                }
                "CONTENT" -> {
                    val hasSynced = lyrics.isSynced()
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .then(
                                if (isBlurredOrMesh) {
                                    Modifier
                                        .graphicsLayer { alpha = 0.99f }
                                        .drawWithContent {
                                            drawContent()
                                            
                                            val topFadeEndPx = (headerHeight - 10.dp).toPx()
                                            val topFadeStartPx = (headerHeight + 70.dp).toPx()
                                            val bottomFadeStartPx = size.height - 200.dp.toPx()
                                            val bottomFadeEndPx = size.height - 130.dp.toPx()
                                            
                                            drawRect(
                                                brush = Brush.verticalGradient(
                                                    colorStops = arrayOf(
                                                        0.0f to Color.Transparent,
                                                        (topFadeEndPx / size.height).coerceIn(0f, 1f) to Color.Transparent,
                                                        (topFadeStartPx / size.height).coerceIn(0f, 1f) to Color.Black,
                                                        (bottomFadeStartPx / size.height).coerceIn(0f, 1f) to Color.Black,
                                                        (bottomFadeEndPx / size.height).coerceIn(0f, 1f) to Color.Transparent,
                                                        1.0f to Color.Transparent
                                                    ),
                                                    startY = 0f,
                                                    endY = size.height
                                                ),
                                                blendMode = BlendMode.DstIn
                                            )
                                        }
                                } else {
                                    Modifier
                                }
                            )
                    ) {
                        Crossfade(
                            targetState = hasSynced,
                            animationSpec = tween(450),
                            label = "LyricsContentTransition"
                        ) { synced ->
                        if (synced) {
                            LazyColumn(
                                state = lazyListState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(top = topPadding, bottom = screenHeight / 2),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                itemsIndexed(
                                    items = lyrics,
                                    key = { index, line -> "${currentSongId}_synced_${index}_${line.originalText.hashCode()}" }
                                ) { index, line ->
                                    Column(
                                        horizontalAlignment = Alignment.Start,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 24.dp, vertical = 12.dp)
                                    ) {
                                        LyricLineItem(
                                            line = line,
                                            isActive = index == activeIndex,
                                            isPassed = index <= activeIndex,
                                            isTranslateLyrics = isTranslateLyrics,
                                            lyricsConfig = lyricsConfig,
                                            lazyListState = lazyListState,
                                            index = index,
                                            onSeekToMs = onSeekToMs
                                        )
                                    }
                                }

                                // --- LYRICS ATTRIBUTION FOOTER ---
                                item {
                                    val rawLyrics = songWithArtist?.song?.lyrics ?: ""
                                    if (rawLyrics.contains("lyrics from lrclib", ignoreCase = true)) {
                                        Text(
                                            text = "lyrics from lrclib",
                                            color = Color.White.copy(alpha = 0.3f),
                                            fontSize = 11.sp,
                                            fontFamily = AppFont.RobotoRegular,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(top = 32.dp, bottom = 16.dp),
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(top = topPadding, bottom = screenHeight / 2),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                itemsIndexed(
                                    items = lyrics,
                                    key = { index, line -> "${currentSongId}_plain_${index}_${line.originalText.hashCode()}" }
                                ) { index, line ->
                                    Column(
                                        horizontalAlignment = Alignment.Start,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 24.dp, vertical = 12.dp)
                                    ) {
                                        LyricLineItem(
                                            line = line,
                                            isActive = false,
                                            isPassed = false,
                                            isTranslateLyrics = isTranslateLyrics,
                                            lyricsConfig = lyricsConfig,
                                            lazyListState = null,
                                            index = index,
                                            onSeekToMs = onSeekToMs
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            }
        }

        // 1. Gradient ATAS
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(headerHeight + 40.dp)
                .heightIn(min = 100.dp, max = 200.dp)
                .align(Alignment.TopCenter)
                .then(
                    if (isBlurredOrMesh) {
                        Modifier
                    } else {
                        Modifier.background(
                            Brush.verticalGradient(
                                colorStops = arrayOf(
                                    0.0f to topPlayerColor,
                                    0.5f to topPlayerColor,
                                    0.6f to topPlayerColor.copy(alpha = 0.8f),
                                    0.8f to topPlayerColor.copy(alpha = 0.5f),
                                    1.0f to topPlayerColor.copy(alpha = 0f)
                                )
                            )
                        )
                    }
                )
        )

        // 2. Gradient BAWAH
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.65f)
                .align(Alignment.BottomCenter)
                .then(
                    if (isBlurredOrMesh) {
                        Modifier
                    } else {
                        Modifier.background(
                            Brush.verticalGradient(
                                colorStops = arrayOf(
                                    0.0f to bottomPlayerColor.copy(alpha = 0f),
                                    0.4f to bottomPlayerColor.copy(alpha = 0.1f),
                                    0.6f to bottomPlayerColor.copy(alpha = 0.5f),
                                    0.8f to bottomPlayerColor,
                                    1.0f to bottomPlayerColor
                                )
                            )
                        )
                    }
                )
        )

        // --- PANEL KONTROL BAWAH ---
        LyricsBottomPanel(
            modifier = Modifier.align(Alignment.BottomCenter),
            currentPosition = currentPosition,
            totalDuration = totalDuration,
            onSeek = onSeek,
            songWithArtist = songWithArtist,
            isPlaying = isPlaying,
            onPlayPauseClick = onPlayPauseClick,
            isTranslateLyrics = isTranslateLyrics,
            onToggleTranslateLyrics = { lyricsViewModel.toggleTranslateLyrics() },
            onTranslateRequest = { 
                if (currentSongId != null && songWithArtist?.song?.lyrics != null) {
                    val musicDao = com.example.remusic.data.local.MusicDatabase.getDatabase(context).musicDao()
                    lyricsViewModel.translateCurrentLyrics(currentSongId, songWithArtist.song.lyrics, musicDao, songWithArtist.song) { translatedText ->
                        onTranslateLyrics(translatedText)
                    }
                } else {
                    android.widget.Toast.makeText(context, "Tidak ada lirik untuk diterjemahkan", android.widget.Toast.LENGTH_SHORT).show()
                }
            },
            isTranslating = lyricsViewModel.isTranslating.collectAsState().value,
            onShowOptions = { showLyricsOptions = true },
            lyrics = lyrics,
            playbackSpeed = playbackSpeed
        )


        // 🔄 FLOATING BADGE "Menunggu Lirik Sinkron..."
        AnimatedVisibility(
            visible = isFetchingSyncedLyrics && !lyrics.isSynced() && !isLoading && isAutoScrollEnabled,
            enter = slideInVertically(initialOffsetY = { it }, animationSpec = tween(250)) + fadeIn(animationSpec = tween(250)),
            exit = slideOutVertically(targetOffsetY = { it }, animationSpec = tween(200)) + fadeOut(animationSpec = tween(200)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 155.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50.dp))
                    .background(Color.White.copy(alpha = 0.08f))
                    .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(50.dp))
                    .padding(horizontal = 14.dp, vertical = 7.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    androidx.compose.material3.CircularProgressIndicator(
                        modifier = Modifier.size(10.dp),
                        strokeWidth = 1.dp,
                        color = Color.White.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Menyinkronkan lirik...",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 11.sp,
                        fontFamily = AppFont.RobotoMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // 🌐 FLOATING BANNER "Terjemahan Offline"
        val hasTranslationMsg = remember(lyrics, isAutoTranslated) { 
            lyrics.any { it.translatedText != null } && isAutoTranslated 
        }
        var showTranslationWarning by remember { androidx.compose.runtime.mutableStateOf(false) }
        var translationToggleCounter by remember { androidx.compose.runtime.mutableIntStateOf(0) }

        androidx.compose.runtime.LaunchedEffect(isTranslateLyrics) {
            translationToggleCounter++
        }

        androidx.compose.runtime.LaunchedEffect(translationToggleCounter, hasTranslationMsg) {
            if (isTranslateLyrics && hasTranslationMsg) {
                showTranslationWarning = true
                kotlinx.coroutines.delay(4000)
                showTranslationWarning = false
            } else {
                showTranslationWarning = false
            }
        }

        AnimatedVisibility(
            visible = showTranslationWarning && isAutoScrollEnabled,
            enter = slideInVertically(initialOffsetY = { it }, animationSpec = tween(250)) + fadeIn(animationSpec = tween(250)),
            exit = slideOutVertically(targetOffsetY = { it }, animationSpec = tween(200)) + fadeOut(animationSpec = tween(200)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 155.dp) // Sesuaikan dengan posisi banner lain
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50.dp))
                    .background(Color.White.copy(alpha = 0.08f))
                    .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(50.dp))
                    .padding(horizontal = 14.dp, vertical = 7.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Translate,
                        contentDescription = "Translate Warning",
                        tint = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Terjemahan offline otomatis. Maklumi jika kaku.",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 11.sp,
                        fontFamily = AppFont.RobotoMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // 🔄 FLOATING BANNER "PROGRES TRANSLATE / DOWNLOAD MODEL"
        AnimatedVisibility(
            visible = isTranslating,
            enter = slideInVertically(initialOffsetY = { it }, animationSpec = tween(250)) + fadeIn(animationSpec = tween(250)),
            exit = slideOutVertically(targetOffsetY = { it }, animationSpec = tween(200)) + fadeOut(animationSpec = tween(200)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 155.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50.dp))
                    .background(Color(0xFF1E1E1E).copy(alpha = 0.88f))
                    .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(50.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.CircularProgressIndicator(
                        modifier = Modifier.size(13.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(9.dp))
                    Text(
                        text = translationProgressText,
                        color = Color.White,
                        fontSize = 12.sp,
                        fontFamily = AppFont.RobotoMedium
                    )
                }
            }
        }

        // ❌ FLOATING BANNER "Lirik synced tidak ditemukan"
        AnimatedVisibility(
            visible = showUnavailableBanner && isAutoScrollEnabled,
            enter = slideInVertically(initialOffsetY = { it }, animationSpec = tween(250)) + fadeIn(animationSpec = tween(250)),
            exit = slideOutVertically(targetOffsetY = { it }, animationSpec = tween(200)) + fadeOut(animationSpec = tween(200)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 155.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50.dp))
                    .background(Color.Black.copy(alpha = 0.25f))
                    .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(50.dp))
                    .padding(horizontal = 14.dp, vertical = 7.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFE53935)) // Sleek warning red
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Lirik synced tidak ditemukan",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 11.sp,
                        fontFamily = AppFont.RobotoMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // ↩ FLOATING BUTTON "Kembali ke Lirik"
        AnimatedVisibility(
            visible = !isAutoScrollEnabled && activeIndex in lyrics.indices,
            enter = slideInVertically(initialOffsetY = { it }, animationSpec = tween(250)) + fadeIn(animationSpec = tween(250)),
            exit = slideOutVertically(targetOffsetY = { it }, animationSpec = tween(200)) + fadeOut(animationSpec = tween(200)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 155.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50.dp))
                    .background(Color.White.copy(alpha = 0.18f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        isAutoScrollEnabled = true
                    }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                val scrollDirectionIcon = if (lazyListState.firstVisibleItemIndex > activeIndex) {
                    Icons.Default.KeyboardArrowUp
                } else {
                    Icons.Default.KeyboardArrowDown
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = scrollDirectionIcon,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Kembali ke Lirik",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontFamily = AppFont.RobotoMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // --- LYRICS OPTIONS BOTTOM SHEET ---
        if (showLyricsOptions) {
            LyricsOptionsBottomSheet(
                onDismiss = { showLyricsOptions = false },
                lyricsOffset = lyricsOffset,
                onOffsetChange = onOffsetChange,
                onSearchLyrics = onSearchLyrics,
                isSearching = isSearching,
                searchResults = searchResults,
                onApplyLyrics = onApplyLyrics,
                topPlayerColor = topPlayerColor,
                trackName = songWithArtist?.song?.title ?: "",
                artistName = songWithArtist?.displayArtistName ?: "",
                isTelegramSong = songWithArtist?.song?.source == "telegram" || songWithArtist?.song?.telegramFileId != null || songWithArtist?.song?.uploaderUserId != null,
                onRegenerateTranslation = {
                    currentSongId?.let { id -> 
                        val musicDao = com.example.remusic.data.local.MusicDatabase.getDatabase(context).musicDao()
                        lyricsViewModel.regenerateTranslation(id, musicDao, songWithArtist?.song) { translated ->
                            onTranslateLyrics(translated)
                        }
                    }
                },
                onRegenerateGemini = { modelName ->
                    val musicDao = com.example.remusic.data.local.MusicDatabase.getDatabase(context).musicDao()
                    currentSongId?.let { id ->
                        val rawLrcBuilder = java.lang.StringBuilder()
                        for (line in lyricsViewModel.lyrics.value) {
                            if (line.originalText == "[tool:MLKit]" || line.originalText == "[tool:Gemini]") continue
                            
                            if (line.timestamp == -1L) {
                                rawLrcBuilder.append(line.originalText).append("\n")
                            } else {
                                val totalMs = line.timestamp
                                val min = totalMs / 60000
                                val sec = (totalMs % 60000) / 1000
                                val ms = (totalMs % 1000) / 10
                                val tsString = String.format("[%02d:%02d.%02d]", min, sec, ms)
                                rawLrcBuilder.append(tsString).append(line.originalText).append("\n")
                            }
                        }
                        val rawLrc = rawLrcBuilder.toString()
                        val title = songWithArtist?.song?.title ?: ""
                        val artist = songWithArtist?.displayArtistName ?: ""
                        lyricsViewModel.translateWithGemini(
                            rawLrc = rawLrc,
                            songId = id,
                            modelName = modelName,
                            songTitle = title,
                            artistName = artist,
                            musicDao = musicDao,
                            song = songWithArtist?.song,
                            onSuccess = { translatedText ->
                                onTranslateLyrics(translatedText)
                            }
                        )
                    }
                }
            )
        }
    }
}

@Composable
fun LyricsSkeletonLoader() {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 120.dp, bottom = 150.dp, start = 24.dp, end = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        userScrollEnabled = false
    ) {
        items(25) { index ->
            val widthFraction = when (index % 3) {
                0 -> 0.6f 
                1 -> 0.8f 
                else -> 0.4f 
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth(widthFraction)
                    .height(20.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White.copy(alpha = alpha))
            )
        }
    }
}

@Composable
fun EmptyLyricsView(onRetryClick: () -> Unit = {}) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Rounded.Lyrics,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.5f),
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Lirik tidak tersedia",
                color = Color.White.copy(alpha = 0.9f),
                fontFamily = AppFont.RobotoMedium,
                fontSize = 18.sp
            )
            Text(
                text = "Atau lirik belum disinkronisasi.",
                color = Color.White.copy(alpha = 0.5f),
                fontFamily = AppFont.RobotoRegular,
                fontSize = 14.sp
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Try Again Button
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.15f))
                    .clickable { onRetryClick() }
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = androidx.compose.material.icons.Icons.Default.Refresh,
                        contentDescription = "Retry",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Coba Lagi",
                        color = Color.White,
                        fontFamily = AppFont.RobotoMedium,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

@Composable
fun LyricLineItem(
    line: LyricLine,
    isActive: Boolean,
    isPassed: Boolean,
    isTranslateLyrics: Boolean,
    lyricsConfig: LyricsConfig,
    lazyListState: androidx.compose.foundation.lazy.LazyListState? = null,
    index: Int = -1,
    onSeekToMs: (Long) -> Unit = {}
) {
    val animationSpec = tween<Float>(durationMillis = 550, easing = LinearOutSlowInEasing)
    val colorAnimationSpec = tween<Color>(durationMillis = 450, easing = LinearOutSlowInEasing)

    val context = LocalContext.current

    val fontFamily = remember(lyricsConfig.fontFamily, lyricsConfig.mainFontWeight) {
        when {
            lyricsConfig.fontFamily == LyricsFontFamily.JOSEFIN_SANS -> AppFont.JosefinSans
            lyricsConfig.fontFamily == LyricsFontFamily.POPPINS   -> AppFont.Poppins
            lyricsConfig.fontFamily == LyricsFontFamily.ROBOTO    -> AppFont.RobotoRegular
            lyricsConfig.fontFamily == LyricsFontFamily.HELVETICA -> AppFont.Helvetica
            lyricsConfig.fontFamily.isOnline -> {
                val baseTypeface = GoogleFontDownloader.loadCachedTypeface(
                    context, lyricsConfig.fontFamily.googleFontName
                )
                if (baseTypeface != null) {
                    val weightValue = lyricsConfig.mainFontWeight.weight.weight
                    val resolvedTypeface = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                        android.graphics.Typeface.create(baseTypeface, weightValue, false)
                    } else {
                        val style = when {
                            weightValue >= 700 -> android.graphics.Typeface.BOLD
                            else -> android.graphics.Typeface.NORMAL
                        }
                        android.graphics.Typeface.create(baseTypeface, style)
                    }
                    androidx.compose.ui.text.font.FontFamily(resolvedTypeface)
                } else {
                    AppFont.JosefinSans
                }
            }
            else -> AppFont.JosefinSans
        }
    }

    val translationFontFamily = remember(lyricsConfig.fontFamily, lyricsConfig.translationFontWeight) {
        when {
            lyricsConfig.fontFamily == LyricsFontFamily.JOSEFIN_SANS -> AppFont.JosefinSans
            lyricsConfig.fontFamily == LyricsFontFamily.POPPINS   -> AppFont.Poppins
            lyricsConfig.fontFamily == LyricsFontFamily.ROBOTO    -> AppFont.RobotoRegular
            lyricsConfig.fontFamily == LyricsFontFamily.HELVETICA -> AppFont.Helvetica
            lyricsConfig.fontFamily.isOnline -> {
                val baseTypeface = GoogleFontDownloader.loadCachedTypeface(
                    context, lyricsConfig.fontFamily.googleFontName
                )
                if (baseTypeface != null) {
                    val weightValue = lyricsConfig.translationFontWeight.weight.weight
                    val resolvedTypeface = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                        android.graphics.Typeface.create(baseTypeface, weightValue, false)
                    } else {
                        val style = when {
                            weightValue >= 700 -> android.graphics.Typeface.BOLD
                            else -> android.graphics.Typeface.NORMAL
                        }
                        android.graphics.Typeface.create(baseTypeface, style)
                    }
                    androidx.compose.ui.text.font.FontFamily(resolvedTypeface)
                } else {
                    AppFont.JosefinSans
                }
            }
            else -> AppFont.JosefinSans
        }
    }

    val textAlign = when (lyricsConfig.align) {
        LyricsAlign.LEFT -> TextAlign.Left
        LyricsAlign.CENTER -> TextAlign.Center
        LyricsAlign.RIGHT -> TextAlign.Right
    }
    
    val horizontalAlignment = when (lyricsConfig.align) {
        LyricsAlign.LEFT -> Alignment.Start
        LyricsAlign.CENTER -> Alignment.CenterHorizontally
        LyricsAlign.RIGHT -> Alignment.End
    }

    val baseFontSize = lyricsConfig.fontSize
    val shouldScale = lyricsConfig.autoScaleIfNoTranslation && (!isTranslateLyrics || line.translatedText == null)
    val addedSize = if (shouldScale) (lyricsConfig.scaleFactor * 2) else 0
    
    val finalFontSize = (baseFontSize + addedSize).sp

    // We don't animate fontSize anymore to prevent reflow. We use the target finalFontSize directly.
    val fontSize = finalFontSize.value

    // Animation for Scale
    val targetScale = if (isActive && lyricsConfig.animateActiveLyric) 1.07f else 1.0f
    val scale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = animationSpec,
        label = "scaleAnimation"
    )

    // Gap (Padding) - dibuat statis agar tidak ada jedug saat scroll (LazyColumn re-measurement)
    val verticalPadding = if (lyricsConfig.animateActiveLyric) 8.dp else 2.dp

    // Margin ekstra horizontal agar lirik yang discale tidak terpotong (keluar layar)
    val extraHorizontalPadding = if (lyricsConfig.animateActiveLyric) {
        when (lyricsConfig.align) {
            LyricsAlign.LEFT -> PaddingValues(end = 16.dp)
            LyricsAlign.CENTER -> PaddingValues(horizontal = 8.dp)
            LyricsAlign.RIGHT -> PaddingValues(start = 16.dp)
        }
    } else {
        PaddingValues(0.dp)
    }

    val transformOrigin = when (lyricsConfig.align) {
        LyricsAlign.LEFT -> TransformOrigin(0f, 0.5f)
        LyricsAlign.CENTER -> TransformOrigin(0.5f, 0.5f)
        LyricsAlign.RIGHT -> TransformOrigin(1f, 0.5f)
    }

    val isPlainLyrics = line.timestamp == -1L
    
    val targetFontColor = if (isActive || (isPassed && lyricsConfig.markPassedLyrics) || isPlainLyrics) {
        Color.White
    } else {
        if (lyricsConfig.inactiveLyricsColor == InactiveLyricsColor.BLACK) {
            Color.Black
        } else {
            Color.White.copy(alpha = 0.5f)
        }
    }

    val fontColor by animateColorAsState(
        targetValue = targetFontColor,
        animationSpec = colorAnimationSpec,
        label = "fontColorAnimation"
    )

    val targetTranslatedColor = if (isActive || (isPassed && lyricsConfig.markPassedLyrics) || isPlainLyrics) {
        Color.White.copy(alpha = 0.9f)
    } else {
        if (lyricsConfig.inactiveLyricsColor == InactiveLyricsColor.BLACK) {
            Color.Black.copy(alpha = 0.8f)
        } else {
            Color.White.copy(alpha = 0.5f)
        }
    }

    val translatedColor by animateColorAsState(
        targetValue = targetTranslatedColor,
        animationSpec = colorAnimationSpec,
        label = "translatedColorAnimation"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (lyricsConfig.clickLyricsToSeek && line.timestamp != -1L) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onSeekToMs(line.timestamp) }
                } else Modifier
            )
            .padding(vertical = verticalPadding)
            .padding(extraHorizontalPadding)
            .graphicsLayer {
                if (lyricsConfig.animateActiveLyric && lazyListState != null && index != -1) {
                    val itemInfo = lazyListState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
                    if (itemInfo != null) {
                        val viewportCenterY = lazyListState.layoutInfo.viewportEndOffset / 2f
                        val itemCenterY = itemInfo.offset + (itemInfo.size / 2f)
                        
                        // Distance from center (-1f to 1f)
                        val normalizedDistance = ((itemCenterY - viewportCenterY) / viewportCenterY).coerceIn(-1f, 1f)
                        
                        if (normalizedDistance > 0f) {
                            // Rotate around X axis (bottom items tilt forward)
                            rotationX = normalizedDistance * -35f
                            // Parallax stretching (push items away from center slightly)
                            translationY = (normalizedDistance * normalizedDistance) * 30f
                            // Pengecilan dinamis (semakin ke bawah, semakin kecil)
                            val depthScale = 1f - (normalizedDistance * 0.15f)
                            scaleX = depthScale
                            scaleY = depthScale
                        } else {
                            // Bagian atas dibiarkan rata (flat)
                            rotationX = 0f
                            translationY = 0f
                            scaleX = 1f
                            scaleY = 1f
                        }
                    }
                }
            },
        horizontalAlignment = horizontalAlignment 
    ) {
        Text(
            text = line.originalText,
            color = fontColor,
            fontSize = fontSize.sp,
            lineHeight = (fontSize * 1.2f).sp,
            fontFamily = fontFamily,
            fontWeight = lyricsConfig.mainFontWeight.weight,
            textAlign = textAlign,
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    this.transformOrigin = transformOrigin
                }
        )

        AnimatedVisibility(
            visible = line.translatedText != null && isTranslateLyrics,
            enter = slideInVertically(
                initialOffsetY = { -it },
                animationSpec = tween(durationMillis = 300)
            ) + fadeIn(animationSpec = tween(durationMillis = 300)) + expandVertically(
                expandFrom = Alignment.Top,
                animationSpec = tween(durationMillis = 300)
            ),
            exit = slideOutVertically(
                targetOffsetY = { -it },
                animationSpec = tween(durationMillis = 300)
            ) + fadeOut(animationSpec = tween(durationMillis = 300)) + shrinkVertically(
                shrinkTowards = Alignment.Top,
                animationSpec = tween(durationMillis = 300)
            )
        ) {
            if (line.translatedText != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = line.translatedText,
                    color = translatedColor,
                    fontSize = lyricsConfig.translateFontSize.sp,
                    fontFamily = translationFontFamily,
                    fontWeight = lyricsConfig.translationFontWeight.weight,
                    textAlign = textAlign,
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            this.transformOrigin = transformOrigin
                        }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun LyricsBottomPanel(
    modifier: Modifier = Modifier,
    currentPosition: Long,
    totalDuration: Long,
    onSeek: (Float) -> Unit,
    songWithArtist: SongWithArtist?,
    isPlaying: Boolean,
    onPlayPauseClick: () -> Unit,
    isTranslateLyrics: Boolean,
    onToggleTranslateLyrics: () -> Unit,
    onTranslateRequest: () -> Unit,
    isTranslating: Boolean = false,
    onShowOptions: () -> Unit,
    lyrics: List<LyricLine>,
    playbackSpeed: Float = 1.0f
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val nestedScrollConnection = remember {
        object : NestedScrollConnection {}
    }

    var isUserSeeking by remember { mutableStateOf(false) }
    var userSeekPosition by remember { mutableFloatStateOf(0f) }

    val sliderValue = if (isUserSeeking) {
        userSeekPosition
    } else {
        if (totalDuration > 0) (currentPosition.toFloat() / totalDuration.toFloat()) else 0f
    }

    val displayCurrentTime = if (isUserSeeking) {
        (userSeekPosition * totalDuration).toLong()
    } else {
        currentPosition
    }

    val scaledTotalDuration = if (playbackSpeed > 0f) {
        (totalDuration / playbackSpeed).toLong()
    } else {
        totalDuration
    }
    
    val scaledCurrentTime = if (playbackSpeed > 0f) {
        (displayCurrentTime / playbackSpeed).toLong()
    } else {
        displayCurrentTime
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(140.dp)
            .verticalScroll(scrollState)
            .nestedScroll(nestedScrollConnection),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Bottom,
    ) {
        Row (modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp),
        ){
            AsyncImage(
                model = songWithArtist?.song?.coverUrl?.toHdUrl(),
                contentDescription = "Cover Album",
                modifier = Modifier
                    .width(50.dp)
                    .aspectRatio(1f)
                    .shadow(
                        elevation = 6.dp,
                        shape = RoundedCornerShape(6.dp),
                        clip = false
                    )
                    .clip(RoundedCornerShape(6.dp)),
                contentScale = ContentScale.Crop,
                placeholder = painterResource(id = R.drawable.img_placeholder),
                error = painterResource(id = R.drawable.img_placeholder)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column (
                modifier = Modifier.weight(0.7f)
            ){
                Text(
                    text = songWithArtist?.song?.title ?: "Unknown Title",
                    color = Color.White,
                    fontFamily = AppFont.RobotoBold,
                    fontSize = 20.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .basicMarquee(
                            animationMode = MarqueeAnimationMode.Immediately,
                            velocity = 70.dp,
                            initialDelayMillis = 2000,
                            repeatDelayMillis = 5000,
                            iterations = Int.MAX_VALUE,
                        )
                        .padding(bottom = 5.dp)
                )
                Text(
                    text = songWithArtist?.displayArtistName ?: "Unknown Artist",
                    color = Color(0xCCFFFFFF),
                    fontFamily = AppFont.RobotoRegular,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Row(
                modifier = Modifier
                    .weight(0.3f)
                    .fillMaxHeight()
                    .padding(end = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End,
            ) {
                val hasTranslation = lyrics.any { it.translatedText != null }
                
                if (isTranslating) {
                    androidx.compose.material3.CircularProgressIndicator(
                        modifier = Modifier.size(24.dp).padding(2.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.Translate,
                        contentDescription = "Translate",
                        tint = if (isTranslateLyrics && hasTranslation) Color.White else Color.White.copy(0.6f),
                        modifier = Modifier.size(26.dp).combinedClickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {
                                if (hasTranslation) {
                                    onToggleTranslateLyrics()
                                } else {
                                    onTranslateRequest()
                                }
                            },
                            onLongClick = {
                                onShowOptions()
                            }
                        )
                    )
                }
                
                Spacer(modifier = Modifier.width(16.dp))

                Icon(
                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = "Play / Pause",
                    tint = Color.White,
                    modifier = Modifier.size(34.dp).clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {onPlayPauseClick()}
                )
            }
        }
        Box(
            modifier = Modifier.fillMaxWidth().height(10.dp)
        ) {
            Slider(
                value = sliderValue,
                onValueChange = { newValue ->
                    isUserSeeking = true
                    userSeekPosition = newValue
                },
                onValueChangeFinished = {
                    onSeek(userSeekPosition)
                    isUserSeeking = false
                },
                valueRange = 0f..1f,
                modifier = Modifier.padding(horizontal = 8.dp),
                colors = SliderDefaults.colors(
                    thumbColor = Color.Transparent,
                    activeTrackColor = Color.Transparent,
                    inactiveTrackColor = Color.Transparent
                ),
                thumb = {
                    Box(
                        modifier = Modifier
                            .offset(y = 1.dp)
                            .size(14.dp)
                            .background(color = Color.White, shape = CircleShape)
                    )
                },
                track = { sliderPositions ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(color = Color.White.copy(0.4f), shape = RoundedCornerShape(2.dp))
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(sliderPositions.value)
                                .fillMaxHeight()
                                .background(color = Color.White, shape = RoundedCornerShape(2.dp))
                        )
                    }
                }
            )
        }

        Row (
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ){
            Text(
                text = formatDuration(scaledCurrentTime),
                color = Color.White,
                fontFamily = AppFont.Coolvetica,
                fontWeight = FontWeight.Normal,
                fontSize = 14.sp,
            )
            Text(
                text = formatDuration(scaledTotalDuration),
                color = Color.White,
                fontFamily = AppFont.Coolvetica,
                fontWeight = FontWeight.Normal,
                fontSize = 14.sp,
            )
        }
        Spacer(modifier = Modifier.height(25.dp).fillMaxWidth())
    }
}



fun List<LyricLine>.isSynced(): Boolean {
    if (isEmpty()) return false
    if (size == 1 && first().originalText == "[Instrumental]") return true
    return any { it.timestamp != -1L }
}