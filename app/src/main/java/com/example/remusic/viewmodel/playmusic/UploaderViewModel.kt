package com.example.remusic.viewmodel.playmusic

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.remusic.data.SupabaseManager
import com.example.remusic.data.model.User
import com.example.remusic.utils.extractGradientColorsFromImageUrl
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Repository yang bertanggung jawab untuk mengambil data user dari Supabase.
 */
class UploaderRepository {

    suspend fun getUploaderInfo(uploaderId: String): Result<User> {
        return try {
            if (uploaderId.isBlank()) {
                return Result.failure(Exception("Uploader ID kosong."))
            }

            // LOGIKA SUPABASE:
            // Ambil data dari tabel 'users' dimana kolom 'id' == uploaderId
            // decodeSingle() akan otomatis mapping JSON ke object User
            val user = SupabaseManager.client
                .from("users")
                .select {
                    filter {
                        eq("id", uploaderId)
                    }
                }
                .decodeSingle<User>()

            // Jika berhasil decode, berarti user ditemukan
            Result.success(user)

        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

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
                    // Perhatikan: user.photoUrl sekarang bisa null (String?), jadi pakai Elvis Operator (?:)
                    val photoUrlSafe = user.photoUrl ?: ""

                    val colors = colorCache[uploaderId] ?: extractGradientColorsFromImageUrl(
                        context = context,
                        imageUrl = photoUrlSafe
                    )

                    // Simpan hasil ke cache
                    userCache[uploaderId] = user
                    colorCache[uploaderId] = colors
                    _uiState.value = UploaderUiState.Success(user, colors)
                }
                .onFailure { exception ->
                    exception.printStackTrace()
                    _uiState.value = UploaderUiState.Error(exception.message ?: "Terjadi kesalahan memuat info uploader")
                }
        }
    }
}