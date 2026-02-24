package com.example.remusic.navigation

object HomeRoute {
    const val ARGS_ID = "id"
    const val ARGS_PLAYLIST_TYPE = "type"

    const val MAIN = "home_main"
    // Route format: playlist_detail/{id}?type={type}
    const val PLAYLIST_DETAIL = "playlist_detail/{$ARGS_ID}?$ARGS_PLAYLIST_TYPE={$ARGS_PLAYLIST_TYPE}"
    const val CREATE_PLAYLIST = "home_create_playlist"

    fun createRoute(id: String, type: String = "AUTO") = 
        "playlist_detail/$id?$ARGS_PLAYLIST_TYPE=$type"
}
