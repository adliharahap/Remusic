package com.example.remusic.ui.components.homecomponents

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.remusic.ui.theme.AppFont

@Composable
fun HomeHeader(
    name: String? = "User",
    greeting: String,
    profileImageUrl: String? = "https://i.pinimg.com/736x/c5/27/81/c52781800203c4abd6bb7c6f670d9918.jpg",
    onNotificationClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(110.dp)
            .padding(top = 15.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize()
        ) {
            // Bagian kiri
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                Row(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Nama + Sambutan
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 15.dp),
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text(
                                text = "Hi, $name",
                                fontFamily = AppFont.PoppinsMedium,
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 14.sp,
                                maxLines = 1
                            )
                            Text(
                                text = greeting,
                                fontFamily = AppFont.PoppinsBold,
                                color = Color.White,
                                fontSize = 22.sp,
                                maxLines = 1
                            )
                        }
                    }
                    // Icon notifikasi
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(0.1f)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Notifications,
                            contentDescription = "Notifications",
                            modifier = Modifier
                                .align(Alignment.Center)
                                .size(24.dp)
                                .clickable { onNotificationClick() },
                            tint = Color.LightGray
                        )
                    }
                }
            }

            // Bagian kanan (foto profil)
            Box(
                modifier = Modifier
                    .width(120.dp)
                    .fillMaxHeight()
            ) {
                AsyncImage(
                    model = profileImageUrl,
                    contentDescription = "Profile Image",
                    modifier = Modifier
                        .size(65.dp)
                        .clip(CircleShape)
                        .border(
                            width = 1.2.dp,
                            color = Color.White,
                            shape = CircleShape
                        )
                        .align(Alignment.Center)
                )
            }
        }
    }
}