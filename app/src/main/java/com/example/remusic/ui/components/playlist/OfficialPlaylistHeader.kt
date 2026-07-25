package com.example.remusic.ui.components.playlist

import com.example.remusic.viewmodel.playmusic.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Verified
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
fun OfficialPlaylistHeader(
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
            contentDescription = "Official Playlist Poster",
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
                .shadow(elevation = 16.dp, shape = RoundedCornerShape(16.dp), clip = false)
                .clip(RoundedCornerShape(16.dp)),
            contentScale = ContentScale.Crop,
            placeholder = painterResource(id = R.drawable.img_placeholder),
            error = painterResource(id = R.drawable.img_placeholder)
        )

        Spacer(modifier = Modifier.height(24.dp))

        // 2. Playlist Info Wrapper (Left Aligned)
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
                fontWeight = FontWeight.ExtraBold,
                fontSize = 30.sp,
                color = Color.White,
            )
            
            Spacer(modifier = Modifier.height(12.dp))

            // Remusic Official Brand Row
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Tentukan status kepemilikan playlist (User/Uploader/Official)
                val isMyPlaylist = playlist?.ownerUserId != null && playlist.ownerUserId == com.example.remusic.data.UserManager.currentUser?.uid
                val isUploaderPlaylist = playlist?.ownerUserId != null && playlist.ownerUserId == uiState?.playlistOwner?.uid

                // Dapatkan nama creator
                val creatorName = when {
                    isMyPlaylist -> com.example.remusic.data.UserManager.currentUser?.displayName ?: "Unknown User"
                    isUploaderPlaylist -> uiState?.playlistOwner?.displayName ?: "Unknown User"
                    playlist != null && uiState?.playlistOwner != null && uiState.playlistOwner.displayName?.isNotBlank() == true -> uiState.playlistOwner.displayName
                    playlist != null && playlist.ownerUserId?.isNotBlank() == true && !playlist.ownerUserId.startsWith("UC") -> playlist.ownerUserId // Fallback ke ID
                    uiState?.artistDetails?.name?.isNotBlank() == true -> uiState.artistDetails.name // Menggunakan nama dari ArtistDetails jika ada
                    songs.isNotEmpty() && songs.firstOrNull()?.artist?.name?.isNotBlank() == true -> songs.first().artist!!.name
                    else -> "Remusic Official"
                }

                // Dapatkan avatar creator
                val creatorAvatarUrl = when {
                    isMyPlaylist -> com.example.remusic.data.UserManager.currentUser?.photoUrl
                    isUploaderPlaylist -> uiState?.playlistOwner?.photoUrl
                    playlist != null && uiState?.playlistOwner != null && uiState.playlistOwner.photoUrl?.isNotBlank() == true -> uiState.playlistOwner.photoUrl
                    uiState?.artistDetails?.photoUrl?.isNotBlank() == true -> uiState.artistDetails.photoUrl // Prioritaskan dari ArtistDetails
                    songs.isNotEmpty() && songs.firstOrNull()?.artist?.photoUrl?.isNotBlank() == true -> songs.first().artist!!.photoUrl
                    else -> null
                }
                
                val defaultAnimeAvatar = "https://bnqhatesnqukmsbjaulm.supabase.co/storage/v1/object/public/Remusic%20Storage/unknownUploadAndArtist/denia.jpeg"

                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(Color.DarkGray),
                    contentAlignment = Alignment.Center
                ) {
                    if (!creatorAvatarUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = creatorAvatarUrl,
                            contentDescription = "Creator Avatar",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        // Gunakan default anime untuk YouTube Music, Remusic Official, atau user/artist tanpa foto
                        AsyncImage(
                            model = defaultAnimeAvatar,
                            contentDescription = "Default Anime Avatar",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = creatorName,
                    fontFamily = AppFont.Poppins,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = Color.White
                )
                Spacer(modifier = Modifier.width(4.dp))
                // Verified Badge
                if (creatorName == "Remusic Official" || playlist?.isOfficial == true) {
                    Icon(
                        imageVector = Icons.Default.Verified,
                        contentDescription = "Verified Official",
                        tint = Color(0xFF4CAF50), // Green verified or brand color
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Meta Data Row (Privacy, Song Count, Duration)
            Row(verticalAlignment = Alignment.CenterVertically) {
                val isPublic = (playlist?.visibility ?: "public").equals("public", ignoreCase = true)
                Icon(
                    imageVector = if (isPublic) Icons.Default.Public else Icons.Default.Lock,
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
            val description = playlist?.description ?: "Dengarkan kompilasi lagu terbaik pilihan editor Remusic, spesial untukmu!"
            if (description.isNotBlank()) {
                Text(
                    text = description,
                    fontFamily = AppFont.Helvetica,
                    fontSize = 13.sp,
                    color = Color.White.copy(0.8f),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(24.dp))
            } else {
                 Spacer(modifier = Modifier.height(12.dp)) 
            }

            // 3. Action Buttons Row (Right Aligned)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End
            ) {
                // Right Side Actions: Shuffle & Play FAB
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Shuffle Icon
                    Icon(
                        imageVector = Icons.Default.Shuffle,
                        contentDescription = "Acak",
                        tint = if (uiState?.isShuffleModeEnabled == true) Color(0xFF4CAF50) else Color.White,
                        modifier = Modifier
                            .size(28.dp)
                            .clickable { playMusicViewModel?.toggleShuffleMode() }
                    )

                    // Play Button (Large Scale)
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF4CAF50)) // Brand Green / Primary Color
                            .clickable {
                                if (filteredAndSortedSongs.isNotEmpty()) {
                                    val title = playlist?.title ?: playlistName
                                    playMusicViewModel?.playingMusicFromPlaylist(title)
                                    playMusicViewModel?.setPlaylist(
                                        songs = filteredAndSortedSongs,
                                        startIndex = 0
                                    )
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Putar",
                            tint = Color.Black,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }
            }
        }
    }
}
