package com.example.remusic.ui.components.homecomponents

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.remusic.data.model.Playlist
import com.example.remusic.ui.theme.AppFont

@Composable
fun OfficialPlaylistSection(
    playlists: List<Playlist>,
    onPlaylistClick: (Playlist) -> Unit = {}
) {
    if (playlists.isEmpty()) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        // Section title
        Text(
            text = "Official Playlists",
            fontFamily = AppFont.Poppins,
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Horizontal scrollable playlist cards
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(playlists) { playlist ->
                PlaylistCard(
                    playlist = playlist,
                    onClick = { onPlaylistClick(playlist) }
                )
            }
        }
    }
}

@Composable
private fun PlaylistCard(
    playlist: Playlist,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(130.dp)
            .clickable { onClick() }
    ) {
        // Cover image - 1:1 aspect ratio
        Box(
            modifier = Modifier
                .size(130.dp)
                .shadow(
                    elevation = 4.dp,
                    shape = RoundedCornerShape(12.dp)
                )
                .clip(RoundedCornerShape(12.dp))
        ) {
            AsyncImage(
                model = playlist.coverUrl,
                contentDescription = playlist.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize()
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Title below image
        Text(
            text = playlist.title,
            fontFamily = AppFont.Poppins,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(130.dp)
        )

        // Description (optional)
        playlist.description?.let { desc ->
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = desc,
                fontFamily = AppFont.Poppins,
                fontWeight = FontWeight.Normal,
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.6f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.width(130.dp)
            )
        }
    }
}
