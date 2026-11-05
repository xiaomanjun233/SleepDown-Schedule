package com.xiaomanjun.sleepdownschedule.feature.home.week

import com.xiaomanjun.sleepdownschedule.model.*
import com.xiaomanjun.sleepdownschedule.domain.schedule.*
import com.xiaomanjun.sleepdownschedule.domain.schedule.CoursePeriodTime
import java.time.LocalDate
import java.time.LocalTime

internal data class WeekDayTimeline(
    val periods: List<PeriodEntity>,
    val activePeriodIndex: Int?,
    val exceptionalActivePeriod: Boolean
)

private data class RailCourse(
    val id: Long, val indices: List<Int>, val times: List<CoursePeriodTime>,
    val replacements: Map<Int, PeriodEntity>
) {
    fun active(now: LocalTime) = times.firstOrNull { !now.isBefore(it.start) && now.isBefore(it.end) }
}

/** Today's rail presents actual clocks without changing stored bells or adding grid rows. */
internal fun weekDayTimeline(state: AppState, date: LocalDate, now: LocalTime): WeekDayTimeline {
    val scheduled = resolveTimetable(state.config, state.periods, state.timetableCatalog, date).periods
    val byIndex = scheduled.associateBy { it.periodIndex }
    val ordinary = coursesForDate(state, date)
    val teachingDate = teachingDateForSchedule(state.config, date)
    val week = teachingDate?.let {
        if (it != date) adjustedTeachingWeekForDate(state.config, it) else scheduleWeekForDateOrNull(state.config, it)
    }.takeIf { scheduleWeekForDateOrNull(state.config, date) != null }
    val occupied = ordinary.flatMap { it.arrangementForWrite().periods }.toSet()
    // Grid special lessons share the numbered rail; independent bands own their own time labels.
    // Keep ordinary courses authoritative wherever their cells hide a special lesson.
    val special = if (week == null || teachingDate == null) emptyList() else
        runCatching { resolvedTimeSlotCourses(state.config.timeSlotCoursesJson, scheduled) }.getOrDefault(emptyList()).mapIndexedNotNull { index, slot ->
            if (!slot.displayMode.occupiesGrid() || slot.customPeriodTimes.isNullOrBlank() ||
                teachingDate.dayOfWeek.value !in slot.weekdays ||
                week < slot.startWeek || (slot.endWeek != null && week > slot.endWeek)) return@mapIndexedNotNull null
            val indices = slot.periodIndices.filter { it !in occupied }
            if (indices.isEmpty()) return@mapIndexedNotNull null
            val clocks = parseCoursePeriodTimes(slot.customPeriodTimes).filter { it.index in indices }
            CourseEntity(id = -index.toLong() - 1, name = slot.name, teacher = null, location = slot.location,
                weekday = teachingDate.dayOfWeek.value, periods = indices, weeks = listOf(week),
                weekParity = WeekParity.ALL, note = slot.note,
                customStartTime = if (clocks.isEmpty()) null else clocks.first().start.toString(),
                customEndTime = if (clocks.isEmpty()) null else clocks.last().end.toString(),
                customPeriodTimes = clocks.takeIf { it.isNotEmpty() }?.joinToString(";") { "${it.index},${it.start}-${it.end}" })
        }
    val courses = (ordinary + special).mapNotNull { course ->
        val source = course.arrangementForWrite()
        val times = courseTimeSegments(course, state.periods)
        val indices = source.periods.filter { it in byIndex }.ifEmpty {
            course.displayPeriodIndices().filter { it in byIndex }
        }.distinct().sorted()
        if (times.isEmpty() || indices.isEmpty()) return@mapNotNull null
        val replacements = linkedMapOf<Int, PeriodEntity>()
        if (times.size == 1 && indices.size > 1) {
            // An overall range has no internal bells: only its known first/last edges change.
            val first = byIndex.getValue(indices.first())
            val last = byIndex.getValue(indices.last())
            replacements[first.periodIndex] = first.copy(startTime = times.single().start.toString())
            replacements[last.periodIndex] = last.copy(endTime = times.single().end.toString())
        } else times.forEach { clock ->
            val index = clock.index.takeIf { it in indices } ?: indices.singleOrNull() ?: return@forEach
            replacements[index] = byIndex.getValue(index).copy(startTime = clock.start.toString(), endTime = clock.end.toString())
        }
        RailCourse(source.id, indices, times, replacements.filter { (index, period) -> period != byIndex[index] })
    }.sortedWith(compareBy<RailCourse> { it.active(now) == null }
        .thenBy { it.replacements.isEmpty() }.thenBy { it.times.first().start }.thenBy { it.id })
    val selected = linkedMapOf<Int, PeriodEntity>()
    courses.forEach { course -> course.replacements.forEach { (index, period) -> selected.putIfAbsent(index, period) } }
    val periods = scheduled.map { selected[it.periodIndex] ?: it }
    val baseline = state.periods.associateBy { it.periodIndex }
    val changed = periods.filter { period -> baseline[period.periodIndex]?.let {
        it.startTime != period.startTime || it.endTime != period.endTime
    } == true }.mapTo(hashSetOf()) { it.periodIndex }
    val activeCourse = courses.firstOrNull { it.active(now) != null }
    val active = if (activeCourse != null) {
        val clock = checkNotNull(activeCourse.active(now))
        if (activeCourse.times.size == 1 && activeCourse.indices.size > 1) {
            currentTimelinePeriod(periods.filter { it.periodIndex in activeCourse.indices }, now)?.periodIndex
                ?: activeCourse.indices.first()
        } else clock.index.takeIf { it in activeCourse.indices } ?: activeCourse.indices.first()
    } else {
        currentTimelinePeriod(periods, now)?.takeUnless { period ->
            period.periodIndex in changed && (now.isBefore(LocalTime.parse(period.startTime)) || !now.isBefore(LocalTime.parse(period.endTime)))
        }?.periodIndex
    }
    return WeekDayTimeline(periods, active, active != null && active in changed)
}
