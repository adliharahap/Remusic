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
import com.example.remusic.utils.toHdUrl
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
    artistNameFallback: String? = null,
    uploader: User?,
    isFollowed: Boolean = false,
    onToggleFollow: () -> Unit = {},
    onLihatPlaylistClick: () -> Unit = {}, // NEW: Navigate to artist playlist
    onSemuaLaguClick: () -> Unit = {},      // NEW: Navigate to all songs (same as artist playlist)
    onUploaderClick: () -> Unit = {}
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
        val artistName = currentArtist?.name ?: artistNameFallback ?: "Unknown Artist"
        val artistPhoto = currentArtist?.photoUrl?.toHdUrl() ?: "https://bnqhatesnqukmsbjaulm.supabase.co/storage/v1/object/public/Remusic%20Storage/unknownUploadAndArtist/denia.jpeg"
        val artistDesc = currentArtist?.description ?: "No description available for this artist."

        val uploaderName = currentUploader?.displayName ?: "Unknown Uploader"
        val uploaderPhoto = currentUploader?.photoUrl?.toHdUrl() ?: "https://bnqhatesnqukmsbjaulm.supabase.co/storage/v1/object/public/Remusic%20Storage/unknownUploadAndArtist/avatar_1775118109127.jpg"
        val uploaderRole = currentUploader?.role?.replaceFirstChar { it.uppercase() } ?: "Uploader"

        // LUXURY DECORATION: Role-based logic
        val isOwner = currentUploader?.role?.lowercase() == "owner" || currentUploader?.role?.lowercase() == "admin"
        val isUploaderRole = currentUploader?.role?.lowercase() == "uploader"
        val roleColor = when {
            isOwner -> Color(0xFFFFD700) // Gold
            isUploaderRole -> Color(0xFF3897F0) // Blue
            else -> Color(0xFF1DB954) // Green
        }

        // CONTAINER UTAMA
        Card(
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF161616).copy(alpha = 0.95f) // Warna lebih solid & elegan
            ),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
                .border(
                    width = 1.dp,
                    brush = Brush.linearGradient(
                        listOf(Color.White.copy(alpha = 0.1f), Color.Transparent)
                    ),
                    shape = RoundedCornerShape(24.dp)
                )
        ) {
            Column {
                // ================= BAGIAN 1: ARTIST (REDESIGNED SLEEK) =================
                Column(
                    modifier = Modifier.padding(18.dp) // Padding disesuaikan agar proporsional
                ) {
                    // Gambar Artist (1:1 Ratio, Perfect Square)
                    AsyncImage(
                        model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                            .data(artistPhoto)
                            .crossfade(true)
                            .build(),
                        contentDescription = "Artist Image",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f) // MEMAKSA RASIO 1:1 SESUAI DATABASE
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.DarkGray)
                            .border(
                                1.dp,
                                Color.White.copy(alpha = 0.1f),
                                RoundedCornerShape(16.dp)
                            )
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // Nama Artist + Verified (Mencegah icon mengecil)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = artistName,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = AppFont.HelveticaRoundedBold,
                            color = Color.White,
                            fontSize = 22.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false) // Teks ngalah jadi ...
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Filled.Verified,
                            contentDescription = "Verified",
                            tint = Color(0xFF1DB954),
                            modifier = Modifier.requiredSize(22.dp) // Pastikan ukuran icon tetap
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Button Row (Ramping, Minimalis, Tidak Stretched Kanan Kiri)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp) // Jarak natural antar button
                    ) {
                        // Button Follow / Mengikuti (Primary Action ditaruh kiri)
                        if (isFollowed) {
                            OutlinedButton(
                                onClick = onToggleFollow,
                                border = BorderStroke(1.dp, Color(0xFF1DB954).copy(alpha = 0.5f)),
                                shape = RoundedCornerShape(50),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                                modifier = Modifier.height(36.dp), // Lebih ramping (sleek)
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = Color(0xFF1DB954)
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = "Followed",
                                    modifier = Modifier.size(16.dp),
                                    tint = Color(0xFF1DB954)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Diikuti", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            Button(
                                onClick = onToggleFollow,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.White,
                                    contentColor = Color.Black
                                ),
                                shape = RoundedCornerShape(50),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                                modifier = Modifier.height(36.dp) // Lebih ramping
                            ) {
                                Text("Ikuti", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        // Button Lihat Playlist (Secondary Action)
                        OutlinedButton(
                            onClick = onLihatPlaylistClick,
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f)),
                            shape = RoundedCornerShape(50),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                            modifier = Modifier.height(36.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                        ) {
                            Text("Lihat Playlist", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Deskripsi (Lebih lega dengan line-height yang nyaman)
                    var isExpanded by remember { mutableStateOf(false) }
                    
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateContentSize(animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing))
                    ) {
                        Text(
                            text = artistDesc,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                lineHeight = 24.sp, // Memberikan breathing room pada teks
                                letterSpacing = 0.2.sp
                            ),
                            fontFamily = AppFont.Helvetica,
                            color = Color.White.copy(alpha = 0.7f),
                            maxLines = if (isExpanded) Int.MAX_VALUE else 3,
                            overflow = TextOverflow.Ellipsis,
                            fontSize = 14.sp,
                            modifier = Modifier.fillMaxWidth()
                        )
                        
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        Text(
                            text = if (isExpanded) "Sembunyikan" else "Selengkapnya",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White, // Putih agar lebih netral dan elegan
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

                // ================= BAGIAN 2: UPLOADER (LEBIH BALANCE) =================
                Column(
                    modifier = Modifier
                        .padding(horizontal = 18.dp, vertical = 16.dp)
                        .fillMaxWidth()
                ) {
                    Text(
                        text = "Diupload oleh",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.5f),
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.5.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (currentUploader?.uid != "youtube" && currentUploader?.uid != null) {
                                    Modifier.clickable { onUploaderClick() }
                                } else Modifier
                            ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp) // Diperkecil sedikit agar tidak mendominasi
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
                                            .padding(2.dp)
                                    } else if (isUploaderRole) {
                                        Modifier
                                            .background(Color(0xFF3897F0))
                                            .padding(2.dp)
                                    } else {
                                        Modifier
                                            .background(Color.White.copy(alpha = 0.15f))
                                            .padding(1.dp)
                                    }
                                )
                                .clip(CircleShape)
                        ) {
                            AsyncImage(
                                model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                                    .data(uploaderPhoto)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = "Uploader Image",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = uploaderName,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = AppFont.HelveticaRoundedBold,
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Filled.CheckCircle,
                                    contentDescription = "Uploader Verified",
                                    tint = roleColor,
                                    modifier = Modifier
                                        .offset(y = 1.dp)
                                        .requiredSize(16.dp)
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(2.dp))
                            
                            if (isOwner) {
                                WavyText(
                                    text = "Founder Of Remusic",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontFamily = AppFont.Helvetica,
                                        letterSpacing = 1.5.sp,
                                        fontSize = 12.sp
                                    ),
                                    color = roleColor
                                )
                            } else {
                                Text(
                                    text = uploaderRole,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = roleColor,
                                    letterSpacing = 1.sp,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
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
                    name = "Nadin Amizah dan Teman-temannya yang asik sekali",
                    description = "Nadin Amizah adalah penyanyi dan penulis lagu berkebangsaan Indonesia. Musiknya dikenal puitis dan menyentuh hati. Album pertamanya 'Selamat Ulang Tahun' mendapat sambutan hangat. Ia mulai dikenal publik saat masih di bangku SMA."
                ),
                uploader = User(
                    displayName = "Remusic Admin Super Long Name Sekali Dong",
                    role = "owner"
                ),
                isFollowed = true,
                onToggleFollow = {}
            )
        }
    }
}