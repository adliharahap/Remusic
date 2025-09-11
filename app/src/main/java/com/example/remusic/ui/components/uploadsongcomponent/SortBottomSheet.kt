package com.example.remusic.ui.components.uploadsongcomponent

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SortBottomSheet(
    currentSort: SortOption?,
    onDismiss: () -> Unit,
    onSortSelected: (SortOption) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp) // Beri ruang di bawah
        ) {
            Spacer(Modifier.height(16.dp))
            Text(
                "Sort by",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 24.dp)
            )

            Spacer(Modifier.height(16.dp))

            // Daftar Opsi Sorting
            val options = SortOption.entries.toTypedArray()
            options.forEach { option ->
                SortOptionItem(
                    icon = option.icon,
                    text = option.displayName,
                    isSelected = currentSort == option, // Cek apakah ini item yang aktif
                    onClick = {
                        onSortSelected(option)
                        onDismiss() // Langsung tutup setelah dipilih
                    }
                )
            }
        }
    }
}

@Composable
fun SortOptionItem(
    icon: ImageVector,
    text: String,
    isSelected: Boolean, // Parameter baru untuk status aktif
    onClick: () -> Unit
) {
    // Tentukan warna berdasarkan status 'isSelected'
    val backgroundColor = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else Color.Transparent
    val contentColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(backgroundColor)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(24.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = contentColor,
            modifier = Modifier.weight(1f) // Agar teks memenuhi sisa ruang
        )
        // Tampilkan ikon centang jika item ini sedang dipilih
        if (isSelected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Selected",
                tint = contentColor,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

// Enum diperbarui dengan properti untuk ikon dan nama yang lebih rapi
enum class SortOption(val displayName: String, val icon: ImageVector) {
    TitleAsc("Title (A-Z)", Icons.Default.Sort),
    TitleDesc("Title (Z-A)", Icons.Default.Sort),
    Artist("Artist", Icons.Default.Person),
    RecentlyAdded("Recently Added", Icons.Default.Schedule),
    DurationAsc("Duration (Shortest)", Icons.Default.Timer),
    DurationDesc("Duration (Longest)", Icons.Default.Timer),
    SizeAsc("Size (Smallest)", Icons.Default.Storage),
    SizeDesc("Size (Largest)", Icons.Default.Storage)
}