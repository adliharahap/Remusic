package com.example.remusic.ui.screen.playmusic

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.remusic.ui.theme.AppFont

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepTimerBottomSheet(
    sheetState: SheetState,
    isTimerActive: Boolean,
    dominantColors: List<Color>,
    onDismiss: () -> Unit,
    onSetTimer: (Int) -> Unit,
    onCancelTimer: () -> Unit
) {
    var showCustomInput by remember { mutableStateOf(false) }
    
    // State untuk Custom Picker
    var selectedHour by remember { mutableIntStateOf(0) }
    var selectedMinute by remember { mutableIntStateOf(0) }

    // Warna Dinamis
    val primaryColor = dominantColors.getOrElse(0) { Color.Green }
    val backgroundColor = dominantColors.getOrElse(1) { Color(0xFF1E1E1E) }
    
    // Gradient Background untuk Sheet
    val sheetBrush = Brush.verticalGradient(
        colors = listOf(
            backgroundColor.copy(alpha = 0.9f),
            Color.Black
        )
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.Transparent, // Kita pakai Box custom background
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
                            imageVector = Icons.Default.Timer,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(
                            text = "Sleep Timer",
                            color = Color.White,
                            fontFamily = AppFont.MontserratBold,
                            fontSize = 20.sp
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.Gray)
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                if (isTimerActive) {
                    // Active State
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.Black.copy(0.3f), RoundedCornerShape(12.dp))
                            .border(1.dp, primaryColor.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Timer is Active",
                            color = primaryColor,
                            fontFamily = AppFont.MontserratBold,
                            fontSize = 22.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Music will stop automatically.",
                            color = Color.White.copy(0.7f),
                            fontFamily = AppFont.MontserratRegular,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = {
                                onCancelTimer()
                                onDismiss()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFCF6679)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                        ) {
                            Text("Stop Timer", color = Color.White, fontFamily = AppFont.MontserratBold)
                        }
                    }
                } else {
                    // Selection State
                    if (showCustomInput) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Set Duration",
                                color = Color.White,
                                fontFamily = AppFont.MontserratMedium,
                                fontSize = 16.sp,
                                modifier = Modifier.padding(bottom = 16.dp)
                            )

                            // Labels for Hours and Minutes
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp),
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = "Hours",
                                    color = Color.Gray,
                                    fontFamily = AppFont.MontserratRegular,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(end = 32.dp)
                                )
                                Text(
                                    text = "Minutes",
                                    color = Color.Gray,
                                    fontFamily = AppFont.MontserratRegular,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(start = 32.dp)
                                )
                            }

                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                val initialTime = remember { java.time.LocalTime.of(selectedHour, selectedMinute) }
                                com.commandiron.wheel_picker_compose.WheelTimePicker(
                                    startTime = initialTime,
                                    timeFormat = com.commandiron.wheel_picker_compose.core.TimeFormat.HOUR_24,
                                    textStyle = androidx.compose.ui.text.TextStyle(
                                        color = Color.White,
                                        fontSize = 32.sp,
                                        fontFamily = AppFont.MontserratBold
                                    ),
                                    size = androidx.compose.ui.unit.DpSize(350.dp, 250.dp), // Lebih besar lagi
                                    rowCount = 5,
                                    selectorProperties = com.commandiron.wheel_picker_compose.core.WheelPickerDefaults.selectorProperties(
                                        enabled = true,
                                        color = Color.White.copy(alpha = 0.1f),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, primaryColor)
                                    ),
                                    onSnappedTime = { time ->
                                        selectedHour = time.hour
                                        selectedMinute = time.minute
                                    }
                                )
                            }

                            Spacer(modifier = Modifier.height(32.dp))

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Button(
                                    onClick = { showCustomInput = false },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Cancel", color = Color.Gray, fontFamily = AppFont.MontserratMedium)
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                Button(
                                    onClick = {
                                        val totalMinutes = (selectedHour * 60) + selectedMinute
                                        if (totalMinutes > 0) {
                                            onSetTimer(totalMinutes)
                                            onDismiss()
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f).height(50.dp)
                                ) {
                                    Text("Start", color = Color.Black, fontFamily = AppFont.MontserratBold)
                                }
                            }
                        }
                    } else {
                        val options = listOf(
                            20 to "20 Minutes",
                            30 to "30 Minutes",
                            60 to "1 Hour",
                            120 to "2 Hours"
                        )

                        options.forEach { (minutes, label) ->
                            TimerOptionItem(label) {
                                onSetTimer(minutes)
                                onDismiss()
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                        }

                        TimerOptionItem("Custom") {
                            showCustomInput = true
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
fun TimerOptionItem(text: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(0.3f), RoundedCornerShape(12.dp))
            .border(1.dp, Color.White.copy(0.1f), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = text,
            color = Color.White,
            fontFamily = AppFont.MontserratMedium,
            fontSize = 16.sp
        )
        Icon(
            imageVector = Icons.Default.Timer,
            contentDescription = null,
            tint = Color.White, // Ubah icon jadi putih semua
            modifier = Modifier.size(20.dp)
        )
    }
}
