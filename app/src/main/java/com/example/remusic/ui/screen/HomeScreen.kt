package com.example.remusic.ui.screen

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.example.remusic.data.preferences.UserPreferencesRepository
import com.example.remusic.navigation.HomeRoute
import com.example.remusic.ui.components.homecomponents.FollowedArtistsSection
import com.example.remusic.ui.components.homecomponents.HomeHeader
import com.example.remusic.ui.components.homecomponents.OfficialPlaylistSection
import com.example.remusic.ui.components.homecomponents.PlaylistSectionSkeleton
import com.example.remusic.ui.components.homecomponents.QuickPickCarousel
import com.example.remusic.ui.components.homecomponents.QuickPickCarouselSkeleton
import com.example.remusic.ui.components.homecomponents.SongSection
import com.example.remusic.ui.components.homecomponents.SongSectionSkeleton
import com.example.remusic.utils.GreetingUtils
import com.example.remusic.viewmodel.homeviewmodel.HomeUiState
import com.example.remusic.viewmodel.homeviewmodel.HomeViewModel
import com.example.remusic.viewmodel.playmusic.PlayMusicViewModel


@Composable
fun HomeScreen(
    // rootNavController removed, using callback instead for clarity
    homeViewModel: HomeViewModel = viewModel(),
    playMusicViewModel: PlayMusicViewModel,
    onSearchClick: () -> Unit
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
                homeNavController = homeNavController, // Berikan nested controller
                onSearchClick = onSearchClick
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
                    val songList = when (playlistTitle) {
                        "Sering Kamu Putar" -> state.recentlyPlayed
                        "Top Trending" -> state.topTrending
                        "Most Loved" -> state.mostLoved
                        else -> state.allSongsWithArtists.take(20)
                    }
                    val cover = songList.firstOrNull()?.song?.coverUrl ?: ""
                    Pair(songList, cover)
                }
                else -> Pair(emptyList<SongWithArtist>(), "")
            }

            // Panggil PlaylistDetailScreen
            PlaylistDetailScreen(
                songs = songs,
                playlistName = playlistTitle,
                playlistCoverUrl = coverUrl,
                playMusicViewModel = playMusicViewModel
            )
        }
    }
}

// --- Composable Baru: Isi dari Layar Utama Home ---
@Composable
private fun HomeMainScreen(
    homeViewModel: HomeViewModel,
    playMusicViewModel: PlayMusicViewModel,
    homeNavController: NavHostController, // Controller nested
    onSearchClick: () -> Unit
) {
    val user = UserManager.currentUser
    val homeState by homeViewModel.uiState.collectAsState()
    val greeting = GreetingUtils.getGreeting()
    val scrollState = rememberScrollState()
    
    // Get saved color from preferences
    val context = LocalContext.current
    val userPreferencesRepository = remember { UserPreferencesRepository(context) }
    val savedColor by userPreferencesRepository.lastSongColorFlow.collectAsState(initial = Color(0xFF755D8D))
    
    // Animate color transitions smoothly
    val lastSongColor by animateColorAsState(
        targetValue = savedColor,
        animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing),
        label = "gradientColorAnimation"
    )

    val verticalGradientBrush = Brush.verticalGradient(
        colorStops = arrayOf(
            0.0f to Color(0xFF755D8D),
            0.23f to Color.Black
        )
    )

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black) // Solid black background
                .verticalScroll(scrollState)
                .padding(bottom = 130.dp)
        ) {
            when (val state = homeState) {
                is HomeUiState.Loading -> {
                    // Show header immediately (data from SplashScreen)
                    Box(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Gradient background with saved color
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(450.dp)
                                .background(
                                    brush = Brush.verticalGradient(
                                        colorStops = arrayOf(
                                            0.0f to lastSongColor,
                                            0.35f to lastSongColor.copy(alpha = 0.7f).compositeOver(Color.Black),
                                            0.65f to lastSongColor.copy(alpha = 0.4f).compositeOver(Color.Black),
                                            1.0f to Color.Black
                                        )
                                    )
                                )
                        )

                        Column {
                            Spacer(modifier = Modifier.height(30.dp))

                            // Header is visible immediately
                            HomeHeader(
                                name = user?.displayName,
                                greeting = greeting,
                                profileImageUrl = user?.photoUrl,
                                onSearchClick = { },
                                onNotificationClick = { },
                                dominantColor = lastSongColor
                            )

                            // Quick Pick Carousel Skeleton
                            QuickPickCarouselSkeleton()
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Section Skeletons
                    SongSectionSkeleton(title = "Sering Kamu Putar")
                    Spacer(modifier = Modifier.height(24.dp))

                    SongSectionSkeleton(title = "Paling Kamu Suka")
                    Spacer(modifier = Modifier.height(24.dp))

                    SongSectionSkeleton(title = "Sedang Trending")
                    Spacer(modifier = Modifier.height(24.dp))

                    PlaylistSectionSkeleton()
                }

                is HomeUiState.Success -> {
                    // Gradient overlay that extends to carousel
                    Box(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Gradient background with dynamic color
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(450.dp) // Extended height to cover header + carousel
                                .background(
                                    brush = Brush.verticalGradient(
                                        colorStops = arrayOf(
                                            0.0f to lastSongColor,
                                            0.35f to lastSongColor.copy(alpha = 0.7f).compositeOver(Color.Black),
                                            0.65f to lastSongColor.copy(alpha = 0.4f).compositeOver(Color.Black),
                                            1.0f to Color.Black
                                        )
                                    )
                                )
                        )

                        // Content on top of gradient
                        Column {
                            Spacer(modifier = Modifier.height(30.dp))

                            HomeHeader(
                                name = user?.displayName,
                                greeting = greeting,
                                profileImageUrl = user?.photoUrl,
                                // Use callback to navigate
                                onSearchClick = onSearchClick,
                                onNotificationClick = { /* TODO: Show notifications */ },
                                dominantColor = lastSongColor
                            )

                            // Quick Pick Carousel - Use persisted data from ViewModel
                            QuickPickCarousel(
                                songs = state.quickPickSongs,
                                onSongClick = { index ->
                                    val song = state.quickPickSongs.getOrNull(index)
                                    if (song != null) {
                                        playMusicViewModel.playSongWithSmartQueue(song)
                                        playMusicViewModel.playingMusicFromPlaylist("Pilihan Cepat")
                                    }
                                },
                                onMoreClick = { song ->
                                    playMusicViewModel.showQueueOptions(song)
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 2. Sering Kamu Putar (Recently Played)
                    if (state.recentlyPlayed.isNotEmpty()) {
                        SongSection(
                            title = "Sering Kamu Putar",
                            displayItems = state.recentlyPlayed,
                            playMusicViewModel = playMusicViewModel,
                            onSeeAllClick = {
                                homeNavController.navigate(HomeRoute.createRoute("Sering Kamu Putar"))
                            },
                            onLongClick = { song ->
                                playMusicViewModel.showQueueOptions(song)
                            }
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                    }

                    // 3. Artists You Follow
                    if (state.followedArtists.isNotEmpty()) {
                        FollowedArtistsSection(
                            artists = state.followedArtists,
                            onArtistClick = { artist ->
                                // TODO: Navigate to artist detail
                            }
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                    }

                    // 4. Lagu Paling Banyak Disukai (Most Loved)
                    if (state.mostLoved.isNotEmpty()) {
                        SongSection(
                            title = "Lagu Paling Banyak Disukai",
                            displayItems = state.mostLoved,
                            playMusicViewModel = playMusicViewModel,
                            onSeeAllClick = {
                                homeNavController.navigate(HomeRoute.createRoute("Most Loved"))
                            },
                            onLongClick = { song ->
                                playMusicViewModel.showQueueOptions(song)
                            }
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                    }

                    // 5. Top Trending
                    if (state.topTrending.isNotEmpty()) {
                        SongSection(
                            title = "Top Trending",
                            displayItems = state.topTrending,
                            playMusicViewModel = playMusicViewModel,
                            onSeeAllClick = {
                                homeNavController.navigate(HomeRoute.createRoute("Top Trending"))
                            },
                            onLongClick = { song ->
                                playMusicViewModel.showQueueOptions(song)
                            }
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    // 1. Official Playlists (below carousel) //taruh bawah karena masih 1 playlist masih sikit
                    if (state.officialPlaylists.isNotEmpty()) {
                        OfficialPlaylistSection(
                            playlists = state.officialPlaylists,
                            onPlaylistClick = { playlist ->
                                // TODO: Navigate to playlist detail
                            }
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }

                is HomeUiState.Error -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "😕 Oops!",
                                color = Color.White,
                                fontSize = 24.sp
                            )
                            Text(
                                text = state.message,
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ScrollAnimatedBlobs(scrollOffset: Int) {
    val infiniteTransition = rememberInfiniteTransition(label = "bgBlobs")

    // Blob 1 - Larger, moves with scroll
    val blob1OffsetX by infiniteTransition.animateFloat(
        initialValue = -80f,
        targetValue = 80f,
        animationSpec = infiniteRepeatable(
            animation = tween(5000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "blob1X"
    )

    // Blob 2 - Different movement
    val blob2OffsetX by infiniteTransition.animateFloat(
        initialValue = 220f,
        targetValue = 300f,
        animationSpec = infiniteRepeatable(
            animation = tween(4500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "blob2X"
    )

    // Blob 1 - follows scroll (more visible)
    Box(
        modifier = Modifier
            .size(400.dp)
            .offset(
                x = blob1OffsetX.dp,
                y = (scrollOffset * 0.3f).dp + 100.dp
            )
            .clip(CircleShape)
            .background(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF9C27B0).copy(alpha = 0.35f),
                        Color.Transparent
                    )
                )
            )
            .blur(80.dp)
    )

    // Blob 2 - different scroll rate (more visible)
    Box(
        modifier = Modifier
            .size(350.dp)
            .offset(
                x = blob2OffsetX.dp,
                y = (scrollOffset * 0.5f).dp + 400.dp
            )
            .clip(CircleShape)
            .background(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF3F51B5).copy(alpha = 0.3f),
                        Color.Transparent
                    )
                )
            )
            .blur(75.dp)
    )
}
