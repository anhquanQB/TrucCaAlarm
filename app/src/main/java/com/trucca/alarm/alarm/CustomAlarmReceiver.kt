package com.trucca.alarm.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class CustomAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra(CustomAlarmManager.EXTRA_ALARM_ID, -1)
        val hour = intent.getIntExtra(CustomAlarmManager.EXTRA_HOUR, 0)
        val minute = intent.getIntExtra(CustomAlarmManager.EXTRA_MINUTE, 0)
        val label = intent.getStringExtra(CustomAlarmManager.EXTRA_LABEL)?.trim().orEmpty()
        val repeatDaily = intent.getBooleanExtra(CustomAlarmManager.EXTRA_REPEAT_DAILY, false)

        val timeText = "%02d:%02d".format(hour, minute)
        if (id > 0) {
            CustomAlarmManager.onAlarmFired(context, id, repeatDaily)
        }

        val serviceIntent = Intent(context, AlarmService::class.java).apply {
            action = AlarmService.ACTION_START_1H
            putExtra(
                AlarmService.EXTRA_TITLE,
                if (label.isNotBlank()) "$label • $timeText" else "Báo thức • $timeText"
            )
            putExtra(
                AlarmService.EXTRA_DETAIL,
                if (repeatDaily) "Báo thức hằng ngày • $timeText" else "Báo thức tự do • $timeText"
            )
        }
        ContextCompat.startForegroundService(context, serviceIntent)
    }
}
