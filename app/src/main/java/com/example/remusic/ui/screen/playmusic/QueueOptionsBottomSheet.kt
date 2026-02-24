package com.example.remusic.ui.screen.playmusic

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.PlaylistAdd
import androidx.compose.material.icons.automirrored.outlined.QueueMusic
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.remusic.R
import com.example.remusic.data.model.SongWithArtist
import com.example.remusic.data.model.displayArtistName
import com.example.remusic.ui.theme.AppFont

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueOptionsBottomSheet(
    sheetState: SheetState,
    songWithArtist: SongWithArtist?,
    onDismiss: () -> Unit,
    // Callbacks
    onShare: () -> Unit = {},
    onAddToPlaylist: () -> Unit = {},
    onAddToLiked: () -> Unit = {},
    onDownload: () -> Unit = {},
    onAddToQueue: () -> Unit = {}, // Add to end of queue
    onPlayNext: () -> Unit = {},   // Add after current song
    showRemoveFromPlaylist: Boolean = false,
    onRemoveFromPlaylist: () -> Unit = {},
    showRemoveFromQueue: Boolean = false,
    onRemoveFromQueue: () -> Unit = {}
) {
    val context = LocalContext.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF1E1E1E),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .width(40.dp)
                    .height(4.dp)
                    .background(Color.White.copy(0.3f), RoundedCornerShape(2.dp))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 30.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // --- HEADER ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Album Art
                AsyncImage(
                    model = songWithArtist?.song?.coverUrl,
                    contentDescription = "Song Cover",
                    modifier = Modifier
                        .size(56.dp)
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop,
                    placeholder = painterResource(R.drawable.img_placeholder),
                    error = painterResource(R.drawable.img_placeholder)
                )

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    // Song Title via Marquee
                    Text(
                        text = songWithArtist?.song?.title ?: "Unknown Title",
                        color = Color.White,
                        fontFamily = AppFont.Coolvetica,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        modifier = Modifier
                            .fillMaxWidth()
                            .basicMarquee(
                                animationMode = androidx.compose.foundation.MarqueeAnimationMode.Immediately,
                                velocity = 70.dp,
                                initialDelayMillis = 2000,
                                repeatDelayMillis = 5000,
                                iterations = Int.MAX_VALUE
                            )
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = songWithArtist?.displayArtistName ?: "Unknown Artist",
                        color = Color.White.copy(0.7f),
                        fontFamily = AppFont.Helvetica,
                        fontWeight = FontWeight.Normal,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            HorizontalDivider(
                modifier = Modifier.fillMaxWidth(),
                thickness = 1.2.dp,
                color = Color.White.copy(alpha = 0.1f)
            )

            Spacer(modifier = Modifier.height(10.dp))

            // --- MENU OPTIONS ---
            
            // 1. Play Next
            QueueMenuOptionItem(
                title = "Putar setelah ini",
                icon = Icons.Outlined.SkipNext,
                onClick = onPlayNext
            )

            // 2. Add to Queue
            QueueMenuOptionItem(
                title = "Tambahkan ke antrean",
                icon = Icons.AutoMirrored.Outlined.QueueMusic,
                onClick = onAddToQueue
            )

            // 3. Add to Playlist
            QueueMenuOptionItem(
                title = "Tambahkan ke playlist",
                icon = Icons.AutoMirrored.Outlined.PlaylistAdd,
                onClick = onAddToPlaylist
            )

            // 3.5 Remove from Playlist (Conditional)
            if (showRemoveFromPlaylist) {
                QueueMenuOptionItem(
                    title = "Hapus dari playlist",
                    icon = Icons.Outlined.Delete,
                    onClick = onRemoveFromPlaylist
                )
            }

            // 3.6 Remove from Queue (Conditional)
            if (showRemoveFromQueue) {
                QueueMenuOptionItem(
                    title = "Hapus dari antrean",
                    icon = Icons.Outlined.Delete, // Or another icon
                    onClick = onRemoveFromQueue
                )
            }

            // 4. Add to Liked
            QueueMenuOptionItem(
                title = "Tambahkan ke Lagu yang Disukai",
                icon = Icons.Outlined.FavoriteBorder,
                onClick = onAddToLiked
            )

            // 5. Download
            QueueMenuOptionItem(
                title = "Unduh",
                icon = Icons.Outlined.Download,
                onClick = {
                    Toast.makeText(context, "Fitur belum tersedia", Toast.LENGTH_SHORT).show()
                }
            )
            
            // 6. Share
            QueueMenuOptionItem(
                title = "Bagikan",
                icon = Icons.Outlined.Share,
                onClick = {
                    Toast.makeText(context, "Fitur belum tersedia", Toast.LENGTH_SHORT).show()
                }
            )

            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

@Composable
fun QueueMenuOptionItem(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(20.dp))
        Text(
            text = title,
            color = Color.White,
            fontFamily = AppFont.Helvetica,
            fontSize = 16.sp
        )
    }
}
