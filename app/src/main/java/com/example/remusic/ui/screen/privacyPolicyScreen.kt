package com.example.remusic.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.Cookie
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import com.example.remusic.ui.theme.AppFont
import kotlinx.coroutines.delay

@Composable
fun PrivacyPolicyScreen(
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
                    text = "Kebijakan Privasi",
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
                            .background(Color(0xFFE91E63).copy(alpha = 0.15f), CircleShape)
                            .padding(20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Shield,
                            contentDescription = "Privacy Shield",
                            tint = Color(0xFFE91E63),
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = "Privasi Anda Penting",
                        color = Color.White,
                        fontSize = 24.sp,
                        fontFamily = AppFont.Poppins,
                        fontWeight = FontWeight.ExtraBold
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Terakhir diperbarui: Februari 2026\nReMusic berkomitmen untuk melindungi data Anda.",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 13.sp,
                        fontFamily = AppFont.Helvetica,
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(48.dp))

            // --- SECTIONS ---
            AnimatedVisibility(
                visible = isVisible,
                enter = fadeIn(animationSpec = tween(500, delayMillis = 150)) + slideInVertically(initialOffsetY = { 30 })
            ) {
                Column(modifier = Modifier.padding(horizontal = 20.dp)) {

                    PrivacySectionCard(
                        icon = Icons.Rounded.AccountCircle,
                        iconColor = Color(0xFF2196F3),
                        title = "1. Data yang Kami Kumpulkan",
                        content = "Karena ReMusic menggunakan autentikasi Google Sign-In, kami hanya mengumpulkan data profil dasar yang diizinkan oleh Google, meliputi:\n• Nama tampilan (Display Name)\n• Alamat Email\n• Foto Profil (Avatar)\n\nKami juga menyimpan preferensi musik Anda, seperti lagu yang disukai, riwayat putar, dan playlist yang Anda buat."
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    PrivacySectionCard(
                        icon = Icons.Rounded.Security,
                        iconColor = Color(0xFF4CAF50),
                        title = "2. Visibilitas & Penggunaan",
                        content = "Data yang dikumpulkan digunakan untuk menyediakan layanan streaming yang dipersonalisasi dan menyimpan cache offline.\n\nVisibilitas Publik: Jika Anda mengatur playlist Anda sebagai 'Publik', pengguna lain dapat melihat profil Anda dan isi playlist tersebut. Anda dapat mengubahnya menjadi 'Privat' kapan saja."
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    PrivacySectionCard(
                        icon = Icons.Rounded.CloudSync,
                        iconColor = Color(0xFFFF9800),
                        title = "3. Bebas Iklan & Layanan Cloud",
                        content = "ReMusic adalah proyek portofolio pribadi dan 100% Bebas Iklan. Kami tidak akan pernah menjual data Anda.\n\nData diproses secara aman melalui layanan pihak ketiga:\n• Supabase (Database Utama & Analitik mendatang)\n• Firebase & Google Cloud (Autentikasi)"
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    PrivacySectionCard(
                        icon = Icons.Rounded.Gavel,
                        iconColor = Color(0xFF9C27B0),
                        title = "4. Hak Pengguna & Penghapusan",
                        content = "Anda memegang kendali penuh atas akun Anda. Untuk mencabut akses, gunakan pengaturan Keamanan Akun Google.\n\nUntuk permintaan penghapusan data secara penuh dari database kami, silakan hubungi pengembang melalui Email atau WhatsApp yang tertera di bawah."
                    )

                }
            }

            Spacer(modifier = Modifier.height(48.dp))

            // --- FOOTER CONTACT ---
            AnimatedVisibility(
                visible = isVisible,
                enter = fadeIn(animationSpec = tween(500, delayMillis = 300))
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Punya pertanyaan atau ingin menghapus data?",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 13.sp,
                        fontFamily = AppFont.Helvetica
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Email",
                            color = Color(0xFFE91E63),
                            fontSize = 15.sp,
                            fontFamily = AppFont.Helvetica,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable {
                                val intent = Intent(Intent.ACTION_SENDTO).apply {
                                    data = Uri.parse("mailto:adliharahap1123@gmail.com")
                                }
                                context.startActivity(intent)
                            }.padding(8.dp) // Memperbesar touch target
                        )

                        Text(
                            text = "  •  ",
                            color = Color.White.copy(alpha = 0.3f),
                            fontSize = 14.sp
                        )

                        Text(
                            text = "WhatsApp",
                            color = Color(0xFF4CAF50), // Warna Hijau khas WA
                            fontSize = 15.sp,
                            fontFamily = AppFont.Helvetica,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable {
                                uriHandler.openUri("https://wa.me/6289676655115")
                            }.padding(8.dp) // Memperbesar touch target
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(140.dp))
        }
    }
}

// --- KOMPONEN BANTUAN ---

@Composable
fun PrivacySectionCard(
    icon: ImageVector,
    iconColor: Color,
    title: String,
    content: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
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
                    text = title,
                    color = Color.White,
                    fontSize = 16.sp,
                    fontFamily = AppFont.Poppins,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = content,
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 14.sp,
                fontFamily = AppFont.Helvetica,
                lineHeight = 22.sp
            )
        }
    }
}