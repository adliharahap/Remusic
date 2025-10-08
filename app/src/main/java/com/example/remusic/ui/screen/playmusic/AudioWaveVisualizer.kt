package com.example.remusic.ui.screen.playmusic

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

fun ClosedFloatingPointRange<Float>.random() =
    Random.nextFloat() * (endInclusive - start) + start

@Composable
fun AudioWaveVisualizer(
    modifier: Modifier = Modifier,
    barCount: Int = 20,
    maxHeight: Dp = 40.dp,
    minHeight: Dp = 4.dp,
    barWidth: Dp = 4.dp,
    barColor: Color = Color.White,
    animationDuration: Int = 300
) {
    val halfMax = maxHeight.value / 2
    val barHeights = remember { List(barCount) { Animatable(halfMax) } }

    LaunchedEffect(Unit) {
        while (true) {
            barHeights.forEach { anim ->
                launch {
                    // Buat random ± range dari tengah
                    val randomHeight = halfMax + Random.nextFloat() * (halfMax - minHeight.value) * if (Random.nextBoolean()) 1 else -1
                    anim.animateTo(
                        targetValue = randomHeight,
                        animationSpec = tween(durationMillis = animationDuration, easing = LinearEasing)
                    )
                }
            }
            delay(animationDuration.toLong())
        }
    }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        barHeights.forEach { anim ->
            Box(
                modifier = Modifier
                    .width(barWidth)
                    .height(anim.value.dp)
                    .background(barColor, shape = RoundedCornerShape(50))
            )
        }
    }
}
