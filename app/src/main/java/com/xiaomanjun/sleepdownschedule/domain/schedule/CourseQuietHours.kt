package com.xiaomanjun.sleepdownschedule.domain.schedule

import com.xiaomanjun.sleepdownschedule.model.AppState
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.ZoneId

@Serializable
enum class CourseQuietSoundMode { SILENT, VIBRATE }

@Serializable
data class CourseQuietSettings(
    val doNotDisturbEnabled: Boolean = false,
    val soundEnabled: Boolean = false,
    val soundMode: CourseQuietSoundMode = CourseQuietSoundMode.SILENT,
    val advanceMinutes: Int = 0,
    val delayMinutes: Int = 0,
    // Kept for older backups; quiet hours now cover each complete course regardless of its breaks.
    val keepDuringBreakMinutes: Int = 20
) {
    val enabled: Boolean get() = doNotDisturbEnabled || soundEnabled
    fun validate() {
        require(advanceMinutes in 0..30 && delayMinutes in 0..30 && keepDuringBreakMinutes in 0..60) {
            "课程勿扰时间设置无效"
        }
    }
}

data class CourseQuietWindow(val start: Long, val end: Long) {
    fun contains(now: Long): Boolean = now >= start && now < end
}

/** Covers the full course, including its internal breaks, using the actual teaching date and bells. */
fun courseQuietWindows(
    state: AppState, settings: CourseQuietSettings, today: LocalDate, zone: ZoneId
): List<CourseQuietWindow> {
    settings.validate()
    if (!settings.enabled) return emptyList()
    val lessons = (-1L..8L).flatMap { offset ->
        val date = today.plusDays(offset)
        coursesForDate(state, date).mapNotNull { course ->
            val segments = courseTimeSegments(course, state.periods)
            val start = segments.minOfOrNull { it.start } ?: return@mapNotNull null
            val end = segments.maxOf { it.end }
            CourseQuietWindow(
                date.atTime(start).atZone(zone).toInstant().toEpochMilli() - settings.advanceMinutes * 60_000L,
                date.atTime(end).atZone(zone).toInstant().toEpochMilli() + settings.delayMinutes * 60_000L
            )
        }
    }.sortedBy { it.start }
    val merged = mutableListOf<CourseQuietWindow>()
    for (lesson in lessons) {
        if (lesson.end <= lesson.start) continue
        val previous = merged.lastOrNull()
        if (previous != null && lesson.start <= previous.end) {
            merged[merged.lastIndex] = previous.copy(end = maxOf(previous.end, lesson.end))
        } else merged += lesson
    }
    return merged
}
