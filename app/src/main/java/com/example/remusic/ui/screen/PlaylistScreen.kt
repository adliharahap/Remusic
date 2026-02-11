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
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.GridView
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
import coil.compose.AsyncImage
import com.example.remusic.R
import com.example.remusic.data.UserManager
import com.example.remusic.ui.theme.AppFont
import kotlinx.coroutines.delay

enum class ViewMode { LIST, GRID }
enum class FilterType { PLAYLIST, ARTIST }
enum class SortType { RECENT, A_Z, Z_A }

data class PlaylistItem(
    val id: String,
    val title: String,
    val songCount: Int,
    val imageUrl: String,
    val type: FilterType
)

@Composable
fun PlaylistScreen() {
    var isVisible by remember { mutableStateOf(false) }
    var viewMode by remember { mutableStateOf(ViewMode.LIST) }
    var filterType by remember { mutableStateOf(FilterType.PLAYLIST) }
    var sortType by remember { mutableStateOf(SortType.RECENT) }

    val user = UserManager.currentUser

    // Dummy data with sample images
    val allItems = remember {
        listOf(
            // Playlists with square images
            PlaylistItem("p1", "Chill Vibes", 25, "https://picsum.photos/seed/playlist1/400", FilterType.PLAYLIST),
            PlaylistItem("p2", "Workout Mix", 30, "https://picsum.photos/seed/playlist2/400", FilterType.PLAYLIST),
            PlaylistItem("p3", "Study Focus", 18, "https://picsum.photos/seed/playlist3/400", FilterType.PLAYLIST),
            PlaylistItem("p4", "Party Hits", 42, "https://picsum.photos/seed/playlist4/400", FilterType.PLAYLIST),
            PlaylistItem("p5", "Road Trip", 35, "https://picsum.photos/seed/playlist5/400", FilterType.PLAYLIST),
            PlaylistItem("p6", "Sleep Sounds", 20, "https://picsum.photos/seed/playlist6/400", FilterType.PLAYLIST),
            
            // Artists with circular images
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
        val filtered = allItems.filter { it.type == filterType }
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

    val verticalGradientBrush = Brush.verticalGradient(
        colorStops = arrayOf(
            0.0f to Color(0xFF755D8D),
            0.6f to Color.Black
        )
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(brush = verticalGradientBrush)
            .padding(horizontal = 16.dp)
            .padding(bottom = 80.dp)
    ) {
        Spacer(modifier = Modifier.height(40.dp))
        
        // Header with Profile Picture
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = user?.photoUrl,
                contentDescription = "Profile",
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .border(2.dp, Color.White.copy(0.3f), CircleShape),
                contentScale = ContentScale.Crop,
                placeholder = painterResource(id = R.drawable.img_placeholder),
                error = painterResource(id = R.drawable.img_placeholder)
            )
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
            IconButton(onClick = { /* TODO: Add */ }) {
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
                    onClick = { filterType = FilterType.PLAYLIST },
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
                    onClick = { filterType = FilterType.ARTIST },
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
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Liked Songs (always first)
                item {
                    if (viewMode == ViewMode.GRID) {
                        LikedSongsCard()
                    } else {
                        LikedSongsListItem()
                    }
                }
                
                // Display filtered and sorted items
                items(displayItems) { item ->
                    if (viewMode == ViewMode.GRID) {
                        if (item.type == FilterType.PLAYLIST) {
                            PlaylistGridItem(
                                title = item.title,
                                songCount = item.songCount,
                                imageUrl = item.imageUrl
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
                                imageUrl = item.imageUrl
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
            }
        }
    }
}

@Composable
fun LikedSongsCard() {
    Card(
        modifier = Modifier
            .height(120.dp)
            .fillMaxWidth()
            .clickable { /* TODO */ },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF4A148C).copy(alpha = 0.6f))
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Icon(
                Icons.Default.Favorite,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.1f),
                modifier = Modifier
                    .size(60.dp)
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                verticalArrangement = Arrangement.Bottom
            ) {
                Icon(
                    Icons.Default.Favorite,
                    contentDescription = "Liked",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Liked",
                    color = Color.White,
                    fontFamily = AppFont.Helvetica,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
                Text(
                    text = "120",
                    color = Color.White.copy(alpha = 0.7f),
                    fontFamily = AppFont.Helvetica,
                    fontSize = 10.sp
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
            .height(70.dp)
            .clickable { /* TODO */ },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF4A148C).copy(alpha = 0.6f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White.copy(0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Favorite,
                    contentDescription = "Liked",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
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
    imageUrl: String
) {
    Card(
        modifier = Modifier
            .height(120.dp)
            .fillMaxWidth()
            .clickable { /* TODO */ },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            AsyncImage(
                model = imageUrl,
                contentDescription = title,
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(6.dp)),
                contentScale = ContentScale.Crop,
                placeholder = painterResource(id = R.drawable.img_placeholder)
            )
            
            Column {
                Text(
                    text = title,
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
fun PlaylistListItem(
    title: String,
    songCount: Int,
    imageUrl: String
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(70.dp)
            .clickable { /* TODO */ },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = imageUrl,
                contentDescription = title,
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop,
                placeholder = painterResource(id = R.drawable.img_placeholder)
            )
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
            .height(120.dp)
            .fillMaxWidth()
            .clickable { /* TODO */ },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            AsyncImage(
                model = imageUrl,
                contentDescription = name,
                modifier = Modifier
                    .size(32.dp)
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
            .height(70.dp)
            .clickable { /* TODO */ },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = imageUrl,
                contentDescription = name,
                modifier = Modifier
                    .size(46.dp)
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
