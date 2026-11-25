package com.trucca.baothuc

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat

class WakePopupActivity : ComponentActivity() {

    companion object {
        const val ACTION_CLOSE_POPUP = "com.trucca.baothuc.CLOSE_POPUP"
    }

    private val closeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_CLOSE_POPUP) finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }

        setContentView(R.layout.dialog_wake_popup)
        findViewById<TextView>(R.id.tvPopupTitle).text =
            intent.getStringExtra(AlarmService.EXTRA_SHIFT_TITLE) ?: "BAO THUC CA TRUC"

        findViewById<Button>(R.id.btnSnooze1Min).setOnClickListener {
            ContextCompat.startForegroundService(
                this,
                Intent(this, AlarmService::class.java).apply { action = AlarmService.ACTION_SNOOZE_1MIN }
            )
            finish()
        }

        findViewById<Button>(R.id.btnCancelHour).setOnClickListener {
            ContextCompat.startForegroundService(
                this,
                Intent(this, AlarmService::class.java).apply { action = AlarmService.ACTION_CANCEL_1H }
            )
            finish()
        }

        val filter = IntentFilter(ACTION_CLOSE_POPUP)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(closeReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(closeReceiver, filter)
        }
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(closeReceiver)
        } catch (_: Exception) {
        }
        super.onDestroy()
    }
}
