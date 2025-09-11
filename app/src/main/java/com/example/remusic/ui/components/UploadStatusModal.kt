package com.example.remusic.ui.components // Sesuaikan dengan package project-mu

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircleOutline
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.remusic.ui.theme.AppFont // Pastikan path ini benar

/**
 * Mendefinisikan kondisi yang bisa ditampilkan oleh UploadStatusModal.
 */
enum class UploadModalState {
    SUCCESS,
    FAILURE
}

@Composable
fun UploadStatusModal(
    state: UploadModalState,
    onDismiss: () -> Unit
) {
    // --- 1. State untuk Animasi (tetap sama) ---
    var startAnimation by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0.3f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "iconScaleAnimation"
    )

    LaunchedEffect(Unit) {
        startAnimation = true
    }

    // --- 2. Tentukan konten berdasarkan state ---
    val icon: ImageVector
    val iconTint: Color
    val title: String
    val message: String
    val buttonText: String

    when (state) {
        UploadModalState.SUCCESS -> {
            icon = Icons.Default.CheckCircleOutline
            iconTint = Color(0xFF4CAF50) // Green
            title = "Upload Berhasil"
            message = "Lagu Anda berhasil diunggah dan akan segera tersedia."
            buttonText = "Selesai"
        }
        UploadModalState.FAILURE -> {
            icon = Icons.Default.ErrorOutline
            iconTint = Color(0xFFF44336) // Red
            title = "Upload Gagal"
            message = "Terjadi kesalahan saat mengunggah lagu. Silakan coba lagi."
            buttonText = "Coba Lagi"
        }
    }

    // --- 3. Tampilan UI (menggunakan variabel dari atas) ---
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF2E2E2E)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .scale(scale)
                        .background(iconTint.copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = "Status Icon",
                        tint = iconTint,
                        modifier = Modifier.size(50.dp)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = title,
                    fontSize = 22.sp,
                    fontFamily = AppFont.RobotoBold,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = message,
                    fontSize = 16.sp,
                    fontFamily = AppFont.RobotoRegular,
                    color = Color.LightGray,
                    textAlign = TextAlign.Center,
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = iconTint),
                    modifier = Modifier.fillMaxWidth().height(50.dp)
                ) {
                    Text(
                        text = buttonText,
                        color = Color.White,
                        fontFamily = AppFont.RobotoMedium,
                        fontSize = 16.sp
                    )
                }
            }
        }
    }
}