package com.example.remusic.ui.screen

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TestPullToRefresh() {
    PullToRefreshBox(
        isRefreshing = false,
        onRefresh = {}
    ) {
    }
}
