package com.xiaomanjun.sleepdownschedule.domain.schedule

import com.xiaomanjun.sleepdownschedule.domain.course.conflictsWith
import com.xiaomanjun.sleepdownschedule.model.CourseEntity
import com.xiaomanjun.sleepdownschedule.model.AppState
import com.xiaomanjun.sleepdownschedule.model.PeriodAlignmentMode
import com.xiaomanjun.sleepdownschedule.model.PeriodEntity
import com.xiaomanjun.sleepdownschedule.model.ScheduleConfigEntity
import java.time.LocalTime

// Source-compatible names; the immutable values belong to model, independently of these rules.
typealias AlignedCourseTime = com.xiaomanjun.sleepdownschedule.model.AlignedCourseTime
typealias EffectiveCourseArrangement = com.xiaomanjun.sleepdownschedule.model.EffectiveCourseArrangement

/** Metadata edits retain the full source even when the caller came from a grid fragment. */
fun CourseEntity.originalArrangement(): CourseEntity {
    val source = arrangementProjection?.source ?: return this
    return copy(periods = source.periods, customStartTime = source.customStartTime,
        customEndTime = source.customEndTime, customPeriodTimes = source.customPeriodTimes,
        originalPeriodTimes = source.originalPeriodTimes, arrangementProjection = null)
}

fun CourseEntity.arrangementForWrite(): CourseEntity =
    if (arrangementProjection?.matches(this) == true) originalArrangement()
    else if (arrangementProjection != null) copy(arrangementProjection = null, originalPeriodTimes = null)
    else this

fun encodeCoursePeriodTimes(times: List<CoursePeriodTime>): String? = times.takeIf { it.isNotEmpty() }
    ?.sortedBy { it.index }?.joinToString(";") { "${it.index},${it.start}-${it.end}" }

/** Full editor information uses the saved source clock and exact overrides, never a new scheme. */
fun CourseEntity.originalTimeSegments(): List<CoursePeriodTime> {
    val raw = originalArrangement()
    val definitions = parseCoursePeriodTimes(raw.originalPeriodTimes).map {
        PeriodEntity(it.index, it.start.toString(), it.end.toString(), raw.scheduleId)
    }
    return courseTimeSegments(raw, definitions)
}

/** Captured at import/explicit timetable edit, never while switching references. */
fun captureOriginalPeriodTimes(course: CourseEntity, periods: List<PeriodEntity>,
    previous: CourseEntity? = null): CourseEntity {
    val raw = course.arrangementForWrite()
    val previousClock = previous?.let { normalizeCourseClock(it.customStartTime, it.customEndTime, it.customPeriodTimes, it.periods) }
    val nextClock = normalizeCourseClock(raw.customStartTime, raw.customEndTime, raw.customPeriodTimes, raw.periods)
    if (previous != null && raw.periods.distinct().sorted() == previous.periods.distinct().sorted() &&
        nextClock == previousClock && previous.originalPeriodTimes != null) {
        return raw.copy(originalPeriodTimes = previous.originalPeriodTimes)
    }
    if (previous == null && raw.originalPeriodTimes != null) {
        val snapshot = parseCoursePeriodTimes(raw.originalPeriodTimes)
        require(snapshot.all { it.index in raw.periods }) { "原始时间快照包含其他节次" }
        return raw
    }
    val old = previous?.let { parseCoursePeriodTimes(it.originalPeriodTimes) }.orEmpty().associateBy { it.index }
    val definitions = periods.associateBy { it.periodIndex }
    val snapshot = raw.periods.distinct().sorted().mapNotNull { index ->
        definitions[index]?.let {
            CoursePeriodTime(index, LocalTime.parse(it.startTime), LocalTime.parse(it.endTime))
        } ?: old[index]?.takeIf { index in previous!!.periods }
    }
    return raw.copy(originalPeriodTimes = encodeCoursePeriodTimes(snapshot) ?: "")
}

fun projectCourseArrangement(course: CourseEntity, config: ScheduleConfigEntity,
    targetPeriods: List<PeriodEntity>, catalog: com.xiaomanjun.sleepdownschedule.model.TimetableCatalog = com.xiaomanjun.sleepdownschedule.model.TimetableCatalog(),
    date: java.time.LocalDate = java.time.LocalDate.now()): CourseEntity {
    val occurrence = resolveCourseOccurrence(course, config, targetPeriods, catalog, date)
    return occurrence.source.copy(arrangementProjection = projectWeekGrid(occurrence, targetPeriods))
}

/** Match complete consecutive lessons only; partial overlaps and ambiguous grids stay exact. */
fun matchingPeriodIndicesForTimeRange(start: LocalTime, end: LocalTime,
    periods: List<PeriodEntity>): List<Int> = matchingPeriodSpan(start, end,
    periods.sortedBy { it.periodIndex }.map {
        CoursePeriodTime(it.periodIndex, LocalTime.parse(it.startTime), LocalTime.parse(it.endTime))
    }.filter { it.end > it.start }).map { it.index }

/** A legacy per-lesson clock is ordinary only when every lesson exactly fits the current grid. */
fun matchingPeriodIndicesForCourseClock(start: String?, end: String?, clock: String?,
    selectedIndices: List<Int>, periods: List<PeriodEntity>): List<Int> {
    val lessons = parseCoursePeriodTimes(clock)
    if (!clock.isNullOrBlank() && lessons.isEmpty()) return emptyList()
    if (lessons.isNotEmpty()) {
        if (lessons.map { it.index }.sorted() != selectedIndices.distinct().sorted()) return emptyList()
        val mapped = lessons.sortedBy { it.start }.map { lesson ->
            matchingPeriodIndicesForTimeRange(lesson.start, lesson.end, periods)
                .takeIf { it.isNotEmpty() } ?: return emptyList()
        }.flatten()
        val order = periods.sortedBy { it.periodIndex }.map { it.periodIndex }
        val positions = mapped.map(order::indexOf)
        if (positions.distinct().size != positions.size ||
            positions.zipWithNext().any { (a, b) -> b != a + 1 }) return emptyList()
        return mapped
    }
    val from = start?.let { runCatching { LocalTime.parse(it) }.getOrNull() } ?: return emptyList()
    val to = end?.let { runCatching { LocalTime.parse(it) }.getOrNull() } ?: return emptyList()
    return matchingPeriodIndicesForTimeRange(from, to, periods)
}

private fun matchingPeriodSpan(start: LocalTime, end: LocalTime,
    targets: List<CoursePeriodTime>): List<CoursePeriodTime> {
    val touched = targets.withIndex().filter { it.value.start < end && it.value.end > start }
    if (touched.isEmpty() || touched.first().value.start != start || touched.last().value.end != end)
        return emptyList()
    if (touched.zipWithNext().any { (a, b) ->
            b.index != a.index + 1 || a.value.end > b.value.start
        }) return emptyList()
    return touched.map { it.value }
}

fun projectCourseArrangements(courses: List<CourseEntity>, config: ScheduleConfigEntity,
    periods: List<PeriodEntity>, catalog: com.xiaomanjun.sleepdownschedule.model.TimetableCatalog = com.xiaomanjun.sleepdownschedule.model.TimetableCatalog(),
    date: java.time.LocalDate = java.time.LocalDate.now()): List<CourseEntity> =
    courses.map { projectCourseArrangement(it, config, periods, catalog, date) }

/** Editors keep source rows; conflict previews use the same arrangements as the current grid. */
fun AppState.previewCourseEdits(edited: List<CourseEntity>): List<CourseEntity> {
    val previous = courses.associateBy { it.id }
    return projectCourseArrangements(edited.map {
        captureOriginalPeriodTimes(it, periods, previous[it.id]?.originalArrangement())
    }, config, periods, timetableCatalog)
}

fun AppState.withEffectiveCourseArrangements(): AppState {
    val configs = (allConfigs + config).associateBy { it.id }
    val definitions = (allPeriods.filter { it.scheduleId != config.id } + periods).groupBy { it.scheduleId }
    return copy(courses = projectCourseArrangements(courses, config, periods, timetableCatalog), allCourses = allCourses.map { course ->
        val owner = configs[course.scheduleId]
        val bells = definitions[course.scheduleId]
        if (owner == null || bells == null) course else projectCourseArrangement(course, owner, bells, timetableCatalog)
    })
}

fun CourseEntity.effectiveArrangementOrNull(): EffectiveCourseArrangement? =
    arrangementProjection?.takeIf { it.matches(this) }

fun CourseEntity.isHiddenByPeriodAlignment(): Boolean = effectiveArrangementOrNull()?.times?.isEmpty() == true

/** Reminder fragments keep every exact lesson boundary and carry the complete saved source. */
fun courseAlignmentFragments(course: CourseEntity, periods: List<PeriodEntity>): List<CourseEntity> =
    courseArrangementFragments(course, periods, forWeekDisplay = false)

/** A consecutive block remains one card even when individual lessons use different clocks. */
fun courseWeekDisplayFragments(course: CourseEntity, periods: List<PeriodEntity>): List<CourseEntity> =
    courseArrangementFragments(course, periods, forWeekDisplay = true)

private fun courseArrangementFragments(
    course: CourseEntity, periods: List<PeriodEntity>, forWeekDisplay: Boolean
): List<CourseEntity> {
    val projection = course.effectiveArrangementOrNull() ?: return listOf(course)
    if (projection.times.isEmpty()) return emptyList()
    val groups = mutableListOf<MutableList<AlignedCourseTime>>()
    projection.times.sortedBy { it.time.start }.forEach { time ->
        val previous = groups.lastOrNull()?.lastOrNull()
        val join = previous != null && time.time.index == previous.time.index + 1 &&
            (forWeekDisplay || (!time.exact && !previous.exact))
        if (!join) groups += mutableListOf<AlignedCourseTime>()
        groups.last() += time
    }
    return groups.map { times ->
        val gridIndices = times.flatMap {
            matchingPeriodIndicesForTimeRange(it.time.start, it.time.end, periods)
                .ifEmpty { courseAnchorPeriodsForTimeRange(it.time.start, it.time.end, periods) }
        }.distinct().sorted()
        val exact = times.any { it.exact }
        course.copy(arrangementProjection = projection.copy(periods = gridIndices, times = times,
            displayStart = times.minOf { it.time.start }.toString().takeIf { exact },
            displayEnd = times.maxOf { it.time.end }.toString().takeIf { exact }))
    }
}

data class CourseAlignmentConflict(val firstId: Long, val secondId: Long,
    val firstName: String, val secondName: String)

data class CourseAlignmentImpact(val courses: List<CourseEntity>, val changedCount: Int,
    val hiddenCount: Int, val partialCount: Int, val conflicts: List<CourseAlignmentConflict>)

fun CourseAlignmentImpact.summary(): String = buildString {
    append("${changedCount} 门课程的显示安排会改变。")
    if (partialCount > 0) append("\n${partialCount} 门课程只显示部分节次。")
    if (hiddenCount > 0) append("\n${hiddenCount} 门课程暂不显示，可在课程列表找到。")
    if (partialCount > 0 || hiddenCount > 0) {
        append("\n原始安排会保留，切回原作息可恢复。")
        courses.filter { it.arrangementProjection?.hiddenPeriods?.isNotEmpty() == true }.take(5).forEach {
            append("\n${it.name}：${it.arrangementProjection!!.hiddenPeriods.joinToString("、")} 节未显示")
        }
    }
    if (conflicts.isNotEmpty()) {
        append("\n新增 ${conflicts.size} 处时间冲突，可在课程中调整：")
        conflicts.take(5).forEach { append("\n${it.firstName} / ${it.secondName}") }
    }
}

fun previewCourseAlignment(courses: List<CourseEntity>, currentConfig: ScheduleConfigEntity,
    currentPeriods: List<PeriodEntity>, targetConfig: ScheduleConfigEntity,
    targetPeriods: List<PeriodEntity>, catalog: com.xiaomanjun.sleepdownschedule.model.TimetableCatalog = com.xiaomanjun.sleepdownschedule.model.TimetableCatalog()): CourseAlignmentImpact {
    val before = projectCourseArrangements(courses, currentConfig, currentPeriods, catalog)
    val after = projectCourseArrangements(courses, targetConfig, targetPeriods, catalog)
    val conflicts = buildList {
        after.indices.forEach { i -> (i + 1 until after.size).forEach { j ->
            if (after[i].weeks.any { week ->
                after[i].conflictsWith(after[j], week, targetPeriods) &&
                    !before[i].conflictsWith(before[j], week, currentPeriods)
            }) add(CourseAlignmentConflict(after[i].id, after[j].id, after[i].name, after[j].name))
        } }
    }
    return CourseAlignmentImpact(after, after.indices.count { index ->
        after[index].periods != before[index].periods ||
            courseTimeSegments(after[index], targetPeriods) != courseTimeSegments(before[index], currentPeriods)
    }, after.count { it.isHiddenByPeriodAlignment() }, after.count {
        !it.isHiddenByPeriodAlignment() && it.arrangementProjection?.hiddenPeriods?.isNotEmpty() == true
    }, conflicts)
}
