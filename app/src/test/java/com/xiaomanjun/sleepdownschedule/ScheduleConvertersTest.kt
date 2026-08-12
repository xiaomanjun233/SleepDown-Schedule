package com.xiaomanjun.sleepdownschedule

import org.junit.Assert.assertEquals
import org.junit.Test

class ScheduleConvertersTest {
    private val converters = ScheduleConverters()

    @Test
    fun unknownWeekParityFallsBackToAllWeeks() {
        assertEquals(WeekParity.ALL, converters.stringToParity("REMOVED_FUTURE_VALUE"))
    }

    @Test
    fun unknownNotificationModeFallsBackToStandardNotification() {
        assertEquals(
            NotificationMode.STANDARD,
            converters.stringToNotificationMode("REMOVED_FUTURE_VALUE")
        )
    }

    @Test
    fun knownPersistentEnumsStillRoundTrip() {
        WeekParity.entries.forEach { value ->
            assertEquals(value, converters.stringToParity(converters.parityToString(value)))
        }
        NotificationMode.entries.forEach { value ->
            assertEquals(
                value,
                converters.stringToNotificationMode(converters.notificationModeToString(value))
            )
        }
    }
}
