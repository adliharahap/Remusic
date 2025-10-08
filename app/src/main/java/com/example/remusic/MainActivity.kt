package com.example.remusic

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
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
import com.example.remusic.data.model.User
import com.example.remusic.navigation.AppNavGraph
import com.example.remusic.ui.theme.ReMusicTheme
import com.example.remusic.utils.NotificationUtils
import com.example.remusic.viewmodel.playmusic.PlayMusicViewModel
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var auth: FirebaseAuth
    private lateinit var credentialManager: CredentialManager

    // Buat PlayMusicViewModel di level Activity
    private val playMusicViewModel: PlayMusicViewModel by viewModels()

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
        if (isLoggedIn) {
            loadUserDataIfLoggedIn()
        }

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
                            notificationRoute = notificationRoute,
                            playMusicViewModel = playMusicViewModel
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

    // Di dalam class MainActivity

    private fun loadUserDataIfLoggedIn() {
        val firebaseUser = auth.currentUser
        if (firebaseUser != null) {
            Log.d("MAIN_ACTIVITY", "User is already logged in. Fetching profile data...")
            // Gunakan lifecycleScope untuk menjalankan coroutine
            lifecycleScope.launch {
                try {
                    // Panggil fungsi fetch dari UserManager
                    UserManager.fetchCurrentUser(firebaseUser.uid)
                } catch (e: Exception) {
                    // Tangani error jika gagal mengambil data, misalnya koneksi internet mati
                    Log.e("MAIN_ACTIVITY", "Failed to fetch returning user data.", e)
                    Toast.makeText(
                        this@MainActivity,
                        "Gagal memuat data profil. Cek koneksi internet Anda.",
                        Toast.LENGTH_LONG
                    ).show()
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
                        checkAndCreateUserInFirestore(user, onSuccess)
                    }else {
                        // Kasus yang jarang terjadi, tapi baik untuk ditangani
                        errorMessage = "Gagal mendapatkan data user setelah login."
                        showDialog = true
                    }
                } else {
                    // Login gagal
                    Log.w("FIREBASE_AUTH", "signInWithCredential:failure", task.exception)
                    // TODO: Tampilkan pesan error ke pengguna
                    errorMessage = task.exception?.localizedMessage ?: "Terjadi error yang tidak diketahui."
                    showDialog = true
                }
            }
    }

    private fun checkAndCreateUserInFirestore(user: FirebaseUser, onComplete: () -> Unit) {
        // 1. Dapatkan referensi ke koleksi 'users' di Firestore
        val db = Firebase.firestore
        val userRef = db.collection("users").document(user.uid)

        // 2. Coba ambil dokumen user berdasarkan UID-nya
        userRef.get()
            .addOnSuccessListener { documentSnapshot ->
                val fetchDataAndNavigate = {
                    lifecycleScope.launch {
                        try {
                            Log.d("MAIN_ACTIVITY", "Memuat data profil untuk UserManager...")
                            UserManager.fetchCurrentUser(user.uid)
                            onComplete() // Navigasi SETELAH data dimuat
                        } catch (e: Exception) {
                            Log.e("MAIN_ACTIVITY", "Gagal memuat data profil.", e)
                            errorMessage = "Gagal memuat data profil Anda."
                            showDialog = true
                        }
                    }
                }

                // 3. Cek apakah dokumennya TIDAK ADA
                if (!documentSnapshot.exists()) {
                    Log.d("FIRESTORE", "User belum ada di database. Membuat data baru...")

                    // Buat objek User baru dari data Firebase Auth
                    val newUser = User(
                        uid = user.uid,
                        displayName = user.displayName,
                        email = user.email,
                        photoUrl = user.photoUrl?.toString() // Ambil URL foto sebagai String
                        // 'role' dan 'createdAt' akan menggunakan nilai default dari data class
                    )

                    // Simpan objek user baru ke Firestore
                    userRef.set(newUser)
                        .addOnSuccessListener {
                            Log.d("FIRESTORE", "Data user berhasil dibuat di Firestore.")
                            Toast.makeText(baseContext, "Selamat datang!", Toast.LENGTH_SHORT).show()
                            fetchDataAndNavigate()
                        }
                        .addOnFailureListener { e ->
                            Log.w("FIRESTORE", "Gagal menyimpan data user.", e)
                            errorMessage = "Gagal menyimpan profil. Silakan coba lagi nanti."
                            showDialog = true
                        }
                } else {
                    // 4. Jika dokumen SUDAH ADA, langsung lanjutkan
                    Log.d("FIRESTORE", "User sudah ada di database. Langsung login.")
                    Toast.makeText(baseContext, "Login berhasil!", Toast.LENGTH_SHORT).show()
                    fetchDataAndNavigate()
                }
            }
            .addOnFailureListener { exception ->
                Log.w("FIRESTORE_CHECK", "Error saat mengecek user", exception)
                errorMessage = "Gagal terhubung ke database. Cek koneksi internet Anda."
                showDialog = true
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
