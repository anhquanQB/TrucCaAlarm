package com.trucca.alarm.alarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.media.AudioAttributes
import android.media.RingtoneManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.trucca.alarm.R

object WatchNotifier {

    const val WATCH_CHANNEL_ID = "trucca_huawei_watch_msg_v4"

    @Volatile
    private var cachedAvatarBitmap: Bitmap? = null

    fun loadAppAvatarBitmap(context: Context): Bitmap? {
        cachedAvatarBitmap?.let { if (!it.isRecycled) return it }
        return try {
            val drawable = ContextCompat.getDrawable(context, R.mipmap.ic_launcher)
                ?: context.packageManager.getApplicationIcon(context.packageName)
            val size = 128
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, size, size)
            drawable.draw(canvas)
            cachedAvatarBitmap = bitmap
            bitmap
        } catch (_: Exception) {
            null
        }
    }

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
        val snoozeAlarmPi = stopPendingIntent ?: PendingIntent.getService(
            context,
            notificationId + 5000,
            Intent(context, AlarmService::class.java).apply {
                action = AlarmService.ACTION_SNOOZE_1MIN
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, WATCH_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setLargeIcon(loadAppAvatarBitmap(context))
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(if (isCallAlert) NotificationCompat.CATEGORY_CALL else NotificationCompat.CATEGORY_MESSAGE)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(false)
            .setOngoing(false)
            .setOnlyAlertOnce(false)
            .addAction(0, "Hủy báo thức", snoozeAlarmPi)

        nm.notify(notificationId, builder.build())
    }

    fun sendTestWatchNotification(context: Context, selectedKip: String, members: String) {
        val snoozePi = PendingIntent.getService(
            context,
            9901,
            Intent(context, AlarmService::class.java).apply {
                action = AlarmService.ACTION_SNOOZE_1MIN
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        sendWatchAlert(
            context = context,
            title = "Ca Sáng • Kíp $selectedKip",
            body = if (members.isNotBlank()) "07:00 – 15:00 • $members" else "07:00 – 15:00 • Thử rung đồng hồ",
            notificationId = (System.currentTimeMillis() % 100000).toInt() + 8000,
            stopPendingIntent = snoozePi,
            isCallAlert = false
        )
    }
}
