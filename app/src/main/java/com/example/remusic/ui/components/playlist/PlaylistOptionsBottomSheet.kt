package com.example.remusic.ui.components.playlist

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.remusic.data.model.Playlist
import com.example.remusic.ui.theme.AppFont
import com.example.remusic.viewmodel.playmusic.PlayMusicViewModel

enum class BottomSheetScreen {
    MAIN, VISIBILITY, EDIT, DELETE
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistOptionsBottomSheet(
    onDismiss: () -> Unit,
    playlist: Playlist?,
    isOwner: Boolean,
    playMusicViewModel: PlayMusicViewModel? = null,
    onDeleteComplete: () -> Unit = {}
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var currentScreen by remember { mutableStateOf(BottomSheetScreen.MAIN) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF1E1E1E), // Dark background matching Remusic
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        AnimatedContent(
            targetState = currentScreen,
            transitionSpec = {
                if (targetState > initialState) {
                    slideInHorizontally(animationSpec = tween(300), initialOffsetX = { fullWidth -> fullWidth }) + fadeIn(tween(300)) togetherWith
                            slideOutHorizontally(animationSpec = tween(300), targetOffsetX = { fullWidth -> -fullWidth }) + fadeOut(tween(300))
                } else {
                    slideInHorizontally(animationSpec = tween(300), initialOffsetX = { fullWidth -> -fullWidth }) + fadeIn(tween(300)) togetherWith
                            slideOutHorizontally(animationSpec = tween(300), targetOffsetX = { fullWidth -> fullWidth }) + fadeOut(tween(300))
                }.using(SizeTransform(clip = false))
            },
            label = "BottomSheetScreenAnimation"
        ) { screen ->
            when (screen) {
                BottomSheetScreen.MAIN -> MainOptionsScreen(
                    playlist = playlist,
                    isOwner = isOwner,
                    onNavigate = { currentScreen = it },
                    onDismiss = onDismiss
                )
                BottomSheetScreen.VISIBILITY -> VisibilityScreen(
                    playlist = playlist,
                    onBack = { currentScreen = BottomSheetScreen.MAIN },
                    onSave = { newVisibility ->
                        playlist?.id?.let { playMusicViewModel?.updatePlaylistVisibility(it, newVisibility) }
                        onDismiss()
                    }
                )
                BottomSheetScreen.EDIT -> EditProfileScreen(
                    playlist = playlist,
                    onBack = { currentScreen = BottomSheetScreen.MAIN },
                    onSave = { title, desc, coverUrl ->
                        playlist?.id?.let { playMusicViewModel?.updatePlaylistProfile(it, title, desc, coverUrl) }
                        onDismiss()
                    }
                )
                BottomSheetScreen.DELETE -> DeletePlaylistScreen(
                    playlist = playlist,
                    onBack = { currentScreen = BottomSheetScreen.MAIN },
                    onDelete = {
                        playlist?.id?.let { 
                            playMusicViewModel?.deletePlaylist(it) {
                                onDismiss()
                                onDeleteComplete()
                            }
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun MainOptionsScreen(
    playlist: Playlist?,
    isOwner: Boolean,
    onNavigate: (BottomSheetScreen) -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 32.dp, top = 8.dp)
    ) {
        Text(
            text = "Playlist Options",
            fontFamily = AppFont.Poppins,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
        )
        
        HorizontalDivider(color = Color.White.copy(0.1f), modifier = Modifier.padding(bottom = 8.dp))

        if (isOwner) {
            // Edit Profile Playlist
            OptionRow(
                icon = Icons.Default.Edit,
                title = "Edit Playlist Profile",
                onClick = { onNavigate(BottomSheetScreen.EDIT) }
            )

            // Change Visibility
            OptionRow(
                icon = Icons.Default.Visibility,
                title = "Change Visibility",
                subtitle = "Current: ${playlist?.visibility?.replaceFirstChar { it.uppercase() } ?: "Public"}",
                onClick = { onNavigate(BottomSheetScreen.VISIBILITY) }
            )
        }

        // Add Friend to Playlist
        OptionRow(
            icon = Icons.Default.GroupAdd,
            title = "Add Friend to Playlist",
            subtitle = "Coming Soon",
            onClick = { onDismiss() }
        )
        
        if (isOwner) {
            HorizontalDivider(color = Color.White.copy(0.1f), modifier = Modifier.padding(vertical = 8.dp))
            
            // Delete Playlist
            OptionRow(
                icon = Icons.Default.Delete,
                title = "Delete Playlist",
                iconTint = Color(0xFFE74C3C), // Red
                textColor = Color(0xFFE74C3C),
                onClick = { onNavigate(BottomSheetScreen.DELETE) }
            )
        }
    }
}

@Composable
fun VisibilityScreen(
    playlist: Playlist?,
    onBack: () -> Unit,
    onSave: (String) -> Unit
) {
    var selectedVisibility by remember { mutableStateOf(playlist?.visibility ?: "public") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 32.dp, top = 8.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Text(
                text = "Change Visibility",
                fontFamily = AppFont.Poppins,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = Color.White,
            )
        }
        
        HorizontalDivider(color = Color.White.copy(0.1f), modifier = Modifier.padding(bottom = 8.dp))

        // Public Option
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { selectedVisibility = "public" }
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = selectedVisibility == "public",
                onClick = { selectedVisibility = "public" },
                colors = RadioButtonDefaults.colors(selectedColor = Color.White, unselectedColor = Color.White.copy(0.5f))
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text("Public", fontFamily = AppFont.Poppins, fontSize = 16.sp, color = Color.White)
                Text("Anyone can view this playlist", fontFamily = AppFont.Poppins, fontSize = 12.sp, color = Color.White.copy(0.6f))
            }
        }

        // Private Option
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { selectedVisibility = "private" }
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = selectedVisibility == "private",
                onClick = { selectedVisibility = "private" },
                colors = RadioButtonDefaults.colors(selectedColor = Color.White, unselectedColor = Color.White.copy(0.5f))
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text("Private", fontFamily = AppFont.Poppins, fontSize = 16.sp, color = Color.White)
                Text("Only you and invited friends can view", fontFamily = AppFont.Poppins, fontSize = 12.sp, color = Color.White.copy(0.6f))
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { onSave(selectedVisibility) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .height(50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.White),
            shape = RoundedCornerShape(25.dp)
        ) {
            Text("Save", color = Color.Black, fontFamily = AppFont.Poppins, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProfileScreen(
    playlist: Playlist?,
    onBack: () -> Unit,
    onSave: (String, String, String) -> Unit
) {
    var title by remember { mutableStateOf(playlist?.title ?: "") }
    var description by remember { mutableStateOf(playlist?.description ?: "") }
    var coverUrl by remember { mutableStateOf(playlist?.coverUrl ?: "") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 32.dp, top = 8.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Text(
                text = "Edit Playlist Profile",
                fontFamily = AppFont.Poppins,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = Color.White,
            )
        }
        
        HorizontalDivider(color = Color.White.copy(0.1f), modifier = Modifier.padding(bottom = 16.dp))

        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("Playlist Name", color = Color.White.copy(0.6f)) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color.White,
                unfocusedBorderColor = Color.White.copy(0.3f),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                cursorColor = Color.White
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = description,
            onValueChange = { description = it },
            label = { Text("Description (Optional)", color = Color.White.copy(0.6f)) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color.White,
                unfocusedBorderColor = Color.White.copy(0.3f),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                cursorColor = Color.White
            )
        )

        Spacer(modifier = Modifier.height(16.dp))
        
        OutlinedTextField(
            value = coverUrl,
            onValueChange = { coverUrl = it },
            label = { Text("Cover Image URL (Optional)", color = Color.White.copy(0.6f)) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color.White,
                unfocusedBorderColor = Color.White.copy(0.3f),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                cursorColor = Color.White
            )
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = { onSave(title, description, coverUrl) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .height(50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.White),
            shape = RoundedCornerShape(25.dp),
            enabled = title.isNotBlank()
        ) {
            Text("Save Changes", color = Color.Black, fontFamily = AppFont.Poppins, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeletePlaylistScreen(
    playlist: Playlist?,
    onBack: () -> Unit,
    onDelete: () -> Unit
) {
    var confirmText by remember { mutableStateOf("") }
    val playlistName = playlist?.title ?: ""

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 32.dp, top = 8.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Text(
                text = "Delete Playlist",
                fontFamily = AppFont.Poppins,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = Color.White,
            )
        }
        
        HorizontalDivider(color = Color.White.copy(0.1f), modifier = Modifier.padding(bottom = 16.dp))

        Text(
            text = "Are you absolutely sure you want to delete this playlist? This action cannot be undone.",
            fontFamily = AppFont.Poppins,
            fontSize = 14.sp,
            color = Color.White.copy(0.8f),
            modifier = Modifier.padding(horizontal = 24.dp)
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "To confirm, type \"$playlistName\" in the box below:",
            fontFamily = AppFont.Poppins,
            fontSize = 14.sp,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 24.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = confirmText,
            onValueChange = { confirmText = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFFE74C3C),
                unfocusedBorderColor = Color.White.copy(0.3f),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                cursorColor = Color(0xFFE74C3C)
            )
        )

        Spacer(modifier = Modifier.height(32.dp))

        val isMatch = confirmText == playlistName
        Button(
            onClick = onDelete,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .height(50.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFE74C3C),
                disabledContainerColor = Color(0xFFE74C3C).copy(0.3f)
            ),
            shape = RoundedCornerShape(25.dp),
            enabled = isMatch
        ) {
            Text("Delete Permanent", color = Color.White, fontFamily = AppFont.Poppins, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}

@Composable
fun OptionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String? = null,
    iconTint: Color = Color.White,
    textColor: Color = Color.White,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = iconTint,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(
                text = title,
                fontFamily = AppFont.Poppins,
                fontSize = 16.sp,
                color = textColor
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    fontFamily = AppFont.Poppins,
                    fontSize = 12.sp,
                    color = textColor.copy(0.6f)
                )
            }
        }
    }
}
