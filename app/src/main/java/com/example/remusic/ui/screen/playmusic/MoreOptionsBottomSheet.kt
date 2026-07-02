package com.example.remusic.ui.screen.playmusic

import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.PlaylistAdd
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.FormatAlignCenter
import androidx.compose.material.icons.outlined.FormatAlignLeft
import androidx.compose.material.icons.outlined.FormatAlignRight
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.remusic.R
import com.example.remusic.data.model.SongWithArtist
import com.example.remusic.data.model.displayArtistName
import com.example.remusic.ui.theme.AppFont
import com.example.remusic.utils.GoogleFontDownloader
import kotlinx.coroutines.launch

enum class LyricsBackgroundStyle(val displayName: String) {
    LINEAR_GRADIENT("Linear Gradient"),
    MESH_GRADIENT("Mesh Gradient (Cloud)"),
    LIQUID_GRADIENT("Liquid Gradient"),
    BLURRED_COVER("Blurred Cover")
}

// Data class for Lyrics Config
data class LyricsConfig(
    val fontFamily: LyricsFontFamily = LyricsFontFamily.POPPINS,
    val fontSize: Int = 21, // dp
    val align: LyricsAlign = LyricsAlign.LEFT,
    val autoScaleIfNoTranslation: Boolean = false,
    val scaleFactor: Int = 1, // +1, +2, +3
    val markPassedLyrics: Boolean = true,
    val translateFontSize: Float = 15.5f,
    val translationFontWeight: LyricsFontWeight = LyricsFontWeight.REGULAR,
    val mainFontWeight: LyricsFontWeight = LyricsFontWeight.BOLD,
    val clickLyricsToSeek: Boolean = true,
    val backgroundStyle: LyricsBackgroundStyle = LyricsBackgroundStyle.LIQUID_GRADIENT,
    val animateActiveLyric: Boolean = true,
    val inactiveLyricsColor: InactiveLyricsColor = InactiveLyricsColor.FADED_WHITE
)

enum class InactiveLyricsColor(val displayName: String) {
    FADED_WHITE("Putih Pudar"),
    BLACK("Hitam")
}

enum class LyricsFontWeight(val displayName: String, val weight: FontWeight) {
    LIGHT("Light", FontWeight.Light),
    REGULAR("Regular", FontWeight.Normal),
    MEDIUM("Medium", FontWeight.Medium),
    SEMIBOLD("SemiBold", FontWeight.SemiBold),
    BOLD("Bold", FontWeight.Bold),
    BLACK("Black", FontWeight.Black)
}

enum class LyricsFontFamily(
    val displayName: String,
    val isOnline: Boolean = false,
    val googleFontName: String = ""
) {
    // --- Font Bawaan (Offline) ---
    POPPINS("Poppins"),
    ROBOTO("Roboto"),
    HELVETICA("Helvetica"),

    // --- Google Fonts (Online, bisa diunduh offline) ---
    NUNITO("Nunito",           isOnline = true, googleFontName = "Nunito"),
    LATO("Lato",               isOnline = true, googleFontName = "Lato"),
    MONTSERRAT("Montserrat",   isOnline = true, googleFontName = "Montserrat"),
    INTER("Inter",             isOnline = true, googleFontName = "Inter"),
    PLAYFAIR_DISPLAY("Playfair Display", isOnline = true, googleFontName = "Playfair_Display"),
    RALEWAY("Raleway",         isOnline = true, googleFontName = "Raleway"),
    JOSEFIN_SANS("Josefin Sans", isOnline = true, googleFontName = "Josefin_Sans"),
    PACIFICO("Pacifico",       isOnline = true, googleFontName = "Pacifico"),
    FJALLA_ONE("Fjalla One",   isOnline = true, googleFontName = "Fjalla_One"),
    GOOGLE_SANS("Google Sans", isOnline = true, googleFontName = "Google_Sans"),
    UBUNTU("Ubuntu",           isOnline = true, googleFontName = "Ubuntu"),
    BUNGEE("Bungee",           isOnline = true, googleFontName = "Bungee"),
    PASSION_ONE("Passion One", isOnline = true, googleFontName = "Passion_One"),
    QUANDO("Quando",           isOnline = true, googleFontName = "Quando"),
    
    // --- Tambahan Font Populer Lainnya ---
    OPEN_SANS("Open Sans",     isOnline = true, googleFontName = "Open_Sans"),
    OSWALD("Oswald",           isOnline = true, googleFontName = "Oswald"),
    MERRIWEATHER("Merriweather", isOnline = true, googleFontName = "Merriweather"),
    DANCING_SCRIPT("Dancing Script", isOnline = true, googleFontName = "Dancing_Script"),
    CAVEAT("Caveat",           isOnline = true, googleFontName = "Caveat"),
    SATISFY("Satisfy",         isOnline = true, googleFontName = "Satisfy"),
    LOBSTER("Lobster",         isOnline = true, googleFontName = "Lobster"),
    COMFORTAA("Comfortaa",     isOnline = true, googleFontName = "Comfortaa"),
    KANIT("Kanit",             isOnline = true, googleFontName = "Kanit"),
    TEKO("Teko",               isOnline = true, googleFontName = "Teko"),
    ABRIL_FATFACE("Abril Fatface", isOnline = true, googleFontName = "Abril_Fatface"),
    GREAT_VIBES("Great Vibes", isOnline = true, googleFontName = "Great_Vibes"),
    LEXEND("Lexend",           isOnline = true, googleFontName = "Lexend"),
    PRESS_START_2P("Press Start 2P", isOnline = true, googleFontName = "Press_Start_2P"),
    RIGHTEOUS("Righteous",     isOnline = true, googleFontName = "Righteous"),
    BEBAS_NEUE("Bebas Neue",   isOnline = true, googleFontName = "Bebas_Neue"),
    TITIPILLIUM_WEB("Titillium Web", isOnline = true, googleFontName = "Titillium_Web"),
    SOURCE_CODE_PRO("Source Code Pro", isOnline = true, googleFontName = "Source_Code_Pro"),
}

enum class LyricsAlign {
    LEFT, CENTER, RIGHT
}

// --- UI Helper Colors ---
private val SheetBackgroundColor = Color(0xFF121212)
private val CardBackgroundColor = Color(0xFF1C1C1E)
private val TextPrimaryColor = Color.White
private val TextSecondaryColor = Color(0xFFA0A0A5)
private val DividerColor = Color.White.copy(alpha = 0.05f)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MoreOptionsBottomSheet(
    sheetState: SheetState,
    songWithArtist: SongWithArtist?,
    onDismiss: () -> Unit,
    // Callbacks for options
    onShare: () -> Unit = {},
    onAddToPlaylist: () -> Unit = {},
    onAddToLiked: () -> Unit = {},
    onDownload: () -> Unit = {},
    onSetSleepTimer: () -> Unit = {},
    // Lyrics Config
    lyricsConfig: LyricsConfig = LyricsConfig(),
    onLyricsConfigChange: (LyricsConfig) -> Unit = {},
    // Background Style
    gradientStyle: com.example.remusic.data.preferences.GradientStyle = com.example.remusic.data.preferences.GradientStyle.AUTO,
    onGradientStyleChange: (com.example.remusic.data.preferences.GradientStyle) -> Unit = {},
    // Gradient Color Positions
    gradientTopColorIndex: Int = 0,
    onGradientTopColorIndexChange: (Int) -> Unit = {},
    gradientBottomColorIndex: Int = 1,
    onGradientBottomColorIndexChange: (Int) -> Unit = {},
    // Data Saver
    isDataSaverModeEnabled: Boolean = false,
    onDataSaverModeChange: (Boolean) -> Unit = {},
    // Playback Speed
    playbackSpeed: Float = 1.0f,
    onPlaybackSpeedChange: (Float) -> Unit = {},
    // Player Theme
    playerBackgroundStyle: com.example.remusic.data.preferences.PlayerBackgroundStyle = com.example.remusic.data.preferences.PlayerBackgroundStyle.LIQUID_GRADIENT,
    onPlayerBackgroundStyleChange: (com.example.remusic.data.preferences.PlayerBackgroundStyle) -> Unit = {},
    isThemeAppliedToQueue: Boolean = true,
    onThemeAppliedToQueueChange: (Boolean) -> Unit = {},
    isThemeAppliedToNowPlaying: Boolean = true,
    onThemeAppliedToNowPlayingChange: (Boolean) -> Unit = {},
    isThemeAppliedToLyrics: Boolean = true,
    onThemeAppliedToLyricsChange: (Boolean) -> Unit = {},
    // Font Picker Navigation
    onOpenFontPicker: () -> Unit = {}
) {
    val context = LocalContext.current
    val isOfflineSong = songWithArtist?.song?.id?.startsWith("offline_") == true

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SheetBackgroundColor,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .width(48.dp)
                    .height(5.dp)
                    .background(Color.White.copy(0.2f), RoundedCornerShape(2.5.dp))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 40.dp)
        ) {
            // --- 1. HEADER SECTION ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = songWithArtist?.song?.coverUrl,
                    contentDescription = "Song Cover",
                    modifier = Modifier
                        .size(64.dp)
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop,
                    placeholder = painterResource(R.drawable.img_placeholder),
                    error = painterResource(R.drawable.img_placeholder)
                )

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = songWithArtist?.song?.title ?: "Unknown Title",
                        color = TextPrimaryColor,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        modifier = Modifier
                            .fillMaxWidth()
                            .basicMarquee(
                                animationMode = androidx.compose.foundation.MarqueeAnimationMode.Immediately,
                                velocity = 40.dp,
                                initialDelayMillis = 2000,
                                repeatDelayMillis = 3000,
                                iterations = Int.MAX_VALUE
                            )
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = songWithArtist?.displayArtistName ?: "Unknown Artist",
                        color = TextSecondaryColor,
                        fontFamily = AppFont.Helvetica,
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // --- 2. MAIN ACTIONS SECTION ---
            SectionCard {
                if (!isOfflineSong) {
                    MenuOptionItem(
                        title = "Bagikan",
                        icon = Icons.Outlined.Share,
                        onClick = { Toast.makeText(context, "Fitur belum tersedia", Toast.LENGTH_SHORT).show() }
                    )
                    ItemDivider()
                    MenuOptionItem(
                        title = "Tambahkan ke playlist",
                        icon = Icons.AutoMirrored.Outlined.PlaylistAdd,
                        onClick = onAddToPlaylist
                    )
                    ItemDivider()
                    MenuOptionItem(
                        title = "Tambahkan ke Lagu yang Disukai",
                        icon = Icons.Outlined.FavoriteBorder,
                        onClick = onAddToLiked
                    )
                    ItemDivider()
                }
                MenuOptionItem(
                    title = "Setel waktu tidur",
                    icon = Icons.Outlined.Bedtime,
                    onClick = {
                        onSetSleepTimer()
                        onDismiss()
                    }
                )
                if (!isOfflineSong) {
                    ItemDivider()
                    MenuOptionItem(
                        title = "Unduh",
                        icon = Icons.Outlined.Download,
                        onClick = {
                            onDownload()
                            onDismiss()
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // --- 3. PLAYBACK & NETWORK SECTION ---
            SectionTitle(
                title = "Playback & Network",
                actionText = if (playbackSpeed != 1.0f) "Reset" else null,
                onActionClick = { onPlaybackSpeedChange(1.0f) }
            )
            SectionCard {
                // Playback Speed
                Column(modifier = Modifier.padding(top = 16.dp, bottom = 12.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Kecepatan Putar",
                            color = TextPrimaryColor,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = if (playbackSpeed == 1.0f) "Normal" else "${playbackSpeed}x",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .background(Color.White.copy(0.1f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    val speedOptions = listOf(0.5f, 0.6f, 0.7f, 0.75f, 0.8f, 0.85f, 0.9f, 0.95f, 1.0f, 1.05f, 1.1f, 1.15f, 1.2f, 1.25f, 1.3f, 1.4f, 1.5f, 1.75f, 2.0f)
                    val currentIndex = speedOptions.indexOf(playbackSpeed).coerceAtLeast(0)

                    Slider(
                        value = currentIndex.toFloat(),
                        onValueChange = { index ->
                            onPlaybackSpeedChange(speedOptions[index.toInt()])
                        },
                        valueRange = 0f..(speedOptions.size - 1).toFloat(),
                        steps = speedOptions.size - 2,
                        modifier = Modifier.padding(horizontal = 24.dp),
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = Color.White,
                            inactiveTrackColor = Color.White.copy(0.2f),
                            activeTickColor = Color.Transparent,
                            inactiveTickColor = Color.Transparent
                        )
                    )

                    // Speed Labels Alignment
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                    ) {
                        // Left Label (0.5x)
                        Text(
                            text = "0.5x",
                            color = if (playbackSpeed == 0.5f) Color.White else TextSecondaryColor,
                            fontSize = 11.sp,
                            fontWeight = if (playbackSpeed == 0.5f) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.align(Alignment.CenterStart)
                        )

                        // Center Label (Normal) - Index 8 of 18 (total 19 points)
                        val normalBias = (8f / 18f) * 2 - 1
                        Text(
                            text = "Normal",
                            color = if (playbackSpeed == 1.0f) Color.White else TextSecondaryColor,
                            fontSize = 11.sp,
                            fontWeight = if (playbackSpeed == 1.0f) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.align(BiasAlignment(normalBias, 0f))
                        )

                        // Right Label (2.0x)
                        Text(
                            text = "2.0x",
                            color = if (playbackSpeed == 2.0f) Color.White else TextSecondaryColor,
                            fontSize = 11.sp,
                            fontWeight = if (playbackSpeed == 2.0f) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.align(Alignment.CenterEnd)
                        )
                    }
                }

                ItemDivider()

                // Data Saver
                SettingToggleRow(
                    title = "Mode Hemat Data",
                    subtitle = "Mematikan canvas video di player",
                    checked = isDataSaverModeEnabled,
                    onCheckedChange = { onDataSaverModeChange(it) }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // --- 4. APPEARANCE SECTION ---
            SectionTitle(
                title = "Appearance",
                actionText = if (gradientStyle != com.example.remusic.data.preferences.GradientStyle.AUTO || gradientTopColorIndex != 0 || gradientBottomColorIndex != 1) "Reset" else null,
                onActionClick = {
                    onGradientStyleChange(com.example.remusic.data.preferences.GradientStyle.AUTO)
                    onGradientTopColorIndexChange(0)
                    onGradientBottomColorIndexChange(1)
                }
            )
            SectionCard {
                // Gradient Style
                Column(modifier = Modifier.padding(vertical = 12.dp)) {
                    Text(
                        text = "Background Style",
                        color = TextPrimaryColor,
                        fontSize = 15.sp,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp, top = 0.dp)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        com.example.remusic.data.preferences.GradientStyle.values().forEach { style ->
                            CustomChip(
                                selected = gradientStyle == style,
                                text = style.displayName,
                                onClick = { onGradientStyleChange(style) }
                            )
                        }
                    }
                }
                
                ItemDivider()

                val isInactiveBlack = lyricsConfig.inactiveLyricsColor == InactiveLyricsColor.BLACK

                // Top Color
                Column(modifier = Modifier.padding(vertical = 12.dp)) {
                    Text(
                        text = "Top Color Position",
                        color = TextPrimaryColor,
                        fontSize = 15.sp,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp, top = 0.dp)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        (0..3).forEach { index ->
                            CustomChip(
                                selected = gradientTopColorIndex == index,
                                text = "Color ${index + 1}",
                                enabled = if (isInactiveBlack) index < 2 else true,
                                onClick = { onGradientTopColorIndexChange(index) }
                            )
                        }
                    }
                }

                ItemDivider()

                // Bottom Color
                Column(modifier = Modifier.padding(vertical = 12.dp)) {
                    Text(
                        text = "Bottom Color Position",
                        color = TextPrimaryColor,
                        fontSize = 15.sp,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp, top = 0.dp)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        (0..3).forEach { index ->
                            CustomChip(
                                selected = gradientBottomColorIndex == index,
                                text = "Color ${index + 1}",
                                enabled = if (isInactiveBlack) index < 2 else true,
                                onClick = { onGradientBottomColorIndexChange(index) }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // --- 5. PLAYER THEME SECTION ---
            SectionTitle(title = "Player Background Theme")
            SectionCard {
                var themeMenuExpanded by remember { mutableStateOf(false) }
                SettingDropdownRow(
                    title = "Background",
                    value = playerBackgroundStyle.displayName,
                    expanded = themeMenuExpanded,
                    onExpandedChange = { themeMenuExpanded = it },
                    items = com.example.remusic.data.preferences.PlayerBackgroundStyle.values().toList(),
                    itemLabel = { it.displayName },
                    onItemSelected = { onPlayerBackgroundStyleChange(it) }
                )

                if (playerBackgroundStyle != com.example.remusic.data.preferences.PlayerBackgroundStyle.LIQUID_GRADIENT) {
                    ItemDivider()
                    SettingToggleRow(
                        title = "Terapkan di Queue",
                        subtitle = "Tema aktif di layar antrian lagu",
                        checked = isThemeAppliedToQueue,
                        onCheckedChange = { onThemeAppliedToQueueChange(it) }
                    )
                    ItemDivider()
                    SettingToggleRow(
                        title = "Terapkan di Now Playing",
                        subtitle = "Tema aktif di layar putar lagu",
                        checked = isThemeAppliedToNowPlaying,
                        onCheckedChange = { onThemeAppliedToNowPlayingChange(it) }
                    )
                    val isBlurredDisabled = playerBackgroundStyle == com.example.remusic.data.preferences.PlayerBackgroundStyle.BLURRED_COVER && lyricsConfig.inactiveLyricsColor == InactiveLyricsColor.BLACK
                    SettingToggleRow(
                        title = "Terapkan di Lyrics",
                        subtitle = if (isBlurredDisabled) "Tidak tersedia untuk Blurred Cover dengan warna inactive Hitam" else "Tema aktif di layar lirik lagu",
                        checked = if (isBlurredDisabled) false else isThemeAppliedToLyrics,
                        enabled = !isBlurredDisabled,
                        onCheckedChange = { onThemeAppliedToLyricsChange(it) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // --- 6. LYRICS SETTINGS SECTION ---
            SectionTitle(
                title = "Lyrics Settings",
                actionText = if (lyricsConfig != LyricsConfig()) "Reset" else null,
                onActionClick = { onLyricsConfigChange(LyricsConfig()) }
            )
            SectionCard {
                // Font Family — Tap untuk buka LyricsFontBottomSheet
                FontNavRow(
                    selectedFontName = lyricsConfig.fontFamily.displayName,
                    onClick = {
                        onOpenFontPicker()
                        onDismiss()
                    }
                )

                ItemDivider()

                // Alignment
                Column(modifier = Modifier.padding(vertical = 12.dp)) {
                    Text(
                        text = "Alignment",
                        color = TextPrimaryColor,
                        fontSize = 15.sp,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 0.dp, bottom = 12.dp)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        val alignOptions = listOf(
                            LyricsAlign.LEFT to Icons.Outlined.FormatAlignLeft,
                            LyricsAlign.CENTER to Icons.Outlined.FormatAlignCenter,
                            LyricsAlign.RIGHT to Icons.Outlined.FormatAlignRight
                        )
                        alignOptions.forEach { (align, icon) ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (lyricsConfig.align == align) Color.White else Color.White.copy(0.05f))
                                    .clickable { onLyricsConfigChange(lyricsConfig.copy(align = align)) }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = if (lyricsConfig.align == align) Color.Black else Color.White
                                )
                            }
                        }
                    }
                }

                ItemDivider()

                // Auto Scale
                SettingToggleRow(
                    title = "Perbesar jika tidak ada terjemahan",
                    subtitle = "Otomatis memperbesar teks lirik",
                    checked = lyricsConfig.autoScaleIfNoTranslation,
                    onCheckedChange = { onLyricsConfigChange(lyricsConfig.copy(autoScaleIfNoTranslation = it)) }
                )

                // Scale Factor
                if (lyricsConfig.autoScaleIfNoTranslation) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, top = 0.dp, bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(1, 2, 3).forEach { scale ->
                            CustomChip(
                                selected = lyricsConfig.scaleFactor == scale,
                                text = "+$scale",
                                onClick = { onLyricsConfigChange(lyricsConfig.copy(scaleFactor = scale)) }
                            )
                        }
                    }
                }

                ItemDivider()

                // Sliders
                Column(modifier = Modifier.padding(vertical = 12.dp)) {
                    // Original Size
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Ukuran Lirik Asli", color = TextPrimaryColor, fontSize = 15.sp)
                        Text("${lyricsConfig.fontSize}sp", color = TextSecondaryColor, fontSize = 14.sp)
                    }
                    Slider(
                        value = lyricsConfig.fontSize.toFloat(),
                        onValueChange = { onLyricsConfigChange(lyricsConfig.copy(fontSize = it.toInt())) },
                        valueRange = 15f..35f,
                        modifier = Modifier.padding(horizontal = 16.dp),
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = Color.White,
                            inactiveTrackColor = Color.DarkGray
                        )
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Translate Size
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Ukuran Terjemahan", color = TextPrimaryColor, fontSize = 15.sp)
                        Text(String.format("%.1f", lyricsConfig.translateFontSize) + "sp", color = TextSecondaryColor, fontSize = 14.sp)
                    }
                    Slider(
                        value = lyricsConfig.translateFontSize,
                        onValueChange = { onLyricsConfigChange(lyricsConfig.copy(translateFontSize = it)) },
                        valueRange = 10f..30f,
                        modifier = Modifier.padding(horizontal = 16.dp),
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = Color.White,
                            inactiveTrackColor = Color.DarkGray
                        )
                    )
                }

                ItemDivider()

                // Font Weights
                var mainWeightMenuExpanded by remember { mutableStateOf(false) }
                SettingDropdownRow(
                    title = "Ketebalan Lirik Asli",
                    value = lyricsConfig.mainFontWeight.displayName,
                    expanded = mainWeightMenuExpanded,
                    onExpandedChange = { mainWeightMenuExpanded = it },
                    items = LyricsFontWeight.values().toList(),
                    itemLabel = { it.displayName },
                    onItemSelected = { onLyricsConfigChange(lyricsConfig.copy(mainFontWeight = it)) }
                )

                ItemDivider()

                var weightMenuExpanded by remember { mutableStateOf(false) }
                SettingDropdownRow(
                    title = "Ketebalan Terjemahan",
                    value = lyricsConfig.translationFontWeight.displayName,
                    expanded = weightMenuExpanded,
                    onExpandedChange = { weightMenuExpanded = it },
                    items = LyricsFontWeight.values().toList(),
                    itemLabel = { it.displayName },
                    onItemSelected = { onLyricsConfigChange(lyricsConfig.copy(translationFontWeight = it)) }
                )

                ItemDivider()

                // Mark Passed Lyrics
                SettingToggleRow(
                    title = "Tandai lirik yang dilewati",
                    subtitle = null,
                    checked = lyricsConfig.markPassedLyrics,
                    onCheckedChange = { onLyricsConfigChange(lyricsConfig.copy(markPassedLyrics = it)) }
                )

                ItemDivider()

                // Click to seek
                SettingToggleRow(
                    title = "Klik lirik untuk seek",
                    subtitle = "Ketuk baris lirik untuk loncat ke waktu tersebut",
                    checked = lyricsConfig.clickLyricsToSeek,
                    onCheckedChange = { onLyricsConfigChange(lyricsConfig.copy(clickLyricsToSeek = it)) }
                )

                ItemDivider()

                // Animate Active Lyric
                SettingToggleRow(
                    title = "Animasi Lirik Aktif",
                    subtitle = "Perbesar dan beri jarak pada lirik yang sedang diputar",
                    checked = lyricsConfig.animateActiveLyric,
                    onCheckedChange = { onLyricsConfigChange(lyricsConfig.copy(animateActiveLyric = it)) }
                )

                ItemDivider()

                // Inactive Lyrics Color
                var inactiveColorMenuExpanded by remember { mutableStateOf(false) }
                SettingDropdownRow(
                    title = "Warna Lirik Inactive",
                    value = lyricsConfig.inactiveLyricsColor.displayName,
                    expanded = inactiveColorMenuExpanded,
                    onExpandedChange = { inactiveColorMenuExpanded = it },
                    items = InactiveLyricsColor.values().toList(),
                    itemLabel = { it.displayName },
                    onItemSelected = { onLyricsConfigChange(lyricsConfig.copy(inactiveLyricsColor = it)) }
                )
            }
        }
    }
}

// --- REUSABLE UI COMPONENTS (Agar kode lebih bersih) ---

@Composable
private fun FontNavRow(
    selectedFontName: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "Font Lirik",
            color = TextPrimaryColor,
            fontSize = 15.sp
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = selectedFontName,
                color = TextSecondaryColor,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = TextSecondaryColor,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}



@Composable
private fun SectionTitle(title: String, actionText: String? = null, onActionClick: () -> Unit = {}) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            color = TextSecondaryColor,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = AppFont.HelveticaRoundedBold
        )
        if (actionText != null) {
            Text(
                text = actionText,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable(onClick = onActionClick)
                    .padding(horizontal = 6.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun SectionCard(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(CardBackgroundColor)
    ) {
        Column { content() }
    }
}

@Composable
private fun ItemDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp),
        thickness = 1.dp,
        color = DividerColor
    )
}

@Composable
private fun MenuOptionItem(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = TextPrimaryColor,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = title,
            color = TextPrimaryColor,
            fontFamily = AppFont.Helvetica,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun CustomChip(
    selected: Boolean,
    text: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(
                if (selected) Color.White 
                else if (!enabled) Color.White.copy(alpha = 0.02f)
                else Color.White.copy(alpha = 0.08f)
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (selected) Color.Black 
                    else if (!enabled) Color.White.copy(alpha = 0.2f)
                    else Color.White,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
        )
    }
}

@Composable
private fun SettingToggleRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, color = if (enabled) TextPrimaryColor else TextPrimaryColor.copy(alpha = 0.3f), fontSize = 15.sp)
            if (subtitle != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(text = subtitle, color = if (enabled) TextSecondaryColor else TextSecondaryColor.copy(alpha = 0.3f), fontSize = 12.sp, lineHeight = 16.sp)
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.Black,
                checkedTrackColor = Color.White,
                uncheckedThumbColor = Color.LightGray,
                uncheckedTrackColor = Color.DarkGray,
                uncheckedBorderColor = Color.Transparent
            )
        )
    }
}

@Composable
private fun <T> SettingDropdownRow(
    title: String,
    value: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    items: List<T>,
    itemLabel: (T) -> String,
    onItemSelected: (T) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onExpandedChange(true) }
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            color = TextPrimaryColor,
            fontSize = 15.sp
        )

        Box {
            Text(
                text = value,
                color = TextSecondaryColor,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { onExpandedChange(false) },
                modifier = Modifier.background(CardBackgroundColor)
            ) {
                items.forEach { item ->
                    DropdownMenuItem(
                        text = { Text(itemLabel(item), color = TextPrimaryColor) },
                        onClick = {
                            onItemSelected(item)
                            onExpandedChange(false)
                        }
                    )
                }
            }
        }
    }
}