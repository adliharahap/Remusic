package com.example.remusic.ui.components.uploadsongcomponent

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.remusic.R
import com.example.remusic.ui.theme.AppFont
import com.example.remusic.utils.AudioFile
import java.util.concurrent.TimeUnit

private fun formatDuration(millis: Int): String {
    val minutes = TimeUnit.MILLISECONDS.toMinutes(millis.toLong())
    val seconds = TimeUnit.MILLISECONDS.toSeconds(millis.toLong()) -
            TimeUnit.MINUTES.toSeconds(minutes)
    return String.format("%02d:%02d", minutes, seconds)
}

@Composable
fun SongCard(audioFile: AudioFile, showMoreOption: Boolean = false, onMoreClick: () -> Unit) {
    // Siapkan data dengan nilai default jika diperlukan
    val title = if (audioFile.title.equals("<unknown>", ignoreCase = true)) "Unknown Title" else audioFile.title
    val artist = if (audioFile.artist.equals("<unknown>", ignoreCase = true)) "Unknown Artist" else audioFile.artist
    val durationFormatted = formatDuration(audioFile.duration)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onMoreClick() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = audioFile.imageUrl,
            contentDescription = "Album Art",
            modifier = Modifier.size(50.dp).clip(RoundedCornerShape(6.dp)),
            contentScale = ContentScale.Crop,
            placeholder = painterResource(R.drawable.ic_music_note),
            error = painterResource(R.drawable.ic_music_note),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.Center
        ) {
            // Baris 1: Judul Lagu
            Text(
                text = title,
                color = Color.White,
                fontFamily = AppFont.RobotoMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 15.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            // Baris 2: Artis, Album, dan Tanggal
            Text(
                text = "$artist • ${audioFile.album} • ${audioFile.addedDate.substringBefore(" ")}",
                color = Color.Gray,
                fontFamily = AppFont.RobotoRegular,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 13.sp
            )
        }
        // Tampilkan durasi di sebelah kanan
        Text(
            text = durationFormatted,
            color = Color.Gray,
            fontFamily = AppFont.RobotoRegular,
            fontSize = 14.sp,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
        if (showMoreOption) {
            IconButton(onClick = onMoreClick) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "More Options",
                    tint = Color.Gray
                )
            }
        }
    }
}