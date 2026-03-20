package com.example.remusic.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.remusic.ui.screen.LoginScreen
import com.example.remusic.ui.screen.MainScreen
import com.example.remusic.ui.screen.SplashScreen
import com.example.remusic.ui.screen.playmusic.PlayMusicScreen
import com.example.remusic.viewmodel.playmusic.PlayMusicViewModel
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
            LoginScreen(onGoogleSignInClick = onGoogleSignInClick)
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
                }
            )
        }
    }
}
