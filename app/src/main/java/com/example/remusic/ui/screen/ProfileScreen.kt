package com.example.remusic.ui.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.remusic.data.UserManager
import com.example.remusic.utils.handleLogout

@Composable
fun ProfileScreen(navController: NavController) {
    // Ambil user dari state global kita
    val user = UserManager.currentUser

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()) // <-- PENTING agar bisa di-scroll
    ) {
        if (user != null) {
            val userInfo = buildString {
                append("--- Data Pengguna (Cara Rapi) ---\n\n")
                append("Nama Tampilan: ${user.displayName ?: "N/A"}\n\n")
                append("Email: ${user.email ?: "N/A"}\n\n")
                append("UID: ${user.uid}\n\n")
                append("Email Terverifikasi: ${user.isEmailVerified}\n\n")
                append("URL Foto: ${user.photoUrl?.toString() ?: "N/A"}\n\n")
                append("Provider ID: ${user.providerId}")
            }

            Text(
                text = userInfo,
            )
        } else {
            Text(text = "Tidak ada user yang sedang login.")
        }
    }
    Button(
        onClick = { handleLogout(navController)}
    ) {
        Text("Logout")
    }
}
