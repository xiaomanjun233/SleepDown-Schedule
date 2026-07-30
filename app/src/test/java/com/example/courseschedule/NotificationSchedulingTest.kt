package com.example.courseschedule

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class NotificationSchedulingTest {
    @Test
    fun triggerEpochUsesProvidedSystemZoneAndClampsNegativeLeadTime() {
        val date = LocalDate.of(2026, 9, 2)
        val time = LocalTime.of(8, 0)
        val zone = ZoneId.of("Asia/Shanghai")
        val expected = date
            .atTime(time)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()

        assertEquals(
            expected - 10 * 60_000L,
            NotificationScheduler.notificationTriggerEpochMillis(date, time, 10, zone)
        )
        assertEquals(
            expected,
            NotificationScheduler.notificationTriggerEpochMillis(date, time, -5, zone)
        )
    }

    @Test
    fun changingOnlyLocationInvalidatesScheduledNotificationPayload() {
        val course = CourseEntity(
            id = 42,
            name = "高等数学",
            teacher = "张老师",
            location = "教学楼 A101",
            weekday = 3,
            periods = listOf(1, 2),
            weeks = listOf(1, 2, 3),
            weekParity = WeekParity.ALL,
            note = null
        )
        val periods = listOf(
            PeriodEntity(1, "08:00", "08:45"),
            PeriodEntity(2, "08:55", "09:40")
        )
        val config = defaultConfig()
        val today = LocalDate.of(2026, 9, 2)

        val before = NotificationScheduler.scheduleSignature(
            courses = listOf(course),
            config = config,
            periods = periods,
            today = today
        )
        val after = NotificationScheduler.scheduleSignature(
            courses = listOf(course.copy(location = "教学楼 B202")),
            config = config,
            periods = periods,
            today = today
        )

        assertNotEquals(before, after)
    }

    @Test
    fun dateChangeInvalidatesScheduleEvenWhenCoursesAreUnchanged() {
        val config = defaultConfig()
        val periods = listOf(PeriodEntity(1, "08:00", "08:45"))

        val firstDay = NotificationScheduler.scheduleSignature(
            courses = emptyList(),
            config = config,
            periods = periods,
            today = LocalDate.of(2026, 9, 2)
        )
        val nextDay = NotificationScheduler.scheduleSignature(
            courses = emptyList(),
            config = config,
            periods = periods,
            today = LocalDate.of(2026, 9, 3)
        )

        assertNotEquals(firstDay, nextDay)
    }
}
