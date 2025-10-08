package com.example.remusic.ui.screen.playmusic

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.remusic.ui.theme.AppFont
import com.example.remusic.utils.LockScreenOrientationPortrait
import com.example.remusic.viewmodel.playmusic.PlayMusicViewModel
import kotlinx.coroutines.launch

@Composable
fun PlayMusicScreen(
    playMusicViewModel: PlayMusicViewModel,
    navController: NavController
) {
    LockScreenOrientationPortrait()
    val pagerState = rememberPagerState(initialPage = 1, pageCount = { 3 })
    // Langsung gunakan uiState dari ViewModel yang sudah berjalan
    val uiState by playMusicViewModel.uiState.collectAsState()

    // 2. ANIMASIKAN setiap warna secara terpisah
    // Animasi untuk warna atas (top color)
    val animatedTopColor by animateColorAsState(
        targetValue = uiState.dominantColors.getOrElse(0) { Color.DarkGray },
        animationSpec = tween(1000),
        label = "topColorAnimation"
    )

    // Animasi untuk warna bawah (bottom color)
    val animatedBottomColor by animateColorAsState(
        targetValue = uiState.dominantColors.getOrElse(1) { Color.Black },
        animationSpec = tween(1000),
        label = "bottomColorAnimation"
    )

    // 3. Buat Brush menggunakan WARNA YANG SUDAH DIANIMASIKAN
    val animatedGradientBrush = Brush.verticalGradient(
        colors = listOf(animatedTopColor, animatedBottomColor)
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(brush = animatedGradientBrush)
    ) {
        // HEADER
        Spacer(modifier = Modifier.height(30.dp).fillMaxWidth())
        val tabs = listOf("Queue", "Playing", "Lyrics")
        val coroutineScope = rememberCoroutineScope()

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Back icon
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }

            // TABS (Queue, Now Playing, Lyrics)
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                tabs.forEachIndexed { index, title ->
                    val isSelected = pagerState.currentPage == index
                    Column(
                        modifier = Modifier
                            .padding(horizontal = 8.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                // pindah page manual kalau diklik
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(index)
                                }
                            },
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = title,
                            color = if (isSelected) Color.White else Color.White.copy(0.8f),
                            fontFamily = if (isSelected) AppFont.RobotoBold else AppFont.RobotoMedium,
                            fontSize = if (isSelected) 17.sp else 15.sp,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        if (isSelected) {
                            Box(
                                modifier = Modifier
                                    .height(2.dp)
                                    .width(40.dp)
                                    .background(Color.White, shape = RoundedCornerShape(1.dp))
                            )
                        } else {
                            Spacer(modifier = Modifier.height(2.dp))
                        }
                    }
                }
            }

            // 3 dots vertical (menu)
            IconButton(onClick = { /* TODO: action menu */ }) {
                Icon(
                    imageVector = Icons.Filled.MoreVert,
                    contentDescription = "More",
                    tint = Color.White
                )
            }
        }

        // PAGER
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            when (page) {
                0 -> QueueScreen(
                    songWithArtist = uiState.currentSong,
                    playlistQueue = uiState.playlist,
                    playMusicFromPlaylist = uiState.playingMusicFromPlaylist,
                    onClickListener = { playMusicViewModel.playSongAt(it) }
                )
                1 -> NowPlaying(
                    songWithArtist = uiState.currentSong,
                    isPlaying = uiState.isPlaying,
                    currentPosition = uiState.currentPosition,
                    totalDuration = uiState.totalDuration,
                    isShuffleEnabled = uiState.isShuffleModeEnabled,
                    repeatMode = uiState.repeatMode,
                    onPlayPauseClick = { playMusicViewModel.togglePlayPause() },
                    onNextClick = { playMusicViewModel.nextSong() },
                    onPrevClick = { playMusicViewModel.previousSong() },
                    onShuffleClick = { playMusicViewModel.toggleShuffleMode() },
                    onRepeatClick = { playMusicViewModel.cycleRepeatMode() },
                    posterAnimation = uiState.currentSongIndex,
                    posterAnimationDirection = uiState.animationDirection,
                    onSeek = { positionFraction ->
                        val newPosition = (uiState.totalDuration * positionFraction).toLong()
                        playMusicViewModel.seekTo(newPosition)
                    },
                )

                    2 -> LyricsScreen(
                        lrcString = uiState.currentSong?.song?.lyrics ?: "",
                        currentPosition = uiState.currentPosition,
                        bottomPlayerColor = animatedBottomColor,
                        songWithArtist = uiState.currentSong,
                        isPlaying = uiState.isPlaying,
                        totalDuration = uiState.totalDuration,
                        onSeek = { positionFraction ->
                            val newPosition = (uiState.totalDuration * positionFraction).toLong()
                            playMusicViewModel.seekTo(newPosition)
                        },
                        onPlayPauseClick = { playMusicViewModel.togglePlayPause() },
                    )
            }
        }
    }
}