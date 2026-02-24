package com.example.remusic.ui.components.playlist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
import com.example.remusic.ui.theme.AppFont
import com.example.remusic.viewmodel.playmusic.PlayMusicViewModel
import com.example.remusic.viewmodel.playmusic.PlayerUiState
import com.example.remusic.ui.screen.SortOrder

@Composable
fun UserPlaylistHeader(
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
    playlistId: String? = null
) {
    val playlist = uiState?.currentPlaylistDetails
    val owner = uiState?.playlistOwner
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 50.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 1. Artwork (ANIMATED)
        val imageUrl = playlist?.coverUrl?.takeIf { it.isNotBlank() }
            ?: playlistCoverUrl.ifBlank { if (songs.isNotEmpty()) songs[0].song.coverUrl else "" }
        AsyncImage(
            model = imageUrl,
            contentDescription = "Playlist Poster",
            modifier = Modifier
                .fillMaxWidth(0.75f)
                .aspectRatio(1f)
                .graphicsLayer {
                    // ANIMATION LOGIC: Shrink/Fade ONLY Image
                    alpha = 1f - imageCollapseProgress
                    val scale = 1f - (imageCollapseProgress * 0.5f) // Shrink to 50%
                    scaleX = scale
                    scaleY = scale
                    translationY = -imageCollapseProgress * 100f
                }
                .shadow(elevation = 12.dp, shape = RoundedCornerShape(12.dp), clip = false)
                .clip(RoundedCornerShape(12.dp)),
            contentScale = ContentScale.Crop,
            placeholder = painterResource(id = R.drawable.img_placeholder),
            error = painterResource(id = R.drawable.img_placeholder)
        )

        Spacer(modifier = Modifier.height(24.dp))

        // 2. Playlist Info Wrapper (Left Aligned for User Playlist)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.Start
        ) {
            // Title
            Text(
                text = playlist?.title ?: playlistName,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                fontFamily = AppFont.Poppins,
                fontWeight = FontWeight.Bold,
                fontSize = 28.sp,
                color = Color.White,
            )
            
            Spacer(modifier = Modifier.height(12.dp))

            // User Profile Row
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    model = owner?.photoUrl ?: "https://i.pravatar.cc/100", // Fallback avatar
                    contentDescription = "Owner Avatar",
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = owner?.displayName ?: "Unknown User",
                    fontFamily = AppFont.Poppins,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Meta Data Row (Privacy, Song Count, Duration)
            Row(verticalAlignment = Alignment.CenterVertically) {
                val isPublic = (playlist?.visibility ?: "public").equals("public", ignoreCase = true)
                Icon(
                    imageVector = if (isPublic) Icons.Default.Public else Icons.Default.Lock, // Add import for Lock if needed, assuming it's imported or will compile
                    contentDescription = "Privacy",
                    tint = Color.White.copy(0.7f),
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = playlist?.visibility?.replaceFirstChar { it.uppercase() } ?: "Public",
                    fontFamily = AppFont.Poppins,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                    color = Color.White.copy(0.7f)
                )
                
                Spacer(modifier = Modifier.width(8.dp))
                Box(modifier = Modifier.size(4.dp).clip(CircleShape).background(Color.White.copy(0.5f)))
                Spacer(modifier = Modifier.width(8.dp))
                
                Text(
                    text = "${songs.size} Lagu",
                    fontFamily = AppFont.Poppins,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                    color = Color.White.copy(0.7f)
                )
                
                Spacer(modifier = Modifier.width(8.dp))
                Box(modifier = Modifier.size(4.dp).clip(CircleShape).background(Color.White.copy(0.5f)))
                Spacer(modifier = Modifier.width(8.dp))
                
                Text(
                    text = formattedTotalDuration,
                    fontFamily = AppFont.Poppins,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                    color = Color.White.copy(0.7f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Description
            val description = playlist?.description
            if (!description.isNullOrBlank()) {
                Text(
                    text = description!!,
                    fontFamily = AppFont.Helvetica,
                    fontSize = 13.sp,
                    color = Color.White.copy(0.8f),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(24.dp))
            } else {
                 Spacer(modifier = Modifier.height(12.dp)) // If no description, maintain some spacing before buttons
            }

            // 3. Action Buttons Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Left Side Actions: Add Song, Download, More
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Add Song Button
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(0.1f))
                            .clickable { /* border radius for click effect */ },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add Song",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Download Button
                    IconButton(
                        onClick = { /* TODO: Download */ },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = "Download",
                            tint = Color.White.copy(0.7f),
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    // Options Button
                    IconButton(
                        onClick = { /* TODO: Options */ },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Options",
                            tint = Color.White.copy(0.7f),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                // Right Side Actions: Shuffle & Play
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Shuffle Button
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(0.1f))
                            .clickable {
                                if (sortedSongs.isNotEmpty()) {
                                    val randomIndex = sortedSongs.indices.random()
                                    playMusicViewModel?.playingMusicFromPlaylist(playlistName)
                                    playMusicViewModel?.setPlaylist(sortedSongs, randomIndex)
                                    playMusicViewModel?.forceSetShuffle(true)
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Shuffle,
                            contentDescription = "Shuffle",
                            tint = if (uiState?.isShuffleModeEnabled == true) Color(0xFF1DB954) else Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    // Play Button
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .shadow(12.dp, CircleShape)
                            .clip(CircleShape)
                            .background(Color.White)
                            .clickable {
                                if (filteredAndSortedSongs.isNotEmpty()) {
                                    playMusicViewModel?.playingMusicFromPlaylist(playlistName)
                                    playMusicViewModel?.setPlaylist(filteredAndSortedSongs, 0)
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = "Play",
                            tint = Color.Black,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Sort Row (Below actions)
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

                DropdownMenu(
                    expanded = showSortMenu,
                    onDismissRequest = onSortMenuDismiss,
                    modifier = Modifier.background(Color(0xFF282828)),
                    offset = androidx.compose.ui.unit.DpOffset(0.dp, 8.dp)
                ) {
                    SortOrder.entries.forEach { sortOption ->
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
    }
}
