package com.example.remusic.navigation

import com.example.remusic.viewmodel.playmusic.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.remusic.data.HomeCacheManager
import com.example.remusic.data.SessionExpiredHandler
import com.example.remusic.data.SupabaseManager
import com.example.remusic.data.UserManager
import com.example.remusic.ui.screen.LoginScreen
import com.example.remusic.ui.screen.MainScreen
import com.example.remusic.ui.screen.SplashScreen
import com.example.remusic.ui.screen.playmusic.PlayMusicScreen
import com.example.remusic.viewmodel.playmusic.PlayMusicViewModel
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun AppNavGraph(
    navController: NavHostController,
    startDestination: String,
    onGoogleSignInClick: () -> Unit,
    notificationRoute: String?,
    onRouteConsumed: () -> Unit = {},
    playMusicViewModel: PlayMusicViewModel,
    connectivityObserver: com.example.remusic.utils.ConnectivityObserver
    ) {
    val connectivityStatus by connectivityObserver.observe().collectAsState(
        initial = com.example.remusic.utils.ConnectivityObserver.Status.Available
    )

    // ── GLOBAL SESSION EXPIRED HANDLER ─────────────────────────────────────────
    // Jika refresh_token habis dan Supabase tidak bisa refresh access_token lagi,
    // RemusicApplication akan emit event ini. Kita tangkap di sini dan paksa logout.
    LaunchedEffect(Unit) {
        SessionExpiredHandler.sessionExpiredEvent.collect {
            android.util.Log.w("AppNavGraph", "🔐 Session expired event diterima! Memaksa logout ke login screen...")
            try { SupabaseManager.client.auth.signOut() } catch (_: Exception) {}
            UserManager.clearUser()
            UserManager.saveCachedFcmToken(null)
            HomeCacheManager.clear()
            playMusicViewModel.stopPlaybackOnLogout()
            SessionExpiredHandler.reset()

            navController.navigate("login") {
                popUpTo(0) { inclusive = true }
            }
        }
    }

    LaunchedEffect(notificationRoute) {
        if (notificationRoute != null) {
            if (notificationRoute == "playmusic") {
                // Pastikan MainScreen ada di bawah PlayMusicScreen (Hapus Splash)
                navController.navigate("main") {
                    popUpTo("splash") { inclusive = true }
                }
                navController.navigate("playmusic")
            } else {
                navController.navigate(notificationRoute) {
                    launchSingleTop = true
                }
            }
            onRouteConsumed()
        }
    }
    //startDestination = startDestination
    NavHost(navController = navController, startDestination = "splash" ) {
        composable(
            route = "splash",
        ) {
            SplashScreen(navController)
        }
        composable(
            route = "login",
        ) {
            LoginScreen(
                onGoogleSignInClick = onGoogleSignInClick,
                onOfflineClick = { navController.navigate(PlaylistRoute.OFFLINE_PLAYLIST) }
            )
        }
        composable(PlaylistRoute.OFFLINE_PLAYLIST) {
            com.example.remusic.ui.screen.offline.OfflinePlaylistScreen(
                playMusicViewModel = playMusicViewModel,
                onBack = { navController.popBackStack() },
                onOpenPlayer = { navController.navigate("playmusic") }
            )
        }
        // Removed Search routes from here as they are now handled in BottomNavGraph (MainScreen)
        composable(route = "main") {
            MainScreen(
                rootNavController = navController,
                playMusicViewModel = playMusicViewModel,
                connectivityStatus = connectivityStatus
            )
        }
        composable(
            route = "playmusic",
        ) {
            val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()
            PlayMusicScreen(
                navController = navController,
                playMusicViewModel = playMusicViewModel,
                onNavigateToArtist = { artistId ->
                    // 1. Pop PlayMusic → kembali ke MainScreen
                    navController.popBackStack()

                    // 2. Setelah layar kembali ke MainScreen, set pendingArtistNavigation
                    //    HomeScreen/SearchScreen akan consume via SharedFlow
                    coroutineScope.launch {
                        // Beri waktu MainScreen + HomeScreen/SearchScreen untuk recompose
                        delay(200)
                        playMusicViewModel.setPendingArtistNavigation(artistId)
                    }
                },
                onNavigateToUploader = { uploaderId ->
                    navController.popBackStack()
                    coroutineScope.launch {
                        delay(200)
                        playMusicViewModel.setPendingUploaderNavigation(uploaderId)
                    }
                }
            )
        }
        composable(
            route = "add_song_to_playlist/{playlistId}",
            arguments = listOf(androidx.navigation.navArgument("playlistId") { type = androidx.navigation.NavType.StringType })
        ) { backStackEntry ->
            val playlistId = backStackEntry.arguments?.getString("playlistId") ?: return@composable
            com.example.remusic.ui.screen.AddSongToPlaylistScreen(
                playlistId = playlistId,
                playMusicViewModel = playMusicViewModel,
                navController = navController
            )
        }
    }
}
