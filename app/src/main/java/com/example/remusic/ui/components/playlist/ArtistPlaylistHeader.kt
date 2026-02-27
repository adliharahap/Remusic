package com.example.remusic.ui.components.playlist

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.remusic.R
import com.example.remusic.data.model.SongWithArtist
import com.example.remusic.ui.components.shimmerEffect
import com.example.remusic.ui.screen.SortOrder
import com.example.remusic.ui.theme.AppFont
import com.example.remusic.viewmodel.playmusic.PlayMusicViewModel
import com.example.remusic.viewmodel.playmusic.PlayerUiState

@Composable
fun ArtistPlaylistHeader(
    songs: List<SongWithArtist>,
    playlistName: String,
    playlistCoverUrl: String,
    formattedTotalDuration: String,
    imageCollapseProgress: Float,
    currentSort: SortOrder,
    showSortMenu: Boolean,
    onSortMenuDismiss: () -> Unit,
    onSortOptionSelected: (SortOrder) -> Unit,
    onSortClick: () -> Unit,
    playMusicViewModel: PlayMusicViewModel?,
    uiState: PlayerUiState?,
    sortedSongs: List<SongWithArtist>,
    filteredAndSortedSongs: List<SongWithArtist>,
    artistId: String? = null
) {
    // 1. Trigger Fetching if needed
    val targetArtistId = artistId ?: songs.firstOrNull()?.artist?.id

    LaunchedEffect(targetArtistId) {
        if (targetArtistId != null && playMusicViewModel != null) {
            // Only fetch if data is missing or different
            if (uiState?.artistDetails?.id != targetArtistId) {
                playMusicViewModel.fetchArtistDetailsForHeader(targetArtistId)
            }
        }
    }

    val artistDetails = uiState?.artistDetails
    val isLoading = uiState?.isLoadingArtistDetails == true

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 70.dp, bottom = 8.dp), // Margin atas diperbesar agar tidak menabrak status bar
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (isLoading || artistDetails == null) {
            // Menggunakan Skeleton Loading
            ArtistHeaderSkeleton()
        } else {
            // --- REAL CONTENT ---
            
            // 1. Artist Photo (Circle)
            AsyncImage(
                model = artistDetails.photoUrl ?: playlistCoverUrl,
                contentDescription = "Artist Photo",
                modifier = Modifier
                    .size(180.dp)
                    .shadow(16.dp, CircleShape, spotColor = Color.Black)
                    .clip(CircleShape)
                    .border(2.dp, Color.White.copy(alpha = 0.1f), CircleShape)
                    .background(Color(0xFF1E1E1E)),
                contentScale = ContentScale.Crop,
                placeholder = painterResource(id = R.drawable.img_placeholder),
                error = painterResource(id = R.drawable.img_placeholder)
            )

            Spacer(modifier = Modifier.height(20.dp))

            // 2. Artist Name with Verified Badge
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.padding(horizontal = 24.dp)
            ) {
                Text(
                    text = artistDetails.name,
                    fontFamily = AppFont.Poppins,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 32.sp,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.width(6.dp))
                // Verified Badge
                Icon(
                    imageVector = Icons.Filled.Verified,
                    contentDescription = "Verified Artist",
                    tint = Color(0xFF1DB954), // Spotify Green
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 3. Stats Row (Dipercantik dengan bentuk Pill / Badge)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.08f))
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                StatItem(formatCount(artistDetails.followerCount) + " Pengikut")
                DotSeparator()
                StatItem("${artistDetails.totalSongs} Lagu")
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "Diputar ${formatCount(artistDetails.totalPlays)} kali",
                fontFamily = AppFont.Helvetica,
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp,
                color = Color.White.copy(0.4f)
            )

            Spacer(modifier = Modifier.height(28.dp))

            // 4. Action Buttons (Follow, Shuffle, Play)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // FOLLOW BUTTON (Kiri)
                val isFollowed = artistDetails.isFollowed
                Button(
                    onClick = { playMusicViewModel?.toggleFollowFromHeader(artistDetails.id) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isFollowed) Color.White.copy(alpha = 0.1f) else Color.Transparent,
                        contentColor = Color.White
                    ),
                    border = if (isFollowed) null else BorderStroke(1.dp, Color.White.copy(alpha = 0.5f)),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Icon(
                        imageVector = if (isFollowed) Icons.Filled.Check else Icons.Filled.PersonAdd,
                        contentDescription = if (isFollowed) "Unfollow" else "Follow",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isFollowed) "Mengikuti" else "Ikuti",
                        color = Color.White,
                        fontFamily = AppFont.Helvetica,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }

                // Shuffle & Play Buttons (Kanan)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Shuffle Button (Dipertegas dengan background semi-transparan)
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.15f)) // Background ditambahkan
                            .clickable {
                                if (sortedSongs.isNotEmpty()) {
                                    if (uiState?.isShuffleModeEnabled == false) {
                                        playMusicViewModel?.toggleShuffleMode()
                                    }
                                    val randomIndex = sortedSongs.indices.random()
                                    
                                    val effectivePlaylistName = if (!artistDetails?.name.isNullOrBlank()) {
                                        artistDetails?.name ?: playlistName
                                    } else {
                                        playlistName
                                    }
                                    playMusicViewModel?.playingMusicFromPlaylist(effectivePlaylistName)
                                    playMusicViewModel?.setPlaylist(sortedSongs, randomIndex)
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Shuffle,
                            contentDescription = "Shuffle",
                            tint = if (uiState?.isShuffleModeEnabled == true) Color(0xFF1DB954) else Color.White, // Icon putih solid jika tidak aktif
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Play Button
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .shadow(12.dp, CircleShape, ambientColor = Color(0xFF1DB954), spotColor = Color(0xFF1DB954))
                            .clip(CircleShape)
                            .background(Color(0xFF1DB954))
                            .clickable {
                                if (filteredAndSortedSongs.isNotEmpty()) {
                                    val effectivePlaylistName = if (!artistDetails?.name.isNullOrBlank()) {
                                        artistDetails?.name ?: playlistName
                                    } else {
                                        playlistName
                                    }
                                    playMusicViewModel?.playingMusicFromPlaylist(effectivePlaylistName)
                                    playMusicViewModel?.setPlaylist(filteredAndSortedSongs, 0)
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = "Play All",
                            tint = Color.Black,
                            modifier = Modifier.size(32.dp).padding(start = 2.dp)
                        )
                    }
                }
            }

            // 5. Bio (Collapsible Glassmorphism Card) - Dipindah KE ATAS Sorting
            if (!artistDetails.description.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(24.dp))
                var isExpanded by remember { mutableStateOf(false) }
                
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White.copy(0.04f))
                        .clickable { isExpanded = !isExpanded }
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Tentang Artis",
                        fontFamily = AppFont.Poppins,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = Color.White.copy(alpha = 0.9f)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = artistDetails.description,
                        fontFamily = AppFont.Helvetica,
                        fontWeight = FontWeight.Normal,
                        fontSize = 13.sp,
                        color = Color.White.copy(0.6f),
                        maxLines = if (isExpanded) Int.MAX_VALUE else 3,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 18.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 6. Sorting Row - Dipindah KE BAWAH agar langsung menempel dengan daftar lagu
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start
            ) {
                Box {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.White.copy(0.08f))
                            .clickable { onSortClick() }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = currentSort.icon,
                            contentDescription = "Sort",
                            tint = Color.White.copy(0.8f),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = currentSort.SdisplayName,
                            color = Color.White.copy(0.9f),
                            fontFamily = AppFont.Helvetica,
                            fontWeight = FontWeight.Medium,
                            fontSize = 12.sp
                        )
                    }

                    // Sort Menu
                    DropdownMenu(
                        expanded = showSortMenu,
                        onDismissRequest = onSortMenuDismiss,
                        modifier = Modifier.background(Color(0xFF282828)),
                        offset = androidx.compose.ui.unit.DpOffset(0.dp, 8.dp)
                    ) {
                        SortOrder.entries.filter { 
                            it != SortOrder.ARTIST_ASC && it != SortOrder.ARTIST_DESC 
                        }.forEach { sortOption ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        sortOption.SdisplayName,
                                        color = Color.White,
                                        fontFamily = AppFont.Helvetica
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = sortOption.icon,
                                        contentDescription = sortOption.SdisplayName,
                                        tint = Color.White.copy(0.8f)
                                    )
                                },
                                onClick = { onSortOptionSelected(sortOption) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatItem(text: String) {
    Text(
        text = text,
        fontFamily = AppFont.Helvetica,
        fontWeight = FontWeight.Bold, // Dipertebal agar lebih kontras di dalam Badge
        fontSize = 13.sp,
        color = Color.White.copy(0.9f)
    )
}

@Composable
private fun DotSeparator() {
    Text(
        text = " • ",
        color = Color.White.copy(0.4f),
        fontSize = 13.sp,
        modifier = Modifier.padding(horizontal = 6.dp)
    )
}

@Composable
private fun ArtistHeaderSkeleton() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth().shimmerEffect()
    ) {
        // Photo
        Box(
            modifier = Modifier
                .size(180.dp)
                .clip(CircleShape)
                .background(Color.White.copy(0.05f))
        )
        Spacer(modifier = Modifier.height(20.dp))
        
        // Name
        Box(
            modifier = Modifier
                .width(220.dp)
                .height(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White.copy(0.05f))
        )
        Spacer(modifier = Modifier.height(12.dp))
        
        // Stats
        Box(
            modifier = Modifier
                .width(160.dp)
                .height(28.dp) // Diperbesar menyesuaikan bentuk Pill
                .clip(RoundedCornerShape(50))
                .background(Color.White.copy(0.05f))
        )
        
        Spacer(modifier = Modifier.height(28.dp))
        
        // Buttons Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Follow skeleton
            Box(
                modifier = Modifier
                    .width(110.dp)
                    .height(36.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.White.copy(0.05f))
            )
            
            // Play/Shuffle skeleton
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(0.05f))
                )
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(0.05f))
                )
            }
        }
    }
}

fun formatCount(count: Long): String {
    if (count < 1000) return count.toString()
    val exp = (Math.log(count.toDouble()) / Math.log(1000.0)).toInt()
    return String.format("%.1f%c", count / Math.pow(1000.0, exp.toDouble()), "kMGTPE"[exp - 1])
}