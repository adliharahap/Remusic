package com.example.remusic.ui.screen

import com.example.remusic.utils.ConnectivityObserver
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
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import com.example.remusic.ui.components.CreatePlaylistBottomSheet
import com.example.remusic.ui.components.AddToPlaylistBottomSheet
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.setValue // Add this import
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
import com.example.remusic.viewmodel.homeviewmodel.HomeViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.remusic.ui.screen.playmusic.QueueOptionsBottomSheet
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import com.example.remusic.ui.components.ExitDialog
import androidx.activity.compose.BackHandler
import com.example.remusic.navigation.HomeRoute
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import android.app.Activity
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf // Ensure this is imported
import androidx.compose.runtime.remember // Ensure this is imported
import com.example.remusic.ui.screen.RequestSongScreen

// ------ Sealed class untuk item navigasi ------
sealed class BottomNavItem(val route: String, val icon: ImageVector, val label: String) {
    object Home : BottomNavItem("home", Icons.Filled.Home, "Home")
    object Search : BottomNavItem("search", Icons.Filled.Search, "Search")
    object Upload : BottomNavItem("upload", Icons.Filled.Add, "Add Music")
    object Playlist : BottomNavItem("playlist", Icons.Filled.LibraryMusic, "Playlist")
    object Profile : BottomNavItem("profile", Icons.Filled.Person, "Profile")

    // ------ BottomBar diperbarui ke Material 3 ------
    @Composable
    fun BottomBar(
        navController: NavHostController,
        onSearchReset: () -> Unit = {}, // Add callback to reset search state
        onCreatePlaylistClick: () -> Unit // New callback for bottom sheet
    ) {
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
                        if (item is Upload) {
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
                    // Simple selection logic again
                    selected = currentRoute == item.route,
                    onClick = {
                        // Reset search state if clicking Search tab
                        if (item == BottomNavItem.Search) {
                            onSearchReset()
                        }
                        
                        if (item is BottomNavItem.Upload) {
                             onCreatePlaylistClick()
                        } else if (currentRoute != item.route) {
                            navController.navigate(item.route) {
                                popUpTo(navController.graph.findStartDestination().id)
                                launchSingleTop = true
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
fun BottomNavGraph(
    navController: NavHostController, 
    rootNavController: NavController, 
    playMusicViewModel: PlayMusicViewModel,
    isSearchActive: Boolean,
    onSearchActiveChange: (Boolean) -> Unit,
    onCreatePlaylistClick: () -> Unit,
    onAtHomeRoot: (Boolean) -> Unit = {},
    homeResetTrigger: Int = 0, // Increments to reset HomeScreen nested nav to root
    connectivityStatus: ConnectivityObserver.Status = ConnectivityObserver.Status.Available,
    homeViewModel: HomeViewModel // Hoisted ViewModel
) {
    NavHost(navController = navController, startDestination = BottomNavItem.Home.route) {
        composable(BottomNavItem.Home.route) { 
            HomeScreen(
                onSearchClick = {
                    onSearchActiveChange(true)
                    navController.navigate(BottomNavItem.Search.route) {
                        popUpTo(navController.graph.findStartDestination().id)
                        launchSingleTop = true
                    }
                },
                playMusicViewModel = playMusicViewModel,
                onAtHomeRoot = onAtHomeRoot,
                homeResetTrigger = homeResetTrigger,
                connectivityStatus = connectivityStatus,
                homeViewModel = homeViewModel // Pass shared instance
            ) 
        }
        
        // Simplified Search Route (no arguments needed)
        composable(BottomNavItem.Search.route) {
            com.example.remusic.ui.screen.SearchScreen(
                playMusicViewModel = playMusicViewModel, // Pass ViewModel
                isSearchActive = isSearchActive, // Pass hoisted state
                onSearchActiveChange = onSearchActiveChange // Pass callback
            )
        }
        

        composable(BottomNavItem.Playlist.route) { 
            PlaylistScreen(
                onCreatePlaylistClick = onCreatePlaylistClick,
                playMusicViewModel = playMusicViewModel
            ) 
        }
        composable(BottomNavItem.Profile.route) {
            ProfileScreen(
                navController = rootNavController,
                playMusicViewModel = playMusicViewModel
            )
        }
        composable("request_song") {
            RequestSongScreen(navController = navController, playMusicViewModel = playMusicViewModel)
        }
        composable("create_playlist") {
            com.example.remusic.ui.screen.CreatePlaylistScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}

// ------ Main Screen diperbarui ke Material 3 ------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    rootNavController: NavController, 
    playMusicViewModel: PlayMusicViewModel,
    connectivityStatus: ConnectivityObserver.Status = ConnectivityObserver.Status.Available
) {
    val navController = rememberNavController()
    val playerUiState by playMusicViewModel.uiState.collectAsState()
    
    // Hoist HomeViewModel here to persist state across tabs
    val homeViewModel: HomeViewModel = viewModel()
    
    // Hoist Search State here
    var isSearchActive by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

    // Track if we're at the root of HomeScreen (not in nested navigation)
    var isAtHomeRoot by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(true) }

    // Trigger to reset HomeScreen nested nav to root when user returns to Home tab
    var homeResetTrigger by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(0) }

    // BottomSheet State
    var showCreatePlaylistSheet by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()

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
            BottomNavGraph(
                navController = navController,
                rootNavController = rootNavController,
                playMusicViewModel = playMusicViewModel,
                isSearchActive = isSearchActive,
                onSearchActiveChange = { isSearchActive = it },
                onCreatePlaylistClick = { showCreatePlaylistSheet = true },
                onAtHomeRoot = { isAtRoot -> isAtHomeRoot = isAtRoot },
                homeResetTrigger = homeResetTrigger,
                connectivityStatus = connectivityStatus,
                homeViewModel = homeViewModel
            )
        }

        // --- INI BAGIAN UTAMA PERBAIKANNYA ---
        // Gunakan Column untuk menumpuk komponen bawah secara vertikal
        Column(modifier = Modifier.align(Alignment.BottomCenter)) {
            // 1. BottomPlayerCard (akan muncul/hilang dengan animasi)
            BottomPlayerCard(
                uiState = playerUiState,
                onPlayPauseClick = { playMusicViewModel.togglePlayPause() },
                onLikeClick = { playMusicViewModel.toggleLike() },
                onCardClick = {
                    rootNavController.navigate("playmusic")
                }
            )
            // 2. BottomBar Navigasi (selalu terlihat)
            BottomBar(
                navController = navController,
                onSearchReset = { isSearchActive = false }, // Reset to inactive when tab clicked
                onCreatePlaylistClick = { showCreatePlaylistSheet = true }
            )
        }

        // --- EXIT CONFIRMATION MODAL ---

        val context = LocalContext.current
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = navBackStackEntry?.destination?.route
        var showExitDialog by remember { mutableStateOf(false) }

        // --- TAB TRACKING: Update previousTab whenever user switches tabs ---
        var previousRoute by remember { mutableStateOf<String?>(null) }
        LaunchedEffect(currentRoute) {
            if (currentRoute != null &&
                (currentRoute == BottomNavItem.Home.route ||
                 currentRoute == BottomNavItem.Search.route ||
                 currentRoute == BottomNavItem.Playlist.route ||
                 currentRoute == BottomNavItem.Profile.route)) {
                playMusicViewModel.setPreviousTab(currentRoute)

                // Reset HomeScreen nested nav if user is returning to Home from another tab
                if (currentRoute == BottomNavItem.Home.route && previousRoute != null && previousRoute != BottomNavItem.Home.route) {
                    homeResetTrigger++
                }
                previousRoute = currentRoute
            }
        }

        // --- CREATE PLAYLIST NAVIGATION: Listen to ViewModel events ---
        LaunchedEffect(playerUiState.navigateToCreatePlaylistEvent) {
            if (playerUiState.navigateToCreatePlaylistEvent) {
                navController.navigate("create_playlist") { launchSingleTop = true }
                playMusicViewModel.consumeNavigateToCreatePlaylistEvent()
            }
        }

        // Only show exit dialog when at Home tab AND at root home screen
        BackHandler(enabled = currentRoute == BottomNavItem.Home.route && isAtHomeRoot) {
            showExitDialog = true
        }

        if (showExitDialog) {
            ExitDialog(
                onDismissRequest = { showExitDialog = false },
                onConfirm = {
                    showExitDialog = false
                    (context as? Activity)?.finish()
                }
            )
        }

        if (showCreatePlaylistSheet) {
            CreatePlaylistBottomSheet(
                sheetState = sheetState,
                onDismissRequest = { showCreatePlaylistSheet = false },
                onCreatePlaylistClick = {
                    scope.launch { sheetState.hide() }.invokeOnCompletion {
                        if (!sheetState.isVisible) {
                            showCreatePlaylistSheet = false
                            navController.navigate("create_playlist") { launchSingleTop = true }
                        }
                    }
                },
                onCreatePlaylistWithFriendClick = {
                     scope.launch { sheetState.hide() }.invokeOnCompletion {
                        if (!sheetState.isVisible) {
                            showCreatePlaylistSheet = false
                            navController.navigate("create_playlist") { launchSingleTop = true }
                        }
                    }
                },
                onRequestSongClick = {
                    scope.launch { sheetState.hide() }.invokeOnCompletion {
                        if (!sheetState.isVisible) {
                            showCreatePlaylistSheet = false
                            navController.navigate("request_song") { launchSingleTop = true }
                        }
                    }
                }
            )
        }

        // --- Global Queue Options Bottom Sheet ---
        val selectedSong = playerUiState.selectedSongForQueueOptions
        if (selectedSong != null) {
            val queueSheetState = rememberModalBottomSheetState()
            val context = LocalContext.current
            
            QueueOptionsBottomSheet(
                sheetState = queueSheetState,
                songWithArtist = selectedSong,
                onDismiss = { playMusicViewModel.dismissQueueOptions() },
                onAddToQueue = {
                    val message = playMusicViewModel.addToQueue(selectedSong)
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                    playMusicViewModel.dismissQueueOptions()
                },
                onPlayNext = {
                    if (playerUiState.currentSong == null) {
                        // Jika tidak ada lagu yang diputar, tampilkan pesan
                        Toast.makeText(context, "Putar lagu lain terlebih dahulu", Toast.LENGTH_SHORT).show()
                    } else if (playerUiState.currentSong?.song?.id == selectedSong.song.id) {
                         Toast.makeText(context, "Lagu ini sedang diputar", Toast.LENGTH_SHORT).show()
                    } else {
                        playMusicViewModel.playNext(selectedSong)
                        Toast.makeText(context, "${selectedSong.song.title} akan diputar setelah ini", Toast.LENGTH_SHORT).show()
                    }
                    playMusicViewModel.dismissQueueOptions()
                },
                onAddToPlaylist = {
                    playMusicViewModel.showAddToPlaylistSheet(selectedSong)
                    playMusicViewModel.dismissQueueOptions()
                },
                onDownload = {
                    playMusicViewModel.downloadSong(selectedSong)
                    playMusicViewModel.dismissQueueOptions()
                },
                onAddToLiked = {
                    playMusicViewModel.toggleLike(selectedSong.song.id)
                },
                showRemoveFromPlaylist = playerUiState.playlistIdToRemoveFromQueueOptions != null,
                onRemoveFromPlaylist = {
                    val playlistId = playerUiState.playlistIdToRemoveFromQueueOptions
                    if (playlistId != null) {
                        playMusicViewModel.removeSongFromPlaylist(playlistId, selectedSong.song.id) { success, message ->
                            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                            if (success) {
                                playMusicViewModel.dismissQueueOptions()
                            }
                        }
                    }
                }
            )
        }

        // --- Global Add To Playlist Bottom Sheet ---
        val songToAdd = playerUiState.selectedSongForAddToPlaylist
        if (songToAdd != null) {
            val addToPlaylistSheetState = rememberModalBottomSheetState()
            val context = LocalContext.current

            AddToPlaylistBottomSheet(
                sheetState = addToPlaylistSheetState,
                onDismissRequest = { playMusicViewModel.dismissAddToPlaylistSheet() },
                playlists = playerUiState.userPlaylists,
                isLoading = playerUiState.isFetchingUserPlaylists,
                onPlaylistSelected = { playlist ->
                    playMusicViewModel.addSongToPlaylist(playlist.id, songToAdd.song.id) { success, message ->
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                        if (success) {
                            playMusicViewModel.dismissAddToPlaylistSheet()
                        }
                    }
                },
                onCreateNewPlaylistClick = {
                    playMusicViewModel.dismissAddToPlaylistSheet()
                    navController.navigate("create_playlist") { launchSingleTop = true }
                }
            )
        }
    }
}
