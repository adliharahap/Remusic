package com.example.remusic.utils

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.remusic.R

object NotificationUtils {

    private const val CHANNEL_ID = "upload_channel"
    private const val CHANNEL_NAME = "Upload Notifications"
    private const val CHANNEL_DESC = "Notifikasi untuk upload status"

    private const val DOWNLOAD_CHANNEL_ID = "download_channel"
    private const val DOWNLOAD_CHANNEL_NAME = "Download Notifications"
    private const val DOWNLOAD_CHANNEL_DESC = "Notifikasi untuk status download lagu"

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Upload Channel
            val uploadChannel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = CHANNEL_DESC
            }

            // Download Channel (LOW importance strictly for progress bar silence)
            val downloadChannel = NotificationChannel(
                DOWNLOAD_CHANNEL_ID,
                DOWNLOAD_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = DOWNLOAD_CHANNEL_DESC
            }

            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(uploadChannel)
            notificationManager.createNotificationChannel(downloadChannel)
        }
    }

    fun showNotification(
        context: Context,
        title: String,
        message: String,
        notificationId: Int = 1,
        icon: Int = R.drawable.app_logo,
        destinationRoute: String? = null
    ) {
        val intent = Intent().apply {
            setClassName(context, "com.example.remusic.MainActivity")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            // ✅ 3. Sisipkan data rute tujuan
            putExtra("destination_route", destinationRoute)
        }

        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            context,
            notificationId, // Gunakan ID unik untuk tiap PendingIntent jika perlu
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )


        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(icon)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        // ✅ Cek permission sebelum notify
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            == PackageManager.PERMISSION_GRANTED
        ) {
            with(NotificationManagerCompat.from(context)) {
                notify(notificationId, builder.build())
            }
        }
    }

    fun showDownloadNotification(
        context: Context,
        title: String,
        progress: Int,
        isFinished: Boolean = false,
        notificationId: Int = 1001
    ) {
        val builder = NotificationCompat.Builder(context, DOWNLOAD_CHANNEL_ID)
            .setSmallIcon(R.drawable.app_logo)
            .setContentTitle(title)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(!isFinished)
            .setOnlyAlertOnce(true)

        if (isFinished) {
            builder.setContentText("download lagu $title has completed")
                .setProgress(0, 0, false)
                .setAutoCancel(true)
        } else {
            builder.setContentText("Mengunduh... $progress%")
                .setProgress(100, progress, progress == 0)
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            == PackageManager.PERMISSION_GRANTED
        ) {
            try {
                NotificationManagerCompat.from(context).notify(notificationId, builder.build())
            } catch (e: SecurityException) {
                Log.e("NotificationUtils", "Missing notification permission", e)
            }
        }
    }
}
