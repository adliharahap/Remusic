package com.example.remusic.data

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.remusic.data.model.User
// Import Supabase
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns

object UserManager {

    var currentUser by mutableStateOf<User?>(null)
        private set

    /**
     * Mengambil data user dari tabel 'public.users' di Supabase.
     */
    suspend fun fetchCurrentUser(uid: String) {
        try {
            Log.d("UserManager", "Mengambil data profil dari Supabase untuk ID: $uid")

            // LOGIKA SUPABASE:
            // 1. Pilih tabel "users"
            // 2. Select semua kolom
            // 3. Filter dimana kolom "id" sama dengan parameter uid
            // 4. decodeSingle() akan otomatis mengubah JSON jadi object User (Berkat @Serializable)

            val user = SupabaseManager.client
                .from("users")
                .select(columns = Columns.ALL) {
                    filter {
                        eq("id", uid)
                    }
                }
                .decodeSingle<User>()

            currentUser = user
            Log.d("UserManager", "Profil dimuat: ${user.displayName}, Role: ${user.role}")

        } catch (e: Exception) {
            Log.e("UserManager", "Gagal memuat data profil user dari Supabase.", e)
            currentUser = null

            // Opsional: Cek error spesifik
            // Jika errornya "contains row not found", berarti user belum ada di DB public.users
            // (Meskipun seharusnya Trigger SQL sudah menanganinya)
            throw e
        }
    }

    fun clearUser() {
        currentUser = null
        Log.d("UserManager", "Data user dibersihkan (logout).")
    }
}