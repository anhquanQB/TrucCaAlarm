package com.trucca.alarm.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.trucca.alarm.MainActivity
import com.trucca.alarm.data.ScheduleRepository
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object ShiftAlarmScheduler {

    private const val MAX_SCHEDULED_ALARMS = 14
    private const val BASE_REQUEST_CODE = 5000
    private const val TEST_REQUEST_CODE = 9999
    private const val SNOOZE_REQUEST_CODE = 9998

    fun rescheduleAll(context: Context): Int {
        cancelAll(context)
        val repo = ScheduleRepository(context)
        val settings = repo.getSettings()
        if (!settings.alarmEnabled) return 0

        val now = LocalDateTime.now()
        val upcoming = repo.getUpcomingShiftsForKip(settings.selectedKip, now, MAX_SCHEDULED_ALARMS)
        var scheduledCount = 0

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val timeFmt = DateTimeFormatter.ofPattern("HH:mm")

        upcoming.forEachIndexed { index, shift ->
            if (shift.alarmDateTime.isAfter(now)) {
                val triggerMillis = shift.alarmDateTime
                    .atZone(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli()

                val title = "Sắp vào ${shift.shiftType.displayName} • Kíp ${shift.kip}"
                val detail = "Giờ vào ca: ${shift.startDateTime.format(timeFmt)} (${settings.shiftTimeConfig.formattedRange(shift.shiftType)})"

                scheduleExact(
                    context = context,
                    alarmManager = alarmManager,
                    requestCode = BASE_REQUEST_CODE + index,
                    triggerAtMillis = triggerMillis,
                    title = title,
                    detail = detail
                )
                scheduledCount++
            }
        }
        return scheduledCount
    }

    fun scheduleTestAlarm(context: Context, delaySeconds: Long = 5) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val triggerAtMillis = System.currentTimeMillis() + delaySeconds * 1000L
        scheduleExact(
            context = context,
            alarmManager = alarmManager,
            requestCode = TEST_REQUEST_CODE,
            triggerAtMillis = triggerAtMillis,
            title = "Báo thức thử nghiệm • Trực Ca",
            detail = "Chuông báo thức điện thoại & thông báo sang đồng hồ Huawei đang hoạt động."
        )
    }

    fun scheduleSnooze(context: Context, title: String, detail: String, minutes: Long = 5) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val triggerAtMillis = System.currentTimeMillis() + minutes * 60_000L
        scheduleExact(
            context = context,
            alarmManager = alarmManager,
            requestCode = SNOOZE_REQUEST_CODE,
            triggerAtMillis = triggerAtMillis,
            title = "$title (Báo lại)",
            detail = detail
        )
    }

    fun cancelAll(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        for (i in 0 until MAX_SCHEDULED_ALARMS) {
            val intent = Intent(context, AlarmReceiver::class.java)
            val pi = PendingIntent.getBroadcast(
                context,
                BASE_REQUEST_CODE + i,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pi)
        }
    }

    private fun scheduleExact(
        context: Context,
        alarmManager: AlarmManager,
        requestCode: Int,
        triggerAtMillis: Long,
        title: String,
        detail: String
    ) {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra(AlarmService.EXTRA_TITLE, title)
            putExtra(AlarmService.EXTRA_DETAIL, detail)
        }
        val operation = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val showIntent = PendingIntent.getActivity(
            context,
            requestCode,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val canExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else true

        if (canExact) {
            val info = AlarmManager.AlarmClockInfo(triggerAtMillis, showIntent)
            alarmManager.setAlarmClock(info, operation)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, operation)
        }
    }
}
