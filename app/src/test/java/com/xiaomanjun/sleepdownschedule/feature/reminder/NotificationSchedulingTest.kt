package com.xiaomanjun.sleepdownschedule.feature.reminder

import com.xiaomanjun.sleepdownschedule.*

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

    @Test
    fun liveUpdatePreferenceChangeInvalidatesScheduledAlarmWindow() {
        val config = defaultConfig().copy(notificationMode = NotificationMode.LIVE_UPDATE)
        val periods = listOf(PeriodEntity(1, "08:00", "08:45"))
        val today = LocalDate.of(2026, 9, 2)
        val before = LiveUpdatePreferencesSnapshot(
            duringClassEnabled = true,
            breakStatusEnabled = true,
            tomorrowReminderEnabled = true,
            tomorrowReminderTime = LocalTime.of(22, 0)
        )

        val first = NotificationScheduler.scheduleSignature(
            courses = emptyList(),
            config = config,
            periods = periods,
            today = today,
            liveUpdatePreferences = before
        )
        val changed = NotificationScheduler.scheduleSignature(
            courses = emptyList(),
            config = config,
            periods = periods,
            today = today,
            liveUpdatePreferences = before.copy(tomorrowReminderTime = LocalTime.of(21, 30))
        )

        assertNotEquals(first, changed)
    }

    @Test
    fun tomorrowReminderWaitsUntilLateClassEndsAndNeverCrossesMidnight() {
        val targetDate = LocalDate.of(2026, 9, 3)
        val zone = ZoneId.of("Asia/Shanghai")
        val lateCourse = CourseEntity(
            id = 7,
            name = "晚课",
            teacher = null,
            location = null,
            weekday = 3,
            periods = listOf(1),
            weeks = listOf(1),
            weekParity = WeekParity.ALL,
            note = null
        )

        val afterClass = NotificationScheduler.tomorrowReminderTriggerEpochMillis(
            targetDate = targetDate,
            reminderTime = LocalTime.of(22, 0),
            previousDayCourses = listOf(lateCourse),
            periods = listOf(PeriodEntity(1, "21:45", "22:30")),
            zone = zone
        )
        assertEquals(
            targetDate.minusDays(1).atTime(22, 35).atZone(zone).toInstant().toEpochMilli(),
            afterClass
        )

        val capped = NotificationScheduler.tomorrowReminderTriggerEpochMillis(
            targetDate = targetDate,
            reminderTime = LocalTime.of(22, 0),
            previousDayCourses = listOf(lateCourse),
            periods = listOf(PeriodEntity(1, "23:30", "23:59")),
            zone = zone
        )
        assertEquals(
            targetDate.atStartOfDay(zone).minusMinutes(5).toInstant().toEpochMilli(),
            capped
        )
    }

    @Test
    fun tomorrowReminderExpiresFiveMinutesAfterItAppears() {
        val trigger = 1_788_272_400_000L

        assertEquals(
            trigger + 5 * 60_000L,
            NotificationScheduler.tomorrowReminderExpiryEpochMillis(trigger)
        )
    }
}
