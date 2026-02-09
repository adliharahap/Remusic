@file:Suppress("COMPOSE_APPLIER_CALL_MISMATCH")

package com.example.remusic.ui.screen.playmusic

import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.MarqueeAnimationMode
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import androidx.media3.common.Player
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import coil.compose.AsyncImage
import com.example.remusic.R
import com.example.remusic.data.model.SongWithArtist
import com.example.remusic.data.model.User
import com.example.remusic.data.model.displayArtistName
import com.example.remusic.ui.components.MusicSlider
import com.example.remusic.ui.theme.AppFont
import com.example.remusic.utils.formatDuration
import com.example.remusic.viewmodel.playmusic.AnimationDirection

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun NowPlaying(
    songWithArtist: SongWithArtist?,
    isPlaying: Boolean,
    isBuffering: Boolean,
    isLoadingData: Boolean,
    debugStatus: String = "Preparing...",
    errorMsg: String? = null,
    currentPosition: Long,
    totalDuration: Long,
    isShuffleEnabled: Boolean,
    repeatMode: Int,
    scrollState: ScrollState = rememberScrollState(),
    posterAnimation: Int,
    posterAnimationDirection: AnimationDirection = AnimationDirection.NONE,
    onPlayPauseClick: () -> Unit,
    onNextClick: () -> Unit,
    onPrevClick: () -> Unit,
    onShuffleClick: () -> Unit,
    onRepeatClick: () -> Unit,
    onTimerClick: () -> Unit,
    isLiked: Boolean,
    onLikeClick: () -> Unit,
    onSeek: (Float) -> Unit,
    uploader: User? = null,
    isSearchContext: Boolean = false
) {
    val context = LocalContext.current
    val nestedScrollConnection = remember {
        object : NestedScrollConnection {}
    }

    // Playback Button Animations
    val playPauseInteractionSource = remember { MutableInteractionSource() }
    val nextInteractionSource = remember { MutableInteractionSource() }
    val prevInteractionSource = remember { MutableInteractionSource() }

    val playPauseScale = remember { Animatable(1f) }
    val nextScale = remember { Animatable(1f) }
    val prevScale = remember { Animatable(1f) }

    val playPauseScope = rememberCoroutineScope()
    val nextScope = rememberCoroutineScope()
    val prevScope = rememberCoroutineScope()

    // State lokal untuk slider
    var isUserSeeking by remember { mutableStateOf(false) }
    var userSeekPosition by remember { mutableFloatStateOf(0f) }

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

    // Slider Box
    // Jika buffering atau loading data, tampilkan skeleton loading
    val isBusy = isBuffering || isLoadingData

    // 1. Gunakan BoxWithConstraints untuk mendapatkan tinggi layar (viewport)
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize()
    ) {
        // Simpan tinggi layar ke variabel
        val screenHeight = maxHeight

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState) // Scrollable container utama
                .nestedScroll(nestedScrollConnection),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // 1. Tentukan State apakah Canvas Aktif atau Tidak
            val hasCanvas = !songWithArtist?.song?.canvasUrl.isNullOrBlank()

            AnimatedContent(
                targetState = hasCanvas,
                label = "PlayerLayoutTransition",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(screenHeight), // Menjaga tinggi tetap sesuai layar
                transitionSpec = {
                    if (targetState) {
                        // TRANSISI KE MODE CANVAS (Video Ada)
                        (slideInVertically { height -> height } + fadeIn()) togetherWith
                                (slideOutVertically { height -> height / 2 } + fadeOut())
                    } else {
                        // TRANSISI KEMBALI KE MODE STANDARD (Video Tidak Ada)
                        (slideInVertically { height -> height / 2 } + fadeIn()) togetherWith
                                (slideOutVertically { height -> height } + fadeOut())
                    }
                }
            ) { isCanvasMode ->

                // ==========================================
                // COLUMN 1: PLAYER CONTROL (FULL SCREEN 100%)
                // ==========================================
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(screenHeight)
                        .padding(bottom = if (isCanvasMode) 0.dp else 60.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    // UBAH DARI SpaceBetween KE Top AGAR SEMUA KE ATAS
                    verticalArrangement = Arrangement.Bottom
                ) {
                    if (isCanvasMode) {
                        val canvasUrl = songWithArtist?.song?.canvasUrl
                        Box(
                            modifier = Modifier.fillMaxSize()
                        ) {
                            CanvasVideoPlayer(
                                videoUrl = canvasUrl.toString(),
                                coverUrl = songWithArtist?.song?.coverUrl.toString(),
                                modifier = Modifier.fillMaxSize()
                            )

                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(0.3f))
                            )

                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        brush = Brush.verticalGradient(
                                            colorStops = arrayOf(
                                                // 0% - 40% (Atas): Transparan Total (Video Jernih)
                                                0.0f to Color.Transparent,
                                                0.4f to Color.Transparent,

                                                // 70%: Mulai menggelap pelan-pelan
                                                0.7f to Color.Black.copy(alpha = 0.5f),

                                                // 100% (Bawah): Hitam Pekat (Agar kontrol & teks jelas)
                                                1.0f to Color.Black
                                            )
                                        )
                                    )
                            )

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight(0.32f)
                                    .align(Alignment.BottomCenter),
                                verticalArrangement = Arrangement.Top,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Row(
                                    modifier = Modifier
                                        .padding(horizontal = 16.dp, vertical = 16.dp)
                                        .fillMaxWidth()
                                        .heightIn(max = 50.dp),
                                ) {
                                    AsyncImage(
                                        model = songWithArtist?.song?.coverUrl,
                                        contentDescription = "Cover Album",
                                        modifier = Modifier.padding(end = 10.dp)
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
                                    Column(
                                        modifier = Modifier.weight(0.7f).fillMaxHeight(),
                                        verticalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = songWithArtist?.song?.title ?: "Unknown Title",
                                            color = Color.White,
                                            fontFamily = AppFont.Poppins,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 19.sp,
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
                                        )
                                        Text(
                                            text = songWithArtist?.displayArtistName ?: "Unknown Artist",
                                            color = Color(0xCCFFFFFF),
                                            fontFamily = AppFont.Helvetica,
                                            fontWeight = FontWeight.Normal,
                                            fontSize = 15.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                    Row(
                                        modifier = Modifier
                                            .weight(0.3f).fillMaxHeight().padding(start = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Icon(
                                            if (isLiked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                            "Fav",
                                            Modifier.size(28.dp).clickable { onLikeClick() },
                                            if (isLiked) Color.Red else Color.White
                                        )
                                        Icon(
                                            Icons.Outlined.Timer,
                                            "Timer",
                                            Modifier.size(28.dp).clickable { onTimerClick() },
                                            Color.White
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.fillMaxWidth().height(10.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(10.dp)
                                ) {
                                    MusicSlider(
                                        value = sliderValue,
                                        onValueChange = { newValue ->
                                            isUserSeeking = true // User sedang memegang slider
                                            userSeekPosition = newValue
                                        },
                                        onValueChangeFinished = {
                                            onSeek(userSeekPosition)
                                            isUserSeeking = false // User melepas slider
                                        },
                                        modifier = Modifier.padding(horizontal = 8.dp),
                                        // Kamu bisa override warna atau ukuran jika mau, atau pakai default
                                        activeColor = Color.White,
                                        inactiveColor = Color.White.copy(0.4f)
                                    )
                                }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
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
                                Row(
                                    modifier = Modifier.fillMaxWidth().height(20.dp).padding(horizontal = 16.dp, vertical = 0.dp),
                                    horizontalArrangement = Arrangement.Start,
                                    verticalAlignment = Alignment.Bottom
                                ) {
                                    val statusText = when {
                                        isLoadingData -> "Loading Get Data..."
                                        isBuffering -> "Buffering..."
                                        else -> debugStatus
                                    }

                                    Text(
                                        text = statusText,
                                        color = if (errorMsg != null) Color.Red else Color.White.copy(alpha = 0.8f),
                                        fontFamily = AppFont.Helvetica,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                    )
                                }
                                // Tombol Kontrol (Play/Pause/Shuffle)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(
                                            start = 16.dp,
                                            end = 16.dp,
                                            bottom = 20.dp
                                        ),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Shuffle,
                                        contentDescription = "Shuffle",
                                        modifier = Modifier
                                            .size(30.dp)
                                            .clickable {
                                                if (isSearchContext) {
                                                    Toast.makeText(context, "Smart Queue / Smart Shuffle Aktif", Toast.LENGTH_SHORT).show()
                                                } else {
                                                    onShuffleClick()
                                                    val msg =
                                                        if (isShuffleEnabled) "Shuffle Off" else "Shuffle On"
                                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT)
                                                        .show()
                                                }
                                            },
                                        tint = if (isSearchContext) Color.Green else (if (isShuffleEnabled) Color.Green else Color.White)
                                    )

                                    Spacer(modifier = Modifier.weight(1f))

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            painter = painterResource(R.drawable.previous_svgrepo_com),
                                            contentDescription = "Prev",
                                            modifier = Modifier
                                                .size(40.dp)
                                                .graphicsLayer(scaleX = prevScale.value, scaleY = prevScale.value)
                                                .clickable(
                                                    interactionSource = prevInteractionSource,
                                                    indication = null
                                                ) {
                                                    prevScope.launch {
                                                        prevScale.animateTo(1.1f, tween(200))
                                                        prevScale.animateTo(1f, tween(200))
                                                    }
                                                    onPrevClick()
                                                },
                                            tint = Color.White
                                        )
                                        Spacer(modifier = Modifier.width(30.dp))
                                        if (isBusy) {
                                            androidx.compose.material3.CircularProgressIndicator(
                                                modifier = Modifier.size(80.dp).padding(10.dp),
                                                color = Color.White,
                                                strokeWidth = 4.dp
                                            )
                                        } else {
                                            Icon(
                                                imageVector = if (isPlaying) Icons.Filled.PauseCircle else Icons.Filled.PlayCircle,
                                                contentDescription = "Play/Pause",
                                                modifier = Modifier
                                                    .size(80.dp)
                                                    .graphicsLayer(scaleX = playPauseScale.value, scaleY = playPauseScale.value)
                                                    .clickable(
                                                        interactionSource = playPauseInteractionSource,
                                                        indication = null
                                                    ) {
                                                        playPauseScope.launch {
                                                            playPauseScale.animateTo(1.1f, tween(200))
                                                            playPauseScale.animateTo(1f, tween(200))
                                                        }
                                                        onPlayPauseClick()
                                                    },
                                                tint = Color.White
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(30.dp))
                                        Icon(
                                            painter = painterResource(R.drawable.next_svgrepo_com),
                                            contentDescription = "Next",
                                            modifier = Modifier
                                                .size(40.dp)
                                                .graphicsLayer(scaleX = nextScale.value, scaleY = nextScale.value)
                                                .clickable(
                                                    interactionSource = nextInteractionSource,
                                                    indication = null
                                                ) {
                                                    nextScope.launch {
                                                        nextScale.animateTo(1.1f, tween(200))
                                                        nextScale.animateTo(1f, tween(200))
                                                    }
                                                    onNextClick()
                                                },
                                            tint = Color.White
                                        )
                                    }

                                    Spacer(modifier = Modifier.weight(1f))

                                    val repeatIcon = when (repeatMode) {
                                        Player.REPEAT_MODE_ONE -> Icons.Filled.RepeatOne
                                        Player.REPEAT_MODE_ALL -> Icons.Outlined.Repeat
                                        else -> Icons.Outlined.Repeat
                                    }
                                    val iconTint =
                                        if (repeatMode == Player.REPEAT_MODE_OFF) Color.Gray else Color.White

                                    IconButton(onClick = {
                                        onRepeatClick()
                                        // Prediksi pesan berdasarkan context
                                        val msg = if (isSearchContext) {
                                            if (repeatMode == Player.REPEAT_MODE_OFF) "Mengulang lagu ini" else "Mengulang dimatikan"
                                        } else {
                                            val nextMode = when (repeatMode) {
                                                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ONE
                                                Player.REPEAT_MODE_ONE -> Player.REPEAT_MODE_ALL
                                                else -> Player.REPEAT_MODE_OFF
                                            }
                                            when (nextMode) {
                                                Player.REPEAT_MODE_OFF -> "Mengulang dimatikan"
                                                Player.REPEAT_MODE_ONE -> "Mengulang lagu ini"
                                                Player.REPEAT_MODE_ALL -> "Mengulang semua"
                                                else -> ""
                                            }
                                        }
                                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                    }) {
                                        Icon(
                                            repeatIcon,
                                            "Repeat",
                                            tint = iconTint,
                                            modifier = Modifier.size(30.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }else {
                        // --- Bagian Atas (Album Art) ---
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Padding top di sini (misal 50dp + margin status bar)
                            Spacer(modifier = Modifier.height(70.dp))

                            AnimatedContent(
                                targetState = posterAnimation,
                                label = "Animated Album Cover",
                                transitionSpec = {
                                    when (posterAnimationDirection) {
                                        AnimationDirection.FORWARD -> {
                                            (slideInHorizontally { it } + fadeIn()).togetherWith(
                                                slideOutHorizontally { -it } + fadeOut())
                                        }

                                        AnimationDirection.BACKWARD -> {
                                            (slideInHorizontally { -it } + fadeIn()).togetherWith(
                                                slideOutHorizontally { it } + fadeOut())
                                        }

                                        else -> {
                                            fadeIn().togetherWith(fadeOut())
                                        }
                                    }
                                }
                            ) { targetvalue ->
                                val isRunning by remember { derivedStateOf { transition.isRunning } }
                                AsyncImage(
                                    model = songWithArtist?.song?.coverUrl,
                                    contentDescription = "Cover Album $targetvalue",
                                    modifier = Modifier
                                        .fillMaxWidth(0.9f)
                                        .aspectRatio(1f)
                                        .shadow(
                                            elevation = if (isRunning) 0.dp else 6.dp,
                                            shape = RoundedCornerShape(16.dp),
                                            clip = false
                                        )
                                        .clip(RoundedCornerShape(10.dp)),
                                    contentScale = ContentScale.Crop,
                                    placeholder = painterResource(id = R.drawable.img_placeholder),
                                    error = painterResource(id = R.drawable.img_placeholder)
                                )
                            }
                            // Jarak antara Album Art dan Judul
                            Spacer(modifier = Modifier.height(60.dp))
                        }

                        // --- Bagian Tengah (Judul & Info Lagu) ---
                        // HAPUS weight(1f) agar tidak mendorong ke bawah
                        Column(
                            modifier = Modifier
                                .fillMaxWidth(),
                            verticalArrangement = Arrangement.Center
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(0.7f)) {
                                    Text(
                                        text = songWithArtist?.song?.title ?: "Unknown Title",
                                        color = Color.White,
                                        fontFamily = AppFont.HelveticaRoundedBold,
                                        fontSize = 20.sp,
                                        maxLines = 1,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .basicMarquee(
                                                animationMode = MarqueeAnimationMode.Immediately,
                                                velocity = 70.dp,
                                                iterations = Int.MAX_VALUE,
                                            )
                                            .padding(start = 16.dp, bottom = 5.dp)
                                    )
                                    Text(
                                        text = songWithArtist?.displayArtistName ?: "Unknown Artist",
                                        color = Color(0xCCFFFFFF),
                                        fontFamily = AppFont.Helvetica,
                                        fontSize = 15.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier
                                            .padding(start = 16.dp)
                                            .fillMaxWidth()
                                    )
                                }
                                Row(
                                    modifier = Modifier
                                        .weight(0.3f)
                                        .padding(start = 5.dp, end = 16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Icon(
                                        if (isLiked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                        "Fav",
                                        Modifier.size(25.dp).clickable { onLikeClick() },
                                        if (isLiked) Color.Red else Color.White
                                    )
                                    Icon(
                                        Icons.Filled.Share,
                                        "Share",
                                        Modifier.size(25.dp),
                                        Color.White
                                    )
                                    Icon(
                                        Icons.Outlined.Timer,
                                        "Timer",
                                        Modifier.size(25.dp).clickable { onTimerClick() },
                                        Color.White
                                    )
                                }
                            }
                        }

                        // Jarak antara Judul dan Slider/Kontrol
                        Spacer(modifier = Modifier.height(30.dp))

                        // --- Bagian Bawah (Slider & Controls) ---
                        Column(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                                Box(modifier = Modifier
                                    .fillMaxWidth()
                                    .height(10.dp)) {
                                    MusicSlider(
                                        value = sliderValue,
                                        onValueChange = { newValue ->
                                            isUserSeeking = true
                                            userSeekPosition = newValue
                                        },
                                        onValueChangeFinished = {
                                            onSeek(userSeekPosition)
                                            isUserSeeking = false
                                        },
                                        modifier = Modifier.padding(horizontal = 8.dp),
                                        activeColor = Color.White,
                                        inactiveColor = Color.White.copy(0.4f)
                                    )
                                }

                            // Waktu (Duration)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = formatDuration(displayCurrentTime),
                                    color = Color.White,
                                    fontFamily = AppFont.Helvetica,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = formatDuration(totalDuration),
                                    color = Color.White,
                                    fontFamily = AppFont.Helvetica,
                                    fontSize = 14.sp
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth().height(20.dp).padding(horizontal = 16.dp, vertical = 0.dp),
                                horizontalArrangement = Arrangement.Start,
                                verticalAlignment = Alignment.Bottom
                            ) {
                                val statusText = when {
                                    isLoadingData -> "Loading Get Data..."
                                    isBuffering -> "Buffering..."
                                    else -> debugStatus
                                }

                                Text(
                                    text = statusText,
                                    color = if (errorMsg != null) Color.Red else Color.White.copy(alpha = 0.8f),
                                    fontFamily = AppFont.Helvetica,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                )
                            }
                            // Tombol Kontrol (Play/Pause/Shuffle)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Shuffle,
                                    contentDescription = "Shuffle",
                                    modifier = Modifier
                                        .size(30.dp)
                                        .clickable {
                                            if (isSearchContext) {
                                                Toast.makeText(context, "Smart Queue / Smart Shuffle Aktif", Toast.LENGTH_SHORT).show()
                                            } else {
                                                onShuffleClick()
                                                val msg =
                                                    if (isShuffleEnabled) "Shuffle Off" else "Shuffle On"
                                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                    tint = if (isSearchContext) Color.Green else (if (isShuffleEnabled) Color.Green else Color.White)
                                )

                                Spacer(modifier = Modifier.weight(1f))

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        painter = painterResource(R.drawable.previous_svgrepo_com),
                                        contentDescription = "Prev",
                                        modifier = Modifier
                                            .size(40.dp)
                                            .graphicsLayer(scaleX = prevScale.value, scaleY = prevScale.value)
                                            .clickable(
                                                interactionSource = prevInteractionSource,
                                                indication = null
                                            ) {
                                                prevScope.launch {
                                                    prevScale.animateTo(1.15f, tween(100))
                                                    prevScale.animateTo(1f, tween(100))
                                                }
                                                onPrevClick()
                                            },
                                        tint = Color.White
                                    )
                                    Spacer(modifier = Modifier.width(30.dp))
                                    if (isBusy) {
                                        androidx.compose.material3.CircularProgressIndicator(
                                            modifier = Modifier.size(80.dp).padding(10.dp),
                                            color = Color.White,
                                            strokeWidth = 4.dp
                                        )
                                    } else {
                                        Icon(
                                            imageVector = if (isPlaying) Icons.Filled.PauseCircle else Icons.Filled.PlayCircle,
                                            contentDescription = "Play/Pause",
                                            modifier = Modifier
                                                .size(80.dp)
                                                .graphicsLayer(scaleX = playPauseScale.value, scaleY = playPauseScale.value)
                                                .clickable(
                                                    interactionSource = playPauseInteractionSource,
                                                    indication = null
                                                ) {
                                                    playPauseScope.launch {
                                                        playPauseScale.animateTo(1.2f, tween(100))
                                                        playPauseScale.animateTo(1f, tween(100))
                                                    }
                                                    onPlayPauseClick()
                                                },
                                            tint = Color.White
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(30.dp))
                                    Icon(
                                        painter = painterResource(R.drawable.next_svgrepo_com),
                                        contentDescription = "Next",
                                        modifier = Modifier
                                            .size(40.dp)
                                            .graphicsLayer(scaleX = nextScale.value, scaleY = nextScale.value)
                                            .clickable(
                                                interactionSource = nextInteractionSource,
                                                indication = null
                                            ) {
                                                nextScope.launch {
                                                    nextScale.animateTo(1.15f, tween(100))
                                                    nextScale.animateTo(1f, tween(100))
                                                }
                                                onNextClick()
                                            },
                                        tint = Color.White
                                    )
                                }

                                Spacer(modifier = Modifier.weight(1f))

                                val repeatIcon = when (repeatMode) {
                                    Player.REPEAT_MODE_ONE -> Icons.Filled.RepeatOne
                                    Player.REPEAT_MODE_ALL -> Icons.Outlined.Repeat
                                    else -> Icons.Outlined.Repeat
                                }
                                val iconTint =
                                    if (repeatMode == Player.REPEAT_MODE_OFF) Color.Gray else Color.White

                                IconButton(onClick = {
                                    onRepeatClick()
                                    val msg = if (isSearchContext) {
                                        if (repeatMode == Player.REPEAT_MODE_OFF) "Mengulang lagu ini" else "Mengulang dimatikan"
                                    } else {
                                        val nextMode = when (repeatMode) {
                                            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ONE
                                            Player.REPEAT_MODE_ONE -> Player.REPEAT_MODE_ALL
                                            else -> Player.REPEAT_MODE_OFF
                                        }
                                        when (nextMode) {
                                            Player.REPEAT_MODE_OFF -> "Mengulang dimatikan"
                                            Player.REPEAT_MODE_ONE -> "Mengulang lagu ini"
                                            Player.REPEAT_MODE_ALL -> "Mengulang semua"
                                            else -> ""
                                        }
                                    }
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                }) {
                                    Icon(
                                        repeatIcon,
                                        "Repeat",
                                        tint = iconTint,
                                        modifier = Modifier.size(30.dp)
                                    )
                                }
                            }

                            // Spacer kecil di paling bawah column 1
                            Spacer(modifier = Modifier.height(20.dp))
                        }
                    }
                }
            }


            // ==========================================
            // COLUMN 2: INFO (UPLOADER & ARTIST)
            // ==========================================
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (hasCanvas) Color.Black else Color.Transparent)
                    .padding(horizontal = 20.dp) // Beri padding kiri kanan
            ) {
                // Spacer agar ada jarak sedikit saat user mulai scroll ke bawah
                Spacer(modifier = Modifier.height(20.dp))

                if (songWithArtist != null) {
                    UploaderBoxAndArtist(
                        artist = songWithArtist.artist,
                        uploader = uploader
                    )
                }

                // Spacer tambahan di paling bawah agar tidak mentok layar saat discroll habis
                Spacer(modifier = Modifier.height(100.dp))
            }
        }
    }
}