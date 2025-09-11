package com.yourpackage.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.toBitmap
import androidx.palette.graphics.Palette
import coil.imageLoader
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Fungsi bantuan untuk menggelapkan warna.
// Mencampurkan warna asli dengan 30% warna hitam.
private fun darkenColor(color: Int, amount: Float = 0.3f): Int {
    return ColorUtils.blendARGB(color, Color.Black.toArgb(), amount)
}

@Composable
fun rememberGradientColorsFromImageUrl(
    imageUrl: String,
    defaultColors: List<Color> = listOf(Color.DarkGray, Color.Black)
): State<List<Color>> {

    val context = LocalContext.current

    return produceState(initialValue = defaultColors, key1 = imageUrl) {
        try {
            val imageLoader = context.imageLoader
            val request = ImageRequest.Builder(context)
                .data(imageUrl)
                .allowHardware(false)
                .build()

            val drawable = (imageLoader.execute(request) as? coil.request.SuccessResult)?.drawable

            if (drawable != null) {
                val bitmap = drawable.toBitmap()
                val palette = withContext(Dispatchers.IO) {
                    Palette.from(bitmap).generate()
                }

                // --- LOGIKA PEMILIHAN WARNA BARU YANG DISEMPURNAKAN ---
                val vibrant = palette.vibrantSwatch?.rgb
                val darkVibrant = palette.darkVibrantSwatch?.rgb
                val muted = palette.mutedSwatch?.rgb
                val darkMuted = palette.darkMutedSwatch?.rgb
                val dominant = palette.dominantSwatch?.rgb

                val colorPair = when {
                    // Prioritas 1: Vibrant (jika ada)
                    vibrant != null && darkVibrant != null -> {
                        listOf(Color(vibrant), Color(darkVibrant))
                    }
                    // Prioritas 2: Muted (jika ada)
                    muted != null && darkMuted != null -> {
                        listOf(Color(muted), Color(darkMuted))
                    }
                    // Prioritas 3: Dominant (selalu ada)
                    dominant != null -> {
                        val darkerDominant = darkenColor(dominant)
                        listOf(Color(dominant), Color(darkerDominant))
                    }
                    // Prioritas 4: Fallback ke default
                    else -> defaultColors
                }
                value = colorPair
            }
        } catch (e: Exception) {
            e.printStackTrace()
            value = defaultColors // Pastikan default di set jika ada error
        }
    }
}