package com.xiaomanjun.sleepdownschedule.data.local

import com.xiaomanjun.sleepdownschedule.model.*
import com.xiaomanjun.sleepdownschedule.domain.schedule.arrangementForWrite
import com.xiaomanjun.sleepdownschedule.domain.schedule.parseCoursePeriodTimes
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private val clockFormat = DateTimeFormatter.ofPattern("HH:mm")
internal fun minuteClock(minute: Int): String = LocalTime.of(minute / 60, minute % 60).format(clockFormat)
private fun clockMinute(value: String): Int = LocalTime.parse(value).let { it.hour * 60 + it.minute }

internal fun CourseEntity.toStoredTiming(): StoredCourseWithTiming {
    val source = arrangementForWrite()
    val numbers = source.periods.distinct().sorted()
    val exact = parseCoursePeriodTimes(source.customPeriodTimes)
    val whole = exact.isEmpty() && source.customStartTime != null && source.customEndTime != null
    val complete = exact.isNotEmpty() && exact.map { it.index } == numbers
    val mode = when {
        whole || complete -> "CUSTOM"
        source.independentPeriodSchemeId != null -> "FOLLOW_SCHEME"
        else -> "FOLLOW_TABLE"
    }
    val clocks = if (whole) listOf(CourseClockSegmentEntity(source.id, 0, null,
        clockMinute(source.customStartTime!!), clockMinute(source.customEndTime!!)))
    else exact.mapIndexed { index, time -> CourseClockSegmentEntity(source.id, index, time.index,
        time.start.hour * 60 + time.start.minute, time.end.hour * 60 + time.end.minute) }
    require(clocks.all { it.startMinute in 0..1439 && it.endMinute in 0..1439 && it.startMinute < it.endMinute }) {
        "课程真实时间无效"
    }
    val activeWeeks = source.weeks.distinct().sorted().filter { week ->
        week > 0 && when (source.weekParity) { WeekParity.ALL -> true; WeekParity.ODD -> week % 2 == 1; WeekParity.EVEN -> week % 2 == 0 }
    }
    return StoredCourseWithTiming(StoredCourseEntity(source.id, source.name, source.teacher, source.location,
        source.weekday, source.note, source.customColorArgb, source.scheduleId, mode,
        source.independentPeriodSchemeId.takeUnless { mode == "CUSTOM" }, source.credit, source.originalPeriodTimes,
        source.timingReviewReason, source.legacyTimingSourceJson, source.sourcePaletteIndex), numbers.map { CourseSectionEntity(source.id, it) },
        activeWeeks.map { CourseWeekEntity(source.id, it) }, clocks)
}

internal fun StoredCourseWithTiming.toCourse(): CourseEntity {
    val sortedClocks = clocks.sortedBy { it.sequence }
    val custom = course.timingMode == "CUSTOM"
    val perLesson = sortedClocks.takeIf { it.isNotEmpty() && it.all { clock -> clock.sectionNumber != null } }
        ?.joinToString(";") { "${it.sectionNumber},${minuteClock(it.startMinute)}-${minuteClock(it.endMinute)}" }
    return CourseEntity(id = course.id, name = course.name, teacher = course.teacher, location = course.location,
        weekday = course.weekday, periods = sections.map { it.number }.sorted(), weeks = weeks.map { it.week }.sorted(),
        weekParity = WeekParity.ALL, note = course.note,
        customStartTime = sortedClocks.minOfOrNull { it.startMinute }?.takeIf { custom }?.let(::minuteClock),
        customEndTime = sortedClocks.maxOfOrNull { it.endMinute }?.takeIf { custom }?.let(::minuteClock),
        customColorArgb = course.customColorArgb, scheduleId = course.scheduleId, customPeriodTimes = perLesson,
        originalPeriodTimes = course.sourcePeriodTimes, independentPeriodSchemeId = course.independentPeriodSchemeId,
        credit = course.credit, timingReviewReason = course.reviewReason, legacyTimingSourceJson = course.legacySourceJson,
        sourcePaletteIndex = course.paletteIndex)
}
