package com.example.remusic.ui.theme

import com.example.remusic.R
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

object AppFont {
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

    // --- REKOMENDASI: Pakai ini untuk LyricsScreen ---
    // Fallback Montserrat to Poppins
    val Montserrat = Poppins
    val MontserratBlack = FontFamily(Font(R.font.poppins_bold))
    val MontserratBold = FontFamily(Font(R.font.poppins_bold))
    val MontserratMedium = FontFamily(Font(R.font.poppins_medium))
    val MontserratRegular = FontFamily(Font(R.font.poppins_regular))
    val MontserratSemiBold = FontFamily(Font(R.font.poppins_semibold))

    // Coolvetica fallback to Poppins
    val Coolvetica = Poppins
    val CoolveticaCompressed = FontFamily(Font(R.font.poppins_bold))
    val CoolveticaCondensed = FontFamily(Font(R.font.poppins_regular))
    val CoolveticaCrammed = FontFamily(Font(R.font.poppins_regular))

    // Helvetica
    val Helvetica = FontFamily(
        Font(R.font.helvetica, FontWeight.Normal),
        Font(R.font.helvetica_bold, FontWeight.Bold),
        Font(R.font.helvetica_rounded_bold_5871d05ead8de, FontWeight.Bold)
    )

    // Separate definitions for distinct Helvetica styles
    val HelveticaRoundedBold = FontFamily(Font(R.font.helvetica_rounded_bold_5871d05ead8de))
    val HelveticaCompressed = FontFamily(Font(R.font.helvetica, FontWeight.Normal))
    val HelveticaLight = FontFamily(Font(R.font.helvetica, FontWeight.Normal))
}