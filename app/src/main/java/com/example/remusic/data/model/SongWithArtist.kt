package com.example.remusic.data.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

// Model ini menggabungkan data lagu dengan objek artis lengkap
@Parcelize
data class SongWithArtist(
    val song: Song,
    val artist: Artist?
): Parcelable