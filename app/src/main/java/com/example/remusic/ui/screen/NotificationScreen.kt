package com.example.remusic.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.remusic.ui.theme.AppFont

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationScreen(
    navController: NavController
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF000000))
    ) {
        // --- TOP BAR ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF121212))
                .padding(top = 40.dp, bottom = 12.dp, start = 8.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { navController.popBackStack() }
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }
            
            Spacer(modifier = Modifier.width(8.dp))
            
            Text(
                text = "Notifikasi",
                color = Color.White,
                fontSize = 20.sp,
                fontFamily = AppFont.RobotoBold
            )
        }

        // --- NOTIFICATION LIST ---
        LazyColumn(
            modifier = Modifier.fillMaxSize()
        ) {
            // Placeholder Items
            item {
                NotificationItem(
                    icon = Icons.Default.CheckCircle,
                    iconTint = Color(0xFF1DB954), // Spotify Green
                    title = "Lagu Berhasil Ditambahkan",
                    message = "Request lagu 'Die With A Smile' oleh Lady Gaga, Bruno Mars telah ditambahkan ke database.",
                    time = "Baru Saja"
                )
            }
            
            item {
                NotificationItem(
                    icon = Icons.Default.Info,
                    iconTint = Color(0xFF3B82F6), // Blue
                    title = "Sistem Update",
                    message = "Aplikasi ReMusic telah diperbarui ke versi terbaru dengan fitur hemat data.",
                    time = "2 Jam Yang Lalu"
                )
            }
            
            item {
                NotificationItem(
                    icon = Icons.Default.Notifications,
                    iconTint = Color.Gray,
                    title = "Selamat Datang!",
                    message = "Selamat menikmati fitur baru di aplikasi ReMusic.",
                    time = "1 Hari Yang Lalu"
                )
            }
            
            item {
                Spacer(modifier = Modifier.height(30.dp))
            }
        }
    }
}

@Composable
fun NotificationItem(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    message: String,
    time: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Icon Box
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(Color(0xFF1E1E1E)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(24.dp)
            )
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        // Text Content
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 16.sp,
                    fontFamily = AppFont.RobotoMedium
                )
                Text(
                    text = time,
                    color = Color.Gray,
                    fontSize = 12.sp,
                    fontFamily = AppFont.RobotoRegular
                )
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                text = message,
                color = Color.LightGray,
                fontSize = 14.sp,
                fontFamily = AppFont.Helvetica,
                lineHeight = 20.sp
            )
        }
    }
}
