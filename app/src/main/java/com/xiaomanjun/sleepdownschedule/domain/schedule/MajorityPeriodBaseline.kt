package com.xiaomanjun.sleepdownschedule.domain.schedule

import com.xiaomanjun.sleepdownschedule.model.CourseEntity
import com.xiaomanjun.sleepdownschedule.model.ImportDraft
import com.xiaomanjun.sleepdownschedule.model.PeriodEntity
import com.xiaomanjun.sleepdownschedule.model.PeriodSchemeTimeEntity
import com.xiaomanjun.sleepdownschedule.model.ScheduleConfigEntity
import java.time.LocalTime

internal data class MajorityPeriodBaselineProposal(
    val periods: List<PeriodEntity>,
    val courses: List<CourseEntity>,
    val matchedCourseCount: Int,
    val customCourseCount: Int,
    val alreadyFollowingCount: Int,
)

private fun CourseEntity.hasStoredCustomClock(): Boolean =
    customStartTime != null || customEndTime != null || !customPeriodTimes.isNullOrBlank()

private fun CourseEntity.votedPeriodClocks(baselineByIndex: Map<Int, PeriodEntity>): Map<Int, Pair<LocalTime, LocalTime>>? {
    if (independentPeriodSchemeId != null || timingReviewReason != null) return null
    val indices = periods.filter { it > 0 }.distinct().sorted()
    if (indices.isEmpty()) return null
    val perLesson = parseCoursePeriodTimes(customPeriodTimes)
    if (perLesson.isNotEmpty()) {
        val byIndex = perLesson.associateBy { it.index }
        if (!indices.all { it in byIndex }) return null
        return indices.associateWith { index -> byIndex.getValue(index).let { it.start to it.end } }
    }
    if (customStartTime != null && customEndTime != null) {
        if (indices.size != 1) return null
        val start = runCatching { LocalTime.parse(customStartTime) }.getOrNull() ?: return null
        val end = runCatching { LocalTime.parse(customEndTime) }.getOrNull() ?: return null
        if (!end.isAfter(start)) return null
        return mapOf(indices.single() to (start to end))
    }
    if (!indices.all { it in baselineByIndex }) return null
    return indices.associateWith { index ->
        val bell = baselineByIndex.getValue(index)
        LocalTime.parse(bell.startTime) to LocalTime.parse(bell.endTime)
    }
}

private fun CourseEntity.normalizedAgainstBaseline(
    candidateByIndex: Map<Int, PeriodEntity>,
): Pair<Boolean, CourseEntity> {
    if (independentPeriodSchemeId != null || timingReviewReason != null) return false to this
    val indices = periods.filter { it > 0 }.distinct().sorted()
    if (indices.isEmpty() || !indices.all { it in candidateByIndex }) return false to this
    val expectedSegments = indices.map { index ->
        val bell = candidateByIndex.getValue(index)
        CoursePeriodTime(index, LocalTime.parse(bell.startTime), LocalTime.parse(bell.endTime))
    }
    val perLesson = parseCoursePeriodTimes(customPeriodTimes)
    val matchesCandidate = when {
        perLesson.isNotEmpty() -> {
            val byIndex = perLesson.associateBy { it.index }
            indices.size == byIndex.size && expectedSegments.all { expected ->
                byIndex[expected.index]?.let { it.start == expected.start && it.end == expected.end } == true
            }
        }
        customStartTime != null && customEndTime != null -> {
            val start = runCatching { LocalTime.parse(customStartTime) }.getOrNull()
            val end = runCatching { LocalTime.parse(customEndTime) }.getOrNull()
            val source = parseCoursePeriodTimes(originalPeriodTimes).associateBy { it.index }
            val expectedWhole = start == expectedSegments.first().start && end == expectedSegments.last().end
            expectedWhole && (indices.size == 1 || (source.size == indices.size && expectedSegments.all { expected ->
                source[expected.index]?.let { it.start == expected.start && it.end == expected.end } == true
            }))
        }
        else -> true
    }
    if (!matchesCandidate) return false to this
    if (!hasStoredCustomClock() && !originalPeriodTimes.isNullOrBlank()) return true to this
    val encodedSource = originalPeriodTimes?.takeIf { it.isNotBlank() } ?: encodeCoursePeriodTimes(expectedSegments)
    return true to copy(
        customStartTime = null,
        customEndTime = null,
        customPeriodTimes = null,
        originalPeriodTimes = encodedSource,
    )
}

/**
 * Infers the most frequently referenced non-overlapping timetable across courses so minority
 * special-campus clocks remain custom without freezing the rest of the schedule.
 */
internal fun proposeMajorityPeriodBaseline(
    courses: List<CourseEntity>,
    baselinePeriods: List<PeriodEntity>,
    scheduleId: Int = baselinePeriods.firstOrNull()?.scheduleId ?: 1,
): MajorityPeriodBaselineProposal? {
    if (courses.isEmpty()) return null
    val baselineByIndex = baselinePeriods.associateBy { it.periodIndex }
    val alreadyFollowingCount = courses.count { course ->
        course.independentPeriodSchemeId == null &&
            course.timingReviewReason == null &&
            !course.hasStoredCustomClock() &&
            course.periods.isNotEmpty() &&
            course.periods.all { it in baselineByIndex }
    }
    val votes = linkedMapOf<Int, MutableMap<Pair<LocalTime, LocalTime>, Int>>()
    courses.forEach { course ->
        course.votedPeriodClocks(baselineByIndex)?.forEach { (index, clock) ->
            val buckets = votes.getOrPut(index) { linkedMapOf() }
            buckets[clock] = (buckets[clock] ?: 0) + 1
        }
    }
    if (votes.isEmpty()) return null

    val winners = linkedMapOf<Int, Pair<LocalTime, LocalTime>>()
    votes.forEach { (index, buckets) ->
        val maxVotes = buckets.values.maxOrNull() ?: return@forEach
        val baselineClock = baselineByIndex[index]?.let {
            LocalTime.parse(it.startTime) to LocalTime.parse(it.endTime)
        }
        val winning = buckets.entries
            .filter { it.value == maxVotes }
            .maxWithOrNull(compareBy<Map.Entry<Pair<LocalTime, LocalTime>, Int>> {
                it.key == baselineClock
            }.thenBy { -it.key.first.toSecondOfDay() })
            ?.key ?: return@forEach
        winners[index] = winning
    }
    val maxVotedIndex = winners.keys.maxOrNull() ?: return null
    val maxCourseIndex = courses.flatMap { it.periods }.filter { it > 0 }.maxOrNull() ?: 0
    val targetIndices = if (alreadyFollowingCount == 0) {
        (1..maxOf(maxVotedIndex, maxCourseIndex)).toList()
    } else {
        (baselineByIndex.keys + winners.keys).distinct().sorted()
    }
    val candidatePeriods = targetIndices.mapNotNull { index ->
        val existing = baselineByIndex[index]
        val clock = winners[index] ?: existing?.let {
            LocalTime.parse(it.startTime) to LocalTime.parse(it.endTime)
        } ?: return@mapNotNull null
        PeriodEntity(
            periodIndex = index,
            startTime = clock.first.toString(),
            endTime = clock.second.toString(),
            scheduleId = scheduleId,
            alias = existing?.alias,
        )
    }
    if (candidatePeriods.isEmpty()) return null
    if (alreadyFollowingCount == 0 && !targetIndices.all { index -> candidatePeriods.any { it.periodIndex == index } }) {
        return null
    }
    val valid = validateResolvedPeriodTimes(candidatePeriods.map {
        PeriodSchemeTimeEntity(0, it.periodIndex, it.startTime, it.endTime, it.alias)
    }) == null
    if (!valid) return null

    val candidateByIndex = candidatePeriods.associateBy { it.periodIndex }
    var matchedCount = 0
    var customCount = 0
    val updatedCourses = courses.map { course ->
        val (matched, normalized) = course.normalizedAgainstBaseline(candidateByIndex)
        if (matched) matchedCount++
        else if (normalized.hasStoredCustomClock() || normalized.independentPeriodSchemeId != null) customCount++
        normalized
    }
    if (matchedCount < 2 || matchedCount <= alreadyFollowingCount) return null
    val periodsChanged = candidatePeriods.map { Triple(it.periodIndex, it.startTime, it.endTime) } !=
        baselinePeriods.sortedBy { it.periodIndex }.map { Triple(it.periodIndex, it.startTime, it.endTime) }
    if (!periodsChanged && updatedCourses == courses) return null
    return MajorityPeriodBaselineProposal(
        periods = candidatePeriods,
        courses = updatedCourses,
        matchedCourseCount = matchedCount,
        customCourseCount = customCount,
        alreadyFollowingCount = alreadyFollowingCount,
    )
}

internal fun ScheduleConfigEntity.withPeriodCounts(periods: List<PeriodEntity>): ScheduleConfigEntity {
    if (periods.isEmpty()) return this
    val counts = inferPeriodCounts(periods)
    return copy(
        morningPeriodCount = counts.morning,
        noonPeriodCount = counts.noon,
        afternoonPeriodCount = counts.afternoon,
        eveningPeriodCount = counts.evening,
    )
}

internal fun normalizeImportMajorityBaseline(
    draft: ImportDraft,
    requireZeroBaselineFollowers: Boolean = false,
): ImportDraft {
    if (draft.timetablePlan.dateTimetables.isNotEmpty()) return draft
    val proposal = proposeMajorityPeriodBaseline(
        courses = draft.courses,
        baselinePeriods = draft.periods,
        scheduleId = draft.config.id,
    ) ?: return draft
    if (requireZeroBaselineFollowers && proposal.alreadyFollowingCount > 0) return draft
    return draft.copy(
        config = draft.config.withPeriodCounts(proposal.periods),
        periods = proposal.periods,
        courses = proposal.courses,
    )
}
