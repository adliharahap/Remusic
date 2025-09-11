package com.example.remusic.ui.components.homecomponents

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import com.example.remusic.data.model.SongWithArtist
import com.example.remusic.ui.theme.AppFont

@Composable
fun ArtistSection(
    title: String,
    items: List<SongWithArtist>,
    onSeeAllClick: () -> Unit = {}
) {
    Column(modifier = Modifier.padding(vertical = 10.dp)) {

        // Judul dan tombol See More
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                fontFamily = AppFont.PoppinsSemiBold,
                maxLines = 1,
                fontSize = 18.sp,
                modifier = Modifier.padding(start = 16.dp),
                overflow = TextOverflow.Ellipsis,
                color = Color.White
            )
            Text(
                text = "See All",
                fontFamily = AppFont.PoppinsSemiBold,
                maxLines = 1,
                fontSize = 13.sp,
                modifier = Modifier
                    .clickable { onSeeAllClick() }
                    .padding(end = 16.dp),
                overflow = TextOverflow.Ellipsis,
                color = Color(color = 0xFF2CA5B2)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // List lagu horizontal
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            items.forEach { item ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .width(100.dp)
                        .clickable { println("Klik lagu: ${item.artist?.name}") }
                ) {
                    Image(
                        painter = rememberAsyncImagePainter(item.artist?.photoUrl),
                        contentDescription = item.artist?.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(100.dp)
                            .clip(RoundedCornerShape(100.dp))
                            .background(MaterialTheme.colorScheme.surface)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = item.artist?.name ?: "unknown Artist",
                        fontSize = 14.sp,
                        fontFamily = AppFont.PoppinsMedium,
                        maxLines = 1,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}
