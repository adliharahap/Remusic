package com.example.remusic.utils

import java.util.Locale

/**
 * Extension function untuk mengubah huruf pertama setiap kata dalam String menjadi huruf besar.
 * Contoh: "adli harahap" -> "Adli Harahap"
 * "ADLI RAHMAN" -> "Adli Rahman" (menangani input huruf besar)
 */
fun String.capitalizeWords(): String =
    // 1. Pisahkan string menjadi beberapa kata berdasarkan spasi
    this.split(' ').joinToString(" ") { word ->
        // 2. Untuk setiap kata:
        //    a. Ubah semua huruf menjadi kecil (untuk menangani input seperti "ADLI")
        //    b. Ganti huruf pertama menjadi versi title-case (huruf besar)
        word.lowercase().replaceFirstChar {
            if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
        }
    }