package com.trucca.alarm.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.trucca.alarm.widget.ShiftWidgetProvider

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        ShiftAlarmScheduler.rescheduleAll(context)
        CustomAlarmManager.rescheduleAll(context)
        ShiftWidgetProvider.updateAllWidgets(context)
    }
}
