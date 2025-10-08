package com.example.remusic.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.remusic.ui.theme.AppFont
import com.example.remusic.viewmodel.playmusic.PlayerUiState

@Composable
fun BottomPlayerCard(
    uiState: PlayerUiState,
    onPlayPauseClick: () -> Unit,
    onCardClick: () -> Unit, // Untuk navigasi ke full screen
    modifier: Modifier = Modifier
) {

    val animatedCardColor by animateColorAsState(
        // Ambil warna pertama (yang lebih terang) dari daftar
        targetValue = uiState.dominantColors.getOrElse(0) { Color.DarkGray },
        animationSpec = tween(durationMillis = 1000),
        label = "cardColorAnimation"
    )

    // Hanya tampilkan kartu jika ada lagu yang sedang dimuat/diputar
    AnimatedVisibility(
        visible = uiState.currentSong != null,
        enter = slideInVertically(initialOffsetY = { it }),
        exit = slideOutVertically(targetOffsetY = { it }),
    ) {
        Card(
            modifier = modifier
                .fillMaxWidth()
                .clickable { onCardClick() },
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(containerColor = animatedCardColor)
        ) {
            Column {
                Row(
                    modifier = Modifier.padding(5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(
                        model = uiState.currentSong?.song?.coverUrl,
                        contentDescription = "Album Cover",
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .shadow(
                                elevation = 6.dp,
                                shape = RoundedCornerShape(16.dp),
                                clip = false
                            ),
                        contentScale = ContentScale.Crop,
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = uiState.currentSong?.song?.title ?: "Not Playing",
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontFamily = AppFont.RobotoMedium,
                            fontSize = 16.sp
                        )
                        Text(
                            text = uiState.currentSong?.artist?.name ?: "Unknown Artist",
                            color = Color.White.copy(0.8f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontFamily = AppFont.RobotoRegular,
                            fontSize = 14.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    IconButton(onClick = {}) {
                        Icon(
                            imageVector = Icons.Filled.FavoriteBorder,
                            contentDescription = "Add to Favorite",
                            tint = Color.White,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(5.dp))
                    IconButton(onClick = onPlayPauseClick) {
                        Icon(
                            imageVector = if (uiState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "Play/Pause",
                            tint = Color.White,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }
                // Progress Bar
                LinearProgressIndicator(
                    progress = { if (uiState.totalDuration > 0) uiState.currentPosition.toFloat() / uiState.totalDuration else 0f },
                    modifier = Modifier.fillMaxWidth().height(2.dp),
                    color = Color.White,
                    trackColor = Color.White.copy(0.4f),
                    gapSize = 0.dp
                )
            }
        }
    }
}
