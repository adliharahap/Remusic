package com.example.remusic.services

import android.util.Log
import com.example.remusic.data.SupabaseManager
import com.google.firebase.messaging.FirebaseMessagingService
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MyFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("FCM_SERVICE", "Refreshed token: $token")
        
        // Cek apakah user sedang login. Jika iya, update token di Supabase.
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val user = SupabaseManager.client.auth.currentUserOrNull()
                if (user != null) {
                    SupabaseManager.client.from("users").update(
                        {
                            set("fcm_token", token)
                        }
                    ) {
                        filter { eq("id", user.id) }
                    }
                    Log.d("FCM_SERVICE", "Berhasil update token di Supabase")
                } else {
                    Log.d("FCM_SERVICE", "User belum login, token tidak di-update ke DB")
                }
            } catch (e: Exception) {
                Log.e("FCM_SERVICE", "Gagal update token ke Supabase", e)
            }
        }
    }

    override fun onMessageReceived(remoteMessage: com.google.firebase.messaging.RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        // Check if message contains a notification payload.
        remoteMessage.notification?.let {
            Log.d("FCM_SERVICE", "Message Notification Body: ${it.body}")
            sendNotification(it.title, it.body)
        }
    }

    private fun sendNotification(title: String?, messageBody: String?) {
        val intent = android.content.Intent(this, com.example.remusic.MainActivity::class.java)
        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP)
        
        val pendingIntent = android.app.PendingIntent.getActivity(
            this, 0, intent,
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
        )

        val channelId = "remusic_general_channel"
        val defaultSoundUri = android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION)
        
        val notificationBuilder = androidx.core.app.NotificationCompat.Builder(this, channelId)
            .setSmallIcon(com.example.remusic.R.drawable.play_circle_svgrepo_com) // make sure this icon exists
            .setContentTitle(title ?: "ReMusic")
            .setContentText(messageBody)
            .setAutoCancel(true)
            .setSound(defaultSoundUri)
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)

        val notificationManager = getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager

        // Since android Oreo notification channel is needed.
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                channelId,
                "General Notifications",
                android.app.NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(channel)
        }

        notificationManager.notify(System.currentTimeMillis().toInt(), notificationBuilder.build())
    }
}
