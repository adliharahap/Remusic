package com.example.remusic.ui.components.homecomponents

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.remusic.ui.components.SkeletonBox
import com.example.remusic.ui.theme.AppFont

@Composable
fun SongSectionSkeleton(
    title: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        // Section title
        Text(
            text = title,
            fontFamily = AppFont.Poppins,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            color = Color.White,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        // Horizontal scrolling skeleton cards
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(end = 16.dp)
        ) {
            items(5) { // Show 5 skeleton cards
                Column(
                    modifier = Modifier.width(100.dp)
                ) {
                    // Album art skeleton
                    SkeletonBox(
                        modifier = Modifier
                            .size(100.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        shape = RoundedCornerShape(8.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Song title skeleton
                    SkeletonBox(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(16.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        shape = RoundedCornerShape(4.dp)
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Artist name skeleton
                    SkeletonBox(
                        modifier = Modifier
                            .fillMaxWidth(0.7f)
                            .height(14.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        shape = RoundedCornerShape(4.dp)
                    )
                }
            }
        }
    }
}
