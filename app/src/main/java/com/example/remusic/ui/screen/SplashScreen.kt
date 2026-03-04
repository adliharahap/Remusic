package com.example.remusic.ui.screen

import android.Manifest
import android.os.Build
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.remusic.R
import com.example.remusic.data.SupabaseManager
import com.example.remusic.utils.LockScreenOrientationPortrait
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// --- Design Tokens ---
private val BgDark = Color(0xFF0A0A0C) // Hampir hitam legam yang premium
private val AccentBrand = Color(0xFF6C63FF) // Ungu elegan
private val AccentSecondary = Color(0xFF00F2EA) // Cyan untuk sedikit sentuhan modern

@Composable
fun SplashScreen(navController: NavController) {
    LockScreenOrientationPortrait()

    // --- State Animasi (Lebih halus dan terkoordinasi) ---
    val logoScale = remember { Animatable(0.8f) }
    val logoAlpha = remember { Animatable(0f) }
    val textOffsetY = remember { Animatable(20f) } // Slide up effect
    val textAlpha = remember { Animatable(0f) }
    val loaderAlpha = remember { Animatable(0f) }

    // --- LOGIKA ORIGINAL (TIDAK DIUBAH SAMA SEKALI) ---
    val permissionsToRequest = remember {
        val perms = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
            perms.add(Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            perms.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        perms.toTypedArray()
    }

    val proceedToApp = {
        CoroutineScope(Dispatchers.Main).launch {
            val TAG = "RemusicAuth"
            val session = SupabaseManager.client.auth.currentSessionOrNull()
            Log.d(TAG, "🔍 [SplashScreen] Memeriksa sesi... Session ID: ${session?.user?.id}")

            if (session != null) {
                Log.d(TAG, "🔐 [SplashScreen] Session ditemukan. Memastikan user data tersedia...")
                val user = com.example.remusic.data.UserManager.ensureUserLoaded()

                if (user != null) {
                    Log.d(TAG, "✅ [SplashScreen] User siap: ${user.displayName}. Navigasi ke Main.")
                    navController.navigate("main") {
                        popUpTo("splash") { inclusive = true }
                    }
                } else {
                    Log.w(TAG, "⚠️ [SplashScreen] User data null. Masuk sebagai auth-only.")
                    navController.navigate("main") {
                        popUpTo("splash") { inclusive = true }
                    }
                }
            } else {
                Log.d(TAG, "🔓 [SplashScreen] Tidak ada session. Navigasi ke Login.")
                navController.navigate("login") {
                    popUpTo("splash") { inclusive = true }
                }
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { permissions ->
            Log.d("SplashScreen", "Permission results: $permissions")
            proceedToApp()
        }
    )
    // --- AKHIR LOGIKA ORIGINAL ---

    // Menjalankan sekuens animasi
    LaunchedEffect(key1 = true) {
        // 1. Munculkan logo perlahan
        launch {
            logoScale.animateTo(1f, tween(800, easing = FastOutSlowInEasing))
        }
        launch {
            logoAlpha.animateTo(1f, tween(800))
        }
        
        delay(200) // Staggered delay untuk efek berurutan

        // 2. Teks nama aplikasi slide up dan fade in
        launch {
            textOffsetY.animateTo(0f, tween(600, easing = FastOutSlowInEasing))
        }
        launch {
            textAlpha.animateTo(1f, tween(600))
        }

        delay(300)

        // 3. Tampilkan Audio Visualizer Loader di bawah
        launch {
            loaderAlpha.animateTo(1f, tween(500))
        }

        delay(400) // Sedikit jeda tambahan agar animasi selesai sebelum pop up permission

        // 4. Minta Permission
        permissionLauncher.launch(permissionsToRequest)
    }

    // --- UI BARU ---
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark), // Solid background, sleek dan clean
        contentAlignment = Alignment.Center
    ) {
        // --- Bagian Tengah (Logo & Nama Aplikasi) ---
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.app_logo),
                contentDescription = "App Logo",
                modifier = Modifier
                    .size(110.dp)
                    .graphicsLayer {
                        scaleX = logoScale.value
                        scaleY = logoScale.value
                        alpha = logoAlpha.value
                    }
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Nama Aplikasi dengan efek teks gradient
            Text(
                text = "ReMusic",
                style = TextStyle(
                    brush = Brush.linearGradient(
                        colors = listOf(Color.White, Color.White.copy(alpha = 0.7f))
                    )
                ),
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                modifier = Modifier.graphicsLayer {
                    translationY = textOffsetY.value
                    alpha = textAlpha.value
                }
            )
        }

        // --- Bagian Bawah (Audio Visualizer & Tagline) ---
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 60.dp)
                .alpha(loaderAlpha.value),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AudioVisualizerLoader()
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "YOUR MUSIC, REIMAGINED",
                color = Color.White.copy(alpha = 0.4f),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 3.sp
            )
        }
    }
}

/**
 * Pengganti JumpingDots yang kekanak-kanakan.
 * Ini adalah animasi bar equalizer yang bergerak naik turun selayaknya
 * musik sedang dimainkan. Sangat cocok untuk aplikasi streaming musik.
 */
@Composable
fun AudioVisualizerLoader(
    modifier: Modifier = Modifier,
    barColor: Color = AccentBrand,
    barWidth: Dp = 4.dp,
    spaceBetween: Dp = 6.dp,
    maxHeight: Dp = 24.dp,
    minHeight: Dp = 8.dp
) {
    // 4 baris equalizer dengan delay dan durasi animasi yang berbeda
    // agar terlihat acak dan natural seperti gelombang suara
    val animations = listOf(
        remember { Animatable(minHeight.value) },
        remember { Animatable(minHeight.value) },
        remember { Animatable(minHeight.value) },
        remember { Animatable(minHeight.value) }
    )

    val configs = listOf(
        Pair(0L, 400),
        Pair(150L, 600),
        Pair(50L, 350),
        Pair(250L, 500)
    )

    animations.forEachIndexed { index, animatable ->
        val (startDelay, duration) = configs[index]
        LaunchedEffect(animatable) {
            delay(startDelay)
            animatable.animateTo(
                targetValue = maxHeight.value,
                animationSpec = infiniteRepeatable(
                    animation = keyframes {
                        durationMillis = duration * 2
                        maxHeight.value at duration
                        minHeight.value at duration * 2
                    },
                    repeatMode = RepeatMode.Reverse
                )
            )
        }
    }

    Row(
        modifier = modifier.height(maxHeight),
        horizontalArrangement = Arrangement.spacedBy(spaceBetween),
        verticalAlignment = Alignment.CenterVertically
    ) {
        animations.forEach { anim ->
            Box(
                modifier = Modifier
                    .width(barWidth)
                    .height(anim.value.dp)
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(AccentSecondary, barColor)
                        ),
                        shape = RoundedCornerShape(50)
                    )
            )
        }
    }
}