package com.example.remusic.viewmodel

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.remusic.data.UserManager
import com.example.remusic.utils.AudioFile
import com.example.remusic.utils.SupabaseStorageUploader
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

data class MusicUploadState(
    val isLoading: Boolean = false,
    val uploadSuccessMessage: String? = null,
    val uploadErrorMessage: String? = null,
    val errorMessage: String? = null,
    val uploadProgress: Int = 0,
    val currentStep: String = "",
)

class MusicUploadViewModel : ViewModel() {

    var uiState by mutableStateOf(MusicUploadState())
        private set

    private val firestore = FirebaseFirestore.getInstance()

    fun resetUploadState() {
        uiState = uiState.copy(
            uploadSuccessMessage = null,
            uploadErrorMessage = null,
            errorMessage = null
        )
    }

    private fun getCoverFileFromUri(context: Context, uri: Uri, songId: String): File? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null

            // Simpan sementara ke cache
            val file = File(context.cacheDir, "cover_$songId.jpg")
            file.outputStream().use { output ->
                inputStream.copyTo(output)
            }
            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }


    /**
     * Fungsi utama untuk upload musik lengkap
     */
    fun uploadMusic(
        context: Context,
        selectedSong: AudioFile?,
        title: String,
        lyrics: String,
        selectedMoods: Set<String>,
        imageUri: String?,
        artistId: String,
    ) {
        val currentUser = UserManager.currentUser
        if (currentUser == null) {
            uiState = uiState.copy(errorMessage = "Tidak ada user yang login. Silakan login kembali.")
            return
        }

        if (selectedSong == null) {
            uiState = uiState.copy(errorMessage = "File audio tidak boleh kosong.")
            return
        }

        if (imageUri.isNullOrBlank()) {
            uiState = uiState.copy(errorMessage = "Cover image tidak boleh kosong.")
            return
        }

        viewModelScope.launch {
            uiState = uiState.copy(
                isLoading = true,
                errorMessage = null,
                uploadSuccessMessage = null,
                uploadProgress = 0,
                currentStep = "Memulai upload..."
            )

            try {
                val songId = UUID.randomUUID().toString()

                Log.d("Image uri di upload", imageUri)

                // ✅ handle cover image (manual vs content)
                val coverFile: File? = run {
                    val uri = imageUri.toUri()
                    if (uri.scheme == "content") {
                        // content:// dari MediaStore
                        getCoverFileFromUri(context, uri, songId)
                    } else {
                        // file fisik (hasil picker manual)
                        File(uri.path!!)
                    }
                }

                if (coverFile == null || !coverFile.exists()) {
                    uiState = uiState.copy(isLoading = false, errorMessage = "Cover image tidak valid.")
                    return@launch
                }

                // ✅ 1. Upload cover image ke Supabase
                uiState = uiState.copy(currentStep = "Mengunggah cover image...")
                val coverUrl = uploadMusicPhoto(coverFile, songId, title)
                if (coverUrl == null) {
                    uiState = uiState.copy(isLoading = false, errorMessage = "Gagal mengunggah cover image.")
                    return@launch
                }

                // ✅ 2. Upload audio file (MP3) ke Supabase
                uiState = uiState.copy(currentStep = "Mengunggah file audio...")
                val audioUrl = uploadMusicFile(selectedSong, songId, title)
                if (audioUrl == null) {
                    uiState = uiState.copy(isLoading = false, errorMessage = "Gagal mengunggah file audio.")
                    return@launch
                }

                // ✅ 3. Simpan metadata ke Firestore
                uiState = uiState.copy(currentStep = "Menyimpan metadata ke database...")
                saveMusicToFirestore(
                    songId = songId,
                    title = title,
                    artistId = artistId,
                    coverUrl = coverUrl,
                    audioUrl = audioUrl,
                    durationMs = selectedSong.duration.toLong(),
                    lyrics = lyrics,
                    moods = selectedMoods.toList()
                )

                uiState = uiState.copy(
                    isLoading = false,
                    uploadSuccessMessage = "Musik berhasil diunggah!"
                )
            } catch (e: Exception) {
                Log.e("MusicUploadVM", "Error saat upload musik", e)
                uiState = uiState.copy(
                    isLoading = false,
                    errorMessage = "Terjadi kesalahan: ${e.message}",
                    uploadErrorMessage = "Music Gagal Diunggah!"
                )
            }
        }
    }

    /**
     * Upload Cover Image ke Supabase
     */
    private suspend fun uploadMusicPhoto(
        coverFile: File,
        musicId: String,
        musicName: String
    ): String? {
        return try {
            if (!coverFile.exists()) throw IllegalStateException("File tidak ditemukan: ${coverFile.path}")

            when (val result = SupabaseStorageUploader.uploadMusicPosterImage(coverFile, musicId, musicName)) {
                is SupabaseStorageUploader.UploadResult.Success -> result.publicUrl
                is SupabaseStorageUploader.UploadResult.Error -> null
            }
        } catch (e: Exception) {
            Log.e("MusicUploadVM", "Error upload cover image", e)
            null
        }
    }


    /**
     * Upload Audio File ke Supabase
     */
    private suspend fun uploadMusicFile(selectedSong: AudioFile, musicId: String, musicName: String): String? {
        return try {
            val fileToUpload = File(selectedSong.audioUrl)
            if (!fileToUpload.exists()) throw IllegalStateException("File audio tidak ditemukan: ${selectedSong.audioUrl}")

            when (val result = SupabaseStorageUploader.uploadMusicFile(fileToUpload, musicId, musicName)) {
                is SupabaseStorageUploader.UploadResult.Success -> result.publicUrl
                is SupabaseStorageUploader.UploadResult.Error -> null
            }
        } catch (e: Exception) {
            Log.e("MusicUploadVM", "Error upload audio file", e)
            null
        }
    }

    /**
     * Simpan metadata ke Firestore
     */
    private fun saveMusicToFirestore(
        songId: String,
        title: String,
        artistId: String,
        coverUrl: String,
        audioUrl: String,
        durationMs: Long,
        lyrics: String,
        moods: List<String>
    ) {
        val userId = UserManager.currentUser!!.uid

        val data = hashMapOf(
            "id" to songId,
            "title" to title,
            "artistId" to artistId,
            "uploaderUserId" to userId,
            "coverUrl" to coverUrl,
            "audioUrl" to audioUrl,
            "durationMs" to durationMs,
            "moods" to moods,
            "lyrics" to lyrics,
            "playCount" to 0,
            "likeCount" to 0,
            "createdAt" to Timestamp.now(),
            "updatedAt" to Timestamp.now()
        )

        firestore.collection("songs").document(songId).set(data)
            .addOnSuccessListener { Log.d("MusicUploadVM", "Data lagu berhasil disimpan.") }
            .addOnFailureListener { e -> Log.e("MusicUploadVM", "Gagal menyimpan data lagu", e) }
    }
}
