package com.example.remusic.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import android.net.Uri
import androidx.compose.ui.layout.ContentScale
import com.example.remusic.utils.rememberImagePickerLauncher
import com.example.remusic.utils.CropShape
import coil.compose.AsyncImage

// Enum untuk mengontrol step UI
enum class CreateStep { NAME, DETAILS, PRIVACY }

@OptIn(ExperimentalAnimationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun CreatePlaylistScreen(
    onNavigateBack: () -> Unit,
    viewModel: com.example.remusic.viewmodel.CreatePlaylistViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    
    LaunchedEffect(uiState) {
        when (uiState) {
            is com.example.remusic.viewmodel.CreatePlaylistState.Success -> {
                android.widget.Toast.makeText(context, (uiState as com.example.remusic.viewmodel.CreatePlaylistState.Success).message, android.widget.Toast.LENGTH_SHORT).show()
                viewModel.resetState()
                onNavigateBack()
            }
            is com.example.remusic.viewmodel.CreatePlaylistState.Error -> {
                android.widget.Toast.makeText(context, (uiState as com.example.remusic.viewmodel.CreatePlaylistState.Error).message, android.widget.Toast.LENGTH_SHORT).show()
                viewModel.resetState()
            }
            else -> {}
        }
    }
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var coverUri by remember { mutableStateOf<Uri?>(null) }
    
    val imagePicker = rememberImagePickerLauncher(
        cropShape = CropShape.SQUARE,
        onImagePicked = { uri -> coverUri = uri }
    )
    
    // Asumsi nilai default enum dari project-mu adalah PlaylistPrivacy.PRIVATE
    var selectedPrivacy by remember { mutableStateOf(PlaylistPrivacy.PRIVATE) }
    
    // State untuk mengontrol slide saat ini
    var currentStep by remember { mutableStateOf(CreateStep.NAME) }

    // Gradient Background ala Spotify (Warna aksen memudar ke hitam)
    val backgroundBrush = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF2B0B16), // Dark Pink/Magenta tint di atas
            Color(0xFF121212),
            Color(0xFF000000)  // Hitam pekat di bawah
        ),
        startY = 0f,
        endY = 1500f
    )

    val scrollState = rememberScrollState()

    // Handle back press hardware/gesture
    BackHandler {
        when (currentStep) {
            CreateStep.NAME -> onNavigateBack()
            CreateStep.DETAILS -> currentStep = CreateStep.NAME
            CreateStep.PRIVACY -> currentStep = CreateStep.DETAILS
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundBrush)
            .windowInsetsPadding(WindowInsets.systemBars)
            .imePadding() // Penting: Mencegah UI tertutup oleh keyboard yang muncul
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            
            // Top Navigation Bar
            TopNavigationBar(
                currentStep = currentStep,
                onBackClick = {
                    when (currentStep) {
                        CreateStep.NAME -> onNavigateBack()
                        CreateStep.DETAILS -> currentStep = CreateStep.NAME
                        CreateStep.PRIVACY -> currentStep = CreateStep.DETAILS
                    }
                }
            )

            // Scrollable Container: Menggabungkan form & tombol
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(scrollState)
                    .padding(bottom = 120.dp), // Jarak aman mutlak dari Bottom Navigation
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(48.dp)) // Jarak dari Header ke Form

                // Animated Content untuk Slide antar Form
                AnimatedContent(
                    targetState = currentStep,
                    transitionSpec = {
                        // Logika animasi slide ke kiri/kanan berdasarkan urutan step
                        if (targetState.ordinal > initialState.ordinal) {
                            (slideInHorizontally(tween(400)) { width -> width } + fadeIn(tween(400))) togetherWith
                                    slideOutHorizontally(tween(400)) { width -> -width } + fadeOut(tween(400))
                        } else {
                            (slideInHorizontally(tween(400)) { width -> -width } + fadeIn(tween(400))) togetherWith
                                    slideOutHorizontally(tween(400)) { width -> width } + fadeOut(tween(400))
                        }
                    },
                    modifier = Modifier.fillMaxWidth(), // weight(1f) dihapus agar mengikuti tinggi konten
                    label = "step_animation"
                ) { step ->
                    when (step) {
                        CreateStep.NAME -> StepName(
                            title = title,
                            onTitleChange = { title = it },
                            onNext = { if (title.isNotBlank()) currentStep = CreateStep.DETAILS }
                        )
                        CreateStep.DETAILS -> StepDetails(
                            description = description,
                            onDescriptionChange = { description = it },
                            coverUri = coverUri,
                            onCoverClick = { imagePicker() }
                        )
                        CreateStep.PRIVACY -> StepPrivacy(
                            selectedPrivacy = selectedPrivacy,
                            onPrivacySelect = { selectedPrivacy = it }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(48.dp)) // Jarak antara Form dan Tombol, tombol jadi lebih dekat

                // Bottom Action Button (Next / Create)
                BottomActionBar(
                    currentStep = currentStep,
                    isNextEnabled = title.isNotBlank(),
                    isCreating = uiState is com.example.remusic.viewmodel.CreatePlaylistState.Loading,
                    onNextClick = {
                        when (currentStep) {
                            CreateStep.NAME -> currentStep = CreateStep.DETAILS
                            CreateStep.DETAILS -> currentStep = CreateStep.PRIVACY
                            CreateStep.PRIVACY -> {
                                if (uiState !is com.example.remusic.viewmodel.CreatePlaylistState.Loading) {
                                    viewModel.createPlaylist(context, title, description, selectedPrivacy, coverUri)
                                }
                            }
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun TopNavigationBar(currentStep: CreateStep, onBackClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBackClick) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
        }
        
        Spacer(modifier = Modifier.weight(1f))
        
        // Step Indicator (e.g., "1 dari 3")
        Text(
            text = "Langkah ${currentStep.ordinal + 1} dari 3",
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(end = 16.dp)
        )
    }
}

// ============================================================================
// SLIDE 1: GIANT TEXT FIELD (Nama Playlist)
// ============================================================================
@Composable
fun StepName(title: String, onTitleChange: (String) -> Unit, onNext: () -> Unit) {
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus() // Auto show keyboard
    }

    Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp), // fillMaxSize diganti jadi fillMaxWidth
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Beri nama playlistmu.",
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 24.dp)
            )
            
            BasicTextField(
                value = title,
                onValueChange = onTitleChange,
                textStyle = TextStyle(
                    fontSize = 36.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    textAlign = TextAlign.Center
                ),
                cursorBrush = SolidColor(Color(0xFFE91E63)),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { onNext() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp)
                    .focusRequester(focusRequester),
                decorationBox = { innerTextField ->
                    Box(contentAlignment = Alignment.Center) {
                        if (title.isEmpty()) {
                            Text(
                                "Playlist Baru",
                                style = TextStyle(
                                    fontSize = 36.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White.copy(alpha = 0.2f),
                                    textAlign = TextAlign.Center
                                )
                            )
                        }
                        innerTextField()
                    }
                }
            )
            
            // Underline Animation effect
            Box(
                modifier = Modifier
                    .padding(top = 16.dp)
                    .width(100.dp)
                    .height(2.dp)
                    .background(
                        if (title.isEmpty()) Color.White.copy(alpha = 0.2f) else Color(0xFFE91E63),
                        RoundedCornerShape(1.dp)
                    )
            )
        }
    }
}

// ============================================================================
// SLIDE 2: COVER & DESCRIPTION
// ============================================================================
@Composable
fun StepDetails(description: String, onDescriptionChange: (String) -> Unit, coverUri: Uri?, onCoverClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth() // fillMaxSize diganti jadi fillMaxWidth
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Cover Image Picker dengan desain Modern Soft Card
        Box(
            modifier = Modifier
                .size(200.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White.copy(alpha = 0.05f)) // Glass effect
                .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
                .clickable { onCoverClick() },
            contentAlignment = Alignment.Center
        ) {
            if (coverUri != null) {
                AsyncImage(
                    model = coverUri,
                    contentDescription = "Cover Image",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.AddPhotoAlternate,
                        contentDescription = "Add Cover",
                        tint = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Pilih Gambar Cover",
                        color = Color.White.copy(alpha = 0.5f),
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(40.dp))

        // Description Input Modern
        OutlinedTextField(
            value = description,
            onValueChange = onDescriptionChange,
            placeholder = { Text("Tambahkan deskripsi (opsional)...", color = Color.Gray) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color.Transparent,
                unfocusedBorderColor = Color.Transparent,
                focusedContainerColor = Color.White.copy(alpha = 0.05f),
                unfocusedContainerColor = Color.White.copy(alpha = 0.05f),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                cursorColor = Color(0xFFE91E63)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp),
            shape = RoundedCornerShape(16.dp),
            maxLines = 4,
            textStyle = TextStyle(fontSize = 16.sp)
        )
    }
}

// ============================================================================
// SLIDE 3: PRIVACY SELECTION
// ============================================================================
@Composable
fun StepPrivacy(selectedPrivacy: PlaylistPrivacy, onPrivacySelect: (PlaylistPrivacy) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth() // fillMaxSize diganti jadi fillMaxWidth
            .padding(horizontal = 24.dp)
    ) {
        Text(
            text = "Siapa yang bisa mendengar?",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        PrivacyCard(
            title = "Publik",
            description = "Semua orang bisa mencari dan melihat playlist ini.",
            icon = Icons.Default.Public,
            isSelected = selectedPrivacy == PlaylistPrivacy.PUBLIC,
            onClick = { onPrivacySelect(PlaylistPrivacy.PUBLIC) }
        )
        Spacer(modifier = Modifier.height(16.dp))
        
        PrivacyCard(
            title = "Pribadi",
            description = "Hanya kamu yang bisa melihat playlist ini.",
            icon = Icons.Default.Lock,
            isSelected = selectedPrivacy == PlaylistPrivacy.PRIVATE,
            onClick = { onPrivacySelect(PlaylistPrivacy.PRIVATE) }
        )
        Spacer(modifier = Modifier.height(16.dp))

        PrivacyCard(
            title = "Kolaborasi",
            description = "Teman-temanmu bisa ikut menambahkan lagu.",
            icon = Icons.Default.Group,
            isSelected = selectedPrivacy == PlaylistPrivacy.FRIENDS,
            isComingSoon = true,
            onClick = {}
        )
    }
}

@Composable
fun PrivacyCard(
    title: String,
    description: String,
    icon: ImageVector,
    isSelected: Boolean,
    isComingSoon: Boolean = false,
    onClick: () -> Unit
) {
    val accentColor = Color(0xFFE91E63)
    val bgColor by animateColorAsState(
        targetValue = if (isSelected) accentColor.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.03f),
        label = "bg_color"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) accentColor else Color.Transparent,
        label = "border_color"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(bgColor)
            .border(1.5.dp, borderColor, RoundedCornerShape(16.dp))
            .clickable(enabled = !isComingSoon) { onClick() }
            .padding(20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isComingSoon) Color.White.copy(alpha = 0.2f) else if (isSelected) accentColor else Color.White,
            modifier = Modifier.size(28.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    color = if (isComingSoon) Color.White.copy(alpha = 0.3f) else Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                if (isComingSoon) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "SEGERA",
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                color = if (isComingSoon) Color.White.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.6f),
                fontSize = 13.sp,
                lineHeight = 18.sp
            )
        }
        
        if (isSelected) {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = "Selected",
                tint = accentColor,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

// ============================================================================
// BOTTOM ACTION BAR (Tombol Next / Selesai)
// ============================================================================
@Composable
fun BottomActionBar(currentStep: CreateStep, isNextEnabled: Boolean, isCreating: Boolean, onNextClick: () -> Unit) {
    val isLastStep = currentStep == CreateStep.PRIVACY
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp), // padding vertical (24.dp) dihapus agar posisinya mengikuti Spacer sebelumnya
        contentAlignment = Alignment.Center
    ) {
        Button(
            onClick = onNextClick,
            enabled = isNextEnabled,
            modifier = Modifier
                .height(56.dp)
                .fillMaxWidth(if (isLastStep) 1f else 0.5f), // Tombol melebar di step terakhir
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFE91E63),
                disabledContainerColor = Color.White.copy(alpha = 0.1f)
            ),
            shape = CircleShape // Bentuk pill/capsule yang modern
        ) {
            AnimatedContent(
                targetState = isLastStep,
                transitionSpec = {
                    fadeIn(tween(300)) togetherWith fadeOut(tween(300))
                },
                label = "button_text"
            ) { isLast ->
                if (isCreating) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                } else {
                    Text(
                        text = if (isLast) "Buat Playlist" else "Selanjutnya",
                        color = if (isNextEnabled) Color.White else Color.White.copy(alpha = 0.3f),
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            }
        }
    }
}