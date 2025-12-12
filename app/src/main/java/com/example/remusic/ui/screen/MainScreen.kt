package com.example.remusic.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.remusic.ui.LocalBottomPadding
import com.example.remusic.ui.components.BottomPlayerCard
import com.example.remusic.ui.screen.BottomNavItem.Home.BottomBar
import com.example.remusic.viewmodel.playmusic.PlayMusicViewModel

// ------ Sealed class untuk item navigasi ------
sealed class BottomNavItem(val route: String, val icon: ImageVector, val label: String) {
    object Home : BottomNavItem("home", Icons.Filled.Home, "Home")
    object Search : BottomNavItem("search", Icons.Filled.Search, "Search")
    object Upload : BottomNavItem("upload", Icons.Filled.Add, "Add Music")
    object Playlist : BottomNavItem("playlist", Icons.Filled.LibraryMusic, "Playlist")
    object Profile : BottomNavItem("profile", Icons.Filled.Person, "Profile")

    // ------ BottomBar diperbarui ke Material 3 ------
    @Composable
    fun BottomBar(navController: NavHostController) {
        val items = listOf(
            BottomNavItem.Home,
            BottomNavItem.Search,
            BottomNavItem.Upload,
            BottomNavItem.Playlist,
            BottomNavItem.Profile
        )

        // Menggunakan NavigationBar dari Material 3
        NavigationBar(
            modifier = (Modifier.height(60.dp)),
            containerColor = Color(0xD9000000),
        ) {
            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentRoute = navBackStackEntry?.destination?.route

            items.forEach { item ->
                // Menggunakan NavigationBarItem dari Material 3
                NavigationBarItem(
                    icon = {
                        if (item is BottomNavItem.Upload) {
                            // Custom icon Upload dengan background bulat putih
                            Box(
                                modifier = Modifier
                                    .size(if (currentRoute == item.route) 34.dp else 40.dp)
                                    .background(if (currentRoute == item.route) Color.White else Color.DarkGray, shape = androidx.compose.foundation.shape.CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = item.icon,
                                    contentDescription = item.label,
                                    modifier = Modifier.size(if (currentRoute == item.route) 24.dp else 26.dp),
                                    tint = if (currentRoute == item.route) Color.Black else Color.White
                                )
                            }
                        } else {
                            // Default icon untuk item lain
                            Icon(
                                item.icon,
                                contentDescription = item.label,
                                modifier = Modifier.size(if (currentRoute == item.route) 24.dp else 26.dp),
                                tint = if (currentRoute == item.route) Color.White else Color.Gray
                            )
                        }
                    },
                    label = { Text(item.label) },
                    selected = currentRoute == item.route,
                    onClick = {
                        // Logika navigasi tetap sama, sudah efisien
                        if (currentRoute != item.route) {
                            navController.navigate(item.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    },
                    alwaysShowLabel = false,
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color.White,
                        unselectedIconColor = Color.Gray,
                        selectedTextColor = Color.White,
                        unselectedTextColor = Color.Gray,
                        indicatorColor = Color.Transparent
                    )
                )
            }
        }
    }
}

// ------ Navigasi antar screen (Tidak ada perubahan) ------
@Composable
fun AppNavGraph(navController: NavHostController, rootNavController: NavController, playMusicViewModel: PlayMusicViewModel) {
    NavHost(navController = navController, startDestination = BottomNavItem.Home.route) {
        composable(BottomNavItem.Home.route) { HomeScreen(rootNavController = rootNavController, playMusicViewModel = playMusicViewModel) }
        composable(BottomNavItem.Search.route) { SearchScreen() }
        composable(BottomNavItem.Upload.route) { UploadSongScreen(onUploadMusicSuccess = {
            // ✅ AKSI NAVIGASI DIEKSEKUSI DI SINI
            navController.navigate("home") {
                // Hapus halaman upload dari back stack
                popUpTo("upload") { inclusive = true }
            }
        }) }
        composable(BottomNavItem.Playlist.route) { PlaylistScreen() }
        composable(BottomNavItem.Profile.route) { ProfileScreen(navController = rootNavController) }
    }
}

// ------ Main Screen diperbarui ke Material 3 ------
@Composable
fun MainScreen(rootNavController: NavController, playMusicViewModel: PlayMusicViewModel) {
    val navController = rememberNavController()
    val playerUiState by playMusicViewModel.uiState.collectAsState()

    // Tentukan tinggi dari komponen bawah untuk padding
    val bottomBarHeight = 65.dp // Sesuaikan dengan tinggi BottomBar Anda
    val playerCardHeight = 64.dp // Perkiraan tinggi BottomPlayerCard

    // Hitung padding dinamis
    val bottomPadding = if (playerUiState.currentSong != null) {
        bottomBarHeight + playerCardHeight
    } else {
        bottomBarHeight
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Sediakan nilai padding ke semua "anak" composable di bawahnya
        CompositionLocalProvider(LocalBottomPadding provides bottomPadding) {
            // Konten utama Anda (tidak akan tertimpa)
            AppNavGraph(
                navController = navController,
                rootNavController = rootNavController,
                playMusicViewModel = playMusicViewModel,
            )
        }

        // --- INI BAGIAN UTAMA PERBAIKANNYA ---
        // Gunakan Column untuk menumpuk komponen bawah secara vertikal
        Column(modifier = Modifier.align(Alignment.BottomCenter)) {
            // 1. BottomPlayerCard (akan muncul/hilang dengan animasi)
            BottomPlayerCard(
                uiState = playerUiState,
                onPlayPauseClick = { playMusicViewModel.togglePlayPause() },
                onCardClick = {
                    rootNavController.navigate("playmusic")
                }
            )
            // 2. BottomBar Navigasi (selalu terlihat)
            BottomBar(navController = navController)
        }
    }
}
