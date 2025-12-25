package com.example.remusic.data.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class SongWithArtist(
    val song: Song,
    val artist: Artist? // Bisa null kalau data artis tidak ditemukan
): Parcelable