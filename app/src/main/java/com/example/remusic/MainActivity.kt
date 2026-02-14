package com.example.remusic

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.core.view.WindowCompat
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
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
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    // Hapus variable auth: FirebaseAuth
    private lateinit var credentialManager: CredentialManager

    // Inisialisasi Observer
    private lateinit var connectivityObserver: ConnectivityObserver

    private val playMusicViewModel: PlayMusicViewModel by viewModels()

    private var showDialog by mutableStateOf(false)
    private var errorMessage by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        connectivityObserver = NetworkConnectivityObserver(applicationContext)
        SupabaseManager.initialize(applicationContext)

        credentialManager = CredentialManager.create(this)
        NotificationUtils.createNotificationChannel(this)
        val notificationRoute = intent.getStringExtra("destination_route")

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
                    initial = ConnectivityObserver.Status.Available
                )

                // LOGIKA BARU: Cek Login secara Asynchronous
                LaunchedEffect (Unit) {
                    try {
                        // KITA PAKSA LOAD DARI STORAGE
                        Log.d("AUTH_CHECK", "Mencoba membaca sesi dari penyimpanan HP...")
                        val isSessionRestored = SupabaseManager.client.auth.loadFromStorage()
                        Log.d("AUTH_CHECK", "Apakah sesi ditemukan? $isSessionRestored")

                        if (isSessionRestored) {
                            // Jika sesi ketemu, ambil data user
                            val user = SupabaseManager.client.auth.currentUserOrNull()
                            Log.d("AUTH_CHECK", "User Login: ${user?.email}")

                            // --- PERBAIKAN: WAJIB TUNGGU SAMPAI USER DATA TERLOAD ---
                            if (user != null) {
                                try {
                                    Log.d("AUTH_CHECK", "🔄 Fetching user data dari database...")
                                    // BLOCKING: tunggu sampai user data berhasil di-fetch
                                    try {
                                        UserManager.fetchCurrentUser(user.id)
                                    } catch (e: Exception) {
                                        Log.w("AUTH_CHECK", "⚠️ Gagal fetch user detail: ${e.message}. Tetap lanjut karena sesi valid.")
                                    }
                                    
                                    // Cek apakah berhasil
                                    if (UserManager.currentUser != null) {
                                        Log.d("AUTH_CHECK", "✅ User data berhasil dimuat: ${UserManager.currentUser?.displayName}")
                                        startDestination = "splash" // Langsung masuk splash/main
                                    } else {
                                        Log.w("AUTH_CHECK", "⚠️ User data null. Masuk sebagai User tanpa profil lengkap.")
                                        // JANGAN LOGOUT! Tetap masuk.
                                        startDestination = "splash"
                                    }
                                } catch (e: Exception) {
                                    Log.e("AUTH_CHECK", "❌ Error tidak terduga: ${e.message}")
                                    // Tetap lanjut, jangan logout sembarangan
                                    startDestination = "splash"
                                }
                            } else {
                                Log.e("AUTH_CHECK", "❌ Session ada tapi user null. Logout.")
                                SupabaseManager.client.auth.signOut()
                                startDestination = "login"
                            }
                        } else {
                            Log.d("AUTH_CHECK", "Tidak ada sesi tersimpan. Masuk Login.")
                            startDestination = "login"
                        }
                    } catch (e: Exception) {
                        Log.e("AUTH_CHECK", "Error saat load sesi", e)
                        startDestination = "login"
                    } finally {
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
                            AppNavGraph(
                                navController = navController,
                                startDestination = startDestination!!,
                                onGoogleSignInClick = { signInWithGoogle {
                                    navController.navigate("main") {
                                        popUpTo("login") { inclusive = true }
                                    }
                                } },
                                notificationRoute = notificationRoute,
                                playMusicViewModel = playMusicViewModel
                            )

                            // 2. Layer Tengah: Notifikasi Offline (Overlay)
                            if (status != ConnectivityObserver.Status.Available) {
                                OfflineBanner(
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .padding(bottom = 80.dp)
                                )
                            }

                            // 3. Layer Paling Atas: Dialog Error
                            if (showDialog) {
                                LoginErrorDialog(
                                    show = showDialog,
                                    errorMessage = errorMessage,
                                    onDismiss = { showDialog = false }
                                )
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
}

@Composable
fun OfflineBanner(modifier: Modifier = Modifier) {
    // Animasi biar munculnya halus (Slide dari bawah + Fade In)
    AnimatedVisibility(
        visible = true,
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
        modifier = modifier
    ) {
        Surface(
            color = Color.Red.copy(alpha = 0.9f), // Warna Merah Transparan
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .height(40.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                Icon(
                    imageVector = Icons.Default.WifiOff, // Ikon Wifi Mati
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Anda sedang Offline",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}