package com.example.remusic.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.CountDownTimer
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.media3.common.util.UnstableApi
import com.example.remusic.R
import java.util.concurrent.TimeUnit

class SleepTimerManager(
    private val context: Context,
    private val onTimerFinished: () -> Unit
) {

    private var countDownTimer: CountDownTimer? = null
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    
    // ID Notification khusus Timer (biar gak ganggu notif musik)
    private val NOTIFICATION_ID = 777
    private val CHANNEL_ID = "sleep_timer_channel"

    var isTimerActive = false
        private set
    
    var timeRemainingMs: Long = 0
        private set

    init {
        createNotificationChannel()
    }

    fun startTimer(durationMs: Long) {
        cancelTimer() // Reset kalau ada yang jalan

        isTimerActive = true
        timeRemainingMs = durationMs
        
        android.util.Log.d("SleepTimerManager", "⏰ START TIMER: $durationMs ms")

        // Update notifikasi awal
        showNotification(durationMs)

        countDownTimer = object : CountDownTimer(durationMs, 1000 * 60) { // Update tiap 1 menit biar hemat baterai
            override fun onTick(millisUntilFinished: Long) {
                timeRemainingMs = millisUntilFinished
                android.util.Log.d("SleepTimerManager", "⏰ TICK: $millisUntilFinished ms left")
                showNotification(millisUntilFinished)
            }

            override fun onFinish() {
                android.util.Log.d("SleepTimerManager", "⏰ FINISHED! Stopping music...")
                isTimerActive = false
                timeRemainingMs = 0
                cancelNotification()
                onTimerFinished()
            }
        }.start()
    }

    fun cancelTimer() {
        android.util.Log.d("SleepTimerManager", "⏰ CANCEL TIMER")
        countDownTimer?.cancel()
        countDownTimer = null
        isTimerActive = false
        timeRemainingMs = 0
        cancelNotification()
    }

    @OptIn(UnstableApi::class)
    private fun showNotification(millisUntilFinished: Long) {
        val minutesLeft = TimeUnit.MILLISECONDS.toMinutes(millisUntilFinished) + 1 // Round up
        
        // Intent untuk Stop Timer
        val stopIntent = Intent(context, MusicService::class.java).apply {
            action = "ACTION_STOP_SLEEP_TIMER"
        }
        val stopPendingIntent = PendingIntent.getService(
            context, 0, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_timer_notification) // Pastikan icon ini ada, atau pakai default
            .setContentTitle("Sleep Timer Active")
            .setContentText("Music will stop in $minutesLeft minutes")
            .setOngoing(true) // Tidak bisa diswipe
            .setOnlyAlertOnce(true)
            .addAction(R.drawable.ic_close, "Stop", stopPendingIntent) // Action Stop
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun cancelNotification() {
        notificationManager.cancel(NOTIFICATION_ID)
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Sleep Timer",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows active sleep timer countdown"
        }
        notificationManager.createNotificationChannel(channel)
    }
}
