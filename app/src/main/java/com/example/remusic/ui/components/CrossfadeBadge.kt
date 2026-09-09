package com.example.remusic.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.remusic.ui.theme.AppFont

@Composable
fun CrossfadeBadge(
    isCrossfadeEnabled: Boolean,
    isCrossfading: Boolean,
    shouldShowBadge: Boolean,
    crossfadeDurationSeconds: Int,
    modifier: Modifier = Modifier
) {
    var localVisible by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

    androidx.compose.runtime.LaunchedEffect(shouldShowBadge) {
        if (shouldShowBadge) {
            localVisible = true
        } else {
            if (localVisible) {
                kotlinx.coroutines.delay(5000)
                localVisible = false
            }
        }
    }

    AnimatedVisibility(
        visible = isCrossfadeEnabled && localVisible,
        enter = slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(500)) + fadeIn(tween(500)),
        exit = slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(500)) + fadeOut(tween(500)),
        modifier = modifier.offset(y = 5.dp)
    ) {
        val shape = RoundedCornerShape(12.dp)

        val badgeColor by animateColorAsState(
            targetValue = if (isCrossfading) Color(0xFF1DB954).copy(alpha = 0.25f) else Color.White.copy(alpha = 0.08f),
            animationSpec = tween(500), label = "badgeColor"
        )
        val borderColor by animateColorAsState(
            targetValue = if (isCrossfading) Color(0xFF1DB954).copy(alpha = 0.8f) else Color.White.copy(alpha = 0.18f),
            animationSpec = tween(500), label = "borderColor"
        )
        val iconColor by animateColorAsState(
            targetValue = if (isCrossfading) Color(0xFF1DB954) else Color.White.copy(alpha = 0.7f),
            animationSpec = tween(500), label = "iconColor"
        )
        val textColor by animateColorAsState(
            targetValue = if (isCrossfading) Color.White else Color.White.copy(alpha = 0.75f),
            animationSpec = tween(500), label = "textColor"
        )

        // Shimmer / kilat-kilat skeleton effect
        val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
        val shimmerTranslate by infiniteTransition.animateFloat(
            initialValue = -200f,
            targetValue = 400f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1800, easing = LinearEasing, delayMillis = 400),
                repeatMode = RepeatMode.Restart
            ),
            label = "shimmerTranslate"
        )

        Box(
            modifier = Modifier
                .clip(shape)
                .border(BorderStroke(1.dp, borderColor), shape)
                .background(badgeColor),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.AutoAwesome,
                    contentDescription = "Crossfade",
                    tint = iconColor,
                    modifier = Modifier.size(10.dp)
                )
                Text(
                    text = "Crossfade ${crossfadeDurationSeconds}s",
                    color = textColor,
                    fontSize = 9.sp,
                    fontFamily = AppFont.Helvetica,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.2.sp
                )
            }

            // Shimmer overlay
            val shimmerBrush = Brush.linearGradient(
                colors = listOf(Color.Transparent, Color.White.copy(alpha = 0.45f), Color.Transparent),
                start = Offset(shimmerTranslate, 0f),
                end = Offset(shimmerTranslate + 80f, 60f)
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(shimmerBrush)
            )
        }
    }
}
