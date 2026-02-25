package com.example.remusic.navigation

object PlaylistRoute {
    const val ARGS_ID = "id"
    const val ARGS_PLAYLIST_TYPE = "type"
    const val ARGS_PLAYLIST_NAME = "name"
    const val ARGS_PLAYLIST_COVER = "coverUrl" // New parameter for instant image load

    const val MAIN = "playlist_screen_main"
    // Route format: playlist_tab_detail/{id}?type={type}&name={name}&coverUrl={coverUrl}
    const val PLAYLIST_DETAIL = "playlist_tab_detail/{$ARGS_ID}?$ARGS_PLAYLIST_TYPE={$ARGS_PLAYLIST_TYPE}&$ARGS_PLAYLIST_NAME={$ARGS_PLAYLIST_NAME}&$ARGS_PLAYLIST_COVER={$ARGS_PLAYLIST_COVER}"
    const val CREATE_PLAYLIST = "playlist_create_playlist"

    fun createRoute(id: String, type: String = "AUTO", name: String = "Unknown Playlist", coverUrl: String = ""): String {
        val encodedName = java.net.URLEncoder.encode(name, "UTF-8")
        val encodedCover = java.net.URLEncoder.encode(coverUrl, "UTF-8") // Encode the URL
        return "playlist_tab_detail/$id?$ARGS_PLAYLIST_TYPE=$type&$ARGS_PLAYLIST_NAME=$encodedName&$ARGS_PLAYLIST_COVER=$encodedCover"
    }
}
