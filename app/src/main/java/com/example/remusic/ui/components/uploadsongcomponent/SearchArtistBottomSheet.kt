package com.example.remusic.ui.components.uploadsongcomponent

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.remusic.ui.theme.AppFont
import com.example.remusic.viewmodel.Artist

// --- MODAL BOTTOM SHEET COMPOSABLE (DIPERBAIKI) ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchArtistBottomSheet(
    artists: List<Artist>,
    onDismiss: () -> Unit,
    onArtistSelected: (Artist) -> Unit,
    onAddNewArtistClick: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var searchQuery by remember { mutableStateOf("") }

    val recentlyAddedArtists = remember(artists) {
        artists.sortedByDescending { it.createdAt }
    }

    val filteredArtists = remember(searchQuery, artists) {
        if (searchQuery.isBlank()) {
            recentlyAddedArtists
        } else {
            artists.filter { it.name.contains(searchQuery, ignoreCase = true) }
        }
    }

    val isSearching = searchQuery.isNotBlank()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = Modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        dragHandle = null // ✅ 1. Hapus drag handle bawaan
    ) {
        // ✅ 2. Terapkan sudut membulat dan gradien pada Box ini
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)) // Bentuk sudut atas
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF1A0920), // Ungu gelap di atas
                            Color(0xFF111111)  // Hitam pekat di bawah
                        )
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp)
            ) {

                Spacer(modifier = Modifier.height(40.dp))
                // Judul
                Text(
                    text = "Search Artist",
                    style = TextStyle(
                        fontFamily = AppFont.RobotoBold,
                        fontSize = 24.sp,
                        color = Color.White
                    ),
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // Search TextField
                TextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text(
                            "Cari artist yang ingin kamu upload",
                            style = TextStyle(
                                fontFamily = AppFont.RobotoRegular,
                                color = Color.White.copy(alpha = 0.5f)
                            )
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search Icon",
                            tint = Color.White
                        )
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.White.copy(alpha = 0.1f),
                        unfocusedContainerColor = Color.White.copy(alpha = 0.1f),
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        cursorColor = MaterialTheme.colorScheme.primary,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    textStyle = TextStyle(
                        fontFamily = AppFont.RobotoMedium,
                        fontSize = 16.sp
                    ),
                    singleLine = true
                )

                Spacer(Modifier.height(24.dp))

                // Konten dinamis
                if (isSearching && filteredArtists.isEmpty()) {
                    ArtistNotFound(onAddNewArtistClick)
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        item {
                            Text(
                                text = if (isSearching) "Search Results" else "Recently Added",
                                style = TextStyle(
                                    fontFamily = AppFont.RobotoBold,
                                    fontSize = 16.sp,
                                    color = Color.White.copy(alpha = 0.8f)
                                ),
                                modifier = Modifier.padding(bottom = 12.dp)
                            )
                        }
                        val itemsToShow = if (isSearching) {
                            filteredArtists
                        } else {
                            filteredArtists.take(10)
                        }
                        items(itemsToShow, key = { it.id }) { artist ->
                            ArtistCard(
                                artist = artist,
                                onClick = { onArtistSelected(artist) }
                            )
                        }
                    }
                }
            }
        }
    }
}

// --- ARTIST CARD COMPOSABLE (TETAP SAMA) ---
@Composable
fun ArtistCard(artist: Artist, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(artist.photoUrl)
                .crossfade(true)
                .build(),
            contentDescription = "${artist.name} profile picture",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(Color.DarkGray)
        )
        Spacer(Modifier.width(16.dp))
        Text(
            text = artist.name,
            style = TextStyle(
                fontFamily = AppFont.RobotoMedium,
                fontSize = 17.sp,
                color = Color.White
            )
        )
    }
}

// --- COMPOSABLE NOT FOUND (TETAP SAMA) ---
@Composable
fun ArtistNotFound(onAddNewArtistClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = "Not Found",
            tint = Color.White.copy(alpha = 0.3f),
            modifier = Modifier.size(60.dp)
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Artis tidak ditemukan",
            style = TextStyle(
                fontFamily = AppFont.RobotoRegular,
                fontSize = 16.sp,
                color = Color.White.copy(alpha = 0.7f)
            )
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onAddNewArtistClick,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Add Icon",
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text("Tambahkan Artis Baru", fontWeight = FontWeight.Bold)
        }
    }
}
