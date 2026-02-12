package com.example.remusic.ui.components.homecomponents

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.remusic.data.model.SongWithArtist
import com.example.remusic.ui.components.QueueSongCard
import com.example.remusic.ui.theme.AppFont
import kotlin.math.ceil

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun QuickPickCarousel(
    songs: List<SongWithArtist>,
    onSongClick: (Int) -> Unit = {}
) {
    if (songs.isEmpty()) return

    // Limit to 20 songs (5 pages x 4 songs)
    val limitedSongs = songs.take(20)
    val songsPerPage = 4
    val totalPages = ceil(limitedSongs.size.toFloat() / songsPerPage).toInt()
    
    val pagerState = rememberPagerState(pageCount = { totalPages })

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
    ) {
        // Title
        Text(
            text = "Pilihan Cepat",
            fontFamily = AppFont.Poppins,
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        // Carousel
        HorizontalPager(
            state = pagerState,
            contentPadding = PaddingValues(horizontal = 16.dp),
            pageSpacing = 12.dp,
            modifier = Modifier.fillMaxWidth()
        ) { page ->
            // Each page shows up to 4 songs vertically
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val startIndex = page * songsPerPage
                val endIndex = minOf(startIndex + songsPerPage, limitedSongs.size)
                
                for (i in startIndex until endIndex) {
                    val songWithArtist = limitedSongs[i]
                    QueueSongCard(
                        index = i,
                        songTitle = songWithArtist.song.title,
                        artistName = songWithArtist.artist?.name ?: "Unknown Artist",
                        posterUri = songWithArtist.song.coverUrl ?: "",
                        isCurrentlyPlaying = false,
                        onClickListener = { onSongClick(i) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Page indicator dots (bottom center)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(totalPages.coerceAtMost(5)) { index ->
                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .size(if (pagerState.currentPage == index) 8.dp else 6.dp)
                        .background(
                            color = if (pagerState.currentPage == index)
                                Color.White
                            else
                                Color.White.copy(alpha = 0.3f),
                            shape = CircleShape
                        )
                )
            }
        }
    }
}
