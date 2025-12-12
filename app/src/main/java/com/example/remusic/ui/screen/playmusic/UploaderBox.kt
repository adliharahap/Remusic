package com.example.remusic.ui.screen.playmusic

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.remusic.viewmodel.playmusic.UploaderUiState
import com.example.remusic.viewmodel.playmusic.UploaderViewModel
import com.example.remusic.viewmodel.playmusic.User
import java.util.*

// UTILITY FUNCTION
fun String.capitalizeWords(): String =
    this.split(' ').joinToString(" ") { word ->
        word.lowercase().replaceFirstChar {
            if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
        }
    }

// Komponen utama yang dipanggil dari luar
@Composable
fun UploaderBox(
    uploaderId: String,
    onSeeAllClick: () -> Unit = {},
    viewModel: UploaderViewModel = viewModel()
) {
    val context = LocalContext.current
    LaunchedEffect(uploaderId) {
        if (uploaderId.isNotBlank()) {
            viewModel.fetchUploaderInfo(uploaderId, context)
        }
    }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    when (val state = uiState) {
        is UploaderUiState.Loading -> {
            Box(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .fillMaxWidth().height(254.dp) // Sesuaikan tinggi agar sama
                    .background(Color(0xFF282828), shape = RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }
        }
        is UploaderUiState.Success -> {
            ActualUploaderBoxContent(
                user = state.user,
                onSeeAllClick = onSeeAllClick
            )
        }
        is UploaderUiState.Error -> {
            Box(modifier = Modifier.padding(16.dp)) {
                Text(text = "Gagal memuat info: ${state.message}")
            }
        }
    }
}

// Komponen yang berisi UI sebenarnya
@Composable
private fun ActualUploaderBoxContent(
    user: User,
    onSeeAllClick: () -> Unit
) {
    val isOwner = user.role.equals("Owner", ignoreCase = true)
    val cardBackgroundColor = if (isOwner) Color.Black.copy(alpha = 0.8f) else Color(0xFF282828)

    Column(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .fillMaxWidth()
            .then(if (isOwner) Modifier.animatedOwnerBorder() else Modifier)
            .background(cardBackgroundColor, shape = RoundedCornerShape(16.dp))
            .padding(20.dp),
    ) {
        Text(
            text = "Diupload oleh",
            color = Color.White.copy(0.7f),
            fontSize = 14.sp
        )
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(64.dp)) {
                AsyncImage(
                    model = user.photoUrl,
                    contentDescription = "Profile Picture",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                )
                if (isOwner) {
                    AnimatedOwnerBadge()
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = user.displayName.capitalizeWords(),
                    color = Color.White,
                    fontSize = 18.sp,
                    maxLines = 1,
                    style = if (isOwner) TextStyle(shadow = Shadow(Color.White.copy(0.5f), blurRadius = 10f)) else TextStyle.Default
                )
                Spacer(modifier = Modifier.height(2.dp))
                if (isOwner) {
                    ShimmerText(text = user.role)
                } else {
                    AnimatedUploaderRoleText(text = user.role)
                }
            }
        }
        Spacer(modifier = Modifier.height(24.dp))

        val buttonBgStart = Color(0xFF4C4D4C)
        val buttonBgEnd = Color(0xFF2D2F2F)
        Button(
            onClick = onSeeAllClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = Color.White),
            contentPadding = PaddingValues(),
            shape = RoundedCornerShape(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.horizontalGradient(colors = listOf(buttonBgStart, buttonBgEnd)),
                        shape = RoundedCornerShape(10.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(imageVector = Icons.Rounded.LibraryMusic, contentDescription = "Music Icon", modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "Lihat Semua Lagu", fontSize = 16.sp)
                }
            }
        }
    }
}

// --- Komponen-komponen Animasi ---

fun Modifier.animatedOwnerBorder(cornerRadius: Dp = 16.dp): Modifier = composed {
    val infiniteTransition = rememberInfiniteTransition(label = "owner_border_transition")
    val angle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ), label = "owner_border_angle"
    )

    val borderBrush = Brush.sweepGradient(
        colors = listOf(
            Color(0xFFF7D43A), Color(0xFFB5880D), Color.Transparent,
            Color.Transparent, Color(0xFFF7D43A)
        )
    )

    this
        .clip(RoundedCornerShape(cornerRadius))
        .drawBehind {
            drawIntoCanvas { canvas ->
                val paint = Paint()
                val frameworkPaint = paint.asFrameworkPaint()
                frameworkPaint.color = Color.Transparent.toArgb()
                frameworkPaint.setShadowLayer(30f, 0f, 0f, Color(0xFFF7D43A).copy(alpha = 0.5f).toArgb())
                canvas.drawRoundRect(0f, 0f, size.width, size.height, cornerRadius.toPx(), cornerRadius.toPx(), paint)
            }
            rotate(angle) {
                drawRoundRect(
                    brush = borderBrush,
                    size = size,
                    cornerRadius = CornerRadius(cornerRadius.toPx()),
                    style = Stroke(width = 4.dp.toPx())
                )
            }
        }
}

@Composable
fun BoxScope.AnimatedOwnerBadge() {
    val infiniteTransition = rememberInfiniteTransition(label = "owner_badge_transition")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 1.2f,
        animationSpec = infiniteRepeatable(tween(1000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "badge_scale"
    )
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "badge_glow"
    )
    val goldColor = Color(0xFFF7D43A)

    Box(
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .size(24.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .drawBehind {
                drawIntoCanvas {
                    val paint = Paint()
                    val frameworkPaint = paint.asFrameworkPaint()
                    frameworkPaint.color = Color.Transparent.toArgb()
                    frameworkPaint.setShadowLayer(20f, 0f, 0f, goldColor.copy(alpha = glowAlpha).toArgb())
                    it.drawCircle(center, size.maxDimension / 2, paint)
                }
            }
            .background(Brush.verticalGradient(listOf(Color(0xFFF7D43A), Color(0xFFB5880D))), CircleShape)
            .padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Rounded.WorkspacePremium, "Owner Badge", tint = Color.White, modifier = Modifier.fillMaxSize())
    }
}

@Composable
fun ShimmerText(text: String) {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer_text_transition")
    val translateAnim by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1500f,
        animationSpec = infiniteRepeatable(tween(2000, easing = LinearEasing), RepeatMode.Restart), label = "shimmer_translate"
    )
    val brush = Brush.linearGradient(
        colors = listOf(Color(0xFFF7D43A), Color(0xFFB5880D), Color(0xFFF7D43A).copy(0.5f), Color(0xFFB5880D), Color(0xFFF7D43A)),
        start = Offset(translateAnim - 500, translateAnim - 500),
        end = Offset(translateAnim, translateAnim)
    )
    Text(text = text, fontSize = 14.sp, style = TextStyle(brush = brush))
}

@Composable
fun AnimatedUploaderRoleText(text: String) {
    val infiniteTransition = rememberInfiniteTransition(label = "uploader_role_transition")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.1f, targetValue = 0.6f,
        animationSpec = infiniteRepeatable(tween(1500, easing = LinearEasing), RepeatMode.Reverse), label = "uploader_glow"
    )
    val uploaderColor = Color(0xFF1DB954)

    Text(
        text = text, color = uploaderColor, fontSize = 14.sp,
        modifier = Modifier.drawBehind {
            drawIntoCanvas { canvas ->
                val paint = Paint()
                val frameworkPaint = paint.asFrameworkPaint()
                frameworkPaint.color = Color.Transparent.toArgb()
                frameworkPaint.setShadowLayer(15f, 0f, 0f, uploaderColor.copy(alpha = glowAlpha).toArgb())
                canvas.nativeCanvas.drawText(text, 0f, size.height / 2 + 10, frameworkPaint)
            }
        }
    )
}