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
 * - Mencegah terlalu terang (Silau)
 * - Mencegah terlalu hitam (Layar mati)
 * - Mencegah warna pudar (Kusam)
 */
private fun fixColorForBackground(color: Int): Int {
    val hsl = floatArrayOf(0f, 0f, 0f)
    ColorUtils.colorToHSL(color, hsl)

    // HSL[0] = Hue (Warna)
    // HSL[1] = Saturation (Kepekatan warna 0.0 - 1.0)
    // HSL[2] = Lightness (Kecerahan 0.0 - 1.0)

    // 1. CEK APAKAH WARNA INI HITAM/ABU-ABU (Saturasi rendah)?
    // Jika saturasi sangat rendah (abu-abu/hitam/putih), kita anggap ini "achromatic".
    val isGrayscale = hsl[1] < 0.15f

    // 2. ATUR KECERAHAN (LIGHTNESS)
    if (isGrayscale) {
        // Jika aslinya Hitam/Abu, JANGAN biarkan hitam pekat (0.0).
        // Kita angkat ke 'Spotify Dark Grey' (sekitar 0.12 - 0.15).
        if (hsl[2] < 0.12f) {
            hsl[2] = 0.12f // Ini warna abu-abu elegan, bukan hitam mati.
        }
        // Jangan terlalu terang juga buat abu-abu
        else if (hsl[2] > 0.3f) {
            hsl[2] = 0.3f
        }
    } else {
        // Jika Berwarna (Merah, Biru, dll)

        // Jangan terlalu terang (biar teks putih terbaca)
        if (hsl[2] > 0.45f) {
            hsl[2] = 0.4f
        }
        // Jangan terlalu gelap (biar warnanya kelihatan)
        else if (hsl[2] < 0.15f) {
            hsl[2] = 0.18f
        }

        // BOOST SATURASI
        // Kalau warnanya ada tapi kusam, kita bikin lebih 'pop' dikit
        if (hsl[1] < 0.4f) {
            hsl[1] += 0.2f // Tambah saturasi
        }
    }

    return ColorUtils.HSLToColor(hsl)
}

suspend fun extractGradientColorsFromImageUrl(
    context: Context,
    imageUrl: String,
    // Default pakai Dark Grey ala Spotify, bukan hitam pekat
    defaultColors: List<Color> = listOf(Color(0xFF202020), Color(0xFF121212))
): List<Color> {
    if (imageUrl.isBlank()) return defaultColors

    return try {
        val request = ImageRequest.Builder(context)
            .data(imageUrl)
            .size(Size(100, 100))
            .allowHardware(false)
            .build()

        val result = context.imageLoader.execute(request)
        val drawable = result.drawable

        if (drawable != null) {
            val bitmap = (drawable as BitmapDrawable).bitmap.copy(Bitmap.Config.ARGB_8888, false)

            val palette = withContext(Dispatchers.Default) {
                Palette.from(bitmap).maximumColorCount(24).generate()
            }

            // --- STRATEGI BARU: CARI WARNA YANG "HIDUP" DULU ---

            // Urutan prioritas pencarian swatch:
            // Kita cari yang punya warna (Saturasi tinggi) dulu, baru yang gelap.
            // Jangan langsung ambil Dominant (karena Dominant seringkali hitam).
            val orderedSwatches = listOf(
                palette.darkVibrantSwatch, // 1. Gelap tapi berwarna (Terbaik)
                palette.vibrantSwatch,     // 2. Terang berwarna (Nanti kita gelapkan)
                palette.lightVibrantSwatch,// 3. Sangat terang berwarna (Nanti kita gelapkan banget)
                palette.mutedSwatch,       // 4. Kalem
                palette.dominantSwatch     // 5. Terakhir baru Dominant (biasanya background luas)
            )

            // Cari swatch pertama yang TIDAK null
            var bestSwatch = orderedSwatches.firstOrNull { it != null }

            // Jika ketemu, proses warnanya
            if (bestSwatch != null) {
                val rawColor = bestSwatch.rgb

                // Masukkan ke "Bengkel Cat" kita untuk diperbaiki
                val topColorInt = fixColorForBackground(rawColor)

                // Warna bawah: Campur warna atas dengan Hitam Pekat (80% Hitam)
                // Hasilnya bukan hitam mati, tapi "Very Dark [Color]"
                val bottomColorInt = ColorUtils.blendARGB(topColorInt, 0xFF000000.toInt(), 0.8f)

                return listOf(Color(topColorInt), Color(bottomColorInt))
            }
        }
        defaultColors
    } catch (e: Exception) {
        // e.printStackTrace() // Boleh dikomen biar logcat gak penuh
        defaultColors
    }
}