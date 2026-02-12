package com.example.remusic.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.compose.ui.graphics.Color
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import coil.imageLoader
import coil.request.ImageRequest
import coil.size.Size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Memperbaiki warna agar layak dijadikan background.
 * (Fungsi ini tetap dipertahankan karena sudah bagus logic-nya)
 */
private fun fixColorForBackground(color: Int): Int {
    val hsl = floatArrayOf(0f, 0f, 0f)
    ColorUtils.colorToHSL(color, hsl)

    val isGrayscale = hsl[1] < 0.15f

    if (isGrayscale) {
        if (hsl[2] < 0.12f) hsl[2] = 0.12f
        else if (hsl[2] > 0.3f) hsl[2] = 0.3f
    } else {
        if (hsl[2] > 0.45f) hsl[2] = 0.4f
        else if (hsl[2] < 0.15f) hsl[2] = 0.18f

        if (hsl[1] < 0.4f) hsl[1] += 0.2f
    }

    return ColorUtils.HSLToColor(hsl)
}

/**
 * Mengecek apakah warna ini termasuk kategori "Skin Tone" atau Kuning/Oranye membosankan.
 * Range Hue:
 * 0-20   = Merah (Aman)
 * 25-50  = Kulit / Oranye / Kuning Pucat (TARGET HINDAR)
 * 60+    = Kuning Terang / Hijau / Biru (Aman)
 */
private fun isSkinOrYellowTone(color: Int): Boolean {
    val hsl = floatArrayOf(0f, 0f, 0f)
    ColorUtils.colorToHSL(color, hsl)
    val hue = hsl[0]

    // Range Hue 25 sampai 50 biasanya adalah warna kulit atau kuning kecoklatan
    return hue in 25f..55f
}

suspend fun extractGradientColorsFromImageUrl(
    context: Context,
    imageUrl: String,
    defaultColors: List<Color> = listOf(
        Color(0xFF202020),
        Color(0xFF181818),
        Color(0xFF141414),
        Color(0xFF121212)
    )
): List<Color> {
    if (imageUrl.isBlank()) return defaultColors

    return try {
        val request = ImageRequest.Builder(context)
            .data(imageUrl)
            .size(Size(100, 100)) // Kecil saja biar cepat
            .allowHardware(false)
            .build()

        val result = context.imageLoader.execute(request)
        val drawable = result.drawable

        if (drawable != null) {
            val bitmap = (drawable as BitmapDrawable).bitmap.copy(Bitmap.Config.ARGB_8888, false)

            val palette = withContext(Dispatchers.Default) {
                Palette.from(bitmap).maximumColorCount(24).generate()
            }

            // --- LOGIKA BARU: SKORING ANTI-KULIT ---

            // 1. Kumpulkan semua kandidat swatch yang valid
            val candidates = listOfNotNull(
                palette.darkVibrantSwatch,
                palette.vibrantSwatch,
                palette.lightVibrantSwatch,
                palette.mutedSwatch,
                palette.darkMutedSwatch,
                palette.dominantSwatch
            ).distinctBy { it.rgb } // Hapus duplikat

            var bestSwatch: Palette.Swatch? = null
            var highestScore = -1.0f

            for (swatch in candidates) {
                // Skor Dasar = Jumlah Pixel (Seberapa dominan warna ini)
                var score = swatch.population.toFloat()

                val colorInt = swatch.rgb

                // 2. CEK HUKUMAN / BONUS
                if (isSkinOrYellowTone(colorInt)) {
                    // PENALTY: Jika warna kulit/kuning, kurangi nilainya drastis (diskon 70%)
                    // Artinya: Warna kulit harus 3.5x lebih banyak pixelnya dibanding warna biru untuk bisa menang.
                    score *= 0.3f
                } else {
                    // BONUS: Warna selain kulit (Biru, Merah, Hijau, Ungu)
                    // Kita prioritaskan vibrant (warna hidup)
                    val hsl = swatch.hsl
                    val saturation = hsl[1]

                    if (saturation > 0.3f) {
                        score *= 1.5f // Boost warna yang saturasi tinggi (Vibrant)
                    }
                }

                // Simpan yang skornya paling tinggi
                if (score > highestScore) {
                    highestScore = score
                    bestSwatch = swatch
                }
            }

            // Jika ada pemenang, proses warnanya menjadi 4 warna gradient
            if (bestSwatch != null) {
                val rawColor = bestSwatch.rgb

                // Masukkan ke logika fix brightness/saturation kamu yang lama
                val topColorInt = fixColorForBackground(rawColor)

                // Buat 4 warna gradient yang harmonis
                val color1 = Color(topColorInt) // Warna utama (paling terang)
                val color2 = Color(ColorUtils.blendARGB(topColorInt, 0xFF000000.toInt(), 0.3f)) // 30% ke hitam
                val color3 = Color(ColorUtils.blendARGB(topColorInt, 0xFF000000.toInt(), 0.6f)) // 60% ke hitam
                val color4 = Color(ColorUtils.blendARGB(topColorInt, 0xFF000000.toInt(), 0.85f)) // 85% ke hitam

                return listOf(color1, color2, color3, color4)
            }
        }
        defaultColors
    } catch (e: Exception) {
        defaultColors
    }
}