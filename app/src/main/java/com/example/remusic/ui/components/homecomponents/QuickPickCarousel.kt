package com.example.remusic.ui.components.homecomponents

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import com.example.remusic.data.model.displayArtistName
import com.example.remusic.ui.theme.AppFont
import kotlin.math.ceil
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import coil.compose.AsyncImage
import com.example.remusic.R

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun QuickPickCarousel(
    songs: List<SongWithArtist>,
    onSongClick: (Int) -> Unit = {},
    onMoreClick: (SongWithArtist) -> Unit
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
            fontSize = 16.sp,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        // Carousel
        HorizontalPager(
            state = pagerState,
            contentPadding = PaddingValues(horizontal = 10.dp),
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
                    CompactSongCard(
                        index = i,
                        songTitle = songWithArtist.song.title,
                        artistName = songWithArtist.displayArtistName,
                        posterUri = songWithArtist.song.coverUrl ?: "",
                        onClickListener = { onSongClick(i) },
                        onMoreClick = { onMoreClick(songWithArtist) }
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
                        .size(if (pagerState.currentPage == index) 6.dp else 5.dp)
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CompactSongCard(
    index: Int,
    songTitle: String,
    artistName: String,
    posterUri: String,
    onClickListener: (index: Int) -> Unit,
    onMoreClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { onClickListener(index) },
                onLongClick = { onMoreClick() }
            )
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = (index + 1).toString(),
            color = Color.White.copy(0.8f),
            fontFamily = AppFont.Coolvetica,
            fontSize = 13.sp,
            modifier = Modifier.width(16.dp)
        )

        Spacer(modifier = Modifier.width(12.dp))

        AsyncImage(
            model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                .data(posterUri)
                .crossfade(true)
                .build(),
            contentDescription = "Poster Music",
            modifier = Modifier
                .size(40.dp)
                .aspectRatio(1f)
                .clip(RoundedCornerShape(4.dp)),
            contentScale = ContentScale.Crop,
            placeholder = painterResource(R.drawable.default_playlist),
            error = painterResource(R.drawable.default_playlist),
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = songTitle,
                color = Color.White,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.Medium
            )
            
            Spacer(modifier = Modifier.height(2.dp))
            
            Text(
                text = artistName,
                color = Color.White.copy(0.7f),
                fontFamily = AppFont.Helvetica,
                fontWeight = FontWeight.Normal,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Icon(
            imageVector = Icons.Outlined.MoreVert,
            contentDescription = "Action",
            tint = Color.White.copy(0.8f),
            modifier = Modifier
                .size(20.dp)
                .clickable { onMoreClick() }
        )
    }
}
