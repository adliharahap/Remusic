package com.example.remusic.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.remusic.R
import com.example.remusic.data.model.displayArtistName
import com.example.remusic.ui.theme.AppFont
import com.example.remusic.viewmodel.playmusic.PlayerUiState

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BottomPlayerCard(
    uiState: PlayerUiState,
    onPlayPauseClick: () -> Unit,
    onNextClick: () -> Unit,
    onCardClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Animasi warna background agar transisi lagu lebih smooth
    val animatedCardColor by animateColorAsState(
        targetValue = uiState.dominantColors.getOrElse(0) { Color(0xFF1E1E1E) }, // Default dark premium color
        animationSpec = tween(durationMillis = 800),
        label = "cardColorAnimation"
    )

    // Animasi pergerakan progress bar agar meluncur sangat mulus
    val smoothProgress by animateFloatAsState(
        targetValue = if (uiState.totalDuration > 0) uiState.currentPosition.toFloat() / uiState.totalDuration else 0f,
        animationSpec = tween(durationMillis = 1000, easing = LinearEasing),
        label = "smoothProgress"
    )

    AnimatedVisibility(
        visible = uiState.currentSong != null,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        modifier = modifier
    ) {
        // Menempel ke bawah, full width, sudut bawah siku agar rata dengan BottomNav
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(elevation = 16.dp, shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomEnd = 16.dp, bottomStart = 16.dp))
                .clickable { onCardClick() },
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomEnd = 16.dp, bottomStart = 16.dp),
            colors = CardDefaults.cardColors(containerColor = animatedCardColor)
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                
                // Overlay Gradient halus untuk memberikan efek kedalaman (3D/Premium feel)
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.08f),
                                    Color.Black.copy(alpha = 0.25f)
                                )
                            )
                        )
                )
                
                // Konten Utama (Gambar, Teks, Tombol)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp), // Padding ditambah sedikit agar tidak sesak (ada ruang napas)
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    
                    // 1. Album Art (Diperbesar sedikit + ditambah shadow agar pop-out)
                    AsyncImage(
                        model = uiState.currentSong?.song?.coverUrl,
                        contentDescription = "Album Cover",
                        modifier = Modifier
                            .size(50.dp) // Ukuran dikembalikan ke proporsi yang pas
                            .shadow(4.dp, RoundedCornerShape(8.dp)) // Tambahan shadow pada cover
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White.copy(alpha = 0.1f)), // Placeholder background
                        contentScale = ContentScale.Crop,
                        placeholder = painterResource(id = R.drawable.img_placeholder),
                        error = painterResource(id = R.drawable.img_placeholder)
                    )
                    
                    Spacer(modifier = Modifier.width(12.dp))
                    
                    // 2. Song Details (Teks)
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = uiState.currentSong?.song?.title ?: "Not Playing",
                            color = Color.White,
                            maxLines = 1,
                            modifier = Modifier.basicMarquee(
                                iterations = Int.MAX_VALUE,
                                velocity = 30.dp
                            ),
                            fontFamily = AppFont.HelveticaRoundedBold,
                            fontSize = 15.sp // Font size sedikit disesuaikan
                        )
                        Spacer(modifier = Modifier.height(1.dp))
                        Text(
                            text = uiState.currentSong?.displayArtistName ?: "Unknown Artist",
                            color = Color.White.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontFamily = AppFont.Poppins,
                            fontSize = 12.sp
                        )
                    }
                    
                    Spacer(modifier = Modifier.width(4.dp))
                    
                    // 3. Controls (Next & Play/Pause)
                    // Tombol Next diperbesar sedikit
                    IconButton(
                        onClick = onNextClick,
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.next_svgrepo_com),
                            contentDescription = "Next",
                            tint = Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.size(28.dp) // Ukuran icon diperbesar
                        )
                    }
                    
                    Spacer(modifier = Modifier.width(6.dp))
                    
                    // Play/Pause Button diperkecil sedikit
                    Box(
                        modifier = Modifier
                            .size(38.dp) // Ukuran background lingkaran diperkecil
                            .clip(CircleShape)
                            .background(Color.White)
                            .clickable { onPlayPauseClick() },
                        contentAlignment = Alignment.Center
                    ) {
                        // Animasi transisi pergantian icon Play/Pause
                        AnimatedContent(
                            targetState = uiState.isPlaying,
                            transitionSpec = {
                                fadeIn(animationSpec = tween(150)) togetherWith fadeOut(animationSpec = tween(150))
                            },
                            label = "playPauseAnimation"
                        ) { isPlaying ->
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "Play/Pause",
                                tint = animatedCardColor.copy(alpha = 1f).let { 
                                    if (it == Color.Transparent || it == Color.Unspecified) Color.Black else it 
                                },
                                modifier = Modifier.size(24.dp) // Ukuran icon diperkecil
                            )
                        }
                    }
                }
                
                // 4. Progress Bar yang Mulus
                LinearProgressIndicator(
                    progress = { smoothProgress }, // Menggunakan state animasi mulus
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .align(Alignment.BottomCenter),
                    color = Color.White,
                    trackColor = Color.Transparent, // Dibuat transparan agar menyatu rapi di atas BottomNav
                    gapSize = 0.dp,
                    drawStopIndicator = {}
                )
            }
        }
    }
}