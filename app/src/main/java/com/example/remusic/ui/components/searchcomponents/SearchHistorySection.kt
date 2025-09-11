package com.example.remusic.ui.components.searchcomponents

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
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
fun SearchHistorySection(
    historyItems: List<String>,
    onRemoveClick: (String) -> Unit = {}
) {
    Column {
        Text(
            text = "Riwayat Pencarian",
            color = Color.White,
            fontSize = 18.sp,
            fontFamily = AppFont.RobotoBold,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Column {
            historyItems.forEach { item ->
                HistoryItem(
                    text = item,
                    onRemoveClick = { onRemoveClick(item) }
                )
            }
        }
    }
}

@Composable
fun HistoryItem(
    text: String,
    onRemoveClick: () -> Unit = {}
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 16.dp)) {
            Icon(
                imageVector = Icons.Default.History,
                contentDescription = "History Icon",
                tint = Color.Gray,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = text,
                color = Color.LightGray,
                fontSize = 14.sp,
                fontFamily = AppFont.RobotoRegular
            )
        }
        IconButton(onClick = onRemoveClick) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Remove History",
                tint = Color.Gray,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}