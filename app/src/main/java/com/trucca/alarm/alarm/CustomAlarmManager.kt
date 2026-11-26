package com.trucca.alarm.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.trucca.alarm.MainActivity
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDateTime
import java.time.ZoneId

data class CustomAlarmItem(
    val id: Int,
    val hour: Int,
    val minute: Int,
    val label: String = "Báo thức",
    val enabled: Boolean = true,
    val repeatDaily: Boolean = false
) {
    fun formattedTime(): String = "%02d:%02d".format(hour, minute)
}

object CustomAlarmManager {

    private const val PREF_NAME = "trucca_custom_alarms_prefs"
    private const val KEY_ALARMS = "custom_alarms_json"
    private const val REQUEST_CODE_BASE = 41000

    const val EXTRA_ALARM_ID = "custom_alarm_id"
    const val EXTRA_HOUR = "custom_alarm_hour"
    const val EXTRA_MINUTE = "custom_alarm_minute"
    const val EXTRA_LABEL = "custom_alarm_label"
    const val EXTRA_REPEAT_DAILY = "custom_alarm_repeat_daily"

    fun getAll(context: Context): List<CustomAlarmItem> {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_ALARMS, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            val result = mutableListOf<CustomAlarmItem>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                result.add(
                    CustomAlarmItem(
                        id = obj.getInt("id"),
                        hour = obj.getInt("hour").coerceIn(0, 23),
                        minute = obj.getInt("minute").coerceIn(0, 59),
                        label = obj.optString("label", "Báo thức"),
                        enabled = obj.optBoolean("enabled", true),
                        repeatDaily = obj.optBoolean("repeatDaily", false)
                    )
                )
            }
            result.sortedWith(compareBy<CustomAlarmItem> { it.hour }.thenBy { it.minute }.thenBy { it.id })
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun addAlarm(
        context: Context,
        hour: Int,
        minute: Int,
        label: String,
        repeatDaily: Boolean
    ): List<CustomAlarmItem> {
        val current = getAll(context).toMutableList()
        val nextId = ((current.maxOfOrNull { it.id } ?: 0) + 1).coerceAtLeast(1)
        val newItem = CustomAlarmItem(
            id = nextId,
            hour = hour.coerceIn(0, 23),
            minute = minute.coerceIn(0, 59),
            label = label.trim().ifBlank { "Báo thức" },
            enabled = true,
            repeatDaily = repeatDaily
        )
        current.add(newItem)
        persist(context, current)
        scheduleOne(context, newItem)
        return getAll(context)
    }

    fun toggleAlarm(context: Context, id: Int, enabled: Boolean): List<CustomAlarmItem> {
        val current = getAll(context).map { item ->
            if (item.id == id) item.copy(enabled = enabled) else item
        }
        persist(context, current)
        val target = current.firstOrNull { it.id == id }
        if (target != null) {
            if (enabled) scheduleOne(context, target) else cancelOne(context, id)
        }
        return current
    }

    fun deleteAlarm(context: Context, id: Int): List<CustomAlarmItem> {
        cancelOne(context, id)
        val updated = getAll(context).filterNot { it.id == id }
        persist(context, updated)
        return updated
    }

    fun rescheduleAll(context: Context) {
        val all = getAll(context)
        all.forEach { item ->
            if (item.enabled) scheduleOne(context, item) else cancelOne(context, item.id)
        }
    }

    fun onAlarmFired(context: Context, id: Int, repeatDaily: Boolean) {
        val target = getAll(context).firstOrNull { it.id == id } ?: return
        if (repeatDaily) {
            scheduleOne(context, target, forceNextDay = true)
        } else {
            toggleAlarm(context, id, enabled = false)
        }
    }

    private fun scheduleOne(
        context: Context,
        item: CustomAlarmItem,
        forceNextDay: Boolean = false
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val now = LocalDateTime.now()
        var trigger = now.withHour(item.hour).withMinute(item.minute).withSecond(0).withNano(0)
        if (forceNextDay || !trigger.isAfter(now)) {
            trigger = trigger.plusDays(1)
        }
        val triggerAtMillis = trigger.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val pi = buildBroadcastPendingIntent(context, item)
        val showIntent = PendingIntent.getActivity(
            context,
            REQUEST_CODE_BASE + item.id,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            val clockInfo = AlarmManager.AlarmClockInfo(triggerAtMillis, showIntent)
            alarmManager.setAlarmClock(clockInfo, pi)
        } catch (_: SecurityException) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
            } else {
                alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
            }
        } catch (_: Exception) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
            } else {
                alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
            }
        }
    }

    private fun cancelOne(context: Context, id: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val dummy = CustomAlarmItem(id = id, hour = 0, minute = 0)
        alarmManager.cancel(buildBroadcastPendingIntent(context, dummy))
    }

    private fun buildBroadcastPendingIntent(context: Context, item: CustomAlarmItem): PendingIntent {
        val intent = Intent(context, CustomAlarmReceiver::class.java).apply {
            putExtra(EXTRA_ALARM_ID, item.id)
            putExtra(EXTRA_HOUR, item.hour)
            putExtra(EXTRA_MINUTE, item.minute)
            putExtra(EXTRA_LABEL, item.label)
            putExtra(EXTRA_REPEAT_DAILY, item.repeatDaily)
        }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_BASE + item.id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun persist(context: Context, items: List<CustomAlarmItem>) {
        val arr = JSONArray()
        items.forEach { item ->
            arr.put(
                JSONObject().apply {
                    put("id", item.id)
                    put("hour", item.hour)
                    put("minute", item.minute)
                    put("label", item.label)
                    put("enabled", item.enabled)
                    put("repeatDaily", item.repeatDaily)
                }
            )
        }
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ALARMS, arr.toString())
            .apply()
    }
}
