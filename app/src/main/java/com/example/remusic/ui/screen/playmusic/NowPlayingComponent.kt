package com.example.remusic.ui.screen.playmusic

import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.with
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.Player
import coil.compose.AsyncImage
import com.example.remusic.R
import com.example.remusic.data.model.SongWithArtist
import com.example.remusic.ui.theme.AppFont
import com.example.remusic.utils.capitalizeWords
import com.example.remusic.utils.formatDuration
import com.example.remusic.viewmodel.playmusic.AnimationDirection
import com.example.remusic.viewmodel.playmusic.UploaderUiState
import com.example.remusic.viewmodel.playmusic.UploaderViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun NowPlaying(
    songWithArtist: SongWithArtist?,
    isPlaying: Boolean,
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
    onSeek: (Float) -> Unit
) {
    val context = LocalContext.current
    val nestedScrollConnection = remember {
        object : NestedScrollConnection {}
    }

    // State lokal untuk slider
    var isUserSeeking by remember { mutableStateOf(false) }
    var userSeekPosition by remember { mutableFloatStateOf(0f) }

    val sliderValue = if (isUserSeeking) {
        userSeekPosition
    } else {
        if (totalDuration > 0) (currentPosition.toFloat() / totalDuration.toFloat()) else 0f
    }

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

            // ==========================================
            // COLUMN 1: PLAYER CONTROL (FULL SCREEN 100%)
            // ==========================================
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(screenHeight) // Kunci: Set tinggi sama dengan tinggi layar
                    .padding(top = screenHeight * 0.1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                // UBAH DARI SpaceBetween KE Top AGAR SEMUA KE ATAS
                verticalArrangement = Arrangement.Top
            ) {

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
                                    slideInHorizontally { it } + fadeIn() with slideOutHorizontally { -it } + fadeOut()
                                }
                                AnimationDirection.BACKWARD -> {
                                    slideInHorizontally { -it } + fadeIn() with slideOutHorizontally { it } + fadeOut()
                                }
                                else -> {
                                    fadeIn() with fadeOut()
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

                    // TWS Badge (Soundcore) - Ditempel di bawah art
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth(0.4f)
                                .height(30.dp)
                                .align(Alignment.TopEnd) // Ubah alignment jika perlu
                                .padding(end = 16.dp, top = 10.dp),
                            shape = RoundedCornerShape(100.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.End
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Headset,
                                    contentDescription = "TWS Icon",
                                    tint = Color.Green,
                                    modifier = Modifier.size(20.dp),
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "Soundcore R50i",
                                    color = Color.Green,
                                    fontFamily = AppFont.RobotoRegular,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }

                // Jarak antara Album Art dan Judul
                Spacer(modifier = Modifier.height(30.dp))

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
                                fontFamily = AppFont.RobotoBold,
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
                        Row(
                            modifier = Modifier
                                .weight(0.3f)
                                .padding(start = 5.dp, end = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Icon(Icons.Filled.FavoriteBorder, "Fav", Modifier.size(25.dp), Color.White)
                            Icon(Icons.Filled.Share, "Share", Modifier.size(25.dp), Color.White)
                            Icon(Icons.Outlined.Timer, "Timer", Modifier.size(25.dp), Color.White)
                        }
                    }
                }

                // Jarak antara Judul dan Slider/Kontrol
                Spacer(modifier = Modifier.height(30.dp))

                // --- Bagian Bawah (Slider & Controls) ---
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Slider
                    Box(modifier = Modifier.fillMaxWidth().height(10.dp)) {
                        Slider(
                            value = sliderValue,
                            onValueChange = { newValue ->
                                isUserSeeking = true
                                userSeekPosition = newValue
                            },
                            onValueChangeFinished = {
                                isUserSeeking = false
                                onSeek(userSeekPosition)
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
                                            .background(
                                                color = Color.White.copy(0.4f),
                                                shape = RoundedCornerShape(2.dp)
                                            )
                                    )
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(sliderPositions.value)
                                            .fillMaxHeight()
                                            .background(
                                                color = Color.White,
                                                shape = RoundedCornerShape(2.dp)
                                            )
                                    )
                                }
                            }
                        )
                    }

                    // Waktu (Duration)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(formatDuration(currentPosition), color = Color.White, fontSize = 14.sp)
                        Text(formatDuration(totalDuration), color = Color.White, fontSize = 14.sp)
                    }

                    Spacer(modifier = Modifier.height(15.dp))

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
                                    onShuffleClick()
                                    val msg = if (isShuffleEnabled) "Shuffle Off" else "Shuffle On"
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                },
                            tint = if (isShuffleEnabled) Color.Green else Color.White
                        )

                        Spacer(modifier = Modifier.weight(1f))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.SkipPrevious,
                                contentDescription = "Prev",
                                modifier = Modifier.size(50.dp).clickable { onPrevClick() },
                                tint = Color.White
                            )
                            Spacer(modifier = Modifier.width(22.dp))
                            Icon(
                                imageVector = if (isPlaying) Icons.Filled.PauseCircle else Icons.Filled.PlayCircle,
                                contentDescription = "Play/Pause",
                                modifier = Modifier.size(80.dp).clickable { onPlayPauseClick() },
                                tint = Color.White
                            )
                            Spacer(modifier = Modifier.width(22.dp))
                            Icon(
                                imageVector = Icons.Filled.SkipNext,
                                contentDescription = "Next",
                                modifier = Modifier.size(50.dp).clickable { onNextClick() },
                                tint = Color.White
                            )
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        val repeatIcon = when (repeatMode) {
                            Player.REPEAT_MODE_ONE -> Icons.Filled.RepeatOne
                            Player.REPEAT_MODE_ALL -> Icons.Outlined.Repeat
                            else -> Icons.Outlined.Repeat
                        }
                        val iconTint = if (repeatMode == Player.REPEAT_MODE_OFF) Color.Gray else Color.White

                        IconButton(onClick = {
                            onRepeatClick()
                            val nextMode = when (repeatMode) {
                                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ONE
                                Player.REPEAT_MODE_ONE -> Player.REPEAT_MODE_ALL
                                else -> Player.REPEAT_MODE_OFF
                            }
                            val msg = when (nextMode) {
                                Player.REPEAT_MODE_OFF -> "Mengulang dimatikan"
                                Player.REPEAT_MODE_ONE -> "Mengulang lagu ini"
                                Player.REPEAT_MODE_ALL -> "Mengulang semua"
                                else -> ""
                            }
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        }) {
                            Icon(repeatIcon, "Repeat", tint = iconTint, modifier = Modifier.size(30.dp))
                        }
                    }

                    // Spacer kecil di paling bawah column 1
                    Spacer(modifier = Modifier.height(20.dp))
                }
            }


            // ==========================================
            // COLUMN 2: INFO (UPLOADER & ARTIST)
            // ==========================================
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp) // Beri padding kiri kanan
            ) {
                // Spacer agar ada jarak sedikit saat user mulai scroll ke bawah
                Spacer(modifier = Modifier.height(20.dp))

                if (songWithArtist?.song?.uploaderUserId != null) {
                    UploaderBox( // Pastikan nama composable ini benar sesuai kode kamu (UploaderBox?)
                        uploaderId = songWithArtist.song.uploaderUserId
                    )
                    // Jika "UploaderUiState" di kode asli adalah "UploaderBox", ganti saja namanya
                }

                Spacer(modifier = Modifier.height(20.dp))

                if (songWithArtist?.artist != null) {
                    ArtistBox(
                        name = songWithArtist.artist.name ?: "Unknown Artist",
                        description = songWithArtist.artist.description ?: "No description available",
                        photoUrl = songWithArtist.artist.photoUrl ?: "",
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Spacer tambahan di paling bawah agar tidak mentok layar saat discroll habis
                Spacer(modifier = Modifier.height(100.dp))
            }
        }
    }
}


@Composable
fun ArtistBox(
    name: String,
    description: String,
    photoUrl: String,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }
    val maxLinesForDescription = if (isExpanded) Int.MAX_VALUE else 4

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF282828))
    ) {
        // 1. Background gambar artist (tanpa gradient)
        AsyncImage(
            model = photoUrl,
            contentDescription = "Artist Background",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .height(250.dp)
                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
        )

        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            // "Tentang artis" label di atas gambar dengan shadow
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(250.dp)
                    .padding(16.dp),
                contentAlignment = Alignment.TopStart
            ) {
                Text(
                    text = "Tentang artis",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontFamily = AppFont.RobotoBold,
                    // Menambahkan shadow pada teks
                    style = TextStyle(
                        shadow = Shadow(
                            color = Color.Black.copy(alpha = 0.5f),
                            blurRadius = 8f
                        )
                    )
                )
            }

            // Konten di bawah gambar
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF282828))
                    .padding(16.dp)
            ) {
                // Baris nama artis & centang
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = name,
                        color = Color.White,
                        fontSize = 24.sp,
                        fontFamily = AppFont.RobotoBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Filled.Verified,
                        contentDescription = "Verified Artist",
                        tint = Color(0xFF01EF56),
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Deskripsi artist
                Text(
                    text = description,
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 14.sp,
                    fontFamily = AppFont.RobotoRegular,
                    maxLines = maxLinesForDescription,
                    overflow = TextOverflow.Ellipsis
                )

                // Tombol "lihat semua" / "tutup"
                if (description.length > 200) {
                    Text(
                        text = if (isExpanded) "tutup" else "lihat semua",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 14.sp,
                        fontFamily = AppFont.RobotoBold,
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .clickable { isExpanded = !isExpanded }
                    )
                }
            }
        }
    }
}