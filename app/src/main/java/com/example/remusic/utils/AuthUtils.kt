package com.example.remusic.utils

import android.content.Context
import android.widget.Toast
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.navigation.NavController
import com.example.remusic.data.SupabaseManager
import com.example.remusic.data.UserManager
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object AuthUtils {
    
    /**
     * Menangani proses logout dari Supabase, membersihkan cache user,
     * dan mengarahkan kembali ke halaman login.
     */
    fun logout(context: Context, navController: NavController) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 0. Hapus fcm_token dari Supabase sebelum logout
                val user = SupabaseManager.client.auth.currentUserOrNull()
                if (user != null) {
                    try {
                        SupabaseManager.client.from("users").update(
                            {
                                set("fcm_token", null as String?)
                            }
                        ) {
                            filter { eq("id", user.id) }
                        }
                    } catch (e: Exception) {
                        // Abaikan error update token
                    }
                }

                // 1. Sign out dari Supabase
                SupabaseManager.client.auth.signOut()
                
                // 2. Membersihkan session credential Google (agar pemilih akun muncul lagi saat login)
                try {
                    val credentialManager = CredentialManager.create(context)
                    credentialManager.clearCredentialState(ClearCredentialStateRequest())
                } catch (e: Exception) {
                    // Abaikan jika device tidak support credential manager atau error
                }
                
                // 3. Membersihkan cache lokal di memori & SharedPrefs
                UserManager.clearUser()
                UserManager.saveCachedFcmToken(null)
                
                // 4. Kembali ke UI dan pindah ke halaman Login
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Berhasil keluar dari akun.", Toast.LENGTH_SHORT).show()
                    navController.navigate("login") {
                        popUpTo(0) { inclusive = true }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Gagal keluar: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
