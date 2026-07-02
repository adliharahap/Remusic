package com.example.remusic.ui.screen.playmusic

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import com.example.remusic.data.preferences.PlayerBackgroundStyle
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun PlayerBackground(
    style: PlayerBackgroundStyle,
    topColor: Color,
    bottomColor: Color,
    gradientBrush: Brush,
    coverUrl: String?,
    meshColors: List<Color> = emptyList()
) {
    if (style == PlayerBackgroundStyle.LINEAR_GRADIENT) {
        Box(modifier = Modifier.fillMaxSize().background(gradientBrush))
        return
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when (style) {
            PlayerBackgroundStyle.MESH_GRADIENT -> {
                // --- Premium Mesh Gradient (Apple Music style) ---
                // 4 warna dari Palette:
                //   color1 (index 0) = paling terang (accent)
                //   color2 (index 1) = medium bright
                //   color3 (index 2) = gelap (60% ke hitam)
                //   color4 (index 3) = paling gelap (85% ke hitam)
                //
                // Strategi:
                //   Background solid = color4 (paling gelap) → lyrics tetap terbaca
                //   Blob besar statis = color3 (gelap) → dominant coverage
                //   Blob kecil mengalir = color1 & color2 (accent) → cahaya lembut

                val color1Target = meshColors.getOrElse(0) { topColor }       // accent bright
                val color2Target = meshColors.getOrElse(1) { topColor.copy(alpha = 0.7f) }
                val color3Target = meshColors.getOrElse(2) { bottomColor }    // dark dominant
                val color4Target = meshColors.getOrElse(3) { Color(0xFF0A0A0A) } // darkest

                val color1 by animateColorAsState(targetValue = color1Target, animationSpec = tween(800), label = "color1")
                val color2 by animateColorAsState(targetValue = color2Target, animationSpec = tween(800), label = "color2")
                val color3 by animateColorAsState(targetValue = color3Target, animationSpec = tween(800), label = "color3")
                val color4 by animateColorAsState(targetValue = color4Target, animationSpec = tween(800), label = "color4")

                // Time-based animation — never resets, perfectly continuous
                var timeSeconds by remember { mutableFloatStateOf(0f) }
                LaunchedEffect(Unit) {
                    var lastNanos = 0L
                    while (true) {
                        withFrameNanos { frameNanos ->
                            if (lastNanos != 0L) {
                                val deltaSec = (frameNanos - lastNanos) / 1_000_000_000f
                                timeSeconds += deltaSec
                            }
                            lastNanos = frameNanos
                        }
                    }
                }

                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    val t = timeSeconds

                    // === LAYER 0: Solid dark base ===
                    drawRect(color = color4)

                    // === LAYER 1: Large dark blob (color3) — slow, dominant ===
                    // Covers most of the screen with a subtle drift
                    val blob3x = w * 0.5f + cos(t * 0.06f) * w * 0.15f + sin(t * 0.11f) * w * 0.05f
                    val blob3y = h * 0.5f + sin(t * 0.08f) * h * 0.12f + cos(t * 0.14f) * h * 0.04f
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(color3.copy(alpha = 0.85f), Color.Transparent),
                            center = Offset(blob3x, blob3y),
                            radius = w * 1.2f
                        ),
                        center = Offset(blob3x, blob3y),
                        radius = w * 1.2f
                    )

                    // === LAYER 2: Secondary dark blob (color3 lighter) — opposite drift ===
                    val blob3bx = w * 0.5f + cos(t * 0.09f + 3.1f) * w * 0.20f + sin(t * 0.05f) * w * 0.08f
                    val blob3by = h * 0.5f + sin(t * 0.07f + 1.5f) * h * 0.18f + cos(t * 0.12f) * h * 0.06f
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(color3.copy(alpha = 0.6f), Color.Transparent),
                            center = Offset(blob3bx, blob3by),
                            radius = w * 0.9f
                        ),
                        center = Offset(blob3bx, blob3by),
                        radius = w * 0.9f
                    )

                    // === LAYER 3: Accent blob 1 (color2) — medium speed, soft glow ===
                    val blob2x = w * 0.5f + cos(t * 0.13f) * w * 0.22f + sin(t * 0.31f) * w * 0.08f
                    val blob2y = h * 0.5f + sin(t * 0.19f) * h * 0.20f + cos(t * 0.27f) * h * 0.10f
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(color2.copy(alpha = 0.35f), Color.Transparent),
                            center = Offset(blob2x, blob2y),
                            radius = w * 0.7f
                        ),
                        center = Offset(blob2x, blob2y),
                        radius = w * 0.7f
                    )

                    // === LAYER 4: Accent blob 2 (color1) — brightest, smallest, flowing ===
                    val blob1x = w * 0.5f + cos(t * 0.15f + 2.0f) * w * 0.25f + sin(t * 0.23f) * w * 0.10f
                    val blob1y = h * 0.5f + sin(t * 0.17f + 0.8f) * h * 0.22f + cos(t * 0.29f) * h * 0.08f
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(color1.copy(alpha = 0.25f), Color.Transparent),
                            center = Offset(blob1x, blob1y),
                            radius = w * 0.55f
                        ),
                        center = Offset(blob1x, blob1y),
                        radius = w * 0.55f
                    )

                    // === LAYER 5: Small bright accent (color1) — fastest, sparkle-like ===
                    val blob1bx = w * 0.5f + cos(t * 0.21f + 4.2f) * w * 0.28f + sin(t * 0.37f) * w * 0.12f
                    val blob1by = h * 0.5f + sin(t * 0.24f + 2.5f) * h * 0.25f + cos(t * 0.33f) * h * 0.09f
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(color1.copy(alpha = 0.15f), Color.Transparent),
                            center = Offset(blob1bx, blob1by),
                            radius = w * 0.45f
                        ),
                        center = Offset(blob1bx, blob1by),
                        radius = w * 0.45f
                    )

                    // === LAYER 6: Extra dark atmosphere at edges ===
                    // Vignette effect to ensure edges stay dark for readability
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(Color.Transparent, color4.copy(alpha = 0.5f)),
                            center = Offset(w * 0.5f, h * 0.5f),
                            radius = w * 0.9f
                        )
                    )
                }
            }

            PlayerBackgroundStyle.LIQUID_GRADIENT -> {
                // --- Liquid Gradient: Flowing blended lights like lava lamp ---
                // Lights travel wider, blend into each other, softer & more organic
                val color1Target = meshColors.getOrElse(0) { topColor }
                val color2Target = meshColors.getOrElse(1) { topColor.copy(alpha = 0.7f) }
                val color3Target = meshColors.getOrElse(2) { bottomColor }
                val color4Target = meshColors.getOrElse(3) { Color(0xFF0A0A0A) }

                val color1 by animateColorAsState(targetValue = color1Target, animationSpec = tween(800), label = "lq_color1")
                val color2 by animateColorAsState(targetValue = color2Target, animationSpec = tween(800), label = "lq_color2")
                val color3 by animateColorAsState(targetValue = color3Target, animationSpec = tween(800), label = "lq_color3")
                val color4 by animateColorAsState(targetValue = color4Target, animationSpec = tween(800), label = "lq_color4")

                var timeSeconds by remember { mutableFloatStateOf(0f) }
                LaunchedEffect(Unit) {
                    var lastNanos = 0L
                    while (true) {
                        withFrameNanos { frameNanos ->
                            if (lastNanos != 0L) {
                                val deltaSec = (frameNanos - lastNanos) / 1_000_000_000f
                                timeSeconds += deltaSec
                            }
                            lastNanos = frameNanos
                        }
                    }
                }

                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    val t = timeSeconds

                    // === BASE ===
                    drawRect(color = color4)

                    // === LIQUID 1: Large dark glow — slow drift from corner to corner ===
                    val lq1x = w * 0.5f + cos(t * 0.04f) * w * 0.45f + sin(t * 0.07f) * w * 0.10f
                    val lq1y = h * 0.3f + sin(t * 0.05f) * h * 0.35f + cos(t * 0.03f) * h * 0.10f
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(color3.copy(alpha = 0.85f), color3.copy(alpha = 0.3f), Color.Transparent),
                            center = Offset(lq1x, lq1y),
                            radius = w * 1.0f
                        ),
                        center = Offset(lq1x, lq1y),
                        radius = w * 1.0f
                    )

                    // === LIQUID 2: Large dark glow — opposite path ===
                    val lq2x = w * 0.5f + sin(t * 0.05f + 3.5f) * w * 0.50f + cos(t * 0.08f) * w * 0.12f
                    val lq2y = h * 0.7f + cos(t * 0.04f + 1.8f) * h * 0.30f + sin(t * 0.06f) * h * 0.08f
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(color3.copy(alpha = 0.7f), color3.copy(alpha = 0.2f), Color.Transparent),
                            center = Offset(lq2x, lq2y),
                            radius = w * 0.9f
                        ),
                        center = Offset(lq2x, lq2y),
                        radius = w * 0.9f
                    )

                    // === LIQUID 3: Medium accent (color2) — flows across screen ===
                    val lq3x = w * 0.5f + cos(t * 0.09f + 1.2f) * w * 0.55f + sin(t * 0.15f) * w * 0.15f
                    val lq3y = h * 0.5f + sin(t * 0.07f + 2.0f) * h * 0.40f + cos(t * 0.11f) * h * 0.10f
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(color2.copy(alpha = 0.40f), color2.copy(alpha = 0.10f), Color.Transparent),
                            center = Offset(lq3x, lq3y),
                            radius = w * 0.75f
                        ),
                        center = Offset(lq3x, lq3y),
                        radius = w * 0.75f
                    )

                    // === LIQUID 4: Bright accent (color1) — elegant wandering light ===
                    val lq4x = w * 0.5f + sin(t * 0.11f + 0.5f) * w * 0.50f + cos(t * 0.19f) * w * 0.18f
                    val lq4y = h * 0.5f + cos(t * 0.08f + 3.8f) * h * 0.38f + sin(t * 0.13f) * h * 0.12f
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(color1.copy(alpha = 0.30f), color1.copy(alpha = 0.08f), Color.Transparent),
                            center = Offset(lq4x, lq4y),
                            radius = w * 0.6f
                        ),
                        center = Offset(lq4x, lq4y),
                        radius = w * 0.6f
                    )

                    // === LIQUID 5: Second bright (color1) — different phase ===
                    val lq5x = w * 0.5f + cos(t * 0.13f + 5.0f) * w * 0.55f + sin(t * 0.21f) * w * 0.12f
                    val lq5y = h * 0.5f + sin(t * 0.10f + 1.5f) * h * 0.42f + cos(t * 0.17f) * h * 0.08f
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(color1.copy(alpha = 0.18f), color1.copy(alpha = 0.05f), Color.Transparent),
                            center = Offset(lq5x, lq5y),
                            radius = w * 0.50f
                        ),
                        center = Offset(lq5x, lq5y),
                        radius = w * 0.50f
                    )

                    // === LIQUID 6: Subtle color2 wanderer at edges ===
                    val lq6x = w * 0.5f + sin(t * 0.06f + 2.7f) * w * 0.60f
                    val lq6y = h * 0.5f + cos(t * 0.09f + 4.0f) * h * 0.45f
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(color2.copy(alpha = 0.22f), color2.copy(alpha = 0.05f), Color.Transparent),
                            center = Offset(lq6x, lq6y),
                            radius = w * 0.55f
                        ),
                        center = Offset(lq6x, lq6y),
                        radius = w * 0.55f
                    )
                }
            }

            PlayerBackgroundStyle.BLURRED_COVER -> {
                val bottomColorAnim by animateColorAsState(targetValue = bottomColor, animationSpec = tween(800), label = "bottomColor")
                Box(modifier = Modifier.fillMaxSize().background(bottomColorAnim))

                if (!coverUrl.isNullOrEmpty()) {
                    Crossfade(
                        targetState = coverUrl,
                        animationSpec = tween(800),
                        label = "coverUrlCrossfade"
                    ) { currentUrl ->
                        AsyncImage(
                            model = currentUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .blur(80.dp)
                        )
                    }
                }
                
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f))
                )
            }
        }
    }
}
