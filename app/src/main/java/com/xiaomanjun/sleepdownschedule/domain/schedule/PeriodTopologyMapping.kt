package com.xiaomanjun.sleepdownschedule.domain.schedule

import com.xiaomanjun.sleepdownschedule.model.CourseEntity
import com.xiaomanjun.sleepdownschedule.model.PeriodEntity
import com.xiaomanjun.sleepdownschedule.model.PeriodSchemeTimeEntity
import com.xiaomanjun.sleepdownschedule.model.ScheduleConfigEntity
import com.xiaomanjun.sleepdownschedule.domain.course.conflictsWith

/** Positive identities belong to saved lessons; negative identities belong to new draft lessons. */
internal fun netPeriodIdentities(originalCount: Int, operations: List<PeriodTopologyOperation>): List<Int> {
    val identities = (1..originalCount).toMutableList()
    val deleted = mutableMapOf<Int, Int>()
    operations.forEachIndexed { operationIndex, operation ->
        when (operation) {
            is PeriodTopologyOperation.Delete -> {
                require(operation.periodIndex in 1..identities.size) { "节次编辑记录已失效，请重新打开作息编辑" }
                deleted[operationIndex] = identities.removeAt(operation.periodIndex - 1)
            }
            is PeriodTopologyOperation.AddAfter -> {
                require(operation.periodIndex in 0..identities.size) { "节次编辑记录已失效，请重新打开作息编辑" }
                val restored = operation.restoredDeletionOperationIndex?.let {
                    requireNotNull(deleted.remove(it)) { "节次恢复记录已失效，请重新打开作息编辑" }
                }
                identities.add(operation.periodIndex, restored ?: -(operationIndex + 1))
            }
        }
    }
    return identities
}

internal fun hasNetPeriodTopologyChange(originalCount: Int, operations: List<PeriodTopologyOperation>): Boolean =
    netPeriodIdentities(originalCount, operations) != (1..originalCount).toList()

/** Automatic generation resizes each section; manual timelines resize only their global tail. */
internal fun periodTopologyResizeOperations(
    original: ScheduleConfigEntity, target: ScheduleConfigEntity, automaticSections: Boolean
): List<PeriodTopologyOperation> {
    val oldCount = original.totalPeriodCount()
    val newCount = target.totalPeriodCount()
    if (oldCount == newCount) return emptyList()
    if (!automaticSections) return if (newCount > oldCount) {
        (oldCount until newCount).map { PeriodTopologyOperation.AddAfter(it) }
    } else (oldCount downTo newCount + 1).map { PeriodTopologyOperation.Delete(it) }
    var current = original
    return buildList {
        PeriodDayPart.entries.forEach { part ->
            while (current.periodCount(part) > target.periodCount(part)) {
                add(PeriodTopologyOperation.Delete(current.periodRange(part).last))
                current = current.withTimelineCount(part, current.periodCount(part) - 1)
            }
            while (current.periodCount(part) < target.periodCount(part)) {
                val after = PeriodDayPart.entries.take(part.ordinal + 1).sumOf { current.periodCount(it) }
                add(PeriodTopologyOperation.AddAfter(after))
                current = current.withTimelineCount(part, current.periodCount(part) + 1)
            }
        }
    }
}

internal data class PeriodCourseMapping(val courses: List<CourseEntity>, val changedCount: Int)

internal data class PeriodCourseMappingApproval(
    val courses: List<CourseEntity>,
    val originalPeriods: List<PeriodEntity>,
    val targetTimes: List<PeriodSchemeTimeEntity>,
    val operations: List<PeriodTopologyOperation>
)

/** Keep clock-based mapping, but require a lossless, unambiguous result before persisting anything. */
internal fun previewPeriodCourseMapping(
    courses: List<CourseEntity>,
    originalPeriods: List<PeriodEntity>,
    targetTimes: List<PeriodSchemeTimeEntity>,
    operations: List<PeriodTopologyOperation>
): PeriodCourseMapping {
    if (!hasNetPeriodTopologyChange(originalPeriods.size, operations)) return PeriodCourseMapping(courses, 0)
    val identities = netPeriodIdentities(originalPeriods.size, operations)
    require(identities.size == targetTimes.size) { "节次编辑记录与作息不一致，请重新打开作息编辑" }
    val newTimes = targetTimes.map {
        Triple(it.periodIndex, requireNotNull(parseMinuteOfDay(it.startTime)), requireNotNull(parseMinuteOfDay(it.endTime)))
    }
    val mapped = courses.map { course ->
        if (course.periods.isEmpty()) return@map course
        require(course.periods.all { it in identities }) {
            "${course.name} 占用了已删除的节次。请放弃本次设置修改，先调整该课程的节次，再重新编辑作息"
        }
        val segments = courseTimeSegments(course, originalPeriods)
        require(segments.isNotEmpty()) { "${course.name} 的原上课时间不完整，请先检查课程时间" }
        val targets = segments.map { segment ->
            val start = segment.start.hour * 60 + segment.start.minute
            val end = segment.end.hour * 60 + segment.end.minute
            newTimes.filter { (_, nextStart, nextEnd) -> nextStart < end && nextEnd > start }.map { it.first }
        }
        require(targets.all { it.isNotEmpty() }) {
            "${course.name} 的上课时间未覆盖新节次，请先调整该课程或作息时间"
        }
        val indices = targets.flatten().distinct().sorted()
        require(indices.size == course.periods.distinct().size &&
            (segments.size == 1 || targets.all { it.size == 1 })) {
            "${course.name} 的节次对应关系不唯一，请先调整该课程或作息时间"
        }
        require(course.customPeriodTimes == null || indices == course.periods) {
            "${course.name} 有独立逐节铃声，请先确认节次结构调整后的对应关系"
        }
        course.copy(periods = indices)
    }
    val targetPeriods = targetTimes.map { PeriodEntity(it.periodIndex, it.startTime, it.endTime) }
    mapped.indices.forEach { i ->
        (i + 1 until mapped.size).forEach { j ->
            val first = mapped[i]
            val second = mapped[j]
            if (first.weeks.any { week ->
                    first.conflictsWith(second, week, targetPeriods) &&
                        !courses[i].conflictsWith(courses[j], week, originalPeriods)
                }) {
                throw IllegalArgumentException("${first.name} 与 ${second.name} 映射后会占用同一节次，请先调整课程或作息时间")
            }
        }
    }
    return PeriodCourseMapping(mapped, courses.indices.count { courses[it].periods != mapped[it].periods })
}
