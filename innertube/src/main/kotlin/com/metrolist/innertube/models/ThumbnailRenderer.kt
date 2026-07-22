package com.metrolist.innertube.models

import kotlinx.serialization.Serializable

@Serializable
data class ThumbnailRenderer(
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
        fun getThumbnailUrl() = thumbnail.thumbnails.lastOrNull()?.url?.toHdUrl()
    }

    fun getThumbnailUrl(): String? =
        musicThumbnailRenderer?.getThumbnailUrl()
            ?: musicAnimatedThumbnailRenderer?.backupRenderer?.getThumbnailUrl()
            ?: croppedSquareThumbnailRenderer?.getThumbnailUrl()

    @Serializable
    data class MusicAnimatedThumbnailRenderer(
        val animatedThumbnail: Thumbnails,
        val backupRenderer: MusicThumbnailRenderer,
    )
}

fun String.toHdUrl(): String {
    var url = this
    
    if (url.contains("googleusercontent.com") || url.contains("ggpht.com")) {
        val index = url.lastIndexOf('=')
        if (index != -1) {
            val suffix = url.substring(index)
            if (suffix.contains("w") || suffix.contains("s") || suffix.contains("h")) {
                url = url.substring(0, index) + "=w544-h544-l90-rj"
            }
        }
    }
    
    if (url.contains("ytimg.com")) {
        url = url.replace("/default.jpg", "/maxresdefault.jpg")
                 .replace("/hqdefault.jpg", "/maxresdefault.jpg")
                 .replace("/mqdefault.jpg", "/maxresdefault.jpg")
                 .replace("/sddefault.jpg", "/maxresdefault.jpg")
    }
    
    return url
}
