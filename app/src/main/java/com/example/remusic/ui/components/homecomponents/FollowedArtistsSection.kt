package com.example.remusic.ui.components.homecomponents

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.remusic.data.model.Artist
import com.example.remusic.ui.theme.AppFont

@Composable
fun FollowedArtistsSection(
    artists: List<Artist>,
    onArtistClick: (Artist) -> Unit = {}
) {
    if (artists.isEmpty()) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        // Section title
        Text(
            text = "Artists You Follow",
            fontFamily = AppFont.Poppins,
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Horizontal scrollable artist circles
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(artists) { artist ->
                ArtistCircle(
                    artist = artist,
                    onClick = { onArtistClick(artist) }
                )
            }
        }
    }
}

@Composable
private fun ArtistCircle(
    artist: Artist,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable { onClick() }
            .padding(vertical = 8.dp)
    ) {
        // Artist photo with gradient border
        AsyncImage(
            model = artist.photoUrl,
            contentDescription = artist.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(80.dp)
                .shadow(
                    elevation = 8.dp,
                    shape = CircleShape,
                    spotColor = Color.White.copy(alpha = 0.2f)
                )
                .clip(CircleShape)
                .border(
                    width = 2.5.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF9C27B0),
                            Color(0xFF673AB7),
                            Color(0xFF3F51B5)
                        )
                    ),
                    shape = CircleShape
                )
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Artist name
        Text(
            text = artist.name,
            fontFamily = AppFont.Poppins,
            fontWeight = FontWeight.Medium,
            fontSize = 13.sp,
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.size(80.dp, 40.dp)
        )
    }
}
