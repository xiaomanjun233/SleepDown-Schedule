package com.xiaomanjun.sleepdownschedule.domain.schedule

import com.xiaomanjun.sleepdownschedule.model.AppState
import com.xiaomanjun.sleepdownschedule.model.CourseEntity
import com.xiaomanjun.sleepdownschedule.model.PeriodEntity
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

internal data class ColorOSCourseExport(
    val json: String,
    val exportedCount: Int,
    val skippedCount: Int
)

internal object ColorOSCourseMapper {
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    fun export(
        date: LocalDate,
        state: AppState,
        zoneId: ZoneId = ZoneId.systemDefault(),
        colorForCourse: (CourseEntity) -> Int = { course ->
            (course.customColorArgb ?: state.config.cardColorArgb).toInt()
        }
    ): ColorOSCourseExport {
        val exported = mutableListOf<ExportedCourse>()
        var skippedCount = 0
        coursesForDate(state, date).forEach { course ->
            val sessions = exportSessions(course, state.periods, colorForCourse(course))
            if (sessions.isEmpty()) {
                skippedCount++
            } else {
                exported += sessions
            }
        }
        val ordered = exported.sortedWith(compareBy<ExportedCourse> { it.startTime }.thenBy { it.id })
        val json = buildJsonArray {
            ordered.forEach { course ->
                add(buildJsonObject {
                    put("id", course.id)
                    put("courseName", course.courseName)
                    put("room", course.room)
                    put("teacher", course.teacher)
                    put("startTime", course.startTime.format(timeFormatter))
                    put("endTime", course.endTime.format(timeFormatter))
                    put("color", course.color)
                    put("extra", "")
                    put("startTimestamp", date.atTime(course.startTime).atZone(zoneId).toEpochSecond())
                    put("endTimestamp", date.atTime(course.endTime).atZone(zoneId).toEpochSecond())
                })
            }
        }.toString()
        return ColorOSCourseExport(json, ordered.size, skippedCount)
    }

    private fun exportSessions(
        course: CourseEntity,
        periodDefinitions: List<PeriodEntity>,
        color: Int
    ): List<ExportedCourse> {
        course.customTimeRangeOrNull()?.let { (start, end) ->
            return listOf(exportedCourse(course, course.periods.minOrNull() ?: 0, start, end, color))
        }

        val timesByPeriod = periodDefinitions.associateBy(PeriodEntity::periodIndex)
        if (course.periods.isEmpty() || course.periods.any { it !in timesByPeriod }) return emptyList()
        return contiguousPeriodGroups(course.periods, periodDefinitions).mapNotNull { group ->
            val start = runCatching { LocalTime.parse(timesByPeriod.getValue(group.first()).startTime) }.getOrNull()
                ?: return@mapNotNull null
            val end = runCatching { LocalTime.parse(timesByPeriod.getValue(group.last()).endTime) }.getOrNull()
                ?: return@mapNotNull null
            if (!end.isAfter(start)) return@mapNotNull null
            exportedCourse(course, group.first(), start, end, color)
        }
    }

    private fun contiguousPeriodGroups(
        selectedPeriods: List<Int>,
        periodDefinitions: List<PeriodEntity>
    ): List<List<Int>> {
        val selected = selectedPeriods.toSet()
        val groups = mutableListOf<MutableList<Int>>()
        var previousPosition = -2
        periodDefinitions.sortedBy(PeriodEntity::periodIndex).forEachIndexed { position, period ->
            if (period.periodIndex in selected) {
                if (position != previousPosition + 1) groups.add(mutableListOf())
                groups.last() += period.periodIndex
                previousPosition = position
            }
        }
        return groups
    }

    private fun exportedCourse(
        course: CourseEntity,
        groupStart: Int,
        startTime: LocalTime,
        endTime: LocalTime,
        color: Int
    ) = ExportedCourse(
        id = stableCourseId(course.id, groupStart),
        courseName = course.name,
        room = course.location.orEmpty(),
        teacher = course.teacher.orEmpty(),
        startTime = startTime,
        endTime = endTime,
        color = "#%08x".format(color.toLong() and 0xFFFF_FFFFL)
    )

    private fun stableCourseId(courseId: Long, groupStart: Int): Long = runCatching {
        Math.addExact(Math.multiplyExact(courseId, 1_000L), groupStart.toLong())
    }.getOrElse {
        courseId xor (groupStart.toLong() shl 32)
    }

    private data class ExportedCourse(
        val id: Long,
        val courseName: String,
        val room: String,
        val teacher: String,
        val startTime: LocalTime,
        val endTime: LocalTime,
        val color: String
    )

}
