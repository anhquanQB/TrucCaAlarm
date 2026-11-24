package com.trucca.alarm.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.trucca.alarm.model.ActiveShiftInfo
import com.trucca.alarm.model.AppSettings
import com.trucca.alarm.model.DaySchedule
import com.trucca.alarm.model.DefaultScheduleData
import com.trucca.alarm.model.ShiftType
import com.trucca.alarm.model.UpcomingShiftInfo
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth

class ScheduleRepository(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    fun getSettings(): AppSettings {
        val raw = prefs.getString(KEY_SETTINGS, null) ?: return AppSettings()
        return try {
            gson.fromJson(raw, AppSettings::class.java) ?: AppSettings()
        } catch (_: Exception) {
            AppSettings()
        }
    }

    fun saveSettings(settings: AppSettings) {
        prefs.edit().putString(KEY_SETTINGS, gson.toJson(settings)).apply()
    }

    fun getAllSchedules(): Map<String, DaySchedule> {
        val raw = prefs.getString(KEY_SCHEDULES, null)
        if (raw.isNullOrBlank()) {
            val defaults = DefaultScheduleData.initialSchedules().associateBy { it.date }
            saveSchedulesMap(defaults)
            return defaults
        }
        return try {
            val type = object : TypeToken<List<DaySchedule>>() {}.type
            val list: List<DaySchedule> = gson.fromJson(raw, type) ?: emptyList()
            if (list.isEmpty()) {
                val defaults = DefaultScheduleData.initialSchedules().associateBy { it.date }
                saveSchedulesMap(defaults)
                defaults
            } else {
                list.associateBy { it.date }
            }
        } catch (_: Exception) {
            DefaultScheduleData.initialSchedules().associateBy { it.date }
        }
    }

    fun saveSchedulesMap(map: Map<String, DaySchedule>) {
        val sorted = map.values.sortedBy { it.date }
        prefs.edit().putString(KEY_SCHEDULES, gson.toJson(sorted)).apply()
    }

    fun upsertSchedules(items: List<DaySchedule>) {
        val current = getAllSchedules().toMutableMap()
        items.forEach { incoming ->
            val existing = current[incoming.date]
            current[incoming.date] = if (existing != null) {
                incoming.copy(
                    morningMembers = incoming.morningMembers.ifBlank { existing.morningMembers },
                    afternoonMembers = incoming.afternoonMembers.ifBlank { existing.afternoonMembers },
                    nightMembers = incoming.nightMembers.ifBlank { existing.nightMembers }
                )
            } else {
                incoming
            }
        }
        saveSchedulesMap(current)
    }

    fun saveDayExact(day: DaySchedule) {
        val current = getAllSchedules().toMutableMap()
        current[day.date] = day
        saveSchedulesMap(current)
    }

    fun getSchedulesForMonth(yearMonth: YearMonth): List<DaySchedule> {
        val all = getAllSchedules()
        val prefix = "%04d-%02d-".format(yearMonth.year, yearMonth.monthValue)
        return all.values.filter { it.date.startsWith(prefix) }.sortedBy { it.date }
    }

    fun computeShiftWindow(date: LocalDate, type: ShiftType, settings: AppSettings): Pair<LocalDateTime, LocalDateTime> {
        val cfg = settings.shiftTimeConfig
        val (sh, sm) = cfg.startHourMinute(type)
        val (eh, em) = cfg.endHourMinute(type)
        val start = LocalDateTime.of(date, LocalTime.of(sh, sm))
        var end = LocalDateTime.of(date, LocalTime.of(eh, em))
        if (!end.isAfter(start)) {
            end = end.plusDays(1)
        }
        return start to end
    }

    fun getCurrentActiveShift(now: LocalDateTime = LocalDateTime.now()): ActiveShiftInfo? {
        val settings = getSettings()
        val schedules = getAllSchedules()
        val candidateDates = listOf(now.toLocalDate().minusDays(1), now.toLocalDate())

        for (d in candidateDates) {
            val daySchedule = schedules[d.toString()] ?: continue
            for (type in ShiftType.entries) {
                val (start, end) = computeShiftWindow(d, type, settings)
                if (!now.isBefore(start) && now.isBefore(end)) {
                    val kip = daySchedule.kipFor(type)
                    val remaining = Duration.between(now, end).seconds.coerceAtLeast(0)
                    val total = Duration.between(start, end).seconds.coerceAtLeast(1)
                    // Prefer per-day/per-shift custom crew list if entered, fallback to Kíp default crew
                    val members = daySchedule.customMembersFor(type)
                        .ifBlank { settings.kipMembers[kip].orEmpty() }
                    return ActiveShiftInfo(
                        shiftDate = d,
                        shiftType = type,
                        kip = kip,
                        startDateTime = start,
                        endDateTime = end,
                        remainingSeconds = remaining,
                        totalSeconds = total,
                        membersText = members
                    )
                }
            }
        }
        return null
    }

    fun getUpcomingShiftsForKip(
        kip: String,
        from: LocalDateTime = LocalDateTime.now(),
        limit: Int = 15
    ): List<UpcomingShiftInfo> {
        val settings = getSettings()
        val schedules = getAllSchedules()
        val result = mutableListOf<UpcomingShiftInfo>()

        val sortedDates = schedules.keys.sorted()
        for (dateStr in sortedDates) {
            val d = try { LocalDate.parse(dateStr) } catch (_: Exception) { continue }
            if (d.isBefore(from.toLocalDate().minusDays(1))) continue
            val daySchedule = schedules[dateStr] ?: continue
            for (type in ShiftType.entries) {
                if (daySchedule.kipFor(type).equals(kip, ignoreCase = true)) {
                    val (start, end) = computeShiftWindow(d, type, settings)
                    if (start.isAfter(from)) {
                        val leadMin = settings.alarmLeadMinutes(type).toLong()
                        val alarmTime = start.minusMinutes(leadMin)
                        val secondsUntil = Duration.between(from, start).seconds.coerceAtLeast(0)
                        result.add(
                            UpcomingShiftInfo(
                                shiftDate = d,
                                shiftType = type,
                                kip = kip,
                                startDateTime = start,
                                endDateTime = end,
                                secondsUntilStart = secondsUntil,
                                alarmDateTime = alarmTime
                            )
                        )
                        if (result.size >= limit) return result
                    }
                }
            }
        }
        return result
    }

    companion object {
        private const val PREFS_NAME = "truc_ca_prefs"
        private const val KEY_SETTINGS = "app_settings"
        private const val KEY_SCHEDULES = "day_schedules"

        fun formatCountdown(totalSeconds: Long): String {
            val safe = totalSeconds.coerceAtLeast(0)
            val days = safe / 86400
            val hours = (safe % 86400) / 3600
            val minutes = (safe % 3600) / 60
            return when {
                days > 0 -> "${days} ngày ${hours} giờ ${minutes} phút"
                hours > 0 -> "${hours} giờ ${minutes} phút"
                else -> "${minutes} phút"
            }
        }
    }
}
