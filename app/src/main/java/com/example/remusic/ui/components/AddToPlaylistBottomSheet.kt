package com.example.remusic.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.remusic.data.model.Playlist
import com.example.remusic.ui.theme.AppFont

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToPlaylistBottomSheet(
    sheetState: SheetState,
    onDismissRequest: () -> Unit,
    playlists: List<Playlist>,
    isLoading: Boolean,
    onPlaylistSelected: (Playlist) -> Unit,
    onCreateNewPlaylistClick: () -> Unit
) {
    var searchQuery by remember { mutableStateOf(TextFieldValue("")) }

    val filteredPlaylists = remember(searchQuery.text, playlists) {
        if (searchQuery.text.isBlank()) {
            playlists
        } else {
            playlists.filter { it.title.contains(searchQuery.text, ignoreCase = true) }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = Color(0xFF1E1E1E),
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Add to playlist",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontFamily = AppFont.Poppins,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )

                IconButton(onClick = onDismissRequest) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color.White
                    )
                }
            }

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                placeholder = { Text("Find a playlist", color = Color.Gray) },
                leadingIcon = {
                    Icon(imageVector = Icons.Default.Search, contentDescription = "Search", tint = Color.Gray)
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    focusedContainerColor = Color(0xFF2A2A2A),
                    unfocusedContainerColor = Color(0xFF2A2A2A),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = Color.White
                ),
                shape = RoundedCornerShape(8.dp),
                singleLine = true
            )

            // Create New Playlist Button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onCreateNewPlaylistClick() }
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(Color(0xFF2A2A2A), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "New Playlist",
                        tint = Color.White
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = "New playlist",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontFamily = AppFont.Poppins,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Playlists List
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color.White)
                }
            } else if (filteredPlaylists.isEmpty() && searchQuery.text.isNotBlank()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                    Text(
                        text = "No playlists found for \"${searchQuery.text}\"",
                        color = Color.Gray,
                        modifier = Modifier.padding(top = 32.dp),
                        fontFamily = AppFont.Poppins
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(filteredPlaylists) { playlist ->
                        PlaylistSelectionItem(
                            playlist = playlist,
                            onClick = { onPlaylistSelected(playlist) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PlaylistSelectionItem(
    playlist: Playlist,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (!playlist.coverUrl.isNullOrBlank()) {
            AsyncImage(
                model = playlist.coverUrl,
                contentDescription = "Playlist Cover",
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(Color(0xFF2A2A2A), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = androidx.compose.material.icons.Icons.Default.LibraryMusic,
                    contentDescription = "Default Cover",
                    tint = Color.Gray
                )
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = playlist.title,
                color = Color.White,
                fontSize = 16.sp,
                fontFamily = AppFont.Poppins,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val privacy = playlist.visibility?.takeIf { it.isNotBlank() } ?: "Public"
            Text(
                text = privacy.replaceFirstChar { it.uppercase() },
                color = Color.Gray,
                fontSize = 13.sp,
                fontFamily = AppFont.Helvetica,
                maxLines = 1
            )
        }
    }
}
