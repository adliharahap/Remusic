package com.example.remusic.ui.screen.playmusic

import android.widget.Toast
import androidx.compose.foundation.MarqueeAnimationMode
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import coil.compose.AsyncImage
import com.example.remusic.R
import com.example.remusic.data.model.SongWithArtist
import com.example.remusic.ui.theme.AppFont
import java.util.Locale
import java.util.concurrent.TimeUnit

fun formatDuration(millis: Long): String {
    val minutes = TimeUnit.MILLISECONDS.toMinutes(millis)
    val seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60
    return String.format(Locale.US, "%02d:%02d", minutes, seconds)
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlaying(
    songWithArtist: SongWithArtist?,
    isPlaying: Boolean,
    currentPosition: Long,
    totalDuration: Long,
    isShuffleEnabled: Boolean,
    repeatMode: Int,
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
        AsyncImage(
            model = songWithArtist?.song?.coverUrl,
            contentDescription = "Cover Album",
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .aspectRatio(1f)
                .shadow(
                    elevation = 6.dp,
                    shape = RoundedCornerShape(16.dp),
                    clip = false
                )
                .clip(RoundedCornerShape(10.dp)),
            contentScale = ContentScale.Crop,
            placeholder = painterResource(R.drawable.ic_music_note),
            error = painterResource(R.drawable.ic_music_note),
        )
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
                                .background(color = Color.DarkGray, shape = RoundedCornerShape(2.dp))
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
                    tint = iconTint
                )
            }

        }
        Spacer(modifier = Modifier.height(150.dp))

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
                    fontWeight = FontWeight.Bold,
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
                        fontWeight = FontWeight.ExtraBold,
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

