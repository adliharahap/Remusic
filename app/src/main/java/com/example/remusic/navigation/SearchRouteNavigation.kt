package com.example.remusic.navigation

object SearchRoute {
    const val ARGS_ID = "id"
    const val ARGS_PLAYLIST_TYPE = "type"

    const val MAIN = "search_main"
    // Route format: search_playlist_detail/{id}?type={type}
    const val PLAYLIST_DETAIL = "search_playlist_detail/{$ARGS_ID}?$ARGS_PLAYLIST_TYPE={$ARGS_PLAYLIST_TYPE}"
    const val CREATE_PLAYLIST = "search_create_playlist"

    fun createRoute(id: String, type: String = "AUTO") = 
        "search_playlist_detail/$id?$ARGS_PLAYLIST_TYPE=$type"
}
