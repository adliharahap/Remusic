package com.example.remusic.ui.components.uploadsongcomponent

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Celebration
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Diamond
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.FlashOn
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.MovieFilter
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.SelfImprovement
import androidx.compose.material.icons.outlined.SentimentVeryDissatisfied
import androidx.compose.material.icons.outlined.SentimentVerySatisfied
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material.icons.outlined.Whatshot
import androidx.compose.material.icons.rounded.AddAPhoto
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.remusic.R
import com.example.remusic.data.UserManager
import com.example.remusic.ui.components.UploadModalState
import com.example.remusic.ui.components.UploadStatusModal
import com.example.remusic.ui.theme.AppFont
import com.example.remusic.utils.AudioFile
import com.example.remusic.utils.CropShape
import com.example.remusic.utils.NotificationUtils
import com.example.remusic.utils.rememberImagePickerLauncher
import com.example.remusic.viewmodel.ArtistUploadViewModel
import com.example.remusic.viewmodel.MusicUploadViewModel
import kotlinx.coroutines.launch

// Data mood beserta deskripsinya
val moodsWithDescription = mapOf(
    "Happy" to "Lagu ceria, lirik positif, beat upbeat, bikin semangat dan senyum-senyum.",
    "Sad" to "Lagu mellow, lirik tentang kehilangan atau patah hati, bikin hati terasa berat.",
    "Chill" to "Lagu santai, relaxing, cocok untuk suasana tenang atau menemani malam hari.",
    "Energetic" to "Lagu cepat, penuh semangat, beat kencang, bikin badan pengen gerak.",
    "Romantic" to "Lagu tentang cinta, penuh perasaan, bikin baper atau suasana jadi manis.",
    "Angry" to "Lagu keras, penuh emosi, lirik marah atau beat intens (rock/metal/trap).",
    "Melancholic" to "Lagu sendu, bernuansa nostalgia, bikin teringat masa lalu yang mendalam.",
    "Confident" to "Lagu swag, penuh percaya diri, biasanya hip hop/rap dengan vibe keren.",
    "Party" to "Lagu upbeat, dance, EDM, cocok untuk club atau kumpul rame-rame.",
    "Epic" to "Lagu megah, cinematic, seperti soundtrack film/game yang bikin merinding.",
    "Focus" to "Lagu instrumental, lo-fi, atau classical yang cocok untuk belajar atau kerja.",
    "Hopeful" to "Lagu optimis, lirik positif, vibe uplifting, bikin hati terasa ringan."
)

// Fungsi bantuan untuk mendapatkan ikon yang sesuai dengan mood
private fun getIconForMood(mood: String): ImageVector {
    return when (mood) {
        "Happy" -> Icons.Outlined.SentimentVerySatisfied
        "Sad" -> Icons.Outlined.SentimentVeryDissatisfied
        "Chill" -> Icons.Outlined.SelfImprovement
        "Energetic" -> Icons.Outlined.FlashOn
        "Romantic" -> Icons.Outlined.FavoriteBorder
        "Angry" -> Icons.Outlined.Whatshot
        "Melancholic" -> Icons.Outlined.History
        "Confident" -> Icons.Outlined.Diamond
        "Party" -> Icons.Outlined.Celebration
        "Epic" -> Icons.Outlined.MovieFilter
        "Focus" -> Icons.Outlined.Headphones
        "Hopeful" -> Icons.Outlined.WbSunny
        else -> Icons.Outlined.MusicNote
    }
}

fun getFileNameFromUri(context: Context, uri: Uri): String {
    var name = ""
    val cursor = context.contentResolver.query(uri, null, null, null, null)
    cursor?.use {
        if (it.moveToFirst()) {
            val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0) {
                name = it.getString(index)
            }
        }
    }
    return name
}


@Composable
fun SongSelectedComponent(selectedSong: AudioFile?, onUploadMusicSuccess: () -> Unit) {
    // --- State Management ---
    var title by remember { mutableStateOf(selectedSong?.title ?: "") }
    var lyrics by remember { mutableStateOf("") }
    var selectedMoods by remember { mutableStateOf<Set<String>>(emptySet()) }
    var imageUri by remember { mutableStateOf(selectedSong?.imageUrl) }
    var selectedArtistName by remember { mutableStateOf(selectedSong?.artist ?: "") }
    var newArtistName by remember { mutableStateOf("") }
    var newArtistDesc by remember { mutableStateOf("") }
    var newArtistPhotoUri by remember { mutableStateOf<String?>(null) }
    var isAddingNewArtist by remember { mutableStateOf(false) }
    var showArtistSearchModal by remember { mutableStateOf(false) }

    val artistUploadViewModel: ArtistUploadViewModel = viewModel()
    val artistUiState = artistUploadViewModel.uiState

    val musicUploadViewModel: MusicUploadViewModel = viewModel()
    val musicUploadState = musicUploadViewModel.uiState

    // --- SETUP UNTUK FUNGSI LIRIK DAN NOTIF---
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    if (musicUploadState.uploadSuccessMessage != null) {
        // Jika ada, tampilkan modal
        UploadStatusModal(
            state = UploadModalState.SUCCESS,
            onDismiss = {
                onUploadMusicSuccess()
                // 2. Panggil fungsi reset di ViewModel
                musicUploadViewModel.resetUploadState()
            }
        )
        NotificationUtils.showNotification(
            context = context,
            title = "Upload Lagu Berhasil",
            message = "Lagu $title oleh $selectedArtistName berhasil diunggah.", // Gunakan judul dari state
            destinationRoute = "main"
        )
    }

    if (musicUploadState.uploadErrorMessage != null) {
        // Jika ada, tampilkan modal
        UploadStatusModal(
            state = UploadModalState.FAILURE,
            onDismiss = {
                onUploadMusicSuccess()
                // 2. Panggil fungsi reset di ViewModel
                musicUploadViewModel.resetUploadState()
            }
        )
        NotificationUtils.showNotification(
            context = context,
            title = "Upload Lagu Gagal",
            message = "Tidak dapat mengunggah $title oleh $selectedArtistName. Silakan coba lagi nanti."
        )
    }

    val lrcPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri: Uri? ->
            uri?.let {
                val fileName = getFileNameFromUri(context, it)
                // Cek jika file adalah .lrc
                if (fileName.endsWith(".lrc", ignoreCase = true)) {
                    try {
                        context.contentResolver.openInputStream(it)?.use { inputStream ->
                            val fileContent = inputStream.bufferedReader().readText()
                            lyrics = fileContent // Sukses, update lirik
                            Toast.makeText(context, "File .lrc berhasil diimpor!", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        Log.e("LrcPicker", "Gagal membaca file .lrc", e)
                        // Tampilkan Snackbar jika gagal baca
                        scope.launch {
                            snackbarHostState.showSnackbar("Gagal membaca file .lrc")
                        }
                    }
                } else {
                    // Jika bukan .lrc, tampilkan Snackbar
                    scope.launch {
                        snackbarHostState.showSnackbar("Harap pilih file dengan format .lrc")
                    }
                }
            }
        }
    )


    val artistPhotoPicker = rememberImagePickerLauncher(
        cropShape = CropShape.CIRCLE,
        onImagePicked = { croppedUri ->
            newArtistPhotoUri = croppedUri.toString()
        }
    )

    val musicPosterPhotoPicker = rememberImagePickerLauncher(
        cropShape = CropShape.SQUARE,
        onImagePicked = { croppedUri ->
            imageUri = croppedUri.toString()
        }
    )

    val isFormValid by remember(
        title,
        lyrics,
        imageUri,
        selectedMoods,
        artistUiState.lastAddedArtist // ✅ KUNCI UTAMA: Jadikan ini sebagai kunci
    ) {
        derivedStateOf {
            // Aturan baru yang lebih sederhana dan eksplisit:
            val isArtistReady = artistUiState.lastAddedArtist != null

            // Gabungkan semua kondisi
            title.isNotBlank() &&
                    isArtistReady &&
                    lyrics.isNotBlank() &&
                    selectedMoods.isNotEmpty() &&
                    imageUri != null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            ImagePickerSection(imageUri = imageUri, onChangeClick = { musicPosterPhotoPicker() }, onErrorImage = { imageUri = null})
            Spacer(modifier = Modifier.height(24.dp))

            InfoInputSection(
                title = title,
                onTitleChange = { title = it },
                isAddingNewArtist = isAddingNewArtist,
                onAddNewArtistClick = {
                    isAddingNewArtist = !isAddingNewArtist
                    if (isAddingNewArtist) {
                        newArtistName = selectedArtistName
                    }
                },
                onSearchArtistClick = { showArtistSearchModal = true },
                newArtistName = newArtistName,
                onNewArtistNameChange = { newArtistName = it },
                newArtistDesc = newArtistDesc,
                onNewArtistDescChange = { newArtistDesc = it },
                newArtistPhotoUri = newArtistPhotoUri,
                onNewArtistPhotoChange = { artistPhotoPicker() },
                artistUploadViewModel = artistUploadViewModel,
            )

            Spacer(modifier = Modifier.height(24.dp))
            LyricsInputSection(
                lyrics = lyrics,
                onLyricsChange = { lyrics = it },
                onPasteClick = {
                    clipboardManager.getText()?.let { clipboardText ->
                        // Tambahkan teks dari clipboard ke state lirik
                        lyrics = lyrics + clipboardText.text
                        Toast.makeText(context, "Teks ditempel!", Toast.LENGTH_SHORT).show()
                    } ?: run {
                        Toast.makeText(context, "Clipboard kosong", Toast.LENGTH_SHORT).show()
                    }
                },
                onImportClick = {
                    lrcPickerLauncher.launch(arrayOf("*/*"))
                }
            )
            Spacer(modifier = Modifier.height(24.dp))
            MoodSelectionSection(
                selectedMoods = selectedMoods,
                onMoodSelected = { mood, isSelected ->
                    selectedMoods = if (isSelected) {
                        selectedMoods + mood
                    } else {
                        selectedMoods - mood
                    }
                }
            )
            Spacer(modifier = Modifier.height(32.dp))

            UploaderInfoSection()

            Spacer(modifier = Modifier.height(32.dp))

            ValidationChecklistSection(
                title = title,
                lyrics = lyrics,
                imageUri = imageUri,
                selectedMoods = selectedMoods,
                isArtistSelected = artistUiState.lastAddedArtist != null
            )

            Spacer(modifier = Modifier.height(32.dp))
            Button(
                onClick = {
                    val artistId = artistUiState.lastAddedArtist?.id ?: ""

                    musicUploadViewModel.uploadMusic(
                        context = context,
                        selectedSong = selectedSong,
                        title = title,
                        lyrics = lyrics,
                        selectedMoods = selectedMoods,
                        imageUri = imageUri,
                        artistId = artistId,
                    ) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                enabled = isFormValid && !musicUploadState.isLoading,
                shape = RoundedCornerShape(12.dp)
            ) {
                if (musicUploadState.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Text("Upload Music", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }

            musicUploadState.errorMessage?.let { error ->
                Text(
                    text = "Error: $error",
                    color = Color.Red,
                    modifier = Modifier.padding(top = 8.dp),
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(80.dp))
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.CenterStart)
        )
        if (showArtistSearchModal) {
            LaunchedEffect(Unit) {
                artistUploadViewModel.getAllArtists()
            }

            SearchArtistBottomSheet(
                artists = artistUiState.artistList,
                onDismiss = { showArtistSearchModal = false },
                onArtistSelected = { artist ->
                    selectedArtistName = artist.name
                    artistUploadViewModel.selectArtist(artist)
                    isAddingNewArtist = false
                    showArtistSearchModal = false
                },
                onAddNewArtistClick = {
                    isAddingNewArtist = true
                    showArtistSearchModal = false
                }
            )
        }
    }
}

@Composable
fun ImagePickerSection(imageUri: String?, onChangeClick: () -> Unit, onErrorImage: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(16.dp)),
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = imageUri,
            contentDescription = "Album Art",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            placeholder = painterResource(R.drawable.ic_music_note),
            error = painterResource(R.drawable.ic_music_note),
            onError = {
                println("Gagal memuat gambar: ${it.result.throwable}")
                onErrorImage()
            }
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.4f))
                .clickable(onClick = onChangeClick),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = "Change Image",
                    tint = Color.White,
                    modifier = Modifier.size(40.dp)
                )
                Text("Change Image", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun InfoInputSection(
    title: String,
    onTitleChange: (String) -> Unit,
    isAddingNewArtist: Boolean,
    onAddNewArtistClick: () -> Unit,
    onSearchArtistClick: () -> Unit,
    newArtistName: String,
    onNewArtistNameChange: (String) -> Unit,
    newArtistDesc: String,
    onNewArtistDescChange: (String) -> Unit,
    newArtistPhotoUri: String?,
    onNewArtistPhotoChange: () -> Unit,
    artistUploadViewModel: ArtistUploadViewModel,
) {
    // Ambil state dari ViewModel untuk menampilkan loading/error
    val formState = artistUploadViewModel.uiState
    val context = LocalContext.current

    val successMessage = formState.uploadSuccessMessage

    LaunchedEffect(successMessage) {
        successMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
        }
    }


    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF1A1A1A), RoundedCornerShape(12.dp))
            .padding(16.dp)
    ) {
        SectionTitle("Info Lagu*")
        StyledTextField(value = title, onValueChange = onTitleChange, label = "Judul Lagu")

        Spacer(modifier = Modifier.height(20.dp))
        SectionTitle("Artis*")

        // Menampilkan nama artis yang sudah dipilih (jika ada)
        if (formState.lastAddedArtist != null) {
            val artist = formState.lastAddedArtist

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF181616)),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Foto artis di tengah
                    AsyncImage(
                        model = artist.photoUrl,
                        contentDescription = "Foto Artis",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(100.dp)
                            .clip(CircleShape)
                            .background(Color.Gray.copy(alpha = 0.3f))
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Nama artis
                    Text(
                        text = artist.name,
                        color = Color.White,
                        fontFamily = AppFont.RobotoBold,
                        fontSize = 20.sp
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // Deskripsi artis
                    Text(
                        text = artist.description,
                        color = Color.Gray,
                        fontFamily = AppFont.RobotoRegular,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp),
                        maxLines = Int.MAX_VALUE
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Tombol Cancel
                    Button(
                        onClick = {
                            artistUploadViewModel.clearLastAddedArtist()
                            onNewArtistNameChange("")
                            onNewArtistDescChange("")
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Cancel", color = Color.White, fontFamily = AppFont.RobotoMedium)
                    }
                }
            }
        }

        if(formState.lastAddedArtist == null) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onSearchArtistClick,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Cari Artist")
                }
                Button(
                    onClick = onAddNewArtistClick,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(if (isAddingNewArtist) "Batal" else "Tambah Baru")
                }
            }
            Text(
                "Untuk menghindari duplikasi, cari dulu sebelum menambah artis baru.",
                fontSize = 12.sp,
                color = Color.Gray,
                modifier = Modifier.padding(top = 8.dp)
            )

            // Form untuk menambah artis baru, muncul/hilang dengan animasi
            AnimatedVisibility(visible = isAddingNewArtist) {
                Column(Modifier.padding(top = 20.dp)) {
                    HorizontalDivider(
                        modifier = Modifier.padding(bottom = 20.dp),
                        color = Color.Gray
                    )
                    SectionTitle("Form Artis Baru*")

                    // Input Foto Profil Artis
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .clip(CircleShape)
                            .background(Color.DarkGray)
                            .clickable(onClick = onNewArtistPhotoChange)
                            .align(Alignment.CenterHorizontally),
                        contentAlignment = Alignment.Center
                    ) {
                        if (newArtistPhotoUri != null) {
                            AsyncImage(
                                model = newArtistPhotoUri,
                                contentDescription = "Foto Profil Artis",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(Icons.Rounded.AddAPhoto, "Tambah Foto", tint = Color.LightGray)
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    StyledTextField(
                        value = newArtistName,
                        onValueChange = onNewArtistNameChange,
                        label = "Nama Artist"
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    StyledTextField(
                        value = newArtistDesc,
                        onValueChange = onNewArtistDescChange,
                        label = "Deskripsi Artist",
                        modifier = Modifier.height(100.dp)
                    )

                    Spacer(modifier = Modifier.height(20.dp))
                    // Logika untuk validasi form artis baru
                    val isNewArtistFormValid = newArtistName.isNotBlank() &&
                            newArtistDesc.isNotBlank() &&
                            newArtistPhotoUri != null

                    Button(
                        onClick = {
                            // PANGGIL FUNGSI UTAMA DARI VIEWMODEL DI SINI
                            artistUploadViewModel.saveNewArtist(
                                context = context,
                                photoUriString = newArtistPhotoUri,
                                artistName = newArtistName,
                                artistDesc = newArtistDesc
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = isNewArtistFormValid && !formState.isLoading,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        if (formState.isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("Simpan Artist")
                        }
                    }

                    // Tampilkan pesan error dari ViewModel
                    formState.errorMessage?.let { error ->
                        Text(
                            text = "Error: $error",
                            color = Color.Red,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LyricsInputSection(
    lyrics: String,
    onLyricsChange: (String) -> Unit,
    onPasteClick: () -> Unit,
    onImportClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF1A1A1A), RoundedCornerShape(12.dp))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionTitle("Lirik Lagu*")
            Spacer(modifier = Modifier.weight(1f))
            TextButton(onClick = {
                onPasteClick()
            }) {
                Icon(Icons.Outlined.ContentPaste, contentDescription = "Paste", modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Paste")
            }
            TextButton(onClick = {
                onImportClick()
            }) {
                Icon(Icons.Outlined.FileUpload, contentDescription = "Import .lrc", modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Import .lrc")
            }
        }
        StyledTextField(
            value = lyrics,
            onValueChange = onLyricsChange,
            label = "Ketik atau paste lirik di sini...",
            modifier = Modifier.height(200.dp)
        )
    }
}

@Composable
fun MoodSelectionSection(
    selectedMoods: Set<String>,
    onMoodSelected: (String, Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF1A1A1A), RoundedCornerShape(12.dp))
            .padding(16.dp)
    ) {
        SectionTitle("Mood Lagu*")
        Text(
            "Pilih mood yang paling dominan pada lagumu. Kamu bisa memilih lebih dari satu.",
            fontSize = 14.sp,
            color = Color.Gray,
            modifier = Modifier.padding(bottom = 20.dp)
        )

        // Tampilkan daftar Mood Card
        moodsWithDescription.forEach { (mood, description) ->
            MoodCard(
                mood = mood,
                description = description,
                icon = getIconForMood(mood),
                isSelected = mood in selectedMoods,
                onClick = { isSelected -> onMoodSelected(mood, isSelected) }
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
fun UploaderInfoSection() {
    // Ambil data user dari state global UserManager
    val user = UserManager.currentUser

    // Tampilkan komponen hanya jika user tidak null
    if (user != null) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // Desain kartu yang konsisten dengan section lainnya
                .background(Color(0xFF1A1A1A), RoundedCornerShape(12.dp))
                .padding(16.dp)
        ) {
            SectionTitle("Uploader Info") // Menggunakan SectionTitle yang sudah ada

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Kiri: Foto Profil
                AsyncImage(
                    model = user.photoUrl,
                    contentDescription = "Uploader Profile Picture",
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop,
                    placeholder = painterResource(R.drawable.ic_music_note),
                    error = painterResource(R.drawable.ic_music_note),
                )

                Spacer(modifier = Modifier.width(16.dp))

                // Kanan: Nama, Email, dan UID
                Column {
                    Text(
                        text = user.displayName ?: "No Name",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = user.email ?: "No Email",
                        color = Color.Gray,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "UID: ${user.uid}",
                        color = Color.Gray.copy(alpha = 0.7f),
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

// --- Komponen-komponen Kecil (Helpers) ---

@Composable
fun SectionTitle(title: String) {
    Text(
        title,
        color = Color.White,
        fontWeight = FontWeight.Bold,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(bottom = 12.dp)
    )
}

@Composable
fun StyledTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = Color.Gray,
            focusedLabelColor = MaterialTheme.colorScheme.primary,
            unfocusedLabelColor = Color.Gray,
            cursorColor = MaterialTheme.colorScheme.primary,
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            unfocusedContainerColor = Color.Transparent,
            focusedContainerColor = Color.Transparent
        )
    )
}

@Composable
fun MoodCard(
    mood: String,
    description: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: (Boolean) -> Unit
) {
    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.5f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(2.dp, borderColor, RoundedCornerShape(10.dp))
            .clip(RoundedCornerShape(10.dp))
            .clickable { onClick(!isSelected) }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = mood,
            tint = borderColor,
            modifier = Modifier.size(32.dp)
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp)
        ) {
            Text(mood, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text(description, color = Color.Gray, fontSize = 13.sp)
        }
        Checkbox(
            checked = isSelected,
            onCheckedChange = { onClick(it) },
            colors = CheckboxDefaults.colors(
                checkedColor = MaterialTheme.colorScheme.primary,
                uncheckedColor = Color.Gray
            )
        )
    }
}

@Composable
fun ValidationChecklistSection(
    title: String,
    lyrics: String,
    imageUri: String?,
    selectedMoods: Set<String>,
    isArtistSelected: Boolean
) {
    Column(
        modifier = Modifier.fillMaxWidth()
            .background(Color(0xFF1A1A1A), RoundedCornerShape(12.dp))
            .padding(16.dp)
    ) {
        Text(
            text = "Checklist Kelengkapan",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Spacer(modifier = Modifier.height(8.dp))

        ValidationRow(
            isValid = imageUri != null,
            validText = "Poster musik sudah dipilih",
            invalidText = "Poster musik tidak boleh kosong",
            hint = "Upload gambar yang sesuai sebagai poster lagu agar tampil lebih menarik."
        )
        ValidationRow(
            isValid = isArtistSelected,
            validText = "Artis sudah dipilih",
            invalidText = "Artis belum diisi",
            hint = "Cari artis yang sudah ada terlebih dahulu. Jika tidak ditemukan, tambahkan artis baru. Pastikan tidak membuat duplikat agar data tetap rapi."
        )
        ValidationRow(
            isValid = title.isNotBlank(),
            validText = "Judul lagu sudah diisi",
            invalidText = "Judul lagu belum diisi",

            hint = "Masukkan judul lagu saja, tanpa tambahan nama artis atau detail lain."
        )
        ValidationRow(
            isValid = lyrics.isNotBlank(),
            validText = "Lirik sudah diisi",
            invalidText = "Lirik belum diisi",
            hint = "Harap masukkan lirik dengan format timestamp, misalnya: [00:30] di awal setiap baris."
        )
        ValidationRow(
            isValid = selectedMoods.isNotEmpty(),
            validText = "Mood lagu sudah dipilih",
            invalidText = "Mood lagu belum dipilih",

            hint = "Pilih satu atau lebih mood yang paling menggambarkan lagu ini. Pemilihan yang tepat akan membantu menjaga kualitas rekomendasi dan playlist."
        )
    }
}

/**
 * Komponen kecil untuk menampilkan satu baris validasi (Icon + Teks).
 */
@Composable
private fun ValidationRow(
    isValid: Boolean,
    validText: String,
    invalidText: String,
    hint: String? = null
) {
    val iconColor by animateColorAsState(
        targetValue = if (isValid) Color.Green else Color.Red,
        label = "iconColorAnimation"
    )
    val textColor by animateColorAsState(
        targetValue = if (isValid) Color.Green else Color.Red,
        label = "textColorAnimation"
    )
    val displayText = if (isValid) validText else invalidText
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 4.dp)
    ) {
        Icon(
            imageVector = if (isValid) Icons.Default.Check else Icons.Default.Close,
            contentDescription = "Validation Status",
            tint = iconColor,
            modifier = Modifier.size(20.dp)
        )
        Column(modifier = Modifier.padding(start = 12.dp)) {
            Text(
                text = displayText,
                color = textColor,
                fontSize = 14.sp
            )
            if (hint != null) {
                Text(
                    text = hint,
                    color = Color.Gray,
                    fontSize = 12.sp
                )
            }
        }
    }
}