package com.example.remusic.ui.components.homecomponents

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrolist.innertube.pages.HomePage

@Composable
fun QuickMoodChips(
    chips: List<HomePage.Chip>,
    onChipClick: (HomePage.Chip) -> Unit,
    modifier: Modifier = Modifier,
    activeParams: String? = null
) {
    LazyRow(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(chips) { chip ->
            val isActive = chip.endpoint?.params == activeParams
            
            val backgroundColors = if (isActive) {
                listOf(Color.White.copy(alpha = 0.4f), Color.White.copy(alpha = 0.2f))
            } else {
                listOf(Color.White.copy(alpha = 0.15f), Color.White.copy(alpha = 0.05f))
            }
            
            val borderColor = if (isActive) Color.White.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.2f)
            val textColor = if (isActive) Color.White else Color.White.copy(alpha = 0.8f)
            val fontWeight = if (isActive) FontWeight.Bold else FontWeight.SemiBold

            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = backgroundColors
                        )
                    )
                    .border(
                        width = if (isActive) 1.5.dp else 1.dp,
                        color = borderColor,
                        shape = CircleShape
                    )
                    .clickable { onChipClick(chip) }
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = chip.title,
                    color = textColor,
                    fontSize = 14.sp,
                    fontWeight = fontWeight,
                    letterSpacing = 0.5.sp
                )
            }
        }
    }
}
