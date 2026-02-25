package com.example.remusic.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ExitToApp
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import coil.compose.AsyncImage
import com.example.remusic.BuildConfig
import com.example.remusic.data.UserManager
import com.example.remusic.navigation.ProfileRoute
import com.example.remusic.ui.theme.AppFont
import com.example.remusic.viewmodel.StorageCacheViewModel
import com.example.remusic.viewmodel.playmusic.PlayMusicViewModel
import com.example.remusic.utils.extractGradientColorsFromImageUrl
import kotlinx.coroutines.delay
import com.example.remusic.R

@Composable
fun ProfileScreen(
    navController: NavController,
    playMusicViewModel: PlayMusicViewModel,
    storageCacheViewModel: StorageCacheViewModel = viewModel()
) {
    val profileNavController = rememberNavController()
    val playerUiState by playMusicViewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        storageCacheViewModel.loadCacheSizes(context)
    }

    // --- Consume pending artist navigation from PlayMusic "Lihat Playlist" via SharedFlow ---
    LaunchedEffect(Unit) {
        playMusicViewModel.artistNavigationFlow.collect { artistId ->
            if (artistId != null && playerUiState.previousTab == "profile") {
                playMusicViewModel.consumePendingArtistNavigation()
                profileNavController.navigate(
                    ProfileRoute.createRoute(id = artistId, type = "ARTIST")
                ) {
                    launchSingleTop = true
                }
            }
        }
    }

    NavHost(
        navController = profileNavController,
        startDestination = ProfileRoute.MAIN,
        modifier = Modifier.fillMaxSize()
    ) {
        composable(ProfileRoute.MAIN) {
            ProfileMainContent(
                navController = navController,
                playMusicViewModel = playMusicViewModel,
                storageCacheViewModel = storageCacheViewModel,
                onEditProfileClick = {
                    profileNavController.navigate(ProfileRoute.EDIT_PROFILE)
                },
                onSecurityClick = {
                    profileNavController.navigate(ProfileRoute.SECURITY)
                },
                onStorageCacheClick = {
                    profileNavController.navigate(ProfileRoute.STORAGE_CACHE)
                },
                onNotificationClick = {
                    profileNavController.navigate(ProfileRoute.NOTIFICATION)
                },
                onAboutClick = {
                    profileNavController.navigate(ProfileRoute.ABOUT)
                },
                onPrivacyPolicyClick = {
                    profileNavController.navigate(ProfileRoute.PRIVACY_POLICY)
                },
                onHelpCenterClick = {
                    profileNavController.navigate(ProfileRoute.HELP_CENTER)
                }
            )
        }
        composable(ProfileRoute.EDIT_PROFILE) {
            EditProfileScreen(
                onNavigateBack = { profileNavController.popBackStack() }
            )
        }
        composable(ProfileRoute.SECURITY) {
            SecurityScreen(
                onNavigateBack = { profileNavController.popBackStack() }
            )
        }
        composable(ProfileRoute.STORAGE_CACHE) {
            StorageCacheScreen(
                onNavigateBack = { profileNavController.popBackStack() },
                viewModel = storageCacheViewModel
            )
        }
        composable(ProfileRoute.NOTIFICATION) {
            NotificationScreen(
                navController = profileNavController
            )
        }
        composable(ProfileRoute.ABOUT) {
            AboutScreen(
                onNavigateBack = { profileNavController.popBackStack() }
            )
        }
        composable(ProfileRoute.PRIVACY_POLICY) {
            PrivacyPolicyScreen(
                onNavigateBack = { profileNavController.popBackStack() }
            )
        }
        composable(ProfileRoute.HELP_CENTER) {
            HelpCenterScreen(
                onNavigateBack = { profileNavController.popBackStack() }
            )
        }
        composable(
            route = ProfileRoute.PLAYLIST_DETAIL,
            arguments = listOf(
                navArgument(ProfileRoute.ARGS_ID) { type = NavType.StringType },
                navArgument(ProfileRoute.ARGS_PLAYLIST_TYPE) { type = NavType.StringType; defaultValue = "AUTO" }
            )
        ) { backStackEntry ->
            val id = backStackEntry.arguments?.getString(ProfileRoute.ARGS_ID) ?: ""
            val typeString = backStackEntry.arguments?.getString(ProfileRoute.ARGS_PLAYLIST_TYPE) ?: "AUTO"
            val playlistType = try {
                PlaylistType.valueOf(typeString) 
            } catch (e: Exception) {
                PlaylistType.AUTO
            }
            PlaylistDetailScreen(
                songs = emptyList(),
                playlistName = "Artist",
                playlistCoverUrl = "",
                playlistType = playlistType,
                playlistId = id,
                playMusicViewModel = playMusicViewModel
            )
        }
        composable("request_song") {
            com.example.remusic.ui.screen.RequestSongScreen(navController = profileNavController, playMusicViewModel = playMusicViewModel)
        }
        composable(ProfileRoute.CREATE_PLAYLIST) {
            CreatePlaylistScreen(
                onNavigateBack = { profileNavController.popBackStack() }
            )
        }
    }
}

@Composable
fun ProfileMainContent(
    navController: NavController,
    playMusicViewModel: PlayMusicViewModel,
    storageCacheViewModel: StorageCacheViewModel,
    onEditProfileClick: () -> Unit,
    onSecurityClick: () -> Unit,
    onStorageCacheClick: () -> Unit,
    onNotificationClick: () -> Unit,
    onAboutClick: () -> Unit,
    onPrivacyPolicyClick: () -> Unit,
    onHelpCenterClick: () -> Unit
) {
    val userProfile = UserManager.currentUser
    val totalCacheSize by storageCacheViewModel.totalCacheSizeMB.collectAsState()
    var isVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(100)
        isVisible = true
    }

    val context = LocalContext.current
    var dominantColors by remember { 
        mutableStateOf(
            listOf(Color(0xFF2B0B16), Color(0xFF0A0A0A), Color(0xFF000000))
        ) 
    }

    LaunchedEffect(userProfile?.photoUrl) {
        if (userProfile?.photoUrl != null) {
            val colors = extractGradientColorsFromImageUrl(context, userProfile.photoUrl)
            if (colors.isNotEmpty()) {
                // Gunakan 2 warna pertama + hitam untuk background gradient
                val c1 = colors.getOrNull(0) ?: Color(0xFF2B0B16)
                val c2 = colors.getOrNull(1) ?: Color(0xFF0A0A0A)
                dominantColors = listOf(c1, c2, Color(0xFF000000))
            }
        }
    }

    // Gradient dinamis dari warna profil (atau default)
    val backgroundBrush = Brush.verticalGradient(
        colors = dominantColors,
        startY = 0f,
        endY = 1200f
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundBrush),
        contentAlignment = Alignment.Center
    ) {
        if (userProfile != null) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                item { Spacer(modifier = Modifier.height(60.dp)) }

                // --- PROFILE HEADER (BESAR & IMMERSIVE) ---
                item {
                    AnimatedVisibility(
                        visible = isVisible,
                        enter = fadeIn(animationSpec = tween(600)) + slideInVertically(initialOffsetY = { -50 })
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            // Foto Profil dengan efek glowing/border elegan
                            Box(
                                modifier = Modifier
                                    .size(136.dp)
                                    .background(
                                        Brush.sweepGradient(
                                            listOf(
                                                dominantColors.firstOrNull() ?: Color(0xFFE91E63), 
                                                dominantColors.getOrNull(1) ?: Color(0xFF9C27B0), 
                                                dominantColors.firstOrNull() ?: Color(0xFFE91E63)
                                            )
                                        ),
                                        CircleShape
                                    )
                                    .padding(3.dp) // Ketebalan border gradient
                            ) {
                                AsyncImage(
                                    model = userProfile.photoUrl ?: R.drawable.img_placeholder,
                                    contentDescription = "Profile Picture",
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(CircleShape)
                                        .border(2.dp, Color.Black, CircleShape), // Inner border
                                    contentScale = ContentScale.Crop,
                                    placeholder = painterResource(R.drawable.img_placeholder),
                                    error = painterResource(R.drawable.img_placeholder)
                                )
                            }
                            Spacer(modifier = Modifier.height(20.dp))
                            
                            // Nama User
                            Text(
                                text = userProfile.displayName ?: "Unknown User",
                                color = Color.White,
                                fontSize = 30.sp,
                                fontFamily = AppFont.Poppins,
                                fontWeight = FontWeight.ExtraBold,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            // Tipe Akun & Email
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                // Badge ala Spotify Premium/Free
                                Box(
                                    modifier = Modifier
                                        .background(
                                            if (userProfile.role.equals("premium", true)) Color(0xFFFFD700).copy(alpha = 0.2f) 
                                            else Color.White.copy(alpha = 0.1f), 
                                            RoundedCornerShape(6.dp)
                                        )
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = userProfile.role.uppercase(),
                                        color = if (userProfile.role.equals("premium", true)) Color(0xFFFFD700) else Color.White,
                                        fontSize = 10.sp,
                                        fontFamily = AppFont.Helvetica,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.sp
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = userProfile.email ?: "",
                                    color = Color.White.copy(0.6f),
                                    fontSize = 14.sp,
                                    fontFamily = AppFont.Helvetica
                                )
                            }
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(40.dp)) }

                // --- SECTION 1: AKUN ---
                item {
                    AnimatedVisibility(
                        visible = isVisible,
                        enter = fadeIn(animationSpec = tween(600, delayMillis = 100)) + slideInVertically(initialOffsetY = { 50 })
                    ) {
                        SettingsGroup(title = "Akun") {
                            SettingsRow(
                                icon = Icons.Rounded.Person,
                                iconBgColor = Color(0xFF2196F3), // Biru
                                title = "Edit Profil",
                                onClick = onEditProfileClick
                            )
                            DividerItem()
                            SettingsRow(
                                icon = Icons.Rounded.Lock,
                                iconBgColor = Color(0xFF4CAF50), // Hijau
                                title = "Keamanan & Kata Sandi",
                                onClick = onSecurityClick
                            )
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(24.dp)) }

                // --- SECTION 2: PREFERENSI & DATA ---
                item {
                    AnimatedVisibility(
                        visible = isVisible,
                        enter = fadeIn(animationSpec = tween(600, delayMillis = 200)) + slideInVertically(initialOffsetY = { 50 })
                    ) {
                        SettingsGroup(title = "Preferensi") {
                            SettingsRow(
                                icon = Icons.Rounded.Palette,
                                iconBgColor = Color(0xFF9C27B0), // Ungu
                                title = "Tampilan",
                                value = "Gelap",
                                onClick = { /* TODO */ }
                            )
                            DividerItem()
                            SettingsRow(
                                icon = Icons.Rounded.Notifications,
                                iconBgColor = Color(0xFFFF9800), // Orange
                                title = "Notifikasi",
                                onClick = onNotificationClick
                            )
                            DividerItem()
                            SettingsRow(
                                icon = Icons.Rounded.DeleteOutline,
                                iconBgColor = Color(0xFF607D8B), // Greyish Blue
                                title = "Penyimpanan & Cache",
                                value = StorageCacheViewModel.formatSize(totalCacheSize),
                                onClick = onStorageCacheClick
                            )
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(24.dp)) }

                // --- SECTION 3: BANTUAN & TENTANG ---
                item {
                    AnimatedVisibility(
                        visible = isVisible,
                        enter = fadeIn(animationSpec = tween(600, delayMillis = 300)) + slideInVertically(initialOffsetY = { 50 })
                    ) {
                        SettingsGroup(title = "Dukungan") {
                            SettingsRow(
                                icon = Icons.AutoMirrored.Rounded.HelpOutline,
                                iconBgColor = Color(0xFF00BCD4), // Cyan
                                title = "Pusat Bantuan",
                                onClick = onHelpCenterClick
                            )
                            DividerItem()
                            SettingsRow(
                                icon = Icons.Rounded.PrivacyTip,
                                iconBgColor = Color(0xFF795548), // Coklat
                                title = "Kebijakan Privasi",
                                onClick = onPrivacyPolicyClick
                            )
                            DividerItem()
                            SettingsRow(
                                icon = Icons.Rounded.Info,
                                iconBgColor = Color(0xFFE91E63), // Pink/Magenta
                                title = "Tentang ReMusic",
                                onClick = onAboutClick
                            )
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(32.dp)) }

                // --- LOGOUT BUTTON (DANGER ACTION) ---
                item {
                    AnimatedVisibility(
                        visible = isVisible,
                        enter = fadeIn(animationSpec = tween(600, delayMillis = 400)) + slideInVertically(initialOffsetY = { 50 })
                    ) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    playMusicViewModel.stopPlaybackOnLogout()
                                    com.example.remusic.utils.AuthUtils.logout(context, navController)
                                },
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)) // Warna card sedikit lebih terang dari background
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 18.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.ExitToApp,
                                    contentDescription = "Logout",
                                    tint = Color(0xFFFF4B4B), // Merah
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "Keluar Akun",
                                    color = Color(0xFFFF4B4B),
                                    fontSize = 16.sp,
                                    fontFamily = AppFont.Helvetica,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(40.dp)) }

                // --- APP VERSION (WATERMARK) ---
                item {
                    AnimatedVisibility(
                        visible = isVisible,
                        enter = fadeIn(animationSpec = tween(600, delayMillis = 500))
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "ReMusic",
                                color = Color.White.copy(alpha = 0.4f),
                                fontSize = 16.sp,
                                fontFamily = AppFont.Poppins,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Versi ${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE})",
                                color = Color.White.copy(alpha = 0.2f),
                                fontSize = 12.sp,
                                fontFamily = AppFont.Helvetica
                            )
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(150.dp)) } // Safe area bottom padding for Navigation Bar
            }
        } else {
            CircularProgressIndicator(color = Color(0xFFE91E63))
        }
    }
}

// --- KOMPONEN BANTUAN UNTUK UI PENGATURAN ---

@Composable
fun SettingsGroup(
    title: String,
    content: @Composable () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // Section Title
        Text(
            text = title.uppercase(),
            color = Color.White.copy(alpha = 0.4f),
            fontSize = 12.sp,
            fontFamily = AppFont.Helvetica,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
        )
        // Group Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF161616))
        ) {
            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                content()
            }
        }
    }
}

@Composable
fun SettingsRow(
    icon: ImageVector,
    iconBgColor: Color,
    title: String,
    value: String? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Colorful Icon Background (Ala iOS/Modern Android Settings)
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(iconBgColor.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = iconBgColor,
                modifier = Modifier.size(20.dp)
            )
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Text(
            text = title,
            color = Color.White.copy(alpha = 0.9f),
            fontSize = 15.sp,
            fontFamily = AppFont.Helvetica,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )
        
        if (value != null) {
            Text(
                text = value,
                color = Color.White.copy(alpha = 0.4f),
                fontSize = 13.sp,
                fontFamily = AppFont.Helvetica
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.2f),
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
fun DividerItem() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 72.dp, end = 20.dp), // Start padding agar sejajar dengan teks, bukan icon
        thickness = 1.dp,
        color = Color.White.copy(alpha = 0.05f)
    )
}