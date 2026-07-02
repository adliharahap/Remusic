package com.example.remusic.ui.screen

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.remusic.data.model.displayArtistName
import com.example.remusic.ui.components.QueueSongCard
import com.example.remusic.ui.theme.AppFont
import com.example.remusic.viewmodel.playmusic.PlayMusicViewModel
import com.example.remusic.viewmodel.playmusic.addSongToPlaylist
import com.example.remusic.viewmodel.searchviewmodel.SearchViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun AddSongToPlaylistScreen(
    playlistId: String,
    searchViewModel: SearchViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    playMusicViewModel: PlayMusicViewModel,
    navController: NavController
) {
    val searchQuery by searchViewModel.searchQuery.collectAsState()
    val combinedSongs by searchViewModel.combinedSongs.collectAsState()
    val combinedVideos by searchViewModel.combinedVideos.collectAsState()
    val ytIsLoading by searchViewModel.ytIsLoading.collectAsState()
    
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    var showEmptyState by remember { mutableStateOf(false) }

    LaunchedEffect(searchQuery, combinedSongs, combinedVideos) {
        if (searchQuery.isNotEmpty() && combinedSongs.isEmpty() && combinedVideos.isEmpty()) {
            showEmptyState = false
            delay(5000)
            if (searchQuery.isNotEmpty() && combinedSongs.isEmpty() && combinedVideos.isEmpty()) {
                showEmptyState = true
            }
        } else {
            showEmptyState = false
        }
    }

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier.fillMaxWidth()
                    .background(color = Color(0xFF2A2A2A))
                    .padding(top = 40.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { navController.popBackStack() }
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
                        .padding(end = 16.dp),
                    placeholder = { Text(
                        text = "Cari lagu untuk ditambah",
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
                                    imageVector = Icons.Default.Close,
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
        },
        containerColor = Color(0xFF0A0A0C)
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
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
                            trailingIcon = Icons.Default.Add,
                            onMoreClick = {
                                playMusicViewModel.addSongToPlaylist(playlistId, song.song.id, song) { success: Boolean, message: String ->
                                    coroutineScope.launch {
                                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        )
                    }
                }

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
                            trailingIcon = Icons.Default.Add,
                            onMoreClick = {
                                playMusicViewModel.addSongToPlaylist(playlistId, song.song.id, song) { success: Boolean, message: String ->
                                    coroutineScope.launch {
                                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        )
                    }
                }
            }

            if (ytIsLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color.White)
                }
            } else if (showEmptyState && searchQuery.isNotEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Tidak menemukan hasil untuk \"$searchQuery\"",
                        color = Color.Gray,
                        fontSize = 16.sp
                    )
                }
            } else if (searchQuery.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Cari lagu untuk ditambahkan",
                        color = Color.Gray,
                        fontSize = 16.sp
                    )
                }
            }
        }
    }
}
