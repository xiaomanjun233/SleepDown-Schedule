package com.xiaomanjun.sleepdownschedule.feature.importing

import com.xiaomanjun.sleepdownschedule.domain.schedule.customTimeRangeOrNull
import com.xiaomanjun.sleepdownschedule.model.ImportDraft

/** AI often repeats the default bell range in fields reserved for independent course times. */
internal fun normalizeAiImportTimes(draft: ImportDraft, previous: ImportDraft? = null): ImportDraft {
    val ordered = draft.periods.sortedBy { it.periodIndex }
    val positions = ordered.mapIndexed { position, period -> period.periodIndex to position }.toMap()
    val previousCourses = previous?.courses?.associateBy { it.id }
    val courses = draft.courses.map normalize@{ course ->
        if (!course.customPeriodTimes.isNullOrBlank()) return@normalize course
        val old = previousCourses?.get(course.id)
        // Metadata edits and imported source snapshots must not change an existing clock policy.
        if (old != null && old.periods == course.periods &&
            old.customStartTime == course.customStartTime && old.customEndTime == course.customEndTime) return@normalize course
        if (previous == null && course.originalPeriodTimes != null) return@normalize course
        val range = course.customTimeRangeOrNull() ?: return@normalize course
        val indexes = course.periods.distinct().sorted()
        val selectedPositions = indexes.map { positions[it] ?: return@normalize course }
        if (selectedPositions.isEmpty() || selectedPositions.zipWithNext().any { (left, right) -> right != left + 1 }) {
            return@normalize course
        }
        val first = ordered[selectedPositions.first()]
        val last = ordered[selectedPositions.last()]
        if (range.first != ScheduleImportParser.parseTimeForUi(first.startTime) ||
            range.second != ScheduleImportParser.parseTimeForUi(last.endTime)) return@normalize course
        course.copy(customStartTime = null, customEndTime = null)
    }
    return if (courses == draft.courses) draft else draft.copy(courses = courses)
}
