package com.example.remusic

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import com.google.firebase.messaging.FirebaseMessaging
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Block
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.ZoneId
import java.util.Locale
import androidx.core.view.WindowCompat
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.example.remusic.data.HomeCacheManager
import com.example.remusic.data.SupabaseManager
import com.example.remusic.data.UserManager
import com.example.remusic.navigation.AppNavGraph
import com.example.remusic.ui.theme.ReMusicTheme
import com.example.remusic.utils.ConnectivityObserver
import com.example.remusic.utils.NetworkConnectivityObserver
import com.example.remusic.utils.NotificationUtils
import com.example.remusic.viewmodel.playmusic.PlayMusicViewModel
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.IDToken
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers

class MainActivity : ComponentActivity() {
    // Hapus variable auth: FirebaseAuth
    private lateinit var credentialManager: CredentialManager

    // Inisialisasi Observer
    private lateinit var connectivityObserver: ConnectivityObserver

    private val playMusicViewModel: PlayMusicViewModel by viewModels()

    private var showDialog by mutableStateOf(false)
    private var errorMessage by mutableStateOf("")
    
    // State global untuk menampung intent baru (workaround simple untuk Compose)
    private var pendingNavigationRoute by mutableStateOf<String?>(null)

    override fun onNewIntent(intent: android.content.Intent) { 
        super.onNewIntent(intent)
        setIntent(intent) // Update intent terkini
        val route = intent.getStringExtra("destination_route")
        if (route != null) {
            Log.d("MainActivity", "🚀 onNewIntent received route: $route")
            pendingNavigationRoute = route
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        connectivityObserver = NetworkConnectivityObserver(applicationContext)
        SupabaseManager.initialize(applicationContext)
        UserManager.init(applicationContext)         // Load cached user dari SharedPreferences
        HomeCacheManager.init(applicationContext)    // Load cached home data dari SharedPreferences

        credentialManager = CredentialManager.create(this)
        NotificationUtils.createNotificationChannel(this)
        val notificationRoute = intent.getStringExtra("destination_route")

        // --- COIL GLOBAL IMAGE LOADER CONFIGURATION ---
        val imageLoader = coil.ImageLoader.Builder(this)
            .memoryCache {
                coil.memory.MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                coil.disk.DiskCache.Builder()
                    .directory(this.cacheDir.resolve("image_cache"))
                    .maxSizePercent(0.02)
                    .build()
            }
            // Tambahkan interceptor untuk Retry 10x dengan jeda bertahap (1s, 2s, 3s, 4s, max 5s)
            .components {
                add(coil.intercept.Interceptor { chain ->
                    var response: coil.request.ImageResult? = null
                    var attempt = 0
                    val maxRetries = 5

                    // Retry loop
                    while (attempt < maxRetries) {
                        attempt++
                        try {
                            // Coba jalankan request
                            response = chain.proceed(chain.request)
                            // Jika sukses (SuccessResult), keluar loop
                            if (response is coil.request.SuccessResult) {
                                break
                            } else if (response is coil.request.ErrorResult) {
                                // Jika error, lempar exception untuk ditangkap dan di-retry
                                throw response.throwable
                            }
                        } catch (e: Exception) {
                            Log.e("CoilRetry", "Attempt $attempt/$maxRetries failed for ${chain.request.data}: ${e.message}")
                            if (attempt >= maxRetries) {
                                // Jika sudah maksimal, kembalikan ErrorResult
                                response = coil.request.ErrorResult(
                                    drawable = chain.request.error,
                                    request = chain.request,
                                    throwable = e
                                )
                            }
                        }
                        // Jika belum sukses dan masih ada sisa attempt, tunggu dengan jeda bertahap
                        if (response == null || response is coil.request.ErrorResult) {
                            if (attempt < maxRetries) {
                                // Jeda: 1 detik, 2 detik, ..., maks 5 detik
                                val delaySeconds = if (attempt <= 5) attempt else 5
                                val retryDelayMillis = delaySeconds * 1000L
                                delay(retryDelayMillis) // Suspend properly without blocking the thread
                            }
                        }
                    }
                    response ?: coil.request.ErrorResult(
                        drawable = chain.request.error,
                        request = chain.request,
                        throwable = IllegalStateException("Unknown Error after $maxRetries attempts")
                    )
                })
            }
            .respectCacheHeaders(false) // Paksa pakai cache lokal kita meski server bilang jangan
            .build()
        coil.Coil.setImageLoader(imageLoader)
        // ----------------------------------------------

        WindowCompat.setDecorFitsSystemWindows(window, false)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            )
        )

        setContent {
            ReMusicTheme {
                val navController = rememberNavController()

                // State untuk menyimpan tujuan awal (default null dulu)
                var startDestination by remember { mutableStateOf<String?>(null) }



                // State untuk loading screen (biar layar gak putih doang)
                var isLoadingSession by remember { mutableStateOf(true) }

                // Ambil status koneksi secara real-time
                val status by connectivityObserver.observe().collectAsState(
                    initial = ConnectivityObserver.Status.Lost
                )

                // LOGIKA SMART RETRY: Player Check
                LaunchedEffect(status) {
                    if (status == ConnectivityObserver.Status.Available) {
                        Log.d("MainActivity", "🌐 Main: Internet Available -> Triggering Player Retry")
                        playMusicViewModel.onConnectivityRestored()
                    }
                }

                // LOGIKA BARU: Cek Login secara Asynchronous
                LaunchedEffect (Unit) {
                    val TAG = "RemusicAuth" // Tag khusus debug auth
                    try {
                        // KITA PAKSA LOAD DARI STORAGE
                        Log.d(TAG, "🚀 [MainActivity] LaunchedEffect triggered. Mencoba membaca sesi dari penyimpanan HP...")
                        val isSessionRestored = SupabaseManager.client.auth.loadFromStorage()
                        Log.d(TAG, "📦 [MainActivity] Apakah sesi berhasil di-restore dari storage? $isSessionRestored")

                        if (isSessionRestored) {
                            // --- FIX: WAIT FOR SESSION STATUS ---
                            Log.d(TAG, "⏳ [MainActivity] Menunggu status sesi stabil (bukan Initializing)...")
                            
                            // Tunggu status berubah dari Initializing (Maks 10 detik)
                            val finalStatus = withTimeoutOrNull(10000) {
                                SupabaseManager.client.auth.sessionStatus.first { status ->
                                    Log.d(TAG, "👀 [MainActivity] Observing Status: $status")
                                    status !is SessionStatus.Initializing
                                }
                            }

                            Log.d(TAG, "🔔 [MainActivity] Status Akhir (setelah wait): $finalStatus")

                            if (finalStatus is SessionStatus.Authenticated) {
                                // Jika sesi ketemu dan Authenticated, ambil data user
                                val user = SupabaseManager.client.auth.currentUserOrNull()
                                Log.d(TAG, "👤 [MainActivity] User Login Session: ${user?.email} (ID: ${user?.id})")
                                
                                // --- PERBAIKAN: WAJIB TUNGGU SAMPAI USER DATA TERLOAD ---
                                if (user != null) {
                                    try {
                                        Log.d(TAG, "🔄 [MainActivity] Fetching user data lengkap dari database...")
                                        // BLOCKING: tunggu sampai user data berhasil di-fetch
                                        try {
                                            UserManager.fetchCurrentUser(user.id)
                                        } catch (e: Exception) {
                                            Log.w(TAG, "⚠️ [MainActivity] Gagal fetch user detail: ${e.message}. Tetap lanjut karena sesi valid.")
                                        }
                                        
                                        // Cek apakah berhasil
                                        if (UserManager.currentUser != null) {
                                            Log.d(TAG, "✅ [MainActivity] User data berhasil dimuat: ${UserManager.currentUser?.displayName}")
                                            updateFcmToken()
                                            startDestination = "splash" // Langsung masuk splash/main
                                        } else {
                                            Log.w(TAG, "⚠️ [MainActivity] User data null setelah fetch. Masuk sebagai User tanpa profil lengkap.")
                                            // JANGAN LOGOUT! Tetap masuk.
                                            startDestination = "splash"
                                        }
                                    } catch (e: Exception) {
                                        Log.e(TAG, "❌ [MainActivity] Error tidak terduga saat fetch user: ${e.message}")
                                        // Tetap lanjut, jangan logout sembarangan
                                        startDestination = "splash"
                                    }
                                } else {
                                    Log.e(TAG, "❌ [MainActivity] Session Authenticated tapi user null. Logout paksa.")
                                    SupabaseManager.client.auth.signOut()
                                    startDestination = "login"
                                }
                            } else {
                                Log.w(TAG, "⛔ [MainActivity] Status sesi tidak valid ($finalStatus). Masuk Login.")
                                startDestination = "login"
                            }
                        } else {
                            Log.d(TAG, "🔓 [MainActivity] Tidak ada sesi tersimpan (loadFromStorage false). Masuk ke Login.")
                            startDestination = "login"
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "💥 [MainActivity] Error CRITICAL saat load sesi", e)
                        startDestination = "login"
                    } finally {
                        Log.d(TAG, "🏁 [MainActivity] Selesai cek sesi. Destination: $startDestination")
                        isLoadingSession = false
                    }
                }

                // TAMPILAN UI
                if (isLoadingSession || startDestination == null) {
//                    // Tampilkan Loading Screen sementara menunggu pengecekan file selesai
//                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
//                        CircularProgressIndicator()
//                    }
                } else {
                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        contentWindowInsets = WindowInsets(0, 0, 0, 0),
                    ) { innerPadding ->
                        Box(modifier = Modifier.padding(innerPadding)) {
                            Column(modifier = Modifier.fillMaxSize()) {
                                Box(modifier = Modifier.weight(1f)) {
                                    AppNavGraph(
                                        navController = navController,
                                        startDestination = startDestination!!,
                                        onGoogleSignInClick = { signInWithGoogle {
                                            navController.navigate("main") {
                                                popUpTo("login") { inclusive = true }
                                            }
                                        } },
                                        // Prioritaskan pendingNavigationRoute (dari onNewIntent) jika ada,
                                        // jika tidak, pakai notificationRoute (dari intent awal)
                                        notificationRoute = pendingNavigationRoute ?: notificationRoute,
                                        onRouteConsumed = {
                                            pendingNavigationRoute = null
                                            intent.removeExtra("destination_route") // Optional: bersihkan intent lama juga
                                        },
                                        playMusicViewModel = playMusicViewModel,
                                        connectivityObserver = connectivityObserver
                                    )

                                    // 3. Layer Paling Atas: Dialog Error (Tetap Overlay)
                                    if (showDialog) {
                                        LoginErrorDialog(
                                            show = showDialog,
                                            errorMessage = errorMessage,
                                            onDismiss = { showDialog = false }
                                        )
                                    }

                                    // 4. Banned Modal
                                    val currentUser = UserManager.currentUser
                                    if (currentUser?.bannedUntil != null) {
                                        BannedUserDialog(
                                            bannedUntil = currentUser.bannedUntil,
                                            banReason = currentUser.banReason,
                                            onLogoutClick = {
                                                com.example.remusic.utils.AuthUtils.logout(this@MainActivity, navController)
                                            }
                                        )
                                    }
                                }

                                // 2. Layer Bawah: Notifikasi Offline (Termakan Sedikit Layar)
                                OfflineBanner(status = status)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun signInWithGoogle(onSuccess: () -> Unit) {
        val serverClientId = BuildConfig.GOOGLE_SERVER_CLIENT_ID
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(serverClientId)
            .build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()
        lifecycleScope.launch {
            try {
                val result = credentialManager.getCredential(this@MainActivity, request)
                handleSignIn(result.credential, onSuccess)
            } catch (e: Exception) {
                if (e !is androidx.credentials.exceptions.GetCredentialCancellationException) {
                    errorMessage = "Login Gagal: ${e.message}"
                    showDialog = true
                }
            }
        }
    }

    private fun handleSignIn(credential: androidx.credentials.Credential, onSuccess: () -> Unit) {
        if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data)
            supabaseAuthWithGoogle(googleIdToken.idToken, onSuccess)
        }
    }

    // FUNGSI INI MENGGANTIKAN 'firebaseAuthWithGoogle' DAN 'checkAndCreateUserInFirestore'
    private fun supabaseAuthWithGoogle(idToken: String, onSuccess: () -> Unit) {
        lifecycleScope.launch {
            try {
                Log.d("SUPABASE_AUTH", "Sending ID Token to Supabase...")

                // 1. Sign In ke Supabase menggunakan ID Token dari Google
                SupabaseManager.client.auth.signInWith(IDToken) {
                    this.idToken = idToken
                    this.provider = Google
                }

                Log.d("SUPABASE_AUTH", "Login Supabase Berhasil!")

                // 2. Trigger SQL di backend otomatis membuat data user di public.users
                // Kita hanya perlu memastikan data lokal terupdate
                val userId = SupabaseManager.client.auth.currentUserOrNull()?.id
                if (userId != null) {
                    // Refresh data user di UserManager
                    UserManager.fetchCurrentUser(userId)
                    updateFcmToken()
                }

                Toast.makeText(this@MainActivity, "Login Berhasil!", Toast.LENGTH_SHORT).show()
                onSuccess() // Navigasi ke Main Screen

            } catch (e: Exception) {
                Log.e("SUPABASE_AUTH", "Login Supabase Gagal", e)
                // Tampilkan pesan error yang ramah user
                errorMessage = when {
                    e.message?.contains("network") == true -> "Masalah koneksi internet."
                    else -> "Login Gagal: ${e.message}"
                }
                showDialog = true
            }
        }
    }

    @Composable
    fun LoginErrorDialog(
        show: Boolean,
        errorMessage: String,
        onDismiss: () -> Unit
    ) {
        if (show) {
            AlertDialog(
                onDismissRequest = { onDismiss() },
                title = { Text(text = "Login Gagal") },
                text = { Text(text = errorMessage) },
                confirmButton = {
                    Button(onClick = { onDismiss() }) {
                        Text("OK")
                    }
                }
            )
        }
    }

    private fun updateFcmToken() {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                Log.w("FCM_TEST", "Gagal dapat token FCM", task.exception)
                return@addOnCompleteListener
            }
            val token = task.result
            
            // CEK CACHE LOKAL DULU:
            val cachedToken = com.example.remusic.data.UserManager.getCachedFcmToken()
            if (cachedToken == token) {
                Log.d("FCM_TEST", "Token FCM tidak berubah, skip update ke database.")
                return@addOnCompleteListener
            }

            val user = SupabaseManager.client.auth.currentUserOrNull()
            if (user != null) {
                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        SupabaseManager.client.from("users").update(
                            {
                                set("fcm_token", token)
                            }
                        ) {
                            filter { eq("id", user.id) }
                        }
                        // Jika berhasil update DB, simpan ke cache lokal
                        com.example.remusic.data.UserManager.saveCachedFcmToken(token)
                        Log.d("FCM_TEST", "Berhasil update FCM Token ke DB: $token")
                    } catch (e: Exception) {
                        Log.e("FCM_TEST", "Gagal update FCM Token ke DB", e)
                    }
                }
            } else {
                Log.w("FCM_TEST", "User belum login, token tidak di-update: $token")
            }
        }
    }
}

@Composable
fun OfflineBanner(
    modifier: Modifier = Modifier,
    status: ConnectivityObserver.Status
) {
    // Derive visibility directly from status — no LaunchedEffect, no delay
    val isOffline = status == ConnectivityObserver.Status.Lost ||
                    status == ConnectivityObserver.Status.Unavailable

    val message = when (status) {
        ConnectivityObserver.Status.Limited  -> "Koneksi Terbatas"
        ConnectivityObserver.Status.Losing   -> "Koneksi Tidak Stabil"
        else                                 -> "Tidak ada koneksi internet"
    }

    AnimatedVisibility(
        visible = isOffline,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        modifier = modifier
    ) {
        Surface(
            color = Color(0xFFB00020),
            shape = androidx.compose.ui.graphics.RectangleShape,
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                Icon(
                    imageVector = Icons.Default.WifiOff,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = message,
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun BannedUserDialog(
    bannedUntil: String,
    banReason: String?,
    onLogoutClick: () -> Unit
) {
    val formattedDate = remember(bannedUntil) {
        try {
            val zdt = ZonedDateTime.parse(bannedUntil)
            val localZdt = zdt.withZoneSameInstant(ZoneId.systemDefault())
            val formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.LONG, FormatStyle.SHORT)
                .withLocale(Locale("id", "ID"))
            localZdt.format(formatter)
        } catch (e: Exception) {
            bannedUntil
        }
    }

    Dialog(
        onDismissRequest = { },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .width(340.dp)
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header Icon
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .background(Color(0xFFB00020).copy(alpha = 0.1f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Block,
                        contentDescription = "Banned",
                        tint = Color(0xFFB00020),
                        modifier = Modifier.size(36.dp)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "Akun Ditangguhkan",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Akses Anda ke aplikasi untuk sementara waktu dibatasi karena melanggar ketentuan layanan kami.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Info Box
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Column {
                            Text(
                                text = "Alasan Penangguhan",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = banReason ?: "Pelanggaran pedoman komunitas atau aktivitas mencurigakan.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Column {
                            Text(
                                text = "Estimasi Berakhir",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = formattedDate,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                Button(
                    onClick = onLogoutClick,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(50),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFB00020)
                    )
                ) {
                    Text(
                        text = "Keluar / Logout",
                        modifier = Modifier.padding(vertical = 6.dp),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}