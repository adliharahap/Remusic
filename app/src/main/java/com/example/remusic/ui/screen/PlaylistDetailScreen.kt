package com.example.remusic.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.remusic.ui.theme.AppFont

@Composable
fun PlaylistDetailScreen() {
    // ini state untuk scroll position
    val listState = rememberLazyListState()

    Box (modifier = Modifier.fillMaxSize()){
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize()
        ) { 
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 40.dp).background(Color.Blue),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    AsyncImage(
                        model = "https://i.pinimg.com/736x/a0/45/bb/a045bbe1de7bcf663541a430903ff2b7.jpg",
                        contentDescription = "Playlist Poster",
                        modifier = Modifier
                            .fillMaxWidth(0.6f)
                            .aspectRatio(1f)
                            .shadow(
                                elevation = 6.dp,
                                shape = RoundedCornerShape(16.dp),
                                clip = false
                            )
                            .clip(RoundedCornerShape(10.dp)),
                        contentScale = ContentScale.Crop,
                    )
                    Spacer(modifier = Modifier.fillMaxWidth().height(16.dp))
                    Text(
                        text = "All Music",
                        maxLines = 1,
                        fontFamily = AppFont.RobotoBold,
                        fontSize = 26.sp,
                        color = Color.White
                    )
                    Text(
                        text = "46 Music",
                        maxLines = 1,
                        fontFamily = AppFont.RobotoBold,
                        fontSize = 16.sp,
                        color = Color.White.copy(0.8f)
                    )
                    Spacer(modifier = Modifier.fillMaxWidth().height(10.dp))
                    Row (
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Absolute.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ){
                        Row {
                            Button(
                                onClick = {},
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.padding(vertical = 6.dp, horizontal = 12.dp).background(Color.White.copy(0.2f))
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.EditNote,
                                    contentDescription = "Edit Playlist",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "Edit",
                                    maxLines = 1,
                                    fontFamily = AppFont.RobotoBold,
                                    fontSize = 14.sp,
                                    color = Color.White
                                )
                            }
                            Spacer(modifier = Modifier.width(20.dp))
                            Button(
                                onClick = {},
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.padding(vertical = 6.dp, horizontal = 12.dp).background(Color.White.copy(0.2f))
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.FilterList,
                                    contentDescription = "Sort by",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "Urutkan",
                                    maxLines = 1,
                                    fontFamily = AppFont.RobotoBold,
                                    fontSize = 14.sp,
                                    color = Color.White
                                )
                            }
                        }

                        Row {
                            IconButton(
                                onClick = {}
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Shuffle,
                                    contentDescription = "shuffle",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(20.dp))
                            IconButton(
                                onClick = {}
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.PlayCircle,
                                    contentDescription = "Play Music",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PreviewPlaylistDetailScreen() {
    PlaylistDetailScreen()
}