package com.xiaomanjun.sleepdownschedule.domain.schedule

import com.xiaomanjun.sleepdownschedule.domain.course.conflictsWith
import com.xiaomanjun.sleepdownschedule.model.CourseEntity
import com.xiaomanjun.sleepdownschedule.model.AppState
import com.xiaomanjun.sleepdownschedule.model.PeriodAlignmentMode
import com.xiaomanjun.sleepdownschedule.model.PeriodEntity
import com.xiaomanjun.sleepdownschedule.model.ScheduleConfigEntity
import java.time.LocalTime

/** Only this immutable, non-Room projection contains clipped/rebound period numbers. */
data class AlignedCourseTime(val time: CoursePeriodTime, val exact: Boolean)

data class EffectiveCourseArrangement(
    val source: CourseEntity,
    val periods: List<Int>,
    val times: List<AlignedCourseTime>,
    val hiddenPeriods: List<Int>,
    val mode: PeriodAlignmentMode,
    val displayStart: String? = source.customStartTime,
    val displayEnd: String? = source.customEndTime,
    val displayPeriodTimes: String? = source.customPeriodTimes
) {
    fun matches(course: CourseEntity): Boolean = course.periods == periods &&
        course.customStartTime == displayStart && course.customEndTime == displayEnd &&
        course.customPeriodTimes == displayPeriodTimes
}

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
    targetPeriods: List<PeriodEntity>): CourseEntity {
    val source = course.originalArrangement()
    val indices = source.periods.distinct().sorted()
    val targets = targetPeriods.sortedBy { it.periodIndex }.map {
        CoursePeriodTime(it.periodIndex, LocalTime.parse(it.startTime), LocalTime.parse(it.endTime))
    }.filter { it.end > it.start }
    val byIndex = targets.associateBy { it.index }
    val custom = parseCoursePeriodTimes(source.customPeriodTimes).associateBy { it.index }
    val wholeCustom = source.customTimeRangeOrNull().takeIf { custom.isEmpty() }
    val original = if (source.originalPeriodTimes != null) parseCoursePeriodTimes(source.originalPeriodTimes)
        else if (config.periodAlignmentMode == PeriodAlignmentMode.INDEX) targets.filter { it.index in indices } else emptyList()
    val originalByIndex = original.associateBy { it.index }
    val aligned = mutableListOf<AlignedCourseTime>()
    val anchors = mutableSetOf<Int>()
    val hidden = mutableSetOf<Int>()

    fun exact(index: Int, start: LocalTime, end: LocalTime) {
        val positions = courseAnchorPeriodsForTimeRange(start, end, targetPeriods)
        anchors.addAll(positions)
        aligned += AlignedCourseTime(CoursePeriodTime(positions.firstOrNull() ?: index, start, end), true)
    }
    if (wholeCustom != null) {
        exact(indices.firstOrNull() ?: 0, wholeCustom.first, wholeCustom.second)
    } else {
        custom.values.filter { it.index in indices }.forEach { exact(it.index, it.start, it.end) }
        val ordinary = indices.filter { it !in custom }
        if (config.periodAlignmentMode == PeriodAlignmentMode.INDEX) {
            ordinary.forEach { index ->
                val target = byIndex[index]
                if (target == null) hidden += index else {
                    anchors += index
                    aligned += AlignedCourseTime(target, false)
                }
            }
        } else {
            require(ordinary.all { it in originalByIndex }) { "${source.name} 的原始上课时间不完整，无法按时间对齐" }
            // A custom-covered source lesson separates the ordinary time ranges around it.
            val ranges = mutableListOf<MutableList<Int>>()
            var group: MutableList<Int>? = null
            indices.forEach { index ->
                if (index in custom) group = null else {
                    if (group == null) { group = mutableListOf(); ranges += group!! }
                    group!! += index
                }
            }
            val matched = ranges.flatMap { range ->
                val start = range.minOf { originalByIndex.getValue(it).start }
                val end = range.maxOf { originalByIndex.getValue(it).end }
                targets.filter { it.start < end && it.end > start }
            }.distinctBy { it.index }
            matched.forEach { anchors += it.index; aligned += AlignedCourseTime(it, false) }
            ordinary.filterTo(hidden) { index ->
                val originalTime = originalByIndex.getValue(index)
                targets.none { it.start < originalTime.end && it.end > originalTime.start }
            }
        }
    }
    val projection = EffectiveCourseArrangement(source, anchors.sorted(),
        aligned.distinct().sortedBy { it.time.start }, hidden.sorted(), config.periodAlignmentMode)
    return source.copy(periods = projection.periods, arrangementProjection = projection)
}

fun projectCourseArrangements(courses: List<CourseEntity>, config: ScheduleConfigEntity,
    periods: List<PeriodEntity>): List<CourseEntity> = courses.map { projectCourseArrangement(it, config, periods) }

/** Editors keep source rows; conflict previews use the same arrangements as the current grid. */
fun AppState.previewCourseEdits(edited: List<CourseEntity>): List<CourseEntity> {
    val previous = courses.associateBy { it.id }
    return projectCourseArrangements(edited.map {
        captureOriginalPeriodTimes(it, periods, previous[it.id]?.originalArrangement())
    }, config, periods)
}

fun AppState.withEffectiveCourseArrangements(): AppState {
    val configs = (allConfigs + config).associateBy { it.id }
    val definitions = (allPeriods.filter { it.scheduleId != config.id } + periods).groupBy { it.scheduleId }
    return copy(courses = projectCourseArrangements(courses, config, periods), allCourses = allCourses.map { course ->
        val owner = configs[course.scheduleId]
        val bells = definitions[course.scheduleId]
        if (owner == null || bells == null) course else projectCourseArrangement(course, owner, bells)
    })
}

fun CourseEntity.effectiveArrangementOrNull(): EffectiveCourseArrangement? =
    arrangementProjection?.takeIf { it.matches(this) }

fun CourseEntity.isHiddenByPeriodAlignment(): Boolean = effectiveArrangementOrNull()?.times?.isEmpty() == true

/** Reuse CourseEntity fragments for the grid/reminder code, carrying the complete source with each. */
fun courseAlignmentFragments(course: CourseEntity, periods: List<PeriodEntity>): List<CourseEntity> {
    val projection = course.effectiveArrangementOrNull() ?: return listOf(course)
    if (projection.times.isEmpty()) return emptyList()
    val order = periods.sortedBy { it.periodIndex }.map { it.periodIndex }
    val normal = projection.times.filterNot { it.exact }.associateBy { it.time.index }
    val groups = mutableListOf<MutableList<AlignedCourseTime>>()
    var group: MutableList<AlignedCourseTime>? = null
    order.forEach { index ->
        val time = normal[index]
        if (time == null) group = null else {
            if (group == null) { group = mutableListOf(); groups += group!! }
            group!! += time
        }
    }
    val exact = projection.times.filter { it.exact }
    // A wholly custom course keeps its original complete clock interval and lesson boundaries.
    if (normal.isEmpty() && exact.size == 1 && projection.source.customPeriodTimes.isNullOrBlank()) return listOf(course)
    return (groups.map { it.toList() } + exact.map { listOf(it) }).map { times ->
        val exactOnly = times.all { it.exact }
        val fragmentIndices = if (exactOnly) times.flatMap {
            courseAnchorPeriodsForTimeRange(it.time.start, it.time.end, periods)
        }.distinct().sorted() else times.map { it.time.index }.distinct().sorted()
        val start = if (exactOnly) times.minOf { it.time.start }.toString() else null
        val end = if (exactOnly) times.maxOf { it.time.end }.toString() else null
        val bells = if (exactOnly) encodeCoursePeriodTimes(times.map { it.time }) else null
        course.copy(periods = fragmentIndices, customStartTime = start, customEndTime = end,
            customPeriodTimes = bells, arrangementProjection = projection.copy(periods = fragmentIndices,
                times = times, displayStart = start, displayEnd = end, displayPeriodTimes = bells))
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
        append("\n新增 ${conflicts.size} 处时间冲突，请先调整课程：")
        conflicts.take(5).forEach { append("\n${it.firstName} / ${it.secondName}") }
    }
}

fun previewCourseAlignment(courses: List<CourseEntity>, currentConfig: ScheduleConfigEntity,
    currentPeriods: List<PeriodEntity>, targetConfig: ScheduleConfigEntity,
    targetPeriods: List<PeriodEntity>): CourseAlignmentImpact {
    val before = projectCourseArrangements(courses, currentConfig, currentPeriods)
    val after = projectCourseArrangements(courses, targetConfig, targetPeriods)
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
