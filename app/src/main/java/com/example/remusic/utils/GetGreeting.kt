package com.example.remusic.utils

import android.icu.util.Calendar

object GreetingUtils {

    fun getGreeting(): String {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)

        return when (hour) {
            in 5..11 -> "Good Morning"
            in 12..15 -> "Good Afternoon"
            in 16..19 -> "Good Evening"
            else -> "Good Night"
        }
    }
}