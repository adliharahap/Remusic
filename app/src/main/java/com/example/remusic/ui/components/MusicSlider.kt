package com.example.remusic.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    // Parameter Styling (Optional, ada default-nya)
    activeColor: Color = Color.White,
    inactiveColor: Color = Color.White.copy(alpha = 0.4f),
    thumbSize: Dp = 14.dp,
    trackHeight: Dp = 3.dp,
    trackShape: Shape = RoundedCornerShape(2.dp)
) {
    Slider(
        value = value,
        onValueChange = onValueChange,
        onValueChangeFinished = onValueChangeFinished,
        valueRange = 0f..1f,
        modifier = modifier,
        // 1. Sembunyikan default colors
        colors = SliderDefaults.colors(
            thumbColor = Color.Transparent,
            activeTrackColor = Color.Transparent,
            inactiveTrackColor = Color.Transparent
        ),
        // 2. Custom Thumb
        thumb = {
            Box(
                modifier = Modifier
                    .offset(y = 1.dp)
                    .size(thumbSize)
                    .background(color = activeColor, shape = CircleShape)
            )
        },
        // 3. Custom Track
        track = { sliderPositions ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(trackHeight)
            ) {
                // Track Belakang (Inactive)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(color = inactiveColor, shape = trackShape)
                )
                // Track Depan (Active)
                Box(
                    modifier = Modifier
                        .fillMaxWidth(sliderPositions.value)
                        .fillMaxHeight()
                        .background(color = activeColor, shape = trackShape)
                )
            }
        }
    )
}