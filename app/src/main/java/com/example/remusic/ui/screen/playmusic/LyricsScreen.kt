package com.example.remusic.ui.screen.playmusic

import android.annotation.SuppressLint
import android.widget.Toast
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import com.example.remusic.utils.formatDuration
import com.example.remusic.viewmodel.playmusic.LyricsViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged

@SuppressLint("ConfigurationScreenWidthHeight")
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
    onSeek: (Float) -> Unit,
    onPlayPauseClick: () -> Unit,
    lyricsConfig: LyricsConfig = LyricsConfig(),
    onSeekToMs: (Long) -> Unit = {},
    playbackSpeed: Float = 1.0f
) {
    // 1. Ambil State dari ViewModel
    val lyrics by lyricsViewModel.lyrics.collectAsState()
    val activeIndex by lyricsViewModel.activeLyricIndex.collectAsState()
    val isTranslateLyrics by lyricsViewModel.isTranslateLyrics.collectAsState()
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

    Box(modifier = Modifier.fillMaxSize()) {

        // --- LOGIKA UTAMA TAMPILAN (CONTENT / EMPTY / LOADING) ---
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
                    EmptyLyricsView()
                }
                "CONTENT" -> {
                    LazyColumn(
                        state = lazyListState,
                        modifier = Modifier.fillMaxSize(),
                        // Pastikan top padding-nya menggunakan variabel topPadding (200.dp)
                        contentPadding = PaddingValues(top = topPadding, bottom = screenHeight / 2),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        itemsIndexed(
                            items = lyrics,
                            key = { index, line -> "${currentSongId}_${index}_${line.originalText.hashCode()}" }
                        ) { index, line ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp, vertical = 12.dp),
                                horizontalAlignment = Alignment.Start
                            ) {
                                LyricLineItem(
                                    line = line,
                                    isActive = index == activeIndex,
                                    isPassed = index <= activeIndex,
                                    isTranslateLyrics = isTranslateLyrics,
                                    lyricsConfig = lyricsConfig,
                                    onSeekToMs = onSeekToMs
                                )
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
                .background(
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
        )

        // 2. Gradient BAWAH
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.65f)
                .align(Alignment.BottomCenter)
                .background(
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
            lyrics = lyrics,
            playbackSpeed = playbackSpeed
        )

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
fun EmptyLyricsView() {
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
                color = Color.White.copy(alpha = 0.7f),
                fontFamily = AppFont.RobotoMedium,
                fontSize = 18.sp
            )
            Text(
                text = "Tenang saja, kami akan menambahkannya nanti.",
                color = Color.White.copy(alpha = 0.4f),
                fontFamily = AppFont.RobotoRegular,
                fontSize = 14.sp
            )
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
    onSeekToMs: (Long) -> Unit = {}
) {
    val animationSpec = tween<Float>(durationMillis = 200, delayMillis = 80)
    val colorAnimationSpec = tween<Color>(durationMillis = 200, delayMillis = 80)

    val fontFamily = when (lyricsConfig.fontFamily) {
        LyricsFontFamily.POPPINS -> AppFont.Poppins
        LyricsFontFamily.ROBOTO -> AppFont.RobotoRegular
        LyricsFontFamily.HELVETICA -> AppFont.Helvetica
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

    val fontSize by animateFloatAsState(
        targetValue = finalFontSize.value,
        animationSpec = animationSpec,
        label = "fontSizeAnimation"
    )

    val targetFontColor = if (isActive || (isPassed && lyricsConfig.markPassedLyrics)) {
        Color.White
    } else {
        Color.White.copy(alpha = 0.5f)
    }

    val fontColor by animateColorAsState(
        targetValue = targetFontColor,
        animationSpec = colorAnimationSpec,
        label = "fontColorAnimation"
    )

    val targetTranslatedColor = if (isActive || (isPassed && lyricsConfig.markPassedLyrics)) {
        Color.White.copy(alpha = 0.9f)
    } else {
        Color.White.copy(alpha = 0.5f)
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
            .padding(vertical = 2.dp),
        horizontalAlignment = horizontalAlignment 
    ) {
        Text(
            text = line.originalText,
            color = fontColor,
            fontSize = fontSize.sp,
            fontFamily = fontFamily,
            fontWeight = lyricsConfig.mainFontWeight.weight,
            textAlign = textAlign,
            modifier = Modifier.fillMaxWidth()
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
                    fontFamily = fontFamily,
                    fontWeight = lyricsConfig.translationFontWeight.weight,
                    textAlign = textAlign,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
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
                model = songWithArtist?.song?.coverUrl,
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
            Row(modifier = Modifier
                .weight(0.3f)
                .fillMaxHeight()
                .padding(end = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                val hasTranslation = lyrics.any { it.translatedText != null }
                Icon(
                    imageVector = Icons.Filled.Translate,
                    contentDescription = "Translate",
                    tint = if (isTranslateLyrics && hasTranslation) Color.White else Color.White.copy(0.6f),
                    modifier = Modifier.size(26.dp).clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        if (hasTranslation) {
                            onToggleTranslateLyrics()
                        } else {
                            Toast.makeText(
                                context,
                                "Lirik terjemahan tidak tersedia",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                )
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