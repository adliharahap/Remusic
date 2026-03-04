package com.example.remusic.ui.screen.playmusic

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.remusic.data.model.SongWithArtist
import com.example.remusic.data.model.displayArtistName
import com.example.remusic.ui.components.QueueSongCard
import com.example.remusic.ui.theme.AppFont

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun QueueScreen(
    songWithArtist: SongWithArtist?,
    playlistQueue: List<SongWithArtist> = emptyList(),
    playMusicFromPlaylist:  String = "Unknown Playlist",
    playlistSubtitle: String = "Memainkan Dari Playlist", // Add parameter
    onClickListener: (index: Int) -> Unit = {},
    // Callbacks for Queue Options
    onAddToQueue: (SongWithArtist) -> Unit = {},
    onPlayNext: (SongWithArtist) -> Unit = {},
    onAddToPlaylist: (SongWithArtist) -> Unit = {},
    onDownload: (SongWithArtist) -> Unit = {},
    onAddToLiked: (SongWithArtist) -> Unit = {},
    onRemoveFromQueue: (SongWithArtist) -> Unit = {},
    topPlayerColor: Color = Color.Transparent,
    headerHeight: androidx.compose.ui.unit.Dp = 120.dp
) {
    // 1. Definisikan ID lagu yang sedang diputar sebagai String
    val currentlyPlayingId = songWithArtist?.song?.id

    // --- Bottom Sheet State ---
    var showQueueOptions by remember { mutableStateOf(false) }
    var selectedSongForOptions by remember { mutableStateOf<SongWithArtist?>(null) }
    val queueSheetState = rememberModalBottomSheetState()

    // --- State untuk LazyColumn dan Drag-and-Drop ---
    val listState = rememberLazyListState()

    //untuk memberikan padding top
    val configuration = LocalConfiguration.current
    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
        modifier = Modifier.fillMaxSize()
        ) {
        item {
            Spacer(modifier = Modifier.height(headerHeight).fillMaxWidth())
        }
        // Bagian Header
        item {
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp, horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(playlistSubtitle, color = Color.White.copy(0.8f), fontSize = 14.sp)
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
                        artistName = current.displayArtistName,
                        posterUri = current.song.coverUrl ?: "",
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
                artistName = songWithArtist?.displayArtistName ?: "Unknown Artist",
                posterUri = songWithArtist.song.coverUrl ?: "",
                isCurrentlyPlaying = currentlyPlayingId != null && songWithArtist.song.id == currentlyPlayingId,
                onClickListener = onClickListener,
                onMoreClick = {
                    selectedSongForOptions = songWithArtist
                    showQueueOptions = true
                }
            )
        }

        item {
            Spacer(modifier = Modifier.height(60.dp))
        }
    }

        // 1. Gradient ATAS (Fade dari Warna ke Transparan)
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
    }

    if (showQueueOptions && selectedSongForOptions != null) {
        val context = androidx.compose.ui.platform.LocalContext.current
        QueueOptionsBottomSheet(
            sheetState = queueSheetState,
            songWithArtist = selectedSongForOptions,
            onDismiss = { showQueueOptions = false },
            onAddToQueue = {
                onAddToQueue(selectedSongForOptions!!)
                showQueueOptions = false
            },
            onPlayNext = {
                onPlayNext(selectedSongForOptions!!)
                showQueueOptions = false
            },
            onAddToPlaylist = {
                onAddToPlaylist(selectedSongForOptions!!)
                showQueueOptions = false
            },
            onDownload = {
                onDownload(selectedSongForOptions!!)
                showQueueOptions = false
            },
            onAddToLiked = {
                onAddToLiked(selectedSongForOptions!!)
                showQueueOptions = false
            },
            showRemoveFromQueue = true,
            onRemoveFromQueue = {
                if (playlistQueue.size <= 1) {
                    android.widget.Toast.makeText(context, "Tidak dapat menghapus lagu terakhir di antrean", android.widget.Toast.LENGTH_SHORT).show()
                } else {
                    onRemoveFromQueue(selectedSongForOptions!!)
                }
                showQueueOptions = false
            }
        )
    }
}