package com.example.remusic.ui.screen

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.remusic.data.network.DeezerTrack
import com.example.remusic.ui.theme.AppFont
import com.example.remusic.viewmodel.playmusic.PlayMusicViewModel
import com.example.remusic.viewmodel.requestsong.RequestSongViewModel
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RequestSongScreen(
    navController: NavController,
    playMusicViewModel: PlayMusicViewModel,
    requestSongViewModel: RequestSongViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current

    val searchResults by requestSongViewModel.searchResults.collectAsState()
    val isLoading by requestSongViewModel.isLoading.collectAsState()
    val errorMessage by requestSongViewModel.errorMessage.collectAsState()
    val playingTrackId by requestSongViewModel.currentlyPlayingTrackId.collectAsState()
    val isPlaying by requestSongViewModel.isPlaying.collectAsState()
    val currentPosition by requestSongViewModel.currentPosition.collectAsState()
    val duration by requestSongViewModel.duration.collectAsState()
    val requestStatus by requestSongViewModel.requestStatus.collectAsState()
    val requestedTrackIds by requestSongViewModel.requestedTrackIds.collectAsState()
    
    val mainPlayerState by playMusicViewModel.uiState.collectAsState()

    DisposableEffect(Unit) {
        onDispose {
            requestSongViewModel.stopPreview()
        }
    }

    LaunchedEffect(mainPlayerState.isPlaying) {
        if (mainPlayerState.isPlaying) {
            requestSongViewModel.stopPreview()
        }
    }

    var searchQuery by remember { mutableStateOf("") }
    var myRequestSearchQuery by remember { mutableStateOf("") }
    var isSortingRecent by remember { mutableStateOf(true) }
    
    val myRequests by requestSongViewModel.myRequests.collectAsState()
    val isLoadingRequests by requestSongViewModel.isLoadingRequests.collectAsState()

    var selectedTabIndex by remember { mutableStateOf(0) }
    val tabs = listOf("Cari Lagu", "Request Saya")
    
    var requestToCancel by remember { mutableStateOf<String?>(null) }
    var showCancelDialog by remember { mutableStateOf(false) }
    var showManualRequestDialog by remember { mutableStateOf(false) }

    if (showManualRequestDialog) {
        var manualTitle by remember { mutableStateOf("") }
        var manualArtist by remember { mutableStateOf("") }
        var manualLink by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showManualRequestDialog = false },
            title = {
                Text(text = "Request Lagu Manual", fontFamily = AppFont.MontserratBold, color = Color.White)
            },
            text = {
                Column {
                    Text(text = "Isi form di bawah ini jika lagu tidak ada di Deezer.", color = Color.Gray, fontSize = 13.sp, fontFamily = AppFont.RobotoRegular)
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    OutlinedTextField(
                        value = manualTitle,
                        onValueChange = { manualTitle = it },
                        label = { Text("Judul Lagu (Wajib)", color = Color.Gray, fontSize = 13.sp) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFFE91E63),
                            unfocusedBorderColor = Color.DarkGray,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            cursorColor = Color(0xFFE91E63)
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = TextStyle(fontSize = 14.sp, fontFamily = AppFont.RobotoRegular)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    OutlinedTextField(
                        value = manualArtist,
                        onValueChange = { manualArtist = it },
                        label = { Text("Nama Artis (Wajib)", color = Color.Gray, fontSize = 13.sp) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFFE91E63),
                            unfocusedBorderColor = Color.DarkGray,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            cursorColor = Color(0xFFE91E63)
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = TextStyle(fontSize = 14.sp, fontFamily = AppFont.RobotoRegular)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    OutlinedTextField(
                        value = manualLink,
                        onValueChange = { manualLink = it },
                        label = { Text("Link (Opsional tapi disarankan)", color = Color.Gray, fontSize = 13.sp) },
                        placeholder = { Text("YouTube, Spotify, Joox...", color = Color.DarkGray, fontSize = 13.sp) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFFE91E63),
                            unfocusedBorderColor = Color.DarkGray,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            cursorColor = Color(0xFFE91E63)
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = TextStyle(fontSize = 14.sp, fontFamily = AppFont.RobotoRegular)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (manualTitle.isNotBlank() && manualArtist.isNotBlank()) {
                            requestSongViewModel.requestManualSong(manualTitle, manualArtist, manualLink)
                            showManualRequestDialog = false
                        } else {
                            Toast.makeText(context, "Judul dan Nama Artis wajib diisi", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE91E63))
                ) {
                    Text("Kirim Request", color = Color.White, fontFamily = AppFont.RobotoMedium)
                }
            },
            dismissButton = {
                TextButton(onClick = { showManualRequestDialog = false }) {
                    Text("Batal", color = Color.LightGray, fontFamily = AppFont.RobotoMedium)
                }
            },
            containerColor = Color(0xFF1E1E1E),
            shape = RoundedCornerShape(12.dp)
        )
    }

    if (showCancelDialog && requestToCancel != null) {
        AlertDialog(
            onDismissRequest = { 
                showCancelDialog = false 
                requestToCancel = null
            },
            title = {
                Text(text = "Batalkan Request?", fontFamily = AppFont.MontserratBold, color = Color.White)
            },
            text = {
                Text(text = "Apakah anda yakin ingin mencancel request lagu ini?", fontFamily = AppFont.RobotoRegular, color = Color.LightGray)
            },
            confirmButton = {
                TextButton(onClick = {
                    requestToCancel?.let { id ->
                        requestSongViewModel.cancelRequest(id, context)
                    }
                    showCancelDialog = false
                    requestToCancel = null
                }) {
                    Text("Yakin", color = Color(0xFFF44336), fontFamily = AppFont.RobotoMedium)
                }
            },
            dismissButton = {
                TextButton(onClick = { 
                    showCancelDialog = false 
                    requestToCancel = null
                }) {
                    Text("Batal", color = Color.White, fontFamily = AppFont.RobotoMedium)
                }
            },
            containerColor = Color(0xFF1E1E1E),
            shape = RoundedCornerShape(12.dp)
        )
    }

    LaunchedEffect(requestStatus) {
        when (val status = requestStatus) {
            is RequestSongViewModel.RequestStatus.Success -> {
                Toast.makeText(context, status.message, Toast.LENGTH_SHORT).show()
                requestSongViewModel.resetRequestStatus()
            }
            is RequestSongViewModel.RequestStatus.Error -> {
                Toast.makeText(context, status.message, Toast.LENGTH_SHORT).show()
                requestSongViewModel.resetRequestStatus()
            }
            else -> {}
        }
    }

    LaunchedEffect(searchQuery) {
        delay(1000)
        if (searchQuery.isNotBlank()) {
            requestSongViewModel.searchSongs(searchQuery)
        } else {
            requestSongViewModel.searchSongs("")
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
    ) {
        // --- SEARCH BAR HEADER ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(color = Color(0xFF2A2A2A))
                .padding(top = 40.dp),
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
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = 2.dp),
                placeholder = {
                    Text(
                        text = "Cari lagu yang ingin kamu request...",
                        fontSize = 15.sp,
                        fontFamily = AppFont.RobotoRegular,
                        color = Color.LightGray
                    )
                },
                singleLine = true,
                textStyle = TextStyle(
                    fontSize = 16.sp,
                    fontFamily = AppFont.RobotoRegular,
                    color = Color.White
                ),
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = {
                            searchQuery = ""
                            requestSongViewModel.searchSongs("")
                            requestSongViewModel.stopPreview()
                        }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear Search",
                                tint = Color.Gray
                            )
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Search
                ),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        focusManager.clearFocus()
                        requestSongViewModel.searchSongs(searchQuery)
                    }
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

        // --- TABS ---
        TabRow(
            selectedTabIndex = selectedTabIndex,
            containerColor = Color(0xFF121212),
            contentColor = Color.White,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                    color = Color(0xFFE91E63)
                )
            }
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTabIndex == index,
                    onClick = { 
                        selectedTabIndex = index 
                        if (index == 1) requestSongViewModel.fetchMyRequests()
                    },
                    text = {
                        Text(
                            text = title,
                            fontFamily = AppFont.RobotoMedium,
                            fontSize = 15.sp,
                            color = if (selectedTabIndex == index) Color.White else Color.Gray
                        )
                    }
                )
            }
        }

        // --- CONTENT ---
        if (selectedTabIndex == 0) {
            // --- SEARCH RESULTS TAB ---
            // --- DISCLAIMER NOTE ---
            if (searchQuery.isEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Info",
                        tint = Color.Gray,
                        modifier = Modifier.padding(top = 2.dp, end = 8.dp)
                    )
                    Text(
                        text = "Data ini diambil dari API Deezer, ini cuma data lagu aja dan cuma menampilkan preview lagu nya, untuk full music nya bisa request dengan mengklik tombol request di  lagu yang kamu inginkan.",
                        color = Color.Gray,
                        fontSize = 13.sp,
                        fontFamily = AppFont.Helvetica,
                        lineHeight = 18.sp
                    )
                }
            }

        // --- DEEZER SEARCH RESULTS ---
        Box(modifier = Modifier.fillMaxSize()) {
            if (isLoading) {
                CircularProgressIndicator(
                    color = Color(0xFFE91E63),
                    modifier = Modifier.align(Alignment.Center)
                )
            } else if (errorMessage != null) {
                Text(
                    text = errorMessage!!,
                    color = Color.Red,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(16.dp)
                )
            } else if (searchResults.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(32.dp).align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = Color.DarkGray,
                        modifier = Modifier.size(64.dp).padding(bottom = 16.dp)
                    )
                    Text(
                        text = "Ketik judul lagu untuk mencari lagu\nyang kamu ingin request",
                        color = Color.DarkGray,
                        fontSize = 15.sp,
                        fontFamily = AppFont.RobotoMedium,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    TextButton(onClick = { showManualRequestDialog = true }) {
                        Text(
                            text = "Tidak Ketemu lagu yang kamu inginkan?\nRequest Manual", 
                            color = Color(0xFFE91E63), 
                            fontFamily = AppFont.RobotoMedium,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(bottom = 100.dp),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp)
                ) {
                    itemsIndexed(searchResults) { index, track ->
                        DeezerSongCard(
                            index = index,
                            track = track,
                            isPlaying = playingTrackId == track.id.toString() && isPlaying,
                            isRequested = requestedTrackIds.contains(track.id),
                            currentPosition = if (playingTrackId == track.id.toString()) currentPosition else 0L,
                            duration = if (playingTrackId == track.id.toString()) duration else 0L,
                            onPlayPauseClick = {
                                if (!(playingTrackId == track.id.toString() && isPlaying)) {
                                    if (playMusicViewModel.uiState.value.isPlaying) {
                                        playMusicViewModel.togglePlayPause()
                                    }
                                }
                                requestSongViewModel.playPreview(track.preview, track.id.toString())
                            },
                            onRequestClick = { requestSongViewModel.requestSong(track) }
                        )

                    }
                    
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            TextButton(onClick = { showManualRequestDialog = true }) {
                                Text("Tidak menemukan lagu yang dicari? Request Manual", color = Color(0xFFE91E63), fontFamily = AppFont.RobotoMedium)
                            }
                        }
                    }
                }
            }
        }
    } else {
        // --- MY REQUESTS STATUS TAB ---
            Box(modifier = Modifier.fillMaxSize()) {
                if (isLoadingRequests) {
                    CircularProgressIndicator(
                        color = Color(0xFFE91E63),
                        modifier = Modifier.align(Alignment.Center)
                    )
                } else if (myRequests.isEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(32.dp).align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Kamu belum pernah request lagu apa pun.",
                            color = Color.DarkGray,
                            fontSize = 14.sp,
                            fontFamily = AppFont.RobotoMedium
                        )
                    }
                } else {
                    val filteredRequests = myRequests.filter { 
                        it.songTitle.contains(myRequestSearchQuery, ignoreCase = true) || 
                        it.artistName.contains(myRequestSearchQuery, ignoreCase = true) 
                    }.let { list ->
                        if (isSortingRecent) list else list.reversed()
                    }
                    
                    Column(modifier = Modifier.fillMaxSize()) {
                        // --- Request Saya Search & Sort UI ---
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            BasicTextField(
                                value = myRequestSearchQuery,
                                onValueChange = { myRequestSearchQuery = it },
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 8.dp)
                                    .background(Color(0xFF1E1E1E), RoundedCornerShape(8.dp))
                                    .border(1.dp, Color(0xFF2A2A2A), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                textStyle = TextStyle(fontSize = 13.sp, fontFamily = AppFont.RobotoRegular, color = Color.White),
                                singleLine = true,
                                decorationBox = { innerTextField ->
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Search, 
                                            contentDescription = "Search", 
                                            tint = Color.Gray, 
                                            modifier = Modifier.size(16.dp).padding(end = 4.dp)
                                        )
                                        Box(Modifier.weight(1f)) {
                                            if (myRequestSearchQuery.isEmpty()) {
                                                Text(
                                                    text = "Cari Request Kamu...", 
                                                    fontSize = 13.sp, 
                                                    color = Color.Gray, 
                                                    fontFamily = AppFont.RobotoRegular
                                                )
                                            }
                                            innerTextField()
                                        }
                                        if (myRequestSearchQuery.isNotEmpty()) {
                                            Icon(
                                                imageVector = Icons.Default.Close, 
                                                contentDescription = "Clear", 
                                                tint = Color.LightGray,
                                                modifier = Modifier.size(16.dp).clickable { myRequestSearchQuery = "" }
                                            )
                                        }
                                    }
                                }
                            )

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF2A2A2A))
                                    .clickable { isSortingRecent = !isSortingRecent }
                                    .padding(horizontal = 12.dp, vertical = 10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Sort,
                                    contentDescription = "Sort",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isSortingRecent) "Terbaru" else "Terlama",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontFamily = AppFont.RobotoMedium
                                )
                            }
                        }

                        if (filteredRequests.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    text = "Request tidak ditemukan",
                                    color = Color.Gray,
                                    fontSize = 14.sp,
                                    fontFamily = AppFont.RobotoMedium
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(top = 8.dp, bottom = 120.dp) // Bottom padding for player bar
                            ) {
                                itemsIndexed(filteredRequests) { index, request ->
                                    // Use original index by finding it in original list if you want to show chronological ID, 
                                    // or just use current list order. Current list order is fine.
                                    val originalIndex = myRequests.indexOf(request)
                                    MyRequestCard(
                                        index = originalIndex,
                                        request = request,
                                        onCancelClick = {
                                            requestToCancel = request.id
                                            showCancelDialog = true
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DeezerSongCard(
    index: Int,
    track: DeezerTrack,
    isPlaying: Boolean,
    isRequested: Boolean,
    currentPosition: Long,
    duration: Long,
    onPlayPauseClick: () -> Unit,
    onRequestClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onPlayPauseClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Index number
        Text(
            text = "${index + 1}",
            style = TextStyle(
                fontSize = 15.sp,
                fontFamily = AppFont.RobotoMedium,
                color = Color.Gray
            ),
            modifier = Modifier.padding(end = 12.dp).width(24.dp)
        )

        // Play/Pause button with Circular Progress
        Box(
            modifier = Modifier
                .size(50.dp)
                .background(Color.Black.copy(alpha = 0.3f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = track.album.coverMedium,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
            
            // Dark overlay for visibility of icon
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f), CircleShape)
            )

            if (duration > 0 && isPlaying) {
                CircularProgressIndicator(
                    progress = { (currentPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxSize(),
                    color = Color.White,
                    strokeWidth = 2.dp,
                    trackColor = Color.Transparent,
                )
            }
            
            IconButton(
                onClick = onPlayPauseClick,
                modifier = Modifier.fillMaxSize()
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = Color.White
                )
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = TextStyle(
                    fontSize = 16.sp,
                    fontFamily = AppFont.MontserratBold,
                    color = Color.White
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = track.artist.name,
                style = TextStyle(
                    fontSize = 14.sp,
                    fontFamily = AppFont.RobotoRegular,
                    color = Color.Gray
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Button(
            onClick = { if (!isRequested) onRequestClick() },
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isRequested) Color(0xFF4CAF50).copy(alpha = 0.2f) else Color.Transparent
            ),
            border = if (isRequested) null else androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF755D8D)),
            shape = RoundedCornerShape(20.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
            modifier = Modifier.height(36.dp)
        ) {
            Text(
                text = if (isRequested) "Requested" else "Request",
                style = TextStyle(
                    fontSize = 13.sp,
                    fontFamily = AppFont.RobotoMedium,
                    color = if (isRequested) Color(0xFF4CAF50) else Color.White
                )
            )
        }
    }
}

fun formatISODate(isoString: String?): String {
    if (isoString.isNullOrBlank()) return "-"
    return try {
        isoString.substringBefore("T")
    } catch (e: Exception) {
        isoString
    }
}

@Composable
fun MyRequestCard(
    index: Int,
    request: RequestSongViewModel.MySongRequest,
    onCancelClick: () -> Unit
) {
    val isPending = request.status?.lowercase() == "pending"
    val (statusColor, statusIcon, statusText) = when (request.status?.lowercase()) {
        "approved" -> Triple(Color(0xFF4CAF50), Icons.Default.CheckCircle, "Disetujui")
        "rejected" -> Triple(Color(0xFFF44336), Icons.Default.Cancel, "Ditolak")
        "fulfilled" -> Triple(Color(0xFF2196F3), Icons.Default.DoneAll, "Selesai (Ditambahkan)")
        else -> Triple(Color(0xFFFFC107), Icons.Default.HourglassEmpty, "Menunggu respon")
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .background(Color(0xFF1A1A1A), RoundedCornerShape(12.dp))
            .border(1.dp, Color(0xFF2A2A2A), RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        // --- Header Section: Dates ---
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Diminta: ${formatISODate(request.createdAt)}",
                color = Color.Gray,
                fontSize = 11.sp,
                fontFamily = AppFont.RobotoMedium
            )
            
            if (!request.updatedAt.isNullOrBlank() && request.updatedAt != request.createdAt) {
                Text(
                    text = "Update: ${formatISODate(request.updatedAt)}",
                    color = Color.Gray,
                    fontSize = 11.sp,
                    fontFamily = AppFont.RobotoMedium
                )
            } else {
                Text(
                    text = "#${index + 1}",
                    color = Color.DarkGray,
                    fontSize = 11.sp,
                    fontFamily = AppFont.MontserratBold
                )
            }
        }
        
        // --- Main Content: Cover, Title, Artist ---
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Cover Image Placeholder
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (!request.coverUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = request.coverUrl,
                        contentDescription = "Cover Album",
                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(6.dp)),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(imageVector = Icons.Default.Info, contentDescription = null, tint = Color.Gray)
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Song & Artist Info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = request.songTitle,
                    style = TextStyle(
                        fontSize = 16.sp,
                        fontFamily = AppFont.MontserratBold,
                        color = Color.White
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!request.artistPhotoUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = request.artistPhotoUrl,
                            contentDescription = "Artist Photo",
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    } else {
                        Box(
                            modifier = Modifier.size(20.dp).background(Color(0xFF2A2A2A), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Info, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(14.dp))
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    
                    Text(
                        text = request.artistName,
                        style = TextStyle(
                            fontSize = 13.sp,
                            fontFamily = AppFont.RobotoMedium,
                            color = Color.LightGray
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        HorizontalDivider(color = Color(0xFF2A2A2A), thickness = 1.dp)
        Spacer(modifier = Modifier.height(12.dp))

        // --- Status & Cancel Button Area ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically, 
                modifier = Modifier
                    .background(statusColor.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Icon(
                    imageVector = statusIcon,
                    contentDescription = statusText,
                    tint = statusColor,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = statusText,
                    color = statusColor,
                    fontSize = 13.sp,
                    fontFamily = AppFont.MontserratBold
                )
            }
            
            if (isPending) {
                OutlinedButton(
                    onClick = onCancelClick,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFF44336)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF44336).copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(20.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text(
                        text = "Batalkan",
                        fontSize = 12.sp,
                        fontFamily = AppFont.RobotoMedium
                    )
                }
            }
        }

        // --- Rejection Reason (if any) ---
        if (request.status?.lowercase() == "rejected" && !request.rejectionReason.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(12.dp))
            ContainerRestrictedText(request.rejectionReason)
        }
    }
}

@Composable
private fun ContainerRestrictedText(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF2A2A2A), RoundedCornerShape(8.dp))
            .padding(8.dp)
    ) {
        Text(
            text = "Catatan Admin: $text",
            color = Color.LightGray,
            fontSize = 13.sp,
            fontFamily = AppFont.Helvetica,
            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
        )
    }
}
