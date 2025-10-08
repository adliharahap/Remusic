package com.example.remusic.data

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.remusic.data.model.User
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.tasks.await

// Pastikan data class User ini ada di project Anda
// data class User(val uid: String = "", ..., val role: String = "listener")

/**
 * Singleton object untuk mengelola sesi dan data profil pengguna secara global.
 */
object UserManager {

    // ✅ DIUBAH: Sekarang menyimpan data class `User`, bukan `FirebaseUser`
    // Ini akan berisi data lengkap dari Firestore, termasuk 'role'.
    var currentUser by mutableStateOf<User?>(null)
        private set

    /**
     * ✅ FUNGSI BARU: Mengambil data lengkap user dari Firestore.
     * Fungsi ini harus dipanggil setelah user berhasil login.
     *
     * @param uid UID dari user yang login.
     * @throws Exception jika data tidak ditemukan atau ada error.
     */
    suspend fun fetchCurrentUser(uid: String) {
        try {
            val db = Firebase.firestore
            val documentSnapshot = db.collection("users").document(uid).get().await()

            if (documentSnapshot.exists()) {
                // Konversi dokumen Firestore ke data class User
                val user = documentSnapshot.toObject(User::class.java)
                currentUser = user // Simpan data lengkap ke state
                Log.d("UserManager", "Data profil user berhasil dimuat: ${user?.displayName}")
            } else {
                throw Exception("Dokumen user tidak ada di Firestore untuk UID: $uid")
            }
        } catch (e: Exception) {
            Log.e("UserManager", "Gagal memuat data profil user.", e)
            currentUser = null // Kosongkan data jika gagal
            throw e
        }
    }

    /**
     * ✅ FUNGSI BARU: Membersihkan data user saat logout.
     */
    fun clearUser() {
        currentUser = null
        Log.d("UserManager", "Data user dibersihkan (logout).")
    }
}