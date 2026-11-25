package com.trucca.alarm.model

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs

data class ShiftSlot(
    val date: LocalDate,
    val shiftType: ShiftType
) : Comparable<ShiftSlot> {
    val absoluteIndex: Long
        get() = date.toEpochDay() * 3L + shiftType.ordinal

    override fun compareTo(other: ShiftSlot): Int = absoluteIndex.compareTo(other.absoluteIndex)
}

data class SwapStep(
    val dateStr: String, // YYYY-MM-DD
    val shiftType: ShiftType,
    val fromKip: String,
    val toKip: String
)

data class SwapConflict(
    val kip: String,
    val firstDate: String,
    val firstShift: ShiftType,
    val secondDate: String,
    val secondShift: ShiftType,
    val restShifts: Int
) {
    fun summary(): String {
        return "Kíp $kip: ${firstShift.shortName} ${firstDate.takeLast(5)} & ${secondShift.shortName} ${secondDate.takeLast(5)} (chỉ nghỉ $restShifts ca)"
    }
}

data class SwapSuggestion(
    val participantCount: Int, // 2, 3, or 4
    val steps: List<SwapStep>,
    val maxDayDistance: Int,
    val totalDaySpan: Int,
    val allSameMonth: Boolean,
    val isFromLearnedPattern: Boolean = false,
    val patternLabel: String = ""
)

data class LearnedSwapStepOffset(
    val dayOffset: Int,
    val shiftTypeOrdinal: Int,
    val fromRoleIndex: Int, // 0 = myKip, 1 = kipB, 2 = kipC, 3 = kipD
    val toRoleIndex: Int
)

data class LearnedSwapPattern(
    val id: String,
    val name: String,
    val participantCount: Int,
    val offsets: List<LearnedSwapStepOffset>
)

object ShiftSwapEngine {

    private const val MIN_SLOT_GAP = 3L // >= 3 slots means >= 2 full 8h shifts (16h >= 12h) rest

    fun toSlotIndex(dateStr: String, shiftType: ShiftType): Long? {
        val d = try { LocalDate.parse(dateStr) } catch (_: Exception) { return null }
        return d.toEpochDay() * 3L + shiftType.ordinal
    }

    /**
     * Checks the entire schedule for any 12h (2-shift) rest violations for a specific kip.
     */
    fun findConflictsForKip(
        schedules: Map<String, DaySchedule>,
        kip: String
    ): List<SwapConflict> {
        val slots = mutableListOf<ShiftSlot>()
        for ((dateStr, sched) in schedules) {
            val d = try { LocalDate.parse(dateStr) } catch (_: Exception) { continue }
            for (type in ShiftType.entries) {
                if (sched.kipFor(type).equals(kip, ignoreCase = true)) {
                    slots.add(ShiftSlot(d, type))
                }
            }
        }
        slots.sort()
        val conflicts = mutableListOf<SwapConflict>()
        for (i in 0 until slots.size - 1) {
            val a = slots[i]
            val b = slots[i + 1]
            val gap = b.absoluteIndex - a.absoluteIndex
            if (gap < MIN_SLOT_GAP) {
                val restShifts = (gap - 1L).coerceAtLeast(0L).toInt()
                conflicts.add(
                    SwapConflict(
                        kip = kip,
                        firstDate = a.date.toString(),
                        firstShift = a.shiftType,
                        secondDate = b.date.toString(),
                        secondShift = b.shiftType,
                        restShifts = restShifts
                    )
                )
            }
        }
        return conflicts
    }

    /**
     * Returns a set of Pair(dateStr, ShiftType) that are involved in any conflict for the given kip.
     */
    fun conflictCellKeys(conflicts: List<SwapConflict>): Set<Pair<String, ShiftType>> {
        val keys = mutableSetOf<Pair<String, ShiftType>>()
        for (c in conflicts) {
            keys.add(c.firstDate to c.firstShift)
            keys.add(c.secondDate to c.secondShift)
        }
        return keys
    }

    /**
     * Simulates applying swap steps and returns all resulting conflicts across all affected kips.
     */
    fun validateSwap(
        schedules: Map<String, DaySchedule>,
        steps: List<SwapStep>
    ): List<SwapConflict> {
        val simulated = schedules.toMutableMap()
        val affectedKips = mutableSetOf<String>()
        for (step in steps) {
            val existing = simulated[step.dateStr] ?: continue
            affectedKips.add(step.fromKip)
            affectedKips.add(step.toKip)
            simulated[step.dateStr] = replaceKip(existing, step.shiftType, step.toKip)
        }
        val allConflicts = mutableListOf<SwapConflict>()
        for (k in affectedKips) {
            allConflicts.addAll(findConflictsForKip(simulated, k))
        }
        return allConflicts
    }

    /**
     * Applies swap steps and returns the list of updated DaySchedule objects to save.
     */
    fun applySwap(
        schedules: Map<String, DaySchedule>,
        steps: List<SwapStep>
    ): List<DaySchedule> {
        val updatedMap = mutableMapOf<String, DaySchedule>()
        for (step in steps) {
            val base = updatedMap[step.dateStr] ?: schedules[step.dateStr] ?: continue
            updatedMap[step.dateStr] = replaceKip(base, step.shiftType, step.toKip)
        }
        return updatedMap.values.toList()
    }

    private fun replaceKip(day: DaySchedule, type: ShiftType, newKip: String): DaySchedule {
        return when (type) {
            ShiftType.MORNING -> day.copy(morningKip = newKip)
            ShiftType.AFTERNOON -> day.copy(afternoonKip = newKip)
            ShiftType.NIGHT -> day.copy(nightKip = newKip)
        }
    }

    /**
     * Extracts a reusable relative pattern from a user-taught valid swap chain.
     */
    fun extractLearnedPattern(
        targetDate: LocalDate,
        steps: List<SwapStep>,
        myKip: String,
        name: String
    ): LearnedSwapPattern {
        val roles = mutableListOf(myKip)
        for (s in steps) {
            if (s.fromKip !in roles) roles.add(s.fromKip)
            if (s.toKip !in roles) roles.add(s.toKip)
        }
        val offsets = steps.mapNotNull { s ->
            val d = try { LocalDate.parse(s.dateStr) } catch (_: Exception) { return@mapNotNull null }
            val diff = ChronoUnit.DAYS.between(targetDate, d).toInt()
            LearnedSwapStepOffset(
                dayOffset = diff,
                shiftTypeOrdinal = s.shiftType.ordinal,
                fromRoleIndex = roles.indexOf(s.fromKip).coerceAtLeast(0),
                toRoleIndex = roles.indexOf(s.toKip).coerceAtLeast(0)
            )
        }
        return LearnedSwapPattern(
            id = "pat_${System.currentTimeMillis()}",
            name = name.ifBlank { "Mẫu ${roles.size} kíp" },
            participantCount = roles.size,
            offsets = offsets
        )
    }

    /**
     * Finds valid swap suggestions within +/- maxDays (default 30 days).
     * Priority:
     * 1. Learned patterns first
     * 2. Participant count: 2 kips -> 3 kips -> 4 kips
     * 3. Same month prioritized over cross-month (within 30 days)
     * 4. Closest days to targetDate (shortest distance first)
     */
    fun findSwapSuggestions(
        schedules: Map<String, DaySchedule>,
        targetDate: LocalDate,
        targetShiftType: ShiftType,
        myKip: String,
        learnedPatterns: List<LearnedSwapPattern> = emptyList(),
        maxDays: Int = 30
    ): List<SwapSuggestion> {
        val targetDateStr = targetDate.toString()
        val targetSched = schedules[targetDateStr] ?: return emptyList()
        if (!targetSched.kipFor(targetShiftType).equals(myKip, ignoreCase = true)) {
            return emptyList()
        }

        val targetSlot = ShiftSlot(targetDate, targetShiftType)
        val targetSlotIdx = targetSlot.absoluteIndex

        // Precompute sorted slot indices per kip across ENTIRE schedule for O(1)/O(logN) rest checks
        val kipSlots = mutableMapOf<String, MutableList<Long>>()
        for (k in DefaultScheduleData.ALL_KIPS) kipSlots[k] = mutableListOf()
        for ((dateStr, sched) in schedules) {
            val d = try { LocalDate.parse(dateStr) } catch (_: Exception) { continue }
            val base = d.toEpochDay() * 3L
            for (type in ShiftType.entries) {
                val k = sched.kipFor(type)
                kipSlots[k]?.add(base + type.ordinal)
            }
        }
        kipSlots.values.forEach { it.sort() }

        fun canKipTake(kip: String, addSlot: Long, removeSlot: Long): Boolean {
            val list = kipSlots[kip] ?: return false
            for (s in list) {
                if (s == removeSlot) continue
                if (abs(s - addSlot) < MIN_SLOT_GAP) return false
            }
            return true
        }

        // Collect candidate shifts within +/- maxDays sorted by closeness to targetDate
        val windowStart = targetDate.minusDays(maxDays.toLong())
        val windowEnd = targetDate.plusDays(maxDays.toLong())
        val candidatesByKip = mutableMapOf<String, MutableList<ShiftSlot>>()
        for (k in DefaultScheduleData.ALL_KIPS) {
            if (!k.equals(myKip, ignoreCase = true)) candidatesByKip[k] = mutableListOf()
        }

        var d = windowStart
        while (!d.isAfter(windowEnd)) {
            val sched = schedules[d.toString()]
            if (sched != null) {
                for (type in ShiftType.entries) {
                    val k = sched.kipFor(type)
                    if (!k.equals(myKip, ignoreCase = true)) {
                        candidatesByKip[k]?.add(ShiftSlot(d, type))
                    }
                }
            }
            d = d.plusDays(1)
        }

        // Sort each kip's candidates by (sameMonth desc, dayDistance asc)
        for (list in candidatesByKip.values) {
            list.sortWith(
                compareByDescending<ShiftSlot> { it.date.year == targetDate.year && it.date.month == targetDate.month }
                    .thenBy { abs(ChronoUnit.DAYS.between(targetDate, it.date)) }
                    .thenBy { abs(it.absoluteIndex - targetSlotIdx) }
            )
        }

        val results = mutableListOf<SwapSuggestion>()
        val seenKeys = mutableSetOf<String>()

        fun addIfValid(suggestion: SwapSuggestion) {
            val key = suggestion.steps.joinToString("|") { "${it.dateStr}:${it.shiftType.ordinal}:${it.fromKip}->${it.toKip}" }
            if (key in seenKeys) return
            seenKeys.add(key)
            results.add(suggestion)
        }

        // 0. Try Learned Patterns first
        val otherKips = DefaultScheduleData.ALL_KIPS.filter { !it.equals(myKip, ignoreCase = true) }
        for (pat in learnedPatterns) {
            if (pat.offsets.size < 2) continue
            // Try mapping role 1..N to otherKips
            val permList = when (pat.participantCount) {
                2 -> otherKips.map { listOf(myKip, it) }
                3 -> {
                    val perms = mutableListOf<List<String>>()
                    for (b in otherKips) for (c in otherKips) if (b != c) perms.add(listOf(myKip, b, c))
                    perms
                }
                else -> emptyList()
            }
            for (roles in permList) {
                val steps = mutableListOf<SwapStep>()
                var ok = true
                for (off in pat.offsets) {
                    val stepDate = targetDate.plusDays(off.dayOffset.toLong())
                    if (abs(ChronoUnit.DAYS.between(targetDate, stepDate)) > maxDays) { ok = false; break }
                    val st = ShiftType.entries.getOrNull(off.shiftTypeOrdinal) ?: run { ok = false; break }
                    val fromK = roles.getOrNull(off.fromRoleIndex) ?: run { ok = false; break }
                    val toK = roles.getOrNull(off.toRoleIndex) ?: run { ok = false; break }
                    val sched = schedules[stepDate.toString()] ?: run { ok = false; break }
                    if (!sched.kipFor(st).equals(fromK, ignoreCase = true)) { ok = false; break }
                    steps.add(SwapStep(stepDate.toString(), st, fromK, toK))
                }
                if (ok && steps.isNotEmpty() && validateSwap(schedules, steps).isEmpty()) {
                    val dates = steps.map { LocalDate.parse(it.dateStr) }
                    val maxDist = dates.maxOf { abs(ChronoUnit.DAYS.between(targetDate, it)).toInt() }
                    val span = dates.sumOf { abs(ChronoUnit.DAYS.between(targetDate, it)).toInt() }
                    val sameM = dates.all { it.year == targetDate.year && it.month == targetDate.month }
                    addIfValid(
                        SwapSuggestion(
                            participantCount = pat.participantCount,
                            steps = steps,
                            maxDayDistance = maxDist,
                            totalDaySpan = span,
                            allSameMonth = sameM,
                            isFromLearnedPattern = true,
                            patternLabel = pat.name
                        )
                    )
                }
            }
        }

        // 1. Priority 1: 2-person swaps (myKip <-> kipB)
        for (kipB in otherKips) {
            val bSlots = candidatesByKip[kipB].orEmpty()
            for (slotB in bSlots) {
                if (canKipTake(kipB, targetSlotIdx, slotB.absoluteIndex) &&
                    canKipTake(myKip, slotB.absoluteIndex, targetSlotIdx)
                ) {
                    val dist = abs(ChronoUnit.DAYS.between(targetDate, slotB.date)).toInt()
                    val sameM = slotB.date.year == targetDate.year && slotB.date.month == targetDate.month
                    addIfValid(
                        SwapSuggestion(
                            participantCount = 2,
                            steps = listOf(
                                SwapStep(targetDateStr, targetShiftType, myKip, kipB),
                                SwapStep(slotB.date.toString(), slotB.shiftType, kipB, myKip)
                            ),
                            maxDayDistance = dist,
                            totalDaySpan = dist,
                            allSameMonth = sameM
                        )
                    )
                }
            }
        }

        // 2. Priority 2: 3-person cycle swaps (myKip -> kipB -> kipC -> myKip)
        // Only search top nearest candidates per kip to keep execution instant (<30ms)
        val threeCap = 12
        var threeFound = 0
        for (kipB in otherKips) {
            val bSlots = candidatesByKip[kipB].orEmpty().take(12)
            for (kipC in otherKips) {
                if (kipC == kipB) continue
                val cSlots = candidatesByKip[kipC].orEmpty().take(12)
                for (slotB in bSlots) {
                    // kipB takes targetSlot, gives slotB
                    if (!canKipTake(kipB, targetSlotIdx, slotB.absoluteIndex)) continue
                    for (slotC in cSlots) {
                        // kipC takes slotB, gives slotC; myKip takes slotC
                        if (canKipTake(kipC, slotB.absoluteIndex, slotC.absoluteIndex) &&
                            canKipTake(myKip, slotC.absoluteIndex, targetSlotIdx)
                        ) {
                            val dB = abs(ChronoUnit.DAYS.between(targetDate, slotB.date)).toInt()
                            val dC = abs(ChronoUnit.DAYS.between(targetDate, slotC.date)).toInt()
                            val sameM = (slotB.date.year == targetDate.year && slotB.date.month == targetDate.month) &&
                                (slotC.date.year == targetDate.year && slotC.date.month == targetDate.month)
                            addIfValid(
                                SwapSuggestion(
                                    participantCount = 3,
                                    steps = listOf(
                                        SwapStep(targetDateStr, targetShiftType, myKip, kipB),
                                        SwapStep(slotB.date.toString(), slotB.shiftType, kipB, kipC),
                                        SwapStep(slotC.date.toString(), slotC.shiftType, kipC, myKip)
                                    ),
                                    maxDayDistance = maxOf(dB, dC),
                                    totalDaySpan = dB + dC,
                                    allSameMonth = sameM
                                )
                            )
                            threeFound++
                            if (threeFound >= threeCap) break
                        }
                    }
                    if (threeFound >= threeCap) break
                }
                if (threeFound >= threeCap) break
            }
            if (threeFound >= threeCap) break
        }

        // 3. Priority 3: 4-person cycle swaps (myKip -> B -> C -> D -> myKip) if 2-person & 3-person are scarce
        if (results.count { it.participantCount <= 3 } < 5) {
            val fourCap = 6
            var fourFound = 0
            for (kipB in otherKips) {
                val bSlots = candidatesByKip[kipB].orEmpty().take(6)
                for (kipC in otherKips) {
                    if (kipC == kipB) continue
                    val cSlots = candidatesByKip[kipC].orEmpty().take(6)
                    for (kipD in otherKips) {
                        if (kipD == kipB || kipD == kipC) continue
                        val dSlots = candidatesByKip[kipD].orEmpty().take(6)
                        for (slotB in bSlots) {
                            if (!canKipTake(kipB, targetSlotIdx, slotB.absoluteIndex)) continue
                            for (slotC in cSlots) {
                                if (!canKipTake(kipC, slotB.absoluteIndex, slotC.absoluteIndex)) continue
                                for (slotD in dSlots) {
                                    if (canKipTake(kipD, slotC.absoluteIndex, slotD.absoluteIndex) &&
                                        canKipTake(myKip, slotD.absoluteIndex, targetSlotIdx)
                                    ) {
                                        val dB = abs(ChronoUnit.DAYS.between(targetDate, slotB.date)).toInt()
                                        val dC = abs(ChronoUnit.DAYS.between(targetDate, slotC.date)).toInt()
                                        val dD = abs(ChronoUnit.DAYS.between(targetDate, slotD.date)).toInt()
                                        val sameM = listOf(slotB, slotC, slotD).all {
                                            it.date.year == targetDate.year && it.date.month == targetDate.month
                                        }
                                        addIfValid(
                                            SwapSuggestion(
                                                participantCount = 4,
                                                steps = listOf(
                                                    SwapStep(targetDateStr, targetShiftType, myKip, kipB),
                                                    SwapStep(slotB.date.toString(), slotB.shiftType, kipB, kipC),
                                                    SwapStep(slotC.date.toString(), slotC.shiftType, kipC, kipD),
                                                    SwapStep(slotD.date.toString(), slotD.shiftType, kipD, myKip)
                                                ),
                                                maxDayDistance = maxOf(dB, dC, dD),
                                                totalDaySpan = dB + dC + dD,
                                                allSameMonth = sameM
                                            )
                                        )
                                        fourFound++
                                        if (fourFound >= fourCap) break
                                    }
                                }
                                if (fourFound >= fourCap) break
                            }
                            if (fourFound >= fourCap) break
                        }
                        if (fourFound >= fourCap) break
                    }
                    if (fourFound >= fourCap) break
                }
                if (fourFound >= fourCap) break
            }
        }

        // Final ranking:
        // 1. Learned patterns first
        // 2. Fewer participants (2 -> 3 -> 4)
        // 3. Same month first, then cross-month within 30 days
        // 4. Shortest max day distance, then shortest total day span
        return results.sortedWith(
            compareByDescending<SwapSuggestion> { it.isFromLearnedPattern }
                .thenBy { it.participantCount }
                .thenByDescending { it.allSameMonth }
                .thenBy { it.maxDayDistance }
                .thenBy { it.totalDaySpan }
        ).take(20)
    }
}
