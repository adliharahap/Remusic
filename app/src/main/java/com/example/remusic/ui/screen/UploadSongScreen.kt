package com.example.remusic.ui.screen

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.remusic.data.UserManager
import com.example.remusic.ui.components.uploadsongcomponent.SearchTextField
import com.example.remusic.ui.components.uploadsongcomponent.SongCard
import com.example.remusic.ui.components.uploadsongcomponent.SongListHeader
import com.example.remusic.ui.components.uploadsongcomponent.SongSelectedComponent
import com.example.remusic.ui.components.uploadsongcomponent.SortBottomSheet
import com.example.remusic.ui.components.uploadsongcomponent.SortOption
import com.example.remusic.ui.theme.AppFont
import com.example.remusic.utils.AudioFile
import com.example.remusic.utils.getAllAudioFiles
import kotlinx.coroutines.launch

// ✅ FIX: Fungsi helper untuk mencari Activity dari Context
private fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

// Fungsi helper untuk memeriksa apakah semua izin sudah diberikan
private fun arePermissionsGranted(context: Context, permissions: Array<String>): Boolean {
    return permissions.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }
}

class UploadSongViewModel : ViewModel() {
    var selectedSong by mutableStateOf<AudioFile?>(null)
        private set

    fun selectSong(song: AudioFile) {
        selectedSong = song
    }

    fun deleteSelectedSong() {
        selectedSong = null
    }
}


@Composable
fun UploadSongScreen(viewModel: UploadSongViewModel = viewModel(), onUploadMusicSuccess: () -> Unit) {
    val context = LocalContext.current
    val activity = context.findActivity() // Dapatkan activity untuk pengecekan rationale

    val permissionsToRequest = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.POST_NOTIFICATIONS)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    var hasPermissions by remember {
        mutableStateOf(arePermissionsGranted(context, permissionsToRequest))
    }

    var isPermanentlyDenied by remember {
        mutableStateOf(false)
    }

    val multiplePermissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { permissions: Map<String, Boolean> ->
        val allGranted = permissions.values.all { it }
        hasPermissions = allGranted

        if (!allGranted) {
            // ✅ FIX: Gunakan activity?. untuk memanggil shouldShowRequestPermissionRationale dengan aman
            isPermanentlyDenied = permissions.any { (permission, isGranted) ->
                !isGranted && activity?.shouldShowRequestPermissionRationale(permission) == false
            }
        }
    }

    LaunchedEffect(Unit) {
        if (!hasPermissions) {
            multiplePermissionsLauncher.launch(permissionsToRequest)
        }
    }

    //state ketika user selesai memilih lagu
    val selectedSong = viewModel.selectedSong

    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        if (hasPermissions) {
            MainContent(
                selectedSong = selectedSong,
                onSongSelected = { viewModel.selectSong(it) },
                deleteSelectedSong = { viewModel.deleteSelectedSong()},
                onUploadMusicSuccess = onUploadMusicSuccess
            )
        } else {
            PermissionDeniedContent(
                isPermanentlyDenied = isPermanentlyDenied,
                onRequestPermission = {
                    if (isPermanentlyDenied) {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                        }
                        context.startActivity(intent)
                    } else {
                        multiplePermissionsLauncher.launch(permissionsToRequest)
                    }
                }
            )
        }
    }
}

// Composable MainContent dan PermissionDeniedContent
@Composable
fun MainContent(
    selectedSong: AudioFile?,
    onSongSelected: (AudioFile) -> Unit,
    deleteSelectedSong: () -> Unit,
    onUploadMusicSuccess: () -> Unit
) {
    val context = LocalContext.current
    val user = UserManager.currentUser
    // State untuk menyimpan daftar lagu dari perangkat
    var audioFiles by remember { mutableStateOf(getAllAudioFiles(context)) }

    var showSortSheet by remember { mutableStateOf(false) }
    var sortOption by remember { mutableStateOf<SortOption?>(SortOption.TitleAsc) }

    //state untuk melakukan pencarian music
    var searchQuery by remember { mutableStateOf("") }
    val filteredAudioFiles = remember(searchQuery, audioFiles) {
        if (searchQuery.isBlank()) {
            audioFiles // Jika kolom pencarian kosong, tampilkan semua lagu
        } else {
            // Filter lagu berdasarkan judul atau artis, tanpa mempedulikan huruf besar/kecil
            audioFiles.filter { audioFile ->
                audioFile.title.contains(searchQuery, ignoreCase = true) ||
                        audioFile.artist.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(color = Color.Black)
    ) {
        Spacer(modifier = Modifier.height(30.dp).fillMaxWidth())
        // --- Bagian Atas: Profil & Judul ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                model = user?.photoUrl,
                contentDescription = "Profile Picture",
                modifier = Modifier.size(40.dp).clip(CircleShape),
                contentScale = ContentScale.Crop
            )
            Text(
                text = "Upload Song",
                color = Color.White,
                fontSize = 20.sp,
                fontFamily = AppFont.RobotoBold,
                modifier = Modifier.padding(start = 16.dp)
            )
            if(selectedSong != null) {
                Spacer(modifier = Modifier.weight(1f))
                Button(
                    onClick ={deleteSelectedSong()},
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White.copy(alpha = 0.2f), // White with opacity
                        contentColor = Color.White // Text and icon color
                    ),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                        Text(
                            text = "Back",
                            fontSize = 16.sp,
                            fontFamily = AppFont.RobotoBold,
                            modifier = Modifier.padding(start = 2.dp)
                        )
                    }
                }
            }
        }

        AnimatedContent(
            targetState = selectedSong,
            transitionSpec = {
                // masuknya fade + geser dari kanan
                (fadeIn() + slideInHorizontally { it / 4 }) togetherWith
                        // keluarnya fade + geser ke kiri
                        (fadeOut() + slideOutHorizontally { -it / 4 })
            },
            label = "SongTransition"
        ) { song ->
            if (song != null) {
                SongSelectedComponent(selectedSong = song, onUploadMusicSuccess = onUploadMusicSuccess)
            } else {
                Column (
                    modifier = Modifier.fillMaxSize(),
                ){
                    // --- Bagian Search ---
                    Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            SearchTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                searchTitle = "Cari lagu yang ingin kamu upload"
                            )
                    }

                    // --- Bagian Header List Lagu ---
                    SongListHeader(songCount = audioFiles.size) {
                        showSortSheet = true
                    }

                    // --- Bagian Daftar Lagu ---
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 5.dp, end = 0.dp)
                    ) {
                        items(
                            items = filteredAudioFiles,
                            key = { it.id }
                        ) { audioFile ->
                            SongCard(audioFile = audioFile, showMoreOption = false) {
                                onSongSelected(audioFile)
                            }
                        }
                        item {
                            Spacer(modifier = Modifier.height(70.dp))
                        }
                    }
                }
            }
        }
    }

    // ✅ Bottom Sheet Sort
    if (showSortSheet) {
        SortBottomSheet(
            currentSort = sortOption,
            onDismiss = { showSortSheet = false },
            onSortSelected = { option ->
                sortOption = option
                showSortSheet = false
                audioFiles = when (option) {
                    SortOption.TitleAsc -> audioFiles.sortedBy { it.title }
                    SortOption.TitleDesc -> audioFiles.sortedByDescending { it.title }
                    SortOption.Artist -> audioFiles.sortedBy { it.artist }
                    SortOption.RecentlyAdded -> audioFiles.sortedByDescending { it.addedDate }
                    SortOption.DurationAsc -> audioFiles.sortedBy { it.duration }
                    SortOption.DurationDesc -> audioFiles.sortedByDescending { it.duration }
                    SortOption.SizeAsc -> audioFiles.sortedBy { it.size }
                    SortOption.SizeDesc -> audioFiles.sortedByDescending { it.size }
                }

                // ✅ Scroll ke atas setiap selesai sorting
                coroutineScope.launch {
                    listState.scrollToItem(0)
                }
            }
        )
    }
}


@Composable
fun PermissionDeniedContent(isPermanentlyDenied: Boolean, onRequestPermission: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.padding(24.dp)
    ) {
        Icon(
            imageVector = Icons.Rounded.Warning,
            contentDescription = "Izin Diperlukan",
            tint = Color.Yellow,
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(text = "Izin Diperlukan", color = Color.White, fontSize = 22.sp)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = if (isPermanentlyDenied) {
                "Anda telah memblokir izin. Untuk menambahkan lagu, izinkan akses media dan notifikasi dari pengaturan aplikasi."
            } else {
                "Untuk menambahkan lagu ke streaming music anda, perlu mengizinkan aplikasi untuk mengakses data lagu di perangkat anda."
            },
            color = Color.Gray, textAlign = TextAlign.Center, fontSize = 16.sp
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(onClick = onRequestPermission) {
            Text(
                text = if (isPermanentlyDenied) "Buka Pengaturan" else "Izinkan Akses"
            )
        }
    }
}