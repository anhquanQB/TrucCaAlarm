package com.trucca.alarm.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.trucca.alarm.model.ActiveShiftInfo
import com.trucca.alarm.model.AppSettings
import com.trucca.alarm.model.DaySchedule
import com.trucca.alarm.model.LearnedSwapPattern
import com.trucca.alarm.model.DefaultScheduleData
import com.trucca.alarm.model.ShiftType
import com.trucca.alarm.model.UpcomingShiftInfo
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth

class ScheduleRepository(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    fun getSettings(): AppSettings {
        cachedSettings?.let { return it }
        val raw = prefs.getString(KEY_SETTINGS, null)
        val loaded = if (raw.isNullOrBlank()) {
            AppSettings()
        } else {
            try {
                gson.fromJson(raw, AppSettings::class.java) ?: AppSettings()
            } catch (_: Exception) {
                AppSettings()
            }
        }
        cachedSettings = loaded
        return loaded
    }

    fun saveSettings(settings: AppSettings) {
        cachedSettings = settings
        prefs.edit().putString(KEY_SETTINGS, gson.toJson(settings)).apply()
    }

    fun getAllSchedules(): Map<String, DaySchedule> {
        cachedSchedules?.let { return it }
        val raw = prefs.getString(KEY_SCHEDULES, null)
        val loaded: Map<String, DaySchedule> = if (raw.isNullOrBlank()) {
            val defaults = DefaultScheduleData.initialSchedules().associateBy { it.date }
            saveSchedulesMap(defaults)
            defaults
        } else {
            try {
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
        cachedSchedules = loaded
        return loaded
    }

    fun saveSchedulesMap(map: Map<String, DaySchedule>) {
        val sortedMap = map.toSortedMap()
        cachedSchedules = sortedMap
        prefs.edit().putString(KEY_SCHEDULES, gson.toJson(sortedMap.values.toList())).apply()
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
        // Dong bo lich goc cho cac ngay moi nhap de nut "Lich goc" khong xoa mat thang moi nhap
        val orig = loadSchedulesByKey(KEY_ORIGINAL_SCHEDULES)?.toMutableMap() ?: current.toMutableMap()
        items.forEach { incoming ->
            val existing = orig[incoming.date]
            orig[incoming.date] = if (existing != null) {
                incoming.copy(
                    morningMembers = incoming.morningMembers.ifBlank { existing.morningMembers },
                    afternoonMembers = incoming.afternoonMembers.ifBlank { existing.afternoonMembers },
                    nightMembers = incoming.nightMembers.ifBlank { existing.nightMembers }
                )
            } else {
                incoming
            }
        }
        saveSchedulesByKey(KEY_ORIGINAL_SCHEDULES, orig)
    }

    fun applySwapSchedules(updatedDays: List<DaySchedule>) {
        val current = getAllSchedules()
        // Luu ban lich goc truoc lan doi ca dau tien (neu chua co)
        if (prefs.getString(KEY_ORIGINAL_SCHEDULES, null).isNullOrBlank()) {
            saveSchedulesByKey(KEY_ORIGINAL_SCHEDULES, current)
        }
        // Luu ban sao ngay truoc lan doi ca nay de co the "Hoan tac"
        saveSchedulesByKey(KEY_LAST_SWAP_BACKUP, current)

        val next = current.toMutableMap()
        updatedDays.forEach { incoming ->
            val existing = next[incoming.date]
            next[incoming.date] = if (existing != null) {
                incoming.copy(
                    morningMembers = incoming.morningMembers.ifBlank { existing.morningMembers },
                    afternoonMembers = incoming.afternoonMembers.ifBlank { existing.afternoonMembers },
                    nightMembers = incoming.nightMembers.ifBlank { existing.nightMembers }
                )
            } else {
                incoming
            }
        }
        saveSchedulesMap(next)
    }

    fun hasSwapBackup(): Boolean {
        return !prefs.getString(KEY_LAST_SWAP_BACKUP, null).isNullOrBlank()
    }

    fun undoLastSwap(): Boolean {
        val backup = loadSchedulesByKey(KEY_LAST_SWAP_BACKUP) ?: return false
        if (backup.isEmpty()) return false
        saveSchedulesMap(backup)
        prefs.edit().remove(KEY_LAST_SWAP_BACKUP).apply()
        return true
    }

    fun restoreOriginalSchedule(): Boolean {
        val original = loadSchedulesByKey(KEY_ORIGINAL_SCHEDULES)
        val target = if (!original.isNullOrEmpty()) {
            original
        } else {
            DefaultScheduleData.initialSchedules().associateBy { it.date }
        }
        saveSchedulesMap(target)
        prefs.edit().remove(KEY_LAST_SWAP_BACKUP).apply()
        return true
    }

    private fun loadSchedulesByKey(key: String): Map<String, DaySchedule>? {
        val raw = prefs.getString(key, null)
        if (raw.isNullOrBlank()) return null
        return try {
            val type = object : TypeToken<List<DaySchedule>>() {}.type
            val list: List<DaySchedule> = gson.fromJson(raw, type) ?: emptyList()
            if (list.isEmpty()) null else list.associateBy { it.date }
        } catch (_: Exception) {
            null
        }
    }

    private fun saveSchedulesByKey(key: String, map: Map<String, DaySchedule>) {
        val sortedMap = map.toSortedMap()
        prefs.edit().putString(key, gson.toJson(sortedMap.values.toList())).apply()
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

        val minDateStr = from.toLocalDate().minusDays(1).toString()
        for ((dateStr, daySchedule) in schedules) {
            if (dateStr < minDateStr) continue
            val d = try { LocalDate.parse(dateStr) } catch (_: Exception) { continue }
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

    fun getLearnedSwapPatterns(): List<LearnedSwapPattern> {
        cachedLearnedPatterns?.let { return it }
        val raw = prefs.getString(KEY_LEARNED_SWAPS, null)
        val loaded: List<LearnedSwapPattern> = if (raw.isNullOrBlank()) {
            emptyList()
        } else {
            try {
                val type = object : TypeToken<List<LearnedSwapPattern>>() {}.type
                gson.fromJson(raw, type) ?: emptyList()
            } catch (_: Exception) {
                emptyList()
            }
        }
        cachedLearnedPatterns = loaded
        return loaded
    }

    fun saveLearnedSwapPattern(pattern: LearnedSwapPattern) {
        val updated = (getLearnedSwapPatterns() + pattern).takeLast(30)
        cachedLearnedPatterns = updated
        prefs.edit().putString(KEY_LEARNED_SWAPS, gson.toJson(updated)).apply()
    }

    fun deleteLearnedSwapPattern(id: String) {
        val updated = getLearnedSwapPatterns().filterNot { it.id == id }
        cachedLearnedPatterns = updated
        prefs.edit().putString(KEY_LEARNED_SWAPS, gson.toJson(updated)).apply()
    }

    companion object {
        private const val PREFS_NAME = "truc_ca_prefs"
        private const val KEY_SETTINGS = "app_settings"
        private const val KEY_SCHEDULES = "day_schedules"
        private const val KEY_ORIGINAL_SCHEDULES = "original_day_schedules"
        private const val KEY_LAST_SWAP_BACKUP = "last_swap_backup"
        private const val KEY_LEARNED_SWAPS = "learned_swap_patterns"

        @Volatile
        private var cachedLearnedPatterns: List<LearnedSwapPattern>? = null

        @Volatile
        private var cachedSettings: AppSettings? = null

        @Volatile
        private var cachedSchedules: Map<String, DaySchedule>? = null

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
