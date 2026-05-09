package com.example.remusic.ui.screen.playmusic

import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.remusic.ui.theme.AppFont
import com.example.remusic.utils.GoogleFontDownloader
import kotlinx.coroutines.launch

// Warna shared
private val SheetBg   = Color(0xFF121212)
private val CardBg    = Color(0xFF1C1C1E)
private val TextPri   = Color.White
private val TextSec   = Color(0xFFA0A0A5)
private val Divider   = Color.White.copy(alpha = 0.05f)
private val GreenBadge = Color(0xFF4CAF50)
private val BlueBadge  = Color(0xFF2196F3)
private val GrayBadge  = Color(0xFF757575)

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

    var searchQuery by remember { mutableStateOf("") }

    // Status per font
    val downloadingMap = remember { mutableStateMapOf<String, Boolean>() }
    val cachedMap      = remember { mutableStateMapOf<String, Boolean>() }

    // Init cache status
    LaunchedEffect(Unit) {
        LyricsFontFamily.values()
            .filter { it.isOnline }
            .forEach { font ->
                cachedMap[font.googleFontName] = GoogleFontDownloader.isFontCached(context, font.googleFontName)
            }
    }

    // Pisah font offline & online
    val offlineFonts = LyricsFontFamily.values().filter { !it.isOnline }
    val onlineFonts  = LyricsFontFamily.values().filter { it.isOnline }

    // Filter berdasarkan query
    val filteredOffline = offlineFonts.filter {
        searchQuery.isBlank() || it.displayName.contains(searchQuery, ignoreCase = true)
    }
    val filteredOnline = onlineFonts.filter {
        searchQuery.isBlank() || it.displayName.contains(searchQuery, ignoreCase = true)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SheetBg,
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
                .imePadding()
        ) {
            // ── Header ──────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Pilih Font Lirik",
                        color = TextPri,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Terpilih: ${selectedFont.displayName}",
                        color = TextSec,
                        fontSize = 13.sp
                    )
                }
                // Badge terpilih saat ini
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White.copy(0.1f))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = selectedFont.displayName,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ── Search Bar ──────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(CardBg)
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = null,
                        tint = TextSec,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Box(modifier = Modifier.weight(1f)) {
                        if (searchQuery.isEmpty()) {
                            Text(
                                text = "Cari font...",
                                color = TextSec,
                                fontSize = 15.sp
                            )
                        }
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            textStyle = TextStyle(
                                color = TextPri,
                                fontSize = 15.sp
                            ),
                            cursorBrush = SolidColor(Color.White),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    if (searchQuery.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(
                            onClick = { searchQuery = "" },
                            modifier = Modifier.size(20.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Clear,
                                contentDescription = "Hapus pencarian",
                                tint = TextSec,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // ── List Font ────────────────────────────────────────────
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 16.dp, end = 16.dp, bottom = 32.dp
                ),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                // ─ Seksi Font Bawaan ─────────────────────────────────
                if (filteredOffline.isNotEmpty()) {
                    item(key = "header_offline") {
                        FontSectionHeader(
                            title = "FONT BAWAAN",
                            subtitle = "${filteredOffline.size} font · Selalu tersedia"
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                    }

                    item(key = "card_offline") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(CardBg)
                        ) {
                            Column {
                                filteredOffline.forEachIndexed { index, font ->
                                    FontRow(
                                        font = font,
                                        isSelected = selectedFont == font,
                                        isCached = true,
                                        isDownloading = false,
                                        badgeText = "Offline",
                                        badgeColor = GrayBadge,
                                        onSelect = {
                                            onFontSelected(font)
                                            onDismiss()
                                        },
                                        onDownload = null
                                    )
                                    if (index < filteredOffline.lastIndex) {
                                        HorizontalDivider(
                                            modifier = Modifier.padding(horizontal = 16.dp),
                                            color = Divider,
                                            thickness = 1.dp
                                        )
                                    }
                                }
                            }
                        }
                    }

                    item(key = "spacer_between") { Spacer(modifier = Modifier.height(16.dp)) }
                }

                // ─ Seksi Google Fonts ────────────────────────────────
                if (filteredOnline.isNotEmpty()) {
                    item(key = "header_online") {
                        FontSectionHeader(
                            title = "GOOGLE FONTS",
                            subtitle = "${filteredOnline.size} font · Unduh untuk pakai offline"
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                    }

                    item(key = "card_online") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(CardBg)
                        ) {
                            Column {
                                filteredOnline.forEachIndexed { index, font ->
                                    val isCached      = cachedMap[font.googleFontName] == true
                                    val isDownloading = downloadingMap[font.googleFontName] == true

                                    FontRow(
                                        font = font,
                                        isSelected = selectedFont == font,
                                        isCached = isCached,
                                        isDownloading = isDownloading,
                                        badgeText = if (isCached) "Tersimpan" else "Online",
                                        badgeColor = if (isCached) BlueBadge else GreenBadge,
                                        onSelect = {
                                            if (isCached) {
                                                onFontSelected(font)
                                                onDismiss()
                                            } else {
                                                Toast.makeText(
                                                    context,
                                                    "Unduh '${font.displayName}' terlebih dahulu",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        },
                                        onDownload = if (!isCached && !isDownloading) ({
                                            scope.launch {
                                                downloadingMap[font.googleFontName] = true
                                                val ok = GoogleFontDownloader.downloadFont(
                                                    context, font.googleFontName
                                                )
                                                downloadingMap[font.googleFontName] = false
                                                if (ok) {
                                                    cachedMap[font.googleFontName] = true
                                                    onFontSelected(font)
                                                    Toast.makeText(
                                                        context,
                                                        "'${font.displayName}' berhasil diunduh!",
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                    onDismiss()
                                                } else {
                                                    Toast.makeText(
                                                        context,
                                                        "Gagal mengunduh '${font.displayName}'",
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                }
                                            }
                                        }) else null
                                    )

                                    if (index < filteredOnline.lastIndex) {
                                        HorizontalDivider(
                                            modifier = Modifier.padding(horizontal = 16.dp),
                                            color = Divider,
                                            thickness = 1.dp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // ─ Empty state ────────────────────────────────────────
                if (filteredOffline.isEmpty() && filteredOnline.isEmpty()) {
                    item(key = "empty") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Filled.Search,
                                    contentDescription = null,
                                    tint = TextSec.copy(alpha = 0.4f),
                                    modifier = Modifier.size(40.dp)
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "Font \"$searchQuery\" tidak ditemukan",
                                    color = TextSec,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─── Header Seksi ───────────────────────────────────────────────────────────

@Composable
private fun FontSectionHeader(title: String, subtitle: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            color = TextSec,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp,
            fontFamily = AppFont.HelveticaRoundedBold
        )
        Text(
            text = subtitle,
            color = TextSec.copy(alpha = 0.6f),
            fontSize = 10.sp
        )
    }
}

// ─── Font Row ────────────────────────────────────────────────────────────────

@Composable
private fun FontRow(
    font: LyricsFontFamily,
    isSelected: Boolean,
    isCached: Boolean,
    isDownloading: Boolean,
    badgeText: String,
    badgeColor: Color,
    onSelect: () -> Unit,
    onDownload: (() -> Unit)?
) {
    val bgColor by animateColorAsState(
        targetValue = if (isSelected) Color.White.copy(0.07f) else Color.Transparent,
        animationSpec = tween(200),
        label = "fontRowBg"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor)
            .clickable(onClick = onSelect)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // ● Selected dot
        Box(
            modifier = Modifier
                .size(9.dp)
                .clip(CircleShape)
                .background(if (isSelected) Color.White else Color.Transparent)
                .border(
                    width = 1.dp,
                    color = if (isSelected) Color.Transparent else Color.White.copy(0.2f),
                    shape = CircleShape
                )
        )

        Spacer(modifier = Modifier.width(12.dp))

        // Font name + badge
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = font.displayName,
                color = if (isSelected) Color.White else TextPri.copy(0.85f),
                fontSize = 15.sp,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(3.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(badgeColor.copy(0.15f))
                    .padding(horizontal = 7.dp, vertical = 2.dp)
            ) {
                Text(
                    text = badgeText,
                    color = badgeColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Right: download / progress / check
        when {
            isDownloading -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )
            }
            isCached && isSelected -> {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = "Dipilih",
                    tint = BlueBadge,
                    modifier = Modifier.size(22.dp)
                )
            }
            !isCached && onDownload != null -> {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color(0xFF1565C0), Color(0xFF1E88E5))
                            )
                        )
                        .clickable { onDownload() }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.CloudDownload,
                            contentDescription = "Unduh",
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "Unduh",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}
