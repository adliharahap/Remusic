package com.example.remusic.utils

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.example.remusic.data.SupabaseManager
import com.example.remusic.data.model.SongWithArtist
import com.example.remusic.data.model.displayArtistName
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

object SongDownloader {
    private const val TAG = "SongDownloader"
    private val client = OkHttpClient()

    // Menggunakan direktori Downloads karena mengizinkan tipe file campuran (mp3, mp4, lrc) di MediaStore dalam satu folder
    private val BASE_DIRECTORY = Environment.DIRECTORY_DOWNLOADS
    private val BASE_URI = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        MediaStore.Downloads.EXTERNAL_CONTENT_URI
    } else {
        Uri.EMPTY // Not used on older versions
    }

    suspend fun downloadSong(
        context: Context,
        songWithArtist: SongWithArtist,
        url: String, // MP3 URL dari network source
        onProgress: (Int) -> Unit,
        onComplete: (Boolean, String?) -> Unit
    ) = withContext(Dispatchers.IO) {
        val song = songWithArtist.song
        val artistName = songWithArtist.displayArtistName
        
        // Membersihkan karakter ilegal dari nama file
        val safeTitle = song.title.replace(Regex("[\\\\/:*?\"<>|]"), "_")
        val safeArtist = artistName.replace(Regex("[\\\\/:*?\"<>|]"), "_")
        
        // Nama folder: "Judul Lagu - Nama Artis"
        val folderName = "$safeTitle - $safeArtist"
        // Target sub-directory di folder Downloads
        val relativePath = "$BASE_DIRECTORY/Remusic/$folderName"
        
        // Aturan nama file di dalam folder (HANYA MENGGUNAKAN JUDUL)
        val mp3FileName = "$safeTitle.mp3"
        val mp4FileName = "$safeTitle.mp4"
        val lrcFileName = "$safeTitle.lrc"

        try {
            // Cukup cek file MP3 untuk menandakan unduhan utama ada
            if (isAlreadyDownloaded(context, mp3FileName, "%$BASE_DIRECTORY/Remusic/$folderName%")) {
                Log.d(TAG, "Lagu sudah ada: $mp3FileName di $folderName")
                onComplete(true, "Lagu sudah ada di folder Download/Remusic/$folderName")
                return@withContext
            }

            Log.d(TAG, "Memulai unduhan ke folder: $folderName")
            
            // 1. Download MP3 (Bobot: 60%)
            val mp3Success = downloadFile(context, url, mp3FileName, "audio/mpeg", relativePath, 0f, 60f, onProgress)
            if (!mp3Success) {
                onComplete(false, "Gagal mengunduh file musik utama")
                return@withContext
            }

            // 2. Download MP4 (Video Canvas) jika tersedia (Bobot: 35%)
            if (!song.canvasUrl.isNullOrBlank()) {
                downloadFile(context, song.canvasUrl, mp4FileName, "video/mp4", relativePath, 60f, 35f, onProgress)
            } else {
                // Menambahkan progress instant 35 points jika video tidak ada
                onProgress(95)
            }

            // 3. Simpan lirik sebagai file .lrc (Bobot: Instan terakhir)
            // Jika song.lyrics dari model kosong, coba fetch langsung dari server
            val finalLyrics = if (!song.lyrics.isNullOrBlank()) {
                song.lyrics
            } else {
                try {
                    Log.d(TAG, "Lyrics lokal kosong, mencoba mengambil dari server untuk lagu: ${song.id}")
                    val result = SupabaseManager.client.postgrest["songs"]
                        .select(columns = io.github.jan.supabase.postgrest.query.Columns.list("lyrics")) {
                            filter { eq("id", song.id) }
                        }.decodeSingleOrNull<kotlinx.serialization.json.JsonObject>()
                    
                    val fetchedLyrics = result?.get("lyrics")?.jsonPrimitive?.content
                    if (!fetchedLyrics.isNullOrBlank() && fetchedLyrics != "null") fetchedLyrics else null
                } catch (e: Exception) {
                    Log.e(TAG, "Gagal mengambil lirik dari server: ${e.message}")
                    null
                }
            }

            if (!finalLyrics.isNullOrBlank()) {
                saveTextFile(context, finalLyrics, lrcFileName, "application/octet-stream", relativePath)
                Log.d(TAG, "Lirik berhasil disimpan ke $lrcFileName")
            } else {
                Log.d(TAG, "Lirik tetap kosong setelah pengecekan server.")
            }
            
            onComplete(true, "Selesai diunduh ke folder: $folderName")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading: ${e.message}", e)
            onComplete(false, "Koneksi bermasalah atau penyimpanan penuh")
        }
    }

    private fun downloadFile(
        context: Context,
        downloadUrl: String,
        fileName: String,
        mimeType: String,
        relativePath: String,
        baseProgress: Float,
        weight: Float,
        onProgress: (Int) -> Unit
    ): Boolean {
        var insertedUri: Uri? = null
        return try {
            val request = Request.Builder().url(downloadUrl).build()
            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                Log.e(TAG, "Gagal request $fileName: ${response.code}")
                return false
            }

            val body = response.body ?: return false
            val totalBytes = body.contentLength()
            val inputStream = body.byteStream()
            
            val outputStream: OutputStream? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
                val uri = context.contentResolver.insert(BASE_URI, contentValues)
                insertedUri = uri
                uri?.let { context.contentResolver.openOutputStream(it) }
            } else {
                val publicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val destDir = File(publicDir, relativePath.removePrefix("${Environment.DIRECTORY_DOWNLOADS}/"))
                if (!destDir.exists()) destDir.mkdirs()
                val file = File(destDir, fileName)
                FileOutputStream(file)
            }

            if (outputStream == null) return false

            outputStream.use { out ->
                inputStream.use { inp ->
                    val buffer = ByteArray(1024 * 16)
                    var bytesRead: Int
                    var totalRead: Long = 0

                    while (inp.read(buffer).also { bytesRead = it } != -1) {
                        out.write(buffer, 0, bytesRead)
                        totalRead += bytesRead
                        if (totalBytes > 0) {
                            val currentFileProgress = (totalRead.toFloat() / totalBytes.toFloat())
                            val overallProgress = baseProgress + (currentFileProgress * weight)
                            onProgress(overallProgress.toInt())
                        }
                    }
                    out.flush()
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && insertedUri != null) {
                val contentValues = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
                context.contentResolver.update(insertedUri, contentValues, null, null)
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Exception in downloadFile $fileName: ${e.message}")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && insertedUri != null) {
                context.contentResolver.delete(insertedUri, null, null)
            }
            false
        }
    }

    private fun saveTextFile(
        context: Context,
        content: String,
        fileName: String,
        mimeType: String,
        relativePath: String
    ): Boolean {
        var insertedUri: Uri? = null
        return try {
            val outputStream: OutputStream? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
                val uri = context.contentResolver.insert(BASE_URI, contentValues)
                insertedUri = uri
                uri?.let { context.contentResolver.openOutputStream(it) }
            } else {
                val publicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val destDir = File(publicDir, relativePath.removePrefix("${Environment.DIRECTORY_DOWNLOADS}/"))
                if (!destDir.exists()) destDir.mkdirs()
                val file = File(destDir, fileName)
                FileOutputStream(file)
            }

            if (outputStream == null) return false

            outputStream.use { out ->
                out.write(content.toByteArray())
                out.flush()
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && insertedUri != null) {
                val contentValues = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
                context.contentResolver.update(insertedUri, contentValues, null, null)
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Exception saving text file $fileName: ${e.message}")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && insertedUri != null) {
                context.contentResolver.delete(insertedUri, null, null)
            }
            false
        }
    }

    private fun isAlreadyDownloaded(context: Context, fileName: String, relativePathLike: String): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val projection = arrayOf(MediaStore.MediaColumns.DISPLAY_NAME)
                val selection = "${MediaStore.MediaColumns.DISPLAY_NAME} = ? AND ${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?"
                val selectionArgs = arrayOf(fileName, relativePathLike)
                
                context.contentResolver.query(
                    BASE_URI,
                    projection,
                    selection,
                    selectionArgs,
                    null
                )?.use { it.count > 0 } ?: false
            } else {
                val publicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                // Hapus persen wildcard layaknya SQL query untuk mencari nama folder persisnya
                val folder = relativePathLike.replace("%", "").removePrefix("${Environment.DIRECTORY_DOWNLOADS}/")
                val file = File(File(publicDir, folder), fileName)
                file.exists()
            }
        } catch (e: Exception) {
            false
        }
    }
}
