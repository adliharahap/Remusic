package com.example.remusic.ui.components.playlist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
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
fun AutoPlaylistHeader(
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
    filteredAndSortedSongs: List<SongWithArtist>
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 50.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 1. Artwork (ANIMATED)
        val imageUrl = if (songs.isNotEmpty()) songs[0].song.coverUrl else playlistCoverUrl
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

        // 2. Title & Meta (STATIC - No Animation)
        Text(
            text = playlistName,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontFamily = AppFont.Poppins,
            fontWeight = FontWeight.Bold,
            fontSize = 28.sp,
            color = Color.White,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))

        // Meta Data Row
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "${songs.size} Lagu",
                fontFamily = AppFont.Poppins,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp,
                color = Color.White.copy(0.7f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(4.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(0.5f))
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = formattedTotalDuration,
                fontFamily = AppFont.Poppins,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp,
                color = Color.White.copy(0.7f)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 3. Action Buttons Row (REDESIGNED - Pro Layout)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // A. SORT BUTTON (Left - Chip Style with Text)
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

            // B. PLAY & SHUFFLE (Right - Grouped & Larger)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Shuffle Button (Large Circle - Secondary)
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(0.1f))
                        .clickable {
                            // Fix: Start from a random index instead of 0
                            if (sortedSongs.isNotEmpty()) {
                                // Pick a random index to start from
                                val randomIndex = sortedSongs.indices.random()

                                // 🔥 FIX BUG SHUFFLE SKIP:
                                // setPlaylist DULU dengan index random,
                                // BARU aktifkan shuffle SETELAH player siap.
                                // Jika shuffle diaktifkan SEBELUM setPlaylist,
                                // ExoPlayer akan shuffle ke lagu lain yang URLnya masih kosong.
                                playMusicViewModel?.playingMusicFromPlaylist(playlistName)
                                playMusicViewModel?.setPlaylist(sortedSongs, randomIndex)

                                // 🔥 FIX RACE CONDITION:
                                // Gunakan forceSetShuffle(true) bukan toggleShuffleMode()!
                                // toggleShuffleMode() membaca uiState yang masih STALE di Compose snapshot
                                // saat tombol diklik, menyebabkan toggle bisa jadi OFF bukan ON.
                                // forceSetShuffle(true) langsung set ke mediaController tanpa cek state.
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

                // Play Button (Large Circle - Primary/White)
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
                        contentDescription = "Play All",
                        tint = Color.Black,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        }
    }
}
