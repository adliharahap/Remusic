package com.example.remusic

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.example.remusic.data.UserManager
import com.example.remusic.navigation.AppNavGraph
import com.example.remusic.ui.theme.ReMusicTheme
import com.example.remusic.utils.NotificationUtils
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var auth: FirebaseAuth
    private lateinit var credentialManager: CredentialManager

    private var showDialog by mutableStateOf(false)
    private var errorMessage by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        auth = Firebase.auth
        credentialManager = CredentialManager.create(this)
        NotificationUtils.createNotificationChannel(this)
        val notificationRoute = intent.getStringExtra("destination_route")

        // 1. Cek status login di sini, SEBELUM setContent
        val isLoggedIn = auth.currentUser != null
        val startDestination = if (isLoggedIn) {
            "splash" // Jika sudah login, mulai dari splash screen
        } else {
            "login"  // Jika belum, mulai dari login screen
        }

        WindowCompat.setDecorFitsSystemWindows(window, false)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            )
        );

        setContent {
            ReMusicTheme {
                val navController = rememberNavController()

                // ✅ BUAT AKSI NAVIGASI DI SINI
                val onLoginSuccess: () -> Unit = {
                    // Arahkan ke "main" dan hapus "login" dari back stack
                    navController.navigate("main") {
                        popUpTo("login") {
                            inclusive = true
                        }
                    }
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    contentWindowInsets = WindowInsets(0, 0, 0, 0),
                ) { innerPadding ->
                    // Pastikan kita kasih padding biar UI-nya nggak ketiban
                    Box(modifier = Modifier.padding(innerPadding)) {
                        AppNavGraph(
                            navController = navController,
                            startDestination = startDestination,
                            onGoogleSignInClick = { signInWithGoogle(onLoginSuccess) },
                            notificationRoute = notificationRoute
                        )
                    }

                    LoginErrorDialog(
                        show = showDialog,
                        errorMessage = errorMessage,
                        onDismiss = { showDialog = false } // Sembunyikan dialog saat di-dismiss
                    )
                }
            }
        }
    }

    // ... fungsi-fungsi lain seperti signInWithGoogle(), handleSignIn(), dll.
    private fun signInWithGoogle(onSuccess: () -> Unit) {
        // Ganti dengan Web Client ID yang Anda salin dari Google Cloud Console
        val serverClientId = BuildConfig.GOOGLE_SERVER_CLIENT_ID

        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false) // Tampilkan semua akun, bukan hanya yang pernah login
            .setServerClientId(serverClientId)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        // Gunakan coroutine untuk memanggil Credential Manager
        lifecycleScope.launch {
            try {
                val result = credentialManager.getCredential(this@MainActivity, request)
                val credential = result.credential
                handleSignIn(credential, onSuccess)
            } catch (e: Exception) {
                Log.e("SIGN_IN_ERROR", "Login Gagal", e)
            }
        }
    }

    private fun handleSignIn(credential: androidx.credentials.Credential, onSuccess: () -> Unit) {
        if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            try {
                val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data)
                val idToken = googleIdToken.idToken
                // Kirim token ke Firebase untuk diautentikasi
                firebaseAuthWithGoogle(idToken, onSuccess)
            } catch (e: Exception) {
                Log.e("HANDLE_SIGN_IN_ERROR", "Gagal membuat kredensial", e)
            }
        } else {
            Log.w("CREDENTIAL_TYPE_ERROR", "Credential bukan tipe Google ID!")
        }
    }

    private fun firebaseAuthWithGoogle(idToken: String, onSuccess: () -> Unit) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    // Login sukses!
                    Log.d("FIREBASE_AUTH", "signInWithCredential:success");
                    val user = auth.currentUser;
                    if (user != null) {
//                        Log.d("DATA_FIREBASE_JSON", user.toPrettyJsonString())
                    }
                    // TODO: Arahkan ke halaman utama atau update UI
                    UserManager.setUser(user)
                    Toast.makeText(baseContext, "Login berhasil!", Toast.LENGTH_LONG).show();
                    onSuccess();
                } else {
                    // Login gagal
                    Log.w("FIREBASE_AUTH", "signInWithCredential:failure", task.exception)
                    // TODO: Tampilkan pesan error ke pengguna
                    errorMessage = task.exception?.localizedMessage ?: "Terjadi error yang tidak diketahui."
                    showDialog = true
                }
            }
    }

    // Anda bisa meletakkan ini di file terpisah atau di bawah MainActivity
    @Composable
    fun LoginErrorDialog(
        show: Boolean,
        errorMessage: String,
        onDismiss: () -> Unit
    ) {
        // Dialog hanya akan ditampilkan jika state 'show' adalah true
        if (show) {
            AlertDialog(
                onDismissRequest = { onDismiss() },
                title = { Text(text = "Login Gagal") },
                text = { Text(text = errorMessage) },
                confirmButton = {
                    Button(
                        onClick = { onDismiss() }
                    ) {
                        Text("OK")
                    }
                }
            )
        }
    }
}
