package com.xiaomanjun.sleepdownschedule.domain.schedule

import com.xiaomanjun.sleepdownschedule.model.*

import androidx.compose.runtime.Immutable
import java.time.LocalTime
import java.time.format.DateTimeFormatter

enum class PeriodDayPart { MORNING, NOON, AFTERNOON, EVENING }

data class PeriodPartCounts(
    val morning: Int,
    val noon: Int,
    val afternoon: Int,
    val evening: Int
)

/**
 * Repartitions an existing, ordered period timeline without adding or deleting a period.
 *
 * Every enabled day part owns at least one period. The first period of each later part is chosen
 * as the feasible period whose real start is closest to that part's configured start. This keeps
 * period indices, courses, manual times and custom course times stable while a day-part switch only
 * changes the semantic boundaries between existing periods.
 */
internal fun allocatePeriodCountsByStartTimes(
    orderedPeriodStartMinutes: List<Int>,
    enabledParts: Set<PeriodDayPart>,
    partStartMinutes: Map<PeriodDayPart, Int>
): PeriodPartCounts? {
    val parts = PeriodDayPart.entries.filter(enabledParts::contains)
    val total = orderedPeriodStartMinutes.size
    if (parts.isEmpty() || total == 0 || parts.size > total) return null

    val starts = orderedPeriodStartMinutes.map { it.coerceIn(0, LastMinuteOfDay) }
    val boundaries = mutableListOf(0)
    parts.drop(1).forEachIndexed { offset, part ->
        val partIndex = offset + 1
        val minimumBoundary = boundaries.last() + 1
        val remainingParts = parts.size - partIndex
        val maximumBoundary = total - remainingParts
        val requestedStart = partStartMinutes[part]
            ?.coerceIn(0, LastMinuteOfDay)
            ?: defaultDayPartStartMinute(part)
        val boundary = (minimumBoundary..maximumBoundary).minWithOrNull(
            compareBy<Int> { kotlin.math.abs(starts[it] - requestedStart) }
                .thenBy { it }
        ) ?: return null
        boundaries += boundary
    }
    boundaries += total

    val counts = parts.mapIndexed { index, part ->
        part to (boundaries[index + 1] - boundaries[index])
    }.toMap()
    return PeriodPartCounts(
        morning = counts[PeriodDayPart.MORNING] ?: 0,
        noon = counts[PeriodDayPart.NOON] ?: 0,
        afternoon = counts[PeriodDayPart.AFTERNOON] ?: 0,
        evening = counts[PeriodDayPart.EVENING] ?: 0
    )
}

private fun defaultDayPartStartMinute(part: PeriodDayPart): Int = when (part) {
    PeriodDayPart.MORNING -> 8 * 60
    PeriodDayPart.NOON -> 12 * 60
    PeriodDayPart.AFTERNOON -> 14 * 60
    PeriodDayPart.EVENING -> 19 * 60
}

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
internal const val LastMinuteOfDay = 23 * 60 + 59

internal data class PeriodTimePickerBounds(
    val minimumStartMinute: Int,
    val maximumEndMinute: Int
)

internal data class PeriodTimeSelection(
    val startMinute: Int,
    val endMinute: Int
)

internal enum class PeriodTimeSelectionAnchor { START, END, NONE }

internal fun parseMinuteOfDay(value: String?): Int? = value
    ?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
    ?.let { it.hour * 60 + it.minute }

internal fun periodTimePickerBounds(previousEnd: String?, nextStart: String?): PeriodTimePickerBounds {
    val minimumStart = (parseMinuteOfDay(previousEnd) ?: 0).coerceIn(0, LastMinuteOfDay - 1)
    val maximumEnd = (parseMinuteOfDay(nextStart) ?: LastMinuteOfDay)
        .coerceIn(minimumStart + 1, LastMinuteOfDay)
    return PeriodTimePickerBounds(minimumStart, maximumEnd)
}

internal fun constrainPeriodTimeSelection(
    startMinute: Int,
    endMinute: Int,
    bounds: PeriodTimePickerBounds,
    anchor: PeriodTimeSelectionAnchor = PeriodTimeSelectionAnchor.NONE
): PeriodTimeSelection {
    val minimumStart = bounds.minimumStartMinute.coerceIn(0, LastMinuteOfDay - 1)
    val maximumEnd = bounds.maximumEndMinute.coerceIn(minimumStart + 1, LastMinuteOfDay)
    return when (anchor) {
        PeriodTimeSelectionAnchor.START -> {
            val safeEnd = endMinute.coerceIn(minimumStart + 1, maximumEnd)
            PeriodTimeSelection(startMinute.coerceIn(minimumStart, safeEnd - 1), safeEnd)
        }
        PeriodTimeSelectionAnchor.END -> {
            val safeStart = startMinute.coerceIn(minimumStart, maximumEnd - 1)
            PeriodTimeSelection(safeStart, endMinute.coerceIn(safeStart + 1, maximumEnd))
        }
        PeriodTimeSelectionAnchor.NONE -> {
            val safeStart = startMinute.coerceIn(minimumStart, maximumEnd - 1)
            PeriodTimeSelection(safeStart, endMinute.coerceIn(safeStart + 1, maximumEnd))
        }
    }
}

internal fun automaticPartSpanMinutes(
    config: ScheduleConfigEntity,
    draft: PeriodSchemeDraft,
    part: PeriodDayPart
): Int {
    val indices = config.periodRange(part).toList()
    if (indices.isEmpty()) return 0
    val lessonMinutes = draft.scheme.classDurationMinutes.coerceIn(1, 300)
    val normalBreak = draft.scheme.breakDurationMinutes.coerceIn(0, 300)
    return indices.sumOf { lessonMinutes } + indices.dropLast(1).sumOf { index ->
        (draft.specialBreaks[index] ?: normalBreak).coerceIn(0, 300)
    }
}

internal fun constrainAutomaticPartStarts(
    config: ScheduleConfigEntity,
    draft: PeriodSchemeDraft,
    requestedStarts: Map<PeriodDayPart, Int>
): Map<PeriodDayPart, Int> {
    val enabledParts = PeriodDayPart.entries.filter { config.periodCount(it) > 0 }
    val spans = enabledParts.associateWith { automaticPartSpanMinutes(config, draft, it) }
    var previousEnd = 0
    return buildMap {
        enabledParts.forEachIndexed { index, part ->
            val remainingSpan = enabledParts.drop(index).sumOf { spans.getValue(it) }
            val maximumStart = (LastMinuteOfDay - remainingSpan)
                .coerceAtLeast(previousEnd)
                .coerceAtMost(LastMinuteOfDay)
            val start = requestedStarts[part].orEmptyMinute().coerceIn(previousEnd.coerceAtMost(maximumStart), maximumStart)
            put(part, start)
            previousEnd = (start + spans.getValue(part)).coerceAtMost(LastMinuteOfDay)
        }
    }
}

private fun Int?.orEmptyMinute(): Int = (this ?: 0).coerceIn(0, LastMinuteOfDay)

fun ScheduleConfigEntity.periodCount(part: PeriodDayPart): Int = when (part) {
    PeriodDayPart.MORNING -> morningPeriodCount
    PeriodDayPart.NOON -> noonPeriodCount
    PeriodDayPart.AFTERNOON -> afternoonPeriodCount
    PeriodDayPart.EVENING -> eveningPeriodCount
}

fun ScheduleConfigEntity.totalPeriodCount(): Int =
    morningPeriodCount + noonPeriodCount + afternoonPeriodCount + eveningPeriodCount

fun ScheduleConfigEntity.hasSamePeriodTopology(other: ScheduleConfigEntity): Boolean =
    morningPeriodCount == other.morningPeriodCount &&
        noonPeriodCount == other.noonPeriodCount &&
        afternoonPeriodCount == other.afternoonPeriodCount &&
        eveningPeriodCount == other.eveningPeriodCount

/**
 * Parameters that are allowed to regenerate an automatic scheme.  The persisted time line is
 * deliberately not treated as a cache: opening an unrelated setting must never rewrite a user's
 * existing summer/winter timetable.
 */
fun PeriodSchemeDraft.hasSameGenerationInputs(other: PeriodSchemeDraft): Boolean {
    if (scheme.mode != other.scheme.mode ||
        scheme.classDurationMinutes != other.scheme.classDurationMinutes ||
        scheme.breakDurationMinutes != other.scheme.breakDurationMinutes ||
        scheme.morningStartTime != other.scheme.morningStartTime ||
        scheme.noonStartTime != other.scheme.noonStartTime ||
        scheme.afternoonStartTime != other.scheme.afternoonStartTime ||
        scheme.eveningStartTime != other.scheme.eveningStartTime ||
        specialBreaks != other.specialBreaks ||
        overriddenPeriods != other.overriddenPeriods
    ) return false

    val mine = times.filter { it.periodIndex in overriddenPeriods }
        .associate { it.periodIndex to (it.startTime to it.endTime) }
    val theirs = other.times.filter { it.periodIndex in other.overriddenPeriods }
        .associate { it.periodIndex to (it.startTime to it.endTime) }
    return mine == theirs
}

fun resolveSchemeTimesForSave(
    config: ScheduleConfigEntity,
    draft: PeriodSchemeDraft,
    storedConfig: ScheduleConfigEntity?,
    storedDraft: PeriodSchemeDraft?
): List<PeriodSchemeTimeEntity> {
    if (draft.scheme.mode == PeriodSchemeMode.MANUAL) return draft.times.sortedBy { it.periodIndex }
    if (storedConfig != null && storedDraft != null &&
        config.hasSamePeriodTopology(storedConfig) && draft.hasSameGenerationInputs(storedDraft)
    ) {
        return storedDraft.times.sortedBy { it.periodIndex }
    }
    return resolveSchemeTimes(config, draft)
}

fun ScheduleConfigEntity.periodRange(part: PeriodDayPart): IntRange {
    val morningEnd = morningPeriodCount
    val noonEnd = morningEnd + noonPeriodCount
    val afternoonEnd = noonEnd + afternoonPeriodCount
    return when (part) {
        PeriodDayPart.MORNING -> if (morningEnd > 0) 1..morningEnd else IntRange.EMPTY
        PeriodDayPart.NOON -> if (noonPeriodCount > 0) (morningEnd + 1)..noonEnd else IntRange.EMPTY
        PeriodDayPart.AFTERNOON -> if (afternoonPeriodCount > 0) (noonEnd + 1)..afternoonEnd else IntRange.EMPTY
        PeriodDayPart.EVENING -> if (eveningPeriodCount > 0) (afternoonEnd + 1)..totalPeriodCount() else IntRange.EMPTY
    }
}

fun inferPeriodCounts(periods: List<PeriodEntity>): PeriodPartCounts {
    var morning = 0
    var noon = 0
    var afternoon = 0
    var evening = 0
    periods.forEach { period ->
        val hour = runCatching { LocalTime.parse(period.startTime).hour }.getOrDefault(8)
        when {
            hour < 12 -> morning++
            hour < 14 -> noon++
            hour < 18 -> afternoon++
            else -> evening++
        }
    }
    if (morning + noon + afternoon + evening == 0) morning = periods.size
    return PeriodPartCounts(morning, noon, afternoon, evening)
}

fun resolveSchemeTimes(config: ScheduleConfigEntity, draft: PeriodSchemeDraft): List<PeriodSchemeTimeEntity> {
    if (draft.scheme.mode == PeriodSchemeMode.MANUAL) {
        return draft.times.sortedBy { it.periodIndex }
    }
    val duration = draft.scheme.classDurationMinutes.coerceIn(1, 300).toLong()
    val normalBreak = draft.scheme.breakDurationMinutes.coerceIn(0, 300)
    val existing = draft.times.associateBy { it.periodIndex }
    val result = mutableListOf<PeriodSchemeTimeEntity>()
    var previousPartEnd: LocalTime? = null
    PeriodDayPart.entries.forEach { part ->
        val range = config.periodRange(part)
        if (range.isEmpty()) return@forEach
        val startText = when (part) {
            PeriodDayPart.MORNING -> draft.scheme.morningStartTime
            PeriodDayPart.NOON -> draft.scheme.noonStartTime
            PeriodDayPart.AFTERNOON -> draft.scheme.afternoonStartTime
            PeriodDayPart.EVENING -> draft.scheme.eveningStartTime
        }
        var cursor = runCatching { LocalTime.parse(startText) }.getOrElse {
            when (part) {
                PeriodDayPart.MORNING -> LocalTime.of(8, 0)
                PeriodDayPart.NOON -> LocalTime.of(12, 0)
                PeriodDayPart.AFTERNOON -> LocalTime.of(14, 0)
                PeriodDayPart.EVENING -> LocalTime.of(19, 0)
            }
        }
        // Each day part has an independently configurable start, but the resulting
        // timetable is still one continuous timeline.  Old drafts (and a picker that
        // is being edited quickly) can contain an earlier start for a later part.  Do
        // not let that transient/stored value produce duplicate periods on screen.
        previousPartEnd?.let { end ->
            if (cursor.isBefore(end)) cursor = end
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
        previousPartEnd = result.lastOrNull()?.endTime?.let {
            runCatching { LocalTime.parse(it) }.getOrNull()
        } ?: previousPartEnd
    }
    return result
}

/**
 * Makes the configured day-part starts agree with the effective, non-overlapping
 * auto-generated timeline.  This is used when the start-time picker is confirmed so
 * the summary never keeps showing an obsolete value that was pushed forward.
 */
fun normalizeAutoSchemeStarts(
    config: ScheduleConfigEntity,
    draft: PeriodSchemeDraft
): PeriodSchemeDraft {
    if (draft.scheme.mode != PeriodSchemeMode.AUTO_MATCH) return draft
    val resolved = resolveSchemeTimes(config, draft)
    val byIndex = resolved.associateBy { it.periodIndex }
    fun firstStart(part: PeriodDayPart, fallback: String): String =
        config.periodRange(part).firstOrNull()?.let { byIndex[it]?.startTime } ?: fallback
    val normalizedScheme = draft.scheme.copy(
        morningStartTime = firstStart(PeriodDayPart.MORNING, draft.scheme.morningStartTime),
        noonStartTime = firstStart(PeriodDayPart.NOON, draft.scheme.noonStartTime),
        afternoonStartTime = firstStart(PeriodDayPart.AFTERNOON, draft.scheme.afternoonStartTime),
        eveningStartTime = firstStart(PeriodDayPart.EVENING, draft.scheme.eveningStartTime)
    )
    val normalized = draft.copy(scheme = normalizedScheme)
    return normalized.copy(times = resolveSchemeTimes(config, normalized))
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
