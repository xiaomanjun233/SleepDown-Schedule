package com.xiaomanjun.sleepdownschedule.feature.course.editor

import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Immutable
import com.xiaomanjun.sleepdownschedule.CourseEntity
import com.xiaomanjun.sleepdownschedule.PeriodEntity
import com.xiaomanjun.sleepdownschedule.ScheduleConfigEntity
import com.xiaomanjun.sleepdownschedule.WeekParity
import com.xiaomanjun.sleepdownschedule.domain.schedule.originalArrangement
import com.xiaomanjun.sleepdownschedule.domain.schedule.parseCoursePeriodTimes
import com.xiaomanjun.sleepdownschedule.feature.home.day.courseWeeksChanged
import com.xiaomanjun.sleepdownschedule.parityMatches
import com.xiaomanjun.sleepdownschedule.scheduleWeekStartDate
import com.xiaomanjun.sleepdownschedule.weekdayLabel
import kotlinx.coroutines.flow.map

@Immutable
data class CourseEditorFormData(
    val config: ScheduleConfigEntity,
    val periods: List<PeriodEntity>,
    val courses: List<CourseEntity> = emptyList()
)

internal data class CourseEditorDraft(
    val name: String,
    val teacher: String,
    val location: String,
    val weekdays: Set<Int>,
    val periodStart: Int,
    val periodEnd: Int,
    val customStartTime: String?,
    val customEndTime: String?,
    val customColorArgb: Long?,
    val weeks: Set<Int>,
    val parity: WeekParity,
    val note: String,
    val customPeriodTimes: String? = null,
    val customLessonTimesEdited: Boolean = false
)

internal enum class CourseWeekSelectionMode {
    EVERY,
    ODD,
    EVEN,
    CUSTOM
}

internal fun inferCourseWeekSelectionMode(weeks: Collection<Int>): CourseWeekSelectionMode {
    val sorted = weeks.filter { it > 0 }.distinct().sorted()
    if (sorted.isEmpty()) return CourseWeekSelectionMode.CUSTOM
    if (sorted.zipWithNext().all { (first, second) -> second == first + 1 }) {
        return CourseWeekSelectionMode.EVERY
    }
    val range = sorted.first()..sorted.last()
    if (sorted == range.filter { it % 2 == 1 }) return CourseWeekSelectionMode.ODD
    if (sorted == range.filter { it % 2 == 0 }) return CourseWeekSelectionMode.EVEN
    return CourseWeekSelectionMode.CUSTOM
}

internal fun weeksForCourseWeekSelectionMode(
    mode: CourseWeekSelectionMode,
    currentWeeks: Collection<Int>,
    totalWeeks: Int
): Set<Int> {
    val bounded = currentWeeks.filter { it in 1..totalWeeks }.distinct().sorted()
    val start = bounded.firstOrNull() ?: 1
    val end = bounded.lastOrNull() ?: totalWeeks.coerceAtLeast(1)
    val range = start..end
    return when (mode) {
        CourseWeekSelectionMode.EVERY -> range.toSet()
        CourseWeekSelectionMode.ODD -> range.filterTo(linkedSetOf()) { it % 2 == 1 }
        CourseWeekSelectionMode.EVEN -> range.filterTo(linkedSetOf()) { it % 2 == 0 }
        CourseWeekSelectionMode.CUSTOM -> bounded.toSet()
    }
}

internal fun CourseWeekSelectionMode.toWeekParity(): WeekParity = when (this) {
    CourseWeekSelectionMode.ODD -> WeekParity.ODD
    CourseWeekSelectionMode.EVEN -> WeekParity.EVEN
    CourseWeekSelectionMode.EVERY,
    CourseWeekSelectionMode.CUSTOM -> WeekParity.ALL
}

internal fun courseWeekSelectionModeLabel(mode: CourseWeekSelectionMode): String = when (mode) {
    CourseWeekSelectionMode.EVERY -> "每周"
    CourseWeekSelectionMode.ODD -> "单周"
    CourseWeekSelectionMode.EVEN -> "双周"
    CourseWeekSelectionMode.CUSTOM -> "自定义"
}

@Immutable
internal data class CourseEditorGroup(val courses: List<CourseEntity>) {
    val representative: CourseEntity? get() = courses.firstOrNull()
}

internal data class CourseEditorGroupingKey(
    val scheduleId: Int,
    val name: String,
    val teacher: String,
    val location: String,
    val weekday: Int,
    // Only weekly fragments on the same weekday share an editor page.
    val periods: List<Int>,
    val customStartTime: String?,
    val customEndTime: String?,
    val customPeriodTimes: String?,
    val originalPeriodTimes: String?,
    val customColorArgb: Long?,
    val parity: WeekParity,
    val note: String
)

private fun CourseEntity.editorGroupingKey() = CourseEditorGroupingKey(
    scheduleId = scheduleId,
    name = name.trim(),
    teacher = teacher.orEmpty().trim(),
    location = location.orEmpty().trim(),
    weekday = weekday,
    periods = periods.distinct().sorted(),
    customStartTime = customStartTime,
    customEndTime = customEndTime,
    customPeriodTimes = customPeriodTimes,
    originalPeriodTimes = originalPeriodTimes,
    customColorArgb = customColorArgb,
    parity = weekParity,
    note = note.orEmpty().trim()
)

internal fun buildCourseEditorGroups(
    initialCourse: CourseEntity?,
    courses: List<CourseEntity>
): List<CourseEditorGroup> {
    if (initialCourse == null) return listOf(CourseEditorGroup(emptyList()))
    val related = (courses + initialCourse).map { it.originalArrangement() }
        .filter {
            it.scheduleId == initialCourse.scheduleId &&
                it.name.trim() == initialCourse.name.trim()
        }
        .distinctBy { it.id }
    return related
        .groupBy(CourseEntity::editorGroupingKey)
        .values
        .map { group ->
            CourseEditorGroup(
                group.sortedWith(
                    compareBy<CourseEntity> { if (it.id == initialCourse.id) 0 else 1 }
                        .thenBy { it.weeks.minOrNull() ?: Int.MAX_VALUE }
                        .thenBy { it.id }
                )
            )
        }
        .sortedWith(
            compareBy<CourseEditorGroup> { it.courses.minOfOrNull(CourseEntity::weekday) ?: 1 }
                .thenBy { it.representative?.periods?.minOrNull() ?: 0 }
                .thenBy { it.representative?.location.orEmpty() }
        )
}

internal data class CourseApplyAllScope(
    val originals: List<CourseEntity>,
    val edited: CourseEntity
)

internal data class CourseEditorEditScope(
    val originals: List<CourseEntity>,
    val edited: List<CourseEntity>
)

internal fun courseEditorApplyAllScope(
    originals: List<CourseEntity>,
    edited: List<CourseEntity>,
    courses: List<CourseEntity>
): CourseEditorEditScope {
    val original = originals.singleOrNull()
    val replacement = edited.singleOrNull()
    if (original != null && replacement != null && !courseWeeksChanged(original, replacement)) {
        val scope = courseApplyAllScope(original, replacement, courses)
        return CourseEditorEditScope(scope.originals, listOf(scope.edited))
    }
    return CourseEditorEditScope(originals, edited)
}

internal fun courseEditorHasOccurrence(course: CourseEntity, week: Int): Boolean =
    week > 0 && week in course.weeks && parityMatches(course.weekParity, week)

/** A single selected destination week moves this occurrence; a normal edit keeps its source week. */
internal fun courseEditorSingleDestinationWeek(original: CourseEntity, edited: CourseEntity, sourceWeek: Int): Int? =
    if (!courseWeeksChanged(original, edited)) sourceWeek
    else edited.weeks.filter { parityMatches(edited.weekParity, it) }.distinct().singleOrNull()

internal fun courseEditorOccurrenceLabel(
    course: CourseEntity,
    week: Int,
    config: ScheduleConfigEntity
): String = "第${week}周 · 周${weekdayLabel(course.weekday)} · " +
    scheduleWeekStartDate(config, week).plusDays((course.weekday - 1).toLong())

internal fun courseEditorScopeDescription(
    courses: List<CourseEntity>,
    config: ScheduleConfigEntity
): String = courses.groupBy(CourseEntity::weekday).toSortedMap().entries.joinToString("\n") { (weekday, entries) ->
    val weeks = entries.flatMap { course ->
        course.weeks.filter { courseEditorHasOccurrence(course, it) }
    }.distinct().sorted()
    val dates = weeks.joinToString("、") { week ->
        scheduleWeekStartDate(config, week).plusDays((weekday - 1).toLong()).toString()
    }
    "周${weekdayLabel(weekday)} · ${compactWeekSelectionLabel(weeks)}" +
        if (dates.isEmpty()) "（没有有效排课）" else "\n$dates"
}

/** Apply-all includes weekly fragments whose only differing course detail is the exact clock range. */
internal fun courseApplyAllScope(
    original: CourseEntity,
    edited: CourseEntity,
    courses: List<CourseEntity>
): CourseApplyAllScope {
    val changingClock = original.customStartTime != edited.customStartTime ||
        original.customEndTime != edited.customEndTime ||
        original.customPeriodTimes != edited.customPeriodTimes
    val candidates = (courses + original).filter { candidate ->
        candidate.scheduleId == original.scheduleId &&
            candidate.name.trim() == original.name.trim() &&
            candidate.teacher.orEmpty().trim() == original.teacher.orEmpty().trim() &&
            candidate.location.orEmpty().trim() == original.location.orEmpty().trim() &&
            candidate.weekday == original.weekday &&
            candidate.periods.distinct().sorted() == original.periods.distinct().sorted() &&
            candidate.weekParity == original.weekParity &&
            candidate.note.orEmpty().trim() == original.note.orEmpty().trim() &&
            candidate.customColorArgb == original.customColorArgb &&
            (changingClock || (
                candidate.customStartTime == original.customStartTime &&
                    candidate.customEndTime == original.customEndTime &&
                    candidate.customPeriodTimes == original.customPeriodTimes
            ))
    }.distinctBy(CourseEntity::id)
    val originals = buildList {
        add(original)
        val coveredWeeks = original.weeks.toMutableSet()
        candidates.sortedBy(CourseEntity::id).forEach { candidate ->
            if (candidate.id != original.id && candidate.weeks.none(coveredWeeks::contains)) {
                add(candidate)
                coveredWeeks.addAll(candidate.weeks)
            }
        }
    }
    return CourseApplyAllScope(
        originals = originals,
        edited = edited.copy(weeks = originals.flatMap(CourseEntity::weeks).distinct().sorted())
    )
}

internal fun excludedWeeksInsideCourseRange(course: CourseEntity?): Set<Int> {
    val weeks = course?.weeks?.distinct()?.sorted().orEmpty()
    if (weeks.size < 2) return emptySet()
    return (weeks.first()..weeks.last()).filterNot(weeks.toHashSet()::contains).toSet()
}

internal fun weeksInEditorRange(
    start: Int,
    end: Int,
    excludedWeeks: Set<Int>
): List<Int> {
    if (start > end) return emptyList()
    return (start..end).filterNot(excludedWeeks::contains)
}

internal fun compactWeekSelectionLabel(weeks: List<Int>): String {
    val sorted = weeks.distinct().sorted()
    if (sorted.isEmpty()) return "未选择"
    val ranges = buildList {
        var start = sorted.first()
        var previous = start
        sorted.drop(1).forEach { week ->
            if (week != previous + 1) {
                add(if (start == previous) "$start" else "$start–$previous")
                start = week
            }
            previous = week
        }
        add(if (start == previous) "$start" else "$start–$previous")
    }
    return "第${ranges.joinToString("、")}周"
}

internal fun compactWeekdaySelectionLabel(weekdays: Collection<Int>): String {
    val labels = weekdays.filter { it in 1..7 }.distinct().sorted().map(::weekdayLabel)
    return if (labels.isEmpty()) "未选择" else "周${labels.joinToString("、")}"
}

internal fun courseEditorDraft(
    courses: List<CourseEntity>,
    periodValues: List<Int>,
    totalWeeks: Int
) : CourseEditorDraft {
    val course = courses.firstOrNull()
    // Existing records retain their weeks even after the term is shortened.
    // A whole-term default belongs only to a genuinely new course.
    val storedWeeks = if (courses.isEmpty()) (1..totalWeeks.coerceAtLeast(1)).toSet()
        else courses.flatMap(CourseEntity::weeks).filter { it > 0 }.toSet()
    val activeWeeks = when (course?.weekParity ?: WeekParity.ALL) {
        WeekParity.ALL -> storedWeeks
        WeekParity.ODD -> storedWeeks.filterTo(linkedSetOf()) { it % 2 == 1 }
        WeekParity.EVEN -> storedWeeks.filterTo(linkedSetOf()) { it % 2 == 0 }
    }
    val selectionMode = inferCourseWeekSelectionMode(activeWeeks)
    return CourseEditorDraft(
        name = course?.name.orEmpty(),
        teacher = course?.teacher.orEmpty(),
        location = course?.location.orEmpty(),
        weekdays = courses.map(CourseEntity::weekday).filter { it in 1..7 }.toSet().ifEmpty { setOf(1) },
        periodStart = course?.periods?.minOrNull() ?: (periodValues.firstOrNull() ?: 1),
        periodEnd = course?.periods?.maxOrNull() ?: (periodValues.firstOrNull() ?: 1),
        customStartTime = course?.customStartTime,
        customEndTime = course?.customEndTime,
        customPeriodTimes = course?.customPeriodTimes,
        customColorArgb = course?.customColorArgb,
        weeks = activeWeeks,
        parity = selectionMode.toWeekParity(),
        note = course?.note.orEmpty()
    )
}

internal fun courseEditorCopyDraft(
    source: CourseEntity,
    periodValues: List<Int>,
    totalWeeks: Int
): CourseEditorDraft = courseEditorDraft(listOf(source), periodValues, totalWeeks)
    .copy(weekdays = emptySet(), periodStart = 0, periodEnd = 0,
        customStartTime = null, customEndTime = null, customPeriodTimes = null)

internal fun courseEditorOriginalForWeekday(
    originals: List<CourseEntity>,
    weekday: Int,
    selectedWeekdayCount: Int
): CourseEntity? = originals
    .filter { it.weekday == weekday }
    .minByOrNull(CourseEntity::id)
    ?: originals.singleOrNull().takeIf { selectedWeekdayCount == 1 }

internal fun CourseEditorDraft.toCourses(
    originals: List<CourseEntity>,
    periodValues: List<Int>,
    allowCustomColorOverride: Boolean
): List<CourseEntity> {
    val originalWeekdays = originals.map(CourseEntity::weekday).toSet()
    val originalWeeks = originals.flatMap { original ->
        original.weeks.filter { parityMatches(original.weekParity, it) }
    }.toSet()
    val keepOriginalDistribution = weekdays == originalWeekdays && weeks == originalWeeks
    val originalsByWeekday = originals.groupBy(CourseEntity::weekday)
    val selectedPeriods = if (customLessonTimesEdited) parseCoursePeriodTimes(customPeriodTimes).map { it.index }
        else periodValues.filter { it in periodStart..periodEnd }
    return weekdays.sorted().mapNotNull { weekday ->
        val weekdayOriginals = originalsByWeekday[weekday].orEmpty()
        val original = courseEditorOriginalForWeekday(originals, weekday, weekdays.size)
        val periods = original?.periods?.takeIf {
            !customLessonTimesEdited &&
            it.minOrNull() == periodStart && it.maxOrNull() == periodEnd
        } ?: selectedPeriods
        val timingUnchanged = original != null && original.periods == periods &&
            original.customStartTime == customStartTime && original.customEndTime == customEndTime &&
            original.customPeriodTimes == customPeriodTimes
        val targetWeeks = if (keepOriginalDistribution && originals.isNotEmpty()) {
            weekdayOriginals.flatMap(CourseEntity::weeks).distinct().sorted()
        } else {
            weeks.distinct().sorted()
        }
        if (targetWeeks.isEmpty()) return@mapNotNull null
        CourseEntity(
            id = original?.id ?: 0,
            name = name.trim(),
            teacher = teacher.trim().ifBlank { null },
            location = location.trim().ifBlank { null },
            weekday = weekday,
            periods = periods,
            weeks = targetWeeks,
            weekParity = if (keepOriginalDistribution) original?.weekParity ?: parity else parity,
            note = note.trim().ifBlank { null },
            customStartTime = customStartTime,
            customEndTime = customEndTime,
            originalPeriodTimes = original?.originalPeriodTimes?.takeIf { timingUnchanged },
            customPeriodTimes = customPeriodTimes?.takeIf {
                customLessonTimesEdited || (original?.periods == periods && original.customStartTime == customStartTime &&
                    original.customEndTime == customEndTime)
            },
            customColorArgb = if (allowCustomColorOverride) {
                customColorArgb
            } else {
                original?.customColorArgb ?: originals.firstOrNull()?.customColorArgb
            },
            scheduleId = original?.scheduleId ?: originals.firstOrNull()?.scheduleId ?: 0
        )
    }
}
