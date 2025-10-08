package com.example.remusic.utils

import android.util.Log
import androidx.navigation.NavController
import com.example.remusic.data.UserManager
import com.google.firebase.auth.FirebaseAuth

/**
 * Top-level function untuk menangani proses logout pengguna.
 * Fungsi ini akan:
 * 1. Mengeluarkan pengguna dari Firebase Authentication.
 * 2. Membersihkan state pengguna lokal di UserManager.
 * 3. Membersihkan back stack navigasi dan mengarahkan ke layar login.
 *
 * @param navController NavController yang aktif untuk mengelola navigasi.
 */
fun handleLogout(navController: NavController) {
    try {
        // 1. Dapatkan instance Firebase Auth dan lakukan sign out
        FirebaseAuth.getInstance().signOut()
        Log.d("LOGOUT_HANDLER", "User signed out from Firebase.")

        // ✅ 2. Panggil UserManager.clearUser() untuk membersihkan state lokal
        // Ini akan membuat `UserManager.currentUser` menjadi null.
        UserManager.clearUser()

        // 3. Arahkan ke halaman login dan bersihkan semua layar sebelumnya (back stack)
        navController.navigate("login") {
            // Membersihkan semua history navigasi hingga ke tujuan awal graph
            popUpTo(navController.graph.startDestinationId) {
                inclusive = true
            }
            // Pastikan tidak ada duplikat layar login jika tombol logout ditekan berkali-kali
            launchSingleTop = true
        }
    } catch (e: Exception) {
        Log.e("LOGOUT_HANDLER", "Error during logout", e)
    }
}