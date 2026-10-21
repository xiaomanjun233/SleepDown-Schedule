package com.xiaomanjun.sleepdownschedule.domain.schedule

import com.xiaomanjun.sleepdownschedule.model.*
import kotlinx.serialization.Serializable
import java.security.MessageDigest

/** Persisted pending review, independent of transient grid projections and database row identity. */
@Serializable
internal data class RefreshCourse(
    val id: Long = 0,
    val name: String,
    val teacher: String?,
    val location: String?,
    val weekday: Int,
    val periods: List<Int>,
    val weeks: List<Int>,
    val parity: String,
    val note: String?,
    val start: String?,
    val end: String?,
    val periodTimes: String?,
    val originalTimes: String?,
    val color: Long?
) {
    fun entity(scheduleId: Int) = CourseEntity(id = id, name = name, teacher = teacher,
        location = location, weekday = weekday, periods = periods, weeks = weeks,
        weekParity = WeekParity.valueOf(parity), note = note, customStartTime = start,
        customEndTime = end, customPeriodTimes = periodTimes, originalPeriodTimes = originalTimes,
        customColorArgb = color, scheduleId = scheduleId)

    fun contentKey() = copy(id = 0, color = null, name = name.trim(),
        teacher = teacher.orEmpty().trim(), location = location.orEmpty().trim(), note = note.orEmpty().trim(),
        periods = periods.distinct().sorted(), weeks = weeks.distinct().sorted())

    companion object {
        fun from(course: CourseEntity): RefreshCourse = course.originalArrangement().let {
            RefreshCourse(it.id, it.name, it.teacher, it.location, it.weekday, it.periods, it.weeks,
                it.weekParity.name, it.note, it.customStartTime, it.customEndTime,
                it.customPeriodTimes, it.originalPeriodTimes, it.customColorArgb)
        }
    }
}

@Serializable
internal data class RefreshTerm(val totalWeeks: Int, val startDate: String?) {
    companion object {
        fun from(config: ScheduleConfigEntity) = RefreshTerm(config.totalWeeks, config.termStartDate)
    }
}

internal data class RefreshDifference(val before: RefreshCourse?, val after: RefreshCourse?)

/** Match exact contents first, then only unambiguous pairs. Repeated lessons never pair arbitrarily. */
internal fun refreshDifferences(before: List<RefreshCourse>, after: List<RefreshCourse>): List<RefreshDifference> {
    val old = before.toMutableList()
    val fresh = after.toMutableList()
    before.forEach { course ->
        val index = fresh.indexOfFirst { it.contentKey() == course.contentKey() }
        if (index >= 0) { old.remove(course); fresh.removeAt(index) }
    }
    val result = mutableListOf<RefreshDifference>()
    fun pairUnique(key: (RefreshCourse) -> Any) {
        val oldGroups = old.groupBy(key)
        val newGroups = fresh.groupBy(key)
        oldGroups.forEach { (identity, rows) ->
            val incoming = newGroups[identity]
            if (rows.size == 1 && incoming?.size == 1) {
                result += RefreshDifference(rows.single(), incoming.single())
                old.remove(rows.single()); fresh.remove(incoming.single())
            }
        }
    }
    pairUnique { listOf(it.name.trim(), it.weekday, it.periods.sorted()) }
    pairUnique { it.name.trim() }
    result += old.map { RefreshDifference(it, null) }
    result += fresh.map { RefreshDifference(null, it) }
    return result
}

internal fun mergeReviewedRefresh(
    before: List<RefreshCourse>, differences: List<RefreshDifference>, useIncoming: Set<Int>
): List<RefreshCourse> {
    require(useIncoming.all { it in differences.indices }) { "课表差异选择已失效，请重新核对" }
    val replacedIds = differences.mapNotNull { it.before?.id }.toSet()
    return before.filterNot { it.id in replacedIds } + differences.mapIndexedNotNull { index, change ->
        if (index !in useIncoming) change.before else change.after?.copy(
            id = change.before?.id ?: 0,
            color = change.before?.color ?: change.after.color
        )
    }
}

/** Ignore appearance changes, but protect clocks, teaching weeks and adjustments during review. */
internal fun refreshTimingKey(config: ScheduleConfigEntity, periods: List<PeriodEntity>): String =
    refreshDigest(listOf(config.totalWeeks, config.termStartDate, config.periodAlignmentMode,
        config.scheduleAdjustmentsJson, periods.sortedBy { it.periodIndex }).toString())

internal fun refreshDigest(value: String): String = MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

internal class ScheduleRefreshStaleException : IllegalStateException("课表在核对期间有新修改，已重新生成差异，请再次选择")
