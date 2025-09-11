package com.example.remusic.ui.components.searchcomponents

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.remusic.ui.screen.Song2
import com.example.remusic.ui.theme.AppFont

@Composable
fun RecentlyPlayedSection(
    title: String = "Baru Saja Diputar",
    songs: List<Song2>,
    onMoreOptionsClick: (Song2) -> Unit = {}
) {
    Column(modifier = Modifier.padding(vertical = 5.dp)) {
        Text(
            text = title,
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        songs.forEach { song ->
            SongListItem(
                song = song,
                onMoreOptionsClick = { onMoreOptionsClick(song) }
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
fun SongListItem(
    song: Song2,
    onMoreOptionsClick: () -> Unit = {}
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp).padding(bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = song.imageUrl,
            contentDescription = "Album Art for ${song.title}",
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(4.dp)),
            contentScale = androidx.compose.ui.layout.ContentScale.Crop
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                color = Color.White,
                fontWeight = FontWeight.Medium,
                fontFamily = AppFont.RobotoMedium
            )
            Text(
                text = song.artist,
                color = Color.Gray,
                fontSize = 12.sp,
                fontFamily = AppFont.RobotoRegular
            )
        }
        IconButton(onClick = onMoreOptionsClick) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = "More Options",
                tint = Color.Gray
            )
        }
    }
}