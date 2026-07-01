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
import com.example.remusic.data.HomeCacheManager
import com.example.remusic.data.SupabaseManager
import com.example.remusic.data.UserManager
import com.example.remusic.utils.LockScreenOrientationPortrait
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

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

            // ── STEP 1: Cek cache lokal (ZERO network, instan) ──────────────────
            val hasCachedUser = UserManager.hasCachedUser()
            Log.d(TAG, "🗄️ [SplashScreen] Ada user di cache lokal? $hasCachedUser")

            // ── STEP 2: Coba load session dari storage lokal Supabase ───────────
            // loadFromStorage() membaca dari SharedPreferences Supabase — tidak hit network
            val hasStoredSession = try {
                SupabaseManager.client.auth.loadFromStorage()
            } catch (e: Exception) {
                Log.w(TAG, "⚠️ [SplashScreen] Gagal loadFromStorage: ${e.message}")
                false
            }
            Log.d(TAG, "📦 [SplashScreen] Ada session tersimpan di storage? $hasStoredSession")

            if (hasStoredSession || hasCachedUser) {
                // User pernah login sebelumnya — coba load data user dengan timeout pendek
                Log.d(TAG, "🔐 [SplashScreen] Indikasi user pernah login. Memuat data user...")

                // ── STEP 3: Validasi session secara aktif ────────────────────────
                // Strategi berlapis:
                // Coba 1: refreshCurrentSession() → token baru dari Supabase
                // Coba 2: retrieveUserForCurrentSession() → validasi dari server tanpa refresh
                // Coba 3: Offline/timeout → pakai cache lokal jika ada
                // Hanya redirect login jika server secara eksplisit menolak (401 token revoked/hangus)
                var sessionValid = false
                var isExplicitAuthError = false

                val validationResult = withTimeoutOrNull(8000L) {
                    // ── Percobaan 1: Refresh session (dapatkan access token baru) ─────────
                    try {
                        SupabaseManager.client.auth.refreshCurrentSession()
                        Log.d(TAG, "✅ [SplashScreen] Session berhasil di-refresh.")
                        "valid"
                    } catch (e1: Exception) {
                        val msg1 = (e1.message?.lowercase() ?: "") + " " + (e1.cause?.message?.lowercase() ?: "")
                        Log.w(TAG, "⚠️ [SplashScreen] refreshCurrentSession gagal: ${e1.message}")

                        val isNetworkError = msg1.contains("unable to resolve host") ||
                                msg1.contains("failed to connect") ||
                                msg1.contains("no route to host") ||
                                msg1.contains("connection refused") ||
                                msg1.contains("network is unreachable") ||
                                msg1.contains("socketexception") ||
                                msg1.contains("unknownhostexception") ||
                                msg1.contains("java.net.connect") ||
                                e1 is java.net.UnknownHostException ||
                                e1 is java.net.ConnectException ||
                                e1 is java.net.SocketException ||
                                e1.cause is java.net.UnknownHostException ||
                                e1.cause is java.net.ConnectException

                        if (isNetworkError) {
                            Log.w(TAG, "🌐 [SplashScreen] Offline saat refresh. Pakai cache.")
                            "offline"
                        } else {
                            // Refresh gagal — tapi ini BUKAN berarti langsung logout!
                            // Refresh token mungkin sudah mati, tapi access token yang tersimpan
                            // masih bisa dipakai. Coba validasi user secara langsung.
                            Log.w(TAG, "🔄 [SplashScreen] Refresh gagal. Coba retrieveUser...")
                            try {
                                // retrieveUser dengan updateSession=true akan me-re-use access token
                                // yang tersimpan untuk hit endpoint /user, membuktikan session masih ada.
                                SupabaseManager.client.auth.retrieveUserForCurrentSession(updateSession = true)
                                Log.d(TAG, "✅ [SplashScreen] Session valid via retrieveUser.")
                                "valid"
                            } catch (e2: Exception) {
                                val msg2 = (e2.message?.lowercase() ?: "") + " " + (e2.cause?.message?.lowercase() ?: "")
                                Log.e(TAG, "❌ [SplashScreen] retrieveUser juga gagal: ${e2.message}")

                                val isNetworkError2 = msg2.contains("unable to resolve host") ||
                                        msg2.contains("failed to connect") ||
                                        msg2.contains("no route to host") ||
                                        msg2.contains("socketexception") ||
                                        msg2.contains("unknownhostexception") ||
                                        e2 is java.net.UnknownHostException ||
                                        e2 is java.net.ConnectException ||
                                        e2 is java.net.SocketException

                                if (isNetworkError2) {
                                    Log.w(TAG, "🌐 [SplashScreen] Offline di percobaan 2. Pakai cache.")
                                    "offline"
                                } else {
                                    // Kedua cara gagal dengan auth error → cek apakah ini
                                    // benar-benar token revoked/hangus atau hanya error sementara
                                    val isHardAuthError = msg2.contains("invalid refresh token") ||
                                            msg2.contains("token is expired") ||
                                            msg2.contains("refresh_token_not_found") ||
                                            msg2.contains("user not found") ||
                                            msg2.contains("invalid token") ||
                                            msg2.contains("jwt expired") ||
                                            msg2.contains("401") ||
                                            msg2.contains("403")

                                    if (isHardAuthError) {
                                        Log.e(TAG, "🚫 [SplashScreen] Token DITOLAK server. Harus login ulang.")
                                        isExplicitAuthError = true
                                        "auth_error"
                                    } else {
                                        // Error tidak dikenal (server error, dll) → jangan logout paksa!
                                        // Anggap offline dan pakai cache untuk keamanan user experience
                                        Log.w(TAG, "❓ [SplashScreen] Error tidak dikenal. Anggap offline, pakai cache.")
                                        "offline"
                                    }
                                }
                            }
                        }
                    }
                }

                when (validationResult) {
                    "valid" -> {
                        sessionValid = true
                    }
                    "offline", null -> {
                        // null = timeout → anggap offline, pakai cache
                        Log.w(TAG, "⏱️ [SplashScreen] Offline/timeout. Pakai session cache lokal.")
                        // Hanya izinkan masuk jika memang ada cached user (pernah login)
                        sessionValid = hasCachedUser
                    }
                    else -> {
                        // "auth_error" → session hangus (hanya jika isExplicitAuthError = true)
                        sessionValid = false
                    }
                }

                if (!sessionValid) {
                    // Session expired/revoked → bersihkan semua data stale
                    Log.w(TAG, "🧹 [SplashScreen] Membersihkan session & cache stale...")
                    try { SupabaseManager.client.auth.signOut() } catch (_: Exception) {}
                    UserManager.clearUser()
                    UserManager.saveCachedFcmToken(null)
                    HomeCacheManager.clear()

                    navController.navigate("login") {
                        popUpTo("splash") { inclusive = true }
                    }
                    return@launch
                }

                // ── STEP 4: Session valid, load data user ───────────────────────
                val networkUser = withTimeoutOrNull(5000L) {
                    UserManager.ensureUserLoaded()
                }

                val finalUser = networkUser ?: UserManager.currentUser

                if (finalUser != null) {
                    Log.d(TAG, "✅ [SplashScreen] User siap: ${finalUser.displayName}. Navigasi ke Main.")
                } else {
                    // Session valid tapi data user tidak bisa diambil
                    // (kemungkinan offline total dan cache belum ada profil lengkap)
                    // Tetap masuk main agar musik offline bisa diputar!
                    Log.w(TAG, "⚠️ [SplashScreen] User data null tapi session valid. Masuk Main (offline mode).")
                }

                navController.navigate("main") {
                    popUpTo("splash") { inclusive = true }
                }

            } else {
                // Benar-benar belum pernah login — tidak ada session & tidak ada cache
                Log.d(TAG, "🔓 [SplashScreen] Tidak ada sesi & tidak ada cache. Navigasi ke Login.")
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