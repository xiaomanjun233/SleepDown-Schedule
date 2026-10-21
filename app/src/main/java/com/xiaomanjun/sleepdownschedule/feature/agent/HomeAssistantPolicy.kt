package com.xiaomanjun.sleepdownschedule.feature.agent

import java.time.Duration
import java.time.LocalDateTime

/** The distance is in a single coordinate space (px in UI, dp in rule tests). */
internal fun assistantPullDistance(distance: Float, limit: Float): Float {
    if (distance <= 0f || limit <= 0f) return 0f
    return limit * distance / (distance + limit * 1.2f)
}

internal data class AssistantReminder(
    val slot: AgentCourseSlot,
    val minutesBefore: Int,
    val at: LocalDateTime
) {
    val key: String = "${slot.course.scheduleId}:${slot.course.id}:${slot.date}:${slot.start}:$minutesBefore"
}

internal fun assistantReminders(slots: List<AgentCourseSlot>): List<AssistantReminder> =
    slots.flatMap { slot ->
        listOf(30, 15, 0).map { minutes ->
            AssistantReminder(slot, minutes, slot.date.atTime(slot.start).minusMinutes(minutes.toLong()))
        }
    }.distinctBy { it.key }.sortedBy { it.at }

/** A resumed screen can show only a fresh boundary, never replay a backlog. */
internal fun dueAssistantReminders(
    events: List<AssistantReminder>,
    now: LocalDateTime,
    consumed: Set<String>
): List<AssistantReminder> = events.filter {
    it.key !in consumed && !now.isBefore(it.at) && Duration.between(it.at, now).seconds < 60
}.sortedWith(compareBy<AssistantReminder> { it.minutesBefore }.thenBy { it.slot.start }.thenBy { it.slot.course.id })
