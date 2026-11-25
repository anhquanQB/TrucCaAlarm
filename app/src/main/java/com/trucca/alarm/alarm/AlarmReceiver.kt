package com.trucca.alarm.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra(AlarmService.EXTRA_TITLE) ?: "Đến giờ chuẩn bị vào ca!"
        val detail = intent.getStringExtra(AlarmService.EXTRA_DETAIL) ?: "Kiểm tra lịch trực ca của bạn."

        val serviceIntent = Intent(context, AlarmService::class.java).apply {
            action = AlarmService.ACTION_START_1H
            putExtra(AlarmService.EXTRA_TITLE, title)
            putExtra(AlarmService.EXTRA_DETAIL, detail)
        }
        ContextCompat.startForegroundService(context, serviceIntent)
    }
}
