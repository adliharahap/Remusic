package com.example.remusic.ui.screen

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.remusic.R
import com.example.remusic.ui.theme.AppFont

@Composable
fun LoginScreen(onGoogleSignInClick: () -> Unit) {

    // --- State untuk mengontrol animasi muncul (Fade & Slide In) ---
    var startAnimation by remember { mutableStateOf(false) }
    val alphaAnim = remember { Animatable(0f) }
    val yOffsetAnim = remember { Animatable(100f) }

    // --- Animasi untuk logo berdenyut (Pulsating) ---
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "pulse_scale"
    )

    // --- Memicu animasi saat Composable pertama kali masuk ke layar ---
    LaunchedEffect(key1 = true) {
        startAnimation = true
        // Animate alpha (fade in)
        alphaAnim.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 800)
        )
    }
    LaunchedEffect(key1 = startAnimation) {
        if(startAnimation) {
            // Animate Y offset (slide up)
            yOffsetAnim.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 800)
            )
        }
    }


    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        // 🔹 Background Image
        Image(
            painter = painterResource(id = R.drawable.bg_login),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize()
        )

        // 🔹 Overlay
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(Color.Black.copy(alpha = 0.8f))
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                // Terapkan animasi pada seluruh Column
                .alpha(alphaAnim.value)
                .offset(y = yOffsetAnim.value.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Image(
                modifier = Modifier
                    .height(140.dp)
                    .width(140.dp)
                    .padding(bottom = 10.dp)
                    .scale(pulseScale), // Terapkan animasi denyut di sini
                painter = painterResource(id = R.drawable.app_logo),
                contentDescription = null,
            )
            Text(
                text = "ReMusic",
                color = Color.White,
                modifier = Modifier.padding(bottom = 10.dp),
                fontSize = 34.sp,
                fontFamily = AppFont.RobotoBold
            )
            Text(
                text = "Temukan, Bagikan, Nikmati",
                color = Color.White.copy(0.8f),
                modifier = Modifier.padding(bottom = 10.dp),
                fontSize = 17.sp,
                fontFamily = AppFont.PoppinsMedium
            )
        }

        // 🔹 Tombol Google di bawah layar
        Button(
            onClick = onGoogleSignInClick,
            colors = ButtonDefaults.buttonColors(containerColor = Color.White),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 15.dp, end = 15.dp, bottom = 60.dp)
                .fillMaxWidth()
                // Terapkan animasi pada tombol juga
                .alpha(alphaAnim.value)
                .offset(y = yOffsetAnim.value.dp),
            shape = RoundedCornerShape(20),
        ) {
            Image(
                painter = painterResource(id = R.drawable.google),
                contentDescription = "Google Icon",
                modifier = Modifier
                    .size(34.dp)
                    .padding(end = 8.dp)
            )
            Text(
                text = "Login with Google",
                color = Color.Black,
                fontSize = 16.sp,
                fontFamily = AppFont.RobotoMedium
            )
        }
    }
}