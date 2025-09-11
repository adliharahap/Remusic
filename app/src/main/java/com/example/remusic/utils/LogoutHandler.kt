package com.example.remusic.utils

import android.util.Log
import androidx.navigation.NavController
import com.google.firebase.auth.FirebaseAuth

/**
 * Top-level function untuk menangani proses logout pengguna.
 * Fungsi ini akan:
 * 1. Mengeluarkan pengguna dari Firebase Authentication.
 * 2. Membersihkan back stack navigasi.
 * 3. Mengarahkan pengguna kembali ke layar login.
 *
 * @param navController NavController yang aktif untuk mengelola navigasi.
 */
fun handleLogout(navController: NavController) {
    try {
        // 1. Dapatkan instance Firebase Auth dan lakukan sign out
        FirebaseAuth.getInstance().signOut()
        Log.d("LOGOUT_HANDLER", "User signed out successfully from Firebase.")

        // 2. Arahkan ke halaman login dan bersihkan semua layar sebelumnya (back stack)
        navController.navigate("login") {
            popUpTo(navController.graph.startDestinationId) {
                inclusive = true
            }
            launchSingleTop = true
        }
    } catch (e: Exception) {
        Log.e("LOGOUT_HANDLER", "Error during logout", e)
    }
}