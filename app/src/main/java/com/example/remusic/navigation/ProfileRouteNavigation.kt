package com.example.remusic.navigation

object ProfileRoute {
    const val ARGS_ID = "id"
    const val ARGS_PLAYLIST_TYPE = "type"

    const val MAIN = "profile_screen_main"
    // Route format: profile_tab_detail/{id}?type={type}
    const val PLAYLIST_DETAIL = "profile_tab_detail/{$ARGS_ID}?$ARGS_PLAYLIST_TYPE={$ARGS_PLAYLIST_TYPE}"
    const val CREATE_PLAYLIST = "profile_create_playlist"
    const val EDIT_PROFILE = "profile_edit_profile"
    const val SECURITY = "profile_security"
    const val STORAGE_CACHE = "profile_storage_cache"
    const val NOTIFICATION = "profile_notification"
    const val NOTIFICATION_DETAIL = "notification_detail/{notif}"
    const val ABOUT = "profile_about"
    const val PRIVACY_POLICY = "profile_privacy_policy"
    const val HELP_CENTER = "profile_help_center"

    fun createRoute(id: String, type: String = "AUTO") =
        "profile_tab_detail/$id?$ARGS_PLAYLIST_TYPE=$type"
}
