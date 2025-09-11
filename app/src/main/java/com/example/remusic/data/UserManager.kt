package com.example.remusic.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth

/**
 * Singleton object untuk mengelola sesi dan data pengguna secara global.
 */
object UserManager {

    /**
     * Menyimpan data user yang sedang login.
     * Menggunakan mutableStateOf agar setiap perubahan pada user
     * akan secara otomatis memicu recomposition pada UI yang menggunakannya.
     *
     * Diinisialisasi dengan currentUser yang mungkin sudah ada dari sesi sebelumnya.
     */
    var currentUser: FirebaseUser? by mutableStateOf(Firebase.auth.currentUser)
        private set // Setter dibuat private agar hanya bisa diubah dari dalam UserManager

    fun setUser(user: FirebaseUser?) {
        currentUser = user
    }
}