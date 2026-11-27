package com.trucca.alarm.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.trucca.alarm.MainActivity
import com.trucca.alarm.data.ScheduleRepository
import com.trucca.alarm.model.LunarCalendarUtil
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object ShiftAlarmScheduler {

    private const val MAX_SCHEDULED_ALARMS = 14
    private const val BASE_REQUEST_CODE = 5000
    private const val TEST_REQUEST_CODE = 9999

    fun rescheduleAll(context: Context): Int {
        cancelAll(context)
        val repo = ScheduleRepository(context)
        val settings = repo.getSettings()
        if (!settings.alarmEnabled) return 0

        val now = LocalDateTime.now()
        val activeKip = settings.effectiveKipForMonth(YearMonth.from(now))
        val upcoming = repo.getUpcomingShiftsForKip(activeKip, now, MAX_SCHEDULED_ALARMS)
        var scheduledCount = 0

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val dateFmt = DateTimeFormatter.ofPattern("dd/MM")

        upcoming.forEachIndexed { index, shift ->
            if (shift.alarmDateTime.isAfter(now)) {
                val triggerMillis = shift.alarmDateTime
                    .atZone(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli()

                val range = settings.shiftTimeConfig.formattedRange(shift.shiftType)
                val title = "${shift.shiftType.displayName} • Kíp ${shift.kip}"
                val lunarTag = LunarCalendarUtil.fromSolar(shift.shiftDate).compactTag()
                val detail = "$range • ${shift.shiftDate.format(dateFmt)} $lunarTag"

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
        val repo = ScheduleRepository(context)
        val settings = repo.getSettings()
        val now = LocalDateTime.now()
        val activeKip = settings.effectiveKipForMonth(YearMonth.from(now))
        val nextShift = repo.getUpcomingShiftsForKip(activeKip, now, 1).firstOrNull()
        val dateFmt = DateTimeFormatter.ofPattern("dd/MM")

        val shiftTitle = if (nextShift != null) {
            "${nextShift.shiftType.displayName} • Kíp $activeKip"
        } else {
            "Ca Sáng • Kíp $activeKip"
        }

        val detail = if (nextShift != null) {
            val range = settings.shiftTimeConfig.formattedRange(nextShift.shiftType)
            val lunarTag = LunarCalendarUtil.fromSolar(nextShift.shiftDate).compactTag()
            "$range • ${nextShift.shiftDate.format(dateFmt)} $lunarTag"
        } else {
            "07:00 – 15:00"
        }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val triggerAtMillis = System.currentTimeMillis() + delaySeconds * 1000L
        scheduleExact(
            context = context,
            alarmManager = alarmManager,
            requestCode = TEST_REQUEST_CODE,
            triggerAtMillis = triggerAtMillis,
            title = shiftTitle,
            detail = detail
        )
    }

    fun cancelAll(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        for (i in 0 until MAX_SCHEDULED_ALARMS) {
            val pi = PendingIntent.getBroadcast(
                context,
                BASE_REQUEST_CODE + i,
                Intent(context, AlarmReceiver::class.java),
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
