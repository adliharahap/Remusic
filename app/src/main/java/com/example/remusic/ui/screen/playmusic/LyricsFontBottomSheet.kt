package com.example.remusic.ui.screen.playmusic

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.remusic.utils.GoogleFontDownloader
import kotlinx.coroutines.launch

// Palette warna konsisten & premium
private val SheetBg   = Color(0xFF101010)
private val CardBg    = Color(0xFF1A1A1C)
private val AccentBlue = Color(0xFF3897F0)
private val TextPri   = Color.White
private val TextSec   = Color(0xFF9E9E9E)
private val Divider   = Color.White.copy(alpha = 0.08f)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricsFontBottomSheet(
    sheetState: SheetState,
    selectedFont: LyricsFontFamily,
    onFontSelected: (LyricsFontFamily) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    var searchQuery by remember { mutableStateOf("") }

    // Status cache per font (optimasi performa)
    val downloadingMap = remember { mutableStateMapOf<String, Boolean>() }
    val cachedMap      = remember { mutableStateMapOf<String, Boolean>() }

    // Inisialisasi status cache saat pertama kali dibuka
    LaunchedEffect(Unit) {
        LyricsFontFamily.values().forEach { font ->
            if (font.isOnline) {
                cachedMap[font.googleFontName] = GoogleFontDownloader.isFontCached(context, font.googleFontName)
            } else {
                cachedMap[font.googleFontName] = true
            }
        }
    }

    // Filter font berdasarkan pencarian
    val allFonts = LyricsFontFamily.values()
    val filteredFonts = allFonts.filter {
        searchQuery.isBlank() || it.displayName.contains(searchQuery, ignoreCase = true)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SheetBg,
        tonalElevation = 8.dp,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 14.dp)
                    .width(42.dp)
                    .height(4.dp)
                    .background(Color.White.copy(0.25f), CircleShape)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f) // Batasi tinggi agar tidak menutupi layar penuh
                .imePadding()
        ) {
            // --- HEADER ---
            Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                Text(
                    text = "Pilih Font Lirik",
                    color = TextPri,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.5).sp
                )
                Text(
                    text = "Berikan gaya unik pada lirik lagu favoritmu",
                    color = TextSec,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )
                
                Spacer(modifier = Modifier.height(20.dp))

                // --- SEARCH BAR (MODERN) ---
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Cari font lirik...", color = TextSec) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = if (searchQuery.isNotEmpty()) AccentBlue else TextSec,
                            modifier = Modifier.size(22.dp)
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Hapus",
                                    tint = TextSec,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = CardBg,
                        unfocusedContainerColor = CardBg,
                        focusedBorderColor = AccentBlue.copy(0.5f),
                        unfocusedBorderColor = Color.Transparent,
                        cursorColor = AccentBlue,
                        focusedTextColor = TextPri,
                        unfocusedTextColor = TextPri
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() })
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // --- FONT LIST ---
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 40.dp)
            ) {
                if (filteredFonts.isEmpty()) {
                    item {
                        EmptySearchResult(query = searchQuery)
                    }
                } else {
                    items(filteredFonts, key = { it.name }) { font ->
                        val isCached = cachedMap[font.googleFontName] == true
                        val isDownloading = downloadingMap[font.googleFontName] == true
                        
                        FontItemRow(
                            font = font,
                            isSelected = selectedFont == font,
                            isCached = isCached,
                            isDownloading = isDownloading,
                            onSelect = {
                                if (isCached) {
                                    onFontSelected(font)
                                    onDismiss()
                                } else {
                                    Toast.makeText(context, "Unduh font ini terlebih dahulu", Toast.LENGTH_SHORT).show()
                                }
                            },
                            onDownload = if (!isCached && !isDownloading) {
                                {
                                    scope.launch {
                                        downloadingMap[font.googleFontName] = true
                                        val ok = GoogleFontDownloader.downloadFont(context, font.googleFontName)
                                        downloadingMap[font.googleFontName] = false
                                        if (ok) {
                                            cachedMap[font.googleFontName] = true
                                            onFontSelected(font)
                                            onDismiss()
                                        } else {
                                            Toast.makeText(context, "Gagal mengunduh font", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            } else null
                        )
                        
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 24.dp),
                            color = Divider,
                            thickness = 0.5.dp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FontItemRow(
    font: LyricsFontFamily,
    isSelected: Boolean,
    isCached: Boolean,
    isDownloading: Boolean,
    onSelect: () -> Unit,
    onDownload: (() -> Unit)?
) {
    val context = LocalContext.current
    val bgColor by animateColorAsState(
        targetValue = if (isSelected) AccentBlue.copy(0.08f) else Color.Transparent,
        label = "bg_color"
    )

    // Load typeface jika sudah di-cache untuk preview
    val customTypeface = remember(isCached) {
        if (isCached && font.isOnline) {
            GoogleFontDownloader.loadCachedTypeface(context, font.googleFontName)
        } else null
    }
    val fontFamily = if (customTypeface != null) FontFamily(customTypeface) else FontFamily.Default

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor)
            .clickable(onClick = onSelect)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Indikator terpilih
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .border(
                    width = 2.dp,
                    color = if (isSelected) AccentBlue else Color.White.copy(0.15f),
                    shape = CircleShape
                )
                .padding(4.dp),
            contentAlignment = Alignment.Center
        ) {
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(AccentBlue)
                )
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        // Informasi Font + Preview
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = font.displayName,
                color = if (isSelected) AccentBlue else TextPri,
                fontSize = 17.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                fontFamily = fontFamily, // Gunakan font aslinya jika sudah di-download
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                Text(
                    text = if (font.isOnline) "Google Fonts" else "Sistem",
                    color = TextSec,
                    fontSize = 12.sp
                )
                if (isCached && font.isOnline) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(modifier = Modifier.size(3.dp).clip(CircleShape).background(TextSec))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Tersimpan",
                        color = Color(0xFF4CAF50),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Action: Progres / Download / Check
        Spacer(modifier = Modifier.width(12.dp))
        
        when {
            isDownloading -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = AccentBlue,
                    strokeWidth = 3.dp
                )
            }
            !isCached && onDownload != null -> {
                IconButton(
                    onClick = onDownload,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(0.05f))
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudDownload,
                        contentDescription = "Unduh",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            isCached && isSelected -> {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Terpilih",
                    tint = AccentBlue,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
private fun EmptySearchResult(query: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 60.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = null,
            tint = TextSec.copy(0.3f),
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Font \"$query\" tidak ditemukan",
            color = TextSec,
            fontSize = 16.sp,
            textAlign = TextAlign.Center
        )
        Text(
            text = "Coba cari dengan nama font lain",
            color = TextSec.copy(0.6f),
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}
