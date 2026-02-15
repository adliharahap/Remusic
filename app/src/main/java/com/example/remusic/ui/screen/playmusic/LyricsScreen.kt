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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
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
    // Add Lyrics Config
    lyricsConfig: LyricsConfig = LyricsConfig()
) {
    // 1. Ambil State dari ViewModel
    val lyrics by lyricsViewModel.lyrics.collectAsState()
    val activeIndex by lyricsViewModel.activeLyricIndex.collectAsState()
    val isTranslateLyrics by lyricsViewModel.isTranslateLyrics.collectAsState()
    val currentPosition by lyricsViewModel.currentPosition.collectAsState()

    // Ambil State GLOBAL Auto Scroll dari ViewModel
    val hasAutoScrolledToTop by lyricsViewModel.hasAutoScrolledToTop.collectAsState()

    // Variable UI
    // Track previous song ID untuk detect song change
    var previousSongId by remember { mutableStateOf(songWithArtist?.song?.id) }
    val currentSongId = songWithArtist?.song?.id
    
    // Track previous active index untuk prevent redundant scroll
    var previousActiveIndex by remember { mutableIntStateOf(-1) }
    val lazyListState = rememberLazyListState()
    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp


    
    // 🔝 SMART AUTO-SCROLL TO TOP
    // Hanya scroll ke atas jika: belum pernah auto-scroll DAN position < timestamp lyric pertama
    LaunchedEffect(currentPosition, lyrics, hasAutoScrolledToTop) {
        if (!hasAutoScrolledToTop && lyrics.isNotEmpty()) {
            val firstLyricTimestamp = lyrics.firstOrNull { it.timestamp != -1L }?.timestamp ?: 0L
            
            // Jika current position masih di bawah lyric pertama, scroll ke atas
            if (currentPosition < firstLyricTimestamp) {
                lazyListState.scrollToItem(0) // Instant scroll ke atas
                // Tandai sudah auto-scroll (HANYA 1x!) via ViewModel
                lyricsViewModel.setAutoScrolledToTop(true)
            }
        }
    }

    // 📜 Auto Scroll Logic untuk follow active lyric (dengan smooth animation)
    LaunchedEffect(lazyListState) {
        snapshotFlow { activeIndex }
            .distinctUntilChanged()
            .collect { index ->
                if (index >= 0 && index < lyrics.size && index != previousActiveIndex) {

                    // Smooth scroll dengan delay untuk animasi yang terlihat
                    delay(150) // Beri waktu mata untuk baca lyric sebelum scroll
                    lazyListState.animateScrollToItem(
                        index = index,
                        scrollOffset = -(screenHeight.value * 0.5f).toInt()
                    )
                }
            }
    }


    Box(modifier = Modifier.fillMaxSize()) {

        // --- LOGIKA UTAMA TAMPILAN (CONTENT / EMPTY / LOADING) ---
        Crossfade(
            targetState = when {
                // HANYA Loading kalau statusnya BENAR-BENAR loading
                isLoading -> "LOADING"

                // Kalau tidak loading, tapi datanya kosong -> Empty View
                lyrics.isEmpty() -> "EMPTY"

                // Sisanya -> Konten
                else -> "CONTENT"
            },
            animationSpec = tween(500), // Durasi animasi fade (0.5 detik)
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
                        contentPadding = PaddingValues(top = 200.dp, bottom = screenHeight / 2),
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
                                    isTranslateLyrics = isTranslateLyrics,
                                    lyricsConfig = lyricsConfig
                                )
                            }
                        }
                    }
                }
            }
        }

        // 1. Gradient ATAS (Fade dari Warna ke Transparan)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(headerHeight + 40.dp) // Total: 94 + 40 = 134.dp
                .heightIn(min = 100.dp, max = 200.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        // KUNCI RAHASIA: Gunakan banyak titik (Stops) untuk bikin kurva halus
                        colorStops = arrayOf(
                            0.0f to topPlayerColor,                    // Atas: Solid
                            0.5f to topPlayerColor,
                            0.6f to topPlayerColor.copy(alpha = 0.8f), // Mulai pudar dikit
                            0.8f to topPlayerColor.copy(alpha = 0.5f), // Pudar makin banyak (efek blur/glow terjadi disini)
                            1.0f to topPlayerColor.copy(alpha = 0f)    // Bawah: Transparan Total
                        )
                    )
                )
        )

        // 2. Gradient BAWAH (Fade dari Transparan ke Warna)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.65f)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            // Titik 0.0 (Paling Atas Box):
                            // Pakai warna Bottom tapi Alpha 0 (Invisible).
                            // Ini kuncinya biar gak belang/abu-abu.
                            0.0f to bottomPlayerColor.copy(alpha = 0f),

                            // Titik 0.3 (30% ke bawah):
                            // Masih sangat tipis (10% opacity).
                            // Ini bikin efek "glow" halus di batas atas.
                            0.4f to bottomPlayerColor.copy(alpha = 0.1f),

                            // Titik 0.6 (60% ke bawah):
                            // Mulai tebal (60% opacity). Teks mulai susah dibaca disini.
                            0.6f to bottomPlayerColor.copy(alpha = 0.5f),

                            // Titik 0.9 - 1.0 (Bawah):
                            // Solid total. Menutupi panel kontrol biar gak tabrakan.
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
        )
    }
}

@Composable
fun LyricsSkeletonLoader() {
    // 1. Setup Animasi Shimmer (Kedip-kedip)
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

    // 2. Gunakan LazyColumn agar bisa scroll (atau memenuhi layar) tanpa error
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 120.dp, bottom = 150.dp, start = 24.dp, end = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        userScrollEnabled = false
    ) {
        // 3. Ulangi sebanyak 25 kali (cukup untuk memenuhi layar HP panjang sekalipun)
        items(25) { index ->
            // Variasi panjang lebar (Pendek, Panjang, Sedang) biar lebih natural
            val widthFraction = when (index % 3) {
                0 -> 0.6f // Pendek
                1 -> 0.8f // Panjang
                else -> 0.4f // Sangat Pendek
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth(widthFraction)
                    .height(20.dp) // Tinggi per baris
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White.copy(alpha = alpha)) // Warna skeleton
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
    isTranslateLyrics: Boolean,
    lyricsConfig: LyricsConfig
) {
    // Spesifikasi animasi (durasi 200ms) untuk semua transisi
    val animationSpec = tween<Float>(durationMillis = 200, delayMillis = 80)
    val colorAnimationSpec = tween<Color>(durationMillis = 200, delayMillis = 80)

    // 1. Tentukan Font Family berdasarkan Config
    val fontFamily = when (lyricsConfig.fontFamily) {
        LyricsFontFamily.MONTSERRAT -> AppFont.MontserratRegular
        LyricsFontFamily.MONTSERRAT_BOLD -> AppFont.MontserratBold
        LyricsFontFamily.MONTSERRAT_BLACK -> AppFont.MontserratBlack
        LyricsFontFamily.POPPINS -> AppFont.Poppins
        LyricsFontFamily.ROBOTO -> AppFont.RobotoRegular
        LyricsFontFamily.COOLVETICA -> AppFont.Coolvetica
        LyricsFontFamily.COOLVETICA_CONDENSED -> AppFont.CoolveticaCondensed
        LyricsFontFamily.COOLVETICA_COMPRESSED -> AppFont.CoolveticaCompressed
        LyricsFontFamily.HELVETICA -> AppFont.Helvetica
        LyricsFontFamily.HELVETICA_ROUNDED -> AppFont.HelveticaRoundedBold
        LyricsFontFamily.HELVETICA_COMPRESSED -> AppFont.HelveticaCompressed
        LyricsFontFamily.HELVETICA_LIGHT -> AppFont.HelveticaLight
    }

    // 2. Tentukan Alignment berdasarkan Config
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

    // 3. Logika Ukuran Font (Scaling)
    // Base size dari config (default 21.dp -> konversi ke sp)
    // Old logic: base * scale. New logic: base + (scale * 2)
    val baseFontSize = lyricsConfig.fontSize
    val shouldScale = lyricsConfig.autoScaleIfNoTranslation && (!isTranslateLyrics || line.translatedText == null)
    val addedSize = if (shouldScale) (lyricsConfig.scaleFactor * 2) else 0 // +2, +4, +6
    
    val finalFontSize = (baseFontSize + addedSize).sp

    val fontSize by animateFloatAsState(
        targetValue = finalFontSize.value,
        animationSpec = animationSpec,
        label = "fontSizeAnimation"
    )

    // 4. Animasikan Warna Font Utama
    val fontColor by animateColorAsState(
        targetValue = if (isActive) Color.White else Color.White.copy(alpha = 0.5f),
        animationSpec = colorAnimationSpec,
        label = "fontColorAnimation"
    )

    // 5. Animasikan Warna Font Terjemahan
    val translatedColor by animateColorAsState(
        targetValue = if (isActive) Color.White.copy(alpha = 0.9f) else Color.White.copy(alpha = 0.5f),
        animationSpec = colorAnimationSpec,
        label = "translatedColorAnimation"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalAlignment = horizontalAlignment // Align column content
    ) {
        Text(
            text = line.originalText,
            color = fontColor, // Gunakan warna yang dianimasikan
            fontSize = fontSize.sp, // Gunakan ukuran yang dianimasikan
            fontFamily = fontFamily,
            fontWeight = FontWeight.Bold,
            textAlign = textAlign,
            modifier = Modifier.fillMaxWidth() // Ensure text takes width for alignment
        )

        // Animasi Masuk/Keluar untuk Lirik Terjemahan
        AnimatedVisibility(
            visible = line.translatedText != null && isTranslateLyrics,
            enter = slideInVertically(
                initialOffsetY = { -it }, // Mulai dari atas (negatif height)
                animationSpec = tween(durationMillis = 300)
            ) + fadeIn(animationSpec = tween(durationMillis = 300)) + expandVertically(
                expandFrom = Alignment.Top,
                animationSpec = tween(durationMillis = 300)
            ),
            exit = slideOutVertically(
                targetOffsetY = { -it }, // Keluar ke atas (negatif height)
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
                    color = translatedColor, // Gunakan warna yang dianimasikan
                    fontSize = 15.5.sp, // Ukuran terjemahan bisa tetap atau scaled proporsional? User ga bilang. Keep static.
                    fontFamily = fontFamily, // Pakai font yang sama? Biasanya ya.
                    fontWeight = FontWeight.Normal,
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
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val nestedScrollConnection = remember {
        object : NestedScrollConnection {}
    }

    // State lokal untuk menghandle saat user menggeser slider
    var isUserSeeking by remember { mutableStateOf(false) }
    var userSeekPosition by remember { mutableFloatStateOf(0f) }

    // Menentukan posisi slider
    val sliderValue = if (isUserSeeking) {
        userSeekPosition
    } else {
        if (totalDuration > 0) (currentPosition.toFloat() / totalDuration.toFloat()) else 0f
    }

    // Hitung waktu yang ditampilkan (Realtime saat digeser)
    val displayCurrentTime = if (isUserSeeking) {
        (userSeekPosition * totalDuration).toLong()
    } else {
        currentPosition
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(140.dp)
//            .background(bottomPlayerColor)
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
                            velocity = 70.dp,             // kecepatan scroll
                            initialDelayMillis = 2000,  // delay sebelum scroll pertama
                            repeatDelayMillis = 5000,   // delay di ujung sebelum loop
                            iterations = Int.MAX_VALUE, // scroll terus-menerus
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
                    onSeek(userSeekPosition) // Kirim posisi akhir ke ViewModel
                    isUserSeeking = false
                },
                valueRange = 0f..1f,
                modifier = Modifier.padding(horizontal = 8.dp),
                // 1. Sembunyikan thumb & track default M3 dengan membuatnya transparan
                colors = SliderDefaults.colors(
                    thumbColor = Color.Transparent,
                    activeTrackColor = Color.Transparent,
                    inactiveTrackColor = Color.Transparent
                ),

                // 2. Gambar THUMB kita sendiri yang bulat sempurna
                thumb = {
                    Box(
                        modifier = Modifier
                            .offset(y = 1.dp)
                            .size(14.dp) // Atur ukuran bulatan di sini
                            .background(color = Color.White, shape = CircleShape) // CircleShape membuatnya bulat
                    )
                },

                // 3. Gambar TRACK kita sendiri yang lebih tipis
                track = { sliderPositions ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                    ) {
                        // Garis sisa (inactive)
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(color = Color.White.copy(0.4f), shape = RoundedCornerShape(2.dp))
                        )
                        // Garis aktif
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
                text = formatDuration(displayCurrentTime),
                color = Color.White,
                fontFamily = AppFont.Coolvetica,
                fontWeight = FontWeight.Normal,
                fontSize = 14.sp,
            )
            Text(
                text = formatDuration(totalDuration),
                color = Color.White,
                fontFamily = AppFont.Coolvetica,
                fontWeight = FontWeight.Normal,
                fontSize = 14.sp,
            )
        }
        Spacer(modifier = Modifier.height(25.dp).fillMaxWidth())
    }
}