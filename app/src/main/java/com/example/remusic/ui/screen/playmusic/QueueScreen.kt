package com.example.remusic.ui.screen.playmusic

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.remusic.R
import com.example.remusic.data.model.SongWithArtist
import com.example.remusic.ui.theme.AppFont

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun QueueScreen(
    songWithArtist: SongWithArtist?,
    playlistQueue: List<SongWithArtist> = emptyList(),
    playMusicFromPlaylist:  String = "Unknown Playlist",
    onClickListener: (index: Int) -> Unit = {},
) {
    // 1. Definisikan ID lagu yang sedang diputar sebagai String
    val currentlyPlayingId = songWithArtist?.song?.id

    // --- State untuk LazyColumn dan Drag-and-Drop ---
    val listState = rememberLazyListState()

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize()
    ) {
        // Bagian Header
        item {
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp, horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Memainkan Dari Playlist", color = Color.White.copy(0.8f), fontSize = 14.sp)
                Text(playMusicFromPlaylist, color = Color.White, fontFamily = AppFont.RobotoBold, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }

        // Bagian "Sedang Diputar"
        songWithArtist?.let { current ->
            item {
                Column(Modifier.fillMaxWidth()) {
                    Text(
                        "Lagu yang sedang diputar",
                        color = Color.White,
                        fontFamily = AppFont.RobotoBold,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    QueueSongCard(
                        index = 0,
                        songTitle = current.song.title.takeIf { it.isNotBlank() } ?: "Unknown Song",
                        artistName = current.artist?.name ?: "Unknown Artist",
                        posterUri = current.song.coverUrl,
                        isCurrentlyPlaying = true
                    )
                }
            }
        }

        // Bagian "Semua Queue"
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp, bottom = 8.dp, start = 16.dp, end = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Semua Antrian", color = Color.White, fontFamily = AppFont.RobotoBold, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text("${playlistQueue.size} Lagu", color = Color.White.copy(0.8f), fontSize = 14.sp)
            }
        }

        itemsIndexed(
            playlistQueue,
            key = { _, songWithArtist -> songWithArtist.song.id } // key unik
        ) { index, songWithArtist ->
            QueueSongCard(
                index = index,
                songTitle = songWithArtist.song.title,
                artistName = songWithArtist.artist?.name ?: "Unknown Artist",
                posterUri = songWithArtist.song.coverUrl,
                isCurrentlyPlaying = currentlyPlayingId != null && songWithArtist.song.id == currentlyPlayingId,
                onClickListener = onClickListener,
            )
        }

        item {
            Spacer(modifier = Modifier.height(60.dp))
        }
    }
}

@Composable
fun QueueSongCard(
    index: Int,
    songTitle: String,
    artistName: String,
    posterUri: String,
    isCurrentlyPlaying: Boolean,
    modifier: Modifier = Modifier,
    onClickListener: (index: Int) -> Unit = {},
) {
    val activeColor = Color.Black
    val cardBackgroundColor = if (isCurrentlyPlaying) {
        activeColor.copy(alpha = 0.15f)
    } else {
        Color.Transparent
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable{ onClickListener(index) },
        colors = CardDefaults.cardColors(
            containerColor = cardBackgroundColor
        ),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isCurrentlyPlaying) {
                AudioWaveVisualizer(
                    barCount = 4,
                    maxHeight = 30.dp,
                    minHeight = 4.dp,
                    barWidth = 2.dp,
                    barColor = Color.White,
                    animationDuration = 300,
                    modifier = Modifier.width(20.dp)
                )
            } else {
                Text(
                    text = (index + 1).toString(),
                    color = Color.White.copy(0.7f),
                    fontFamily = AppFont.RobotoRegular,
                    fontSize = 14.sp,
                    modifier = Modifier.width(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            AsyncImage(
                model = posterUri,
                contentDescription = "Poster Music",
                modifier = Modifier
                    .size(48.dp)
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(6.dp)),
                contentScale = ContentScale.Crop,
                placeholder = painterResource(R.drawable.ic_music_note),
                error = painterResource(R.drawable.ic_music_note),
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = songTitle,
                    color = if (isCurrentlyPlaying) Color.White else Color.White.copy(0.9f),
                    fontFamily = AppFont.RobotoMedium,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = artistName,
                    color = Color.White.copy(0.7f),
                    fontFamily = AppFont.RobotoRegular,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Icon(
                imageVector = Icons.Outlined.MoreVert,
                contentDescription = "more",
                tint = Color.White.copy(0.8f),
                modifier = Modifier.size(24.dp)
            )
        }
    }
}