package com.example.remusic.ui.components.homecomponents

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.remusic.data.model.SongWithArtist
import com.example.remusic.data.model.displayArtistName

@Composable
fun SpeedDialSection(
    title: String,
    songs: List<SongWithArtist>,
    onSongClick: (SongWithArtist) -> Unit,
    modifier: Modifier = Modifier
) {
    if (songs.isEmpty()) return

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = title,
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        
        // Speed dial as a horizontal grid of 1:1 boxes
        LazyHorizontalGrid(
            rows = GridCells.Fixed(2), // 2 rows usually look good for square grids
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp), // Height to fit 2 rows of 80dp + gaps
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(1.5.dp),
            verticalArrangement = Arrangement.spacedBy(1.5.dp)
        ) {
            items(songs) { songWithArtist ->
                SpeedDialItem(
                    songWithArtist = songWithArtist,
                    onClick = { onSongClick(songWithArtist) }
                )
            }
        }
    }
}

@Composable
fun SpeedDialItem(
    songWithArtist: SongWithArtist,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(85.dp) // 1:1 square
            .clip(RoundedCornerShape(4.dp))
            .clickable { onClick() }
    ) {
        AsyncImage(
            model = songWithArtist.song.coverUrl,
            contentDescription = "Cover for ${songWithArtist.song.title}",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        
        // Gradient for text readability
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f)),
                        startY = 40f
                    )
                )
        )
        
        // Song Title
        Text(
            text = songWithArtist.song.title,
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(6.dp)
        )
    }
}
