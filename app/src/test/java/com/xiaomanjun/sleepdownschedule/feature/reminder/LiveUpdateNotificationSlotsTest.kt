package com.xiaomanjun.sleepdownschedule.feature.reminder

import com.xiaomanjun.sleepdownschedule.LiveUpdateChipTextMode
import org.junit.Assert.*
import org.junit.Test

class LiveUpdateNotificationSlotsTest {
    private val payload = LiveUpdatePayload(
        name = "课程", timeText = "08:00 - 09:40", location = "", showActions = true,
        muteKey = "course-1", muteUntil = "day", chipTextMode = LiveUpdateChipTextMode.COUNTDOWN,
        segments = listOf(LiveUpdateSegment(600_000, 3_300_000), LiveUpdateSegment(3_900_000, 6_600_000)),
        duringClassEnabled = true
    )

    @Test fun minuteTicksReuseSlotAndBothClassTransitionsUseFreshSlots() {
        val before = payload.notificationIdentityAt(0)
        val during = payload.notificationIdentityAt(600_000)
        val gap = payload.notificationIdentityAt(3_300_000)
        val resumed = payload.notificationIdentityAt(3_900_000)
        assertEquals(11, liveUpdateNotificationSlot(emptyList(), before, 11, 12))
        assertEquals(before, payload.notificationIdentityAt(60_000))
        assertEquals(11, liveUpdateNotificationSlot(listOf(11 to before), before, 11, 12))
        assertEquals(12, liveUpdateNotificationSlot(listOf(11 to before), during, 11, 12))
        assertEquals(11, liveUpdateNotificationSlot(listOf(12 to during), gap, 11, 12))
        assertEquals(12, liveUpdateNotificationSlot(listOf(11 to gap), resumed, 11, 12))
    }

    @Test fun serviceHandoffKeepsTheNewSlotWhenOldNotificationStillExists() {
        val old = payload.notificationIdentityAt(0)
        val current = payload.notificationIdentityAt(600_000)
        assertEquals(12, liveUpdateNotificationSlot(listOf(12 to current, 11 to old), current, 11, 12))
    }

    @Test fun legacyNotificationIsReplacedAndHiddenBreaksDoNotRealert() {
        assertEquals(12, liveUpdateNotificationSlot(listOf(11 to null), payload.notificationIdentityAt(0), 11, 12))
        val continuous = payload.copy(breakStatusEnabled = false)
        assertEquals(continuous.notificationIdentityAt(600_000), continuous.notificationIdentityAt(3_300_000))
        assertNotEquals(payload.notificationIdentityAt(0), payload.copy(muteKey = "course-2").notificationIdentityAt(0))
    }
}
