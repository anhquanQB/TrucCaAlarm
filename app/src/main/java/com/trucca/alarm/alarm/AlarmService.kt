package com.trucca.alarm.alarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import com.trucca.alarm.data.ScheduleRepository

class AlarmService : Service() {

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private val watchHandler = Handler(Looper.getMainLooper())
    private var watchRunnable: Runnable? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopAlarmAndSelf()
                return START_NOT_STICKY
            }
            ACTION_SNOOZE -> {
                val title = intent.getStringExtra(EXTRA_TITLE) ?: "Báo thức trực ca"
                val detail = intent.getStringExtra(EXTRA_DETAIL) ?: ""
                ShiftAlarmScheduler.scheduleSnooze(this, title, detail, 5)
                stopAlarmAndSelf()
                return START_NOT_STICKY
            }
            else -> {
                val title = intent?.getStringExtra(EXTRA_TITLE) ?: "Sắp vào ca trực!"
                val detail = intent?.getStringExtra(EXTRA_DETAIL) ?: "Chuẩn bị đi làm."
                startAlarmNotification(title, detail)
                startSoundAndVibration()
                startWatchAlertBursts(title, detail)
            }
        }
        return START_STICKY
    }

    private fun startWatchAlertBursts(title: String, detail: String) {
        watchRunnable?.let { watchHandler.removeCallbacks(it) }
        val settings = ScheduleRepository(this).getSettings()
        if (!settings.watchSyncEnabled) return

        val totalRepeats = settings.watchRepeatCount.coerceIn(1, 5)
        var sent = 0

        val runner = object : Runnable {
            override fun run() {
                if (sent >= totalRepeats) return
                sent++
                val suffix = if (totalRepeats > 1) " (Nhắc $sent/$totalRepeats)" else ""
                WatchNotifier.sendWatchAlert(
                    context = this@AlarmService,
                    title = "$title$suffix",
                    body = detail,
                    notificationId = 8800 + sent
                )
                if (sent < totalRepeats) {
                    watchHandler.postDelayed(this, 20_000L)
                }
            }
        }
        watchRunnable = runner
        watchHandler.post(runner)
    }

    private fun startAlarmNotification(title: String, detail: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Báo thức trực ca",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Kênh thông báo chuông báo thức lịch trực ca"
            setSound(null, null)
            enableVibration(false)
        }
        nm.createNotificationChannel(channel)

        val fullScreenIntent = Intent(this, AlarmActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_TITLE, title)
            putExtra(EXTRA_DETAIL, detail)
        }
        val fullScreenPending = PendingIntent.getActivity(
            this,
            101,
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, AlarmService::class.java).apply { action = ACTION_STOP }
        val stopPending = PendingIntent.getService(
            this,
            102,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(detail)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .setFullScreenIntent(fullScreenPending, true)
            .addAction(0, "Tắt báo thức", stopPending)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        try {
            startActivity(fullScreenIntent)
        } catch (_: Exception) {
        }
    }

    private fun startSoundAndVibration() {
        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setDataSource(this@AlarmService, uri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                isLooping = true
                prepare()
                start()
            }
        } catch (_: Exception) {
        }

        try {
            vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            val pattern = longArrayOf(0, 700, 500)
            vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
        } catch (_: Exception) {
        }
    }

    private fun stopAlarmAndSelf() {
        watchRunnable?.let { watchHandler.removeCallbacks(it) }
        watchRunnable = null
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (_: Exception) {
        }
        mediaPlayer = null
        try {
            vibrator?.cancel()
        } catch (_: Exception) {
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        watchRunnable?.let { watchHandler.removeCallbacks(it) }
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
            vibrator?.cancel()
        } catch (_: Exception) {
        }
        super.onDestroy()
    }

    companion object {
        const val CHANNEL_ID = "trucca_alarm_channel"
        const val NOTIFICATION_ID = 7701
        const val ACTION_START = "com.trucca.alarm.START"
        const val ACTION_STOP = "com.trucca.alarm.STOP"
        const val ACTION_SNOOZE = "com.trucca.alarm.SNOOZE"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_DETAIL = "extra_detail"
    }
}
