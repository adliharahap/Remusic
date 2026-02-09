package com.example.remusic.ui.screen

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
import com.example.remusic.data.local.entity.CachedSong

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
    // Tambahkan parameter navigasi atau callback play jika perlu
    onSongClick: (com.example.remusic.data.model.SongWithArtist, String) -> Unit = { _, _ -> }
) {
    var isFullSearch by remember { mutableStateOf(false) }
    
    // Collect State from ViewModel
    val searchQuery by searchViewModel.searchQuery.collectAsState()
    val searchResults by searchViewModel.searchResults.collectAsState()
    val recentSongs by searchViewModel.recentSongs.collectAsState()
    val topArtist by searchViewModel.topArtist.collectAsState()
    val searchHistory by searchViewModel.searchHistory.collectAsState(initial = emptyList<CachedSong>())

    BackHandler(enabled = isFullSearch) {
        isFullSearch = false
        searchViewModel.onSearchQueryChanged("") // Clear query saat back
    }

    if (!isFullSearch) {
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
                        ?: "https://i.pinimg.com/736x/0c/86/83/0c86831120a35560280ce0e235fd7e57.jpg"
                    
                    HeaderSearchSection(
                        profileImageUrl = photoUrl,
                        title = "Pencarian",
                        onSearchClick = { isFullSearch = true }
                    )
                }
                
                // History Section (Real Data from Room)
                if (searchHistory.isNotEmpty()) {
                    item { 
                        Text(
                            text = "Riwayat Pencarian",
                            style = TextStyle(
                                fontSize = 20.sp,
                                fontFamily = AppFont.RobotoBold,
                                color = Color.White
                            ),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                    
                    itemsIndexed(searchHistory) { index, historyItem ->
                        val song = com.example.remusic.data.model.Song(
                            id = historyItem.id,
                            title = historyItem.title,
                            coverUrl = historyItem.coverUrl,
                            audioUrl = historyItem.telegramDirectUrl,
                            artistId = null, // CachedSong has artistId, maybe include it?
                            telegramFileId = historyItem.telegramFileId,
                            canvasUrl = historyItem.canvasUrl // Fix: Include Canvas URL
                        )
                        val artist = com.example.remusic.data.model.Artist(id = "", name = historyItem.artistName)

                        com.example.remusic.ui.components.QueueSongCard(
                            index = index,
                            songTitle = historyItem.title,
                            artistName = historyItem.artistName,
                            posterUri = historyItem.coverUrl ?: "",
                            isCurrentlyPlaying = false, // History item is not playing context
                            onClickListener = {
                                android.util.Log.d("SearchScreen", "CLICKED HISTORY: ${historyItem.title}")
                                onSongClick(com.example.remusic.data.model.SongWithArtist(song, artist), historyItem.title)
                            }
                        )
                    }
                }

                // Recently Added Section
                if (recentSongs.isNotEmpty()) {
                    item {
                        val mappedSongs = recentSongs.map { 
                            Song2(
                                id = it.song.id.hashCode(),
                                title = it.song.title,
                                artist = it.artist?.name ?: "Unknown",
                                imageUrl = it.song.coverUrl ?: ""
                            )
                        }
                        
                        RecentlyPlayedSection(
                            title = "Baru Saja Ditambahkan",
                            songs = mappedSongs,
                            onMoreOptionsClick = { song2 ->
                                val original = recentSongs.find { it.song.title == song2.title }
                                original?.let { 
                                    searchViewModel.onSongPlayed(it)
                                    onSongClick(it, "Recent Added") 
                                }
                            }
                        )
                    }
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
                        isFullSearch = false 
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
            
            // Hasil Pencarian
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                
                // --- TOP RESULT (ARTIST) ---
                if (topArtist != null) {
                    item {
                        Text(
                            text = "Top Result",
                            style = TextStyle(
                                fontSize = 18.sp,
                                fontFamily = AppFont.MontserratBold,
                                color = Color.White
                            ),
                            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp)
                        )
                        
                        // Artist Card
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                .background(Color(0xFF1E1E1E), androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                                .clickable { /* Handle Artist Click - Maybe show all songs */ }
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
                            Column {
                                Text(
                                    text = topArtist!!.name,
                                    style = TextStyle(
                                        fontSize = 20.sp,
                                        fontFamily = AppFont.MontserratBold,
                                        color = Color.White
                                    )
                                )
                                Text(
                                    text = "Artist",
                                    style = TextStyle(
                                        fontSize = 14.sp,
                                        fontFamily = AppFont.RobotoRegular,
                                        color = Color.Gray
                                    )
                                )
                            }
                        }
                        
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
                }

                itemsIndexed(searchResults) { index, song ->
                    com.example.remusic.ui.components.QueueSongCard(
                        index = index,
                        songTitle = song.song.title,
                        artistName = song.artist?.name ?: "Unknown Artist",
                        posterUri = song.song.coverUrl ?: "",
                        isCurrentlyPlaying = false,
                        onClickListener = {
                            android.util.Log.d("SearchScreen", "CLICKED RESULT: ${song.song.title}")
                            searchViewModel.onSongPlayed(song)
                            onSongClick(song, searchQuery)
                        }
                    )
                }
            }
        }
    }
}




