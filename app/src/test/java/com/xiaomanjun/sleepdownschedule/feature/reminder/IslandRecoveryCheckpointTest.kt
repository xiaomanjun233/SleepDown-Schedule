package com.xiaomanjun.sleepdownschedule.feature.reminder

import com.xiaomanjun.sleepdownschedule.model.LiveUpdateChipTextMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IslandRecoveryCheckpointTest {
    private val start = 1_800_000_000_000L
    private val end = start + 45 * 60_000L
    private val course = LiveUpdatePayload(
        name = "课程",
        timeText = "08:00 - 08:45",
        location = "",
        showActions = true,
        muteKey = "test",
        muteUntil = end.toString(),
        chipTextMode = LiveUpdateChipTextMode.COUNTDOWN,
        segments = listOf(LiveUpdateSegment(start, end)),
        duringClassEnabled = true
    )

    @Test fun missedStartGetsAnInClassRecoveryAndThenBoundedChecks() {
        assertEquals(start + 2 * 60_000L,
            NotificationScheduler.nextIslandRecoveryAt(start - 60_000L, listOf(course)))
        assertEquals(start + 22 * 60_000L,
            NotificationScheduler.nextIslandRecoveryAt(start + 3 * 60_000L, listOf(course)))
        assertEquals(start + 42 * 60_000L,
            NotificationScheduler.nextIslandRecoveryAt(start + 22 * 60_000L, listOf(course)))
        assertNull(NotificationScheduler.nextIslandRecoveryAt(end, listOf(course)))
    }

    @Test fun nextCourseIsChosenAndDisabledDuringClassDoesNotSchedule() {
        val later = course.copy(segments = listOf(LiveUpdateSegment(end + 10 * 60_000L, end + 55 * 60_000L)))
        assertEquals(end + 12 * 60_000L,
            NotificationScheduler.nextIslandRecoveryAt(end, listOf(course, later)))
        assertNull(NotificationScheduler.nextIslandRecoveryAt(start, listOf(course.copy(duringClassEnabled = false))))
    }
}
