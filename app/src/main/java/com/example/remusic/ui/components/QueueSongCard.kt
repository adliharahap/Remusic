package com.example.remusic.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import com.example.remusic.ui.screen.playmusic.AudioWaveVisualizer
import com.example.remusic.ui.theme.AppFont


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