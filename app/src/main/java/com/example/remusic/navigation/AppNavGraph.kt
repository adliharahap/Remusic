package com.example.remusic.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.remusic.data.model.SongWithArtist
import com.example.remusic.ui.screen.LoginScreen
import com.example.remusic.ui.screen.MainScreen
import com.example.remusic.ui.screen.SplashScreen
import com.example.remusic.ui.screen.playmusic.PlayMusicScreen

@Composable
fun AppNavGraph(
    navController: NavHostController,
    startDestination: String,
    onGoogleSignInClick: () -> Unit,
    notificationRoute: String?
    ) {
    LaunchedEffect(notificationRoute) {
        if (notificationRoute != null) {
            navController.navigate(notificationRoute)
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
        composable(
            route = "main",
        ) {
            MainScreen(rootNavController = navController)
        }
        composable(
            route = "playmusic",
        ) {
            val songs = navController.previousBackStackEntry
                ?.savedStateHandle
                ?.get<List<SongWithArtist>>("songs")

            val index = navController.previousBackStackEntry
                ?.savedStateHandle
                ?.get<Int>("index") ?: 0

            PlayMusicScreen(
                songs = songs ?: emptyList(),
                initialIndex = index,
                navController = navController,
            )
        }
    }
}
