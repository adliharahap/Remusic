package com.example.remusic.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.remusic.data.UserManager
import com.example.remusic.data.model.SongWithArtist
import com.example.remusic.navigation.HomeRoute
import com.example.remusic.ui.components.homecomponents.ArtistSection
import com.example.remusic.ui.components.homecomponents.HomeHeader
import com.example.remusic.ui.components.homecomponents.SongSection
import com.example.remusic.utils.GreetingUtils
import com.example.remusic.viewmodel.homeviewmodel.HomeUiState
import com.example.remusic.viewmodel.homeviewmodel.HomeViewModel
import com.example.remusic.viewmodel.playmusic.PlayMusicViewModel


@Composable
fun HomeScreen(
    rootNavController: NavController, // Ini NavController UTAMA (dari MainScreen)
    homeViewModel: HomeViewModel = viewModel(),
    playMusicViewModel: PlayMusicViewModel,
) {
    // Buat NavController BARU untuk navigasi di dalam Home
    val homeNavController = rememberNavController()

    // HomeViewModel akan dibagikan ke semua layar di dalam nested NavHost ini

    NavHost(
        navController = homeNavController,
        startDestination = HomeRoute.MAIN,
        modifier = Modifier.fillMaxSize()
    ) {
        // --- Layar 1: Tampilan Utama Home ---
        composable(HomeRoute.MAIN) {
            HomeMainScreen(
                homeViewModel = homeViewModel,
                playMusicViewModel = playMusicViewModel,
                homeNavController = homeNavController // Berikan nested controller
            )
        }

        // --- Layar 2: Tampilan Detail Playlist ---
        composable(
            route = HomeRoute.PLAYLIST_DETAIL,
            arguments = listOf(navArgument(HomeRoute.ARGS_PLAYLIST_TITLE) { type = NavType.StringType })
        ) { backStackEntry ->
            // Ambil judul playlist dari argumen navigasi
            val playlistTitle = backStackEntry.arguments?.getString(HomeRoute.ARGS_PLAYLIST_TITLE) ?: ""

            // Ambil state dari HomeViewModel yang sama
            val homeState by homeViewModel.uiState.collectAsState()

            // Tentukan data mana yang akan ditampilkan
            val (songs, coverUrl) = when (val state = homeState) {
                is HomeUiState.Success -> {
                    // Logika untuk mengambil data berdasarkan judul
                    // Anda bisa buat ini lebih canggih, misal dgn ID
                    val songList = when (playlistTitle) {
                        "Recently Played" -> state.songsWithArtists.take(3)
                        "Top Trending" -> state.songsWithArtists.drop(3).take(3)
                        "All Songs" -> state.songsWithArtists
                        else -> emptyList()
                    }
                    val cover = songList.firstOrNull()?.song?.coverUrl ?: ""
                    Pair(songList, cover)
                }
                else -> Pair(emptyList<SongWithArtist>(), "") // Loading atau Error
            }

            // Panggil PlaylistDetailScreen Anda yang sudah ada
            // (pastikan impornya benar)
            PlaylistDetailScreen(
                songs = songs,
                playlistName = playlistTitle,
                playlistCoverUrl = coverUrl
            )
        }
    }
}

// --- Composable Baru: Isi dari Layar Utama Home ---
@Composable
private fun HomeMainScreen(
    homeViewModel: HomeViewModel,
    playMusicViewModel: PlayMusicViewModel,
    homeNavController: NavHostController // Controller nested
) {
    val user = UserManager.currentUser
    val homeState by homeViewModel.uiState.collectAsState()
    val greeting = GreetingUtils.getGreeting()

    val verticalGradientBrush = Brush.verticalGradient(
        colorStops = arrayOf(
            0.0f to Color(0xFF755D8D),
            0.23f to Color.Black
        )
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(brush = verticalGradientBrush)
            .verticalScroll(rememberScrollState())
            .padding(bottom = 130.dp) // Padding untuk BottomBar
    ) {
        Spacer(modifier = Modifier.height(30.dp))
        HomeHeader(
            name = user?.displayName,
            greeting = greeting,
            profileImageUrl = user?.photoUrl?.toString(),
            onNotificationClick = { }
        )
        Spacer(modifier = Modifier.height(16.dp))

        when (val state = homeState) {
            is HomeUiState.Loading -> {
                HomeSkeletonScreen()
            }
            is HomeUiState.Success -> {
                val allSongsWithArtists = state.songsWithArtists
                val recentlyPlayed = allSongsWithArtists.take(10)
                val topTrending = allSongsWithArtists.drop(10).take(10)

                SongSection(
                    title = "Recently Played",
                    displayItems = recentlyPlayed,
                    playMusicViewModel = playMusicViewModel,
                    onSeeAllClick = {
                        // Navigasi menggunakan nested controller
                        homeNavController.navigate(HomeRoute.createRoute("Recently Played"))
                    }
                )
                Spacer(modifier = Modifier.height(16.dp))

                val recentlyPlayedArtists = recentlyPlayed.distinctBy { it.artist?.id }
                ArtistSection("Artist Recently Add", recentlyPlayedArtists)

                Spacer(modifier = Modifier.height(16.dp))
                SongSection(
                    title = "Top Trending",
                    displayItems = topTrending,
                    playMusicViewModel = playMusicViewModel,
                    onSeeAllClick = {
                        homeNavController.navigate(HomeRoute.createRoute("Top Trending"))
                    }
                )
                Spacer(modifier = Modifier.height(16.dp))

                //list untuk "All Songs" yang sudah di-filter dan di-limit
                val allSongsSorted = allSongsWithArtists
                    .sortedByDescending { it.song.createdAt }
                // Buat daftar TERBATAS untuk UI (dari daftar yang sudah disortir)
                val allSongsSortedAndLimited = allSongsSorted.take(20)
                SongSection(
                    title = "All Songs",
                    displayItems = allSongsSortedAndLimited,
                    fullPlaylistForPlayback = allSongsSorted,
                    playMusicViewModel = playMusicViewModel,
                    onSeeAllClick = {
                        homeNavController.navigate(HomeRoute.createRoute("All Songs"))
                    }
                )
            }
            is HomeUiState.Error -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(text = "Error: ${state.message}", color = Color.Red)
                }
            }
        }
    }
}
