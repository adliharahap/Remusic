package com.example.remusic.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.remusic.ui.screen.LoginScreen
import com.example.remusic.ui.screen.MainScreen
import com.example.remusic.ui.screen.SplashScreen
import com.example.remusic.ui.screen.playmusic.PlayMusicScreen
import com.example.remusic.viewmodel.playmusic.PlayMusicViewModel

@Composable
fun AppNavGraph(
    navController: NavHostController,
    startDestination: String,
    onGoogleSignInClick: () -> Unit,
    notificationRoute: String?,
    playMusicViewModel: PlayMusicViewModel
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
            MainScreen(
                rootNavController = navController,
                playMusicViewModel = playMusicViewModel
            )
        }
        composable(
            route = "playmusic",
        ) {
            PlayMusicScreen(
                navController = navController,
                playMusicViewModel = playMusicViewModel
            )
        }
    }
}
