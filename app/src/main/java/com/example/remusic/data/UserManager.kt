package com.example.remusic.data

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.remusic.data.model.User
import io.github.jan.supabase.auth.auth
// Import Supabase
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.delay

object UserManager {

    private val TAG = "UserManager"

    var currentUser by mutableStateOf<User?>(null)
        private set

    /**
     * Mengambil data user dari tabel 'public.users' di Supabase dengan retry logic.
     * Akan mencoba hingga 4 kali dengan delay 1 detik jika gagal.
     */
    suspend fun fetchCurrentUser(uid: String) {
        Log.d(TAG, "================================================================")
        Log.d(TAG, "🔍 [USER FETCH] Request data user untuk ID: $uid")

        var attempt = 1
        val maxAttempts = 4

        while (attempt <= maxAttempts) {
            try {
                if (attempt > 1) Log.d(TAG, "🔄 RETRY USER: Percobaan ke-$attempt...")

                // Query ke Supabase
                val user = SupabaseManager.client
                    .from("users")
                    .select(columns = Columns.ALL) {
                        filter {
                            eq("id", uid)
                        }
                    }
                    .decodeSingle<User>()

                // SUKSES! Set currentUser
                currentUser = user
                Log.d(TAG, "✅ [SUCCESS] Profil dimuat: ${user.displayName}, Role: ${user.role}, Email: ${user.email}")
                Log.d(TAG, "================================================================")
                return // Keluar dari fungsi, tidak perlu retry lagi

            } catch (e: Exception) {
                Log.e(TAG, "❌ [USER ERROR] Percobaan $attempt Gagal: ${e.message}")

                // Jika ini adalah percobaan terakhir, set currentUser = null dan throw error
                if (attempt >= maxAttempts) {
                    Log.e(TAG, "💀 [GIVE UP] Gagal total ambil user setelah $maxAttempts percobaan.")
                    currentUser = null
                    throw e // Re-throw biar caller tau kalau gagal
                }
            }

            // Delay sebelum retry (kecuali di percobaan terakhir)
            if (attempt < maxAttempts) {
                Log.d(TAG, "⏳ Menunggu 1 detik sebelum coba lagi...")
                delay(1000)
            }
            attempt++
        }
    }

    /**
     * Fungsi untuk memastikan user data sudah loaded.
     * Jika currentUser masih null, akan mencoba fetch ulang.
     */
    suspend fun ensureUserLoaded(): User? {
        if (currentUser != null) {
            Log.d(TAG, "✅ [CACHE HIT] User sudah ada: ${currentUser?.displayName}")
            return currentUser
        }

        // Jika null, coba ambil dari session yang tersimpan
        val session = SupabaseManager.client.auth.currentSessionOrNull()
        if (session != null) {
            Log.d(TAG, "🔄 [ENSURE] User null, fetching dari session...")
            fetchCurrentUser(session.user?.id ?: return null)
            return currentUser
        }

        Log.w(TAG, "⚠️ [ENSURE FAIL] Tidak ada session dan user null.")
        return null
    }

    fun clearUser() {
        currentUser = null
        Log.d(TAG, "🗑️ Data user dibersihkan (logout).")
    }
}