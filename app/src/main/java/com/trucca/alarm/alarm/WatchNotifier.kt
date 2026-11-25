package com.trucca.alarm.alarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import androidx.core.app.NotificationCompat
import com.trucca.alarm.MainActivity

object WatchNotifier {

    const val WATCH_CHANNEL_ID = "trucca_huawei_watch_msg_v4"

    fun ensureChannel(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val audioAttrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val channel = NotificationChannel(
            WATCH_CHANNEL_ID,
            "Báo thức rung Huawei Watch",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Kênh thông báo tin nhắn ưu tiên cao để Huawei Sức khỏe luôn kích hoạt rung mạnh trên đồng hồ"
            setSound(soundUri, audioAttrs)
            enableVibration(true)
            enableLights(true)
            vibrationPattern = longArrayOf(0, 800, 200, 800)
        }
        nm.createNotificationChannel(channel)
    }

    fun sendWatchAlert(
        context: Context,
        title: String,
        body: String,
        notificationId: Int,
        stopPendingIntent: PendingIntent? = null
    ) {
        ensureChannel(context)
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, WATCH_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .setOngoing(false)
            .setOnlyAlertOnce(false)
            .setContentIntent(pendingIntent)

        if (stopPendingIntent != null) {
            builder.addAction(0, "Tắt báo thức", stopPendingIntent)
        }

        nm.notify(notificationId, builder.build())
    }

    fun sendTestWatchNotification(context: Context, selectedKip: String, members: String) {
        sendWatchAlert(
            context = context,
            title = "Ca Sáng • Kíp $selectedKip",
            body = "07:00 – 15:00 • [Đợt 1/3]",
            notificationId = (System.currentTimeMillis() % 100000).toInt() + 8000
        )
    }
}
