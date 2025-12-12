package com.example.remusic.navigation

object HomeRoute {
    const val ARGS_PLAYLIST_TITLE = "playlistTitle"

    const val MAIN = "home_main"
    const val PLAYLIST_DETAIL = "playlist_detail/{$ARGS_PLAYLIST_TITLE}"

    fun createRoute(title: String) = "playlist_detail/$title"
}
