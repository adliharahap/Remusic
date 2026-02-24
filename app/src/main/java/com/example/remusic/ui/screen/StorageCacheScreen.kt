package com.example.remusic.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.Dataset
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.remusic.ui.theme.AppFont
import com.example.remusic.viewmodel.StorageCacheViewModel
import kotlinx.coroutines.delay

@Composable
fun StorageCacheScreen(
    onNavigateBack: () -> Unit,
    viewModel: StorageCacheViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    val context = LocalContext.current
    
    // Observasi state dari ViewModel
    val audioCacheSizeMB by viewModel.audioCacheSizeMB.collectAsState()
    val dataCacheSizeMB by viewModel.dataCacheSizeMB.collectAsState()
    val totalCache = audioCacheSizeMB + dataCacheSizeMB

    // Trigger load cache size saat screen dibuka
    // State animasi UI
    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        viewModel.loadCacheSizes(context)
        delay(100)
        isVisible = true
    }

    val maxCacheMB = 2048f // 2 GB
    val cachePercentage = if (totalCache > 0f) totalCache / maxCacheMB else 0f

    // Animasi progress bar agar terlihat smooth saat cache dihapus
    val animatedProgress by animateFloatAsState(
        targetValue = cachePercentage.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 1000),
        label = "progress_anim"
    )

    // Gradient Background pekat ala Spotify
    val backgroundBrush = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF2B0B16), // Dark Pink/Magenta tint
            Color(0xFF0A0A0A),
            Color(0xFF000000)
        ),
        startY = 0f,
        endY = 1200f
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundBrush)
            .windowInsetsPadding(WindowInsets.systemBars)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {

            // --- TOP APP BAR ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Kembali",
                        tint = Color.White
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = "Penyimpanan & Cache",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontFamily = AppFont.Poppins,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // --- HEADER ILLUSTRATION & PROGRESS BAR ---
            AnimatedVisibility(
                visible = isVisible,
                enter = fadeIn(animationSpec = tween(500)) + slideInVertically(initialOffsetY = { -30 })
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(90.dp)
                            .background(Color(0xFF2196F3).copy(alpha = 0.15f), CircleShape)
                            .padding(20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Storage,
                            contentDescription = "Storage",
                            tint = Color(0xFF2196F3),
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Indikator Kapasitas
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Column {
                            Text(
                                text = StorageCacheViewModel.formatSize(totalCache),
                                color = Color.White,
                                fontSize = 28.sp,
                                fontFamily = AppFont.Poppins,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = "Digunakan",
                                color = Color.White.copy(alpha = 0.5f),
                                fontSize = 13.sp,
                                fontFamily = AppFont.Helvetica
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "Maks. ~2 GB",
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 16.sp,
                                fontFamily = AppFont.Helvetica,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Batas Cache",
                                color = Color.White.copy(alpha = 0.5f),
                                fontSize = 13.sp,
                                fontFamily = AppFont.Helvetica
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Linear Progress Bar Custom
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(12.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.White.copy(alpha = 0.1f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(animatedProgress)
                                .fillMaxHeight()
                                .background(
                                    Brush.horizontalGradient(
                                        colors = listOf(Color(0xFF2196F3), Color(0xFFE91E63))
                                    )
                                )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(48.dp))

            // --- OFFLINE FIRST INFO CARD ---
            AnimatedVisibility(
                visible = isVisible,
                enter = fadeIn(animationSpec = tween(500, delayMillis = 150)) + slideInVertically(initialOffsetY = { 30 })
            ) {
                Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                    Text(
                        text = "TENTANG PENYIMPANAN",
                        color = Color.White.copy(alpha = 0.4f),
                        fontSize = 12.sp,
                        fontFamily = AppFont.Helvetica,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
                    )

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF161616))
                    ) {
                        Row(
                            modifier = Modifier.padding(20.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Info,
                                contentDescription = "Info",
                                tint = Color(0xFFE91E63),
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(
                                    text = "Aplikasi Offline-First",
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontFamily = AppFont.Helvetica,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "ReMusic dirancang untuk menghemat kuota Anda. Kami menyimpan cache lagu (melalui ExoPlayer) dan metadata artis/playlist (menggunakan SQLite) di perangkat ini agar musik dapat diputar seketika, bahkan saat koneksi tidak stabil.\n\nSistem secara otomatis akan mengelola ruang ini dan membatasi ukuran maksimal cache di kisaran 2 GB.",
                                    color = Color.White.copy(alpha = 0.6f),
                                    fontSize = 13.sp,
                                    fontFamily = AppFont.Helvetica,
                                    lineHeight = 20.sp
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // --- MANAGE CACHE CARD ---
            AnimatedVisibility(
                visible = isVisible,
                enter = fadeIn(animationSpec = tween(500, delayMillis = 250)) + slideInVertically(initialOffsetY = { 30 })
            ) {
                Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                    Text(
                        text = "KELOLA CACHE",
                        color = Color.White.copy(alpha = 0.4f),
                        fontSize = 12.sp,
                        fontFamily = AppFont.Helvetica,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
                    )

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF161616))
                    ) {
                        Column {
                            // Hapus Cache Audio (ExoPlayer)
                            CacheItemRow(
                                icon = Icons.Rounded.Audiotrack,
                                iconColor = Color(0xFF9C27B0),
                                title = "Cache Audio Lagu",
                                subtitle = "Data lagu yang disimpan ExoPlayer",
                                sizeMB = audioCacheSizeMB,
                                onClick = {
                                    viewModel.clearAudioCache(context)
                                }
                            )

                            HorizontalDivider(
                                modifier = Modifier.padding(start = 72.dp, end = 20.dp),
                                thickness = 1.dp,
                                color = Color.White.copy(alpha = 0.05f)
                            )

                            // Hapus Cache Data (SQLite)
                            CacheItemRow(
                                icon = Icons.Rounded.Dataset,
                                iconColor = Color(0xFFFF9800),
                                title = "Cache Data & Gambar",
                                subtitle = "Metadata SQLite & Thumbnail UI",
                                sizeMB = dataCacheSizeMB,
                                onClick = {
                                    viewModel.clearDataCache(context)
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(150.dp))
        }
    }
}

@Composable
fun CacheItemRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color,
    title: String,
    subtitle: String,
    sizeMB: Float,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = sizeMB > 0f, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icon
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(iconColor.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = iconColor,
                modifier = Modifier.size(24.dp)
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        // Teks
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = Color.White.copy(alpha = if (sizeMB > 0f) 0.9f else 0.4f),
                fontSize = 15.sp,
                fontFamily = AppFont.Helvetica,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                color = Color.White.copy(alpha = 0.4f),
                fontSize = 12.sp,
                fontFamily = AppFont.Helvetica,
                lineHeight = 16.sp
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Status Ukuran atau Tombol Hapus
        if (sizeMB > 0.05f) { // Hanya tampilkan jika size minimal ~50KB
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = StorageCacheViewModel.formatSize(sizeMB),
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 14.sp,
                    fontFamily = AppFont.Helvetica,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.width(12.dp))
                Icon(
                    imageVector = Icons.Rounded.DeleteOutline,
                    contentDescription = "Hapus",
                    tint = Color(0xFFFF4B4B),
                    modifier = Modifier.size(22.dp)
                )
            }
        } else {
            Text(
                text = "Bersih",
                color = Color(0xFF4CAF50),
                fontSize = 14.sp,
                fontFamily = AppFont.Helvetica,
                fontWeight = FontWeight.Bold
            )
        }
    }
}