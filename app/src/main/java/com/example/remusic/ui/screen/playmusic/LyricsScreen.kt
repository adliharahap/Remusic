package com.example.remusic.ui.screen.playmusic

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.remusic.ui.theme.AppFont
import com.example.remusic.viewmodel.playmusic.LyricsViewModel
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
fun LyricsScreen(
    lyricsViewModel: LyricsViewModel = viewModel(),
    currentPosition: Long,
    lrcString: String
) {

    // load lirik sekali saat lrcString berubah
    LaunchedEffect(lrcString) {
        lyricsViewModel.loadLyrics(lrcString)
    }

    // setiap currentPosition berubah, update ViewModel lirik
    LaunchedEffect(currentPosition) {
        lyricsViewModel.updatePlaybackPosition(currentPosition)
    }

    val lyrics by lyricsViewModel.lyrics.collectAsState()
    val activeIndex by lyricsViewModel.activeLyricIndex.collectAsState()
    val lazyListState = rememberLazyListState()
    val configuration = LocalConfiguration.current

    // Menghitung padding untuk membuat item bisa berada di tengah layar
    val screenHeight = configuration.screenHeightDp.dp
    val topBottomPadding = screenHeight / 2

    // Efek untuk auto-scroll ke lirik yang aktif dengan animasi smooth
    LaunchedEffect(lazyListState) {
        snapshotFlow { activeIndex }
            .distinctUntilChanged()
            .collect { index ->
                if (index >= 0 && index < lyrics.size) {
                    // Scroll dengan animasi ke item yang aktif
                    // Offset negatif untuk menempatkan item di tengah layar
                    lazyListState.animateScrollToItem(
                        index = index,
                        scrollOffset = -screenHeight.value.toInt() / 2
                    )
                }
            }
    }

    LazyColumn(
        state = lazyListState,
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Spacer atas agar item pertama bisa di tengah layar
        item {
            Spacer(modifier = Modifier.height(200.dp))
        }

        itemsIndexed(lyrics) { index, line ->
            val isActive = index == activeIndex
            val hasTimestamp = line.timestamp != -1L

            // Styling berdasarkan status
            val color = when {
                !hasTimestamp -> Color.White // Baris tanpa timestamp selalu putih (aktif)
                isActive -> Color.White // Lirik aktif - putih terang
                else -> Color.White.copy(alpha = 0.5f) // Lirik lain - semi transparan
            }

            val fontFamily = if (isActive || !hasTimestamp) AppFont.RobotoBold else AppFont.RobotoRegular
            val fontSize = when {
                !hasTimestamp -> 22.sp // Baris tanpa timestamp ukuran sedang
                isActive -> 24.sp // Lirik aktif paling besar
                else -> 20.sp // Lirik lain ukuran normal
            }

            Text(
                text = line.text,
                color = color,
                fontSize = fontSize,
                fontFamily = fontFamily,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp)
            )
        }

        // Spacer bawah agar item terakhir bisa di tengah layar
        item {
            Spacer(modifier = Modifier.height(topBottomPadding))
        }
    }
}