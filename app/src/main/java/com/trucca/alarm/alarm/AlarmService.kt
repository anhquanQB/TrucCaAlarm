package com.trucca.baothuc

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat

class AlarmService : Service() {

    companion object {
        const val ACTION_START_1H = "com.trucca.baothuc.START_1H"
        const val ACTION_SNOOZE_1MIN = "com.trucca.baothuc.SNOOZE_1MIN"
        const val ACTION_CANCEL_1H = "com.trucca.baothuc.CANCEL_1H"
        const val EXTRA_SHIFT_TITLE = "extra_shift_title"

        private const val CHANNEL_ID = "watch_vib_1h"
        private const val FG_ID = 2001
        private const val PULSE_ID = 2002

        private const val WINDOW_MS = 60L * 60L * 1000L
        private const val SNOOZE_MS = 60L * 1000L
        private const val PULSE_INTERVAL_MS = 2_200L
    }

    private val handler = Handler(Looper.getMainLooper())
    private var vibrator: Vibrator? = null

    private var shiftTitle = "Ca Truc"
    private var endTimeMs = 0L
    private var sessionActive = false
    private var vibActive = false
    private var seq = 0

    private val pulseTask = object : Runnable {
        override fun run() {
            if (!sessionActive) return
            if (System.currentTimeMillis() >= endTimeMs) {
                stopAll()
                return
            }
            if (!vibActive) return

            seq++
            vibrateNow()
            pushWatchPulse(seq)
            handler.postDelayed(this, PULSE_INTERVAL_MS)
        }
    }

    private val resumeTask = Runnable {
        if (!sessionActive) return@Runnable
        if (System.currentTimeMillis() >= endTimeMs) {
            stopAll()
            return@Runnable
        }
        startVibCycle()
    }

    private val expireTask = Runnable { stopAll() }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_1H -> {
                shiftTitle = intent.getStringExtra(EXTRA_SHIFT_TITLE)?.ifBlank { "Ca Truc" } ?: "Ca Truc"
                startSession()
            }
            ACTION_SNOOZE_1MIN -> snooze1Min()
            ACTION_CANCEL_1H -> stopAll()
        }
        return START_NOT_STICKY
    }

    private fun startSession() {
        clearTimers()
        val now = System.currentTimeMillis()
        endTimeMs = now + WINDOW_MS
        sessionActive = true
        seq = 0

        startForeground(FG_ID, buildFgNotif("Dang rung trong 1 gio"))
        handler.postDelayed(expireTask, WINDOW_MS)
        startVibCycle()
    }

    private fun startVibCycle() {
        if (!sessionActive) return
        handler.removeCallbacks(pulseTask)
        vibActive = true
        updateFgNotif("Dang rung lien tuc")
        openPopup()
        handler.post(pulseTask)
    }

    private fun snooze1Min() {
        if (!sessionActive) return
        clearTimers()
        vibActive = false
        vibrator?.cancel()
        cancelPulseNotif()

        val rem = endTimeMs - System.currentTimeMillis()
        if (rem <= 0L) {
            stopAll()
            return
        }

        updateFgNotif("Tam ngung 1 phut")
        handler.postDelayed(expireTask, rem)
        handler.postDelayed(resumeTask, minOf(SNOOZE_MS, rem))
    }

    private fun stopAll() {
        sessionActive = false
        vibActive = false
        clearTimers()
        vibrator?.cancel()
        cancelPulseNotif()
        sendBroadcast(Intent(WakePopupActivity.ACTION_CLOSE_POPUP).setPackage(packageName))
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun openPopup() {
        val i = Intent(this, WakePopupActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(EXTRA_SHIFT_TITLE, shiftTitle)
        }
        try {
            startActivity(i)
        } catch (_: Exception) {
        }
    }

    private fun vibrateNow() {
        val v = vibrator ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(
                    VibrationEffect.createWaveform(
                        longArrayOf(0, 650, 150, 650),
                        intArrayOf(0, 255, 0, 255),
                        -1
                    )
                )
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(1450)
            }
        } catch (_: Exception) {
        }
    }

    private fun pushWatchPulse(n: Int) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(PULSE_ID)

        val snoozePi = PendingIntent.getService(
            this,
            n,
            Intent(this, AlarmService::class.java).apply { action = ACTION_SNOOZE_1MIN },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notif = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(shiftTitle)
            .setContentText("Rung #$n")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setOnlyAlertOnce(false)
            .setContentIntent(snoozePi)
            .build()

        nm.notify(PULSE_ID, notif)
    }

    private fun cancelPulseNotif() {
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(PULSE_ID)
    }

    private fun buildFgNotif(text: String) = run {
        val cancelPi = PendingIntent.getService(
            this,
            999,
            Intent(this, AlarmService::class.java).apply { action = ACTION_CANCEL_1H },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(shiftTitle)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "HUY 1 GIO", cancelPi)
            .build()
    }

    private fun updateFgNotif(text: String) {
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(FG_ID, buildFgNotif(text))
    }

    private fun clearTimers() {
        handler.removeCallbacks(pulseTask)
        handler.removeCallbacks(resumeTask)
        handler.removeCallbacks(expireTask)
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL_ID, "Rung bao thuc 1 gio", NotificationManager.IMPORTANCE_HIGH).apply {
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 600, 150, 600)
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }

    override fun onDestroy() {
        sessionActive = false
        vibActive = false
        clearTimers()
        vibrator?.cancel()
        cancelPulseNotif()
        super.onDestroy()
    }
}
