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
    onCancelTimer: () -> Unit,
    timerEndTime: Long? = null
) {

    // State untuk Custom Picker (Default 1 Jam)
    val initialHour = 1
    val initialMinute = 0
    val selectedTime = remember { mutableStateOf(Pair(initialHour, initialMinute)) }

    // Warna Dinamis
    val primaryColor = dominantColors.getOrElse(0) { Color.Green }
    val secondaryColor = dominantColors.getOrElse(1) { Color(0xFF1E1E1E) }

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
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                if (isTimerActive) {
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
                            text = "Timer is Active",
                            color = Color.White, // REQ: White Text
                            fontFamily = AppFont.MontserratBold,
                            fontSize = 20.sp
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        // REQ: Countdown Timer
                        if (timerEndTime != null && timerEndTime > System.currentTimeMillis()) {
                            SleepTimerCountdown(endTime = timerEndTime)
                        } else {
                            // Fallback styling
                            Text(
                                "--:--:--",
                                color = Color.White,
                                fontSize = 32.sp,
                                fontFamily = AppFont.MontserratBold
                            )
                        }

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
                            Text(
                                "Stop Timer",
                                color = Color.White,
                                fontFamily = AppFont.MontserratBold
                            )
                        }
                    }
                } else {
                    // Selection State
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Set Duration",
                            color = Color.White,
                            fontFamily = AppFont.MontserratMedium,
                            fontSize = 16.sp,
                            modifier = Modifier.padding(bottom = 24.dp)
                        )

                        // Labels for Hours and Minutes (Subtle)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 0.dp), // Rapatkan ke picker
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "HOURS",
                                color = Color.White.copy(0.6f), // REQ: Lebih transparan/subtle
                                fontFamily = AppFont.MontserratRegular,
                                fontSize = 10.sp, // REQ: Kecil
                                modifier = Modifier.padding(end = 50.dp),
                                letterSpacing = 2.sp
                            )
                            Text(
                                text = "MINUTES",
                                color = Color.White.copy(0.6f),
                                fontFamily = AppFont.MontserratRegular,
                                fontSize = 10.sp,
                                modifier = Modifier.padding(start = 50.dp),
                                letterSpacing = 2.sp
                            )
                        }

                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            SleepTimerSelector(
                                primaryColor = primaryColor,
                                initialHour = initialHour,
                                initialMinute = initialMinute,
                                onTimeChanged = { h, m ->
                                    selectedTime.value = Pair(h, m)
                                }
                            )
                        }

                        Spacer(modifier = Modifier.height(32.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Button(
                                onClick = { onDismiss() },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    "Cancel",
                                    color = Color.White,
                                    fontFamily = AppFont.MontserratBold
                                ) // REQ: White text
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Button(
                                onClick = {
                                    val totalMinutes = (selectedTime.value.first * 60) + selectedTime.value.second
                                    if (totalMinutes > 0) {
                                        onSetTimer(totalMinutes)
                                        onDismiss()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f).height(50.dp)
                            ) {
                                Text(
                                    "Start",
                                    color = Color.White,
                                    fontFamily = AppFont.MontserratBold
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
fun SleepTimerCountdown(endTime: Long) {
    var timeLeft by remember { androidx.compose.runtime.mutableLongStateOf(endTime - System.currentTimeMillis()) }

    androidx.compose.runtime.LaunchedEffect(endTime) {
        while (timeLeft > 0) {
            timeLeft = endTime - System.currentTimeMillis()
            kotlinx.coroutines.delay(1000)
        }
    }

    val hours = java.util.concurrent.TimeUnit.MILLISECONDS.toHours(timeLeft)
    val minutes = java.util.concurrent.TimeUnit.MILLISECONDS.toMinutes(timeLeft) % 60
    val seconds = java.util.concurrent.TimeUnit.MILLISECONDS.toSeconds(timeLeft) % 60

    Text(
        text = String.format("%02dh %02dm %02ds", hours, minutes, seconds),
        color = Color.White,
        fontFamily = AppFont.MontserratBold,
        fontSize = 32.sp
    )
}

@Composable
fun SleepTimerSelector(
    primaryColor: Color,
    initialHour: Int,
    initialMinute: Int,
    onTimeChanged: (Int, Int) -> Unit
) {
    val hours = (0..23).toList()
    val minutes = (0..59).toList()

    val hourState = androidx.compose.foundation.lazy.rememberLazyListState(initialFirstVisibleItemIndex = initialHour)
    val minuteState = androidx.compose.foundation.lazy.rememberLazyListState(initialFirstVisibleItemIndex = initialMinute)

    val itemHeight = 50.dp
    val visibleItemsCount = 5
    val listHeight = itemHeight * visibleItemsCount

    // Helper to calculate the center item based on scroll state
    fun calculateCenterItemIndex(state: androidx.compose.foundation.lazy.LazyListState, itemsCount: Int): Int {
        val layoutInfo = state.layoutInfo
        val visibleItemsInfo = layoutInfo.visibleItemsInfo
        if (visibleItemsInfo.isEmpty()) return 0
        
        val center = layoutInfo.viewportStartOffset + (layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset) / 2
        
        var closestItemInfo = visibleItemsInfo.first()
        var minDistance = Int.MAX_VALUE
        
        for (itemInfo in visibleItemsInfo) {
            val itemCenter = itemInfo.offset + itemInfo.size / 2
            val distance = kotlin.math.abs(itemCenter - center)
            if (distance < minDistance) {
                minDistance = distance
                closestItemInfo = itemInfo
            }
        }
        return closestItemInfo.index.coerceIn(0, itemsCount - 1)
    }

    // Effect to report time changes when scrolling stops
    androidx.compose.runtime.LaunchedEffect(hourState.isScrollInProgress, minuteState.isScrollInProgress) {
        if (!hourState.isScrollInProgress && !minuteState.isScrollInProgress) {
            val selectedHour = calculateCenterItemIndex(hourState, hours.size)
            val selectedMinute = calculateCenterItemIndex(minuteState, minutes.size)
            onTimeChanged(selectedHour, selectedMinute)
        }
    }

    Box(
        modifier = Modifier
            .width(350.dp)
            .height(listHeight),
        contentAlignment = Alignment.Center
    ) {
        // Selection highlight box
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(itemHeight)
                .background(Color.White.copy(alpha = 0.1f))
                .border(androidx.compose.foundation.BorderStroke(1.dp, primaryColor))
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            // Hours Wheel
            androidx.compose.foundation.lazy.LazyColumn(
                state = hourState,
                modifier = Modifier
                    .weight(1f)
                    .height(listHeight),
                horizontalAlignment = Alignment.CenterHorizontally,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = itemHeight * 2),
                flingBehavior = androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior(lazyListState = hourState)
            ) {
                items(hours.size) { index ->
                    val isSelected = index == calculateCenterItemIndex(hourState, hours.size)
                    Box(
                        modifier = Modifier.height(itemHeight),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = String.format("%02d", hours[index]),
                            style = androidx.compose.ui.text.TextStyle(
                                color = if (isSelected) Color.White else Color.White.copy(alpha = 0.5f),
                                fontSize = if (isSelected) 32.sp else 24.sp,
                                fontFamily = AppFont.MontserratBold
                            )
                        )
                    }
                }
            }

            // Separator Box to align with wheels visually
            Box(
                modifier = Modifier
                    .width(itemHeight)
                    .height(listHeight),
                contentAlignment = Alignment.Center
            ) {
                 Text(
                    text = ":",
                    style = androidx.compose.ui.text.TextStyle(
                        color = Color.White,
                        fontSize = 32.sp,
                        fontFamily = AppFont.MontserratBold
                    )
                )
            }

            // Minutes Wheel
            androidx.compose.foundation.lazy.LazyColumn(
                state = minuteState,
                modifier = Modifier
                    .weight(1f)
                    .height(listHeight),
                horizontalAlignment = Alignment.CenterHorizontally,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = itemHeight * 2),
                flingBehavior = androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior(lazyListState = minuteState)
            ) {
                items(minutes.size) { index ->
                    val isSelected = index == calculateCenterItemIndex(minuteState, minutes.size)
                    Box(
                        modifier = Modifier.height(itemHeight),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = String.format("%02d", minutes[index]),
                            style = androidx.compose.ui.text.TextStyle(
                                color = if (isSelected) Color.White else Color.White.copy(alpha = 0.5f),
                                fontSize = if (isSelected) 32.sp else 24.sp,
                                fontFamily = AppFont.MontserratBold
                            )
                        )
                    }
                }
            }
        }
    }
}





