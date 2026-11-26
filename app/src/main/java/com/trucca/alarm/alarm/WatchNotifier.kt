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
import com.trucca.alarm.R

object WatchNotifier {

    const val WATCH_CHANNEL_ID = "trucca_huawei_watch_msg_v4"

    fun ensureChannel(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val audioAttrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val channel = NotificationChannel(
            WATCH_CHANNEL_ID,
            "Thông báo đồng hồ Huawei Watch",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Kênh đồng bộ thông báo và rung nhịp sang Huawei Watch GT 6 Pro"
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
        stopPendingIntent: PendingIntent? = null,
        isCallAlert: Boolean = false
    ) {
        ensureChannel(context)
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val contentPending = PendingIntent.getActivity(
            context,
            notificationId,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val cancelAlarmPi = stopPendingIntent ?: PendingIntent.getService(
            context,
            notificationId + 5000,
            Intent(context, AlarmService::class.java).apply {
                action = AlarmService.ACTION_CANCEL_1H
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, WATCH_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(if (isCallAlert) NotificationCompat.CATEGORY_CALL else NotificationCompat.CATEGORY_MESSAGE)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .setOngoing(false)
            .setOnlyAlertOnce(false)
            .setContentIntent(contentPending)
            .addAction(0, "Hủy báo thức", cancelAlarmPi)

        nm.notify(notificationId, builder.build())
    }

    fun sendTestWatchNotification(context: Context, selectedKip: String, members: String) {
        val cancelPi = PendingIntent.getService(
            context,
            9901,
            Intent(context, AlarmService::class.java).apply {
                action = AlarmService.ACTION_CANCEL_1H
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        sendWatchAlert(
            context = context,
            title = "Ca Sáng • Kíp $selectedKip",
            body = if (members.isNotBlank()) "07:00 – 15:00 • $members" else "07:00 – 15:00 • Thử rung đồng hồ",
            notificationId = (System.currentTimeMillis() % 100000).toInt() + 8000,
            stopPendingIntent = cancelPi,
            isCallAlert = false
        )
    }
}
