package com.example.remusic.ui.screen.playmusic

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.remusic.data.model.Artist
import com.example.remusic.data.model.User
import com.example.remusic.ui.theme.AppFont
import kotlinx.coroutines.delay

@Composable
fun WavyText(
    text: String,
    style: androidx.compose.ui.text.TextStyle,
    color: Color,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "WavyTextTransition")
    
    // Duration of the wave cycle: 8000ms.
    val totalCycle = 8000
    
    Row(modifier = modifier) {
        text.forEachIndexed { index, char ->
            val delayMillis = index * 100 // Sequential delay per character
            
            val offset by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 0f,
                animationSpec = infiniteRepeatable(
                    animation = keyframes {
                        durationMillis = totalCycle
                        // The actual movement happens in the first 1000ms (shifted by delay)
                        0f at delayMillis using FastOutSlowInEasing
                        -8f at delayMillis + 300 using FastOutSlowInEasing // Up
                        0f at delayMillis + 600 using FastOutSlowInEasing // Down
                        0f at totalCycle // Pause until next cycle
                    },
                    repeatMode = RepeatMode.Restart
                ),
                label = "CharOffset"
            )
            
            Text(
                text = char.toString(),
                style = style,
                color = color,
                modifier = Modifier.graphicsLayer(translationY = offset)
            )
        }
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun UploaderBoxAndArtist(
    artist: Artist?,
    uploader: User?,
    isFollowed: Boolean = false,
    onToggleFollow: () -> Unit = {},
    onLihatPlaylistClick: () -> Unit = {}, // NEW: Navigate to artist playlist
    onSemuaLaguClick: () -> Unit = {}      // NEW: Navigate to all songs (same as artist playlist)
) {
    // 1. DELAYED STATE LOGIC (Prevent flickering to 'Unknown' during transitions)
    var displayedArtist by remember { mutableStateOf<Artist?>(null) }
    var displayedUploader by remember { mutableStateOf<User?>(null) }

    LaunchedEffect(artist) {
        if (artist != null) {
            displayedArtist = artist
        } else {
            delay(2000)
            displayedArtist = artist
        }
    }

    LaunchedEffect(uploader) {
        if (uploader != null) {
            displayedUploader = uploader
        } else {
            delay(2000)
            displayedUploader = uploader
        }
    }

    // 2. DATA TRANSITION (Smooth change when song changes)
    AnimatedContent(
        targetState = Pair(displayedArtist, displayedUploader),
        transitionSpec = {
            (fadeIn(animationSpec = tween(500)) + scaleIn(initialScale = 0.98f)).togetherWith(
                fadeOut(animationSpec = tween(400))
            )
        },
        label = "UploaderBoxTransition"
    ) { (currentArtist, currentUploader) ->

        // Fallback data
        val artistName = currentArtist?.name ?: "Unknown Artist"
        val artistPhoto = currentArtist?.photoUrl ?: "https://picsum.photos/id/64/300/300"
        val artistDesc = currentArtist?.description ?: "No description available for this artist."

        val uploaderName = currentUploader?.displayName ?: "Unknown Uploader"
        val uploaderPhoto = currentUploader?.photoUrl ?: "https://picsum.photos/id/65/200/200"
        val uploaderRole = currentUploader?.role?.replaceFirstChar { it.uppercase() } ?: "Uploader"

        // LUXURY DECORATION: Role-based logic
        val isOwner = currentUploader?.role?.lowercase() == "owner" || currentUploader?.role?.lowercase() == "admin"
        val isUploaderRole = currentUploader?.role?.lowercase() == "uploader"
        val roleColor = when {
            isOwner -> Color(0xFFFFD700) // Gold
            isUploaderRole -> Color(0xFF3897F0) // Blue
            else -> Color(0xFF1DB954) // Green
        }

        // CONTAINER UTAMA (WIDTH 100%)
        Card(
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF1A1A1A).copy(alpha = 0.9f)
            ),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
                .border(
                    width = 1.dp,
                    brush = Brush.linearGradient(
                        listOf(Color.White.copy(alpha = 0.12f), Color.Transparent)
                    ),
                    shape = RoundedCornerShape(24.dp)
                )
        ) {
            Column {
                // ================= BAGIAN 1: ARTIST (FULL WIDTH DESCRIPTION) =================
                Column(
                    modifier = Modifier.padding(20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Gambar Artist
                        AsyncImage(
                            model = artistPhoto,
                            contentDescription = "Artist Image",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(110.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color.DarkGray)
                                .border(
                                    1.dp,
                                    Color.White.copy(alpha = 0.15f),
                                    RoundedCornerShape(16.dp)
                                )
                        )

                        Spacer(modifier = Modifier.width(20.dp))

                        Column {
                            // Nama Artist + Verified
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = artistName,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontFamily = AppFont.HelveticaRoundedBold,
                                    color = Color.White,
                                    fontSize = 22.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    imageVector = Icons.Filled.Verified,
                                    contentDescription = "Verified",
                                    tint = Color(0xFF1DB954),
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Button Row
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // Button Lihat Playlist
                                OutlinedButton(
                                    onClick = onLihatPlaylistClick,
                                    border = BorderStroke(1.2.dp, Color.White.copy(alpha = 0.25f)),
                                    shape = RoundedCornerShape(50),
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                                    modifier = Modifier.height(36.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                                ) {
                                    Text("Lihat Playlist", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                // Button Follow / Mengikuti
                                if (isFollowed) {
                                    // MENGIKUTI (Outlined, Green Text/Border)
                                    OutlinedButton(
                                        onClick = onToggleFollow,
                                        border = BorderStroke(1.2.dp, Color.White.copy(alpha = 0.5f)),
                                        shape = RoundedCornerShape(50),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                        modifier = Modifier.height(36.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            contentColor = Color.White
                                        )
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Check,
                                            contentDescription = "Followed",
                                            modifier = Modifier.size(16.dp),
                                            tint = Color.White
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Diikuti", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    }
                                } else {
                                    // IKUTI (Filled Translucent White)
                                    Button(
                                        onClick = onToggleFollow,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color.White.copy(alpha = 0.2f),
                                            contentColor = Color.White
                                        ),
                                        shape = RoundedCornerShape(50),
                                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                                        modifier = Modifier.height(36.dp)
                                    ) {
                                        Text("Ikuti", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Deskripsi (FULL WIDTH with ANIMATED HEIGHT)
                    var isExpanded by remember { mutableStateOf(false) }
                    
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateContentSize(animationSpec = tween(durationMillis = 500, easing = LinearOutSlowInEasing))
                    ) {
                        Text(
                            text = artistDesc,
                            style = MaterialTheme.typography.bodyLarge,
                            fontFamily = AppFont.Helvetica,
                            color = Color.White.copy(alpha = 0.75f),
                            lineHeight = 22.sp,
                            maxLines = if (isExpanded) Int.MAX_VALUE else 3,
                            overflow = TextOverflow.Ellipsis,
                            fontSize = 15.sp,
                            modifier = Modifier.fillMaxWidth()
                        )
                        
                        Text(
                            text = if (isExpanded) "Show Less" else "Show More",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1DB954),
                            modifier = Modifier
                                .clickable { isExpanded = !isExpanded }
                                .padding(vertical = 4.dp)
                        )
                    }
                }

                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = Color.White.copy(alpha = 0.08f)
                )

                // ================= BAGIAN 2: UPLOADER (BUTTON BELOW) =================
                Column(
                    modifier = Modifier
                        .padding(20.dp)
                        .fillMaxWidth()
                ) {
                    Text(
                        text = "Lagu ini diupload oleh :",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.5f),
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 0.5.sp
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(62.dp)
                                .clip(CircleShape)
                                .then(
                                    if (isOwner) {
                                        Modifier
                                            .background(
                                                brush = Brush.linearGradient(
                                                    colors = listOf(
                                                        Color(0xFFE91E63),
                                                        Color(0xFF9C27B0),
                                                        Color(0xFF3F51B5)
                                                    )
                                                )
                                            )
                                            .padding(2.5.dp)
                                    } else if (isUploaderRole) {
                                        Modifier
                                            .background(Color(0xFF3897F0))
                                            .padding(2.5.dp)
                                    } else {
                                        Modifier
                                            .background(Color.White.copy(alpha = 0.15f))
                                            .padding(1.5.dp)
                                    }
                                )
                                .clip(CircleShape)
                        ) {
                            AsyncImage(
                                model = uploaderPhoto,
                                contentDescription = "Uploader Image",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxSize()
                            )
                        }

                        Spacer(modifier = Modifier.width(18.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = uploaderName,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = AppFont.HelveticaRoundedBold,
                                    color = Color.White,
                                    fontSize = 18.sp
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                // Checkmark on the right
                                Icon(
                                    imageVector = Icons.Filled.CheckCircle,
                                    contentDescription = "Uploader Verified",
                                    tint = roleColor,
                                    modifier = Modifier.offset(y = 1.5.dp).size(19.dp)
                                )
                            }
                            
                            // WAVY ANIMATION FOR ROLE TEXT (Wavy cycle: 4s total, 1s action + 3s pause)
                            if (isOwner) {
                                WavyText(
                                    text = "Remusic Official $uploaderRole",
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontFamily = AppFont.Helvetica,
                                        letterSpacing = 3.sp,
                                        fontSize = 14.sp
                                    ),
                                    color = roleColor
                                )
                            } else {
                                Text(
                                    text = uploaderRole,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = roleColor,
                                    letterSpacing = 1.2.sp,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // BUTTON BELOW UPLOADER (PROFESSIONAL OUTLINED)
                    val infiniteTransition = rememberInfiniteTransition(label = "ArrowAnimation")
                    val arrowTranslation by infiniteTransition.animateFloat(
                        initialValue = -2f,
                        targetValue = 6f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(1000, easing = LinearEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "ArrowSlide"
                    )

                    OutlinedButton(
                        onClick = onSemuaLaguClick,
                        border = BorderStroke(1.5.dp, roleColor.copy(alpha = 0.4f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = roleColor),
                        shape = RoundedCornerShape(16.dp),
                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 0.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                    ) {
                        Text(
                            "Semua Lagu", 
                            color = Color.White,
                            fontSize = 16.sp, 
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = AppFont.Helvetica
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                            tint = Color.White,
                            contentDescription = null,
                            modifier = Modifier
                                .size(26.dp)
                                .graphicsLayer(translationX = arrowTranslation)
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
fun PreviewCombinedCardLuxury() {
    MaterialTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            UploaderBoxAndArtist(
                artist = Artist(
                    name = "Nadin Amizah", 
                    description = "Nadin Amizah adalah penyanyi dan penulis lagu berkebangsaan Indonesia. Musiknya dikenal puitis dan menyentuh hati. Album pertamanya 'Selamat Ulang Tahun' mendapat sambutan hangat. Ia mulai dikenal publik saat masih di bangku SMA."
                ),
                uploader = User(
                    displayName = "Remusic Admin",
                    role = "owner"
                ),
                isFollowed = true,
                onToggleFollow = {}
            )
        }
    }
}