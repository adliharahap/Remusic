package com.example.remusic.ui.components.uploadsongcomponent

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.remusic.ui.theme.AppFont

@Composable
fun SongListHeader(songCount: Int, onSortClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "All Songs ($songCount)",
            color = Color.White,
            fontFamily = AppFont.RobotoBold,
            fontSize = 16.sp
        )
        Spacer(modifier = Modifier.weight(1f)) // Mendorong ikon ke kanan
        IconButton(onClick = onSortClick) {
            Icon(
                imageVector = Icons.Default.Sort,
                contentDescription = "Sort Songs",
                tint = Color.White
            )
        }
    }
}