package com.example.remusic.ui.components.playlist

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
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
    artistId: String? = null // NEW: Direct artist ID parameter
) {
    // 1. Trigger Fetching if needed
    // Use the passed artistId parameter instead of extracting from songs
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
            .padding(top = 50.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (isLoading || artistDetails == null) {
             Box(modifier = Modifier.fillMaxWidth().height(200.dp))
        } else {
            // --- REAL CONTENT ---
            
            // 1. Artist Photo (Circle)
            AsyncImage(
                model = artistDetails.photoUrl ?: playlistCoverUrl,
                contentDescription = "Artist Photo",
                modifier = Modifier
                    .size(180.dp)
                    .shadow(12.dp, CircleShape)
                    .clip(CircleShape)
                    .background(Color.DarkGray),
                contentScale = ContentScale.Crop,
                placeholder = painterResource(id = R.drawable.img_placeholder),
                error = painterResource(id = R.drawable.img_placeholder)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // 2. Artist Name with Verified Badge
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.padding(horizontal = 24.dp)
            ) {
                Text(
                    text = artistDetails.name,
                    fontFamily = AppFont.Poppins,
                    fontWeight = FontWeight.Bold,
                    fontSize = 28.sp,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.width(8.dp))
                // Verified Badge (Green checkmark)
                Icon(
                    imageVector = Icons.Filled.Verified,
                    contentDescription = "Verified Artist",
                    tint = Color(0xFF1DB954), // Spotify Green
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .padding(2.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 3. Stats Row (Followers • Songs • Plays)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                StatItem(formatCount(artistDetails.followerCount) + " Followers")
                DotSeparator()
                StatItem("${artistDetails.totalSongs} Lagu")
            }
             Spacer(modifier = Modifier.height(4.dp))
             Text(
                text = "Total ${formatCount(artistDetails.totalPlays)} Plays",
                fontFamily = AppFont.Poppins,
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp,
                color = Color.White.copy(0.5f)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // 4. Action Buttons (Follow, Shuffle, Play) - NEW LAYOUT
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // FOLLOW BUTTON (Left)
                val isFollowed = artistDetails.isFollowed
                Button(
                    onClick = { playMusicViewModel?.toggleFollowFromHeader(artistDetails.id) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isFollowed) Color.Transparent else Color.White,
                        contentColor = if (isFollowed) Color.White else Color.Black
                    ),
                    border = if (isFollowed) androidx.compose.foundation.BorderStroke(1.dp, Color.White) else null,
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 0.dp),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.height(40.dp)
                ) {
                    Icon(
                        imageVector = if (isFollowed) Icons.Filled.Check else Icons.Filled.PersonAdd,
                        contentDescription = if (isFollowed) "Unfollow" else "Follow",
                        tint = if (isFollowed) Color.White else Color.Black,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isFollowed) "Mengikuti" else "Ikuti",
                        color = if (isFollowed) Color.White else Color.Black,
                        fontFamily = AppFont.Poppins,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                }

                // Shuffle & Play Buttons (Right)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Shuffle Button (Medium - 48dp)
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .shadow(8.dp, CircleShape)
                            .clip(CircleShape)
                            .background(Color.White.copy(0.15f))
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
                            tint = if (uiState?.isShuffleModeEnabled == true) Color(0xFF1DB954) else Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    // Play Button (Large - 60dp, More Prominent)
                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .shadow(16.dp, CircleShape)
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
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 5. Sorting Row (NEW)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start
            ) {
                // Sort Button with Dropdown
                Box {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(0.1f))
                            .clickable { onSortClick() }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = currentSort.icon,
                            contentDescription = "Sort",
                            tint = Color.White.copy(0.9f),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = currentSort.SdisplayName,
                            color = Color.White.copy(0.9f),
                            fontFamily = AppFont.Poppins,
                            fontWeight = FontWeight.Medium,
                            fontSize = 13.sp
                        )
                    }

                    // Sort Menu (filtered for Artist - no ARTIST_ASC/DESC)
                    DropdownMenu(
                        expanded = showSortMenu,
                        onDismissRequest = onSortMenuDismiss,
                        modifier = Modifier.background(Color(0xFF282828)),
                        offset = androidx.compose.ui.unit.DpOffset(0.dp, 8.dp)
                    ) {
                        // Filter out ARTIST sorting options
                        SortOrder.entries.filter { 
                            it != SortOrder.ARTIST_ASC && it != SortOrder.ARTIST_DESC 
                        }.forEach { sortOption ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        sortOption.SdisplayName,
                                        color = Color.White,
                                        fontFamily = AppFont.Poppins
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

            // 6. Bio (Collapsible)
            if (!artistDetails.description.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(24.dp))
                var isExpanded by remember { mutableStateOf(false) }
                
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(0.05f))
                        .clickable { isExpanded = !isExpanded }
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Tentang Artis",
                         fontFamily = AppFont.Poppins,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = artistDetails.description,
                        fontFamily = AppFont.Poppins,
                        fontWeight = FontWeight.Normal,
                        fontSize = 13.sp,
                        color = Color.White.copy(0.7f),
                        maxLines = if (isExpanded) Int.MAX_VALUE else 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun StatItem(text: String) {
    Text(
        text = text,
        fontFamily = AppFont.Poppins,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        color = Color.White.copy(0.9f)
    )
}

@Composable
private fun DotSeparator() {
    Text(
        text = " • ",
        color = Color.White.copy(0.5f),
        fontSize = 13.sp
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
                .background(Color.White.copy(0.1f))
        )
        Spacer(modifier = Modifier.height(24.dp))
        
        // Name
        Box(
            modifier = Modifier
                .width(200.dp)
                .height(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White.copy(0.1f))
        )
        Spacer(modifier = Modifier.height(16.dp))
        
        // Stats
        Box(
            modifier = Modifier
                .width(150.dp)
                .height(16.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color.White.copy(0.1f))
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Buttons
        Row {
             Box(
                modifier = Modifier
                    .width(100.dp)
                    .height(42.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(0.1f))
            )
             Spacer(modifier = Modifier.width(16.dp))
              Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(0.1f))
            )
             Spacer(modifier = Modifier.width(16.dp))
              Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(0.1f))
            )
        }
    }
}

fun formatCount(count: Long): String {
    if (count < 1000) return count.toString()
    val exp = (Math.log(count.toDouble()) / Math.log(1000.0)).toInt()
    return String.format("%.1f%c", count / Math.pow(1000.0, exp.toDouble()), "kMGTPE"[exp - 1])
}
