package com.example.remusic.navigation

object ProfileRoute {
    const val ARGS_ID = "id"
    const val ARGS_PLAYLIST_TYPE = "type"

    const val MAIN = "profile_screen_main"
    // Route format: profile_tab_detail/{id}?type={type}
    const val PLAYLIST_DETAIL = "profile_tab_detail/{$ARGS_ID}?$ARGS_PLAYLIST_TYPE={$ARGS_PLAYLIST_TYPE}"

    fun createRoute(id: String, type: String = "AUTO") =
        "profile_tab_detail/$id?$ARGS_PLAYLIST_TYPE=$type"
}
