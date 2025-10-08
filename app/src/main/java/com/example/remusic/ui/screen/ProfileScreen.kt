package com.example.remusic.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.remusic.data.UserManager
import com.example.remusic.utils.handleLogout

@Composable
fun ProfileScreen(navController: NavController) {
    // Ambil user dari state global kita
    val userProfile = UserManager.currentUser

    // ✅ Gunakan `Box` untuk bisa menampilkan loading indicator di tengah layar.
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        if (userProfile != null) {
            // ✅ Pisahkan UI profil ke dalam Column tersendiri.
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally, // <-- Buat semua item di tengah
                verticalArrangement = Arrangement.Top
            ) {
                // ✅ Tampilkan foto profil pengguna
                AsyncImage(
                    model = userProfile.photoUrl,
                    contentDescription = "Foto Profil",
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape), // <-- Bentuk bulat
                    contentScale = ContentScale.Crop
                )

                Spacer(modifier = Modifier.height(16.dp))

                // ✅ Tampilkan nama dengan style yang lebih besar
                Text(
                    text = userProfile.displayName ?: "Nama Tidak Tersedia",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = userProfile.email ?: "Email Tidak Tersedia",
                    style = MaterialTheme.typography.bodyLarge
                )

                Spacer(modifier = Modifier.height(8.dp))

                // ✅ Tampilkan Role dengan penekanan
                Text(
                    text = "Role: ${userProfile.role.uppercase()}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )

                // ✅ Spacer dengan 'weight' akan mendorong tombol Logout ke bawah
                Spacer(modifier = Modifier.weight(1f))

                // ✅ Pindahkan Button ke dalam Column agar menjadi bagian dari layout
                Button(
                    onClick = { handleLogout(navController) },
                    modifier = Modifier.padding(bottom = 16.dp) // Beri jarak dari bawah
                ) {
                    Text("Logout")
                }
            }
        } else {
            // ✅ Tampilkan loading indicator saat data sedang dimuat (atau jika user null)
            CircularProgressIndicator()
        }
    }
}