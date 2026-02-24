package com.example.remusic.viewmodel

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.remusic.data.SupabaseManager
import com.example.remusic.data.UserManager
import com.example.remusic.data.model.User
import com.example.remusic.utils.ImageUtils
import com.example.remusic.utils.SupabaseStorageUploader
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant

sealed class EditProfileState {
    object Idle : EditProfileState()
    object Loading : EditProfileState()
    data class Success(val message: String) : EditProfileState()
    data class Error(val message: String) : EditProfileState()
}

class EditProfileViewModel : ViewModel() {

    private val _uiState = MutableStateFlow<EditProfileState>(EditProfileState.Idle)
    val uiState: StateFlow<EditProfileState> = _uiState.asStateFlow()

    fun updateProfile(context: Context, newName: String, photoUri: Uri?) {
        val currentUser = UserManager.currentUser
        if (currentUser == null) {
            _uiState.value = EditProfileState.Error("User not logged in.")
            return
        }

        if (newName.isBlank()) {
            _uiState.value = EditProfileState.Error("Nama tidak boleh kosong.")
            return
        }

        _uiState.value = EditProfileState.Loading

        viewModelScope.launch {
            try {
                var finalPhotoUrl: String? = currentUser.photoUrl

                // Handle image upload if a new photo was selected
                if (photoUri != null) {
                    val file = ImageUtils.getCompressedImageFile(context, photoUri)
                    if (file != null) {
                        val result = SupabaseStorageUploader.uploadProfileImage(file, currentUser.uid)
                        if (result is SupabaseStorageUploader.UploadResult.Success) {
                            finalPhotoUrl = result.publicUrl
                        } else if (result is SupabaseStorageUploader.UploadResult.Error) {
                            Log.e("EditProfileVM", "Failed to upload image: ${result.message}")
                            _uiState.value = EditProfileState.Error("Gagal mengunggah foto profil: ${result.message}")
                            return@launch
                        }
                    } else {
                        Log.e("EditProfileVM", "Failed to compress image")
                        _uiState.value = EditProfileState.Error("Gagal memproses gambar.")
                        return@launch
                    }
                }

                // Update Supabase Users Table
                val updateData = mapOf(
                    "display_name" to newName,
                    "photo_url" to finalPhotoUrl,
                    "updated_at" to Instant.now().toString()
                )

                SupabaseManager.client.from("users")
                    .update(updateData) {
                        filter { eq("id", currentUser.uid) }
                    }

                // Update Local UserManager Cache
                val updatedUser = currentUser.copy(
                    displayName = newName,
                    photoUrl = finalPhotoUrl
                )
                UserManager.updateLocalUser(updatedUser)

                Log.d("EditProfileVM", "Profile successfully updated.")
                _uiState.value = EditProfileState.Success("Profil berhasil diperbarui!")

            } catch (e: Exception) {
                Log.e("EditProfileVM", "Error updating profile", e)
                _uiState.value = EditProfileState.Error(e.message ?: "Terjadi kesalahan saat memperbarui profil.")
            }
        }
    }

    fun resetState() {
        _uiState.value = EditProfileState.Idle
    }
}
