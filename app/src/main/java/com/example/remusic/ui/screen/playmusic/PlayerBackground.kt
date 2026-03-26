package com.example.remusic.ui.screen.playmusic

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.remusic.data.preferences.PlayerBackgroundStyle
import kotlin.math.sin
import kotlin.math.PI

@Composable
fun PlayerBackground(
    style: PlayerBackgroundStyle,
    topColor: Color,
    bottomColor: Color,
    gradientBrush: Brush,
    coverUrl: String?
) {
    if (style == PlayerBackgroundStyle.LINEAR_GRADIENT) {
        Box(modifier = Modifier.fillMaxSize().background(gradientBrush))
        return
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when (style) {
            PlayerBackgroundStyle.MESH_GRADIENT -> {
                val infiniteTransition = rememberInfiniteTransition(label = "mesh_transition")
                val floatAnim1 by infiniteTransition.animateFloat(
                    initialValue = 0f,
                    targetValue = 2f * PI.toFloat(),
                    animationSpec = infiniteRepeatable(
                        animation = tween(15000),
                        repeatMode = RepeatMode.Restart
                    ),
                    label = "mesh_anim_1"
                )
                val floatAnim2 by infiniteTransition.animateFloat(
                    initialValue = 0f,
                    targetValue = 2f * PI.toFloat(),
                    animationSpec = infiniteRepeatable(
                        animation = tween(20000),
                        repeatMode = RepeatMode.Restart
                    ),
                    label = "mesh_anim_2"
                )

                Canvas(modifier = Modifier.fillMaxSize().blur(80.dp)) {
                    val w = size.width
                    val h = size.height

                    val offsetX1 = w * 0.5f + (w * 0.3f) * sin(floatAnim1)
                    val offsetY1 = h * 0.5f + (h * 0.2f) * sin(floatAnim1 * 0.5f + 1f)
                    
                    val offsetX2 = w * 0.5f + (w * 0.3f) * sin(floatAnim2 + 2f)
                    val offsetY2 = h * 0.5f + (h * 0.2f) * sin(floatAnim2 * 1.5f)

                    drawRect(color = bottomColor)

                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(topColor.copy(alpha = 0.8f), Color.Transparent),
                            center = Offset(offsetX1, offsetY1),
                            radius = w * 0.8f
                        ),
                        center = Offset(offsetX1, offsetY1),
                        radius = w * 0.8f
                    )

                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(topColor.copy(alpha = 0.5f), Color.Transparent),
                            center = Offset(offsetX2, offsetY2),
                            radius = w * 0.9f
                        ),
                        center = Offset(offsetX2, offsetY2),
                        radius = w * 0.9f
                    )
                }
            }
            PlayerBackgroundStyle.RADIAL_GRADIENT -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.radialGradient(
                                colors = listOf(topColor, bottomColor),
                                radius = 1000f
                            )
                        )
                )
            }
            PlayerBackgroundStyle.BLURRED_COVER -> {
                Box(modifier = Modifier.fillMaxSize().background(bottomColor))

                if (!coverUrl.isNullOrEmpty()) {
                    AsyncImage(
                        model = coverUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .blur(80.dp)
                    )
                }
                
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f))
                )
            }
            else -> {}
        }
    }
}
