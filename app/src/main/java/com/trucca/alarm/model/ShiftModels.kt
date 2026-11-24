package com.trucca.alarm.model

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth

enum class ShiftType(val displayName: String, val shortName: String) {
    MORNING("Ca sáng", "Sáng"),
    AFTERNOON("Ca chiều", "Chiều"),
    NIGHT("Ca đêm", "Đêm")
}

data class DaySchedule(
    val date: String, // YYYY-MM-DD
    val morningKip: String,
    val afternoonKip: String,
    val nightKip: String,
    val morningMembers: String = "",
    val afternoonMembers: String = "",
    val nightMembers: String = ""
) {
    fun kipFor(shiftType: ShiftType): String = when (shiftType) {
        ShiftType.MORNING -> morningKip
        ShiftType.AFTERNOON -> afternoonKip
        ShiftType.NIGHT -> nightKip
    }

    fun customMembersFor(shiftType: ShiftType): String = when (shiftType) {
        ShiftType.MORNING -> morningMembers
        ShiftType.AFTERNOON -> afternoonMembers
        ShiftType.NIGHT -> nightMembers
    }

    fun hasCustomMembers(): Boolean {
        return morningMembers.isNotBlank() || afternoonMembers.isNotBlank() || nightMembers.isNotBlank()
    }

    fun shiftForKip(kip: String): ShiftType? = when {
        morningKip.equals(kip, ignoreCase = true) -> ShiftType.MORNING
        afternoonKip.equals(kip, ignoreCase = true) -> ShiftType.AFTERNOON
        nightKip.equals(kip, ignoreCase = true) -> ShiftType.NIGHT
        else -> null
    }
}

data class ShiftTimeConfig(
    val morningStartHour: Int = 7,
    val morningStartMinute: Int = 0,
    val afternoonStartHour: Int = 15,
    val afternoonStartMinute: Int = 0,
    val nightStartHour: Int = 23,
    val nightStartMinute: Int = 0
) {
    fun startHourMinute(type: ShiftType): Pair<Int, Int> = when (type) {
        ShiftType.MORNING -> morningStartHour to morningStartMinute
        ShiftType.AFTERNOON -> afternoonStartHour to afternoonStartMinute
        ShiftType.NIGHT -> nightStartHour to nightStartMinute
    }

    fun endHourMinute(type: ShiftType): Pair<Int, Int> = when (type) {
        ShiftType.MORNING -> afternoonStartHour to afternoonStartMinute
        ShiftType.AFTERNOON -> nightStartHour to nightStartMinute
        ShiftType.NIGHT -> morningStartHour to morningStartMinute
    }

    fun formattedRange(type: ShiftType): String {
        val (sh, sm) = startHourMinute(type)
        val (eh, em) = endHourMinute(type)
        return "%02d:%02d – %02d:%02d".format(sh, sm, eh, em)
    }
}

data class AppSettings(
    val selectedKip: String = "A",
    val alarmEnabled: Boolean = true,
    val morningAlarmBeforeMin: Int = 60,
    val afternoonAlarmBeforeMin: Int = 60,
    val nightAlarmBeforeMin: Int = 60,
    val watchSyncEnabled: Boolean = true,
    val watchRepeatCount: Int = 3,
    val shiftTimeConfig: ShiftTimeConfig = ShiftTimeConfig(),
    val kipMembers: Map<String, String> = mapOf(
        "A" to "Kíp A (Vào Báo thức để nhập tên thành viên)",
        "B" to "Kíp B (Vào Báo thức để nhập tên thành viên)",
        "C" to "Chu Tiến Đạt, Trần Đình Hợi, Nguyễn Văn Dũng(DP)",
        "D" to "Kíp D (Vào Báo thức để nhập tên thành viên)",
        "E" to "Kíp E (Vào Báo thức để nhập tên thành viên)"
    )
) {
    fun alarmLeadMinutes(type: ShiftType): Int = when (type) {
        ShiftType.MORNING -> morningAlarmBeforeMin
        ShiftType.AFTERNOON -> afternoonAlarmBeforeMin
        ShiftType.NIGHT -> nightAlarmBeforeMin
    }
}

data class ActiveShiftInfo(
    val shiftDate: LocalDate,
    val shiftType: ShiftType,
    val kip: String,
    val startDateTime: LocalDateTime,
    val endDateTime: LocalDateTime,
    val remainingSeconds: Long,
    val totalSeconds: Long,
    val membersText: String
)

data class UpcomingShiftInfo(
    val shiftDate: LocalDate,
    val shiftType: ShiftType,
    val kip: String,
    val startDateTime: LocalDateTime,
    val endDateTime: LocalDateTime,
    val secondsUntilStart: Long,
    val alarmDateTime: LocalDateTime
)

object DefaultScheduleData {
    val ALL_KIPS = listOf("A", "B", "C", "D", "E")

    // Exact 10-day cycle extracted from user's November & December 2026 schedules
    private val cycleMorning = listOf("B", "B", "C", "C", "D", "D", "E", "E", "A", "A")
    private val cycleAfternoon = listOf("C", "D", "D", "E", "E", "A", "A", "B", "B", "C")
    private val cycleNight = listOf("A", "A", "B", "B", "C", "C", "D", "D", "E", "E")

    fun generateCycleForMonth(year: Int, month: Int, offsetDayIndex: Int = 0): List<DaySchedule> {
        val ym = YearMonth.of(year, month)
        val result = mutableListOf<DaySchedule>()
        for (day in 1..ym.lengthOfMonth()) {
            val idx = ((day - 1 + offsetDayIndex) % 10 + 10) % 10
            val dateStr = LocalDate.of(year, month, day).toString()
            result.add(
                DaySchedule(
                    date = dateStr,
                    morningKip = cycleMorning[idx],
                    afternoonKip = cycleAfternoon[idx],
                    nightKip = cycleNight[idx]
                )
            )
        }
        return result
    }

    fun initialSchedules(): List<DaySchedule> {
        return generateCycleForMonth(2026, 11, 0) + generateCycleForMonth(2026, 12, 0)
    }
}
