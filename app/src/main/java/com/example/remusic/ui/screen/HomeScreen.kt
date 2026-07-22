package com.example.remusic.ui.screen

import com.example.remusic.viewmodel.playmusic.*
import com.example.remusic.utils.ConnectivityObserver
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.remusic.data.UserManager
import com.example.remusic.data.model.SongWithArtist
import com.example.remusic.data.preferences.UserPreferencesRepository
import com.example.remusic.navigation.HomeRoute
import com.example.remusic.ui.components.homecomponents.ArtistRecommendationSection
import com.example.remusic.ui.components.homecomponents.CommunityAlbumSection
import com.example.remusic.ui.components.homecomponents.FeaturedPlaylistSection
import com.example.remusic.ui.components.homecomponents.FeaturedPlaylist
import com.example.remusic.ui.components.homecomponents.FollowedArtistsSection
import com.example.remusic.ui.components.homecomponents.HomeHeader
import com.example.remusic.ui.components.homecomponents.OfficialPlaylistSection
import com.example.remusic.ui.components.homecomponents.PlaylistSectionSkeleton
import com.example.remusic.ui.components.homecomponents.QuickPickCarousel
import com.example.remusic.ui.components.homecomponents.QuickPickCarouselSkeleton
import com.example.remusic.ui.components.homecomponents.SongSection
import com.example.remusic.ui.components.homecomponents.SongSectionSkeleton
import com.example.remusic.ui.components.homecomponents.SpeedDialSection
import com.example.remusic.ui.components.homecomponents.SimilarArtistSection
import com.example.remusic.ui.components.homecomponents.RecentlyPlayedGridPagerSection
import com.example.remusic.utils.GreetingUtils
import com.example.remusic.viewmodel.homeviewmodel.HomeUiState
import com.example.remusic.viewmodel.homeviewmodel.HomeViewModel
import com.example.remusic.viewmodel.playmusic.PlayMusicViewModel
import com.example.remusic.viewmodel.AppUpdateViewModel
import android.content.Intent
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BrowserUpdated
import androidx.compose.ui.text.font.FontWeight
import com.example.remusic.viewmodel.notification.NotificationViewModel
import androidx.core.net.toUri
import com.metrolist.innertube.models.AlbumItem
import com.metrolist.innertube.models.ArtistItem
import com.metrolist.innertube.models.EpisodeItem
import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.innertube.models.PodcastItem
import com.metrolist.innertube.models.SongItem
import com.example.remusic.data.model.Song
import com.example.remusic.data.model.Artist as DbArtist

@Composable
fun HomeScreen(
    // rootNavController removed, using callback instead for clarity
    homeViewModel: HomeViewModel,
    playMusicViewModel: PlayMusicViewModel,
    onSearchClick: () -> Unit,
    onNotificationClick: () -> Unit = {},
    onRequestSongClick: () -> Unit = {},
    onAtHomeRoot: (Boolean) -> Unit = {}, // Callback to notify if at root
    homeResetTrigger: Int = 0,            // Increments to reset nested nav to root
    connectivityStatus: ConnectivityObserver.Status = ConnectivityObserver.Status.Available,
    appUpdateViewModel: AppUpdateViewModel = viewModel()
) {
    // Listen to connectivity changes and trigger refresh
    LaunchedEffect(connectivityStatus) {
        if (connectivityStatus == ConnectivityObserver.Status.Available) {
            homeViewModel.onConnectivityRestored()
        }
    }
    // Buat NavController BARU untuk navigasi di dalam Home
    val homeNavController = rememberNavController()

    // Track if we're at the root home screen
    val navBackStackEntry by homeNavController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val isAtRoot = currentRoute == HomeRoute.MAIN
    
    // Notify parent about root status
    LaunchedEffect(isAtRoot) {
        onAtHomeRoot(isAtRoot)
    }

    // --- Cek Update Aplikasi (Hanya di Root Home) ---
    val updateAvailable by appUpdateViewModel.updateAvailable.collectAsState()
    
    LaunchedEffect(isAtRoot) {
        if (isAtRoot && updateAvailable == null) {
            appUpdateViewModel.checkForUpdates()
        }
    }

    // --- Consume pending artist navigation from PlayMusic "Lihat Playlist" via SharedFlow ---
    val playerUiState by playMusicViewModel.uiState.collectAsState()
    LaunchedEffect(Unit) {
        playMusicViewModel.artistNavigationFlow.collect { artistId ->
            if (artistId != null && playerUiState.previousTab == "home") {
                playMusicViewModel.consumePendingArtistNavigation()
                homeNavController.navigate(
                    HomeRoute.createRoute(id = artistId, type = "ARTIST")
                ) {
                    launchSingleTop = true
                }
            }
        }
    }

    // --- Consume pending uploader navigation via SharedFlow ---
    LaunchedEffect(Unit) {
        playMusicViewModel.uploaderNavigationFlow.collect { uploaderId ->
            if (uploaderId != null && playerUiState.previousTab == "home") {
                playMusicViewModel.consumePendingUploaderNavigation()
                homeNavController.navigate(
                    HomeRoute.createRoute(id = uploaderId, type = "UPLOADER")
                ) {
                    launchSingleTop = true
                }
            }
        }
    }

    // --- Reset nested nav to root when user returns to Home from another tab ---
    LaunchedEffect(homeResetTrigger) {
        if (homeResetTrigger > 0) {
            homeNavController.popBackStack(HomeRoute.MAIN, inclusive = false)
        }
    }

    // HomeViewModel akan dibagikan ke semua layar di dalam nested NavHost ini

    // Dialog Update Aplikasi
    updateAvailable?.let { update ->
        val context = LocalContext.current
        AlertDialog(
            onDismissRequest = { 
                if (!update.isMandatory) appUpdateViewModel.dismissUpdate() 
            },
            icon = {
                Icon(Icons.Rounded.BrowserUpdated, contentDescription = "Update Available", tint = Color(0xFFE91E63))
            },
            title = {
                Text(text = "Versi Baru Tersedia!", fontWeight = FontWeight.Bold, color = Color.White)
            },
            text = {
                Column {
                    Text("ReMusic ${update.versionName} kini tersedia.", color = Color.White.copy(0.8f))
                    if (!update.releaseNotes.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Apa yang baru:\n${update.releaseNotes}",
                            color = Color.White.copy(0.6f),
                            fontSize = 13.sp
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, update.downloadUrl.toUri())
                    context.startActivity(intent)
                }) {
                    Text("Update Sekarang", color = Color(0xFFE91E63), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                if (!update.isMandatory) {
                    TextButton(onClick = { appUpdateViewModel.dismissUpdate() }) {
                        Text("Nanti", color = Color.Gray)
                    }
                }
            },
            containerColor = Color(0xFF1E1E1E),
            titleContentColor = Color.White,
            textContentColor = Color.White
        )
    }

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
                onSearchClick = onSearchClick,
                onNotificationClick = onNotificationClick,
                onRequestSongClick = onRequestSongClick
            )
        }

        // --- Layar 2: Tampilan Detail Playlist ---
        composable(
            route = HomeRoute.PLAYLIST_DETAIL,
            arguments = listOf(
                navArgument(HomeRoute.ARGS_ID) { type = NavType.StringType },
                navArgument(HomeRoute.ARGS_PLAYLIST_TYPE) { type = NavType.StringType; defaultValue = "AUTO" }
            )
        ) { backStackEntry ->
            // Extract Arguments
            val id = backStackEntry.arguments?.getString(HomeRoute.ARGS_ID) ?: ""
            val typeString = backStackEntry.arguments?.getString(HomeRoute.ARGS_PLAYLIST_TYPE) ?: "AUTO"
            
            val playlistType = try {
                PlaylistType.valueOf(typeString)
            } catch (e: Exception) {
                PlaylistType.AUTO
            }

            // Ambil state dari HomeViewModel dan tentukan Data + Title
            val homeState by homeViewModel.uiState.collectAsState()
            
            var playlistTitle = "Loading..."
            var playlistCoverUrl = ""
            var songs: List<SongWithArtist> = emptyList()

            when (val state = homeState) {
                is HomeUiState.Success -> {
                    when (playlistType) {
                        PlaylistType.ARTIST -> {
                            // For ARTIST type, DON'T pass any songs - let pagination handle it
                            songs = emptyList()
                            
                            // Get artist info from followedArtists list
                            val artist = state.followedArtists.find { it.id == id }
                            playlistTitle = artist?.name ?: "Unknown Artist"
                            playlistCoverUrl = artist?.photoUrl ?: ""
                        }
                        PlaylistType.AUTO -> {
                            // Map ID to Data
                            when (id) {
                                "baru_saja_ditambahkan" -> {
                                    playlistTitle = "Baru Saja Ditambahkan"
                                    songs = state.recentlyPlayed
                                }
                                "top_trending" -> {
                                    playlistTitle = "Top Trending"
                                    songs = state.topTrending
                                }
                                "most_loved" -> {
                                    playlistTitle = "Paling Banyak Disukai"
                                    songs = state.mostLoved
                                }
                                else -> {
                                    // Fallback / Unknown
                                    playlistTitle = "Playlist"
                                    songs = state.allSongsWithArtists.take(20)
                                }
                            }
                            playlistCoverUrl = songs.firstOrNull()?.song?.coverUrl ?: ""
                        }
                        PlaylistType.OFFICIAL -> {
                            // Sama seperti ARTIST, biarkan Viewmodel di PlaylistDetailScreen yang fetch lagu-lagunya
                            songs = emptyList()
                            val officialPlaylist = state.officialPlaylists.find { it.id == id }
                            playlistTitle = officialPlaylist?.title ?: "Official Playlist"
                            playlistCoverUrl = officialPlaylist?.coverUrl ?: ""
                        }
                        else -> {
                            // Handle other types later
                        }
                    }
                }
                else -> { /* Loading or Error */ }
            }

            // Panggil PlaylistDetailScreen
            PlaylistDetailScreen(
                songs = songs,
                playlistName = playlistTitle,
                playlistCoverUrl = playlistCoverUrl,
                playlistType = playlistType,
                playlistId = id, // Pass ID for pagination
                playMusicViewModel = playMusicViewModel
            )
        }
        composable("notification") {
            NotificationScreen(navController = homeNavController)
        }
        composable(
            route = "notification_detail/{notificationJson}",
            arguments = listOf(navArgument("notificationJson") { type = NavType.StringType })
        ) { backStackEntry ->
            val json = backStackEntry.arguments?.getString("notificationJson")
            val notif = remember(json) {
                try {
                    if (json != null) {
                        kotlinx.serialization.json.Json.decodeFromString<com.example.remusic.data.model.Notification>(android.net.Uri.decode(json))
                    } else null
                } catch (e: Exception) {
                    e.printStackTrace()
                    null
                }
            }

            if (notif != null) {
                NotificationDetailScreen(notification = notif, navController = homeNavController)
            } else {
                LaunchedEffect(Unit) {
                    homeNavController.popBackStack()
                }
            }
        }
        composable("request_song") {
            RequestSongScreen(navController = homeNavController, playMusicViewModel = playMusicViewModel)
        }
        composable(HomeRoute.CREATE_PLAYLIST) {
            CreatePlaylistScreen(
                onNavigateBack = { homeNavController.popBackStack() }
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
    onSearchClick: () -> Unit,
    onNotificationClick: () -> Unit,
    onRequestSongClick: () -> Unit,
    notificationViewModel: NotificationViewModel = viewModel()
) {
    val user = UserManager.currentUser
    val homeState by homeViewModel.uiState.collectAsState()
    val unreadCount by notificationViewModel.unreadCount.collectAsState()
    val greeting = GreetingUtils.getGreeting()
    val scrollState = rememberScrollState()
    
    // Get saved color from preferences
    val context = LocalContext.current
    val userPreferencesRepository = remember { UserPreferencesRepository(context) }
    val savedColor by userPreferencesRepository.lastSongColorFlow.collectAsState(initial = Color(0xFFB71C1C))
    
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
                                .matchParentSize()
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
                                unreadCount = unreadCount,
                                onSearchClick = onSearchClick,
                                onNotificationClick = { homeNavController.navigate("notification") },
                                onRequestSongClick = { homeNavController.navigate("request_song") },
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
                                .matchParentSize()
                                .background(
                                    brush = Brush.verticalGradient(
                                        colorStops = arrayOf(
                                            0.0f to lastSongColor,
                                            0.2f to lastSongColor.copy(alpha = 0.7f).compositeOver(Color.Black),
                                            0.5f to lastSongColor.copy(alpha = 0.4f).compositeOver(Color.Black),
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
                                unreadCount = unreadCount,
                                // Use callback to navigate
                                onSearchClick = onSearchClick,
                                onNotificationClick = { 
                                    homeNavController.navigate("notification") { launchSingleTop = true }
                                },
                                onRequestSongClick = { 
                                    homeNavController.navigate("request_song") { launchSingleTop = true }
                                },
                                dominantColor = lastSongColor
                            )

                            if (state.isRefreshing) {
                                QuickPickCarouselSkeleton()
                            } else {
                                // 1. Quick Picks (Pilihan Cepat)
                                QuickPickCarousel(
                                    songs = state.quickPickSongs.take(20),
                                    onSongClick = { index ->
                                        val song = state.quickPickSongs.getOrNull(index)
                                        if (song != null) {
                                            playMusicViewModel.playSongWithSmartQueue(song, "Pilihan Cepat")
                                        }
                                    },
                                    onMoreClick = { song ->
                                        playMusicViewModel.showQueueOptions(song)
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 2. Dari Komunitas
                    if (state.communityAlbums.isNotEmpty()) {
                        CommunityAlbumSection(
                            albums = state.communityAlbums,
                            onAlbumClick = { id ->
                                val type = state.communityAlbums.find { it.playlistId == id }?.playlistType ?: "YOUTUBE_PLAYLIST"
                                homeNavController.navigate(HomeRoute.createRoute(id = id, type = type))
                            },
                            onSongClick = { song, albumTitle ->
                                playMusicViewModel.playSongWithSmartQueue(song, albumTitle)
                            }
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                    }

                    // 3. Quick Mood (Chips)
                    val chips = state.homePage?.chips
                    if (!chips.isNullOrEmpty()) {
                        com.example.remusic.ui.components.homecomponents.QuickMoodChips(
                            chips = chips,
                            activeParams = state.activeMoodParams,
                            onChipClick = { chip ->
                                val chipParams = chip.endpoint?.params
                                if (chipParams == state.activeMoodParams) {
                                    // User clicked the active mood again, turn it off (reset to default)
                                    homeViewModel.fetchAllHomeData(null)
                                } else {
                                    // Set to new mood
                                    homeViewModel.fetchAllHomeData(chipParams)
                                }
                            }
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                    }

                    // 4. Baru Saja Diputar
                    if (state.recentlyPlayed.isNotEmpty()) {
                        com.example.remusic.ui.components.homecomponents.RecentlyPlayedGridPagerSection(
                            title = "Baru Saja Diputar",
                            songs = state.recentlyPlayed.take(50),
                            onSongClick = { song ->
                                playMusicViewModel.playSongWithSmartQueue(song, "Baru Saja Diputar")
                            }
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                    }

                    Spacer(modifier = Modifier.height(16.dp))



                    // 4. Baru Saja Diputar (Grid 3x3 Pager)
                    state.homePage?.sections?.forEach { section ->
                        val firstItem = section.items.firstOrNull()
                        when (firstItem) {
                            is AlbumItem, is PlaylistItem, is PodcastItem -> {
                                val playlists = section.items.mapNotNull { item ->
                                    when (item) {
                                        is AlbumItem -> FeaturedPlaylist(item.title, item.thumbnail, item.browseId, "YOUTUBE_ALBUM")
                                        is PlaylistItem -> FeaturedPlaylist(item.title, item.thumbnail ?: "", item.id, "YOUTUBE_PLAYLIST")
                                        is PodcastItem -> FeaturedPlaylist(item.title, item.thumbnail ?: "", item.id, "YOUTUBE_PLAYLIST")
                                        else -> null
                                    }
                                }
                                
                                if (section.title?.contains("Komunitas", ignoreCase = true) == true || section.title?.contains("Community", ignoreCase = true) == true) {
                                    // Skip rendering Community section here since it is already rendered manually above
                                } else {
                                    FeaturedPlaylistSection(
                                        title = section.title ?: "",
                                        playlists = playlists,
                                        onPlaylistClick = { playlist ->
                                            homeNavController.navigate(HomeRoute.createRoute(id = playlist.playlistId, type = playlist.playlistType))
                                        }
                                    )
                                }
                                Spacer(modifier = Modifier.height(24.dp))
                            }
                            is SongItem, is EpisodeItem -> {
                                val songs = section.items.mapNotNull { item ->
                                    when (item) {
                                        is SongItem -> {
                                            SongWithArtist(
                                                song = Song(
                                                    id = item.id,
                                                    title = item.title,
                                                    artistId = item.artists.firstOrNull()?.id,
                                                    coverUrl = item.thumbnail,
                                                    durationMs = (item.duration ?: 0) * 1000L
                                                ),
                                                artist = item.artists.firstOrNull()?.let { a ->
                                                    com.example.remusic.data.model.Artist(id = a.id ?: "", name = a.name)
                                                }
                                            )
                                        }
                                        else -> null
                                    }
                                }
                                if (songs.isNotEmpty()) {
                                    if (section.title?.contains("Serupa", ignoreCase = true) == true || section.title?.contains("Similar", ignoreCase = true) == true) {
                                        SimilarArtistSection(
                                            title = section.title ?: "",
                                            subtitle = "Lagu-lagu pilihan untukmu",
                                            songs = songs,
                                            onSongClick = { song -> playMusicViewModel.playSongWithSmartQueue(song, section.title ?: "") }
                                        )
                                    } else {
                                        SongSection(
                                            title = section.title ?: "",
                                            displayItems = songs,
                                            playMusicViewModel = playMusicViewModel,
                                            onSeeAllClick = {},
                                            onLongClick = { song -> playMusicViewModel.showQueueOptions(song) }
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(24.dp))
                                }
                            }
                            is ArtistItem -> {
                                val artists = section.items.filterIsInstance<ArtistItem>().map { item ->
                                    com.example.remusic.data.model.Artist(id = item.id, name = item.title, photoUrl = item.thumbnail ?: "")
                                }
                                if (artists.isNotEmpty()) {
                                    FollowedArtistsSection(
                                        title = section.title ?: "",
                                        artists = artists,
                                        onArtistClick = { artist ->
                                            homeNavController.navigate(HomeRoute.createRoute(id = artist.id, type = "ARTIST"))
                                        }
                                    )
                                    Spacer(modifier = Modifier.height(24.dp))
                                }
                            }
                            else -> { }
                        }
                    }

                    // 6. Artists You Follow (Local Data)
                    if (state.followedArtists.isNotEmpty()) {
                        FollowedArtistsSection(
                            artists = state.followedArtists,
                            onArtistClick = { artist ->
                                homeNavController.navigate(HomeRoute.createRoute(id = artist.id, type = "ARTIST"))
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
                        Color(0xFFE91E63).copy(alpha = 0.3f),
                        Color.Transparent
                    )
                )
            )
            .blur(75.dp)
    )
}
