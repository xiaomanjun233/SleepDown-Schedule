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

/** Uses actual lesson segments, teaching-date adjustments and the active schedule's term bounds. */
fun courseQuietWindows(
    state: AppState, settings: CourseQuietSettings, today: LocalDate, zone: ZoneId
): List<CourseQuietWindow> {
    settings.validate()
    if (!settings.enabled) return emptyList()
    val lessons = (-1L..8L).flatMap { offset ->
        val date = today.plusDays(offset)
        coursesForDate(state, date).flatMap { course ->
            courseTimeSegments(course, state.periods).map { segment ->
                CourseQuietWindow(date.atTime(segment.start).atZone(zone).toInstant().toEpochMilli(),
                    date.atTime(segment.end).atZone(zone).toInstant().toEpochMilli())
            }
        }
    }.sortedBy { it.start }
    val merged = mutableListOf<CourseQuietWindow>()
    val mergeGap = maxOf(settings.keepDuringBreakMinutes,
        settings.advanceMinutes + settings.delayMinutes) * 60_000L
    for (lesson in lessons) {
        if (lesson.end <= lesson.start) continue
        val previous = merged.lastOrNull()
        // Merge raw lessons first. Offsets can bridge an overlap, but never inflate the break allowance.
        if (previous != null && lesson.start <= previous.end + mergeGap) {
            merged[merged.lastIndex] = previous.copy(end = maxOf(previous.end, lesson.end))
        } else merged += lesson
    }
    return merged.map { CourseQuietWindow(it.start - settings.advanceMinutes * 60_000L,
        it.end + settings.delayMinutes * 60_000L) }
}
