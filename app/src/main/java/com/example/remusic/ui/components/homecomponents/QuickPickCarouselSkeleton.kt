package com.example.remusic.ui.components.homecomponents

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.example.remusic.ui.components.SkeletonBox

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun QuickPickCarouselSkeleton() {
    val pagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { 3 } // Show 3 skeleton cards
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
    ) {
        // Carousel
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp),
            contentPadding = PaddingValues(horizontal = 32.dp),
            pageSpacing = 16.dp
        ) { page ->
            // Each page shows 4 skeleton rows vertically
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                repeat(4) {
                    // Row mimicking QueueSongCard
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Image skeleton
                        SkeletonBox(
                            modifier = Modifier
                                .size(50.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            shape = RoundedCornerShape(8.dp)
                        )

                        Spacer(modifier = Modifier.width(12.dp))

                        // Text detail skeleton
                        Column(
                            verticalArrangement = Arrangement.Center
                        ) {
                            SkeletonBox(
                                modifier = Modifier
                                    .width(160.dp)
                                    .height(16.dp),
                                shape = RoundedCornerShape(4.dp)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            SkeletonBox(
                                modifier = Modifier
                                    .width(100.dp)
                                    .height(12.dp),
                                shape = RoundedCornerShape(4.dp)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Page indicators skeleton
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(3) { index ->
                SkeletonBox(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape),
                    shape = CircleShape
                )
                if (index != 2) {
                    Spacer(modifier = Modifier.width(6.dp))
                }
            }
        }
    }
}
