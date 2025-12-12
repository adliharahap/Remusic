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
import kotlin.math.roundToInt

/**
 * Menggelapkan warna dengan mencampurkannya dengan warna hitam.
 */
private fun darkenColor(color: Int, amount: Float = 0.5f): Int {
    return ColorUtils.blendARGB(color, Color.Black.toArgb(), amount)
}

/**
 * Mengekstrak sepasang warna gradasi dengan algoritma paling canggih.
 * Metode ini menerapkan sistem skor yang memprioritaskan warna berdasarkan populasi,
 * saturasi, dan HUE-nya.
 * --- BARU: Ditambahkan logika untuk menangani gambar yang dominan putih ---
 * Jika warna terbaik terlalu terang, fungsi akan mencari warna terbaik kedua yang lebih gelap.
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
            .size(Size(128, 128))
            .allowHardware(false)
            .build()

        val drawable = context.imageLoader.execute(request).drawable
        if (drawable != null) {
            val bitmap = (drawable as BitmapDrawable).bitmap
            val palette = withContext(Dispatchers.IO) {
                Palette.from(bitmap).maximumColorCount(32).generate()
            }

            // --- FUNGSI HELPER UNTUK MENCARI WARNA TERBAIK DARI DAFTAR SWATCH ---
            // Ini dibuat agar kita bisa menggunakannya kembali untuk mencari warna kedua.
            fun findBestSwatch(swatches: List<Palette.Swatch?>): Palette.Swatch? {
                var bestSwatch: Palette.Swatch? = null
                var highestScore = -1.0

                for (swatch in swatches) {
                    if (swatch == null) continue

                    val hsl = swatch.hsl
                    val hue = hsl[0]
                    val saturation = hsl[1]
                    val luminance = hsl[2]

                    if (saturation < 0.25f || luminance < 0.15f || luminance > 0.95f) {
                        continue
                    }

                    val hueWeight = when (hue.roundToInt()) {
                        in 45..160 -> 0.2
                        in 270..350 -> 2.0
                        else -> 1.0
                    }

                    val score = (swatch.population * saturation) * hueWeight
                    if (score > highestScore) {
                        highestScore = score
                        bestSwatch = swatch
                    }
                }
                return bestSwatch
            }

            // --- LOGIKA UTAMA ---

            // 1. Cari warna terbaik pertama dari semua swatch yang ada.
            val primaryBestSwatch = findBestSwatch(palette.swatches)

            var finalSwatch: Palette.Swatch? = primaryBestSwatch

            // 2. CEK JIKA WARNA TERLALU PUTIH
            // Jika swatch terbaik ada DAN tingkat kecerahannya (luminance) sangat tinggi.
            if (primaryBestSwatch != null && primaryBestSwatch.hsl[2] > 0.85f) {
                // Buat daftar swatch kandidat kedua, yaitu semua swatch KECUALI yang pertama.
                val secondaryCandidates = palette.swatches.filter { it != primaryBestSwatch }

                // Cari warna terbaik dari sisa kandidat.
                val secondaryBestSwatch = findBestSwatch(secondaryCandidates)

                // Jika ditemukan kandidat kedua yang bagus, gunakan itu.
                // Jika tidak, biarkan finalSwatch tetap null agar jatuh ke fallback.
                finalSwatch = secondaryBestSwatch
            }

            // 3. Tentukan warna akhir berdasarkan hasil pengecekan
            if (finalSwatch != null) {
                val chosenColor = finalSwatch.rgb
                val topColor = ColorUtils.blendARGB(chosenColor, Color.Black.toArgb(), 0.15f)

                // Cek kecerahan (luminance) dari topColor. Skala luminance: 0.0 (hitam) - 1.0 (putih).
                val luminance = ColorUtils.calculateLuminance(topColor)

                val bottomColor = if (luminance > 0.4f) {
                    // Jika topColor TERANG, gelapkan secara signifikan untuk gradasi yang kuat.
                    darkenColor(topColor, 0.5f)
                } else {
                    // Jika topColor SUDAH GELAP, hanya gelapkan sedikit (20%)
                    // untuk gradasi yang halus dan elegan.
                    ColorUtils.blendARGB(topColor, Color.Black.toArgb(), 0.2f)
                }

                listOf(Color(topColor), Color(bottomColor))
            } else {
                // FALLBACK: Jika tidak ada warna yang cocok sama sekali (misal album full putih
                // tanpa warna lain, atau primary swatch null), gunakan warna default yang aman.
                defaultColors
            }

        } else {
            defaultColors
        }
    } catch (e: Exception) {
        e.printStackTrace()
        defaultColors
    }
}