package com.xiaomanjun.sleepdownschedule.feature.reminder

import com.xiaomanjun.sleepdownschedule.LiveUpdateChipTextMode
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveUpdatePayloadTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val date = LocalDate.of(2026, 9, 2)
    private val firstStart = epoch(8, 0)
    private val firstEnd = epoch(8, 45)
    private val secondStart = epoch(8, 55)
    private val secondEnd = epoch(9, 40)

    private fun epoch(hour: Int, minute: Int): Long =
        date.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()

    private fun coursePayload(breakStatusEnabled: Boolean = true) = LiveUpdatePayload(
        name = "高等数学",
        timeText = "08:00 - 09:40",
        location = "A101",
        showActions = true,
        muteKey = "course",
        muteUntil = secondEnd.toString(),
        chipTextMode = LiveUpdateChipTextMode.COUNTDOWN,
        segments = listOf(
            LiveUpdateSegment(firstStart, firstEnd),
            LiveUpdateSegment(secondStart, secondEnd)
        ),
        duringClassEnabled = true,
        breakStatusEnabled = breakStatusEnabled,
        expiresAtMillis = secondEnd
    )

    @Test
    fun refreshAlarmsCoverEveryClassBoundaryEvenWhenBreakDisplayIsDisabled() {
        val expected = listOf(firstStart, firstEnd, secondStart, secondEnd)
        assertEquals(expected, coursePayload().refreshBoundaries())
        assertEquals(expected, coursePayload(breakStatusEnabled = false).refreshBoundaries())
    }

    @Test
    fun preparationOnlyReminderHasAnAlarmToStopAtClassStart() {
        val payload = coursePayload().copy(duringClassEnabled = false)
        assertEquals(listOf(firstStart, secondEnd), payload.refreshBoundaries())
        assertFalse(payload.shouldStop(firstStart - 1))
        assertTrue(payload.shouldStop(firstStart))
    }

    @Test
    fun preparationRefreshesWhenTheDisplayedMinuteChanges() {
        assertEquals(epoch(7, 51), coursePayload().nextRefreshAtMillis(epoch(7, 50)))
        val payload = coursePayload().copy(segments = listOf(
            LiveUpdateSegment(firstStart + 30_000L, firstEnd)
        ))
        val before = epoch(7, 50)
        val next = requireNotNull(payload.nextRefreshAtMillis(before))
        assertEquals(before + 30_000L, next)
        assertEquals(11, payload.statusAt(next - 1L).minutesToTransition)
        assertEquals(10, payload.statusAt(next).minutesToTransition)
        // A late callback computes from current time instead of replaying missed minutes.
        assertEquals(before + 90_000L, payload.nextRefreshAtMillis(next + 40_000L))
    }

    @Test
    fun secondPrecisionClassAndBreakBoundariesPreemptTheProgressTick() {
        val start = firstStart + 30_000L
        val end = firstEnd + 15_000L
        val resume = firstEnd + 45_000L
        val payload = coursePayload().copy(segments = listOf(
            LiveUpdateSegment(start, end),
            LiveUpdateSegment(resume, secondEnd)
        ))
        assertEquals(start, payload.nextRefreshAtMillis(firstStart))
        assertEquals(epoch(8, 1) + 15_000L, payload.nextRefreshAtMillis(start))
        assertEquals(end, payload.nextRefreshAtMillis(firstEnd))
        assertEquals(LiveUpdatePhase.BREAK, payload.statusAt(end).phase)
        assertEquals(resume, payload.nextRefreshAtMillis(end))
        assertEquals(LiveUpdatePhase.IN_CLASS, payload.statusAt(resume).phase)
    }

    @Test
    fun expiryPreemptsRefreshAndFinishedPayloadHasNoFurtherTick() {
        val expiry = epoch(8, 20) + 15_000L
        val payload = coursePayload().copy(expiresAtMillis = expiry)
        assertEquals(expiry, payload.nextRefreshAtMillis(expiry - 10_000L))
        assertNull(payload.nextRefreshAtMillis(expiry))
        assertNull(coursePayload().copy(duringClassEnabled = false).nextRefreshAtMillis(firstStart))
        assertNull(coursePayload().nextRefreshAtMillis(secondEnd))
    }

    @Test
    fun courseStateMovesFromPreparationThroughClassBreakAndFinish() {
        val payload = coursePayload()

        val before = payload.statusAt(epoch(7, 50))
        assertEquals(LiveUpdatePhase.BEFORE_CLASS, before.phase)
        assertEquals(10, before.minutesToTransition)
        assertEquals(firstStart, before.nextTransitionAtMillis)

        val firstClass = payload.statusAt(epoch(8, 20))
        assertEquals(LiveUpdatePhase.IN_CLASS, firstClass.phase)
        assertEquals("08:45课间 · 还有25分钟", firstClass.detailText)
        assertEquals(25, firstClass.minutesToTransition)
        assertEquals(firstEnd, firstClass.nextTransitionAtMillis)

        val courseBreak = payload.statusAt(epoch(8, 50))
        assertEquals(LiveUpdatePhase.BREAK, courseBreak.phase)
        assertEquals("08:55上课 · 还有5分钟", courseBreak.detailText)
        assertEquals(5, courseBreak.minutesToTransition)
        assertEquals(secondStart, courseBreak.nextTransitionAtMillis)

        val secondClass = payload.statusAt(epoch(9, 0))
        assertEquals(LiveUpdatePhase.IN_CLASS, secondClass.phase)
        assertEquals("09:40下课 · 还有40分钟", secondClass.detailText)
        assertEquals(40, secondClass.minutesToTransition)
        assertEquals(secondEnd, secondClass.nextTransitionAtMillis)

        assertEquals(LiveUpdatePhase.FINISHED, payload.statusAt(secondEnd).phase)
        assertFalse(payload.shouldStop(secondEnd - 1))
        assertTrue(payload.shouldStop(secondEnd))
    }

    @Test
    fun disablingBreakStatusKeepsCountdownPointedAtFinalDismissal() {
        val payload = coursePayload(breakStatusEnabled = false)

        val firstClass = payload.statusAt(epoch(8, 20))
        assertEquals(LiveUpdatePhase.IN_CLASS, firstClass.phase)
        assertEquals(80, firstClass.minutesToTransition)
        assertEquals(secondEnd, firstClass.nextTransitionAtMillis)

        val betweenPeriods = payload.statusAt(epoch(8, 50))
        assertEquals(LiveUpdatePhase.IN_CLASS, betweenPeriods.phase)
        assertEquals(50, betweenPeriods.minutesToTransition)
        assertEquals(secondEnd, betweenPeriods.nextTransitionAtMillis)
    }

    @Test
    fun progressResetsAtClassAndBreakBoundaries() {
        val payload = coursePayload()

        assertNull(payload.statusAt(firstStart - 1).progressPercent)
        assertEquals(0, payload.statusAt(firstStart).progressPercent)
        assertEquals(50, payload.statusAt(firstStart + (firstEnd - firstStart) / 2).progressPercent)
        assertEquals(99, payload.statusAt(firstEnd - 1).progressPercent)
        assertEquals(0, payload.statusAt(firstEnd).progressPercent)
        assertEquals(50, payload.statusAt(epoch(8, 50)).progressPercent)
        assertEquals(99, payload.statusAt(secondStart - 1).progressPercent)
        assertEquals(0, payload.statusAt(secondStart).progressPercent)
        assertEquals(50, payload.statusAt(secondStart + (secondEnd - secondStart) / 2).progressPercent)
        assertNull(payload.statusAt(secondEnd).progressPercent)
    }

    @Test
    fun progressCoversWholeCourseWhenBreakStatusIsDisabled() {
        val payload = coursePayload(breakStatusEnabled = false)

        assertEquals(0, payload.statusAt(firstStart).progressPercent)
        assertEquals(45, payload.statusAt(firstEnd).progressPercent)
        assertEquals(50, payload.statusAt(epoch(8, 50)).progressPercent)
        assertEquals(55, payload.statusAt(secondStart).progressPercent)
        assertEquals(99, payload.statusAt(secondEnd - 1).progressPercent)
    }

    @Test
    fun progressUsesValidSortedSegmentsAndHandlesAdjacentClasses() {
        val payload = coursePayload().copy(segments = listOf(
            LiveUpdateSegment(firstEnd, secondEnd),
            LiveUpdateSegment(secondStart, secondStart),
            LiveUpdateSegment(firstStart, firstEnd)
        ))

        val status = payload.statusAt(firstEnd)
        assertEquals(LiveUpdatePhase.IN_CLASS, status.phase)
        assertEquals(0, status.progressPercent)
        assertNull(payload.copy(segments = emptyList()).statusAt(firstStart).progressPercent)
    }

    @Test
    fun tomorrowReminderRemainsVisibleOnlyUntilItsExpiry() {
        val expiry = epoch(23, 59)
        val payload = LiveUpdatePayload(
            kind = LiveUpdateKind.TOMORROW,
            name = "明天有2门课",
            timeText = "08:00开始",
            location = "第一节：高等数学 · A101",
            showActions = true,
            muteKey = "tomorrow:${date.plusDays(1)}",
            muteUntil = expiry.toString(),
            chipTextMode = LiveUpdateChipTextMode.NORMAL,
            expiresAtMillis = expiry,
            tomorrowCourseCount = 2
        )

        assertEquals(LiveUpdatePhase.TOMORROW, payload.statusAt(epoch(22, 0)).phase)
        assertNull(payload.statusAt(epoch(22, 0)).progressPercent)
        assertFalse(payload.shouldStop(expiry - 1))
        assertTrue(payload.shouldStop(expiry))
    }
}
