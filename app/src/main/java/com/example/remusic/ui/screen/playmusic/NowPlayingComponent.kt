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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.media3.common.Player
import coil.compose.AsyncImage
import com.example.remusic.R
import com.example.remusic.data.model.SongWithArtist
import com.example.remusic.ui.theme.AppFont
import com.example.remusic.utils.formatDuration
import com.example.remusic.viewmodel.playmusic.AnimationDirection

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun NowPlaying(
    songWithArtist: SongWithArtist?,
    isPlaying: Boolean,
    currentPosition: Long,
    totalDuration: Long,
    isShuffleEnabled: Boolean,
    repeatMode: Int,
    posterAnimation: Int,
    posterAnimationDirection: AnimationDirection = AnimationDirection.NONE,
    onPlayPauseClick: () -> Unit,
    onNextClick: () -> Unit,
    onPrevClick: () -> Unit,
    onShuffleClick: () -> Unit,
    onRepeatClick: () -> Unit,
    onSeek: (Float) -> Unit // Menerima Float (0.0f - 1.0f)
) {
    val scrollState = rememberScrollState()
    val context = LocalContext.current
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

    Column (
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .nestedScroll(nestedScrollConnection),
        horizontalAlignment = Alignment.CenterHorizontally
    ){

        Spacer(modifier = Modifier.height(70.dp).fillMaxWidth())
        AnimatedContent(
            targetState = posterAnimation, // Data yang "ditonton" perubahannya
            label = "Animated Album Cover",
            transitionSpec = {
                // ✅ Gunakan 'when' pada state arah yang baru
                when (posterAnimationDirection) {
                    AnimationDirection.FORWARD -> {
                        // Animasi "Next" (maju)
                        slideInHorizontally { it } + fadeIn() with
                                slideOutHorizontally { -it } + fadeOut()
                    }
                    AnimationDirection.BACKWARD -> {
                        // Animasi "Previous" (mundur)
                        slideInHorizontally { -it } + fadeIn() with
                                slideOutHorizontally { it } + fadeOut()
                    }
                    else -> { // AnimationDirection.NONE
                        // Animasi default tanpa geser, hanya fade
                        fadeIn() with fadeOut()
                    }
                }
            }
        ) { targetvalue ->

            val isRunning by remember {
                derivedStateOf { transition.isRunning }
            }
            AsyncImage(
                model = songWithArtist?.song?.coverUrl,
                contentDescription = "Cover Album $targetvalue",
                modifier = Modifier // Modifier Anda yang sebelumnya ditaruh di sini
                    .fillMaxWidth(0.9f)
                    .aspectRatio(1f)
                    .shadow(
                        elevation = if (isRunning) 0.dp else 6.dp,
                        shape = RoundedCornerShape(16.dp),
                        clip = false
                    )
                    .clip(RoundedCornerShape(10.dp)),
                contentScale = ContentScale.Crop,
                error = painterResource(R.drawable.ic_music_note),
            )
        }
        Box(modifier = Modifier.height(90.dp).fillMaxWidth()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.4f)
                    .height(30.dp)
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 5.dp),
                shape = RoundedCornerShape(100.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.Transparent
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
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
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontFamily = AppFont.RobotoRegular,
                        fontSize = 13.sp
                    )
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
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
            Row (
                modifier = Modifier.weight(0.3f).padding(start = 5.dp, end = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ){
                Icon(
                    imageVector = Icons.Filled.FavoriteBorder,
                    contentDescription = "add song to favorite",
                    modifier = Modifier.size(25.dp),
                    tint = Color.White
                )
                Icon(
                    imageVector = Icons.Filled.Share,
                    contentDescription = "Share Song",
                    modifier = Modifier.size(25.dp),
                    tint = Color.White
                )
                Icon(
                    imageVector = Icons.Outlined.Timer,
                    contentDescription = "Sleep Timer",
                    modifier = Modifier.size(25.dp),
                    tint = Color.White
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp).fillMaxWidth())

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

        Spacer(modifier = Modifier.height(15.dp).fillMaxWidth())
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Shuffle di kiri
            Icon(
                imageVector = Icons.Outlined.Shuffle,
                contentDescription = "Shuffle",
                modifier = Modifier
                    .size(30.dp)
                    .clickable {
                    onShuffleClick()

                    val message = if (isShuffleEnabled) {
                        "Shuffle Off"
                    } else {
                        "Shuffle On"
                    }

                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                },
                tint = if (isShuffleEnabled) Color.Green else Color.White
            )

            Spacer(modifier = Modifier.weight(1f)) // <-- flexible space

            // Row tengah: prev, play, next
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.SkipPrevious,
                    contentDescription = "Previous",
                    modifier = Modifier.size(50.dp).clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onPrevClick() },
                    tint = Color.White
                )
                Spacer(modifier = Modifier.width(22.dp))
                Icon(
                    imageVector = if (isPlaying) Icons.Filled.PauseCircle else Icons.Filled.PlayCircle,
                    contentDescription = "Play/Pause",
                    modifier = Modifier
                        .size(80.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            onPlayPauseClick()
                          },
                    tint = Color.White
                )

                Spacer(modifier = Modifier.width(22.dp))
                Icon(
                    imageVector = Icons.Filled.SkipNext,
                    contentDescription = "Next",
                    modifier = Modifier.size(50.dp).clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onNextClick() },
                    tint = Color.White
                )
            }

            Spacer(modifier = Modifier.weight(1f)) // <-- flexible space

            val repeatIcon = when (repeatMode) {
                Player.REPEAT_MODE_ONE -> Icons.Filled.RepeatOne
                Player.REPEAT_MODE_ALL -> Icons.Outlined.Repeat
                else -> Icons.Outlined.Repeat
            }
            val iconTint = if (repeatMode == Player.REPEAT_MODE_OFF) {
                Color.Gray // OFF → abu-abu
            } else {
                Color.White // ONE/ALL → aktif
            }
            IconButton(onClick = {
                onRepeatClick() // ⬅️ ini update repeatMode dulu (UIState berubah)

                // Ambil repeatMode terbaru setelah update
                val newMode = when (repeatMode) {
                    Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ONE
                    Player.REPEAT_MODE_ONE -> Player.REPEAT_MODE_ALL
                    Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_OFF
                    else -> Player.REPEAT_MODE_OFF
                }

                // 🔥 Munculin toast sesuai mode
                val message = when (newMode) {
                    Player.REPEAT_MODE_OFF -> "mengulang dimatikan"
                    Player.REPEAT_MODE_ONE -> "Mengulang lagu ini"
                    Player.REPEAT_MODE_ALL -> "Mengulang semua lagu dalam playlist"
                    else -> ""
                }

                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            }) {
                Icon(
                    imageVector = repeatIcon,
                    contentDescription = "Repeat",
                    tint = iconTint,
                    modifier = Modifier.size(30.dp)
                )
            }

        }
        Spacer(modifier = Modifier.height(100.dp))

        UploaderBox()

        Spacer(modifier = Modifier.height(20.dp))

        // Artist Box (sudah dinamis)
        if (songWithArtist?.artist != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF755D8D))
            ) {
                ArtistBox(
                    name = songWithArtist.artist.name,
                    description = songWithArtist.artist.description,
                    photoUrl = songWithArtist.artist.photoUrl,
                    modifier = Modifier.fillMaxWidth()
                )
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

@Composable
fun UploaderBox(
    name: String = "Adli Rahman Harun Harahap",
    role: String = "Owner",
    profileUrl: String = "https://i.pinimg.com/1200x/6e/16/06/6e1606fca286ddfc951aeb5fe2aae3bd.jpg",
    onSeeAllClick: () -> Unit = {}
) {
    // --- Definisi Warna & Brush ---
    val cardBackgroundColor = Color(0xFF282828)
    val buttonBgStart = Color(0xFF4C4D4C)
    val buttonBgEnd = Color(0xFF2D2F2F)
    val ownerGoldStart = Color(0xFFF7D43A)
    val ownerGoldEnd = Color(0xFFB5880D)
    val subtleTextColor = Color.White.copy(0.7f)

    // Brush untuk gradasi emas, akan kita pakai di badge DAN teks
    val ownerGoldBrush = remember {
        Brush.verticalGradient(colors = listOf(ownerGoldStart, ownerGoldEnd))
    }

    Column(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .fillMaxWidth()
            .background(cardBackgroundColor, shape = RoundedCornerShape(16.dp))
            .padding(20.dp),
    ) {
        // --- Header Teks "Diupload oleh" ---
        Text(
            text = "Diupload oleh",
            color = subtleTextColor,
            fontSize = 14.sp,
             fontFamily = AppFont.RobotoRegular,
            modifier = Modifier.align(Alignment.Start)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // --- Row (Foto, Nama, Role) ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(64.dp)) {
                AsyncImage(
                    model = profileUrl,
                    contentDescription = "Profile Picture",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(CircleShape)
                )

                if (role.equals("Owner", ignoreCase = true)) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(24.dp)
                            .background(brush = ownerGoldBrush, shape = CircleShape)
                            .padding(4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.WorkspacePremium,
                            contentDescription = "Owner Badge",
                            tint = Color.White,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column {
                Text(
                    text = name,
                    color = Color.White,
                    fontSize = 18.sp,
                     fontFamily = AppFont.RobotoBold,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(2.dp))

                // --- STYLE "OWNER" YANG LEBIH MEWAH ---
                if (role.equals("Owner", ignoreCase = true)) {
                    Text(
                        text = role,
                        fontSize = 14.sp,
                         fontFamily = AppFont.RobotoMedium,
                        // Terapkan gradasi emas langsung ke teks & beri shadow
                        style = TextStyle(
                            brush = ownerGoldBrush,
                            shadow = Shadow(Color.Black.copy(0.4f), blurRadius = 8f)
                        )
                    )
                } else {
                    // Fallback untuk role lain
                    Text(
                        text = role,
                        color = Color(0xFF1DB954), // Warna hijau biasa
                        fontSize = 14.sp,
                         fontFamily = AppFont.RobotoMedium,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // --- TOMBOL DENGAN WARNA NETRAL (ABU-ABU GELAP) ---
        Button(
            onClick = onSeeAllClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Transparent,
                contentColor = Color.White
            ),
            contentPadding = PaddingValues(),
            shape = RoundedCornerShape(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(buttonBgStart, buttonBgEnd)
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.LibraryMusic,
                        contentDescription = "Music Icon",
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Lihat Semua Lagu",
                        fontFamily = AppFont.RobotoBold,
                        fontSize = 16.sp
                    )
                }
            }
        }
    }
}



