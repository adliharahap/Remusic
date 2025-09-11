package com.example.remusic.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.remusic.data.UserManager
import com.example.remusic.ui.components.homecomponents.ArtistSection
import com.example.remusic.ui.components.homecomponents.HomeHeader
import com.example.remusic.ui.components.homecomponents.SongSection
import com.example.remusic.utils.GreetingUtils
import com.example.remusic.viewmodel.homeviewmodel.HomeUiState
import com.example.remusic.viewmodel.homeviewmodel.HomeViewModel


@Composable
fun HomeScreen(
    rootNavController: NavController,
    homeViewModel: HomeViewModel = viewModel()
) {
    // Ambil user dari state global kita
    val user = UserManager.currentUser
    // Ambil state dari ViewModel dan observasi perubahannya
    val homeState by homeViewModel.uiState.collectAsState()
    val greeting = GreetingUtils.getGreeting()

    val verticalGradientBrush = Brush.verticalGradient(
        colorStops = arrayOf(
            0.0f to Color(0xFF755D8D),
            0.23f to Color.Black
        )
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(brush = verticalGradientBrush)
            .verticalScroll(rememberScrollState())
            .padding(bottom = 70.dp)
    ) {
        Spacer(modifier = Modifier.height(30.dp))
        HomeHeader(
            name = user?.displayName,
            greeting = greeting,
            profileImageUrl = user?.photoUrl?.toString(),
            onNotificationClick = { }
        )
        Spacer(modifier = Modifier.height(16.dp))

        // Tampilkan UI berdasarkan state dari ViewModel
        when (val state = homeState) {
            is HomeUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is HomeUiState.Success -> {
                // Data kita adalah List<SongWithArtist>, ini sudah benar
                val allSongsWithArtists = state.songsWithArtists

                // Kita bisa langsung ambil beberapa item untuk section yang berbeda
                val recentlyPlayed = allSongsWithArtists.take(3)
                val topTrending = allSongsWithArtists.drop(3).take(3)

                // Langsung teruskan List<SongWithArtist> ke komponen
                SongSection("Recently Played", rootNavController, recentlyPlayed)
                Spacer(modifier = Modifier.height(16.dp))

                // Untuk ArtistSection, kita ambil artis yang unik agar tidak duplikat
                val recentlyPlayedArtists = recentlyPlayed.distinctBy { it.artist?.id }
                ArtistSection("Artist Recently Add", recentlyPlayedArtists)

                Spacer(modifier = Modifier.height(16.dp))
                SongSection("Top Trending", rootNavController, topTrending)
                Spacer(modifier = Modifier.height(16.dp))
                SongSection("All Songs", rootNavController, allSongsWithArtists)
            }
            is HomeUiState.Error -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(text = "Error: ${state.message}", color = Color.Red)
                }
            }
        }
    }
}

