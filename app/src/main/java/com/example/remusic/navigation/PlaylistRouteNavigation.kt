package com.example.remusic.navigation

object PlaylistRoute {
    const val ARGS_ID = "id"
    const val ARGS_PLAYLIST_TYPE = "type"

    const val MAIN = "playlist_screen_main"
    // Route format: playlist_tab_detail/{id}?type={type}
    const val PLAYLIST_DETAIL = "playlist_tab_detail/{$ARGS_ID}?$ARGS_PLAYLIST_TYPE={$ARGS_PLAYLIST_TYPE}"

    fun createRoute(id: String, type: String = "AUTO") =
        "playlist_tab_detail/$id?$ARGS_PLAYLIST_TYPE=$type"
}
