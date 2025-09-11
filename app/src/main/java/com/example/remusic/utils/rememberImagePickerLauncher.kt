package com.example.remusic.utils

import android.app.Activity
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.yalantis.ucrop.UCrop
import java.io.File

enum class CropShape {
    CIRCLE, SQUARE
}

@Composable
fun rememberImagePickerLauncher(
    cropShape: CropShape = CropShape.SQUARE,
    onImagePicked: (Uri) -> Unit
): () -> Unit {
    val context = LocalContext.current

    // Launcher untuk hasil crop
    val cropLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.let { intent ->
                UCrop.getOutput(intent)?.let { uri ->
                    Log.d("ImagePickerUtil", "Gambar berhasil di-crop. URI Cache: $uri")
                    onImagePicked(uri)
                }
            }
        }
    }

    // Launcher untuk memilih gambar dari galeri
    val pickMediaLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { sourceUri: Uri? ->
        if (sourceUri != null) {
            val destinationUri = createTempUri(context)

            // Buat konfigurasi UCrop
            val options = UCrop.Options().apply {
                setCompressionQuality(90)
                setHideBottomControls(true)
                setFreeStyleCropEnabled(false) // Tidak bebas, wajib sesuai rasio
                if (cropShape == CropShape.CIRCLE) {
                    setCircleDimmedLayer(true)
                    setShowCropGrid(false)
                }
            }

            // Set aspect ratio fix ke 1:1
            val uCrop = UCrop.of(sourceUri, destinationUri)
                .withAspectRatio(1f, 1f) // wajib square
                .withOptions(options)

            cropLauncher.launch(uCrop.getIntent(context))
        }
    }

    return {
        pickMediaLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }
}

private fun createTempUri(context: Context): Uri {
    val file = File(context.cacheDir, "cropped_image_${System.currentTimeMillis()}.jpg")
    return Uri.fromFile(file)
}
