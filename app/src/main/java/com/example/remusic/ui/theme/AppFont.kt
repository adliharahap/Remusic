package com.example.remusic.ui.theme

import com.example.remusic.R
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

object AppFont {
    // --- REKOMENDASI: Pakai ini untuk LyricsScreen ---
    // Ini menggabungkan semua varian jadi satu keluarga.
    // Jadi bisa pakai: fontWeight = FontWeight.Bold atau FontWeight.Medium
    val Montserrat = FontFamily(
        Font(R.font.montserrat_regular, FontWeight.Normal),
        Font(R.font.montserrat_medium, FontWeight.Medium),
        Font(R.font.montserrat_semibold, FontWeight.SemiBold),
        Font(R.font.montserrat_bold, FontWeight.Bold),
        Font(R.font.montserrat_black, FontWeight.Black)
    )

    // --- Definisi Terpisah (Gaya Lama) ---
    // Yang belum ada tadi: Black & Bold
    val MontserratBlack = FontFamily(Font(R.font.montserrat_black))
    val MontserratBold = FontFamily(Font(R.font.montserrat_bold))

    // Yang sudah ada
    val MontserratMedium = FontFamily(Font(R.font.montserrat_medium))
    val MontserratRegular = FontFamily(Font(R.font.montserrat_regular))
    val MontserratSemiBold = FontFamily(Font(R.font.montserrat_semibold))

    // Poppins
    val Poppins = FontFamily(
        Font(R.font.poppins_regular, FontWeight.Normal),
        Font(R.font.poppins_medium, FontWeight.Medium),
        Font(R.font.poppins_semibold, FontWeight.SemiBold),
        Font(R.font.poppins_bold, FontWeight.Bold)
    )

    // Roboto
    val RobotoBold = FontFamily(Font(R.font.roboto_bold))
    val RobotoMedium = FontFamily(Font(R.font.roboto_medium))
    val RobotoRegular = FontFamily(Font(R.font.roboto_regular))
}