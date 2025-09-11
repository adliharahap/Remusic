package com.example.remusic.ui.screen

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.remusic.R // Pastikan untuk mengimpor R dari package Anda
import com.example.remusic.utils.LockScreenOrientationPortrait
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(navController: NavController) {
    LockScreenOrientationPortrait()

    // Kumpulan state animasi untuk kontrol yang lebih detail
    val logoScale = remember { Animatable(0f) }
    val loadingAlpha = remember { Animatable(0f) }
    val textAlpha = remember { Animatable(0f) }
    val subtextAlpha = remember { Animatable(0f) }

    // Rangkaian animasi yang lebih kompleks
    LaunchedEffect(key1 = true) {
        // 1. Logo muncul dengan animasi scale up
        logoScale.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 800)
        )

        // 2. Loading indicator muncul
        loadingAlpha.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 300)
        )
        delay(1500L) // Jeda untuk simulasi loading

        // 3. Loading indicator hilang, teks utama muncul
        loadingAlpha.animateTo(
            targetValue = 0f,
            animationSpec = tween(durationMillis = 300)
        )
        textAlpha.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 500)
        )
        subtextAlpha.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 500)
        )

        // Tunggu sejenak sebelum navigasi
        delay(1200L)

        // Navigasi ke home screen
        navController.navigate("main") {
            popUpTo("splash") { inclusive = true }
        }
    }

    // Warna untuk gradien radial
    val gradientColors = listOf(
        Color(0xFF441088),
        Color(0xFF121212)
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.radialGradient(
                    colors = gradientColors,
                    radius = 1200f
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        // Konten utama di tengah (Logo, Judul, Loading)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Logo dibuat bulat dengan border dan animasi scale
            Image(
                painter = painterResource(id = R.drawable.app_logo),
                contentDescription = "App Logo",
                modifier = Modifier
                    .size(140.dp * logoScale.value) // Terapkan animasi scale
                    .clip(CircleShape) // Membuat gambar menjadi bulat
                    .border(
                        width = 2.dp,
                        color = Color.White.copy(alpha = 0.5f),
                        shape = CircleShape
                    )
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Box untuk menampung antara Loading dan Judul
            Box(contentAlignment = Alignment.Center, modifier = Modifier.height(48.dp)) {
                // Loader 3 titik yang baru
                JumpingDotsLoader(
                    modifier = Modifier.alpha(loadingAlpha.value)
                )

                // Nama Aplikasi
                Text(
                    text = "ReMusic",
                    color = Color.White,
                    fontSize = 44.sp,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.headlineLarge,
                    modifier = Modifier.alpha(textAlpha.value)
                )
            }
        }

        // Subteks di bagian bawah layar
        Text(
            text = "Your Music, Reimagined",
            color = Color.White.copy(alpha = 0.8f),
            fontSize = 16.sp,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 48.dp)
                .alpha(subtextAlpha.value)
        )
    }
}

@Composable
fun JumpingDotsLoader(
    modifier: Modifier = Modifier,
    dotSize: Dp = 10.dp,
    dotColor: Color = Color.White,
    spaceBetween: Dp = 4.dp,
    jumpHeight: Dp = 15.dp
) {
    val dots = listOf(
        remember { Animatable(0f) },
        remember { Animatable(0f) },
        remember { Animatable(0f) }
    )

    dots.forEachIndexed { index, animatable ->
        LaunchedEffect(animatable) {
            // Delay untuk membuat animasi setiap titik berbeda
            delay(index * 150L)
            animatable.animateTo(
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = keyframes {
                        durationMillis = 1000
                        0f at 0
                        -jumpHeight.value at 250
                        0f at 500
                        0f at 1000
                    },
                    repeatMode = RepeatMode.Restart
                )
            )
        }
    }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(spaceBetween)
    ) {
        dots.forEach { anim ->
            Box(
                modifier = Modifier
                    .size(dotSize)
                    .offset(y = anim.value.dp)
                    .background(
                        color = dotColor,
                        shape = CircleShape
                    )
            )
        }
    }
}
