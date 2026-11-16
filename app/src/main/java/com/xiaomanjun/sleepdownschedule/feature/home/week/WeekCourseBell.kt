package com.xiaomanjun.sleepdownschedule.feature.home.week

import com.xiaomanjun.sleepdownschedule.model.*
import com.xiaomanjun.sleepdownschedule.domain.schedule.*
import com.xiaomanjun.sleepdownschedule.domain.schedule.CoursePeriodTime
import java.time.LocalDate
import java.time.LocalTime

internal data class WeekDayTimeline(
    val periods: List<PeriodEntity>,
    val activePeriodIndex: Int?,
    val exceptionalActivePeriod: Boolean,
    val activeCourseId: Long? = null,
)

private data class RailCourse(
    val id: Long, val indices: List<Int>, val times: List<CoursePeriodTime>,
    val replacements: Map<Int, PeriodEntity>
) {
    val start: LocalTime get() = times.first().start
    val end: LocalTime get() = times.last().end
    val exceptional: Boolean get() = replacements.isNotEmpty()
    fun active(now: LocalTime) = times.firstOrNull { !now.isBefore(it.start) && now.isBefore(it.end) }
    fun preClassStart(leadMinutes: Long): LocalTime =
        if (start.toSecondOfDay() < leadMinutes * 60L) LocalTime.MIN else start.minusMinutes(leadMinutes)
}

/** Today's rail presents actual clocks during an active exception window without mutating stored bells. */
internal fun weekDayTimeline(state: AppState, date: LocalDate, now: LocalTime): WeekDayTimeline {
    val scheduled = resolveTimetable(state.config, state.periods, state.timetableCatalog, date).periods
    val byIndex = scheduled.associateBy { it.periodIndex }
    val ordinary = coursesForDate(state, date)
    val teachingDate = teachingDateForSchedule(state.config, date)
    val week = teachingDate?.let {
        if (it != date) adjustedTeachingWeekForDate(state.config, it) else scheduleWeekForDateOrNull(state.config, it)
    }.takeIf { scheduleWeekForDateOrNull(state.config, date) != null }
    val weekBaseline = week?.let {
        resolveWeekTimetable(state.config, state.periods, state.timetableCatalog, it, date).periods
    }?.takeIf { it.isNotEmpty() } ?: state.periods
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
    val ordinaryCourses = ordinary.mapNotNull { course ->
        val source = course.arrangementForWrite()
        val times = resolveCourseOccurrence(source, state.config, state.periods, state.timetableCatalog, date).segments
        source to times
    }
    val specialCourses = special.map { course ->
        course to courseTimeSegments(course, scheduled)
    }
    val courses = (ordinaryCourses + specialCourses).mapNotNull { (source, times) ->
        val indices = source.periods.filter { it in byIndex }.ifEmpty {
            source.displayPeriodIndices().filter { it in byIndex }
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
    }
    val leadMinutes = state.config.notificationLeadMinutes.coerceAtLeast(0).toLong()
    val runningCourse = courses.filter { it.active(now) != null }
        .minWithOrNull(compareBy<RailCourse> { !it.exceptional }.thenBy { it.start }.thenBy { it.id })
    val preClassCourse = if (runningCourse != null) null else courses
        .filter { it.exceptional && !now.isBefore(it.preClassStart(leadMinutes)) && now.isBefore(it.start) }
        .minWithOrNull(compareBy<RailCourse> { it.start }.thenBy { it.id })
    val activeWindowCourse = runningCourse ?: preClassCourse
    val selected = linkedMapOf<Int, PeriodEntity>()
    if (activeWindowCourse != null && activeWindowCourse.exceptional) {
        activeWindowCourse.replacements.forEach { (index, period) -> selected.putIfAbsent(index, period) }
    }
    courses.filter {
        it.exceptional && (it.active(now) != null || (runningCourse == null && !now.isBefore(it.start) && now.isBefore(it.end)))
    }.sortedWith(compareBy<RailCourse> { it.start }.thenBy { it.id }).forEach { course ->
        course.replacements.forEach { (index, period) -> selected.putIfAbsent(index, period) }
    }
    val periods = scheduled.map { selected[it.periodIndex] ?: it }
    val baseline = weekBaseline.associateBy { it.periodIndex }
    val changed = periods.filter { period -> baseline[period.periodIndex]?.let {
        it.startTime != period.startTime || it.endTime != period.endTime
    } == true }.mapTo(hashSetOf()) { it.periodIndex }
    val active = when {
        runningCourse != null -> {
            val clock = checkNotNull(runningCourse.active(now))
            if (runningCourse.times.size == 1 && runningCourse.indices.size > 1) {
                currentTimelinePeriod(periods.filter { it.periodIndex in runningCourse.indices }, now)?.periodIndex
                    ?: runningCourse.indices.first()
            } else clock.index.takeIf { it in runningCourse.indices } ?: runningCourse.indices.first()
        }
        preClassCourse != null -> {
            preClassCourse.replacements.keys.firstOrNull { it in preClassCourse.indices } ?: preClassCourse.indices.first()
        }
        else -> {
            currentTimelinePeriod(periods, now)?.takeUnless { period ->
                period.periodIndex in changed && (now.isBefore(LocalTime.parse(period.startTime)) || !now.isBefore(LocalTime.parse(period.endTime)))
            }?.periodIndex
        }
    }
    val exceptionalActive = active != null && (active in changed || preClassCourse != null)
    return WeekDayTimeline(periods, active, exceptionalActive, activeWindowCourse?.id)
}
