package com.xiaomanjun.sleepdownschedule.domain.schedule

import com.xiaomanjun.sleepdownschedule.model.CourseEntity
import com.xiaomanjun.sleepdownschedule.model.PeriodEntity
import com.xiaomanjun.sleepdownschedule.model.PeriodSchemeTimeEntity
import com.xiaomanjun.sleepdownschedule.model.ScheduleConfigEntity
import com.xiaomanjun.sleepdownschedule.model.TimetableComboEntity
import com.xiaomanjun.sleepdownschedule.model.TimetableDateRuleEntity
import java.time.LocalDate

internal data class ScheduleRoutines(
    val config: ScheduleConfigEntity,
    val combo: TimetableComboEntity?,
    val rules: List<TimetableDateRuleEntity>,
    val schemes: List<PeriodSchemeDraft>,
    val courses: List<CourseEntity>,
) {
    val gridId: Long get() = requireNotNull(config.activePeriodSchemeId)
    val regularId: Long get() = combo?.baseSchemeId ?: gridId
    val grid: PeriodSchemeDraft get() = schemes.first { it.scheme.id == gridId }
    val regular: PeriodSchemeDraft get() = schemes.first { it.scheme.id == regularId }
    val canChangeStructure: Boolean get() {
        val indices = (1..config.totalPeriodCount()).toList()
        return grid.times.map { it.periodIndex } == indices && regular.times.map { it.periodIndex } == indices
    }

    fun routineOn(date: LocalDate): Long {
        val day = date.toString()
        return rules.firstOrNull { day in it.startDate..it.endDate }?.schemeId ?: regularId
    }

    fun timeline(schemeId: Long): PeriodTimelineSession {
        val displayed = schemes.map { scheme ->
            if (scheme.scheme.id == gridId || scheme.scheme.id == regularId) scheme
            else scheme.copy(times = alignedRoutineTimes(regular.times, scheme.times).map {
                it.copy(schemeId = scheme.scheme.id)
            })
        }
        return PeriodTimelineSession(config, SchedulePeriodSchemesDraft(displayed, schemeId))
    }

    fun gridPeriods(): List<PeriodEntity> = grid.times.map {
        PeriodEntity(it.periodIndex, it.startTime, it.endTime, config.id, it.alias)
    }
}

/** Keep an ordinary course at the same position within its part of the day. */
internal fun mapCoursesForRoutineCopy(target: ScheduleRoutines, source: ScheduleRoutines): List<CourseEntity> {
    val sourceIndices = source.grid.times.mapTo(hashSetOf()) { it.periodIndex }
    sourceIndices.retainAll(source.regular.times.map { it.periodIndex })
    val targetIndices = target.grid.times.mapTo(hashSetOf()) { it.periodIndex }
    val sameParts = target.config.hasSamePeriodTopology(source.config)
    return target.courses.map { course ->
        val overrides = parseCoursePeriodTimes(course.customPeriodTimes)
        val numbers = course.periods.distinct().sorted()
        val exactTime = (overrides.isEmpty() && course.customStartTime != null && course.customEndTime != null) ||
            (overrides.isNotEmpty() && overrides.map { it.index }.sorted() == numbers)
        if (course.independentPeriodSchemeId != null || exactTime || numbers.isEmpty() ||
            course.timingReviewReason != null) return@map course

        val mapping = numbers.associateWith { number ->
            if (sameParts) {
                require(number in sourceIndices) { "“${course.name}”的第 ${number} 节在来源作息中没有对应节次" }
                number
            } else {
                val part = PeriodDayPart.entries.firstOrNull { number in target.config.periodRange(it) }
                require(part != null && number in targetIndices) { "请先确认“${course.name}”原来的第 ${number} 节" }
                val oldRange = target.config.periodRange(part)
                val newRange = source.config.periodRange(part)
                val mapped = newRange.first + number - oldRange.first
                require(mapped in newRange && mapped in sourceIndices) {
                    "“${course.name}”的第 ${number} 节在来源作息中没有对应节次"
                }
                mapped
            }
        }
        if (mapping.all { (before, after) -> before == after }) return@map course
        fun remapClocks(value: String?): String? = encodeCoursePeriodTimes(
            parseCoursePeriodTimes(value).map { clock -> clock.copy(index = mapping[clock.index] ?: clock.index) }
        ) ?: value
        course.copy(
            periods = numbers.map(mapping::getValue),
            originalPeriodTimes = remapClocks(course.originalPeriodTimes),
            customPeriodTimes = remapClocks(course.customPeriodTimes),
        )
    }
}

internal fun alignedRoutineTimes(
    baseline: List<PeriodSchemeTimeEntity>,
    alternate: List<PeriodSchemeTimeEntity>,
): List<PeriodSchemeTimeEntity> {
    val byIndex = alternate.associateBy { it.periodIndex }
    return baseline.map { byIndex[it.periodIndex] ?: it }
}
