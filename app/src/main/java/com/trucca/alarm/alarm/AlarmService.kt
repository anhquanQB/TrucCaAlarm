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
    private var activeTitle: String = "Sắp vào ca trực!"
    private var activeDetail: String = "Chuẩn bị đi làm."
    private var activeStage: Int = 1

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                val stageToFinish = intent.getIntExtra(EXTRA_STAGE, activeStage)
                val title = intent.getStringExtra(EXTRA_TITLE) ?: activeTitle
                val detail = intent.getStringExtra(EXTRA_DETAIL) ?: activeDetail
                when (stageToFinish) {
                    1 -> ShiftAlarmScheduler.scheduleFollowUpStage(this, title, detail, nextStage = 2, delayMinutes = 10)
                    2 -> ShiftAlarmScheduler.scheduleFollowUpStage(this, title, detail, nextStage = 3, delayMinutes = 5)
                }
                stopAlarmAndSelf()
                return START_NOT_STICKY
            }
            ACTION_DISMISS_ALL -> {
                stopAlarmAndSelf()
                return START_NOT_STICKY
            }
            ACTION_SNOOZE -> {
                val title = intent.getStringExtra(EXTRA_TITLE) ?: activeTitle
                val detail = intent.getStringExtra(EXTRA_DETAIL) ?: activeDetail
                val stage = intent.getIntExtra(EXTRA_STAGE, activeStage)
                ShiftAlarmScheduler.scheduleSnooze(this, title, detail, stage, 5)
                stopAlarmAndSelf()
                return START_NOT_STICKY
            }
            else -> {
                activeTitle = intent?.getStringExtra(EXTRA_TITLE) ?: "Sắp vào ca trực!"
                activeDetail = intent?.getStringExtra(EXTRA_DETAIL) ?: "Chuẩn bị đi làm."
                activeStage = intent?.getIntExtra(EXTRA_STAGE, 1) ?: 1
                val watchBody = "$activeDetail • [Đợt $activeStage/3]"
                startAlarmNotification(activeTitle, watchBody, activeStage)
                startSoundAndVibration()
                startWatchAlertBursts(activeTitle, watchBody)
            }
        }
        return START_STICKY
    }

    private fun startWatchAlertBursts(title: String, detail: String) {
        watchRunnable?.let { watchHandler.removeCallbacks(it) }
        val settings = ScheduleRepository(this).getSettings()
        if (!settings.watchSyncEnabled) return

        // Vibrate watch continuously every 10 seconds until user manually dismisses the alarm
val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
     var sent = 0
     val runner = object : Runnable {
         override fun run() {
             if (sent > 0) {
                 nm.cancel(8800 + ((sent - 1) % 5))
             }
             sent++
             WatchNotifier.sendWatchAlert(
                 context = this@AlarmService,
                 title = title,
                 body = detail,
                 notificationId = 8800 + (sent % 5)
             )
             watchHandler.postDelayed(this, 2_500L)
         }
     }        watchRunnable = runner
        watchHandler.post(runner)
    }

    private fun startAlarmNotification(title: String, detail: String, stage: Int) {
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
            putExtra(EXTRA_TITLE, activeTitle)
            putExtra(EXTRA_DETAIL, activeDetail)
            putExtra(EXTRA_STAGE, stage)
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
            .setStyle(NotificationCompat.BigTextStyle().bigText(detail))
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
            vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 700, 500), 0))
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
        const val ACTION_DISMISS_ALL = "com.trucca.alarm.DISMISS_ALL"
        const val ACTION_SNOOZE = "com.trucca.alarm.SNOOZE"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_DETAIL = "extra_detail"
        const val EXTRA_STAGE = "extra_stage"
    }
}
