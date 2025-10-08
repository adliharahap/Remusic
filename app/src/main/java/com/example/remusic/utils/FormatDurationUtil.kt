package com.example.remusic.utils

import java.util.Locale
import java.util.concurrent.TimeUnit

fun formatDuration(millis: Long): String {
    val minutes = TimeUnit.MILLISECONDS.toMinutes(millis)
    val seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60
    return String.format(Locale.US, "%02d:%02d", minutes, seconds)
}