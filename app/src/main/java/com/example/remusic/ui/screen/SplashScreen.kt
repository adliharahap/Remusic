package com.example.remusic.ui.screen

import android.util.Log
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
import com.example.remusic.R
import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.example.remusic.data.SupabaseManager
import com.example.remusic.utils.LockScreenOrientationPortrait
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SplashScreen(navController: NavController) {
    LockScreenOrientationPortrait()

    // Kumpulan state animasi
    val logoScale = remember { Animatable(0f) }
    val loadingAlpha = remember { Animatable(0f) }
    val textAlpha = remember { Animatable(0f) }
    val subtextAlpha = remember { Animatable(0f) }

    // Persiapkan daftar permission yang akan diminta berdasarkan versi Android
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

    // Fungsi untuk melanjutkan navigasi setelah permission diminta/sudah ada
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

    // Launcher untuk meminta permission
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { permissions ->
            // Apakah diizinkan atau ditolak, kita tetap lanjut ke aplikasi
            Log.d("SplashScreen", "Permission results: $permissions")
            proceedToApp()
        }
    )

    LaunchedEffect(key1 = true) {
        // 1. Animasi Logo & Teks Muncul Bersamaan (Lebih Cepat)
        logoScale.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 500)
        )
        textAlpha.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 300)
        )
        subtextAlpha.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 300)
        )

        // 2. Minta Permission (Ini akan meng-pause navigasi sampai user merespons)
        permissionLauncher.launch(permissionsToRequest)
    }

    // ... (SISA KODE UI KE BAWAH TIDAK PERLU DIUBAH, SAMA PERSIS) ...
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
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.app_logo),
                contentDescription = "App Logo",
                modifier = Modifier
                    .size(140.dp * logoScale.value)
                    .clip(CircleShape)
                    .border(
                        width = 2.dp,
                        color = Color.White.copy(alpha = 0.5f),
                        shape = CircleShape
                    )
            )

            Spacer(modifier = Modifier.height(24.dp))

            Box(contentAlignment = Alignment.Center, modifier = Modifier.height(48.dp)) {
                JumpingDotsLoader(
                    modifier = Modifier.alpha(loadingAlpha.value)
                )

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