package com.example.remusic.ui.screen

import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.AvTimer
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SortByAlpha
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
// import com.example.remusic.ui.theme.AppFont // Saya nonaktifkan ini agar preview jalan
import java.util.concurrent.TimeUnit

// --- Impor untuk data model Anda ---
import com.example.remusic.data.model.Artist
import com.example.remusic.data.model.Song
import com.example.remusic.data.model.SongWithArtist

// --- Impor untuk Composable Anda ---
import com.example.remusic.ui.components.QueueSongCard
import com.example.remusic.ui.components.atoms.CustomOutlinedTextField
import com.example.remusic.ui.theme.AppFont
import com.example.remusic.utils.extractGradientColorsFromImageUrl
// --- Impor ViewModel ---
import com.example.remusic.viewmodel.playmusic.PlayMusicViewModel


// --- Impor Custom Headers ---
import com.example.remusic.ui.components.playlist.AutoPlaylistHeader
import com.example.remusic.ui.components.playlist.ArtistPlaylistHeader
import com.example.remusic.ui.components.playlist.UserPlaylistHeader
import com.example.remusic.ui.components.playlist.OfficialPlaylistHeader
import com.example.remusic.ui.components.skeletons.PlaylistDetailSkeleton

// --- REVISI: Enum dengan Ikon ---
enum class SortOrder(val SdisplayName: String, val icon: ImageVector) {
    DEFAULT("Urutan Playlist", Icons.AutoMirrored.Filled.List), // Default option
    NEWEST_FIRST("Paling Baru", Icons.Default.NewReleases),
    OLDEST_FIRST("Paling Lama", Icons.Default.History),
    TITLE_ASC("Judul (A-Z)", Icons.Default.SortByAlpha),
    TITLE_DESC("Judul (Z-A)", Icons.Default.SortByAlpha), // Bisa dibalik jika ada ikon
    ARTIST_ASC("Artis (A-Z)", Icons.Default.Person),
    ARTIST_DESC("Artis (Z-A)", Icons.Default.Person),
    DURATION_ASC("Durasi (Pendek)", Icons.Default.AvTimer),
    DURATION_DESC("Durasi (Panjang)", Icons.Default.Timer)
}

// --- Playlist Type Enum ---
enum class PlaylistType {
    ARTIST,
    USER_CREATED,
    OFFICIAL,
    AUTO // Top Hits, Most Loved, etc.
}

@Composable
fun PlaylistDetailScreen(
    songs: List<SongWithArtist>,
    playlistName: String,
    playlistCoverUrl: String, // Fallback jika songs empty
    playlistType: PlaylistType = PlaylistType.AUTO, // Default type
    playlistId: String? = null, // New Param for ID-based fetching
    playMusicViewModel: PlayMusicViewModel? = null
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val density = LocalDensity.current

    // --- State Management ---
    val uiState = playMusicViewModel?.uiState?.collectAsState()?.value

    var currentSort by remember { mutableStateOf(SortOrder.DEFAULT) }
    var showSortMenu by remember { mutableStateOf(false) }

    var isSearching by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    // --- Dynamic Gradient State ---
    var headersColors by remember { mutableStateOf(listOf(Color(0xFF202020), Color(0xFF000000))) }

    // Update Gradient based on playlist type
    LaunchedEffect(playlistType, uiState?.artistDetails, songs) {
        when (playlistType) {
            PlaylistType.ARTIST -> {
                // For artist playlists, use artist photo
                val artistPhotoUrl = uiState?.artistDetails?.photoUrl ?: playlistCoverUrl
                if (artistPhotoUrl.isNotBlank()) {
                    headersColors = extractGradientColorsFromImageUrl(context, artistPhotoUrl)
                    Log.d("PlaylistDetailScreen", "Artist Headers Colors: $headersColors")
                }
            }
            else -> {
                // For other playlists, use first song's cover
                if (songs.isNotEmpty()) {
                    val coverUrl = songs[0].song.coverUrl
                    if (!coverUrl.isNullOrBlank()) {
                        headersColors = extractGradientColorsFromImageUrl(context, coverUrl)
                        Log.d("PlaylistDetailScreen", "Headers Colors: $headersColors")
                    }
                } else if (playlistCoverUrl.isNotBlank()) {
                    headersColors = extractGradientColorsFromImageUrl(context, playlistCoverUrl)
                    Log.d("PlaylistDetailScreen", "Headers Colors: $headersColors")
                }
            }
        }
    }

    // --- Pagination Logic (Artist) ---
    // For Artist playlists, always use paginated data from ViewModel
    val effectiveSongs = if (playlistType == PlaylistType.ARTIST) {
        uiState?.artistSongs ?: emptyList()
    } else {
        songs
    }

    // Initial Fetch for Artist
    LaunchedEffect(playlistType, playlistId) {
        if (playlistType == PlaylistType.ARTIST && !playlistId.isNullOrBlank()) {
             playMusicViewModel?.fetchArtistSongs(playlistId)
        }
    }

    // --- Loading State Logic ---
    // Show skeleton until BOTH artist details AND songs are loaded
    val isInitialLoading = remember(playlistType, uiState, effectiveSongs) {
        when (playlistType) {
            PlaylistType.ARTIST -> {
                 val isLoadingSongs = uiState?.isArtistSongsLoading == true
                 val isLoadingDetails = uiState?.isLoadingArtistDetails == true
                 val hasSongs = effectiveSongs.isNotEmpty()
                 val hasDetails = uiState?.artistDetails != null
                 
                 // Show skeleton ONLY if:
                 // - Currently loading AND data not ready yet
                 (isLoadingSongs || isLoadingDetails) && (!hasSongs || !hasDetails)
            }
            else -> false 
        }
    }

    // --- Animation Logic ---
    
    // 1. Search Bar Scale Logic (Index 0)
    val searchScale by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val searchItem = layoutInfo.visibleItemsInfo.find { it.index == 0 }
            
            if (searchItem == null) {
                0f // Item not visible -> Scale 0
            } else {
                val itemSize = searchItem.size
                val itemOffset = searchItem.offset // Distance from top of viewport.
                
                val visibleFraction = 1f + (itemOffset.toFloat() / itemSize.toFloat())
                visibleFraction.coerceIn(0f, 1f)
            }
        }
    }

    // 2. Header Image Collapse Logic (Index 1)
    val imageCollapseProgress by remember {
        derivedStateOf {
            val headerIndex = 1 // Header is now Index 1
            if (listState.firstVisibleItemIndex > headerIndex) {
                1f // Fully collapsed/gone
            } else if (listState.firstVisibleItemIndex < headerIndex) {
                0f // Fully expanded (we are at search item)
            } else {
                // We are at Header.
                val offset = listState.firstVisibleItemScrollOffset.toFloat()
                val maxOffset = with(density) { 300.dp.toPx() } // Threshold
                (offset / maxOffset).coerceIn(0f, 1f)
            }
        }
    }

    // --- Kalkulasi Total Durasi ---
    val totalDurationMs = remember(effectiveSongs) {
        effectiveSongs.sumOf { it.song.durationMs }
    }
    val formattedTotalDuration = remember(totalDurationMs) {
        formatDuration(totalDurationMs)
    }

    // --- Logika Sorting ---
    val sortedSongs = remember(effectiveSongs, currentSort) {
        when (currentSort) {
            SortOrder.DEFAULT -> effectiveSongs // Return original order
            SortOrder.NEWEST_FIRST -> effectiveSongs.sortedByDescending { it.song.createdAt }
            SortOrder.OLDEST_FIRST -> effectiveSongs.sortedBy { it.song.createdAt }
            SortOrder.TITLE_ASC -> effectiveSongs.sortedBy { it.song.title }
            SortOrder.TITLE_DESC -> effectiveSongs.sortedByDescending { it.song.title }
            SortOrder.ARTIST_ASC -> effectiveSongs.sortedBy { it.artist?.name }
            SortOrder.ARTIST_DESC -> effectiveSongs.sortedByDescending { it.artist?.name }
            SortOrder.DURATION_ASC -> effectiveSongs.sortedBy { it.song.durationMs }
            SortOrder.DURATION_DESC -> effectiveSongs.sortedByDescending { it.song.durationMs }
        }
    }

    // --- Logika Filtering ---
    val filteredAndSortedSongs = remember(sortedSongs, searchQuery) {
        if (searchQuery.isBlank()) {
            sortedSongs
        } else {
            sortedSongs.filter {
                it.song.title.contains(searchQuery, ignoreCase = true) ||
                        it.artist?.name?.contains(searchQuery, ignoreCase = true) == true
            }
        }
    }


    if (isInitialLoading) {
        PlaylistDetailSkeleton()
    } else {
        // --- REAL CONTENT ---
        PlaylistDetailContent(
            songs = songs,
            effectiveSongs = effectiveSongs,
            playlistName = playlistName,
            playlistCoverUrl = playlistCoverUrl,
            playlistType = playlistType,
            playlistId = playlistId,
            playMusicViewModel = playMusicViewModel,
            listState = listState,
            headersColors = headersColors,
            uiState = uiState,
            context = context,
            density = density,
            currentSort = currentSort,
            onSortChange = { currentSort = it },
            isSearching = isSearching,
            onSearchChange = { isSearching = it },
            searchQuery = searchQuery,
            onSearchQueryChange = { searchQuery = it },
            imageCollapseProgress = imageCollapseProgress,
            formattedTotalDuration = formattedTotalDuration,
            sortedSongs = sortedSongs,
            filteredAndSortedSongs = filteredAndSortedSongs,
            searchScale = searchScale,
            showSortMenu = showSortMenu,
            onShowSortMenuChange = { showSortMenu = it }
        )
    }
}

@Composable
fun PlaylistDetailContent(
    songs: List<SongWithArtist>,
    effectiveSongs: List<SongWithArtist>,
    playlistName: String,
    playlistCoverUrl: String,
    playlistType: PlaylistType,
    playlistId: String?,
    playMusicViewModel: PlayMusicViewModel?,
    listState: androidx.compose.foundation.lazy.LazyListState,
    headersColors: List<Color>,
    uiState: com.example.remusic.viewmodel.playmusic.PlayerUiState?,
    context: android.content.Context,
    density: androidx.compose.ui.unit.Density,
    currentSort: SortOrder,
    onSortChange: (SortOrder) -> Unit,
    isSearching: Boolean,
    onSearchChange: (Boolean) -> Unit,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    imageCollapseProgress: Float,
    formattedTotalDuration: String,
    sortedSongs: List<SongWithArtist>,
    filteredAndSortedSongs: List<SongWithArtist>,
    searchScale: Float,
    showSortMenu: Boolean,
    onShowSortMenuChange: (Boolean) -> Unit
) {

    // We want to start at Index 1 (Header), hiding Index 0 (Search)
    LaunchedEffect(Unit) {
        // Scroll to the Header item (index 1) with 0 offset (top of header)
        listState.scrollToItem(1, 0)
    }

    // --- Smooth Scroll to Header when Search Exits ---
    LaunchedEffect(isSearching) {
        if (!isSearching) {
            // User requested smooth scroll to top (Header - Index 1) but NOT Index 0 (Fake Search)
            // This resets the view to the header cleanly.
            listState.animateScrollToItem(index = 1, scrollOffset = 0)
        }
    }

    // --- SNAP LOGIC (Pull-to-Reveal 60%) ---
    LaunchedEffect(listState.isScrollInProgress) {
        if (!listState.isScrollInProgress && !isSearching) {
            // Scroll stopped. Check where we are.
            val firstVisibleIndex = listState.firstVisibleItemIndex
            val firstVisibleOffset = listState.firstVisibleItemScrollOffset
            
            // Item 0 is the Search Dummy
            if (firstVisibleIndex == 0) {
                // Calculate how much of Item 0 is visible.
                val searchItem = listState.layoutInfo.visibleItemsInfo.find { it.index == 0 }
                
                if (searchItem != null) {
                    val itemSize = searchItem.size
                    val hiddenPixels = firstVisibleOffset
                    val visiblePixels = itemSize - hiddenPixels
                    val visibleFraction = visiblePixels.toFloat() / itemSize.toFloat()
                    
                    if (visibleFraction >= 0.6f) {
                         if (firstVisibleOffset > 0) {
                               listState.animateScrollToItem(0)
                         }
                    } else {
                         listState.animateScrollToItem(1)
                    }
                }
            }
        }
    }

    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            
            // 1. ACTIVE SEARCH BAR (Full Screen Mode)
             AnimatedVisibility(
                visible = isSearching,
                enter = androidx.compose.animation.slideInVertically(
                    animationSpec = androidx.compose.animation.core.tween(500),
                    initialOffsetY = { -it }
                ) + fadeIn(
                    animationSpec = androidx.compose.animation.core.tween(500)
                ),
                //Exit animation should be instant (no animation)
                exit = androidx.compose.animation.fadeOut(animationSpec = androidx.compose.animation.core.snap(0))
            ) {
                 // Top Bar untuk Search Mode
                 Box(
                    modifier= Modifier
                        .background(Color.Black) // Background hitam pekat saat search
                        .fillMaxWidth()
                        .padding(top = 45.dp, bottom = 10.dp)
                        .zIndex(10f)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Start,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            modifier = Modifier
                                .padding(horizontal = 16.dp)
                                .clickable {
                                    onSearchChange(false)
                                    onSearchQueryChange("")
                                },
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                        CustomOutlinedTextField(
                            value = searchQuery,
                            onValueChange = { onSearchQueryChange(it) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(40.dp)
                                .padding(end = 16.dp)
                                .background(Color.White.copy(0.15f), shape = RoundedCornerShape(8.dp)),
                            placeholder = { Text(
                                text = "Cari lagu...",
                                fontSize = 14.sp,
                                fontFamily = AppFont.Poppins,
                                fontWeight = FontWeight.Normal,
                                color = Color.White.copy(0.6f)
                            ) },
                            singleLine = true,
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Filled.Search,
                                    contentDescription = "Search Icon",
                                    tint = Color.White.copy(0.8f),
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            textStyle = TextStyle(
                                fontSize = 16.sp,
                                fontFamily = AppFont.Poppins,
                                fontWeight = FontWeight.Normal,
                                color = Color.White
                            ),
                            keyboardOptions = KeyboardOptions(
                                imeAction = ImeAction.Search
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                cursorColor = Color.White,
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent,
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent
                            ),
                            shape = RoundedCornerShape(8.dp),
                        )
                    }
                }
            }


            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize()
            ) {
                // --- ITEM 0: SEARCH DUMMY (Pull-to-Reveal) ---
                item {
                    // Only show if NOT fully searching (to avoid duplication visually, though overlapping is fine)
                    if (!isSearching) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp) // Height of the area
                                .background(headersColors.firstOrNull() ?: Color.Black),
                            contentAlignment = Alignment.BottomCenter,
                        ) {
                             Row(
                                modifier = Modifier
                                    .fillMaxWidth(0.9f)
                                    .graphicsLayer {
                                        // ANIMATION: Grow from small to 90%
                                        // Our 'searchScale' goes 0 -> 1 based on reveal.
                                        scaleX = 0.5f + (searchScale * 0.5f) // 50% -> 100% (of 0.9f)
                                        scaleY = 0.5f + (searchScale * 0.5f)
                                        alpha = searchScale
                                    }
                                    .clip(RoundedCornerShape(20)) // Pill shape
                                    .background(Color.White.copy(alpha = 0.2f)) // Glassy
                                    .clickable { onSearchChange(true) }
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Search,
                                    contentDescription = null,
                                    tint = Color.White.copy(0.9f),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    text = "Find in playlist",
                                    color = Color.White.copy(0.9f),
                                    fontFamily = AppFont.Poppins,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }

                // --- ITEM 1: HEADER ---
                item {
                    AnimatedVisibility(
                        visible = !isSearching,
                        enter = androidx.compose.animation.slideInVertically(
                            animationSpec = androidx.compose.animation.core.tween(500),
                            initialOffsetY = { -it }
                        ) + androidx.compose.animation.expandVertically(
                            animationSpec = androidx.compose.animation.core.tween(500),
                            expandFrom = Alignment.Top
                        ) + fadeIn(
                            animationSpec = androidx.compose.animation.core.tween(500)
                        ),
                        exit = androidx.compose.animation.slideOutVertically(
                            animationSpec = androidx.compose.animation.core.tween(500),
                            targetOffsetY = { -it }
                        ) + androidx.compose.animation.shrinkVertically(
                            animationSpec = androidx.compose.animation.core.tween(500),
                            shrinkTowards = Alignment.Top
                        ) + fadeOut(
                            animationSpec = androidx.compose.animation.core.tween(500)
                        )
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    brush = Brush.verticalGradient(
                                        colors = headersColors + Color.Black // Flow into black
                                    )
                                )
                        ) {
                            when (playlistType) {
                                PlaylistType.AUTO -> {
                                    AutoPlaylistHeader(
                                        songs = songs,
                                        playlistName = playlistName,
                                        playlistCoverUrl = playlistCoverUrl,
                                        formattedTotalDuration = formattedTotalDuration,
                                        imageCollapseProgress = imageCollapseProgress,
                                        currentSort = currentSort,
                                        showSortMenu = showSortMenu,
                                        onSortMenuDismiss = { onShowSortMenuChange(false) },
                                        onSortOptionSelected = { sortOption ->
                                            onSortChange(sortOption)
                                            onShowSortMenuChange(false)
                                        },
                                        onSortClick = { onShowSortMenuChange(true) },
                                        playMusicViewModel = playMusicViewModel,
                                        uiState = uiState,
                                        sortedSongs = sortedSongs,
                                        filteredAndSortedSongs = filteredAndSortedSongs
                                    )
                                }
                                PlaylistType.ARTIST -> {
                                    ArtistPlaylistHeader(
                                        songs = songs,
                                        playlistName = playlistName,
                                        playlistCoverUrl = playlistCoverUrl,
                                        formattedTotalDuration = formattedTotalDuration,
                                        imageCollapseProgress = imageCollapseProgress,
                                        currentSort = currentSort,
                                        showSortMenu = showSortMenu,
                                        onSortMenuDismiss = { onShowSortMenuChange(false) },
                                        onSortOptionSelected = { sortOption ->
                                            onSortChange(sortOption)
                                            onShowSortMenuChange(false)
                                        },
                                        onSortClick = { onShowSortMenuChange(true) },
                                        playMusicViewModel = playMusicViewModel,
                                        uiState = uiState,
                                        sortedSongs = sortedSongs,
                                        filteredAndSortedSongs = filteredAndSortedSongs,
                                        artistId = playlistId // Pass the artist ID
                                    )
                                }
                                PlaylistType.USER_CREATED -> {
                                     UserPlaylistHeader(
                                        songs = songs,
                                        playlistName = playlistName,
                                        playlistCoverUrl = playlistCoverUrl,
                                        formattedTotalDuration = formattedTotalDuration,
                                        imageCollapseProgress = imageCollapseProgress,
                                        currentSort = currentSort,
                                        showSortMenu = showSortMenu,
                                        onSortMenuDismiss = { onShowSortMenuChange(false) },
                                        onSortOptionSelected = { sortOption ->
                                            onSortChange(sortOption)
                                            onShowSortMenuChange(false)
                                        },
                                        onSortClick = { onShowSortMenuChange(true) },
                                        playMusicViewModel = playMusicViewModel,
                                        uiState = uiState,
                                        sortedSongs = sortedSongs,
                                        filteredAndSortedSongs = filteredAndSortedSongs
                                    )
                                }
                                PlaylistType.OFFICIAL -> {
                                     OfficialPlaylistHeader(
                                        songs = songs,
                                        playlistName = playlistName,
                                        playlistCoverUrl = playlistCoverUrl,
                                        formattedTotalDuration = formattedTotalDuration,
                                        imageCollapseProgress = imageCollapseProgress,
                                        currentSort = currentSort,
                                        showSortMenu = showSortMenu,
                                        onSortMenuDismiss = { onShowSortMenuChange(false) },
                                        onSortOptionSelected = { sortOption ->
                                            onSortChange(sortOption)
                                            onShowSortMenuChange(false)
                                        },
                                        onSortClick = { onShowSortMenuChange(true) },
                                        playMusicViewModel = playMusicViewModel,
                                        uiState = uiState,
                                        sortedSongs = sortedSongs,
                                        filteredAndSortedSongs = filteredAndSortedSongs
                                    )
                                }
                            }
                        }
                    }
                }

                // --- Daftar Lagu (Items 2+) ---
                itemsIndexed(
                    items = filteredAndSortedSongs,
                    key = { _, item -> item.song.id }
                ) { index, songWithArtist ->
                    val isPlaying = uiState?.currentSong?.song?.id == songWithArtist.song.id

                    QueueSongCard(
                        index = index,
                        songTitle = songWithArtist.song.title,
                        artistName = songWithArtist.artist?.name ?: "Unknown Artist",
                        posterUri = songWithArtist.song.coverUrl ?: "",
                        isCurrentlyPlaying = isPlaying, // Use ViewModel state
                        modifier = Modifier.padding(horizontal = 0.dp, vertical = 4.dp),
                        onClickListener = { _ ->
                            // Use sortedSongs (full list) instead of filteredAndSortedSongs
                            val originalIndex = sortedSongs.indexOfFirst { it.song.id == songWithArtist.song.id }
                            if (originalIndex != -1) {
                                playMusicViewModel?.setPlaylist(
                                    songs = sortedSongs,
                                    startIndex = originalIndex
                                )
                                playMusicViewModel?.playingMusicFromPlaylist(playlistName)
                            }
                        },
                        onMoreClick = {
                             playMusicViewModel?.showQueueOptions(songWithArtist)
                        }
                    )
                }

                // --- Pagination Trigger (No Loading Indicator) ---
                item {
                    if (playlistType == PlaylistType.ARTIST && playlistId != null) {
                         val isEndReached = uiState?.artistSongsEndReached == true
                         
                         if (!isEndReached) {
                             // Trigger Load More when visible
                             LaunchedEffect(Unit) {
                                  playMusicViewModel?.loadMoreArtistSongs(playlistId)
                             }
                         }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(130.dp)) // Padding bawah cukup besar
                }
            }
        }
    }
}

// remove unused imports if any
private fun formatDuration(ms: Long): String {
    if (ms <= 0) return "0 dtk"

    val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(ms)
    val hours = TimeUnit.SECONDS.toHours(totalSeconds)
    val minutes = TimeUnit.SECONDS.toMinutes(totalSeconds) % 60
    val seconds = totalSeconds % 60

    return when {
        hours > 0 -> "${hours} jam ${minutes} mnt"
        minutes > 0 -> "${minutes} mnt ${seconds} dtk"
        else -> "${seconds} dtk"
    }
}