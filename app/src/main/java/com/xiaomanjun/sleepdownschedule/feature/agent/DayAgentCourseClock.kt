package com.xiaomanjun.sleepdownschedule.feature.agent

import com.xiaomanjun.sleepdownschedule.domain.schedule.CoursePeriodTime
import com.xiaomanjun.sleepdownschedule.domain.schedule.courseTimeSegments
import com.xiaomanjun.sleepdownschedule.domain.schedule.hasCustomTime
import com.xiaomanjun.sleepdownschedule.model.PeriodEntity
import java.time.Duration
import java.time.LocalDateTime

internal enum class AgentLessonPhase { BEFORE, IN_CLASS, BREAK }

/** The assistant switches from a plain countdown to the read-bar layout at ten minutes. */
internal const val AgentCountdownWindowSeconds = 600L

internal data class AgentLessonFocus(
    val slot: AgentCourseSlot,
    val phase: AgentLessonPhase,
    val segment: CoursePeriodTime,
    val phaseStartAt: LocalDateTime,
    val transitionAt: LocalDateTime,
    val finalLesson: Boolean
) {
    fun secondsRemainingAt(now: LocalDateTime): Long =
        (Duration.between(now, transitionAt).toMillis().coerceAtLeast(0L) + 999L) / 1000L

    /** Elapsed fraction of the current lesson or break, for the whole phase. */
    fun progressAt(now: LocalDateTime): Float {
        val total = Duration.between(phaseStartAt, transitionAt).toMillis().toFloat()
        if (total <= 0f) return 0f
        val elapsed = Duration.between(phaseStartAt, now).toMillis().toFloat()
        return (elapsed / total).coerceIn(0f, 1f)
    }

    val transitionKey: String
        get() = "${slot.course.scheduleId}:${slot.course.id}:${slot.date}:$phase:$transitionAt"
}

/** Resolve the same individual lessons and intervening breaks as the live activity. */
internal fun agentLessonFocus(
    slots: List<AgentCourseSlot>,
    periods: List<PeriodEntity>,
    now: LocalDateTime
): AgentLessonFocus? = slots.mapNotNull { slot ->
    val segments = courseTimeSegments(slot.course, periods).ifEmpty {
        listOf(CoursePeriodTime(slot.course.periods.minOrNull() ?: 0, slot.start, slot.end))
    }
    val positionByPeriod = periods.sortedBy(PeriodEntity::periodIndex)
        .map(PeriodEntity::periodIndex).withIndex().associate { it.value to it.index }
    segments.forEachIndexed { index, segment ->
        val start = slot.date.atTime(segment.start)
        val end = slot.date.atTime(segment.end)
        if (now.isBefore(start)) {
            val previous = segments.getOrNull(index - 1)
            val continuous = previous != null && (slot.course.hasCustomTime() ||
                positionByPeriod[previous.index]?.plus(1) == positionByPeriod[segment.index])
            val phase = if (continuous) AgentLessonPhase.BREAK else AgentLessonPhase.BEFORE
            val phaseStart = if (continuous) slot.date.atTime(previous!!.end) else start
            return@mapNotNull AgentLessonFocus(slot, phase, segment, phaseStart, start, false)
        }
        if (now.isBefore(end)) {
            return@mapNotNull AgentLessonFocus(slot, AgentLessonPhase.IN_CLASS, segment, start, end,
                index == segments.lastIndex)
        }
    }
    null
}.minWithOrNull(compareBy<AgentLessonFocus> {
    if (it.phase == AgentLessonPhase.IN_CLASS) 0 else 1
}.thenBy { it.transitionAt }.thenBy {
    if (it.phase == AgentLessonPhase.BREAK) 0 else 1
}.thenBy { it.slot.course.id })

internal fun agentCountdownText(seconds: Long): String {
    val safe = seconds.coerceAtLeast(0L)
    return "%02d:%02d".format(safe / 60L, safe % 60L)
}

internal fun agentNeedsSecondTick(
    slots: List<AgentCourseSlot>,
    periods: List<PeriodEntity>,
    now: LocalDateTime
): Boolean {
    val focus = agentLessonFocus(slots, periods, now) ?: return false
    return focus.phase != AgentLessonPhase.BEFORE ||
        focus.secondsRemainingAt(now) <= AgentCountdownWindowSeconds
}
