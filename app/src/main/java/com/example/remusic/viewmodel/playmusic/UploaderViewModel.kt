package com.example.remusic.viewmodel.playmusic // Ganti sesuai package Anda

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.remusic.utils.extractGradientColorsFromImageUrl
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Model data yang merepresentasikan seorang user.
 * Pastikan field-nya cocok dengan yang ada di Firestore.
 */
data class User(
    val uid: String = "",
    val displayName: String = "",
    val role: String = "",
    val photoUrl: String = ""
)

/**
 * Repository yang bertanggung jawab untuk mengambil data user dari Firestore.
 */
class UploaderRepository {

    private val db = Firebase.firestore

    suspend fun getUploaderInfo(uploaderId: String): Result<User> {
        return try {
            if (uploaderId.isBlank()) {
                return Result.failure(Exception("Uploader ID kosong."))
            }

            // Langsung ambil dokumen user dari koleksi 'users'
            val userDocument = db.collection("users").document(uploaderId).get().await()

            if (!userDocument.exists()) {
                return Result.failure(Exception("User dengan ID '$uploaderId' tidak ditemukan."))
            }

            val user = userDocument.toObject(User::class.java)
            if (user != null) {
                Result.success(user.copy(uid = userDocument.id))
            } else {
                Result.failure(Exception("Gagal mem-parsing data user."))
            }

        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

/**
 * State untuk merepresentasikan kondisi UI: Loading, Success, atau Error.
 */
// State SEKARANG MENYIMPAN WARNA GRADIEN JUGA
sealed interface UploaderUiState {
    object Loading : UploaderUiState
    data class Success(
        val user: User,
        val backgroundColors: List<Color> = listOf(Color(0xFF282828), Color(0xFF1E1E1E))
    ) : UploaderUiState
    data class Error(val message: String) : UploaderUiState
}

class UploaderViewModel : ViewModel() {

    private val repository = UploaderRepository()
    private val userCache = mutableMapOf<String, User>()
    // Cache juga untuk warna agar tidak diekstrak berulang kali
    private val colorCache = mutableMapOf<String, List<Color>>()

    private val _uiState = MutableStateFlow<UploaderUiState>(UploaderUiState.Loading)
    val uiState = _uiState.asStateFlow()

    fun fetchUploaderInfo(uploaderId: String, context: Context) {
        // Cek cache untuk user dan warna
        if (userCache.containsKey(uploaderId) && colorCache.containsKey(uploaderId)) {
            _uiState.value = UploaderUiState.Success(userCache[uploaderId]!!, colorCache[uploaderId]!!)
            return
        }

        viewModelScope.launch {
            _uiState.value = UploaderUiState.Loading
            repository.getUploaderInfo(uploaderId)
                .onSuccess { user ->
                    // Ambil warna dari cache atau ekstrak jika belum ada
                    val colors = colorCache[uploaderId] ?: extractGradientColorsFromImageUrl(
                        context = context,
                        imageUrl = user.photoUrl
                    )
                    // Simpan hasil ke cache
                    userCache[uploaderId] = user
                    colorCache[uploaderId] = colors
                    _uiState.value = UploaderUiState.Success(user, colors)
                }
                .onFailure { exception ->
                    _uiState.value = UploaderUiState.Error(exception.message ?: "Terjadi kesalahan")
                }
        }
    }
}