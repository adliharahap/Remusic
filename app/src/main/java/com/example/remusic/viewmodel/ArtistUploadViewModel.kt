package com.example.remusic.viewmodel

import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.remusic.data.UserManager
import com.example.remusic.utils.SupabaseStorageUploader
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.File
import java.util.UUID

// Data class untuk merepresentasikan state UI
data class Artist(
    val id: String,
    val name: String,
    val normalizedName: String,
    val photoUrl: String,
    val description: String,
    val createdAt: Long? = null
)

data class ArtistFormState(
    val isLoading: Boolean = false,
    val uploadSuccessMessage: String? = null,
    val errorMessage: String? = null,
    val lastAddedArtist: Artist? = null,
    val artistList: List<Artist> = emptyList()
)

class ArtistUploadViewModel : ViewModel() {

    var uiState by mutableStateOf(ArtistFormState())
        private set

    fun selectArtist(artist: Artist) {
        uiState = uiState.copy(lastAddedArtist = artist)
        Log.d("ArtistUploadVM", "Artist selected and stored in state: ${artist.name}")
    }

    fun clearLastAddedArtist() {
        uiState = uiState.copy(lastAddedArtist = null)
    }
    private val firestore = FirebaseFirestore.getInstance()

    fun saveNewArtist(
        context: Context,
        photoUriString: String?,
        artistName: String,
        artistDesc: String
    ) {
        if (photoUriString == null) {
            uiState = uiState.copy(errorMessage = "URI gambar tidak boleh kosong.")
            return
        }

        viewModelScope.launch {
            uiState = uiState.copy(isLoading = true, errorMessage = null, uploadSuccessMessage = null)
            val artistId = UUID.randomUUID().toString()
            // Langkah 1: Upload foto artis terlebih dahulu
            val photoUrl = uploadArtistPhoto(context, photoUriString, artistId, artistName)

            if (photoUrl != null) {
                // Jika upload foto berhasil, lanjutkan ke penyimpanan data
                Log.d("ArtistUploadVM", "Foto berhasil diunggah, URL: $photoUrl. Melanjutkan simpan data...")

                // ---===[ TODO: LOGIKA SIMPAN KE DATABASE ]===---
                try {
                    val user = UserManager.currentUser
                    // Siapkan data artis
                    val artistData = hashMapOf(
                        "id" to artistId,
                        "name" to artistName,
                        "normalizedName" to artistName.lowercase(),
                        "photoUrl" to photoUrl,
                        "description" to artistDesc,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp(),
                        "createdBy" to user?.uid,
                    )

                    // Simpan ke Firestore (pakai coroutine)
                    firestore.collection("artists")
                        .document(artistId)
                        .set(artistData)
                        .await()

                    Log.i("ArtistUploadVM", "Data artis berhasil disimpan ke Firestore.")

                    // Buat objek Artist untuk UI
                    val newArtist = Artist(
                        id = artistId,
                        name = artistName,
                        normalizedName = artistName.lowercase(),
                        photoUrl = photoUrl,
                        description = artistDesc,
                        createdAt = System.currentTimeMillis() // sementara pakai device time
                    )

                    uiState = uiState.copy(
                        isLoading = false,
                        uploadSuccessMessage = "Artis berhasil disimpan!",
                        lastAddedArtist = newArtist
                    )

                } catch (e: Exception) {
                    Log.e("ArtistUploadVM", "Gagal menyimpan data artis ke Firestore", e)
                    uiState = uiState.copy(isLoading = false, errorMessage = "Gagal menyimpan data artis: $e")
                }

            } else {
                // Jika upload foto gagal, hentikan proses dan tampilkan error
                uiState = uiState.copy(isLoading = false, errorMessage = "Gagal mengunggah foto artis.")
            }
        }
    }

    /**
     * Fungsi privat untuk menangani upload file.
     * Mengembalikan URL publik jika berhasil, atau null jika gagal.
     */
    private suspend fun uploadArtistPhoto(context: Context, photoUriString: String, artistId: String, artistName: String): String? {
        val photoUri = photoUriString.toUri()
        Log.d("ArtistUploadVM", "Starting upload for File URI: $photoUri")

        return try {
            val fileToUpload = File(photoUri.path!!)
            if (!fileToUpload.exists()) {
                throw IllegalStateException("File tidak ditemukan di path: ${photoUri.path}")
            }

            when (val result = SupabaseStorageUploader.uploadArtistImage(fileToUpload, artistId, artistName)) {
                is SupabaseStorageUploader.UploadResult.Success -> {
                    Log.i("ArtistUploadVM", "Upload foto berhasil: ${result.publicUrl}")
                    result.publicUrl // Kembalikan URL jika sukses
                }
                is SupabaseStorageUploader.UploadResult.Error -> {
                    Log.e("ArtistUploadVM", "Upload foto gagal: ${result.message}")
                    null // Kembalikan null jika gagal
                }
            }
        } catch (e: Exception) {
            Log.e("ArtistUploadVM", "Error saat membuat File dari URI", e)
            null
        }
    }

    fun getAllArtists() {
        viewModelScope.launch {
            uiState = uiState.copy(isLoading = true, errorMessage = null)

            try {
                val snapshot = firestore.collection("artists")
                    .orderBy("name") // bisa pakai normalizedName untuk search
                    .get()
                    .await()

                val artists = snapshot.documents.mapNotNull { doc ->
                    val id = doc.id
                    val name = doc.getString("name") ?: return@mapNotNull null
                    val normalizedName = doc.getString("normalizedName") ?: name.lowercase()
                    val photoUrl = doc.getString("photoUrl") ?: ""
                    val description = doc.getString("description") ?: ""
                    val createdAt = doc.getTimestamp("createdAt")?.toDate()?.time

                    Artist(
                        id = id,
                        name = name,
                        normalizedName = normalizedName,
                        photoUrl = photoUrl,
                        description = description,
                        createdAt = createdAt
                    )
                }

                uiState = uiState.copy(isLoading = false, artistList = artists)

            } catch (e: Exception) {
                Log.e("ArtistUploadVM", "Gagal mengambil data artist", e)
                uiState = uiState.copy(isLoading = false, errorMessage = "Gagal memuat daftar artis.")
            }
        }
    }
}
