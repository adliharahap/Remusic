package com.example.remusic.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import coil.compose.AsyncImage
import com.example.remusic.data.UserManager
import com.example.remusic.navigation.ProfileRoute
import com.example.remusic.ui.theme.AppFont
import com.example.remusic.utils.handleLogout
import com.example.remusic.viewmodel.playmusic.PlayMusicViewModel
import kotlinx.coroutines.delay

@Composable
fun ProfileScreen(
    navController: NavController,
    playMusicViewModel: PlayMusicViewModel
) {
    val profileNavController = rememberNavController()
    val playerUiState by playMusicViewModel.uiState.collectAsState()

    // --- Consume pending artist navigation from PlayMusic "Lihat Playlist" via SharedFlow ---
    LaunchedEffect(Unit) {
        playMusicViewModel.artistNavigationFlow.collect { artistId ->
            if (artistId != null && playerUiState.previousTab == "profile") {
                playMusicViewModel.consumePendingArtistNavigation()
                profileNavController.navigate(
                    ProfileRoute.createRoute(id = artistId, type = "ARTIST")
                ) {
                    launchSingleTop = true
                }
            }
        }
    }

    NavHost(
        navController = profileNavController,
        startDestination = ProfileRoute.MAIN,
        modifier = Modifier.fillMaxSize()
    ) {
        composable(ProfileRoute.MAIN) {
            ProfileMainContent(navController = navController)
        }
        composable(
            route = ProfileRoute.PLAYLIST_DETAIL,
            arguments = listOf(
                navArgument(ProfileRoute.ARGS_ID) { type = NavType.StringType },
                navArgument(ProfileRoute.ARGS_PLAYLIST_TYPE) { type = NavType.StringType; defaultValue = "AUTO" }
            )
        ) { backStackEntry ->
            val id = backStackEntry.arguments?.getString(ProfileRoute.ARGS_ID) ?: ""
            val typeString = backStackEntry.arguments?.getString(ProfileRoute.ARGS_PLAYLIST_TYPE) ?: "AUTO"
            val playlistType = try {
                PlaylistType.valueOf(typeString)
            } catch (e: Exception) {
                PlaylistType.AUTO
            }
            PlaylistDetailScreen(
                songs = emptyList(),
                playlistName = "Artist",
                playlistCoverUrl = "",
                playlistType = playlistType,
                playlistId = id,
                playMusicViewModel = playMusicViewModel
            )
        }
        composable("request_song") {
            com.example.remusic.ui.screen.RequestSongScreen(navController = profileNavController, playMusicViewModel = playMusicViewModel)
        }
    }
}

@Composable
fun ProfileMainContent(navController: NavController) {
    val userProfile = UserManager.currentUser
    var isVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(100)
        isVisible = true
    }

    val verticalGradientBrush = Brush.verticalGradient(
        colorStops = arrayOf(
            0.0f to Color(0xFF755D8D),
            0.6f to Color.Black
        )
    )

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        if (userProfile != null) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(brush = verticalGradientBrush)
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 80.dp)
            ) {
                item { Spacer(modifier = Modifier.height(40.dp)) }
                
                // Profile Header
                item {
                    AnimatedVisibility(
                        visible = isVisible,
                        enter = fadeIn(animationSpec = tween(500))
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            AsyncImage(
                                model = userProfile.photoUrl,
                                contentDescription = "Profile Picture",
                                modifier = Modifier
                                    .size(120.dp)
                                    .clip(CircleShape)
                                    .border(4.dp, Color.White.copy(0.3f), CircleShape),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = userProfile.displayName ?: "Unknown User",
                                color = Color.White,
                                fontSize = 28.sp,
                                fontFamily = AppFont.Poppins,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = userProfile.email ?: "",
                                color = Color.White.copy(0.7f),
                                fontSize = 14.sp,
                                fontFamily = AppFont.Helvetica
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(Color(0xFF4A148C).copy(0.6f))
                                    .padding(horizontal = 16.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = userProfile.role.uppercase(),
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontFamily = AppFont.Helvetica,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
                
                item { Spacer(modifier = Modifier.height(32.dp)) }
                
                // Statistics Section
                item {
                    AnimatedVisibility(
                        visible = isVisible,
                        enter = fadeIn(animationSpec = tween(500, delayMillis = 200)) + 
                                slideInVertically(initialOffsetY = { 50 })
                    ) {
                        Column {
                            Text(
                                text = "Statistics",
                                color = Color.White,
                                fontSize = 20.sp,
                                fontFamily = AppFont.Poppins,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                StatCard(
                                    title = "Songs",
                                    value = "247",
                                    icon = Icons.Default.LibraryMusic,
                                    modifier = Modifier.weight(1f)
                                )
                                StatCard(
                                    title = "Artists",
                                    value = "42",
                                    icon = Icons.Default.Person,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
                
                item { Spacer(modifier = Modifier.height(24.dp)) }
                
                // Settings Section
                item {
                    AnimatedVisibility(
                        visible = isVisible,
                        enter = fadeIn(animationSpec = tween(500, delayMillis = 400)) + 
                                slideInVertically(initialOffsetY = { 50 })
                    ) {
                        Column {
                            Text(
                                text = "Settings",
                                color = Color.White,
                                fontSize = 20.sp,
                                fontFamily = AppFont.Poppins,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )
                            SettingsItem(
                                icon = Icons.Default.Person,
                                title = "Account Settings",
                                onClick = { /* TODO */ }
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            SettingsItem(
                                icon = Icons.Default.Settings,
                                title = "Preferences",
                                onClick = { /* TODO */ }
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            SettingsItem(
                                icon = Icons.Default.Info,
                                title = "About",
                                onClick = { /* TODO */ }
                            )
                        }
                    }
                }
                
                item { Spacer(modifier = Modifier.height(32.dp)) }
                
                // Logout Button
                item {
                    AnimatedVisibility(
                        visible = isVisible,
                        enter = fadeIn(animationSpec = tween(500, delayMillis = 600))
                    ) {
                        Button(
                            onClick = { handleLogout(navController) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF755D8D)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                Icons.Default.ExitToApp,
                                contentDescription = "Logout",
                                tint = Color.White
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Logout",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontFamily = AppFont.Helvetica,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                
                item { Spacer(modifier = Modifier.height(16.dp)) }
            }
        } else {
            CircularProgressIndicator(color = Color.White)
        }
    }
}

@Composable
fun StatCard(
    title: String,
    value: String,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.height(100.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(0.1f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = Color.White.copy(0.8f),
                modifier = Modifier.size(24.dp)
            )
            Column {
                Text(
                    text = value,
                    color = Color.White,
                    fontSize = 24.sp,
                    fontFamily = AppFont.Poppins,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = title,
                    color = Color.White.copy(0.6f),
                    fontSize = 12.sp,
                    fontFamily = AppFont.Helvetica
                )
            }
        }
    }
}

@Composable
fun SettingsItem(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(0.05f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = Color.White.copy(0.8f),
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = title,
                color = Color.White,
                fontSize = 16.sp,
                fontFamily = AppFont.Helvetica,
                fontWeight = FontWeight.Medium
            )
        }
    }
}