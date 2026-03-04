package com.example.remusic.ui.screen

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.remusic.data.model.Notification
import com.example.remusic.ui.theme.AppFont
import java.util.Locale

// --- Design Tokens (Premium Dark Theme) ---
private val BgDark = Color(0xFF09090B)
private val SurfaceDark = Color(0xFF18181B)
private val AccentPink = Color(0xFFE91E63)
private val TextPrimary = Color(0xFFFAFAFA)
private val TextSecondary = Color(0xFFA1A1AA)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationDetailScreen(
    notification: Notification,
    navController: NavController
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        text = "Detail", 
                        fontFamily = AppFont.Poppins, 
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(scrollState)
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally // Memusatkan konten layaknya Spotify/Apple Music
        ) {
            
            // --- 1. Image Preview (Hero Image) ---
            if (!notification.imageUrl.isNullOrBlank()) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = notification.imageUrl,
                        contentDescription = "Notification Image",
                        // Menggunakan ContentScale.Crop, namun ukurannya dibatasi oleh widthIn & heightIn
                        // Ini memastikan gambar 1:1 atau 16:9 tetap proporsional dan tidak kebesaran (anti pecah)
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .widthIn(max = 240.dp) // Ukuran ideal album art di tengah layar
                            .heightIn(max = 240.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(SurfaceDark)
                            .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(16.dp))
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))
            } else {
                Spacer(modifier = Modifier.height(16.dp))
            }

            // --- 2. Type Badge (Kategori Notifikasi) ---
            val (icon, typeText) = when (notification.type) {
                "promo" -> Icons.Default.LocalOffer to "PROMO"
                "song_request" -> Icons.Default.MusicNote to "SONG REQUEST"
                "welcome" -> Icons.Default.Star to "WELCOME"
                else -> Icons.Default.Info to notification.type.uppercase(Locale.getDefault())
            }
            
            Surface(
                color = AccentPink.copy(alpha = 0.15f),
                shape = RoundedCornerShape(50),
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = AccentPink,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = typeText,
                        fontFamily = AppFont.Poppins,
                        fontWeight = FontWeight.Bold,
                        color = AccentPink,
                        fontSize = 11.sp,
                        letterSpacing = 1.sp
                    )
                }
            }

            // --- 3. Title ---
            Text(
                text = notification.title,
                fontFamily = AppFont.Poppins,
                fontWeight = FontWeight.ExtraBold,
                color = TextPrimary,
                fontSize = 24.sp,
                textAlign = TextAlign.Center,
                lineHeight = 32.sp
            )

            Spacer(modifier = Modifier.height(32.dp))

            // --- 4. Message Body (Dibungkus Card Elegan) ---
            Surface(
                color = SurfaceDark,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = notification.message,
                    fontFamily = AppFont.Poppins,
                    color = TextSecondary,
                    fontSize = 15.sp,
                    lineHeight = 26.sp,
                    fontWeight = FontWeight.Normal,
                    modifier = Modifier.padding(24.dp) // Memberikan ruang napas di dalam card
                )
            }

            // --- 5. Reference Button (Call to Action) ---
            if (!notification.referenceId.isNullOrBlank() && notification.referenceId.startsWith("http")) {
                Spacer(modifier = Modifier.height(32.dp))
                Button(
                    onClick = {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(notification.referenceId))
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentPink,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(100.dp)
                ) {
                    Text(
                        text = "Buka Tautan",
                        fontFamily = AppFont.Poppins,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        letterSpacing = 0.5.sp
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}