package com.example.remusic.ui.screen.playmusic

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
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
import androidx.compose.material.icons.automirrored.outlined.PlaylistAdd
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.FormatAlignCenter
import androidx.compose.material.icons.outlined.FormatAlignLeft
import androidx.compose.material.icons.outlined.FormatAlignRight
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
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

// Data class for Lyrics Config
data class LyricsConfig(
    val fontFamily: LyricsFontFamily = LyricsFontFamily.POPPINS,
    val fontSize: Int = 21, // dp
    val align: LyricsAlign = LyricsAlign.LEFT,
    val autoScaleIfNoTranslation: Boolean = false,
    val scaleFactor: Int = 1, // +1, +2, +3
    val markPassedLyrics: Boolean = false,
    val translateFontSize: Float = 15.5f
)

enum class LyricsFontFamily(val displayName: String) {
    POPPINS("Poppins"),
    ROBOTO("Roboto"),
    HELVETICA("Helvetica")
}

enum class LyricsAlign {
    LEFT, CENTER, RIGHT
}

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
    gradientStyle: com.example.remusic.data.preferences.GradientStyle = com.example.remusic.data.preferences.GradientStyle.PRIMARY,
    onGradientStyleChange: (com.example.remusic.data.preferences.GradientStyle) -> Unit = {},
    // Gradient Color Positions
    gradientTopColorIndex: Int = 0,
    onGradientTopColorIndexChange: (Int) -> Unit = {},
    gradientBottomColorIndex: Int = 1,
    onGradientBottomColorIndexChange: (Int) -> Unit = {},
    // Data Saver
    isDataSaverModeEnabled: Boolean = false,
    onDataSaverModeChange: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF1E1E1E), // Dark background matching theme roughly
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .width(40.dp)
                    .height(4.dp)
                    .background(Color.White.copy(0.3f), RoundedCornerShape(2.dp))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 30.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // --- HEADER (Design from QueueSongCard) ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Album Art
                AsyncImage(
                    model = songWithArtist?.song?.coverUrl,
                    contentDescription = "Song Cover",
                    modifier = Modifier
                        .size(56.dp)
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop,
                    placeholder = painterResource(R.drawable.img_placeholder),
                    error = painterResource(R.drawable.img_placeholder)
                )

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    // Song Title with Marquee
                    Text(
                        text = songWithArtist?.song?.title ?: "Unknown Title",
                        color = Color.White,
                        fontFamily = AppFont.Coolvetica,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        modifier = Modifier
                            .fillMaxWidth()
                            .basicMarquee(
                                animationMode = androidx.compose.foundation.MarqueeAnimationMode.Immediately,
                                velocity = 70.dp,
                                initialDelayMillis = 2000,
                                repeatDelayMillis = 5000,
                                iterations = Int.MAX_VALUE
                            )
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = songWithArtist?.displayArtistName ?: "Unknown Artist",
                        color = Color.White.copy(0.7f),
                        fontFamily = AppFont.Helvetica,
                        fontWeight = FontWeight.Normal,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Thin Horizontal Divider
            HorizontalDivider(
                modifier = Modifier.fillMaxWidth(),
                thickness = 1.2.dp,
                color = Color.White.copy(alpha = 0.1f)
            )

            Spacer(modifier = Modifier.height(10.dp))

            // --- MENU OPTIONS ---
            MenuOptionItem(
                title = "Bagikan",
                icon = Icons.Outlined.Share,
                onClick = {
                    Toast.makeText(context, "Fitur belum tersedia", Toast.LENGTH_SHORT).show()
                }
            )
            MenuOptionItem(
                title = "Tambahkan ke playlist",
                icon = Icons.AutoMirrored.Outlined.PlaylistAdd,
                onClick = onAddToPlaylist
            )
            MenuOptionItem(
                title = "Tambahkan ke Lagu yang Disukai",
                icon = Icons.Outlined.FavoriteBorder, // Use Favorite if liked, logic handled by parent
                onClick = onAddToLiked
            )
            MenuOptionItem(
                title = "Setel waktu tidur",
                icon = Icons.Outlined.Bedtime,
                onClick = {
                    onSetSleepTimer()
                    onDismiss() // Dismiss this sheet to show timer sheet? Or handle in parent
                }
            )
            MenuOptionItem(
                title = "Unduh",
                icon = Icons.Outlined.Download,
                onClick = {
                    Toast.makeText(context, "Fitur belum tersedia", Toast.LENGTH_SHORT).show()
                }
            )

            // --- DATA SAVER MODE ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onDataSaverModeChange(!isDataSaverModeEnabled) }
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Mode Hemat Data",
                        color = Color.White,
                        fontSize = 16.sp
                    )
                    Text(
                        "Mematikan canvas video di player",
                        color = Color.White.copy(0.6f),
                        fontSize = 12.sp
                    )
                }
                Switch(
                    checked = isDataSaverModeEnabled,
                    onCheckedChange = { onDataSaverModeChange(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.Black,
                        checkedTrackColor = Color.White,
                        uncheckedThumbColor = Color.White,
                        uncheckedTrackColor = Color.DarkGray
                    )
                )
            }

            Spacer(modifier = Modifier.height(20.dp))
            HorizontalDivider(thickness = 0.5.dp, color = Color.White.copy(0.1f))
            Spacer(modifier = Modifier.height(20.dp))

            // --- BACKGROUND SETTINGS ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Background Style",
                    color = Color.White.copy(0.7f),
                    fontSize = 14.sp,
                    fontFamily = AppFont.HelveticaRoundedBold
                )

                if (gradientStyle != com.example.remusic.data.preferences.GradientStyle.PRIMARY ||
                    gradientTopColorIndex != 0 ||
                    gradientBottomColorIndex != 1
                ) {
                    TextButton(
                        onClick = {
                            onGradientStyleChange(com.example.remusic.data.preferences.GradientStyle.PRIMARY)
                            onGradientTopColorIndexChange(0)
                            onGradientBottomColorIndexChange(1)
                        },
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text("Restore Default", color = Color.Gray, fontSize = 12.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                com.example.remusic.data.preferences.GradientStyle.values().forEach { style ->
                    FilterChip(
                        selected = gradientStyle == style,
                        onClick = { onGradientStyleChange(style) },
                        label = { Text(style.displayName) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color.White,
                            selectedLabelColor = Color.Black,
                            containerColor = Color.Transparent,
                            labelColor = Color.White
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // --- TOP COLOR INDEX ---
            Text(
                "Top Color Position",
                modifier = Modifier.padding(horizontal = 20.dp),
                color = Color.White.copy(0.7f),
                fontSize = 14.sp,
                fontFamily = AppFont.HelveticaRoundedBold
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                (0..3).forEach { index ->
                    FilterChip(
                        selected = gradientTopColorIndex == index,
                        onClick = { onGradientTopColorIndexChange(index) },
                        label = { Text("Color ${index + 1}") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color.White,
                            selectedLabelColor = Color.Black,
                            containerColor = Color.Transparent,
                            labelColor = Color.White
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // --- BOTTOM COLOR INDEX ---
            Text(
                "Bottom Color Position",
                modifier = Modifier.padding(horizontal = 20.dp),
                color = Color.White.copy(0.7f),
                fontSize = 14.sp,
                fontFamily = AppFont.HelveticaRoundedBold
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                (0..3).forEach { index ->
                    FilterChip(
                        selected = gradientBottomColorIndex == index,
                        onClick = { onGradientBottomColorIndexChange(index) },
                        label = { Text("Color ${index + 1}") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color.White,
                            selectedLabelColor = Color.Black,
                            containerColor = Color.Transparent,
                            labelColor = Color.White
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            HorizontalDivider(thickness = 0.5.dp, color = Color.White.copy(0.1f))
            Spacer(modifier = Modifier.height(20.dp))

            // --- LYRICS SETTINGS ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Lyrics Settings",
                    color = Color.White.copy(0.7f),
                    fontSize = 14.sp,
                    fontFamily = AppFont.HelveticaRoundedBold
                )

                if (lyricsConfig != LyricsConfig()) {
                    TextButton(
                        onClick = { onLyricsConfigChange(LyricsConfig()) },
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text("Restore Default", color = Color.Gray, fontSize = 12.sp)
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            // 1. Font Family
            var fontMenuExpanded by remember { mutableStateOf(false) }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "Font Config",
                    modifier = Modifier.weight(1f),
                    color = Color.White,
                    fontSize = 16.sp,
                    fontFamily = AppFont.Helvetica
                )

                Box {
                    OutlinedButton(
                        onClick = { fontMenuExpanded = true },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                    ) {
                        Text(lyricsConfig.fontFamily.displayName)
                    }
                    DropdownMenu(
                        expanded = fontMenuExpanded,
                        onDismissRequest = { fontMenuExpanded = false },
                        modifier = Modifier.background(Color(0xFF2A2A2A))
                    ) {
                        LyricsFontFamily.values().forEach { font ->
                            DropdownMenuItem(
                                text = { Text(font.displayName, color = Color.White) },
                                onClick = {
                                    onLyricsConfigChange(lyricsConfig.copy(fontFamily = font))
                                    fontMenuExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            // 2. Alignment
            Text("Align", modifier = Modifier.padding(horizontal = 20.dp), color = Color.White, fontSize = 16.sp, fontFamily = AppFont.Helvetica)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                val alignOptions = listOf(
                    LyricsAlign.LEFT to Icons.Outlined.FormatAlignLeft,
                    LyricsAlign.CENTER to Icons.Outlined.FormatAlignCenter,
                    LyricsAlign.RIGHT to Icons.Outlined.FormatAlignRight
                )
                alignOptions.forEach { (align, icon) ->
                   Box(
                       modifier = Modifier
                           .clip(RoundedCornerShape(8.dp))
                           .background(if (lyricsConfig.align == align) Color.White else Color.White.copy(0.1f))
                           .clickable { onLyricsConfigChange(lyricsConfig.copy(align = align)) }
                           .padding(12.dp)
                   ) {
                       Icon(
                           imageVector = icon,
                           contentDescription = null,
                           tint = if (lyricsConfig.align == align) Color.Black else Color.White
                       )
                   }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 3. Auto Scale (Translation Off)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onLyricsConfigChange(lyricsConfig.copy(autoScaleIfNoTranslation = !lyricsConfig.autoScaleIfNoTranslation)) }
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Perbesar jika tidak ada terjemahan", color = Color.White, fontSize = 16.sp, fontFamily = AppFont.Helvetica)
                    Text("Otomatis memperbesar teks lirik", color = Color.White.copy(0.6f), fontSize = 12.sp)
                }
                Switch(
                    checked = lyricsConfig.autoScaleIfNoTranslation,
                    onCheckedChange = { onLyricsConfigChange(lyricsConfig.copy(autoScaleIfNoTranslation = it)) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color.Green.copy(0.7f), // Theme color
                        uncheckedThumbColor = Color.Gray,
                        uncheckedTrackColor = Color.DarkGray
                    )
                )
            }

            // 4. Scale Factor (If Auto Scale is ON)
            if (lyricsConfig.autoScaleIfNoTranslation) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val scales = listOf(1, 2, 3)
                    scales.forEach { scale ->
                        FilterChip(
                            selected = lyricsConfig.scaleFactor == scale,
                            onClick = { onLyricsConfigChange(lyricsConfig.copy(scaleFactor = scale)) },
                            label = { Text("+${scale}") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color.White,
                                selectedLabelColor = Color.Black,
                                containerColor = Color.Transparent,
                                labelColor = Color.White
                            )
                        )
                    }
                }
            }

            // 5. Sliders Output Ukuran Font Lirik
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "Ukuran Lirik Asli: ${lyricsConfig.fontSize}sp",
                color = Color.White,
                fontSize = 14.sp,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
            androidx.compose.material3.Slider(
                value = lyricsConfig.fontSize.toFloat(),
                onValueChange = { newValue ->
                    onLyricsConfigChange(lyricsConfig.copy(fontSize = newValue.toInt()))
                },
                valueRange = 15f..35f,
                modifier = Modifier.padding(horizontal = 16.dp),
                colors = androidx.compose.material3.SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color.White,
                    inactiveTrackColor = Color.DarkGray
                )
            )

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Ukuran Lirik Terjemahan: ${String.format("%.1f", lyricsConfig.translateFontSize)}sp",
                color = Color.White,
                fontSize = 14.sp,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
            androidx.compose.material3.Slider(
                value = lyricsConfig.translateFontSize,
                onValueChange = { newValue ->
                    onLyricsConfigChange(lyricsConfig.copy(translateFontSize = newValue))
                },
                valueRange = 10f..30f,
                modifier = Modifier.padding(horizontal = 16.dp),
                colors = androidx.compose.material3.SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color.White,
                    inactiveTrackColor = Color.DarkGray
                )
            )

            // 6. Mark Passed Lyrics
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onLyricsConfigChange(
                            lyricsConfig.copy(markPassedLyrics = !lyricsConfig.markPassedLyrics)
                        )
                    }
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "Tandai lirik yang dilewati",
                    color = Color.White,
                    fontSize = 16.sp
                )
                Switch(
                    checked = lyricsConfig.markPassedLyrics,
                    onCheckedChange = {
                        onLyricsConfigChange(lyricsConfig.copy(markPassedLyrics = it))
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.Black,
                        checkedTrackColor = Color.White,
                        uncheckedThumbColor = Color.White,
                        uncheckedTrackColor = Color.DarkGray
                    )
                )
            }

            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

@Composable
fun MenuOptionItem(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(20.dp))
        Text(
            text = title,
            color = Color.White,
            fontFamily = AppFont.Helvetica,
            fontSize = 16.sp
        )
    }
}
