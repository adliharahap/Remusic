package com.example.remusic.data.network

import com.google.gson.annotations.SerializedName

data class DeezerResponse(
    val data: List<DeezerTrack>
)

data class DeezerTrack(
    val id: Long,
    val title: String,
    val duration: Int,
    val preview: String,
    val artist: DeezerArtist,
    val album: DeezerAlbum,
    val link: String
)

data class DeezerArtist(
    val id: Long,
    val name: String,
    @SerializedName("picture_medium")
    val pictureMedium: String
)

data class DeezerAlbum(
    val id: Long,
    val title: String,
    @SerializedName("cover_medium")
    val coverMedium: String
)
