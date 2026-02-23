package com.example.remusic.ui.components.homecomponents

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.example.remusic.R
import com.example.remusic.ui.theme.AppFont

@Composable
fun HomeHeader(
    name: String? = "User",
    greeting: String,
    profileImageUrl: String? = null,
    onSearchClick: () -> Unit = {},
    onNotificationClick: () -> Unit = {},
    onRequestSongClick: () -> Unit = {},
    dominantColor: Color = Color(0xFF755D8D) // Default purple
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp) // Increased height
    ) {
        // Animated background blobs
        AnimatedBlobs(dominantColor = dominantColor)

        // Main content
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, bottom = 0.dp,  start = 16.dp, end = 16.dp)
        ) {
            // Top row: Logo + Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Logo + Text
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        painter = painterResource(R.drawable.app_logo),
                        contentDescription = "Remusic Logo",
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Remusic",
                        fontFamily = AppFont.Poppins,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = Color.White
                    )
                }

                // Action buttons
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Search
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(
                                color = Color.White.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable { onSearchClick() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Request Song
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(
                                color = Color.White.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable { onRequestSongClick() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Request Song",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Notification
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(
                                color = Color.White.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable { onNotificationClick() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = "Notifications",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                        // Badge
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .align(Alignment.TopEnd)
                                .offset(x = (-2).dp, y = 2.dp)
                                .background(Color(0xFFE91E63), CircleShape)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Greeting + Profile
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Profile picture
                Box(
                    modifier = Modifier.size(56.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .background(
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        Color(0xFFE91E63),
                                        Color(0xFF9C27B0),
                                        Color(0xFF3F51B5)
                                    )
                                ),
                                shape = CircleShape
                            )
                    )
                    AsyncImage(
                        model = profileImageUrl,
                        contentDescription = "Profile",
                        modifier = Modifier
                            .size(52.dp)
                            .align(Alignment.Center)
                            .clip(CircleShape)
                            .background(Color.Black)
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                // Text
                Column {
                    Text(
                        text = greeting,
                        fontFamily = AppFont.Poppins,
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
                        color = Color.White
                    )
                    Text(
                        text = name ?: "Guest",
                        fontFamily = AppFont.Poppins,
                        fontWeight = FontWeight.Normal,
                        fontSize = 14.sp,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

@Composable
private fun AnimatedBlobs(dominantColor: Color = Color(0xFF755D8D)) {
    val infiniteTransition = rememberInfiniteTransition(label = "blobs")

    // Blob 1 - Moving diagonally
    val blob1OffsetX by infiniteTransition.animateFloat(
        initialValue = -40f,
        targetValue = 40f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "blob1X"
    )
    val blob1OffsetY by infiniteTransition.animateFloat(
        initialValue = -20f,
        targetValue = 20f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "blob1Y"
    )

    // Blob 2 - Moving differently
    val blob2OffsetX by infiniteTransition.animateFloat(
        initialValue = 200f,
        targetValue = 240f,
        animationSpec = infiniteRepeatable(
            animation = tween(3500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "blob2X"
    )
    val blob2OffsetY by infiniteTransition.animateFloat(
        initialValue = 40f,
        targetValue = -20f,
        animationSpec = infiniteRepeatable(
            animation = tween(4500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "blob2Y"
    )

    // Blob 1 - Using dynamic dominant color (maximum visibility)
    Box(
        modifier = Modifier
            .size(220.dp)
            .offset(x = blob1OffsetX.dp, y = blob1OffsetY.dp)
            .clip(CircleShape)
            .background(
                brush = Brush.radialGradient(
                    colors = listOf(
                        dominantColor.copy(alpha = 0.8f),
                        Color.Transparent
                    )
                )
            )
            .blur(30.dp)
    )

    // Blob 2 - Using dominant color with high visibility
    Box(
        modifier = Modifier
            .size(200.dp)
            .offset(x = blob2OffsetX.dp, y = blob2OffsetY.dp)
            .clip(CircleShape)
            .background(
                brush = Brush.radialGradient(
                    colors = listOf(
                        dominantColor.copy(alpha = 0.75f),
                        Color.Transparent
                    )
                )
            )
            .blur(25.dp)
    )
}