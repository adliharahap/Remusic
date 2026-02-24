package com.example.remusic.ui.screen

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.remusic.ui.theme.AppFont
import kotlinx.coroutines.delay
import coil.compose.AsyncImage
import com.example.remusic.R
import androidx.compose.ui.layout.ContentScale

@Composable
fun AboutScreen(
    onNavigateBack: () -> Unit
) {
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current

    // State animasi UI
    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(100)
        isVisible = true
    }

    // Gradient Background pekat
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
                    text = "Tentang",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontFamily = AppFont.Poppins,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // --- APP LOGO & VERSION ---
            AnimatedVisibility(
                visible = isVisible,
                enter = fadeIn(animationSpec = tween(500)) + slideInVertically(initialOffsetY = { -30 })
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Placeholder Logo ReMusic yang Elegan
                    AsyncImage(
                        model = R.drawable.app_logo,
                        contentDescription = "ReMusic Logo",
                        modifier = Modifier
                            .size(120.dp)
                            .background(Color.White.copy(alpha = 0.05f), CircleShape)
                            .border(2.dp, Color.White.copy(alpha = 0.1f), CircleShape)
                            .padding(12.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Fit
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "ReMusic",
                        color = Color.White,
                        fontSize = 28.sp,
                        fontFamily = AppFont.Poppins,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp
                    )

                    Text(
                        text = "Versi 1.0.0 (Build 1)",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 14.sp,
                        fontFamily = AppFont.Helvetica
                    )
                }
            }

            Spacer(modifier = Modifier.height(48.dp))

            // --- DEVELOPER INFO CARD ---
            AnimatedVisibility(
                visible = isVisible,
                enter = fadeIn(animationSpec = tween(500, delayMillis = 150)) + slideInVertically(initialOffsetY = { 30 })
            ) {
                Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                    Text(
                        text = "DIBUAT OLEH",
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
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(120.dp)
                                    .padding(3.dp)
                                    .background(
                                        Brush.sweepGradient(
                                            listOf(Color(0xFFE91E63), Color(0xFF9C27B0), Color(0xFFE91E63))
                                        ),
                                        CircleShape
                                    )
                                    .padding(2.dp)
                            ) {
                                AsyncImage(
                                    model = "https://bnqhatesnqukmsbjaulm.supabase.co/storage/v1/object/public/Remusic%20Storage/profiles/IMG_20251004_184307.jpg",
                                    contentDescription = "Developer",
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(CircleShape)
                                        .border(2.dp, Color(0xFF161616), CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Text(
                                text = "Adli Rahman Harun Harahap",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontFamily = AppFont.Poppins,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = "Android Developer",
                                color = Color(0xFFE91E63),
                                fontSize = 13.sp,
                                fontFamily = AppFont.Helvetica,
                                fontWeight = FontWeight.Medium
                            )

                            Spacer(modifier = Modifier.height(24.dp))

                            // Social Links Grid
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                SocialButton(
                                    icon = Icons.Rounded.Code,
                                    label = "GitHub",
                                    onClick = { uriHandler.openUri("https://github.com/adliharahap") }
                                )
                                SocialButton(
                                    icon = Icons.Rounded.Public,
                                    label = "LinkedIn",
                                    onClick = { uriHandler.openUri("https://www.linkedin.com/in/adlirahmanharunharahap/?originalSubdomain=id") }
                                )
                                SocialButton(
                                    icon = Icons.Rounded.Phone,
                                    label = "WhatsApp",
                                    onClick = { uriHandler.openUri("https://wa.me/6289676655115") }
                                )
                                SocialButton(
                                    icon = Icons.Rounded.Email,
                                    label = "Email",
                                    onClick = {
                                        val intent = Intent(Intent.ACTION_SENDTO).apply {
                                            data = Uri.parse("mailto:adliharahap1123@gmail.com")
                                        }
                                        context.startActivity(intent)
                                    }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // --- TECH STACK CARD ---
            AnimatedVisibility(
                visible = isVisible,
                enter = fadeIn(animationSpec = tween(500, delayMillis = 250)) + slideInVertically(initialOffsetY = { 30 })
            ) {
                Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                    Text(
                        text = "TECH STACK YANG DIGUNAKAN",
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
                        Column(modifier = Modifier.padding(20.dp)) {
                            TechStackRow("Jetpack Compose", "UI Framework")
                            TechStackRow("Media3 (ExoPlayer)", "Audio Engine")
                            TechStackRow("Supabase & Firebase", "Backend & Auth")
                            TechStackRow("Room (SQLite)", "Local Database")
                            TechStackRow("Retrofit & Ktor", "Networking")
                            TechStackRow("Coil", "Image Loading")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // --- LICENSE CARD ---
            AnimatedVisibility(
                visible = isVisible,
                enter = fadeIn(animationSpec = tween(500, delayMillis = 350)) + slideInVertically(initialOffsetY = { 30 })
            ) {
                Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF161616))
                    ) {
                        Row(
                            modifier = Modifier.padding(20.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Gavel,
                                contentDescription = "License",
                                tint = Color.White.copy(alpha = 0.5f),
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(
                                    text = "Lisensi MIT",
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontFamily = AppFont.Helvetica,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Perangkat lunak ini disediakan \"apa adanya\", tanpa jaminan dalam bentuk apapun.",
                                    color = Color.White.copy(alpha = 0.5f),
                                    fontSize = 12.sp,
                                    fontFamily = AppFont.Helvetica,
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

// --- KOMPONEN BANTUAN ---

@Composable
fun SocialButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(Color.White.copy(alpha = 0.05f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = Color.White.copy(alpha = 0.8f),
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 11.sp,
            fontFamily = AppFont.Helvetica,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun TechStackRow(title: String, subtitle: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            color = Color.White.copy(alpha = 0.9f),
            fontSize = 14.sp,
            fontFamily = AppFont.Helvetica,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = subtitle,
            color = Color(0xFFE91E63).copy(alpha = 0.8f),
            fontSize = 12.sp,
            fontFamily = AppFont.Helvetica,
            fontWeight = FontWeight.Bold
        )
    }
}