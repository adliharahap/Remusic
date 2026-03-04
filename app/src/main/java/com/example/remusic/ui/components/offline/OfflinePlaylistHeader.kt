package com.example.remusic.ui.components.offline

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.remusic.R
import com.example.remusic.data.model.SongWithArtist
import com.example.remusic.ui.theme.AppFont
import com.example.remusic.viewmodel.playmusic.PlayMusicViewModel

@Composable
fun OfflinePlaylistHeader(
    songs: List<SongWithArtist>,
    playMusicViewModel: PlayMusicViewModel?,
    imageCollapseProgress: Float = 0f,
    accentBlue: Color = Color(0xFF1E88E5),
    accentBlue2: Color = Color(0xFF0D47A1),
    bgBlack: Color = Color(0xFF0A0A0A)
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colorStops = arrayOf(
                        0.0f to accentBlue2,
                        0.55f to accentBlue,
                        1.0f to bgBlack
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(top = 50.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(16.dp))

            // 2×2 cover collage (ANIMATED SCALE & ALPHA)
            val coverList = songs.take(4).mapNotNull { it.song.coverUrl }
            Box(
                modifier = Modifier
                    .size(180.dp)
                    .graphicsLayer {
                        // Matrix animation approach from PlaylistDetail
                        alpha = 1f - imageCollapseProgress
                        val scale = 1f - (imageCollapseProgress * 0.5f)
                        scaleX = scale
                        scaleY = scale
                        translationY = -imageCollapseProgress * 100f
                    }
                    .shadow(24.dp, RoundedCornerShape(20.dp))
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFF1E40AF), Color(0xFF3B82F6), Color(0xFF0F172A))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                when {
                    coverList.isEmpty() -> Icon(
                        Icons.Default.MusicNote, null,
                        tint = Color.White.copy(0.7f),
                        modifier = Modifier.size(80.dp)
                    )
                    coverList.size < 4 -> AsyncImage(
                        model = coverList.first(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                        error = painterResource(R.drawable.img_placeholder),
                        placeholder = painterResource(R.drawable.img_placeholder)
                    )
                    else -> Column(Modifier.fillMaxSize()) {
                        Row(Modifier.weight(1f).fillMaxWidth()) {
                            AsyncImage(
                                model = coverList[0], contentDescription = null,
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                                contentScale = ContentScale.Crop,
                                error = painterResource(R.drawable.img_placeholder),
                                placeholder = painterResource(R.drawable.img_placeholder)
                            )
                            Spacer(Modifier.width(1.dp))
                            AsyncImage(
                                model = coverList[1], contentDescription = null,
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                                contentScale = ContentScale.Crop,
                                error = painterResource(R.drawable.img_placeholder),
                                placeholder = painterResource(R.drawable.img_placeholder)
                            )
                        }
                        Spacer(Modifier.height(1.dp))
                        Row(Modifier.weight(1f).fillMaxWidth()) {
                            AsyncImage(
                                model = coverList[2], contentDescription = null,
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                                contentScale = ContentScale.Crop,
                                error = painterResource(R.drawable.img_placeholder),
                                placeholder = painterResource(R.drawable.img_placeholder)
                            )
                            Spacer(Modifier.width(1.dp))
                            AsyncImage(
                                model = coverList[3], contentDescription = null,
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                                contentScale = ContentScale.Crop,
                                error = painterResource(R.drawable.img_placeholder),
                                placeholder = painterResource(R.drawable.img_placeholder)
                            )
                        }
                    }
                }
                
                // OFFLINE badge
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .background(Color.Black.copy(0.55f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 7.dp, vertical = 3.dp)
                ) {
                    Text(
                        "OFFLINE", color = Color.White, fontSize = 9.sp,
                        fontWeight = FontWeight.Bold, fontFamily = AppFont.Poppins,
                        letterSpacing = 1.sp
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // Title & Meta (Stationary)
            Text(
                "Musik Offline", color = Color.White,
                fontFamily = AppFont.HelveticaRoundedBold,
                fontWeight = FontWeight.ExtraBold, fontSize = 28.sp
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "${songs.size} lagu tersedia",
                color = Color.White.copy(0.7f),
                fontFamily = AppFont.Helvetica, fontSize = 14.sp
            )

            Spacer(Modifier.height(24.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = {
                        if (songs.isNotEmpty()) {
                            playMusicViewModel?.playingMusicFromPlaylist("Offline Music")
                            playMusicViewModel?.setPlaylist(songs.shuffled(), 0)
                            playMusicViewModel?.forceSetShuffle(true)
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(0.35f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    modifier = Modifier.weight(1f).height(48.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp)
                ) {
                    Icon(Icons.Default.Shuffle, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Acak", fontFamily = AppFont.Poppins, fontWeight = FontWeight.SemiBold)
                }
                
                Button(
                    onClick = {
                        if (songs.isNotEmpty()) {
                            playMusicViewModel?.playingMusicFromPlaylist("Offline Music")
                            playMusicViewModel?.setPlaylist(songs, 0)
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = accentBlue2
                    ),
                    modifier = Modifier.weight(1f).height(48.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Putar Semua", fontFamily = AppFont.Poppins, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
