package com.example.remusic.ui.screen

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import com.example.remusic.data.UserManager
import com.example.remusic.ui.theme.AppFont
import com.example.remusic.utils.CropShape
import com.example.remusic.utils.rememberImagePickerLauncher
import com.example.remusic.viewmodel.EditProfileState
import com.example.remusic.viewmodel.EditProfileViewModel
import kotlinx.coroutines.delay

@Composable
fun EditProfileScreen(
    onNavigateBack: () -> Unit,
    viewModel: EditProfileViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val userProfile = UserManager.currentUser
    val focusManager = LocalFocusManager.current

    // Error and Success handling
    LaunchedEffect(uiState) {
        when (uiState) {
            is EditProfileState.Success -> {
                android.widget.Toast.makeText(context, (uiState as EditProfileState.Success).message, android.widget.Toast.LENGTH_SHORT).show()
                viewModel.resetState()
                onNavigateBack()
            }
            is EditProfileState.Error -> {
                android.widget.Toast.makeText(context, (uiState as EditProfileState.Error).message, android.widget.Toast.LENGTH_LONG).show()
                viewModel.resetState()
            }
            else -> {}
        }
    }

    // State untuk form
    var displayName by remember { mutableStateOf(userProfile?.displayName ?: "") }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    
    // Animasi masuk
    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(100)
        isVisible = true
    }

    // Photo Picker Launcher with 1:1 Crop
    val photoPickerLauncher = rememberImagePickerLauncher(
        cropShape = CropShape.SQUARE,
        onImagePicked = { uri -> selectedImageUri = uri }
    )

    // Gradient Background sama dengan Profile Screen
    val backgroundBrush = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF2B0B16), // Dark Pink/Magenta tint
            Color(0xFF0A0A0A),
            Color(0xFF000000)
        ),
        startY = 0f,
        endY = 1200f
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundBrush)
            .windowInsetsPadding(WindowInsets.systemBars)
            .imePadding() // Agar tidak tertutup keyboard
            .clickable { focusManager.clearFocus() } // Klik area kosong untuk tutup keyboard
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            
            // --- TOP APP BAR (Batal & Simpan) ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Batal",
                        tint = Color.White
                    )
                }
                
                Text(
                    text = "Edit Profil",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontFamily = AppFont.Poppins,
                    fontWeight = FontWeight.Bold
                )

                TextButton(
                    onClick = { 
                        focusManager.clearFocus()
                        if (uiState !is EditProfileState.Loading) {
                            viewModel.updateProfile(context, displayName, selectedImageUri)
                        }
                    },
                    enabled = displayName.isNotBlank() && uiState !is EditProfileState.Loading
                ) {
                    if (uiState is EditProfileState.Loading) {
                        CircularProgressIndicator(
                            color = Color(0xFFE91E63),
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            text = "Simpan",
                            color = if (displayName.isNotBlank()) Color(0xFFE91E63) else Color.Gray,
                            fontSize = 16.sp,
                            fontFamily = AppFont.Helvetica,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // --- AVATAR EDITOR ---
            AnimatedVisibility(
                visible = isVisible,
                enter = fadeIn(animationSpec = tween(500)) + slideInVertically(initialOffsetY = { -30 })
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(140.dp)
                            .clickable(enabled = uiState !is EditProfileState.Loading) {
                                photoPickerLauncher()
                            },
                        contentAlignment = Alignment.BottomEnd
                    ) {
                        // Foto Profil
                        AsyncImage(
                            model = selectedImageUri ?: userProfile?.photoUrl,
                            contentDescription = "Profile Picture",
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                                .border(2.dp, Color.White.copy(alpha = 0.1f), CircleShape),
                            contentScale = ContentScale.Crop
                        )
                        
                        // Icon Camera Badge (Untuk menandakan bisa di-klik)
                        Box(
                            modifier = Modifier
                                .padding(4.dp)
                                .size(36.dp)
                                .background(Color(0xFFE91E63), CircleShape)
                                .border(3.dp, Color(0xFF121212), CircleShape), // Fake cutout effect
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CameraAlt,
                                contentDescription = "Ubah Foto",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Ketuk untuk mengubah foto",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 13.sp,
                        fontFamily = AppFont.Helvetica
                    )
                }
            }

            Spacer(modifier = Modifier.height(48.dp))

            // --- FORM INPUT FIELDS ---
            AnimatedVisibility(
                visible = isVisible,
                enter = fadeIn(animationSpec = tween(500, delayMillis = 150)) + slideInVertically(initialOffsetY = { 30 })
            ) {
                Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                    Text(
                        text = "INFORMASI PUBLIK",
                        color = Color.White.copy(alpha = 0.4f),
                        fontSize = 12.sp,
                        fontFamily = AppFont.Helvetica,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
                    )

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF161616))
                    ) {
                        Column {
                            // Input Nama
                            EditTextFieldItem(
                                label = "Nama Lengkap",
                                value = displayName,
                                onValueChange = { displayName = it },
                                placeholder = "Masukkan namamu...",
                                imeAction = ImeAction.Done,
                                onDone = { focusManager.clearFocus() }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    Text(
                        text = "INFORMASI PRIBADI",
                        color = Color.White.copy(alpha = 0.4f),
                        fontSize = 12.sp,
                        fontFamily = AppFont.Helvetica,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
                    )

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF161616))
                    ) {
                        Column {
                            // Read-only Email
                            ReadOnlyFieldItem(
                                label = "Email",
                                value = userProfile?.email ?: "Tidak tersedia"
                            )
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 16.dp),
                                thickness = 1.dp,
                                color = Color.White.copy(alpha = 0.05f)
                            )
                            // Read-only Role
                            ReadOnlyFieldItem(
                                label = "Tipe Akun",
                                value = (userProfile?.role ?: "Listener").uppercase(),
                                isHighlight = userProfile?.role?.equals("premium", true) == true
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Email dan tipe akun dikelola oleh sistem dan tidak dapat diubah secara langsung dari layar ini.",
                        color = Color.White.copy(alpha = 0.3f),
                        fontSize = 12.sp,
                        fontFamily = AppFont.Helvetica,
                        lineHeight = 16.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

// --- KOMPONEN CUSTOM UNTUK TEXT FIELD ---

@Composable
fun EditTextFieldItem(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    imeAction: ImeAction = ImeAction.Next,
    onDone: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = 15.sp,
            fontFamily = AppFont.Helvetica,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.width(110.dp) // Fixed width agar sejajar
        )
        
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = TextStyle(
                color = Color.White,
                fontSize = 15.sp,
                fontFamily = AppFont.Helvetica
            ),
            cursorBrush = SolidColor(Color(0xFFE91E63)),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = imeAction),
            keyboardActions = KeyboardActions(onDone = { onDone() }),
            modifier = Modifier.weight(1f),
            decorationBox = { innerTextField ->
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        color = Color.White.copy(alpha = 0.3f),
                        fontSize = 15.sp,
                        fontFamily = AppFont.Helvetica
                    )
                }
                innerTextField()
            }
        )
    }
}

@Composable
fun ReadOnlyFieldItem(
    label: String,
    value: String,
    isHighlight: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 15.sp,
            fontFamily = AppFont.Helvetica,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.width(110.dp)
        )
        
        Text(
            text = value,
            color = if (isHighlight) Color(0xFFFFD700) else Color.White.copy(alpha = 0.4f), // Emas jika premium, abu-abu jika biasa
            fontSize = 15.sp,
            fontFamily = AppFont.Helvetica,
            fontWeight = if (isHighlight) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.weight(1f)
        )
    }
}