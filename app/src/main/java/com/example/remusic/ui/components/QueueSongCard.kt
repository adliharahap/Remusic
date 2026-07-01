package com.example.remusic.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
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
    sourceLabel: String? = null,
    isEditMode: Boolean = false,
    dragModifier: Modifier = Modifier,
    onClickListener: (index: Int) -> Unit = {},
    onMoreClick: () -> Unit = {}
) {
    val activeColor = Color.Black
    val cardBackgroundColor = if (isCurrentlyPlaying) {
        activeColor.copy(alpha = 0.15f)
    } else {
        Color.Transparent
    }

    @OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .combinedClickable(
                onClick = { onClickListener(index) },
                onLongClick = if (isEditMode) null else { { onMoreClick() } }
            ),
        colors = CardDefaults.cardColors(
            containerColor = cardBackgroundColor
        ),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (isEditMode) dragModifier else Modifier)
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
            } else if (isEditMode) {
                Icon(
                    imageVector = Icons.Default.DragHandle,
                    contentDescription = "Drag Handle",
                    tint = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.size(18.dp)
                )
            } else {
                Text(
                    text = (index + 1).toString(),
                    color = Color.White.copy(0.8f),
                    fontFamily = AppFont.Coolvetica,
                    fontSize = 15.sp,
                    modifier = Modifier.width(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            AsyncImage(
                model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                    .data(posterUri)
                    .crossfade(true)
                    .build(),
                contentDescription = "Poster Music",
                modifier = Modifier
                    .size(48.dp)
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(6.dp)),
                contentScale = ContentScale.Crop,
                placeholder = painterResource(R.drawable.default_playlist),
                error = painterResource(R.drawable.default_playlist),
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = songTitle,
                    color = if (isCurrentlyPlaying) Color.White else Color.White.copy(0.9f),
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Medium
                )
                
                Spacer(modifier = Modifier.height(2.dp))
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Artist Name akan mengambil sisa ruang tapi tidak akan mendesak badge
                    Text(
                        text = artistName,
                        color = Color.White.copy(0.7f), // Dibuat sedikit lebih redup agar elegan
                        fontFamily = AppFont.Helvetica,
                        fontWeight = FontWeight.Normal,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false) 
                    )
                    
                    if (sourceLabel != null) {
                        val isYouTube = sourceLabel.contains("YouTube", ignoreCase = true) || 
                                        sourceLabel.contains("Extended", ignoreCase = true) ||
                                        sourceLabel.contains("Video", ignoreCase = true)
                        
                        val displayLabel = sourceLabel ?: "Uploaded"
                        
                        // Warna aksen: Merah lembut untuk YT/Video, Biru lembut untuk Uploaded
                        val accentColor = if (isYouTube) Color(0xFFFF5252) else Color(0xFF64B5F6)

                        Spacer(modifier = Modifier.width(8.dp))

                        // UI Baru: "Soft Pill" yang sangat minimalis dan premium
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50)) // Membuatnya membulat seperti kapsul penuh
                                .background(accentColor.copy(alpha = 0.12f)) // Background transparan halus
                                .padding(horizontal = 6.dp, vertical = 2.dp) // Sangat ramping
                        ) {
                            // Aksen titik warna (Dot) untuk menambah kesan profesional
                            Box(
                                modifier = Modifier
                                    .size(4.dp)
                                    .clip(CircleShape)
                                    .background(accentColor)
                            )
                            
                            Spacer(modifier = Modifier.width(4.dp))
                            
                            Text(
                                text = displayLabel,
                                color = accentColor,
                                fontFamily = AppFont.Helvetica,
                                fontSize = 10.sp, // Ukuran mungil tapi jelas
                                fontWeight = FontWeight.Medium,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Icon(
                imageVector = Icons.Outlined.MoreVert,
                contentDescription = "more",
                tint = Color.White.copy(0.8f),
                modifier = Modifier
                    .size(24.dp)
                    .clickable { onMoreClick() }
            )
        }
    }
}

@Composable
fun QueueSongCardSkeleton() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SkeletonBox(
            modifier = Modifier
                .width(20.dp)
                .height(15.dp)
        )

        Spacer(modifier = Modifier.width(14.dp))

        SkeletonBox(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(6.dp)),
            shape = RoundedCornerShape(6.dp)
        )

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            SkeletonBox(
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .height(16.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))
            SkeletonBox(
                modifier = Modifier
                    .fillMaxWidth(0.4f)
                    .height(14.dp)
            )
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        SkeletonBox(
            modifier = Modifier.size(24.dp),
            shape = CircleShape
        )
    }
}