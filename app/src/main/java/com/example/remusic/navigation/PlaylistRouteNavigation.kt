package com.example.remusic.navigation

object PlaylistRoute {
    const val ARGS_ID = "id"
    const val ARGS_PLAYLIST_TYPE = "type"
    const val ARGS_PLAYLIST_NAME = "name"

    const val MAIN = "playlist_screen_main"
    // Route format: playlist_tab_detail/{id}?type={type}&name={name}
    const val PLAYLIST_DETAIL = "playlist_tab_detail/{$ARGS_ID}?$ARGS_PLAYLIST_TYPE={$ARGS_PLAYLIST_TYPE}&$ARGS_PLAYLIST_NAME={$ARGS_PLAYLIST_NAME}"
    const val CREATE_PLAYLIST = "playlist_create_playlist"

    fun createRoute(id: String, type: String = "AUTO", name: String = "Unknown Playlist"): String {
        val encodedName = java.net.URLEncoder.encode(name, "UTF-8")
        return "playlist_tab_detail/$id?$ARGS_PLAYLIST_TYPE=$type&$ARGS_PLAYLIST_NAME=$encodedName"
    }
}
