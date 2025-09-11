package com.example.remusic.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.remusic.ui.components.searchcomponents.HeaderSearchSection
import com.example.remusic.ui.components.searchcomponents.RecentlyPlayedSection
import com.example.remusic.ui.components.searchcomponents.SearchHistorySection
import com.example.remusic.ui.theme.AppFont

// Data class lagu
data class Song2(
    val id: Int,
    val title: String,
    val artist: String,
    val imageUrl: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen() {
    var isFullSearch by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    // 👇 Tambahkan BackHandler di sini
    BackHandler(enabled = isFullSearch) {
        // Blok ini hanya akan dijalankan jika 'enabled' bernilai true.
        // Saat tombol kembali ditekan, ubah state isFullSearch menjadi false.
        isFullSearch = false
    }


    if (!isFullSearch) {
        // Mode awal pencarian
        val searchHistory = listOf("Top Hits Indonesia", "Lagu Galau", "Indie Folk")
        val recentlyPlayed = listOf(
            Song2(1, "Monokrom", "Tulus", "https://i.pinimg.com/736x/27/a1/fa/27a1faa850e8041d9a2c59ba6c7fb91f.jpg"),
            Song2(2, "Secukupnya", "Hindia", "https://i.pinimg.com/736x/27/a1/fa/27a1faa850e8041d9a2c59ba6c7fb91f.jpg"),
            Song2(3, "Sial", "Mahalini", "https://i.pinimg.com/736x/27/a1/fa/27a1faa850e8041d9a2c59ba6c7fb91f.jpg")
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF000000))
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
            ) {
                item {
                    Spacer(modifier = Modifier.height(30.dp))
                }
                item {
                    HeaderSearchSection(
                        profileImageUrl = "https://i.pinimg.com/736x/0c/86/83/0c86831120a35560280ce0e235fd7e57.jpg",
                        title = "Pencarian",
                        onSearchClick = { isFullSearch = true }
                    )
                }
                item { SearchHistorySection(historyItems = searchHistory, onRemoveClick = {}) }
                item {
                    RecentlyPlayedSection(
                        title = "Baru Saja Ditambahkan",
                        songs = recentlyPlayed,
                        onMoreOptionsClick = { song ->
                            // Logika tampilkan opsi lagu, misal show bottom sheet atau dialog
                        }
                    )
                }
            }
        }
    } else {
        // Mode pencarian aktif (full search)
        // Menggunakan Column agar lebih mudah menambahkan list hasil pencarian nanti
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF121212)) // Samakan background agar transisi mulus
        ) {
            Row(
                modifier = Modifier.fillMaxWidth()
                    .background(color = Color(0xFF2A2A2A))
                    .padding(top = 40.dp),
                verticalAlignment = Alignment.CenterVertically // Agar icon dan text field sejajar
            ) {
                // Tombol kembali, ditempatkan di dalam Row sebelum TextField
                IconButton(
                    // 1. Aksi untuk kembali adalah mengubah isFullSearch menjadi false
                    onClick = { isFullSearch = false }
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White // Sesuaikan warna agar terlihat di background gelap
                    )
                }

                // TextField untuk input pencarian
                OutlinedTextField(
                    // 2. Gunakan state 'searchQuery' yang sudah ada
                    value = searchQuery,
                    // 3. Update state 'searchQuery' saat teks berubah
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(end = 2.dp),
                    placeholder = { Text(
                        text = "Ketik lagu atau artist yang kamu suka",
                        fontSize = 15.sp,
                        fontFamily = AppFont.RobotoRegular,
                        color = Color.LightGray
                    ) },
                    singleLine = true,
                    textStyle = TextStyle(
                        fontSize = 16.sp,
                        fontFamily = AppFont.RobotoRegular,
                        color = Color.White
                    ),
                    keyboardOptions = KeyboardOptions(
                        // 5. Mengubah tombol 'Enter' di keyboard menjadi ikon 'Search'
                        imeAction = ImeAction.Search
                    ),
                    // 4. Gunakan OutlinedTextFieldDefaults untuk OutlinedTextField
                    colors = OutlinedTextFieldDefaults.colors(
                        cursorColor = Color.White,
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        focusedPlaceholderColor = Color.Gray,
                        unfocusedPlaceholderColor = Color.Gray,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                    )
                )
            }
            // Di sini Anda bisa menambahkan LazyColumn untuk menampilkan hasil pencarian
            // Contoh:
            // LazyColumn(modifier = Modifier.fillMaxSize()) {
            //     // items(hasilPencarian) { ... }
            // }
        }
    }
}




