package com.example.courseschedule

import androidx.compose.runtime.Immutable
import java.time.LocalTime
import java.time.format.DateTimeFormatter

enum class PeriodDayPart { MORNING, AFTERNOON, EVENING }

sealed interface PeriodTopologyOperation {
    data class AddAfter(val periodIndex: Int) : PeriodTopologyOperation
    data class Delete(val periodIndex: Int) : PeriodTopologyOperation
}

@Immutable
data class PeriodSchemeDraft(
    val scheme: PeriodSchemeEntity,
    val times: List<PeriodSchemeTimeEntity>,
    val specialBreaks: Map<Int, Int> = emptyMap(),
    val overriddenPeriods: Set<Int> = emptySet()
)

@Immutable
data class SchedulePeriodSchemesDraft(
    val schemes: List<PeriodSchemeDraft>,
    val activeSchemeId: Long,
    val topologyOperations: List<PeriodTopologyOperation> = emptyList()
)

private val PeriodTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

fun ScheduleConfigEntity.periodCount(part: PeriodDayPart): Int = when (part) {
    PeriodDayPart.MORNING -> morningPeriodCount
    PeriodDayPart.AFTERNOON -> afternoonPeriodCount
    PeriodDayPart.EVENING -> eveningPeriodCount
}

fun ScheduleConfigEntity.totalPeriodCount(): Int =
    morningPeriodCount + afternoonPeriodCount + eveningPeriodCount

fun ScheduleConfigEntity.periodRange(part: PeriodDayPart): IntRange {
    val morningEnd = morningPeriodCount
    val afternoonEnd = morningEnd + afternoonPeriodCount
    return when (part) {
        PeriodDayPart.MORNING -> if (morningEnd > 0) 1..morningEnd else IntRange.EMPTY
        PeriodDayPart.AFTERNOON -> if (afternoonPeriodCount > 0) (morningEnd + 1)..afternoonEnd else IntRange.EMPTY
        PeriodDayPart.EVENING -> if (eveningPeriodCount > 0) (afternoonEnd + 1)..totalPeriodCount() else IntRange.EMPTY
    }
}

fun inferPeriodCounts(periods: List<PeriodEntity>): Triple<Int, Int, Int> {
    var morning = 0
    var afternoon = 0
    var evening = 0
    periods.forEach { period ->
        val hour = runCatching { LocalTime.parse(period.startTime).hour }.getOrDefault(8)
        when {
            hour < 12 -> morning++
            hour < 18 -> afternoon++
            else -> evening++
        }
    }
    if (morning + afternoon + evening == 0) morning = periods.size
    return Triple(morning, afternoon, evening)
}

fun resolveSchemeTimes(config: ScheduleConfigEntity, draft: PeriodSchemeDraft): List<PeriodSchemeTimeEntity> {
    if (draft.scheme.mode == PeriodSchemeMode.MANUAL) {
        return draft.times.sortedBy { it.periodIndex }
    }
    val duration = draft.scheme.classDurationMinutes.coerceIn(1, 300).toLong()
    val normalBreak = draft.scheme.breakDurationMinutes.coerceIn(0, 300)
    val existing = draft.times.associateBy { it.periodIndex }
    val result = mutableListOf<PeriodSchemeTimeEntity>()
    PeriodDayPart.entries.forEach { part ->
        val range = config.periodRange(part)
        if (range.isEmpty()) return@forEach
        val startText = when (part) {
            PeriodDayPart.MORNING -> draft.scheme.morningStartTime
            PeriodDayPart.AFTERNOON -> draft.scheme.afternoonStartTime
            PeriodDayPart.EVENING -> draft.scheme.eveningStartTime
        }
        var cursor = runCatching { LocalTime.parse(startText) }.getOrElse {
            when (part) {
                PeriodDayPart.MORNING -> LocalTime.of(8, 0)
                PeriodDayPart.AFTERNOON -> LocalTime.of(14, 0)
                PeriodDayPart.EVENING -> LocalTime.of(19, 0)
            }
        }
        range.forEach { index ->
            val automaticEnd = cursor.plusMinutes(duration)
            val automatic = PeriodSchemeTimeEntity(
                schemeId = draft.scheme.id,
                periodIndex = index,
                startTime = cursor.format(PeriodTimeFormatter),
                endTime = automaticEnd.format(PeriodTimeFormatter)
            )
            result += if (index in draft.overriddenPeriods) existing[index] ?: automatic else automatic
            val gap = draft.specialBreaks[index] ?: normalBreak
            cursor = automaticEnd.plusMinutes(gap.toLong())
        }
    }
    return result
}

fun encodeSpecialBreaks(value: Map<Int, Int>): String =
    value.toSortedMap().entries.joinToString(prefix = "{", postfix = "}") { "\"${it.key}\":${it.value}" }

fun decodeSpecialBreaks(value: String): Map<Int, Int> =
    Regex("\\\"(\\d+)\\\"\\s*:\\s*(\\d+)").findAll(value).associate {
        it.groupValues[1].toInt() to it.groupValues[2].toInt()
    }

fun encodeOverrides(value: Set<Int>): String = value.sorted().joinToString(prefix = "[", postfix = "]")

fun decodeOverrides(value: String): Set<Int> = Regex("\\d+").findAll(value).map { it.value.toInt() }.toSet()

fun validateResolvedPeriodTimes(times: List<PeriodSchemeTimeEntity>): String? {
    val sorted = times.sortedBy { it.periodIndex }
    sorted.forEach { item ->
        val start = runCatching { LocalTime.parse(item.startTime) }.getOrNull()
            ?: return "第 ${item.periodIndex} 节开始时间无效"
        val end = runCatching { LocalTime.parse(item.endTime) }.getOrNull()
            ?: return "第 ${item.periodIndex} 节结束时间无效"
        if (!end.isAfter(start)) return "第 ${item.periodIndex} 节结束时间必须晚于开始时间"
    }
    sorted.zipWithNext().forEach { (left, right) ->
        val leftEnd = LocalTime.parse(left.endTime)
        val rightStart = LocalTime.parse(right.startTime)
        if (rightStart.isBefore(leftEnd)) return "第 ${left.periodIndex} 节与第 ${right.periodIndex} 节时间重叠"
    }
    return null
}
