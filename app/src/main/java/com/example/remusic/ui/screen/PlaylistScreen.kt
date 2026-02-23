package com.example.remusic.ui.screen

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import coil.compose.AsyncImage
import com.example.remusic.R
import com.example.remusic.data.UserManager
import com.example.remusic.navigation.PlaylistRoute
import com.example.remusic.ui.theme.AppFont
import com.example.remusic.viewmodel.playmusic.PlayMusicViewModel
import kotlinx.coroutines.delay

enum class ViewMode { LIST, GRID }
enum class FilterType { PLAYLIST, ARTIST }
enum class SortType { RECENT, A_Z, Z_A }
enum class PlaylistPrivacy { PUBLIC, FRIENDS, PRIVATE }

data class PlaylistItem(
    val id: String,
    val title: String,
    val songCount: Int,
    val imageUrl: String,
    val type: FilterType,
    val privacy: PlaylistPrivacy = PlaylistPrivacy.PRIVATE // Default
)

@Composable
fun PlaylistScreen(
    onCreatePlaylistClick: () -> Unit,
    playMusicViewModel: PlayMusicViewModel
) {
    val playlistNavController = rememberNavController()
    val playerUiState by playMusicViewModel.uiState.collectAsState()

    // --- Consume pending artist navigation from PlayMusic "Lihat Playlist" via SharedFlow ---
    LaunchedEffect(Unit) {
        playMusicViewModel.artistNavigationFlow.collect { artistId ->
            if (artistId != null && playerUiState.previousTab == "playlist") {
                playMusicViewModel.consumePendingArtistNavigation()
                playlistNavController.navigate(
                    PlaylistRoute.createRoute(id = artistId, type = "ARTIST")
                ) {
                    launchSingleTop = true
                }
            }
        }
    }

    NavHost(
        navController = playlistNavController,
        startDestination = PlaylistRoute.MAIN,
        modifier = Modifier.fillMaxSize()
    ) {
        composable(PlaylistRoute.MAIN) {
            PlaylistMainContent(onCreatePlaylistClick = onCreatePlaylistClick)
        }
        composable(
            route = PlaylistRoute.PLAYLIST_DETAIL,
            arguments = listOf(
                navArgument(PlaylistRoute.ARGS_ID) { type = NavType.StringType },
                navArgument(PlaylistRoute.ARGS_PLAYLIST_TYPE) { type = NavType.StringType; defaultValue = "AUTO" }
            )
        ) { backStackEntry ->
            val id = backStackEntry.arguments?.getString(PlaylistRoute.ARGS_ID) ?: ""
            val typeString = backStackEntry.arguments?.getString(PlaylistRoute.ARGS_PLAYLIST_TYPE) ?: "AUTO"
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
            com.example.remusic.ui.screen.RequestSongScreen(navController = playlistNavController, playMusicViewModel = playMusicViewModel)
        }
    }
}

@Composable
fun PlaylistMainContent(onCreatePlaylistClick: () -> Unit) {
    var isVisible by remember { mutableStateOf(false) }
    var viewMode by remember { mutableStateOf(ViewMode.LIST) }
    var filterType by remember { mutableStateOf<FilterType?>(null) } // Default null (All)
    var sortType by remember { mutableStateOf(SortType.RECENT) }

    val user = UserManager.currentUser

    // Dummy data with sample images
    val allItems = remember {
        listOf(
            // Playlists with square images
            PlaylistItem("p1", "Chill Vibes", 25, "https://picsum.photos/seed/playlist1/400", FilterType.PLAYLIST, PlaylistPrivacy.PUBLIC),
            PlaylistItem("p2", "Workout Mix", 30, "https://picsum.photos/seed/playlist2/400", FilterType.PLAYLIST, PlaylistPrivacy.PRIVATE),
            PlaylistItem("p3", "Study Focus", 18, "https://picsum.photos/seed/playlist3/400", FilterType.PLAYLIST, PlaylistPrivacy.FRIENDS),
            PlaylistItem("p4", "Party Hits", 42, "https://picsum.photos/seed/playlist4/400", FilterType.PLAYLIST, PlaylistPrivacy.PUBLIC),
            PlaylistItem("p5", "Road Trip", 35, "https://picsum.photos/seed/playlist5/400", FilterType.PLAYLIST, PlaylistPrivacy.FRIENDS),
            PlaylistItem("p6", "Sleep Sounds", 20, "https://picsum.photos/seed/playlist6/400", FilterType.PLAYLIST, PlaylistPrivacy.PRIVATE),
            
            // Artists with circular images (Privacy not applicable, default PRIVATE or ignore)
            PlaylistItem("a1", "Taylor Swift", 45, "https://i.pravatar.cc/400?img=1", FilterType.ARTIST),
            PlaylistItem("a2", "Ed Sheeran", 38, "https://i.pravatar.cc/400?img=2", FilterType.ARTIST),
            PlaylistItem("a3", "Billie Eilish", 32, "https://i.pravatar.cc/400?img=3", FilterType.ARTIST),
            PlaylistItem("a4", "The Weeknd", 50, "https://i.pravatar.cc/400?img=4", FilterType.ARTIST),
            PlaylistItem("a5", "Ariana Grande", 41, "https://i.pravatar.cc/400?img=5", FilterType.ARTIST),
            PlaylistItem("a6", "Drake", 55, "https://i.pravatar.cc/400?img=6", FilterType.ARTIST)
        )
    }

    // Filter and sort logic
    val displayItems = remember(filterType, sortType) {
        val filtered = if (filterType == null) allItems else allItems.filter { it.type == filterType }
        when (sortType) {
            SortType.RECENT -> filtered
            SortType.A_Z -> filtered.sortedBy { it.title }
            SortType.Z_A -> filtered.sortedByDescending { it.title }
        }
    }

    LaunchedEffect(Unit) {
        delay(100)
        isVisible = true
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black) // Set background to Black
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(50.dp))
        
        // Header with Profile Picture
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "Your Library",
                color = Color.White,
                fontSize = 28.sp,
                fontFamily = AppFont.Poppins,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { /* TODO: Search */ }) {
                Icon(Icons.Default.Search, contentDescription = "Search", tint = Color.White)
            }
            IconButton(onClick = { onCreatePlaylistClick() }) {
                Icon(Icons.Default.Add, contentDescription = "Add", tint = Color.White)
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Filter and View Controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = filterType == FilterType.PLAYLIST,
                    onClick = { filterType = if (filterType == FilterType.PLAYLIST) null else FilterType.PLAYLIST },
                    label = { Text("Playlists", fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color.White.copy(0.2f),
                        selectedLabelColor = Color.White,
                        containerColor = Color.White.copy(0.05f),
                        labelColor = Color.White.copy(0.6f)
                    )
                )
                FilterChip(
                    selected = filterType == FilterType.ARTIST,
                    onClick = { filterType = if (filterType == FilterType.ARTIST) null else FilterType.ARTIST },
                    label = { Text("Artists", fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color.White.copy(0.2f),
                        selectedLabelColor = Color.White,
                        containerColor = Color.White.copy(0.05f),
                        labelColor = Color.White.copy(0.6f)
                    )
                )
                FilterChip(
                    selected = sortType != SortType.RECENT,
                    onClick = { 
                        sortType = when(sortType) {
                            SortType.RECENT -> SortType.A_Z
                            SortType.A_Z -> SortType.Z_A
                            SortType.Z_A -> SortType.RECENT
                        }
                    },
                    label = { 
                        Text(
                            when(sortType) {
                                SortType.RECENT -> "Recent"
                                SortType.A_Z -> "A-Z"
                                SortType.Z_A -> "Z-A"
                            },
                            fontSize = 12.sp
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color.White.copy(0.2f),
                        selectedLabelColor = Color.White,
                        containerColor = Color.White.copy(0.05f),
                        labelColor = Color.White.copy(0.6f)
                    )
                )
            }
            
            IconButton(onClick = { 
                viewMode = if (viewMode == ViewMode.LIST) ViewMode.GRID else ViewMode.LIST 
            }) {
                Icon(
                    if (viewMode == ViewMode.LIST) Icons.Default.GridView else Icons.Default.ViewList,
                    contentDescription = "Toggle View",
                    tint = Color.White
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Content
        AnimatedVisibility(
            visible = isVisible,
            enter = fadeIn() + slideInVertically(initialOffsetY = { 50 })
        ) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(if (viewMode == ViewMode.GRID) 3 else 1),
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(5.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                // Liked Songs (always first)
                item {
                    if (viewMode == ViewMode.GRID) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                             OfflineMusicCard(viewMode)
                             LikedSongsCard()
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                             OfflineMusicCard(viewMode)
                             LikedSongsListItem()
                        }
                    }
                }
                
                // Display filtered and sorted items
                items(displayItems) { item ->
                    if (viewMode == ViewMode.GRID) {
                        if (item.type == FilterType.PLAYLIST) {
                            PlaylistGridItem(
                                title = item.title,
                                songCount = item.songCount,
                                imageUrl = item.imageUrl,
                                privacy = item.privacy
                            )
                        } else {
                            ArtistGridItem(
                                name = item.title,
                                songCount = item.songCount,
                                imageUrl = item.imageUrl
                            )
                        }
                    } else {
                        if (item.type == FilterType.PLAYLIST) {
                            PlaylistListItem(
                                title = item.title,
                                songCount = item.songCount,
                                imageUrl = item.imageUrl,
                                privacy = item.privacy
                            )
                        } else {
                            ArtistListItem(
                                name = item.title,
                                songCount = item.songCount,
                                imageUrl = item.imageUrl
                            )
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(120.dp).fillMaxWidth())
                }
            }
        }
    }
}

@Composable
fun LikedSongsCard() {
    Card(
        modifier = Modifier
            .height(180.dp) // Matched to PlaylistGridItem (Reduced from 200)
            .fillMaxWidth()
            .clickable { /* TODO */ },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
             Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                // Background/Icon
                 Box(
                    modifier = Modifier
                        .size(100.dp) // Matched to PlaylistGridItem (Reduced from 120)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF4A148C).copy(alpha = 0.6f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Favorite,
                        contentDescription = "Liked",
                        tint = Color.White,
                        modifier = Modifier.size(50.dp) // Adjusted size
                    )
                }
             }

            Column {
                Text(
                    text = "Liked Songs",
                    color = Color.White,
                    fontFamily = AppFont.Helvetica,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Text(
                    text = "120 songs",
                    color = Color.White.copy(alpha = 0.7f),
                    fontFamily = AppFont.Helvetica,
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
fun LikedSongsListItem() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(90.dp) // Matched (Reduced from 100)
            .clickable { /* TODO */ },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(70.dp) // Matched (Reduced from 80)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF4A148C).copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Favorite,
                    contentDescription = "Liked",
                    tint = Color.White,
                    modifier = Modifier.size(35.dp) // Adjusted size
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "Liked Songs",
                    color = Color.White,
                    fontFamily = AppFont.Helvetica,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Text(
                    text = "120 songs",
                    color = Color.White.copy(alpha = 0.7f),
                    fontFamily = AppFont.Helvetica,
                    fontSize = 12.sp
                )
            }
        }
    }
}

// PLAYLIST COMPONENTS (Square Images)
@Composable
fun PlaylistGridItem(
    title: String,
    songCount: Int,
    imageUrl: String,
    privacy: PlaylistPrivacy
) {
    Card(
        modifier = Modifier
            .height(180.dp) // Adjusted height (Reduced from 200)
            .fillMaxWidth()
            .clickable { /* TODO */ },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp), // Reduced padding to 4.dp
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = title,
                    modifier = Modifier
                        .size(100.dp) // Reduced to 100.dp (from 120)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop,
                    placeholder = painterResource(id = R.drawable.img_placeholder)
                )
                // Privacy Icon Overlay
                 Icon(
                    imageVector = when(privacy) {
                        PlaylistPrivacy.PUBLIC -> Icons.Default.Public
                        PlaylistPrivacy.FRIENDS -> Icons.Default.Group
                        PlaylistPrivacy.PRIVATE -> Icons.Default.Lock
                    },
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                        .size(16.dp)
                        .background(Color.Black.copy(alpha=0.5f), CircleShape)
                        .padding(2.dp)
                )
            }
            
            Column {
                Text(
                    text = title,
                    color = Color.White,
                    fontFamily = AppFont.Helvetica,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp, // Slightly bigger text
                    maxLines = 1
                )
                Text(
                    text = "$songCount songs",
                    color = Color.White.copy(alpha = 0.6f),
                    fontFamily = AppFont.Helvetica,
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
fun PlaylistListItem(
    title: String,
    songCount: Int,
    imageUrl: String,
    privacy: PlaylistPrivacy
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(90.dp) // Adjusted height (Reduced from 100)
            .clickable { /* TODO */ },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = title,
                    modifier = Modifier
                        .size(70.dp) // Reduced to 70.dp (from 80)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop,
                    placeholder = painterResource(id = R.drawable.img_placeholder)
                )
                 // Privacy Icon Overlay
                 Icon(
                    imageVector = when(privacy) {
                        PlaylistPrivacy.PUBLIC -> Icons.Default.Public
                        PlaylistPrivacy.FRIENDS -> Icons.Default.Group
                        PlaylistPrivacy.PRIVATE -> Icons.Default.Lock
                    },
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(2.dp)
                        .size(16.dp)
                        .background(Color.Black.copy(alpha=0.5f), CircleShape)
                        .padding(2.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                    color = Color.White,
                    fontFamily = AppFont.Helvetica,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    maxLines = 1
                )
                Text(
                    text = "$songCount songs",
                    color = Color.White.copy(alpha = 0.6f),
                    fontFamily = AppFont.Helvetica,
                    fontSize = 12.sp
                )
            }
        }
    }
}

// ARTIST COMPONENTS (Circular Images)
@Composable
fun ArtistGridItem(
    name: String,
    songCount: Int,
    imageUrl: String
) {
    Card(
        modifier = Modifier
            .height(180.dp) // Matched to PlaylistGridItem
            .fillMaxWidth()
            .clickable { /* TODO */ },
        shape = RoundedCornerShape(percent = 50), // Full Rounded
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally // Center content
        ) {
            AsyncImage(
                model = imageUrl,
                contentDescription = name,
                modifier = Modifier
                    .size(100.dp) // Matched to PlaylistGridItem
                    .clip(CircleShape),
                contentScale = ContentScale.Crop,
                placeholder = painterResource(id = R.drawable.img_placeholder)
            )
            
            Column {
                Text(
                    text = name,
                    color = Color.White,
                    fontFamily = AppFont.Helvetica,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    maxLines = 1
                )
                Text(
                    text = "$songCount songs",
                    color = Color.White.copy(alpha = 0.6f),
                    fontFamily = AppFont.Helvetica,
                    fontSize = 10.sp
                )
            }
        }
    }
}

@Composable
fun ArtistListItem(
    name: String,
    songCount: Int,
    imageUrl: String
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(90.dp) // Matched to PlaylistListItem (up from 80)
            .clickable { /* TODO */ },
        shape = RoundedCornerShape(percent = 50), // Full Rounded
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp), // Reduced padding
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = imageUrl,
                contentDescription = name,
                modifier = Modifier
                    .size(70.dp) // Matched to PlaylistListItem (up from 60)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop,
                placeholder = painterResource(id = R.drawable.img_placeholder)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = name,
                    color = Color.White,
                    fontFamily = AppFont.Helvetica,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    maxLines = 1
                )
                Text(
                    text = "$songCount songs",
                    color = Color.White.copy(alpha = 0.6f),
                    fontFamily = AppFont.Helvetica,
                    fontSize = 12.sp
                )
            }
        }
    }
}
@Composable
fun OfflineMusicCard(viewMode: ViewMode) {
    if (viewMode == ViewMode.GRID) {
        Card(
            modifier = Modifier
                .height(180.dp) // Matched to PlaylistGridItem (Reduced from 200)
                .fillMaxWidth()
                .clickable { /* TODO: Navigate to Offline Music */ },
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent) // No background
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                // Background Icon (faint)
                Icon(
                    imageVector = Icons.Filled.Download, // Pastikan icon ini ada atau ganti ICON LAIN
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.1f),
                    modifier = Modifier
                        .size(100.dp) // Matched size (Reduced from 120)
                        .padding(8.dp)
                )
                
                // Actual Content - but wait, the structure is box > icon + column.
                // Replicating PlaylistGridItem structure essentially:
                Column(
                     modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                         Icon(
                            imageVector = Icons.Filled.Download, // Placeholder icon
                            contentDescription = "Offline Music",
                            tint = Color.White,
                            modifier = Modifier.size(100.dp) // Big icon (Reduced from 120)
                        )
                    }

                    Column {
                         Text(
                            text = "Offline Music",
                            color = Color.White,
                            fontFamily = AppFont.Helvetica,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "240 songs", // Dummy count
                            color = Color.White.copy(alpha = 0.7f),
                            fontFamily = AppFont.Helvetica,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    } else {
        // List Item View
          Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(90.dp) // Matched PlaylistListItem (Reduced from 100)
                .clickable { /* TODO */ },
            shape = RoundedCornerShape(12.dp),
             colors = CardDefaults.cardColors(containerColor = Color.Transparent)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(70.dp) // Matched PlaylistListItem (Reduced from 80)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White.copy(0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Download,
                        contentDescription = "Offline Music",
                        tint = Color.White,
                        modifier = Modifier.size(35.dp) // Adjusted size
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Offline Music",
                        color = Color.White,
                        fontFamily = AppFont.Helvetica,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Text(
                        text = "240 songs",
                        color = Color.White.copy(alpha = 0.7f),
                        fontFamily = AppFont.Helvetica,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}
