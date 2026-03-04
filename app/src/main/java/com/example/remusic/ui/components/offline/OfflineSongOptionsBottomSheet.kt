package com.example.remusic.ui.components.offline

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.remusic.R
import com.example.remusic.data.model.SongWithArtist
import com.example.remusic.ui.theme.AppFont
import com.example.remusic.utils.formatDuration

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfflineSongOptionsBottomSheet(
    song: SongWithArtist,
    onDismiss: () -> Unit,
    onAddToQueue: (SongWithArtist) -> Unit,
    onPlayNext: (SongWithArtist) -> Unit,
    onDeleteFromDevice: (SongWithArtist) -> Unit
) {
    var showDetails by remember { mutableStateOf(false) }
    
    val sheetBg    = Color(0xFF101010)
    val divider    = Color.White.copy(0.07f)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = sheetBg,
        contentColor = Color.White,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 4.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(0.25f))
            )
        }
    ) {
        if (showDetails) {
            OfflineSongDetailsContent(song = song, onBack = { showDetails = false })
        } else {
            Column(modifier = Modifier.fillMaxWidth()) {

                // ── Song Header ────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = song.song.coverUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(10.dp)),
                    contentScale = ContentScale.Crop,
                    placeholder = painterResource(R.drawable.default_playlist),
                    error       = painterResource(R.drawable.default_playlist)
                )
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = song.song.title,
                        color = Color.White,
                        fontFamily = AppFont.Poppins,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = song.artist?.name ?: "Unknown Artist",
                        color = Color.White.copy(0.6f),
                        fontFamily = AppFont.Helvetica,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            HorizontalDivider(color = divider, thickness = 0.5.dp)
            Spacer(Modifier.height(8.dp))

            // ── Action Items ───────────────────────────────────────────────
            OfflineOptionItem(
                icon = Icons.Outlined.QueueMusic,
                label = "Tambahkan ke Antrean",
                onClick = { onAddToQueue(song); onDismiss() }
            )
            OfflineOptionItem(
                icon = Icons.Outlined.SkipNext,
                label = "Putar setelah ini",
                onClick = { onPlayNext(song); onDismiss() }
            )

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                color = divider, thickness = 0.5.dp
            )

            OfflineOptionItem(
                icon = Icons.Outlined.Info,
                label = "Rincian Lagu",
                onClick = { showDetails = true }
            )
            OfflineOptionItem(
                icon = Icons.Outlined.Delete,
                label = "Hapus dari Perangkat",
                tint = Color(0xFFEF5350),  // red accent for destructive action
                onClick = { onDeleteFromDevice(song); onDismiss() }
            )

            Spacer(Modifier.height(24.dp))
        }
    }
}
}


@Composable
fun OfflineOptionItem(
    icon: ImageVector,
    label: String,
    tint: Color = Color.White.copy(0.85f),
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.width(16.dp))
        Text(
            text = label,
            color = tint,
            fontFamily = AppFont.Helvetica,
            fontWeight = FontWeight.Normal,
            fontSize = 15.sp
        )
    }
}

@Composable
fun OfflineSongDetailsContent(song: SongWithArtist, onBack: () -> Unit) {
    val divider = Color.White.copy(0.07f)

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        // Toolbar
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Outlined.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Spacer(Modifier.width(16.dp))
            Text(
                text = "Rincian Lagu",
                color = Color.White,
                fontFamily = AppFont.Poppins,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp
            )
        }

        Spacer(Modifier.height(12.dp))

        DetailRow(label = "Judul", value = song.song.title)
        HorizontalDivider(color = divider, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 12.dp))

        DetailRow(label = "Artis", value = song.artist?.name ?: "Unknown Artist")
        HorizontalDivider(color = divider, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 12.dp))


        DetailRow(label = "Durasi", value = formatDuration(song.song.durationMs))
        HorizontalDivider(color = divider, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 12.dp))

        DetailRow(label = "ID Lagu", value = song.song.id)
        HorizontalDivider(color = divider, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 12.dp))

        DetailRow(
            label = "Lokasi File", 
            value = (song.song.audioUrl ?: "").removePrefix("file://").ifBlank { "Unknown path" }
        )

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
fun DetailRow(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            color = Color.White.copy(0.5f),
            fontFamily = AppFont.Helvetica,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = value,
            color = Color.White.copy(0.9f),
            fontFamily = AppFont.Helvetica,
            fontSize = 14.sp
        )
    }
}
