package com.example.remusic.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AvTimer
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayCircle
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
// import com.example.remusic.ui.theme.AppFont // Saya nonaktifkan ini agar preview jalan
import com.google.firebase.Timestamp // Diperlukan untuk mock data di preview
import java.util.Date
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
// --- Impor ViewModel ---
import com.example.remusic.viewmodel.playmusic.PlayMusicViewModel


// --- REVISI: Enum dengan Ikon ---
enum class SortOrder(val SdisplayName: String, val icon: ImageVector) {
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
    val listState = rememberLazyListState()
    val density = LocalDensity.current

    LaunchedEffect(Unit) {
        val initialOffset = with(density) { 60.dp.toPx().toInt() }
        listState.scrollToItem(0, initialOffset)
    }

    // --- State Management untuk UI ---
    var currentSort by remember { mutableStateOf(SortOrder.NEWEST_FIRST) }
    var showSortMenu by remember { mutableStateOf(false) }
    var currentPlayingIndex by remember { mutableIntStateOf(-1) }

    var isSearching by remember { mutableStateOf(false)}
    var searchQuery by remember { mutableStateOf("") }


    // --- REVISI: Kalkulasi Total Durasi ---
    val totalDurationMs = remember(songs) {
        songs.sumOf { it.song.durationMs }
    }
    val formattedTotalDuration = remember(totalDurationMs) {
        formatDuration(totalDurationMs)
    }

    // --- Logika Sorting ---
    val sortedSongs = remember(songs, currentSort) {
        when (currentSort) {
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
    ){
        val headerGradient = Brush.verticalGradient(
            colors = listOf(Color.Blue.copy(0.3f), Color(0x000000)) // Gradient bebas
        )
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize()
        ) {
            // --- Header ---
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 40.dp)
                        .background(brush = headerGradient),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Spacer(modifier = Modifier.height(80.dp).fillMaxWidth())
                    // REVISI: Ambil gambar dari lagu pertama
                    val imageUrl = if (songs.isNotEmpty()) {
                        songs[0].song.coverUrl
                    } else {
                        playlistCoverUrl // Fallback
                    }
                    AsyncImage(
                        model = imageUrl,
                        contentDescription = "Playlist Poster",
                        modifier = Modifier
                            .fillMaxWidth(0.6f)
                            .aspectRatio(1f)
                            .shadow(elevation = 6.dp, shape = RoundedCornerShape(16.dp), clip = false)
                            .clip(RoundedCornerShape(10.dp)),
                        contentScale = ContentScale.Crop,
                        placeholder = painterResource(id = R.drawable.img_placeholder),
                        error = painterResource(id = R.drawable.img_placeholder)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = playlistName,
                        maxLines = 1,
                        // fontFamily = AppFont.RobotoBold,
                        fontWeight = FontWeight.Bold,
                        fontSize = 26.sp,
                        color = Color.White
                    )
                    // REVISI: Text jumlah lagu dihapus dari sini

                    Spacer(modifier = Modifier.height(16.dp)) // Beri jarak lebih
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        horizontalArrangement = Arrangement.Absolute.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // --- REVISI: Ganti Tombol Edit dengan Info Durasi ---
                            Column(horizontalAlignment = Alignment.Start) {
                                Text(
                                    text = "${songs.size} lagu",
                                    color = Color.White.copy(0.8f),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                    // fontFamily = AppFont.RobotoBold,
                                )
                                Text(
                                    text = formattedTotalDuration,
                                    color = Color.White.copy(0.8f),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                    // fontFamily = AppFont.RobotoBold,
                                )
                            }
                            // --- AKHIR REVISI ---

                            Spacer(modifier = Modifier.width(16.dp)) // Beri jarak

                            // --- Tombol Urutkan dengan Dropdown ---
                            Box {
                                Button(
                                    onClick = { showSortMenu = true },
                                    shape = RoundedCornerShape(16.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color.White.copy(0.2f),
                                        contentColor = Color.White
                                    )
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.FilterList,
                                        contentDescription = "Sort by",
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = currentSort.SdisplayName,
                                        maxLines = 1,
                                        // fontFamily = AppFont.RobotoBold,
                                        fontSize = 14.sp,
                                    )
                                }

                                // --- REVISI: Menu Dropdown dengan Ikon ---
                                DropdownMenu(
                                    expanded = showSortMenu,
                                    onDismissRequest = { showSortMenu = false },
                                    modifier = Modifier.background(Color(0xFF282828))
                                ) {
                                    SortOrder.entries.forEach { sortOption ->
                                        DropdownMenuItem(
                                            text = { Text(sortOption.SdisplayName, color = Color.White) },
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
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { /* TODO: Panggil onShuffleClick */ }) {
                                Icon(
                                    imageVector = Icons.Filled.Shuffle,
                                    contentDescription = "shuffle",
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            IconButton(onClick = { /* TODO: Panggil onPlayAllClick */ }) {
                                Icon(
                                    imageVector = Icons.Filled.PlayCircle,
                                    contentDescription = "Play Music",
                                    tint = Color.White,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                        }
                    }
                }
            }

            // --- Daftar Lagu (UI Baru) ---
            itemsIndexed(
                items = filteredAndSortedSongs,
                key = { _, item -> item.song.id }
            ) { index, songWithArtist ->
                QueueSongCard(
                    index = index + 1,
                    songTitle = songWithArtist.song.title,
                    artistName = songWithArtist.artist?.name ?: "Unknown Artist",
                    posterUri = songWithArtist.song.coverUrl ?: "",
                    isCurrentlyPlaying = (currentPlayingIndex == index),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    // --- REVISI: Hubungkan ke ViewModel ---
                    onClickListener = {
                        // 'it' adalah index (0-based) dari QueueSongCard
                        currentPlayingIndex = it

                        // Panggil ViewModel dengan daftar yang *sedang ditampilkan*
                        playMusicViewModel?.setPlaylist(
                            songs = filteredAndSortedSongs,
                            startIndex = it
                        )
                        playMusicViewModel?.playingMusicFromPlaylist(playlistName)
                    },
                )
            }

            // Spacer di akhir list
            item {
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
        val showSearch = listState.firstVisibleItemScrollOffset < 30
        val alpha by animateFloatAsState(
            targetValue = if (showSearch) 1f else 0f,
            label = ""
        )
        val offsetY by animateDpAsState(
            targetValue = if (showSearch) 0.dp else (-30).dp,
            label = ""
        )
        Row(
            modifier = Modifier
                .padding(top = 50.dp)
                .fillMaxWidth(0.9f)
                .align(Alignment.TopCenter)
                .offset(y = offsetY)
                .graphicsLayer { this.alpha = alpha }
                .zIndex(20f)
                .clip(RoundedCornerShape(percent = 20))
                .background(Color.White.copy(alpha = 0.25f))
                .padding(horizontal = 14.dp, vertical = 8.dp)
                .clickable{
                    isSearching = true
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = null,
                tint = Color.White
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Cari Lagu di Playlist",
                color = Color.White,
                fontFamily = AppFont.PoppinsRegular,
                fontSize = 12.sp
            )
        }

        if (isSearching) {
            AnimatedVisibility(
                visible = isSearching,
                enter = fadeIn(animationSpec = tween(durationMillis = 300)),   // fade masuk
                exit = fadeOut(animationSpec = tween(durationMillis = 300)),    // fade keluar
            ) {
                val topBarGradient = Brush.verticalGradient(
                    colors = listOf(Color.Blue, Color.Black) // Gradient bebas
                )
                Box(
                    modifier= Modifier
                        .background(Color.DarkGray)
                        .fillMaxWidth()
                        .height(100.dp)
                        .padding(top = 45.dp)
                        .zIndex(10f)
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.Start,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            modifier = if (!isSearching) {
                                Modifier.padding(horizontal = 20.dp)
                            } else {
                                Modifier.padding(start = 20.dp, end = 8.dp)
                            },
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                        if (isSearching) {
                            CustomOutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(35.dp)
                                    .padding(end = 10.dp,)
                                    .background(Color.White.copy(0.15f), shape = RoundedCornerShape(8.dp)),
                                placeholder = { Text(
                                    text = "Cari lagu yang ada di playlist",
                                    fontSize = 15.sp,
                                    fontFamily = AppFont.RobotoRegular,
                                    color = Color.White.copy(0.8f)
                                ) },
                                singleLine = true,
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Filled.Search,
                                        contentDescription = "Search Icon",
                                        tint = Color.White,
                                        modifier = Modifier.size(22.dp)
                                    )
                                },
                                textStyle = TextStyle(
                                    fontSize = 16.sp,
                                    fontFamily = AppFont.RobotoRegular,
                                    color = Color.White
                                ),
                                keyboardOptions = KeyboardOptions(
                                    // 5. Mengubah tombol 'Enter' di keyboard menjadi ikon 'Search'
                                    imeAction = ImeAction.Search
                                ),
                                // 4. Gunakan OutlinedTextFieldDefaults untuk OutlinedTextField
                                colors = OutlinedTextFieldDefaults.colors(
                                    cursorColor = Color.White,
                                    focusedBorderColor = Color.Transparent,
                                    unfocusedBorderColor = Color.Transparent,
                                    focusedPlaceholderColor = Color.Gray,
                                    unfocusedPlaceholderColor = Color.Gray,
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                ),
                                shape = RoundedCornerShape(8.dp),
                            )
                        }else {
                            Text(
                                text = "Music Yang Disukai",
                                color = Color.White,
                                fontFamily = AppFont.PoppinsBold,
                                fontSize = 16.sp,
                            )
                        }
                    }
                }
            }
        }
    }
}


// --- Fungsi Helper Baru untuk Durasi ---
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

