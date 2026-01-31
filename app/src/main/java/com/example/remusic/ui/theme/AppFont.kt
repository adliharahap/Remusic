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

    // Coolvetica
    val Coolvetica = FontFamily(
        Font(R.font.coolvetica_rg, FontWeight.Normal),
        Font(R.font.coolvetica_rg_it, FontWeight.Normal, androidx.compose.ui.text.font.FontStyle.Italic),
        Font(R.font.coolvetica_condensed_rg, FontWeight.Normal), // Note: Condensed usually needs separate family or font variation settings
        Font(R.font.coolvetica_compressed_hv, FontWeight.Black), // "hv" often means heavy
        Font(R.font.coolvetica_crammed_rg, FontWeight.Normal)
    )
    // Separate definitions for specific variations if needed
    val CoolveticaCompressed = FontFamily(Font(R.font.coolvetica_compressed_hv))
    val CoolveticaCondensed = FontFamily(Font(R.font.coolvetica_condensed_rg))
    val CoolveticaCrammed = FontFamily(Font(R.font.coolvetica_crammed_rg))


    // Helvetica
    val Helvetica = FontFamily(
        Font(R.font.helvetica, FontWeight.Normal),
        Font(R.font.helvetica_bold, FontWeight.Bold),
        Font(R.font.helvetica_oblique, FontWeight.Normal, androidx.compose.ui.text.font.FontStyle.Italic),
        Font(R.font.helvetica_boldoblique, FontWeight.Bold, androidx.compose.ui.text.font.FontStyle.Italic),
        Font(R.font.helvetica_light_587ebe5a59211, FontWeight.Light),
        Font(R.font.helvetica_rounded_bold_5871d05ead8de, FontWeight.Bold), // Distinct style
        Font(R.font.helvetica_compressed_5871d14b6903a, FontWeight.Normal) // Distinct style
    )

    // Separate definitions for distinct Helvetica styles
    val HelveticaRoundedBold = FontFamily(Font(R.font.helvetica_rounded_bold_5871d05ead8de))
    val HelveticaCompressed = FontFamily(Font(R.font.helvetica_compressed_5871d14b6903a))
    val HelveticaLight = FontFamily(Font(R.font.helvetica_light_587ebe5a59211))
}