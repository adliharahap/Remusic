package com.example.remusic.ui.screen

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.remusic.data.UserManager
import com.example.remusic.ui.theme.AppFont
import kotlinx.coroutines.delay

@Composable
fun SecurityScreen(
    onNavigateBack: () -> Unit
) {
    val userProfile = UserManager.currentUser

    // Animasi masuk
    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(100)
        isVisible = true
    }

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
                    text = "Keamanan",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontFamily = AppFont.Poppins,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // --- HEADER ILLUSTRATION ---
            AnimatedVisibility(
                visible = isVisible,
                enter = fadeIn(animationSpec = tween(500)) + slideInVertically(initialOffsetY = { -30 })
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .background(Color(0xFF4CAF50).copy(alpha = 0.15f), CircleShape)
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Shield,
                            contentDescription = "Secured",
                            tint = Color(0xFF4CAF50),
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = "Akun Anda Aman",
                        color = Color.White,
                        fontSize = 22.sp,
                        fontFamily = AppFont.Poppins,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Keamanan tingkat lanjut dikelola otomatis\noleh penyedia layanan autentikasi.",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 14.sp,
                        fontFamily = AppFont.Helvetica,
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(48.dp))

            // --- GOOGLE SIGN IN INFO CARD ---
            AnimatedVisibility(
                visible = isVisible,
                enter = fadeIn(animationSpec = tween(500, delayMillis = 150)) + slideInVertically(initialOffsetY = { 30 })
            ) {
                Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                    Text(
                        text = "METODE LOGIN",
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
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // "G" Icon Placeholder (Warna warni khas Google)
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(Color.White),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "G",
                                    color = Color(0xFF4285F4),
                                    fontSize = 24.sp,
                                    fontFamily = AppFont.Poppins,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Google Sign-In",
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontFamily = AppFont.Helvetica,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = userProfile?.email ?: "Email tidak ditemukan",
                                    color = Color.White.copy(alpha = 0.6f),
                                    fontSize = 14.sp,
                                    fontFamily = AppFont.Helvetica
                                )
                            }

                            // Connected Badge
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = "Terhubung",
                                tint = Color(0xFF4CAF50),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // --- PASSWORD EXPLANATION CARD ---
            AnimatedVisibility(
                visible = isVisible,
                enter = fadeIn(animationSpec = tween(500, delayMillis = 250)) + slideInVertically(initialOffsetY = { 30 })
            ) {
                Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                    Text(
                        text = "KATA SANDI",
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
                            modifier = Modifier.padding(20.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Rounded.Lock,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.8f),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "Dikelola oleh Google",
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontFamily = AppFont.Helvetica,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Text(
                                text = "Karena Anda menggunakan Google untuk masuk ke ReMusic, kata sandi Anda dilindungi secara penuh oleh sistem Google. Anda tidak perlu membuat atau mengubah kata sandi terpisah di aplikasi ini.",
                                color = Color.White.copy(alpha = 0.5f),
                                fontSize = 14.sp,
                                fontFamily = AppFont.Helvetica,
                                lineHeight = 22.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}