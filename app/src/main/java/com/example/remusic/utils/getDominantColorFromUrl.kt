package com.example.remusic.utils

import android.content.Context
import android.graphics.drawable.BitmapDrawable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import coil.imageLoader
import coil.request.ImageRequest
import coil.size.Size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Mengecek apakah warna terlalu terang.
 * Luminance: 0.0 (hitam) -> 1.0 (putih).
 * Warna dengan luminance > 0.5 umumnya dianggap terang.
 */
private fun isColorTooBright(color: Int, threshold: Float = 0.5f): Boolean {
    return ColorUtils.calculateLuminance(color) > threshold
}

/**
 * Menggelapkan warna dengan mencampurkannya dengan warna hitam.
 */
private fun darkenColor(color: Int, amount: Float = 0.3f): Int {
    return ColorUtils.blendARGB(color, Color.Black.toArgb(), amount)
}

/**
 * Mengekstrak sepasang warna gradasi dari URL gambar dengan logika prioritas
 * dan penyesuaian kecerahan otomatis.
 */
suspend fun extractGradientColorsFromImageUrl(
    context: Context,
    imageUrl: String,
    defaultColors: List<Color> = listOf(Color(0xFF333333), Color.Black)
): List<Color> {
    if (imageUrl.isBlank()) return defaultColors

    return try {
        val request = ImageRequest.Builder(context)
            .data(imageUrl)
            .size(Size(256, 256)) // Ukuran diperkecil untuk performa
            .allowHardware(false) // Wajib untuk Palette API
            .build()

        val drawable = context.imageLoader.execute(request).drawable
        if (drawable != null) {
            val bitmap = (drawable as BitmapDrawable).bitmap
            val palette = withContext(Dispatchers.IO) {
                Palette.from(bitmap).generate()
            }

            val vibrant = palette.vibrantSwatch?.rgb
            val darkVibrant = palette.darkVibrantSwatch?.rgb
            val muted = palette.mutedSwatch?.rgb
            val darkMuted = palette.darkMutedSwatch?.rgb
            val dominant = palette.dominantSwatch?.rgb

            // --- ALGORITMA HIBRIDA DENGAN PRIORITAS DAN PENYESUAIAN KECERAHAN ---
            val colorPair = when {
                // Prioritas 1: Pasangan Vibrant, dengan penyesuaian jika terlalu terang
                vibrant != null && darkVibrant != null -> {
                    val topColor = if (isColorTooBright(vibrant)) darkenColor(vibrant, 0.2f) else vibrant
                    listOf(Color(topColor), Color(darkVibrant))
                }
                // Prioritas 2: Pasangan Muted, dengan penyesuaian jika terlalu terang
                muted != null && darkMuted != null -> {
                    val topColor = if (isColorTooBright(muted)) darkenColor(muted, 0.2f) else muted
                    listOf(Color(topColor), Color(darkMuted))
                }
                // Prioritas 3: Warna Dominan, dengan penyesuaian & pembuatan pasangan
                dominant != null -> {
                    val topColor = if (isColorTooBright(dominant)) darkenColor(dominant, 0.4f) else dominant
                    val bottomColor = darkenColor(topColor)
                    listOf(Color(topColor), Color(bottomColor))
                }
                // Prioritas 4: Fallback ke warna gelap yang aman
                darkVibrant != null -> listOf(Color(darkVibrant), Color(darkenColor(darkVibrant)))
                darkMuted != null -> listOf(Color(darkMuted), Color(darkenColor(darkMuted)))
                // Prioritas 5: Fallback ke default
                else -> defaultColors
            }
            colorPair

        } else {
            defaultColors
        }
    } catch (e: Exception) {
        e.printStackTrace()
        defaultColors
    }
}