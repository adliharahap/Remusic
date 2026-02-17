package com.example.remusic.ui.components.skeletons

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.remusic.ui.components.shimmerEffect

@Composable
fun PlaylistDetailSkeleton() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .shimmerEffect() // Apply shimmer to the whole container or specific parts? Usually specific parts.
    ) {
        // --- HEADER SKELETON ---
        // Mimic generic header (Square/Circle + Text)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 50.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Image Placeholder (Circle/Square hybrid or just generic square)
            Box(
                modifier = Modifier
                    .size(200.dp)
                    .clip(RoundedCornerShape(12.dp)) // Generic shape
                    .background(Color.White.copy(0.1f))
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Title Placeholder
            Box(
                modifier = Modifier
                    .width(180.dp)
                    .height(28.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White.copy(0.1f))
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Subtitle/Stats Placeholder
            Box(
                modifier = Modifier
                    .width(120.dp)
                    .height(16.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White.copy(0.1f))
            )
        }
        
        // --- ACTION BUTTONS SKELETON ---
        Row(
             modifier = Modifier
                 .fillMaxWidth()
                 .padding(horizontal = 24.dp, vertical = 16.dp),
             horizontalArrangement = Arrangement.Center
        ) {
             Box(
                 modifier = Modifier
                     .size(50.dp)
                     .clip(CircleShape)
                     .background(Color.White.copy(0.1f))
             )
             Spacer(modifier = Modifier.width(32.dp))
             Box(
                 modifier = Modifier
                     .size(50.dp)
                     .clip(CircleShape)
                     .background(Color.White.copy(0.1f))
             )
        }

        // --- SONG LIST SKELETON ---
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            repeat(6) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Image/Index
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White.copy(0.1f))
                    )
                    
                    Spacer(modifier = Modifier.width(16.dp))
                    
                    // Texts
                    Column(modifier = Modifier.weight(1f)) {
                        Box(
                            modifier = Modifier
                                .width(120.dp)
                                .height(16.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color.White.copy(0.1f))
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Box(
                            modifier = Modifier
                                .width(80.dp)
                                .height(12.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color.White.copy(0.1f))
                        )
                    }
                }
            }
        }
    }
}


