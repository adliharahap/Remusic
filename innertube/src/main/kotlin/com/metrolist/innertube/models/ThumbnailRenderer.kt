package com.metrolist.innertube.models

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class ThumbnailRenderer(
    @JsonNames("croppedSquareThumbnailRenderer")
    val musicThumbnailRenderer: MusicThumbnailRenderer?,
    val musicAnimatedThumbnailRenderer: MusicAnimatedThumbnailRenderer?,
    val croppedSquareThumbnailRenderer: MusicThumbnailRenderer?,
) {
    @Serializable
    data class MusicThumbnailRenderer(
        val thumbnail: Thumbnails,
        val thumbnailCrop: String?,
        val thumbnailScale: String?,
    ) {
        fun getThumbnailUrl(): String? {
            var url = thumbnail.thumbnails.lastOrNull()?.url ?: return null
            if (url.contains("=")) {
                // Force high resolution w800 and s800
                url = url.replace(Regex("=w\\d+-h\\d+.*"), "=w800-h800-l90-rj")
                         .replace(Regex("=s\\d+.*"), "=s800-l90-rj")
            } else {
                // If it doesn't have "=", it might be a direct link, but let's try appending
                // if it looks like a googleusercontent/ggpht link
                if (url.contains("googleusercontent.com") || url.contains("ggpht.com")) {
                    url += "=w800-h800-l90-rj"
                }
            }
            return url
        }
    }

    @Serializable
    data class MusicAnimatedThumbnailRenderer(
        val animatedThumbnail: Thumbnails,
        val backupRenderer: MusicThumbnailRenderer,
    )
}
