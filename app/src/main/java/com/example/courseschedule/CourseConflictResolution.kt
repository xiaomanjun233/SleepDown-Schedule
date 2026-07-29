package com.example.courseschedule

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
    periodIndexes: List<Int>
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

    val groups = mutableListOf<WeekConflictGroup>()
    var active = mutableListOf(segments.first())
    var activeEnd = segments.first().endPosition
    segments.drop(1).forEach { segment ->
        if (segment.startPosition <= activeEnd) {
            active += segment
            activeEnd = maxOf(activeEnd, segment.endPosition)
        } else {
            groups += WeekConflictGroup(active.toList())
            active = mutableListOf(segment)
            activeEnd = segment.endPosition
        }
    }
    groups += WeekConflictGroup(active.toList())
    return groups
}

fun CourseEntity.conflictsWith(other: CourseEntity, week: Int): Boolean {
    if (id == other.id || scheduleId != other.scheduleId || weekday != other.weekday) return false
    if (week !in weeks || week !in other.weeks) return false
    if (!parityMatches(weekParity, week) || !parityMatches(other.weekParity, week)) return false
    val otherPeriods = other.periods.toHashSet()
    return periods.any(otherPeriods::contains)
}

fun conflictWeeksForEditedCourse(
    original: CourseEntity,
    edited: CourseEntity,
    courses: List<CourseEntity>
): List<Int> {
    return edited.weeks
        .distinct()
        .sorted()
        .filter { week ->
            parityMatches(edited.weekParity, week) &&
                courses.any { other ->
                    other.id != original.id &&
                        edited.conflictsWith(other, week) &&
                        !original.conflictsWith(other, week)
                }
        }
}

fun conflictWeeksForSingleWeekEdit(
    original: CourseEntity,
    edited: CourseEntity,
    targetWeek: Int,
    courses: List<CourseEntity>
): List<Int> = conflictWeeksForEditedCourse(
    original = original.copy(weeks = listOf(targetWeek)),
    edited = edited.copy(weeks = listOf(targetWeek)),
    courses = courses
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
