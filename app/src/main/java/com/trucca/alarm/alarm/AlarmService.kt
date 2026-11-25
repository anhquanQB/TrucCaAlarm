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
import android.os.PowerManager
import android.os.VibrationEffect
import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Vibrator
import android.os.VibratorManager
import android.telecom.TelecomManager
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationCompat
import com.trucca.alarm.data.ScheduleRepository

class AlarmService : Service() {

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null
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
                val stopPending = buildStopStagePendingIntent()
                val settings = ScheduleRepository(this).getSettings()
                val willAutoCall = settings.watchSyncEnabled &&
                    settings.watchAutoCallEnabled &&
                    settings.watchCallNumber.filter { it.isDigit() || it == '+' }.length >= 3 &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED

                startAlarmNotification(
                    title = activeTitle,
                    detail = watchBody,
                    stage = activeStage,
                    stopPending = stopPending,
                    launchActivityImmediately = !willAutoCall
                )
                startSoundAndVibration()
                if (willAutoCall) {
                    watchHandler.postDelayed({ triggerWatchWakeCallIfEnabled() }, 350L)
                    watchHandler.postDelayed({ startWatchAlertBursts(activeTitle, watchBody, stopPending) }, 25_000L)
                } else {
                    startWatchAlertBursts(activeTitle, watchBody, stopPending)
                }
            }
        }
        return START_STICKY
    }

    private fun buildStopStagePendingIntent(): PendingIntent {
        val stopIntent = Intent(this, AlarmService::class.java).apply {
            action = ACTION_STOP
            putExtra(EXTRA_TITLE, activeTitle)
            putExtra(EXTRA_DETAIL, activeDetail)
            putExtra(EXTRA_STAGE, activeStage)
        }
        return PendingIntent.getService(
            this,
            102,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun clearWatchNotifications() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        for (id in 8800..8835) {
            nm.cancel(id)
        }
        nm.cancel(WATCH_ACTIVE_CALL_ID)
    }

    private fun startWatchAlertBursts(title: String, detail: String, stopPending: PendingIntent) {
        watchRunnable?.let { watchHandler.removeCallbacks(it) }
        val settings = ScheduleRepository(this).getSettings()
        if (!settings.watchSyncEnabled) return

        try {
            if (wakeLock == null) {
                val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
                @Suppress("DEPRECATION")
                wakeLock = pm.newWakeLock(
                    PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                    "TrucCaAlarm::WatchAlarmLock"
                )
            }
            if (wakeLock?.isHeld == false) {
                wakeLock?.acquire(15 * 60 * 1000L)
            }
        } catch (_: Exception) {
        }

        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        var sent = 0
        val runner = object : Runnable {
            override fun run() {
                sent++
                val tagTitle = if (sent == 1) title else "$title (#$sent)"
                try {
                    nm.cancel(WATCH_ACTIVE_CALL_ID)
                    WatchNotifier.sendWatchAlert(
                        context = this@AlarmService,
                        title = tagTitle,
                        body = detail,
                        notificationId = WATCH_ACTIVE_CALL_ID,
                        stopPendingIntent = stopPending,
                        isCallAlert = true
                    )
                } catch (_: Exception) {
                }
                watchHandler.postDelayed(this, 6_000L)
            }
        }
        watchRunnable = runner
        watchHandler.post(runner)
    }

    private fun startAlarmNotification(
        title: String,
        detail: String,
        stage: Int,
        stopPending: PendingIntent,
        launchActivityImmediately: Boolean = true
    ) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Báo thức trực ca",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Kênh thông báo chuông báo thức lịch trực ca"
            setSound(null, null)
            enableVibration(false)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
        }
        nm.createNotificationChannel(channel)

        val fullScreenIntent = Intent(this, AlarmActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
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

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(detail)
            .setStyle(NotificationCompat.BigTextStyle().bigText(detail))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
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

        if (launchActivityImmediately) {
            try {
                startActivity(fullScreenIntent)
            } catch (_: Exception) {
            }
        }
    }

    private fun triggerWatchWakeCallIfEnabled() {
        try {
            val settings = ScheduleRepository(this).getSettings()
            if (!settings.watchSyncEnabled || !settings.watchAutoCallEnabled) return
            val rawNumber = settings.watchCallNumber.trim()
            val sanitized = rawNumber.filter { it.isDigit() || it == '+' }
            if (sanitized.length < 3) return
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE)
                != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }

            val uri = Uri.parse("tel:$sanitized")
            val telecomManager = getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
            val targetTail = sanitized.filter { it.isDigit() }.takeLast(9)

            var chosenHandle: android.telecom.PhoneAccountHandle? = null
            var chosenSlot = 1
            if (telecomManager != null &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
            ) {
                val handles = telecomManager.callCapablePhoneAccounts
                if (handles.size >= 2) {
                    var matchedIndex = -1
                    for (i in handles.indices) {
                        val acct = telecomManager.getPhoneAccount(handles[i])
                        val addrDigits = acct?.address?.schemeSpecificPart?.filter { it.isDigit() }.orEmpty()
                        val labelDigits = acct?.label?.toString()?.filter { it.isDigit() }.orEmpty()
                        if (targetTail.isNotEmpty() && (addrDigits.endsWith(targetTail) || labelDigits.endsWith(targetTail))) {
                            matchedIndex = i
                            break
                        }
                    }
                    chosenSlot = if (matchedIndex == 0) 1 else if (matchedIndex == 1) 0 else 1
                    chosenHandle = handles.getOrNull(chosenSlot) ?: handles.last()
                } else if (handles.size == 1) {
                    chosenSlot = 0
                    chosenHandle = handles.first()
                }
            }

            if (telecomManager != null && chosenHandle != null) {
                val extras = Bundle().apply {
                    putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, chosenHandle)
                    putInt("com.android.phone.extra.slot", chosenSlot)
                    putInt("simSlot", chosenSlot)
                }
                telecomManager.placeCall(uri, extras)
            } else {
                val callIntent = Intent(Intent.ACTION_CALL, uri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    putExtra("com.android.phone.extra.slot", chosenSlot)
                    putExtra("simSlot", chosenSlot)
                }
                startActivity(callIntent)
            }
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
            vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 800, 300), 0))
        } catch (_: Exception) {
        }
    }

    private fun stopAlarmAndSelf() {
        watchRunnable?.let { watchHandler.removeCallbacks(it) }
        watchRunnable = null
        clearWatchNotifications()
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
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        } catch (_: Exception) {
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        watchRunnable?.let { watchHandler.removeCallbacks(it) }
        clearWatchNotifications()
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
            vibrator?.cancel()
        } catch (_: Exception) {
        }
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        } catch (_: Exception) {
        }
        super.onDestroy()
    }

    companion object {
        const val CHANNEL_ID = "trucca_alarm_channel"
        const val NOTIFICATION_ID = 7701
        const val WATCH_ACTIVE_CALL_ID = 8899
        const val ACTION_START = "com.trucca.alarm.START"
        const val ACTION_STOP = "com.trucca.alarm.STOP"
        const val ACTION_DISMISS_ALL = "com.trucca.alarm.DISMISS_ALL"
        const val ACTION_SNOOZE = "com.trucca.alarm.SNOOZE"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_DETAIL = "extra_detail"
        const val EXTRA_STAGE = "extra_stage"
    }
}
