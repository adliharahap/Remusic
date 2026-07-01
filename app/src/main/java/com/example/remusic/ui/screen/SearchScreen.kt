package com.example.remusic.ui.screen

import com.example.remusic.viewmodel.playmusic.*
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.draw.clip
import com.example.remusic.ui.components.searchcomponents.HeaderSearchSection
import com.example.remusic.ui.components.searchcomponents.RecentlyPlayedSection
import com.example.remusic.ui.theme.AppFont
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.runtime.LaunchedEffect
import com.example.remusic.data.local.entity.CachedSong
import com.example.remusic.data.model.displayArtistName
import com.example.remusic.ui.components.*
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.NavType
import androidx.compose.foundation.shape.RoundedCornerShape

// Data class lagu
data class Song2(
    val id: Int,
    val title: String,
    val artist: String,
    val imageUrl: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    searchViewModel: com.example.remusic.viewmodel.searchviewmodel.SearchViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    playMusicViewModel: com.example.remusic.viewmodel.playmusic.PlayMusicViewModel,
    isSearchActive: Boolean = false,
    onSearchActiveChange: (Boolean) -> Unit = {}
) {
    // Create nested NavController for Search screen
    val searchNavController = rememberNavController()

    // --- Consume pending artist navigation from PlayMusic "Lihat Playlist" via SharedFlow ---
    val playerUiState by playMusicViewModel.uiState.collectAsState()
    LaunchedEffect(Unit) {
        playMusicViewModel.artistNavigationFlow.collect { artistId ->
            if (artistId != null && playerUiState.previousTab == "search") {
                playMusicViewModel.consumePendingArtistNavigation()
                searchNavController.navigate(
                    com.example.remusic.navigation.SearchRoute.createRoute(
                        id = artistId,
                        type = "ARTIST"
                    )
                ) {
                    launchSingleTop = true
                }
            }
        }
    }

    androidx.navigation.compose.NavHost(
        navController = searchNavController,
        startDestination = com.example.remusic.navigation.SearchRoute.MAIN,
        modifier = Modifier.fillMaxSize()
    ) {
        // Main Search Screen
        composable(com.example.remusic.navigation.SearchRoute.MAIN) {
            SearchMainScreen(
                searchViewModel = searchViewModel,
                playMusicViewModel = playMusicViewModel,
                isSearchActive = isSearchActive,
                onSearchActiveChange = onSearchActiveChange,
                onSongClick = { song, query ->
                    playMusicViewModel.playFromSearch(song, query)
                },
                onArtistClick = { artistId ->
                    // Navigate to local playlist detail
                    searchNavController.navigate(
                        com.example.remusic.navigation.SearchRoute.createRoute(
                            id = artistId,
                            type = "ARTIST"
                        )
                    )
                },
                onPlaylistClick = { playlist ->
                    val type = if (playlist.isOfficial) "OFFICIAL" else "USER_CREATED"
                    searchNavController.navigate(
                        com.example.remusic.navigation.SearchRoute.createRoute(
                            id = playlist.id,
                            type = type
                        )
                    )
                },
                onRequestSongClick = {
                    searchNavController.navigate("request_song") { launchSingleTop = true }
                },
                onUserClick = { userId ->
                    searchNavController.navigate(
                        com.example.remusic.navigation.SearchRoute.createRoute(
                            id = userId,
                            type = "UPLOADER"
                        )
                    )
                }
            )
        }

        // Playlist Detail Screen (nested in Search)
        composable(
            route = com.example.remusic.navigation.SearchRoute.PLAYLIST_DETAIL,
            arguments = listOf(
                navArgument(com.example.remusic.navigation.SearchRoute.ARGS_ID) { type = NavType.StringType },
                navArgument(com.example.remusic.navigation.SearchRoute.ARGS_PLAYLIST_TYPE) { 
                    type = NavType.StringType
                    defaultValue = "AUTO"
                }
            )
        ) { backStackEntry ->
            val id = backStackEntry.arguments?.getString(com.example.remusic.navigation.SearchRoute.ARGS_ID) ?: ""
            val typeString = backStackEntry.arguments?.getString(com.example.remusic.navigation.SearchRoute.ARGS_PLAYLIST_TYPE) ?: "AUTO"
            val type = PlaylistType.valueOf(typeString)

            PlaylistDetailScreen(
                songs = emptyList(), // Will be fetched by detail screen
                playlistName = "", // Will be fetched
                playlistCoverUrl = "", // Will be fetched
                playlistType = type,
                playlistId = id,
                playMusicViewModel = playMusicViewModel
            )
        }
        composable("request_song") {
            RequestSongScreen(navController = searchNavController, playMusicViewModel = playMusicViewModel)
        }
        composable(com.example.remusic.navigation.SearchRoute.CREATE_PLAYLIST) {
            CreatePlaylistScreen(
                onNavigateBack = { searchNavController.popBackStack() }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchMainScreen(
    searchViewModel: com.example.remusic.viewmodel.searchviewmodel.SearchViewModel,
    playMusicViewModel: com.example.remusic.viewmodel.playmusic.PlayMusicViewModel,
    isSearchActive: Boolean = false,
    onSearchActiveChange: (Boolean) -> Unit = {},
    onSongClick: (com.example.remusic.data.model.SongWithArtist, String) -> Unit = { _, _ -> },
    onArtistClick: (String) -> Unit = {},
    onPlaylistClick: (com.example.remusic.data.model.Playlist) -> Unit = {},
    onRequestSongClick: () -> Unit = {},
    onUserClick: (String) -> Unit = {}
) {
    // Local state for emptiness check only
    var showEmptyState by remember { mutableStateOf(false) }
    var isArtistExpanded by remember { mutableStateOf(false) }
    
    // Collect State from ViewModel
    val searchQuery by searchViewModel.searchQuery.collectAsState()
    val searchResults by searchViewModel.searchResults.collectAsState()
    val recentSongs by searchViewModel.recentSongs.collectAsState()
    val topArtist by searchViewModel.topArtist.collectAsState()
    val searchHistory by searchViewModel.searchHistory.collectAsState(initial = emptyList<CachedSong>())

    // New Advanced Search States
    val foundArtists by searchViewModel.foundArtists.collectAsState()
    val foundPlaylists by searchViewModel.foundPlaylists.collectAsState()
    val foundUsers by searchViewModel.foundUsers.collectAsState()

    // YouTube states
    val ytResults by searchViewModel.ytSearchResults.collectAsState()
    val ytFoundArtists by searchViewModel.ytFoundArtists.collectAsState()
    
    // Combined prioritized states
    val combinedSongs by searchViewModel.combinedSongs.collectAsState()
    val combinedVideos by searchViewModel.combinedVideos.collectAsState()
    val combinedArtists by searchViewModel.combinedArtists.collectAsState()
    
    val ytIsLoading by searchViewModel.ytIsLoading.collectAsState()
    val ytError by searchViewModel.ytError.collectAsState()
    val searchError by searchViewModel.searchError.collectAsState()
    val searchSuggestions by searchViewModel.searchSuggestions.collectAsState()

    // Delay showing empty state to give search time to load
    LaunchedEffect(searchQuery, combinedSongs, combinedArtists, foundPlaylists, foundUsers) {
        if (searchQuery.isNotEmpty() && combinedSongs.isEmpty() && combinedArtists.isEmpty() && foundPlaylists.isEmpty() && foundUsers.isEmpty()) {
            showEmptyState = false
            kotlinx.coroutines.delay(2500) // 2.5 second delay
            if (searchQuery.isNotEmpty() && combinedSongs.isEmpty() && combinedArtists.isEmpty() && foundPlaylists.isEmpty() && foundUsers.isEmpty()) {
                showEmptyState = true
            }
        } else {
            showEmptyState = false
        }
    }

    BackHandler(enabled = isSearchActive) {
        onSearchActiveChange(false)
        searchViewModel.onSearchQueryChanged("") // Clear query saat back
    }

    if (!isSearchActive) {
        // ... (Existing code for initial search screen)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF000000))
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
            ) {
                item {
                    Spacer(modifier = Modifier.height(30.dp))
                }
                item {
                    val photoUrl = com.example.remusic.data.UserManager.currentUser?.photoUrl
                        ?: "https://i.pinimg.com/736x/0c86831120a35560280ce0e235fd7e57.jpg"
                    
                    HeaderSearchSection(
                        profileImageUrl = photoUrl,
                        title = "Pencarian",
                        onSearchClick = { onSearchActiveChange(true) }
                    )
                }
                
                // Recently Added Section
                if (recentSongs.isNotEmpty()) {
                    item {
                        val mappedSongs = recentSongs.map { 
                            Song2(
                                id = it.song.id.hashCode(),
                                title = it.song.title,
                                artist = it.displayArtistName,
                                imageUrl = it.song.coverUrl ?: ""
                            )
                        }
                        
                        RecentlyPlayedSection(
                            title = "Baru Saja Ditambahkan",
                            songs = mappedSongs,
                            onSongClick = { song2 ->
                                val original = recentSongs.find { it.song.title == song2.title }
                                original?.let {
                                    searchViewModel.onSongPlayed(it)
                                    onSongClick(it, "Baru Saja Ditambahkan")
                                }
                            },
                            onMoreOptionsClick = { song2 ->
                                val original = recentSongs.find { it.song.title == song2.title }
                                original?.let { 
                                    playMusicViewModel.showQueueOptions(it)
                                }
                            }
                        )
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(150.dp))
                }
            }
        }
    } else {
        // Mode pencarian aktif (full search)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF121212))
        ) {
            Row(
                modifier = Modifier.fillMaxWidth()
                    .background(color = Color(0xFF2A2A2A))
                    .padding(top = 40.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { 
                        onSearchActiveChange(false)
                        searchViewModel.onSearchQueryChanged("")
                    }
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchViewModel.onSearchQueryChanged(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(end = 2.dp),
                    placeholder = { Text(
                        text = "Ketik lagu atau artist yang kamu suka",
                        fontSize = 15.sp,
                        fontFamily = AppFont.RobotoRegular,
                        color = Color.LightGray
                    ) },
                    singleLine = true,
                    textStyle = TextStyle(
                        fontSize = 16.sp,
                        fontFamily = AppFont.RobotoRegular,
                        color = Color.White
                    ),
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Search
                    ),
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchViewModel.onSearchQueryChanged("") }) {
                                Icon(
                                    imageVector = androidx.compose.material.icons.Icons.Default.Close,
                                    contentDescription = "Clear search",
                                    tint = Color.Gray
                                )
                            }
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        cursorColor = Color.White,
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        focusedPlaceholderColor = Color.Gray,
                        unfocusedPlaceholderColor = Color.Gray,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                    )
                )
            }
            
            // ── SEARCH SUGGESTIONS CHIPS ────────────────────────────────
            // Tampil hanya saat ada query dan suggestions tersedia
            if (searchQuery.isNotEmpty() && searchSuggestions.isNotEmpty()) {
                androidx.compose.foundation.lazy.LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(searchSuggestions.size) { idx ->
                        val suggestion = searchSuggestions[idx]
                        androidx.compose.foundation.layout.Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(Color.White.copy(alpha = 0.08f))
                                .clickable {
                                    searchViewModel.onSearchQueryChanged(suggestion)
                                }
                                .padding(horizontal = 14.dp, vertical = 7.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = null,
                                    tint = Color.Gray,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = suggestion,
                                    color = Color.White.copy(0.9f),
                                    fontSize = 13.sp,
                                    fontFamily = AppFont.RobotoRegular,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
            
            // Hasil Pencarian atau Riwayat
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                
                // Show search history when query is empty
                if (searchQuery.isEmpty() && searchHistory.isNotEmpty()) {
                    item { 
                        Text(
                            text = "Riwayat Pencarian",
                            style = TextStyle(
                                fontSize = 20.sp,
                                fontFamily = AppFont.RobotoBold,
                                color = Color.White
                            ),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)
                        )
                    }
                    
                    itemsIndexed(searchHistory) { index, historyItem ->
                        
                        // --- 🛑 DEBUG LOG START (BACA DARI DB) 🛑 ---
                        if (index == 0) { // Log item pertama saja biar ga spam
                             android.util.Log.e("DEBUG_DATA", "3. [LOAD] Membaca History Item ke-0: ${historyItem.title}")
                             android.util.Log.d("DEBUG_DATA", "   -> DB Artist ID: ${historyItem.artistId}")
                             android.util.Log.d("DEBUG_DATA", "   -> DB Artist Name: ${historyItem.artistName}")
                             android.util.Log.d("DEBUG_DATA", "   -> DB Featured List: ${historyItem.featuredArtists}")
                             android.util.Log.d("DEBUG_DATA", "   -> DB Featured Size: ${historyItem.featuredArtists.size}")
                        }
                        // --- 🛑 DEBUG LOG END 🛑 ---

                        val song = com.example.remusic.data.model.Song(
                            id = historyItem.id,
                            title = historyItem.title,
                            coverUrl = historyItem.coverUrl,
                            audioUrl = historyItem.telegramDirectUrl,
                            artistId = historyItem.artistId,
                            telegramFileId = historyItem.telegramFileId,
                            canvasUrl = historyItem.canvasUrl,
                            featuredArtists = historyItem.featuredArtists,
                            source = historyItem.source
                        )

                        // 2. Reconstruct Artist (Nama Primary)
                        val artist = com.example.remusic.data.model.Artist(
                            id = historyItem.artistId ?: "",
                            name = historyItem.artistName
                        )
                        // 3. Gabungkan
                        val songWithArtist = com.example.remusic.data.model.SongWithArtist(song, artist)
                        
                        // --- 🛑 DEBUG LOG HASIL AKHIR 🛑 ---
                        if (index == 0) {
                            android.util.Log.d("DEBUG_DATA", "4. [FINAL] Display Name UI: ${songWithArtist.displayArtistName}")
                            android.util.Log.e("DEBUG_DATA", "========================================")
                        }
                        // ------------------------------------

                        QueueSongCard(
                            index = index,
                            songTitle = historyItem.title,
                            artistName = songWithArtist.displayArtistName,
                            posterUri = historyItem.coverUrl ?: "",
                            isCurrentlyPlaying = false,
                            onClickListener = {
                                android.util.Log.d("SearchScreen", "CLICKED HISTORY: ${historyItem.title}")
                                searchViewModel.onSongPlayed(songWithArtist)
                                onSongClick(songWithArtist, historyItem.title)
                            },
                            onMoreClick = {
                                playMusicViewModel.showQueueOptions(songWithArtist)
                            }
                        )
                    }
                }
                
                // Empty State - No Results Found (with delay)
                if (showEmptyState) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 32.dp, vertical = 60.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Not Found",
                                tint = Color.Gray,
                                modifier = Modifier.size(120.dp)
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            Text(
                                text = "Lagu Tidak Ditemukan",
                                style = TextStyle(
                                    fontSize = 24.sp,
                                    fontFamily = AppFont.MontserratBold,
                                    color = Color.White
                                )
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Lagu yang kamu cari tidak ada atau belum ditambahkan. Jika lagu yang kamu inginkan tidak ditemukan, kamu bisa meminta request lagu untuk ditambahkan. Klik tombol di bawah untuk request lagu.",
                                style = TextStyle(
                                    fontSize = 14.sp,
                                    fontFamily = AppFont.RobotoRegular,
                                    color = Color.Gray,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                ),
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            androidx.compose.material3.Button(
                                onClick = onRequestSongClick,
                                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFB71C1C)
                                ),
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 32.dp)
                            ) {
                                Text(
                                    text = "Request Lagu",
                                    style = TextStyle(
                                        fontSize = 16.sp,
                                        fontFamily = AppFont.MontserratBold,
                                        color = Color.White
                                    ),
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                            }
                        }
                    }
                }

                // --- SEARCH RESULT SECTIONS ---
                if (searchQuery.isNotEmpty()) {
                    
                    // --- SEARCH RESULT HEADER ---
                    if (combinedSongs.isNotEmpty() || combinedArtists.isNotEmpty() || foundPlaylists.isNotEmpty() || foundUsers.isNotEmpty()) {
                         item {
                            Text(
                                text = "Hasil pencarian",
                                style = TextStyle(
                                    fontSize = 20.sp,
                                    fontFamily = AppFont.RobotoBold,
                                    color = Color.White
                                ),
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)
                            )
                        }
                    }

                    // --- TOP RESULT (ARTIST) - Large Card ---
                    if (topArtist != null) {
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                                    .background(Color(0xFF1E1E1E), androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                                    .clickable { onArtistClick(topArtist!!.id) }
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                coil.compose.AsyncImage(
                                    model = topArtist!!.photoUrl,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(80.dp)
                                        .clip(androidx.compose.foundation.shape.CircleShape),
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = topArtist!!.name,
                                        style = TextStyle(
                                            fontSize = 20.sp,
                                            fontFamily = AppFont.MontserratBold,
                                            color = Color.White
                                        )
                                    )
                                    Text(
                                        text = if (topArtist!!.source == "youtube") "Artist from Extended" else "Artist from Uploader",
                                        style = TextStyle(
                                            fontSize = 14.sp,
                                            fontFamily = AppFont.RobotoRegular,
                                            color = if (topArtist!!.source == "youtube") Color(0xFF90CAF9) else Color.Gray
                                        )
                                    )
                                }
                                if (topArtist!!.source == "youtube") {
                                    Icon(
                                        imageVector = androidx.compose.material.icons.Icons.Default.KeyboardArrowDown,
                                        contentDescription = "Show More",
                                        tint = Color.White,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        }
                    }

                    // --- ARTISTS LIST SECTION ---
                    if (combinedArtists.isNotEmpty()) {
                        val displayArtists = if (isArtistExpanded) combinedArtists else combinedArtists.take(2)
                        
                        item {
                            Text(
                                text = "Artists",
                                style = TextStyle(fontSize = 18.sp, fontFamily = AppFont.MontserratBold, color = Color.White),
                                modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 12.dp)
                            )
                        }
                        
                        itemsIndexed(displayArtists) { _, artist ->
                            if (artist.id != topArtist?.id || isArtistExpanded) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onArtistClick(artist.id) }
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    coil.compose.AsyncImage(
                                        model = artist.photoUrl,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(50.dp)
                                            .clip(androidx.compose.foundation.shape.CircleShape),
                                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                    )
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = artist.name,
                                            style = TextStyle(fontSize = 16.sp, fontFamily = AppFont.MontserratBold, color = Color.White)
                                        )
                                        Text(
                                            text = if (artist.source == "youtube") "Artist from Extended" else "Artist from Uploader",
                                            style = TextStyle(
                                                fontSize = 12.sp, 
                                                fontFamily = AppFont.RobotoRegular, 
                                                color = if (artist.source == "youtube") Color(0xFF90CAF9) else Color.Gray
                                            )
                                        )
                                    }
                                }
                            }
                        }
                        
                        if (combinedArtists.size > 1) {
                            item {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { isArtistExpanded = !isArtistExpanded }
                                        .padding(vertical = 12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = if (isArtistExpanded) "Show Less" else "Show More",
                                        color = Color.Gray,
                                        fontSize = 14.sp,
                                        fontFamily = AppFont.RobotoMedium
                                    )
                                    Icon(
                                        imageVector = if (isArtistExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                        contentDescription = null,
                                        tint = Color.Gray,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }

                    // --- USERS / UPLOADERS SECTION ---
                    if (foundUsers.isNotEmpty()) {
                        item {
                             Text(
                                text = "Profiles",
                                style = TextStyle(fontSize = 18.sp, fontFamily = AppFont.MontserratBold, color = Color.White),
                                modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp)
                            )
                        }
                        itemsIndexed(foundUsers) { _, user ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { 
                                        onUserClick(user.uid)
                                    }
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                 coil.compose.AsyncImage(
                                    model = user.photoUrl,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(50.dp)
                                        .clip(androidx.compose.foundation.shape.CircleShape),
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Text(
                                    text = user.displayName ?: "User",
                                    style = TextStyle(fontSize = 16.sp, fontFamily = AppFont.MontserratBold, color = Color.White)
                                )
                            }
                        }
                    }

                    // --- PLAYLISTS SECTION ---
                    if (foundPlaylists.isNotEmpty()) {
                        item {
                             Text(
                                text = "Playlists",
                                style = TextStyle(fontSize = 18.sp, fontFamily = AppFont.MontserratBold, color = Color.White),
                                modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp)
                            )
                        }
                        itemsIndexed(foundPlaylists) { _, playlist ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onPlaylistClick(playlist) }
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                 coil.compose.AsyncImage(
                                    model = playlist.coverUrl,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(50.dp)
                                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(4.dp)), // Playlist square
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                 Column {
                                    Text(
                                        text = playlist.title,
                                        style = TextStyle(fontSize = 16.sp, fontFamily = AppFont.MontserratBold, color = Color.White)
                                    )
                                    Text(
                                        text = "Playlist",
                                        style = TextStyle(fontSize = 12.sp, fontFamily = AppFont.RobotoRegular, color = Color.Gray)
                                    )
                                }
                            }
                        }
                    }

                    // --- SONGS SECTION ---
                    if (combinedSongs.isNotEmpty()) {
                         item {
                            Text(
                                text = "Songs",
                                style = TextStyle(
                                    fontSize = 18.sp,
                                    fontFamily = AppFont.MontserratBold,
                                    color = Color.White
                                ),
                                modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp)
                            )
                        }
                        
                        itemsIndexed(combinedSongs) { index, song ->
                            QueueSongCard(
                                index = index,
                                songTitle = song.song.title,
                                artistName = song.displayArtistName,
                                posterUri = song.song.coverUrl ?: "",
                                isCurrentlyPlaying = false,
                                sourceLabel = when(song.song.source) {
                                    "youtube" -> "Extended"
                                    "youtube_video" -> "Video"
                                    else -> "Uploader"
                                },
                                onClickListener = {
                                    searchViewModel.onSongPlayed(song)
                                    onSongClick(song, searchQuery)
                                },
                                onMoreClick = {
                                    playMusicViewModel.showQueueOptions(song)
                                }
                            )
                        }
                    }

                    // --- VIDEOS SECTION ---
                    if (combinedVideos.isNotEmpty()) {
                         item {
                            Text(
                                text = "Videos",
                                style = TextStyle(
                                    fontSize = 18.sp,
                                    fontFamily = AppFont.MontserratBold,
                                    color = Color.White
                                ),
                                modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp)
                            )
                        }
                        
                        itemsIndexed(combinedVideos) { index, song ->
                            QueueSongCard(
                                index = index,
                                songTitle = song.song.title,
                                artistName = song.displayArtistName,
                                posterUri = song.song.coverUrl ?: "",
                                isCurrentlyPlaying = false,
                                sourceLabel = "Video",
                                onClickListener = {
                                    searchViewModel.onSongPlayed(song)
                                    onSongClick(song, searchQuery)
                                },
                                onMoreClick = {
                                    playMusicViewModel.showQueueOptions(song)
                                }
                            )
                        }
                    }

                    // --- LOADING & ERROR (YT) ---
                    if (ytIsLoading) {
                        items(5) {
                            QueueSongCardSkeleton()
                        }
                    }

                    ytError?.let { err ->
                        item {
                            Text(
                                err,
                                color = Color(0xFFE53935),
                                fontSize = 14.sp,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                    }

                    searchError?.let { err ->
                        item {
                            Text(
                                err,
                                color = Color(0xFFE53935),
                                fontSize = 14.sp,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(150.dp))
                }
            }
        }
    }
}