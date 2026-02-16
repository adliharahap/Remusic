package com.example.remusic.ui.screen

import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.AvTimer
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SortByAlpha
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
// import com.example.remusic.ui.theme.AppFont // Saya nonaktifkan ini agar preview jalan
import java.util.concurrent.TimeUnit
import com.example.remusic.R

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

// --- Mock Data HANYA UNTUK PREVIEW ---
private val mockArtists = listOf(
    Artist(id = "a1", name = "One Ok Rock", photoUrl = "..."),
    Artist(id = "a2", name = "Yoasobi", photoUrl = "..."),
    Artist(id = "a3", name = "Ado", photoUrl = "...")
)

private val mockSongs = listOf(
    SongWithArtist(
        song = Song(
            id = "s1",
            title = "The Beginning",
            artistId = "a1",
            coverUrl = "https://i.scdn.co/image/ab67616d0000b2737d6e65e63833d780e77ad6f6",
            durationMs = 295000,
            // Ubah jadi String ISO (Supabase Format)
            createdAt = "2025-01-01T10:00:00Z"
        ),
        artist = mockArtists[0]
    ),
    SongWithArtist(
        song = Song(
            id = "s2",
            title = "Yoru ni Kakeru",
            artistId = "a2",
            coverUrl = "https://i.scdn.co/image/ab67616d0000b27330c6a7a01b43b6de9d8a1835",
            durationMs = 261000,
            createdAt = "2025-01-02T12:00:00Z" // Lebih baru
        ),
        artist = mockArtists[1]
    ),
    SongWithArtist(
        song = Song(
            id = "s3",
            title = "Usseewa",
            artistId = "a3",
            coverUrl = "https://i.scdn.co/image/ab67616d0000b27341d3b2a30d977e5e3c50ea51",
            durationMs = 206000,
            createdAt = "2024-12-30T09:00:00Z" // Lebih lama
        ),
        artist = mockArtists[2]
    ),
    SongWithArtist(
        song = Song(
            id = "s4",
            title = "We Are",
            artistId = "a1",
            coverUrl = "https://i.scdn.co/image/ab67616d0000b2731802d28e7636e651f93e9b1f",
            durationMs = 250000,
            createdAt = "2023-01-01T10:00:00Z" // Paling lama
        ),
        artist = mockArtists[0]
    ),
    SongWithArtist(
        song = Song(
            id = "s5",
            title = "Idol",
            artistId = "a2",
            coverUrl = "https://i.scdn.co/image/ab67616d0000b27393c83e25d8a2455f46f32e65",
            durationMs = 213000,
            createdAt = "2025-01-03T15:00:00Z" // Paling baru
        ),
        artist = mockArtists[1]
    )
)


@Composable
fun PlaylistDetailScreen(
    songs: List<SongWithArtist>,
    playlistName: String,
    playlistCoverUrl: String, // Fallback jika songs empty
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
    
    // Update Gradient when songs change
    LaunchedEffect(songs) {
        if (songs.isNotEmpty()) {
            // Use the first song's cover for the gradient
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

    // --- Initial Scroll to Hide Search (Pull-to-Reveal Logic) ---
    // We want to start at Index 1 (Header), hiding Index 0 (Search)
    LaunchedEffect(Unit) {
        // Scroll to the Header item (index 1) with 0 offset (top of header)
        listState.scrollToItem(1, 0)
    }

    // --- Animation Logic ---
    
    // 1. Search Bar Scale Logic (Index 0)
    // It should grow from small to 90% as we scroll UP (pull down content).
    val searchScale by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val searchItem = layoutInfo.visibleItemsInfo.find { it.index == 0 }
            
            if (searchItem == null) {
                0f // Item not visible -> Scale 0
            } else {
                // Calculate visibility fraction based on offset
                // Search item is at the top.
                // If offset is 0 (fully visible top), scale is max.
                // If offset is negative (scrolled up/hidden), scale reduces.
                // However, wait. "Initial scroll to index 1". So index 0 is ABOVE.
                // When we scroll UP (pull down), offset of index 0 goes from negative (hidden) to 0 (visible).
                // Or rather, we are looking at offset relative to viewport start.
                val itemSize = searchItem.size
                val itemOffset = searchItem.offset // Distance from top of viewport.
                
                // If offset is 0, it's fully visible at top. Scale 1.
                // If offset is -itemSize, it's fully hidden above. Scale 0.
                
                val visibleFraction = 1f + (itemOffset.toFloat() / itemSize.toFloat())
                visibleFraction.coerceIn(0f, 1f)
            }
        }
    }

    // 2. Header Image Collapse Logic (Index 1)
    // Request: "Only image changes". Shrink/Fade as we scroll down.
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
    val totalDurationMs = remember(songs) {
        songs.sumOf { it.song.durationMs }
    }
    val formattedTotalDuration = remember(totalDurationMs) {
        formatDuration(totalDurationMs)
    }

    // --- Logika Sorting ---
    val sortedSongs = remember(songs, currentSort) {
        when (currentSort) {
            SortOrder.DEFAULT -> songs // Return original order
            SortOrder.NEWEST_FIRST -> songs.sortedByDescending { it.song.createdAt }
            SortOrder.OLDEST_FIRST -> songs.sortedBy { it.song.createdAt }
            SortOrder.TITLE_ASC -> songs.sortedBy { it.song.title }
            SortOrder.TITLE_DESC -> songs.sortedByDescending { it.song.title }
            SortOrder.ARTIST_ASC -> songs.sortedBy { it.artist?.name }
            SortOrder.ARTIST_DESC -> songs.sortedByDescending { it.artist?.name }
            SortOrder.DURATION_ASC -> songs.sortedBy { it.song.durationMs }
            SortOrder.DURATION_DESC -> songs.sortedByDescending { it.song.durationMs }
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

    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            
            // 1. ACTIVE SEARCH BAR (Full Screen Mode)
             AnimatedVisibility(
                visible = isSearching,
                enter = slideInVertically() + fadeIn(),
                exit = slideOutVertically() + fadeOut()
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
                                    isSearching = false
                                    searchQuery = "" 
                                },
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                        CustomOutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
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
                                        // Let's modify opacity and scale.
                                        scaleX = 0.5f + (searchScale * 0.5f) // 50% -> 100% (of 0.9f)
                                        scaleY = 0.5f + (searchScale * 0.5f)
                                        alpha = searchScale
                                    }
                                    .clip(RoundedCornerShape(20)) // Pill shape
                                    .background(Color.White.copy(alpha = 0.2f)) // Glassy
                                    .clickable { isSearching = true }
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
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
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
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 50.dp, bottom = 24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                // 1. Artwork (ANIMATED)
                                val imageUrl = if (songs.isNotEmpty()) songs[0].song.coverUrl else playlistCoverUrl
                                AsyncImage(
                                    model = imageUrl,
                                    contentDescription = "Playlist Poster",
                                    modifier = Modifier
                                        .fillMaxWidth(0.75f)
                                        .aspectRatio(1f)
                                        .graphicsLayer {
                                            // ANIMATION LOGIC: Shrink/Fade ONLY Image
                                            alpha = 1f - imageCollapseProgress
                                            val scale = 1f - (imageCollapseProgress * 0.5f) // Shrink to 50%
                                            scaleX = scale
                                            scaleY = scale
                                            translationY = -imageCollapseProgress * 100f
                                        }
                                        .shadow(elevation = 12.dp, shape = RoundedCornerShape(12.dp), clip = false)
                                        .clip(RoundedCornerShape(12.dp)),
                                    contentScale = ContentScale.Crop,
                                    placeholder = painterResource(id = R.drawable.img_placeholder),
                                    error = painterResource(id = R.drawable.img_placeholder)
                                )

                                Spacer(modifier = Modifier.height(24.dp))

                                // 2. Title & Meta (STATIC - No Animation)
                                Text(
                                    text = playlistName,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    fontFamily = AppFont.Poppins,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 28.sp,
                                    color = Color.White,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 24.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                
                                // Meta Data Row
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                     Text(
                                        text = "${songs.size} Lagu",
                                        fontFamily = AppFont.Poppins,
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 13.sp,
                                        color = Color.White.copy(0.7f)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Box(modifier = Modifier.size(4.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(0.5f)))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = formattedTotalDuration,
                                        fontFamily = AppFont.Poppins,
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 13.sp,
                                        color = Color.White.copy(0.7f)
                                    )
                                }

                                Spacer(modifier = Modifier.height(24.dp))

                                // 3. Action Buttons Row (STATIC - No Animation)
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    // Sort Button (Left)
                                     Box {
                                        IconButton(
                                            onClick = { showSortMenu = true },
                                            modifier = Modifier.background(Color.White.copy(0.1f), RoundedCornerShape(50)).size(42.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.FilterList, // Use Sort icon if available
                                                contentDescription = "Sort",
                                                tint = Color.White,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                         DropdownMenu(
                                            expanded = showSortMenu,
                                            onDismissRequest = { showSortMenu = false },
                                            modifier = Modifier.background(Color(0xFF282828))
                                        ) {
                                            SortOrder.entries.forEach { sortOption ->
                                                DropdownMenuItem(
                                                    text = { Text(sortOption.SdisplayName, color = Color.White, fontFamily = AppFont.Poppins) },
                                                    leadingIcon = {
                                                        Icon(
                                                            imageVector = sortOption.icon,
                                                            contentDescription = sortOption.SdisplayName,
                                                            tint = Color.White.copy(0.8f)
                                                        )
                                                    },
                                                    onClick = {
                                                        currentSort = sortOption
                                                        showSortMenu = false
                                                    }
                                                )
                                            }
                                        }
                                     }
                                     
                                     Spacer(Modifier.width(16.dp))

                                     Button(
                                         onClick = {
                                            if (filteredAndSortedSongs.isNotEmpty()) {
                                                playMusicViewModel?.setPlaylist(filteredAndSortedSongs, 0)
                                                playMusicViewModel?.playingMusicFromPlaylist(playlistName)
                                            }
                                         },
                                         modifier = Modifier
                                             .weight(1f)
                                             .height(52.dp),
                                         colors = ButtonDefaults.buttonColors(
                                             containerColor = Color.White,
                                             contentColor = Color.Black
                                         ),
                                         shape = RoundedCornerShape(50)
                                     ) {
                                         Icon(
                                             imageVector = Icons.Filled.PlayArrow,
                                             contentDescription = null,
                                             modifier = Modifier.size(28.dp)
                                         )
                                         Spacer(Modifier.width(8.dp))
                                         Text(
                                             "Play All",
                                             fontFamily = AppFont.Poppins,
                                             fontWeight = FontWeight.Bold,
                                             fontSize = 16.sp
                                         )
                                     }

                                     Spacer(Modifier.width(16.dp))

                                     // Shuffle Button (Right)
                                     IconButton(
                                        onClick = {
                                            playMusicViewModel?.setPlaylist(filteredAndSortedSongs, 0)
                                            playMusicViewModel?.playingMusicFromPlaylist(playlistName)
                                            if (uiState?.isShuffleModeEnabled == false) playMusicViewModel?.toggleShuffleMode()
                                        },
                                        modifier = Modifier.background(Color.White.copy(0.1f), RoundedCornerShape(50)).size(42.dp)
                                     ) {
                                        Icon(
                                            imageVector = Icons.Filled.Shuffle,
                                            contentDescription = "Shuffle",
                                            tint = if (uiState?.isShuffleModeEnabled == true) Color.Green else Color.White,
                                            modifier = Modifier.size(20.dp)
                                        )
                                     }
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


@Preview(showBackground = true)
@Composable
private fun PreviewPlaylistDetailScreen() {
    PlaylistDetailScreen(
        songs = mockSongs,
        playlistName = "Music Yang Disukai",
        playlistCoverUrl = "https://i.pinimg.com/474x/f7/7f/85/f77f8594da3b7c81e095df0b54ea9f86.jpg",
        playMusicViewModel = null // Beri null untuk preview
    )
}

