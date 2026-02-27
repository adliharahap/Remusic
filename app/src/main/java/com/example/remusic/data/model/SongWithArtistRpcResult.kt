package com.example.remusic.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SongWithArtistRpcResult(
    @SerialName("id") val id: String,
    @SerialName("title") val title: String,
    @SerialName("audio_url") val audioUrl: String?,
    @SerialName("cover_url") val coverUrl: String?,
    @SerialName("artist_id") val artistId: String?,
    @SerialName("canvas_url") val canvasUrl: String?,
    @SerialName("duration_ms") val durationMs: Int,
    @SerialName("telegram_audio_file_id") val telegramFileId: String?,
    @SerialName("created_at") val createdAt: String?,
    @SerialName("featured_artists") val featuredArtists: List<String>?,
    @SerialName("lyrics") val lyrics: String?,
    @SerialName("artist_name") val artistName: String,
    @SerialName("match_score") val matchScore: Float
) {
    fun toSongWithArtist(): SongWithArtist {
        val song = Song(
            id = id,
            title = title,
            audioUrl = audioUrl,
            coverUrl = coverUrl,
            artistId = artistId,
            canvasUrl = canvasUrl,
            durationMs = durationMs.toLong(),
            telegramFileId = telegramFileId,
            createdAt = createdAt,
            featuredArtists = featuredArtists ?: emptyList(),
            lyrics = lyrics
        )
        val artist = if (artistId != null) {
            Artist(
                id = artistId,
                name = artistName
            )
        } else null

        return SongWithArtist(song, artist)
    }
}
