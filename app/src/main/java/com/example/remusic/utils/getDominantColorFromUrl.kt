package com.example.remusic.utils

import com.example.remusic.data.preferences.GradientStyle

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
    ),
    gradientStyle: GradientStyle = GradientStyle.PRIMARY
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

            // --- LOGIKA BARU: BERDASARKAN GRADIENT STYLE ---
            var bestSwatch: Palette.Swatch? = null

            when (gradientStyle) {
                GradientStyle.VIBRANT -> {
                    bestSwatch = palette.vibrantSwatch ?: palette.darkVibrantSwatch ?: palette.lightVibrantSwatch ?: palette.dominantSwatch
                }
                GradientStyle.MUTED -> {
                    bestSwatch = palette.mutedSwatch ?: palette.darkMutedSwatch ?: palette.lightMutedSwatch ?: palette.dominantSwatch
                }
                GradientStyle.DARK -> {
                    bestSwatch = palette.darkVibrantSwatch ?: palette.darkMutedSwatch ?: palette.dominantSwatch
                }
                GradientStyle.LIGHT -> {
                    bestSwatch = palette.lightVibrantSwatch ?: palette.lightMutedSwatch ?: palette.dominantSwatch
                }
                GradientStyle.PRIMARY -> {
                    // Logika lama (Skoring Anti-Kulit)
                    val candidates = listOfNotNull(
                        palette.darkVibrantSwatch,
                        palette.vibrantSwatch,
                        palette.lightVibrantSwatch,
                        palette.mutedSwatch,
                        palette.darkMutedSwatch,
                        palette.dominantSwatch
                    ).distinctBy { it.rgb }

                    var highestScore = -1.0f

                    for (swatch in candidates) {
                        var score = swatch.population.toFloat()
                        val colorInt = swatch.rgb

                        if (isSkinOrYellowTone(colorInt)) {
                            score *= 0.3f
                        } else {
                            val hsl = swatch.hsl
                            val saturation = hsl[1]
                            if (saturation > 0.3f) {
                                score *= 1.5f
                            }
                        }

                        if (score > highestScore) {
                            highestScore = score
                            bestSwatch = swatch
                        }
                    }
                }
                GradientStyle.AUTO -> {
                    // Gunakan SELURUH swatch yang digenerate oleh Palette (bisa sampai 24 warna)
                    val candidates = palette.swatches

                    var bestScore = -1.0f

                    for (swatch in candidates) {
                        val hsl = swatch.hsl
                        val hue = hsl[0]
                        val saturation = hsl[1]
                        val lightness = hsl[2]

                        // 1. Eliminasi Ekstrem: Hindari warna terlalu gelap (hitam) atau terlalu terang (putih)
                        if (lightness < 0.25f || lightness > 0.85f || saturation < 0.15f) {
                            continue
                        }

                        // 2. Base Score: Utamakan populasi pixel, tetapi beri bobot besar pada seberapa hidup warnanya
                        var score = swatch.population.toFloat() * (0.5f + (saturation * 3f))

                        // 3. Multiplier Estetik (Hue)
                        var hueMultiplier = 1.0f

                        if (hue in 190f..260f) {
                            // [REWARD] Biru Langit & Biru Laut (Sangat Estetik)
                            hueMultiplier += 1.5f
                        } else if (hue in 260f..330f) {
                            // [REWARD] Ungu & Pink
                            hueMultiplier += 1.2f
                        } else if (hue >= 340f || hue <= 15f) {
                            // [REWARD/PENALTY] Merah Terang bagus, tapi Merah Gelap (Darah/Maroon) dihindari
                            if (lightness > 0.4f) hueMultiplier += 0.8f
                            else hueMultiplier -= 0.5f 
                        }
                        
                        // [PENALTY] Coklat (Hue 15-40 tapi kecerahan rendah)
                        if (hue in 15f..40f && lightness < 0.5f) {
                            hueMultiplier -= 0.6f
                        }
                        
                        // [PENALTY] Kuning kusam & Skin tone
                        if (hue in 40f..70f) {
                            hueMultiplier -= 0.5f
                        }

                        // Terapkan multiplier (pastikan minimal 0.1 agar tidak tembus negatif)
                        score *= hueMultiplier.coerceAtLeast(0.1f)

                        if (score > bestScore) {
                            bestScore = score
                            bestSwatch = swatch
                        }
                    }

                    // Fallback jika cover album murni hitam putih (semua swatch tereliminasi)
                    if (bestSwatch == null) {
                        bestSwatch = palette.mutedSwatch ?: palette.dominantSwatch ?: palette.vibrantSwatch ?: candidates.maxByOrNull { it.population }
                    }
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