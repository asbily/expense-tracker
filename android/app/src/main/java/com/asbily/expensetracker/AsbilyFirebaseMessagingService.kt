package com.asbily.expensetracker

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class AsbilyFirebaseMessagingService : FirebaseMessagingService() {

    companion object {
        const val PREFS_NAME = "asbily_fcm_prefs"
        const val KEY_FCM_TOKEN = "fcm_token"
        const val LEGACY_CHANNEL_ID = "asbily_alerts"
        const val NOTIF_CHANNEL_ID = "asbily_alerts_v2"
        const val NOTIF_CHANNEL_NAME = "Asbily Reminders & Alerts"
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_FCM_TOKEN, token)
            .apply()
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        val title = remoteMessage.notification?.title?.takeIf { it.isNotBlank() }
            ?: remoteMessage.data["title"]?.takeIf { it.isNotBlank() }
            ?: "Asbily Expense Tracker"

        val body = remoteMessage.notification?.body?.takeIf { it.isNotBlank() }
            ?: remoteMessage.data["body"]?.takeIf { it.isNotBlank() }
            ?: remoteMessage.data["message"]?.takeIf { it.isNotBlank() }
            ?: ""

        if (body.isBlank() && remoteMessage.notification == null && remoteMessage.data.isEmpty()) {
            return
        }

        showFcmNotification(title, body)
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.deleteNotificationChannel(LEGACY_CHANNEL_ID)

            val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val audioAttrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            val channel = NotificationChannel(
                NOTIF_CHANNEL_ID,
                NOTIF_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Asbily Expense Tracker announcements, updates & reminders"
                setSound(soundUri, audioAttrs)
                enableLights(true)
                enableVibration(true)
                vibrationPattern = longArrayOf(0L, 180L, 90L, 180L)
            }
            nm.createNotificationChannel(channel)
        }
    }

    private fun showFcmNotification(title: String, body: String) {
        ensureNotificationChannel()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val tapIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val pendingIntent = PendingIntent.getActivity(this, 0, tapIntent, flags)
        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val notification = NotificationCompat.Builder(this, NOTIF_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notif_asbily)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setSound(soundUri)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val notifId = (System.currentTimeMillis() and 0x7FFFFFFF).toInt()
        NotificationManagerCompat.from(this).notify(notifId, notification)
    }
}
