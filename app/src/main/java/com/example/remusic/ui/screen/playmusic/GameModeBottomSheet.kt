package com.example.remusic.ui.screen.playmusic

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.remusic.ui.theme.AppFont
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameModeBottomSheet(
    sheetState: SheetState,
    isGameModeActive: Boolean,
    gameModeVolume: Float,
    dominantColors: List<Color>,
    onDismiss: () -> Unit,
    onToggleGameMode: () -> Unit,
    onVolumeChange: (Float) -> Unit
) {
    // Warna Dinamis
    val primaryColor = dominantColors.getOrElse(0) { Color.Green }

    // Gradient Background: Dominant Color (Top/Center) -> Black/Dark (Bottom)
    val sheetBrush = Brush.verticalGradient(
        colors = listOf(
            primaryColor.copy(alpha = 0.9f), // Lebih solid (90%)
            Color.Black
        )
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.Transparent, // Custom background
        dragHandle = null
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(sheetBrush)
                .padding(24.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.SportsEsports,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(
                            text = "Game Mode",
                            color = Color.White,
                            fontFamily = AppFont.MontserratBold,
                            fontSize = 20.sp
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                if (isGameModeActive) {
                    // Active State
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.Black.copy(0.3f), RoundedCornerShape(12.dp))
                            .border(
                                1.dp,
                                primaryColor.copy(alpha = 0.5f),
                                RoundedCornerShape(12.dp)
                            )
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Game Mode is Active",
                            color = primaryColor,
                            fontFamily = AppFont.MontserratBold,
                            fontSize = 20.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Music volume is managed internally so you can hear game sounds.",
                            color = Color.White.copy(0.7f),
                            fontFamily = AppFont.MontserratRegular,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        // Volume Slider
                        Text(
                            text = "Volume: ${(gameModeVolume * 100).roundToInt()}%",
                            color = Color.White,
                            fontFamily = AppFont.MontserratMedium,
                            fontSize = 16.sp
                        )
                        Slider(
                            value = gameModeVolume,
                            onValueChange = onVolumeChange,
                            valueRange = 0f..1f,
                            colors = SliderDefaults.colors(
                                thumbColor = Color.White,
                                activeTrackColor = primaryColor,
                                inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                            ),
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )

                        Spacer(modifier = Modifier.height(24.dp))
                        
                        Button(
                            onClick = {
                                onToggleGameMode()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFCF6679)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                        ) {
                            Text(
                                "Disable Game Mode",
                                color = Color.White,
                                fontFamily = AppFont.MontserratBold
                            )
                        }
                    }
                } else {
                    // Inactive State
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Reduce music volume while gaming without changing your device volume.",
                            color = Color.White.copy(0.8f),
                            fontFamily = AppFont.MontserratRegular,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(bottom = 32.dp)
                        )

                        Button(
                            onClick = {
                                onToggleGameMode()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                        ) {
                            Text(
                                "Enable Game Mode",
                                color = Color.White,
                                fontFamily = AppFont.MontserratBold
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}
