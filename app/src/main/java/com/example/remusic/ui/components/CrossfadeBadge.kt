package com.example.remusic.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.remusic.ui.theme.AppFont

@Composable
fun CrossfadeBadge(
    isCrossfadeEnabled: Boolean,
    isCrossfading: Boolean,
    crossfadeDurationSeconds: Int,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = isCrossfadeEnabled,
        enter = fadeIn(tween(300)) + scaleIn(tween(300)),
        exit = fadeOut(tween(250)) + scaleOut(tween(250)),
        modifier = modifier
    ) {
        val shape = RoundedCornerShape(12.dp)
        val spotifyGreen = Color(0xFF1DB954)

        if (isCrossfading) {
            // Skeleton shimmer animation saat transisi crossfade sedang aktif
            Box(
                modifier = Modifier
                    .clip(shape)
                    .border(BorderStroke(1.dp, spotifyGreen.copy(alpha = 0.8f)), shape)
                    .background(Color.Black.copy(alpha = 0.45f))
                    .shimmerEffect(shape = shape)
                    .padding(horizontal = 10.dp, vertical = 3.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.AutoAwesome,
                        contentDescription = "Crossfade Active",
                        tint = spotifyGreen,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = "Crossfading • ${crossfadeDurationSeconds}s",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontFamily = AppFont.Helvetica,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.3.sp
                    )
                }
            }
        } else {
            // Badge statis halus saat fitur crossfade aktif namun belum masuk jendela crossfade
            Box(
                modifier = Modifier
                    .clip(shape)
                    .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)), shape)
                    .background(Color.White.copy(alpha = 0.08f))
                    .padding(horizontal = 9.dp, vertical = 2.5.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.AutoAwesome,
                        contentDescription = "Crossfade Ready",
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(11.dp)
                    )
                    Text(
                        text = "Crossfade ${crossfadeDurationSeconds}s",
                        color = Color.White.copy(alpha = 0.75f),
                        fontSize = 10.sp,
                        fontFamily = AppFont.Helvetica,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 0.2.sp
                    )
                }
            }
        }
    }
}
