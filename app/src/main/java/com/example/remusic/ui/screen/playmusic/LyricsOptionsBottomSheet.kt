package com.example.remusic.ui.screen.playmusic

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.remusic.data.network.LrcLibResponse
import com.example.remusic.ui.theme.AppFont
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricsOptionsBottomSheet(
    onDismiss: () -> Unit,
    lyricsOffset: Long,
    onOffsetChange: (Long) -> Unit,
    onSearchLyrics: (String) -> Unit,
    isSearching: Boolean,
    searchResults: List<LrcLibResponse>,
    onApplyLyrics: (LrcLibResponse) -> Unit,
    topPlayerColor: Color,
    trackName: String,
    artistName: String,
    isTelegramSong: Boolean = false,
    onRegenerateTranslation: () -> Unit,
    onRegenerateGemini: (String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    
    val geminiModels = listOf(
        "gemini-3.1-flash-lite",
        "gemini-2.5-flash",
        "gemini-2.5-pro",
        "gemini-2.5-flash-lite",
        "gemini-3.1-pro",
        "gemini-3-flash"
    )
    var selectedGeminiModel by remember { mutableStateOf(geminiModels[0]) }
    var isGeminiDropdownExpanded by remember { mutableStateOf(false) }

    // AUTO SEARCH ON OPEN (Only for non-telegram songs)
    LaunchedEffect(Unit) {
        if (!isTelegramSong) {
            val query = "$trackName $artistName".trim()
            if (query.isNotEmpty()) {
                onSearchLyrics(query)
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF121212), // Background lebih gelap & solid
        scrimColor = Color.Black.copy(alpha = 0.7f),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 14.dp)
                    .width(48.dp)
                    .height(5.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.2f))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 24.dp)
        ) {
            // --- HEADER ---
            Text(
                text = "Pengaturan Lirik",
                color = Color.White,
                fontSize = 22.sp,
                fontFamily = AppFont.Poppins,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(24.dp))

            // --- SECTION 1: LYRICS OFFSET (Waktu Sinkronisasi) ---
            OffsetControllerCard(
                lyricsOffset = lyricsOffset,
                onOffsetChange = onOffsetChange,
                accentColor = topPlayerColor.copy(alpha = 1f).takeOrElse { Color.White }
            )

            Spacer(modifier = Modifier.height(24.dp))

            // --- SECTION 2: TERJEMAHAN LIRIK ---
            Button(
                onClick = { 
                    onRegenerateTranslation() 
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E1E1E))
            ) {
                Icon(
                    imageVector = Icons.Rounded.Translate,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Terjemahkan Ulang (ML Kit Kaku)",
                    color = Color.White,
                    fontFamily = AppFont.Poppins,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Gemini Model Selector
            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = { isGeminiDropdownExpanded = true },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.Transparent, contentColor = Color.White),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.3f))
                ) {
                    Text(text = "Model AI: $selectedGeminiModel", fontFamily = AppFont.Poppins)
                }
                DropdownMenu(
                    expanded = isGeminiDropdownExpanded,
                    onDismissRequest = { isGeminiDropdownExpanded = false },
                    modifier = Modifier.background(Color(0xFF2C2C2C))
                ) {
                    geminiModels.forEach { model ->
                        DropdownMenuItem(
                            text = { Text(model, color = Color.White, fontFamily = AppFont.Poppins) },
                            onClick = {
                                selectedGeminiModel = model
                                isGeminiDropdownExpanded = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = { 
                    onRegenerateGemini(selectedGeminiModel) 
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF007AFF)) // Blue for Gemini
            ) {
                Icon(
                    imageVector = Icons.Rounded.Translate,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Terjemahkan Akurat (Gemini AI)",
                    color = Color.White,
                    fontFamily = AppFont.Poppins,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // --- SECTION 3: LYRICS SOURCE / ALTERNATIVE ---
            Text(
                text = "Sumber Lirik",
                color = Color.White,
                fontSize = 18.sp,
                fontFamily = AppFont.Poppins,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            if (isTelegramSong) {
                TelegramVerifiedCard()
            } else {
                AlternativeLyricsList(
                    isSearching = isSearching,
                    searchResults = searchResults,
                    onApplyLyrics = { 
                        onApplyLyrics(it)
                        onDismiss()
                    }
                )
            }
        }
    }
}

@Composable
private fun OffsetControllerCard(
    lyricsOffset: Long,
    onOffsetChange: (Long) -> Unit,
    accentColor: Color
) {
    // Format millis to seconds (e.g., 1500 -> 1.5s)
    val formattedTime = String.format("%.1f s", abs(lyricsOffset) / 1000f)
    val sign = if (lyricsOffset > 0) "+" else if (lyricsOffset < 0) "-" else ""

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF1E1E1E))
            .padding(20.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Timer,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Sinkronisasi Waktu",
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 15.sp,
                        fontFamily = AppFont.Poppins,
                        fontWeight = FontWeight.Medium
                    )
                }
                
                // Indikator angka berlatar belakang warna aksen
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (lyricsOffset == 0L) Color.White.copy(alpha = 0.1f) else accentColor.copy(alpha = 0.2f))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "$sign$formattedTime",
                        color = if (lyricsOffset == 0L) Color.White.copy(alpha = 0.7f) else accentColor,
                        fontSize = 13.sp,
                        fontFamily = AppFont.RobotoBold,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Slider(
                value = lyricsOffset.toFloat(),
                onValueChange = { onOffsetChange(it.toLong()) },
                valueRange = -5000f..5000f,
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = accentColor,
                    inactiveTrackColor = Color.White.copy(alpha = 0.1f)
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Lambat (-5s)", color = Color.White.copy(alpha = 0.4f), fontSize = 11.sp, fontFamily = AppFont.RobotoRegular)
                Text("Cepat (+5s)", color = Color.White.copy(alpha = 0.4f), fontSize = 11.sp, fontFamily = AppFont.RobotoRegular)
            }
        }
    }
}

@Composable
private fun TelegramVerifiedCard() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF192A32)) // Warna kebiruan gelap ala Telegram
            .padding(20.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Rounded.VerifiedUser,
                contentDescription = null,
                tint = Color(0xFF2CA5E0), // Warna biru Telegram
                modifier = Modifier.size(36.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Lirik Terverifikasi Komunitas",
                color = Color.White,
                fontSize = 16.sp,
                fontFamily = AppFont.Poppins,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Lagu ini diunggah via Telegram. Lirik sudah disinkronkan secara permanen oleh pengunggah dan tidak dapat diganti.",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 13.sp,
                fontFamily = AppFont.RobotoRegular,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )
        }
    }
}

@Composable
private fun AlternativeLyricsList(
    isSearching: Boolean,
    searchResults: List<LrcLibResponse>,
    onApplyLyrics: (LrcLibResponse) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 150.dp, max = 400.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF1E1E1E))
    ) {
        if (isSearching) {
            Column(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(color = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(32.dp))
                Spacer(modifier = Modifier.height(16.dp))
                Text("Mencari lirik alternatif...", color = Color.White.copy(alpha = 0.5f), fontSize = 13.sp)
            }
        } else if (searchResults.isNotEmpty()) {
            LazyColumn(
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(searchResults.take(20)) { result ->
                    LyricResultCard(
                        result = result,
                        onClick = { onApplyLyrics(result) }
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.SmsFailed,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.2f),
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Lirik tidak ditemukan.",
                    color = Color.White.copy(alpha = 0.4f),
                    fontSize = 14.sp,
                    fontFamily = AppFont.RobotoMedium
                )
            }
        }
    }
}

@Composable
fun LyricResultCard(
    result: LrcLibResponse,
    onClick: () -> Unit
) {
    val isSynced = result.syncedLyrics != null
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icon indikator lagu
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.05f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.MusicNote,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.5f),
                modifier = Modifier.size(20.dp)
            )
        }
        
        Spacer(modifier = Modifier.width(12.dp))

        // Info Text
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = result.trackName,
                color = Color.White,
                fontSize = 15.sp,
                fontFamily = AppFont.RobotoBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = result.artistName,
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 13.sp,
                fontFamily = AppFont.RobotoRegular,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        
        Spacer(modifier = Modifier.width(12.dp))
        
        // Badge Synced/Plain yang dipercantik
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(if (isSynced) Color(0xFF4CAF50).copy(alpha = 0.15f) else Color.White.copy(alpha = 0.1f))
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Icon(
                imageVector = if (isSynced) Icons.Rounded.CheckCircle else Icons.Rounded.Notes,
                contentDescription = null,
                tint = if (isSynced) Color(0xFF81C784) else Color.White.copy(alpha = 0.6f),
                modifier = Modifier.size(12.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = if (isSynced) "SYNCED" else "PLAIN",
                color = if (isSynced) Color(0xFF81C784) else Color.White.copy(alpha = 0.6f),
                fontSize = 10.sp,
                fontFamily = AppFont.RobotoBold,
                letterSpacing = 0.5.sp
            )
        }
    }
}