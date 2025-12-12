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
import com.example.remusic.ui.components.QueueSongCard
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