package com.trucca.alarm.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
import com.trucca.alarm.R

class WakePopupActivity : ComponentActivity() {

    companion object {
        const val ACTION_CLOSE_POPUP = "com.trucca.alarm.CLOSE_POPUP"
    }

    private val closeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_CLOSE_POPUP) finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        window.addFlags(
            WindowManager.LayoutParams.FLAG_DIM_BEHIND or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )
        window.setDimAmount(0.55f)
        window.attributes = window.attributes.apply {
            gravity = Gravity.CENTER
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setBlurBehindRadius(48)
                addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            }
        }

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
        val title = intent.getStringExtra(AlarmService.EXTRA_TITLE) ?: "BÁO THỨC CA TRỰC"
        val detail = intent.getStringExtra(AlarmService.EXTRA_DETAIL) ?: ""
        findViewById<TextView>(R.id.tvPopupTitle).text =
            if (detail.isNotBlank()) "$title\n$detail" else title

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
