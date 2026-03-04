package com.example.remusic.ui.screen

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.remusic.data.model.Notification
import com.example.remusic.ui.theme.AppFont
import com.example.remusic.viewmodel.notification.NotificationUiState
import com.example.remusic.viewmodel.notification.NotificationViewModel

// --- Design Tokens (Premium Dark Theme) ---
private val BgDark = Color(0xFF09090B)       // Latar belakang utama (Sangat gelap/modern)
private val SurfaceDark = Color(0xFF18181B)  // Latar belakang card/item
private val AccentPink = Color(0xFFE91E63)   // Warna aksen original
private val TextPrimary = Color(0xFFFAFAFA)
private val TextSecondary = Color(0xFFA1A1AA)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationScreen(
    navController: NavController,
    viewModel: NotificationViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        text = "Notifications", 
                        fontFamily = AppFont.Poppins, 
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = TextPrimary
                    ) 
                },
                navigationIcon = {
                    IconButton(
                        onClick = { navController.popBackStack() },
                        modifier = Modifier.padding(start = 8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(SurfaceDark),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack, 
                                contentDescription = "Back", 
                                tint = TextPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BgDark,
                    scrolledContainerColor = BgDark
                )
            )
        },
        containerColor = BgDark
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (val state = uiState) {
                is NotificationUiState.Loading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = AccentPink,
                        strokeWidth = 3.dp
                    )
                }
                is NotificationUiState.Error -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info, 
                            contentDescription = "Error",
                            tint = AccentPink,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = state.message,
                            color = TextSecondary,
                            fontFamily = AppFont.Poppins,
                            fontSize = 14.sp
                        )
                    }
                }
                is NotificationUiState.Success -> {
                    if (state.notifications.isEmpty()) {
                        EmptyNotificationState(modifier = Modifier.align(Alignment.Center))
                    } else {
                        LazyColumn(
                            // Padding bawah dihapus karena sudah diganti Spacer di dalam item
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 20.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(state.notifications, key = { it.id }) { notif ->
                                NotificationItem(
                                    notification = notif,
                                    onClick = {
                                        // Logic utuh tidak diubah
                                        if (!notif.isRead) {
                                            viewModel.markAsRead(notif)
                                        }
                                        val jsonNotif = Uri.encode(kotlinx.serialization.json.Json.encodeToString(notif))
                                        navController.navigate("notification_detail/$jsonNotif")
                                    }
                                )
                            }
                            // Spacer agar tidak tertutup Bottom Navigation
                            item {
                                Spacer(modifier = Modifier.height(120.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun NotificationItem(
    notification: Notification,
    onClick: () -> Unit
) {
    // Styling dinamis berdasarkan status read/unread
    val isUnread = !notification.isRead
    val backgroundColor = if (isUnread) SurfaceDark else Color.Transparent
    val borderColor = if (isUnread) SurfaceDark.copy(alpha = 0.5f) else Color.Transparent
    
    val iconBackground = if (isUnread) AccentPink.copy(alpha = 0.15f) else SurfaceDark
    val iconTint = if (isUnread) AccentPink else TextSecondary

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(backgroundColor)
            .border(1.dp, borderColor, RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(14.dp), // Padding sedikit dikecilkan agar lebih compact
        verticalAlignment = Alignment.CenterVertically // Sejajar tengah secara horizontal
    ) {
        // --- Icon Box ---
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(iconBackground),
            contentAlignment = Alignment.Center
        ) {
            val icon = when (notification.type) {
                "promo" -> Icons.Default.LocalOffer
                "song_request" -> Icons.Default.MusicNote
                "welcome" -> Icons.Default.Star
                else -> Icons.Default.Info
            }
            Icon(
                imageVector = icon,
                contentDescription = notification.type,
                tint = iconTint,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        // --- Konten Teks ---
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = notification.title,
                fontFamily = AppFont.Poppins,
                fontWeight = if (isUnread) FontWeight.Bold else FontWeight.SemiBold,
                color = if (isUnread) TextPrimary else TextPrimary.copy(alpha = 0.8f),
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            
            Spacer(modifier = Modifier.height(2.dp))
            
            Text(
                text = notification.message,
                fontFamily = AppFont.Poppins,
                fontWeight = FontWeight.Normal,
                color = TextSecondary,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                maxLines = 2, // Dibatasi 2 baris agar tetap compact
                overflow = TextOverflow.Ellipsis
            )
        }

        // --- Image Preview (Thumbnail Kotak 1:1 di Kanan) ---
        if (!notification.imageUrl.isNullOrBlank()) {
            Spacer(modifier = Modifier.width(12.dp))
            AsyncImage(
                model = notification.imageUrl,
                contentDescription = "Notification Image",
                contentScale = ContentScale.Crop, // Crop memastikan gambarnya menjadi persegi 1:1 tanpa gepeng
                modifier = Modifier
                    .size(56.dp) // Ukuran kecil dan proporsional
                    .clip(RoundedCornerShape(8.dp))
                    .background(SurfaceDark) // Placeholder saat loading
            )
        }

        // --- Dot Unread Indicator (Pindah ke paling kanan) ---
        if (isUnread) {
            Spacer(modifier = Modifier.width(12.dp))
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(AccentPink, AccentPink.copy(alpha = 0.5f))
                        )
                    )
            )
        }
    }
}

@Composable
fun EmptyNotificationState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(CircleShape)
                .background(SurfaceDark),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Notifications,
                contentDescription = "Empty Notifications",
                tint = TextSecondary.copy(alpha = 0.5f),
                modifier = Modifier.size(48.dp)
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "No New Notifications",
            color = TextPrimary,
            fontFamily = AppFont.Poppins,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "When you get notifications, they'll show up here.",
            color = TextSecondary,
            fontFamily = AppFont.Poppins,
            fontSize = 14.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            lineHeight = 22.sp
        )
    }
}