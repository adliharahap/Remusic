package com.example.remusic.ui.screen.playmusic

import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.remusic.R
import com.example.remusic.data.model.LyricLine
import com.example.remusic.data.model.SongWithArtist
import com.example.remusic.ui.theme.AppFont
import com.example.remusic.utils.formatDuration
import com.example.remusic.viewmodel.playmusic.LyricsViewModel
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
fun LyricsScreen(
    currentPosition: Long,
    lrcString: String,
    bottomPlayerColor: Color,
    songWithArtist: SongWithArtist?,
    isPlaying: Boolean,
    totalDuration: Long,
    onSeek: (Float) -> Unit,
    onPlayPauseClick: () -> Unit,
) {
    val lyricsViewModel: LyricsViewModel = viewModel()

    // load lirik sekali saat lrcString berubah
    LaunchedEffect(lrcString) {
        lyricsViewModel.loadLyrics(lrcString)
    }

    // setiap currentPosition berubah, update ViewModel lirik
    LaunchedEffect(currentPosition) {
        lyricsViewModel.updatePlaybackPosition(currentPosition)
    }

    val lyrics by lyricsViewModel.lyrics.collectAsState()
    val activeIndex by lyricsViewModel.activeLyricIndex.collectAsState()
    val isTranslateLyrics by lyricsViewModel.isTranslateLyrics.collectAsState()

    val lazyListState = rememberLazyListState()
    val configuration = LocalConfiguration.current

    // Menghitung padding untuk membuat item bisa berada di tengah layar
    val screenHeight = configuration.screenHeightDp.dp

    LaunchedEffect(lazyListState) {
        snapshotFlow { activeIndex }
            .distinctUntilChanged()
            .collect { index ->
                if (index >= 0 && index < lyrics.size) {
                    lazyListState.animateScrollToItem(
                        index = index,
                        scrollOffset = -(screenHeight.value * 0.2f).toInt()
                    )
                }
            }
    }

    // Tinggi panel bawah Anda adalah 130.dp
    val bottomPanelHeight = 130.dp

    Box(modifier = Modifier.fillMaxSize()) {
        // Konten utama = lirik
        LazyColumn(
            state = lazyListState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 200.dp, bottom = screenHeight / 2),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            itemsIndexed(lyrics) { index, line ->

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    LyricLineItem(
                        line = line,
                        isActive = index == activeIndex,
                        isTranslateLyrics = isTranslateLyrics
                    )
                }
            }
        }
        val fadeHeight = 100.dp
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(fadeHeight)
                .align(Alignment.BottomCenter) // Menempel di bawah
                // Offset ke atas setinggi panel bawah agar gradasi MULAI tepat di atas panel
                .offset(y = -bottomPanelHeight)
                .background(
                    Brush.verticalGradient(
                        // Warna sudah benar, dari transparan ke warna dasar layar
                        colors = listOf(Color.Transparent, bottomPlayerColor)
                    )
                )
        )

        // Panel bawah
        LyricsBottomPanel(
            modifier = Modifier.align(Alignment.BottomCenter),
            bottomPlayerColor = bottomPlayerColor,
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
fun LyricLineItem(
    line: LyricLine,
    isActive: Boolean,
    isTranslateLyrics: Boolean
) {
    // Spesifikasi animasi (durasi 200ms) untuk semua transisi
    val animationSpec = tween<Float>(durationMillis = 200)
    val colorAnimationSpec = tween<Color>(durationMillis = 200)

    // 1. Animasikan Ukuran Font
    val fontSize by animateFloatAsState(
        targetValue = if (isActive) 22f else 20f,
        animationSpec = animationSpec,
        label = "fontSizeAnimation"
    )

    // 2. Animasikan Warna Font Utama
    val fontColor by animateColorAsState(
        targetValue = if (isActive) Color.White else Color.White.copy(alpha = 0.5f),
        animationSpec = colorAnimationSpec,
        label = "fontColorAnimation"
    )

    // 3. Animasikan Warna Font Terjemahan
    val translatedColor by animateColorAsState(
        targetValue = if (isActive) Color.White.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.4f),
        animationSpec = colorAnimationSpec,
        label = "translatedColorAnimation"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Text(
            text = line.originalText,
            color = fontColor, // Gunakan warna yang dianimasikan
            fontSize = fontSize.sp, // Gunakan ukuran yang dianimasikan
            fontFamily = if (isActive) AppFont.RobotoBold else AppFont.RobotoRegular,
            textAlign = TextAlign.Left
        )

        if (line.translatedText != null && isTranslateLyrics) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = line.translatedText,
                color = translatedColor, // Gunakan warna yang dianimasikan
                fontSize = 18.sp, // Ukuran terjemahan bisa tetap
                fontFamily = AppFont.RobotoRegular,
                textAlign = TextAlign.Left
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricsBottomPanel(
    modifier: Modifier = Modifier,
    bottomPlayerColor: Color,
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

    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(140.dp)
            .background(bottomPlayerColor)
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
                placeholder = painterResource(R.drawable.ic_music_note),
                error = painterResource(R.drawable.ic_music_note),
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
                        .padding(start = 16.dp, bottom = 5.dp)
                )
                Text(
                    text = songWithArtist?.artist?.name ?: "Unknown Artist",
                    color = Color(0xCCFFFFFF),
                    fontFamily = AppFont.RobotoRegular,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .padding(start = 16.dp)
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
                    isUserSeeking = false
                    onSeek(userSeekPosition) // Kirim posisi akhir ke ViewModel
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
                text = formatDuration(currentPosition),
                color = Color.White,
                fontFamily = AppFont.RobotoRegular,
                fontSize = 14.sp,
            )
            Text(
                text = formatDuration(totalDuration),
                color = Color.White,
                fontFamily = AppFont.RobotoRegular,
                fontSize = 14.sp,
            )
        }
        Spacer(modifier = Modifier.height(25.dp).fillMaxWidth())
    }
}
