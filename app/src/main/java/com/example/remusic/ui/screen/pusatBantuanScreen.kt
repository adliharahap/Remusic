package com.example.remusic.ui.screen

import android.content.Intent
import android.net.Uri
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
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.SupportAgent
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

@Composable
fun HelpCenterScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current

    // State animasi UI
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
                    text = "Pusat Bantuan",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontFamily = AppFont.Poppins,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

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
                            .size(90.dp)
                            .background(Color(0xFF2196F3).copy(alpha = 0.15f), CircleShape)
                            .padding(20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SupportAgent,
                            contentDescription = "Support Agent",
                            tint = Color(0xFF2196F3),
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = "Ada yang bisa dibantu?",
                        color = Color.White,
                        fontSize = 24.sp,
                        fontFamily = AppFont.Poppins,
                        fontWeight = FontWeight.ExtraBold
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Temukan jawaban cepat untuk pertanyaan umum\natau hubungi developer secara langsung.",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 13.sp,
                        fontFamily = AppFont.Helvetica,
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(48.dp))

            // --- FAQ SECTIONS ---
            AnimatedVisibility(
                visible = isVisible,
                enter = fadeIn(animationSpec = tween(500, delayMillis = 150)) + slideInVertically(initialOffsetY = { 30 })
            ) {
                Column(modifier = Modifier.padding(horizontal = 20.dp)) {

                    Text(
                        text = "PERTANYAAN UMUM (FAQ)",
                        color = Color.White.copy(alpha = 0.4f),
                        fontSize = 12.sp,
                        fontFamily = AppFont.Helvetica,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(start = 16.dp, bottom = 12.dp)
                    )

                    HelpFAQCard(
                        icon = Icons.Rounded.LibraryMusic,
                        iconColor = Color(0xFFE91E63),
                        question = "Bagaimana cara meminta (request) lagu?",
                        answer = "Jika lagu yang Anda cari tidak ada, Anda bisa pergi ke tab Profil, lalu pilih menu 'Request Song'. Masukkan judul dan nama artis, lalu tunggu persetujuan dari sistem."
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    HelpFAQCard(
                        icon = Icons.Rounded.CloudOff,
                        iconColor = Color(0xFFFF9800),
                        question = "Apakah ReMusic bisa diputar offline?",
                        answer = "Ya! ReMusic dirancang sebagai aplikasi 'Offline-First'. Lagu yang sudah pernah Anda putar hingga selesai akan secara otomatis disimpan ke dalam cache lokal perangkat. Saat Anda tidak ada internet, lagu tersebut tetap bisa diputar tanpa memakan kuota."
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    HelpFAQCard(
                        icon = Icons.Rounded.PlayCircle,
                        iconColor = Color(0xFF4CAF50),
                        question = "Apakah aplikasi ini benar-benar gratis?",
                        answer = "Tentu saja! ReMusic adalah proyek portofolio pribadi dari developer tunggal (Solo Dev). Tidak ada biaya langganan, tidak ada batasan putar, dan 100% bebas dari iklan pihak ketiga."
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    HelpFAQCard(
                        icon = Icons.Rounded.BugReport,
                        iconColor = Color(0xFF9C27B0),
                        question = "Saya menemukan error/bug, apa yang harus saya lakukan?",
                        answer = "Karena aplikasi ini masih dalam tahap pengembangan aktif, bug mungkin saja terjadi. Anda dapat membantu kami dengan melaporkan bug tersebut langsung melalui kontak developer di bawah ini."
                    )

                }
            }

            Spacer(modifier = Modifier.height(48.dp))

            // --- DIRECT CONTACT CARD ---
            AnimatedVisibility(
                visible = isVisible,
                enter = fadeIn(animationSpec = tween(500, delayMillis = 300))
            ) {
                Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF161616))
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Masih butuh bantuan?",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontFamily = AppFont.Poppins,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Hubungi Adli (Developer) secara langsung. Kami biasanya merespons dalam waktu 1x24 jam.",
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = 13.sp,
                                fontFamily = AppFont.Helvetica,
                                textAlign = TextAlign.Center,
                                lineHeight = 20.sp
                            )

                            Spacer(modifier = Modifier.height(24.dp))

                            // Action Buttons
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // WhatsApp Button
                                Button(
                                    onClick = { uriHandler.openUri("https://wa.me/6289676655115") },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF4CAF50).copy(alpha = 0.15f),
                                        contentColor = Color(0xFF4CAF50)
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    elevation = ButtonDefaults.buttonElevation(0.dp)
                                ) {
                                    Text(
                                        text = "WhatsApp",
                                        fontFamily = AppFont.Helvetica,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                }

                                // Email Button
                                Button(
                                    onClick = {
                                        val intent = Intent(Intent.ACTION_SENDTO).apply {
                                            data = Uri.parse("mailto:adliharahap1123@gmail.com")
                                        }
                                        context.startActivity(intent)
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFFE91E63).copy(alpha = 0.15f),
                                        contentColor = Color(0xFFE91E63)
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    elevation = ButtonDefaults.buttonElevation(0.dp)
                                ) {
                                    Text(
                                        text = "Kirim Email",
                                        fontFamily = AppFont.Helvetica,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                }
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
fun HelpFAQCard(
    icon: ImageVector,
    iconColor: Color,
    question: String,
    answer: String
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable { expanded = !expanded },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF161616))
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(iconColor.copy(alpha = 0.15f), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = question,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontFamily = AppFont.Poppins,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = answer,
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 14.sp,
                        fontFamily = AppFont.Helvetica,
                        lineHeight = 22.sp
                    )
                }
            }

            AnimatedVisibility(visible = !expanded) {
                Column {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Ketuk untuk melihat jawaban...",
                        color = Color.White.copy(alpha = 0.3f),
                        fontSize = 12.sp,
                        fontFamily = AppFont.Helvetica,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                    )
                }
            }
        }
    }
}