package com.xiaomanjun.sleepdownschedule

import java.time.LocalTime

/**
 * A visually continuous portion of a course. Imported courses may contain
 * discontinuous period indexes, so they must not be represented by one tall card.
 */
data class WeekCourseSegment(
    val course: CourseEntity,
    val startPosition: Int,
    val endPosition: Int,
    val periods: List<Int>
) {
    val span: Int get() = endPosition - startPosition + 1
}

/** A connected component in the interval-overlap graph for one weekday. */
data class WeekConflictGroup(
    val segments: List<WeekCourseSegment>
) {
    val courses: List<CourseEntity> = segments
        .map { it.course }
        .distinctBy { it.id }
        .sortedBy { it.id }

    val hasConflict: Boolean get() = courses.size > 1
}

fun buildWeekConflictGroups(
    courses: List<CourseEntity>,
    periodIndexes: List<Int>,
    periodDefinitions: List<PeriodEntity> = emptyList()
): List<WeekConflictGroup> {
    if (periodIndexes.isEmpty()) return emptyList()
    val positionByPeriod = periodIndexes.withIndex().associate { it.value to it.index }
    val segments = courses.flatMap { course ->
        val positions = course.periods
            .mapNotNull(positionByPeriod::get)
            .distinct()
            .sorted()
        if (positions.isEmpty()) {
            emptyList()
        } else {
            buildList {
                var start = positions.first()
                var previous = start
                positions.drop(1).forEach { position ->
                    if (position != previous + 1) {
                        add(course.toWeekSegment(start, previous, periodIndexes))
                        start = position
                    }
                    previous = position
                }
                add(course.toWeekSegment(start, previous, periodIndexes))
            }
        }
    }.sortedWith(
        compareBy<WeekCourseSegment> { it.startPosition }
            .thenBy { it.endPosition }
            .thenBy { it.course.id }
    )
    if (segments.isEmpty()) return emptyList()

    fun overlaps(first: WeekCourseSegment, second: WeekCourseSegment): Boolean {
        if (first.startPosition > second.endPosition || second.startPosition > first.endPosition) {
            return false
        }
        if (periodDefinitions.isEmpty() ||
            (!first.course.hasCustomTime() && !second.course.hasCustomTime())
        ) return true
        val firstIntervals = first.course.copy(periods = first.periods)
            .occupiedTimeIntervals(periodDefinitions)
        val secondIntervals = second.course.copy(periods = second.periods)
            .occupiedTimeIntervals(periodDefinitions)
        if (firstIntervals.isEmpty() || secondIntervals.isEmpty()) return true
        return firstIntervals.any { (firstStart, firstEnd) ->
            secondIntervals.any { (secondStart, secondEnd) ->
                firstStart < secondEnd && secondStart < firstEnd
            }
        }
    }

    val remaining = segments.indices.toMutableSet()
    val groups = mutableListOf<WeekConflictGroup>()
    while (remaining.isNotEmpty()) {
        val queue = ArrayDeque<Int>()
        val component = mutableListOf<WeekCourseSegment>()
        val firstIndex = remaining.first()
        remaining.remove(firstIndex)
        queue.addLast(firstIndex)
        while (queue.isNotEmpty()) {
            val index = queue.removeFirst()
            val segment = segments[index]
            component += segment
            val connected = remaining.filter { candidate -> overlaps(segment, segments[candidate]) }
            connected.forEach { candidate ->
                remaining.remove(candidate)
                queue.addLast(candidate)
            }
        }
        groups += WeekConflictGroup(
            component.sortedWith(
                compareBy<WeekCourseSegment> { it.startPosition }
                    .thenBy { it.endPosition }
                    .thenBy { it.course.id }
            )
        )
    }
    return groups.sortedWith(
        compareBy<WeekConflictGroup> { group -> group.segments.minOf { it.startPosition } }
            .thenBy { group -> group.segments.minOf { it.endPosition } }
    )
}

fun CourseEntity.conflictsWith(other: CourseEntity, week: Int): Boolean {
    return conflictsWith(other, week, emptyList())
}

fun CourseEntity.conflictsWith(
    other: CourseEntity,
    week: Int,
    periodDefinitions: List<PeriodEntity>
): Boolean {
    if (id == other.id || scheduleId != other.scheduleId || weekday != other.weekday) return false
    if (week !in weeks || week !in other.weeks) return false
    if (!parityMatches(weekParity, week) || !parityMatches(other.weekParity, week)) return false
    if (periodDefinitions.isNotEmpty() && (hasCustomTime() || other.hasCustomTime())) {
        val ownIntervals = occupiedTimeIntervals(periodDefinitions)
        val otherIntervals = other.occupiedTimeIntervals(periodDefinitions)
        if (ownIntervals.isNotEmpty() && otherIntervals.isNotEmpty()) {
            return ownIntervals.any { (ownStart, ownEnd) ->
                otherIntervals.any { (otherStart, otherEnd) ->
                    ownStart < otherEnd && otherStart < ownEnd
                }
            }
        }
    }
    val otherPeriods = other.periods.toHashSet()
    return periods.any(otherPeriods::contains)
}

private fun CourseEntity.occupiedTimeIntervals(
    periodDefinitions: List<PeriodEntity>
): List<Pair<LocalTime, LocalTime>> {
    customTimeRangeOrNull()?.let { return listOf(it) }
    val definitions = periodDefinitions.associateBy(PeriodEntity::periodIndex)
    return periods.distinct().mapNotNull { periodIndex ->
        val period = definitions[periodIndex] ?: return@mapNotNull null
        val start = runCatching { LocalTime.parse(period.startTime) }.getOrNull() ?: return@mapNotNull null
        val end = runCatching { LocalTime.parse(period.endTime) }.getOrNull() ?: return@mapNotNull null
        (start to end).takeIf { end.isAfter(start) }
    }
}

fun conflictWeeksForEditedCourse(
    original: CourseEntity,
    edited: CourseEntity,
    courses: List<CourseEntity>,
    periodDefinitions: List<PeriodEntity> = emptyList()
): List<Int> {
    return edited.weeks
        .distinct()
        .sorted()
        .filter { week ->
            parityMatches(edited.weekParity, week) &&
                courses.any { other ->
                    other.id != original.id &&
                        edited.conflictsWith(other, week, periodDefinitions) &&
                        !original.conflictsWith(other, week, periodDefinitions)
                }
        }
}

fun conflictWeeksForEditedCourseGroup(
    originals: List<CourseEntity>,
    edited: List<CourseEntity>,
    courses: List<CourseEntity>,
    periodDefinitions: List<PeriodEntity> = emptyList()
): List<Int> {
    val originalIds = originals.map(CourseEntity::id).toSet()
    val otherCourses = courses.filterNot { it.id in originalIds }
    return edited
        .flatMap(CourseEntity::weeks)
        .distinct()
        .sorted()
        .filter { week ->
            edited.any { replacement ->
                replacement.weekday in 1..7 &&
                    week in replacement.weeks &&
                    otherCourses.any { other ->
                        replacement.conflictsWith(other, week, periodDefinitions) &&
                            originals.none { original ->
                                original.conflictsWith(other, week, periodDefinitions)
                            }
                    }
            }
        }
}

fun conflictWeeksForAddedCourses(
    added: List<CourseEntity>,
    courses: List<CourseEntity>,
    periodDefinitions: List<PeriodEntity> = emptyList()
): List<Int> = added
    .flatMap(CourseEntity::weeks)
    .distinct()
    .sorted()
    .filter { week ->
        added.any { candidate ->
            week in candidate.weeks &&
                courses.any { existing ->
                    candidate.conflictsWith(existing, week, periodDefinitions)
                }
        }
    }

fun conflictWeeksForSingleWeekEdit(
    original: CourseEntity,
    edited: CourseEntity,
    targetWeek: Int,
    courses: List<CourseEntity>,
    periodDefinitions: List<PeriodEntity> = emptyList()
): List<Int> = conflictWeeksForEditedCourse(
    original = original.copy(weeks = listOf(targetWeek)),
    edited = edited.copy(weeks = listOf(targetWeek)),
    courses = courses,
    periodDefinitions = periodDefinitions
)

fun nearestAvailableCourseMove(
    course: CourseEntity,
    week: Int,
    courses: List<CourseEntity>,
    periodIndexes: List<Int>,
    weekdayCount: Int
): CourseEntity? {
    if (periodIndexes.isEmpty() || weekdayCount <= 0) return null
    val sourcePositions = course.periods
        .mapNotNull { periodIndexes.indexOf(it).takeIf { position -> position >= 0 } }
        .distinct()
        .sorted()
    if (sourcePositions.isEmpty()) return null
    val sourceStart = sourcePositions.first()
    val offsets = sourcePositions.map { it - sourceStart }
    val occupiedByDay = courses
        .asSequence()
        .filter {
            it.id != course.id &&
                week in it.weeks &&
                parityMatches(it.weekParity, week)
        }
        .groupBy({ it.weekday }, { it.periods })
        .mapValues { (_, values) -> values.flatten().toHashSet() }
    return (1..weekdayCount)
        .flatMap { weekday ->
            periodIndexes.indices.mapNotNull { startPosition ->
                val candidatePositions = offsets.map { startPosition + it }
                if (candidatePositions.any { it !in periodIndexes.indices }) return@mapNotNull null
                val candidatePeriods = candidatePositions.map(periodIndexes::get)
                if (candidatePeriods.any { it in occupiedByDay[weekday].orEmpty() }) return@mapNotNull null
                val dayDistance = kotlin.math.abs(weekday - course.weekday)
                val periodDistance = kotlin.math.abs(startPosition - sourceStart)
                Triple(dayDistance * periodIndexes.size * 2 + periodDistance, weekday, candidatePeriods)
            }
        }
        .minWithOrNull(
            compareBy<Triple<Int, Int, List<Int>>> { it.first }
                .thenBy { kotlin.math.abs(it.second - course.weekday) }
                .thenBy { it.second }
                .thenBy { periodIndexes.indexOf(it.third.first()) }
        )
        ?.let { (_, weekday, periods) -> course.copy(weekday = weekday, periods = periods) }
}

private fun CourseEntity.toWeekSegment(
    start: Int,
    end: Int,
    periodIndexes: List<Int>
): WeekCourseSegment {
    return WeekCourseSegment(
        course = this,
        startPosition = start,
        endPosition = end,
        periods = periodIndexes.subList(start, end + 1)
    )
}
