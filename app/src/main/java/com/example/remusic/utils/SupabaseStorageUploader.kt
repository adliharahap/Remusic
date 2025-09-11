package com.example.remusic.utils

import com.example.remusic.data.SupabaseManager
import io.github.jan.supabase.storage.storage
import java.io.File

object SupabaseStorageUploader {

    // Nama bucket Supabase
    private const val BUCKET_NAME = "Remusic Storage"

    // Sealed class untuk hasil upload
    sealed class UploadResult {
        data class Success(val publicUrl: String, val filePath: String) : UploadResult()
        data class Error(val message: String) : UploadResult()
    }

    // Fungsi umum untuk sanitize nama file
    private fun sanitizeFileName(fileName: String): String {
        return fileName
            .lowercase()
            .replace("\\s+".toRegex(), "-") // spasi → "-"
            .replace("[^a-z0-9._-]".toRegex(), "") // hapus karakter ilegal
    }

    /**
     * Upload gambar artis
     */
    suspend fun uploadArtistImage(imageFile: File, artistId: String, artistName: String): UploadResult {
        return uploadFile(
            file = imageFile,
            path = "artist/$artistId/${sanitizeFileName(artistName)}.${imageFile.extension}"
        )
    }

    /**
     * Upload poster musik
     */
    suspend fun uploadMusicPosterImage(imageFile: File, musicId: String, musicName: String): UploadResult {
        return uploadFile(
            file = imageFile,
            path = "music/$musicId/${sanitizeFileName(musicName)}.${imageFile.extension}"
        )
    }

    /**
     * Upload file musik (hanya MP3)
     */
    suspend fun uploadMusicFile(audioFile: File, musicId: String, musicName: String): UploadResult {
        if (audioFile.extension.lowercase() != "mp3") {
            return UploadResult.Error("File harus berformat MP3.")
        }

        return uploadFile(
            file = audioFile,
            path = "music/$musicId/${sanitizeFileName(musicName)}.${audioFile.extension}"
        )
    }

    /**
     * Fungsi umum untuk upload ke Supabase
     */
    private suspend fun uploadFile(file: File, path: String): UploadResult {
        return try {
            println("☁️ Memulai upload ke path: $path")

            val bucket = SupabaseManager.client.storage.from(BUCKET_NAME)

            bucket.upload(
                path = path,
                data = file.readBytes()
            ) {
                upsert = false // Jangan timpa file jika sudah ada
            }

            val publicUrl = bucket.publicUrl(path)
            println("✅ Upload berhasil! URL: $publicUrl")

            UploadResult.Success(publicUrl, path)
        } catch (e: Exception) {
            e.printStackTrace()
            println("❌ Gagal upload: ${e.message}")
            UploadResult.Error(e.message ?: "Terjadi kesalahan tidak diketahui")
        }
    }
}
