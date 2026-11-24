package com.trucca.alarm.alarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.trucca.alarm.MainActivity

object WatchNotifier {

    const val WATCH_CHANNEL_ID = "trucca_huawei_watch_channel"

    fun ensureChannel(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            WATCH_CHANNEL_ID,
            "Nhắc ca đồng hồ Huawei Watch",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Kênh thông báo chuẩn để Huawei Sức khỏe đẩy sang đồng hồ Huawei Watch GT 6 Pro"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 400, 200, 400)
        }
        nm.createNotificationChannel(channel)
    }

    fun sendWatchAlert(context: Context, title: String, body: String, notificationId: Int) {
        ensureChannel(context)
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, WATCH_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setOngoing(false)
            .setOnlyAlertOnce(false)
            .setContentIntent(pendingIntent)
            .build()

        nm.notify(notificationId, notification)
    }

    fun sendTestWatchNotification(context: Context, selectedKip: String, members: String) {
        val body = buildString {
            append("Nếu đồng hồ Huawei GT 6 Pro vừa rung, kết nối đã thành công!")
            if (members.isNotBlank()) append("\nTrực cùng: $members")
        }
        sendWatchAlert(
            context = context,
            title = "Kiểm tra Huawei Watch • Kíp $selectedKip",
            body = body,
            notificationId = (System.currentTimeMillis() % 100000).toInt() + 8000
        )
    }
}
