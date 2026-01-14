package com.example.remusic.ui.screen.playmusic

import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.request.ImageRequest

@OptIn(UnstableApi::class)
@Composable
fun CanvasVideoPlayer(
    videoUrl: String,
    coverUrl: String?, // 1. Tambah parameter coverUrl
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // 2. State untuk melacak apakah video sudah siap tampil
    var isVideoReady by remember { mutableStateOf(false) }

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_ONE
            volume = 0f
        }
    }

    // Listener untuk mendeteksi kapan frame video pertama muncul
    DisposableEffect(Unit) {
        val listener = object : Player.Listener {
            override fun onRenderedFirstFrame() {
                super.onRenderedFirstFrame()
                // 3. Video sudah jalan -> Sembunyikan poster
                isVideoReady = true
            }
        }
        exoPlayer.addListener(listener)

        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    LaunchedEffect(videoUrl) {
        // Reset state setiap kali URL berubah (ganti lagu)
        // Jadi poster muncul dulu, baru video loading lagi
        isVideoReady = false

        if (videoUrl.isNotBlank()) {
            val mediaItem = MediaItem.fromUri(videoUrl)
            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.prepare()
            exoPlayer.playWhenReady = true
        } else {
            exoPlayer.stop()
        }
    }

    Box(modifier = modifier.fillMaxSize().background(Color.Black).clip(RectangleShape)) {
        // LAYER 1: VIDEO PLAYER
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    // Agar video full screen (Zoom & Crop)
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    layoutParams = FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // LAYER 2: POSTER GAMBAR (Overlay)
        // Kita gunakan AnimatedVisibility agar saat video siap, gambarnya hilang pelan-pelan (Fade Out)
        // Bukan ngilang kaget (kedip).
        AnimatedVisibility(
            visible = !isVideoReady, // Tampil jika video BELUM siap
            exit = fadeOut(animationSpec = tween(500)), // Hilang perlahan selama 0.5 detik
            modifier = Modifier.fillMaxSize()
        ) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(coverUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = "Loading Poster",
                contentScale = ContentScale.Crop, // Agar gambar full screen (Zoom & Crop) sama kayak video
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}