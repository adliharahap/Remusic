package com.example.remusic.ui.screen.offline

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import android.widget.Toast
import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.remusic.data.model.SongWithArtist
import com.example.remusic.data.preferences.UserPreferencesRepository
import com.example.remusic.ui.components.OfflineSongCard
import com.example.remusic.ui.components.atoms.CustomOutlinedTextField
import com.example.remusic.ui.components.offline.OfflinePlaylistHeader
import com.example.remusic.ui.components.offline.OfflineSongOptionsBottomSheet
import com.example.remusic.ui.theme.AppFont
import com.example.remusic.viewmodel.offline.OfflineMusicViewModel
import com.example.remusic.viewmodel.offline.OfflineSortOrder
import com.example.remusic.viewmodel.offline.OfflinePlaylistUiState
import com.example.remusic.viewmodel.playmusic.PlayMusicViewModel
import com.example.remusic.viewmodel.playmusic.PlayerUiState
import kotlinx.coroutines.launch

@Composable
fun OfflinePlaylistScreen(
    playMusicViewModel: PlayMusicViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { UserPreferencesRepository(context) }
    val offlineMusicViewModel: OfflineMusicViewModel = viewModel(
        factory = OfflineMusicViewModel.Factory(prefs)
    )
    val listState = rememberLazyListState()
    val density = LocalDensity.current

    val uiState by offlineMusicViewModel.uiState.collectAsState()
    val playerUiState by playMusicViewModel.uiState.collectAsState()

    var isSearching by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var showSortMenu by remember { mutableStateOf(false) }
    var selectedSong by remember { mutableStateOf<SongWithArtist?>(null) }

    var isFirstComposition by rememberSaveable { mutableStateOf(true) }

    // Launcher for Android 11+ Scoped Storage delete request
    val deleteLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            offlineMusicViewModel.deleteExtraFilesAndRefresh(context)
            Toast.makeText(context, "Lagu berhasil dihapus", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Penghapusan dibatalkan", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(Unit) {
        offlineMusicViewModel.loadOfflineMusic(context)
        if (isFirstComposition) {
            // Start at item 1 (header), hiding item 0 (search dummy) ONLY on first load
            listState.scrollToItem(1, 0)
            isFirstComposition = false
        }
    }

    // Whether the list has enough content to scroll (used as guard for all snap/animation logic)
    val canScroll by remember { derivedStateOf { listState.canScrollForward || listState.canScrollBackward } }

    // 1. Search Bar Scale (only when scrollable — avoids layoutInfo reads when list is short)
    val searchScale by remember {
        derivedStateOf {
            if (!listState.canScrollBackward && !listState.canScrollForward) return@derivedStateOf 0f
            val searchItem = listState.layoutInfo.visibleItemsInfo.find { it.index == 0 }
            if (searchItem == null) 0f
            else {
                val visibleFraction = 1f + (searchItem.offset.toFloat() / searchItem.size.toFloat())
                visibleFraction.coerceIn(0f, 1f)
            }
        }
    }

    // 2. Header Image Collapse (only when scrollable)
    val imageCollapseProgress by remember {
        derivedStateOf {
            if (!listState.canScrollBackward && !listState.canScrollForward) return@derivedStateOf 0f
            val headerIndex = 1
            when {
                listState.firstVisibleItemIndex > headerIndex -> 1f
                listState.firstVisibleItemIndex < headerIndex -> 0f
                else -> {
                    val offset = listState.firstVisibleItemScrollOffset.toFloat()
                    val maxOffset = with(density) { 300.dp.toPx() }
                    (offset / maxOffset).coerceIn(0f, 1f)
                }
            }
        }
    }

    // --- Search filter only — ViewModel already handles sort+filter ---
    val displayedSongs by remember {
        derivedStateOf {
            val songs = uiState.songs
            if (searchQuery.isBlank()) songs
            else songs.filter {
                it.song.title.contains(searchQuery, ignoreCase = true) ||
                it.artist?.name?.contains(searchQuery, ignoreCase = true) == true
            }
        }
    }

    OfflinePlaylistContent(
        uiState = uiState,
        playerUiState = playerUiState,
        listState = listState,
        canScroll = canScroll,
        isSearching = isSearching,
        onSearchChange = { isSearching = it },
        searchQuery = searchQuery,
        onSearchQueryChange = { searchQuery = it },
        showSortMenu = showSortMenu,
        onShowSortMenuChange = { showSortMenu = it },
        searchScale = searchScale,
        imageCollapseProgress = imageCollapseProgress,
        displayedSongs = displayedSongs,
        offlineMusicViewModel = offlineMusicViewModel,
        playMusicViewModel = playMusicViewModel,
        onMoreClick = { selectedSong = it },
        onBack = onBack
    )

    // ── Song Options Bottom Sheet ──
    selectedSong?.let { song ->
        OfflineSongOptionsBottomSheet(
            song = song,
            onDismiss = { selectedSong = null },
            onAddToQueue = { 
                val message = playMusicViewModel.addToQueue(it) 
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            },
            onPlayNext   = { 
                if (playerUiState.currentSong == null) {
                    Toast.makeText(context, "Putar lagu lain terlebih dahulu", Toast.LENGTH_SHORT).show()
                } else if (playerUiState.currentSong?.song?.id == it.song.id) {
                     Toast.makeText(context, "Lagu ini sedang diputar", Toast.LENGTH_SHORT).show()
                } else {
                    playMusicViewModel.playNext(it)
                    Toast.makeText(context, "${it.song.title} akan diputar setelah ini", Toast.LENGTH_SHORT).show()
                }
            },
            onDeleteFromDevice = {
                offlineMusicViewModel.requestDeleteSong(context, it) { intentSender ->
                    if (intentSender != null) {
                        deleteLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
                    } else {
                        // Deletion completed either on API 29 or failed
                        offlineMusicViewModel.loadOfflineMusic(context)
                    }
                }
                selectedSong = null
            }
        )
    }
}

@Composable
fun OfflinePlaylistContent(
    uiState: OfflinePlaylistUiState,
    playerUiState: PlayerUiState,
    listState: androidx.compose.foundation.lazy.LazyListState,
    canScroll: Boolean,
    isSearching: Boolean,
    onSearchChange: (Boolean) -> Unit,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    showSortMenu: Boolean,
    onShowSortMenuChange: (Boolean) -> Unit,
    searchScale: Float,
    imageCollapseProgress: Float,
    displayedSongs: List<SongWithArtist>,
    offlineMusicViewModel: OfflineMusicViewModel,
    playMusicViewModel: PlayMusicViewModel,
    onMoreClick: (SongWithArtist) -> Unit,
    onBack: () -> Unit
) {
    val accentBlue = Color(0xFF1E88E5)
    val bgBlack = Color(0xFF0A0A0A)

    // --- Smooth Scroll to Header when Search Exits ---
    LaunchedEffect(isSearching) {
        if (!isSearching) {
            listState.animateScrollToItem(index = 1, scrollOffset = 0)
        }
    }

    // --- Snap Logic (ONLY when list is actually scrollable) ---
    LaunchedEffect(listState.isScrollInProgress) {
        // Guard: don't snap if list cannot scroll — prevents infinite loop with few songs
        if (!canScroll) return@LaunchedEffect
        if (!listState.isScrollInProgress && !isSearching) {
            if (listState.firstVisibleItemIndex == 0) {
                val searchItem = listState.layoutInfo.visibleItemsInfo.find { it.index == 0 }
                if (searchItem != null) {
                    val visibleFraction = (searchItem.size - listState.firstVisibleItemScrollOffset.toFloat()) / searchItem.size.toFloat()
                    if (visibleFraction >= 0.6f) {
                        listState.animateScrollToItem(0)
                    } else {
                        listState.animateScrollToItem(1)
                    }
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(bgBlack)) {
        Column(modifier = Modifier.fillMaxSize()) {
            
            // 1. ACTIVE SEARCH BAR OVERLAY
            AnimatedVisibility(
                visible = isSearching,
                enter = slideInVertically(tween(500), initialOffsetY = { -it }) + fadeIn(tween(500)),
                exit = fadeOut(snap(0))
            ) {
                Box(
                    modifier = Modifier
                        .background(Color.Black)
                        .fillMaxWidth()
                        .padding(top = 45.dp, bottom = 10.dp)
                        .zIndex(10f)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Start,
                        verticalAlignment = Alignment.CenterVertically
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
                                .background(Color.White.copy(0.15f), RoundedCornerShape(8.dp)),
                            placeholder = {
                                Text(
                                    "Cari lagu...",
                                    fontSize = 14.sp,
                                    fontFamily = AppFont.Poppins,
                                    fontWeight = FontWeight.Normal,
                                    color = Color.White.copy(0.6f)
                                )
                            },
                            singleLine = true,
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Filled.Search,
                                    contentDescription = null,
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
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            colors = OutlinedTextFieldDefaults.colors(
                                cursorColor = Color.White,
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent,
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent
                            ),
                            shape = RoundedCornerShape(8.dp)
                        )
                    }
                }
            }

            // 2. MAIN LAZY COLUMN
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                
                // Only show item 0 (search dummy) if the list is scrollable
                // — prevents the layoutInfo loop bug when there are few songs
                if (canScroll) {
                    item {
                        if (!isSearching) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(120.dp)
                                    .background(Color(0xFF0D47A1)),
                                contentAlignment = Alignment.BottomCenter
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth(0.9f)
                                        .graphicsLayer {
                                            scaleX = 0.5f + (searchScale * 0.5f)
                                            scaleY = 0.5f + (searchScale * 0.5f)
                                            alpha = searchScale
                                        }
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(Color.White.copy(0.2f))
                                        .clickable { onSearchChange(true) }
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Filled.Search, null,
                                        tint = Color.White.copy(0.9f),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Text(
                                        "Cari di Musik Offline",
                                        color = Color.White.copy(0.9f),
                                        fontFamily = AppFont.Poppins,
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        }
                    }
                }

                // --- ITEM 1: HEADER (COMPONENIZED) ---
                item {
                    AnimatedVisibility(
                        visible = !isSearching,
                        enter = slideInVertically(tween(500), initialOffsetY = { -it })
                              + expandVertically(tween(500), expandFrom = Alignment.Top)
                              + fadeIn(tween(500)),
                        exit = slideOutVertically(tween(500), targetOffsetY = { -it })
                              + shrinkVertically(tween(500), shrinkTowards = Alignment.Top)
                              + fadeOut(tween(500))
                    ) {
                        OfflinePlaylistHeader(
                            songs = uiState.songs,
                            playMusicViewModel = playMusicViewModel,
                            imageCollapseProgress = imageCollapseProgress
                        )
                    }
                }

                // --- ITEM 2: SORT + FILTER TOOLBAR ---
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(bgBlack)
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Sort Button
                        Box {
                            OutlinedButton(
                                onClick = { onShowSortMenuChange(true) },
                                shape = RoundedCornerShape(10.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(0.2f)),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                modifier = Modifier.height(36.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Sort, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(uiState.sortOrder.label, fontSize = 12.sp, fontFamily = AppFont.Helvetica, maxLines = 1)
                            }
                            DropdownMenu(
                                expanded = showSortMenu,
                                onDismissRequest = { onShowSortMenuChange(false) },
                                modifier = Modifier.background(Color(0xFF1E1E1E))
                            ) {
                                OfflineSortOrder.entries.forEach { order ->
                                    val isActive = uiState.sortOrder == order
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                order.label,
                                                color = if (isActive) accentBlue else Color.White,
                                                fontFamily = AppFont.Helvetica,
                                                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                                                fontSize = 14.sp
                                            )
                                        },
                                        leadingIcon = {
                                            if (isActive) Icon(Icons.Default.Check, null, tint = accentBlue, modifier = Modifier.size(16.dp))
                                        },
                                        onClick = {
                                            offlineMusicViewModel.setSortOrder(order)
                                            onShowSortMenuChange(false)
                                        }
                                    )
                                }
                            }
                        }

                        // Filter Chip
                        val chipBg by animateColorAsState(
                            if (uiState.showOnlyRemusic) accentBlue else Color(0xFF1C1C1C), tween(250), label = "chipBg"
                        )
                        val chipBorder by animateColorAsState(
                            if (uiState.showOnlyRemusic) accentBlue else Color.White.copy(0.2f), tween(250), label = "chipBorder"
                        )
                        Row(
                            modifier = Modifier
                                .height(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(chipBg)
                                .border(1.dp, chipBorder, RoundedCornerShape(10.dp))
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) { offlineMusicViewModel.toggleShowOnlyRemusic(!uiState.showOnlyRemusic) }
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Outlined.FilterList, null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                if (uiState.showOnlyRemusic) "Remusic saja" else "Semua lagu offline",
                                color = Color.White, fontSize = 12.sp,
                                fontFamily = AppFont.Helvetica,
                                fontWeight = if (uiState.showOnlyRemusic) FontWeight.Bold else FontWeight.Normal
                            )
                        }

                        Spacer(Modifier.weight(1f))

                        Text("${displayedSongs.size} lagu", color = Color.White.copy(0.45f),
                            fontFamily = AppFont.Helvetica, fontSize = 12.sp)
                    }
                    HorizontalDivider(thickness = 0.5.dp, color = Color.White.copy(0.08f))
                }

                // --- EMPTY STATE OR SONG LIST ---
                if (displayedSongs.isEmpty() && !uiState.isLoading) {
                    item {
                        Column(
                            modifier = Modifier.fillMaxWidth().height(260.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Default.MusicNote, null, tint = Color.White.copy(0.2f), modifier = Modifier.size(64.dp))
                            Spacer(Modifier.height(16.dp))
                            Text(
                                text = when {
                                    searchQuery.isNotBlank() -> "Tidak ada lagu yang cocok"
                                    uiState.showOnlyRemusic  -> "Belum ada unduhan dari Remusic\nCoba matikan filter \"Remusic saja\""
                                    else                     -> "Belum ada musik tersimpan di HP"
                                },
                                color = Color.White.copy(0.4f),
                                fontFamily = AppFont.Helvetica,
                                fontSize = 14.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 40.dp)
                            )
                        }
                    }
                } else {
                    itemsIndexed(
                        items = displayedSongs,
                        key = { _, item -> item.song.id }
                    ) { index, songWithArtist ->
                        val isPlaying = playerUiState.currentSong?.song?.id == songWithArtist.song.id
                        OfflineSongCard(
                            index = index,
                            songTitle = songWithArtist.song.title,
                            artistName = songWithArtist.artist?.name ?: "Unknown Artist",
                            durationMs = songWithArtist.song.durationMs,
                            posterUri = songWithArtist.song.coverUrl,
                            isCurrentlyPlaying = isPlaying,
                            modifier = Modifier.padding(vertical = 2.dp),
                            onClickListener = {
                                val originalIndex = uiState.songs.indexOfFirst { it.song.id == songWithArtist.song.id }
                                if (originalIndex != -1) {
                                    playMusicViewModel.playingMusicFromPlaylist("Offline Music")
                                    playMusicViewModel.setPlaylist(uiState.songs, originalIndex)
                                }
                            },
                            onMoreClick = { onMoreClick(songWithArtist) }
                        )
                    }
                }

                // Extra spacer so even a short list can scroll to hide item 0 (search dummy)
                item { Spacer(Modifier.height(250.dp)) }
            }
        }

        // Loading Overlay
        if (uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().background(bgBlack.copy(0.85f)).zIndex(5f),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = accentBlue)
            }
        }
    }
}
