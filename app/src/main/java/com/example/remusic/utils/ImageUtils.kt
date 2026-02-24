package com.example.remusic.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

object ImageUtils {
    /**
     * Mengambil file dari Uri dan mengompresnya hingga ukurannya di bawah maxSizeMb (MB).
     */
    fun getCompressedImageFile(context: Context, uri: Uri, maxSizeMb: Int = 10): File? {
        return try {
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            if (bitmap == null) return null

            // Simpan ke cache directory
            val file = File(context.cacheDir, "compressed_image_${System.currentTimeMillis()}.jpg")
            file.createNewFile()

            val bos = ByteArrayOutputStream()
            var quality = 100
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, bos)

            val maxSizeBytes = maxSizeMb * 1024 * 1024

            // Turunkan kualitas 5% tiap langkah sampai ukuran memenuhi syarat (min quality 10)
            while (bos.toByteArray().size > maxSizeBytes && quality > 10) {
                bos.reset()
                quality -= 5
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, bos)
            }

            // Tulis ke file
            val fos = FileOutputStream(file)
            fos.write(bos.toByteArray())
            fos.flush()
            fos.close()

            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
