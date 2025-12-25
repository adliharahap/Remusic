package com.example.remusic.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.remusic.ui.components.SkeletonBox

@Composable
fun HomeSkeletonScreen() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        // 2. Section Skeleton: Recently Played (Judul + Row Horizontal)
        SkeletonSection(titleWidth = 150.dp)

        Spacer(modifier = Modifier.height(24.dp))

        // 3. Section Skeleton: Artist (Bulat-bulat)
        SkeletonArtistSection()

        Spacer(modifier = Modifier.height(24.dp))

        // 4. Section Skeleton: Top Trending
        SkeletonSection(titleWidth = 120.dp)

        Spacer(modifier = Modifier.height(24.dp))

        // 4. Section Skeleton: all music
        SkeletonSection(titleWidth = 120.dp)
    }
}

// Skeleton untuk Section Lagu (Kotak-kotak)
@Composable
fun SkeletonSection(titleWidth: androidx.compose.ui.unit.Dp) {
    Column {
        // Judul Section & See All
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            SkeletonBox(modifier = Modifier.width(titleWidth).height(20.dp))
            SkeletonBox(modifier = Modifier.width(50.dp).height(16.dp))
        }
        Spacer(modifier = Modifier.height(12.dp))

        // Deretan Kartu Lagu (Misal 3 biji)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            repeat(3) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    SkeletonBox(
                        modifier = Modifier.size(100.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) // Cover
                    Spacer(modifier = Modifier.height(8.dp))
                    SkeletonBox(modifier = Modifier.width(80.dp).height(14.dp)) // Judul
                    Spacer(modifier = Modifier.height(4.dp))
                    SkeletonBox(modifier = Modifier.width(60.dp).height(12.dp)) // Artis
                }
            }
        }
    }
}

// Skeleton untuk Section Artis (Bulat)
@Composable
fun SkeletonArtistSection() {
    Column {
        SkeletonBox(modifier = Modifier.width(160.dp).height(20.dp)) // Judul
        Spacer(modifier = Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            repeat(4) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    SkeletonBox(modifier = Modifier.size(70.dp), shape = CircleShape) // Foto Bulat
                    Spacer(modifier = Modifier.height(8.dp))
                    SkeletonBox(modifier = Modifier.width(60.dp).height(12.dp)) // Nama
                }
            }
        }
    }
}