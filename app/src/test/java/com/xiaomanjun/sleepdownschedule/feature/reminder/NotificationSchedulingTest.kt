package com.xiaomanjun.sleepdownschedule.feature.reminder

import com.xiaomanjun.sleepdownschedule.*

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationSchedulingTest {
    @Test
    fun lateNightPreviewRemainsVisibleUntilTomorrowInsteadOfExpiringImmediately() {
        val zone = ZoneId.of("Asia/Shanghai")
        val now = LocalDate.of(2026, 9, 12).atTime(23, 44, 2).atZone(zone)
        val payload = NotificationScheduler.liveUpdatePreviewPayload(
            defaultConfig().copy(notificationLeadMinutes = 30), now
        )
        val start = LocalDate.of(2026, 9, 13).atTime(0, 14).atZone(zone).toInstant().toEpochMilli()
        val end = LocalDate.of(2026, 9, 13).atTime(0, 59).atZone(zone).toInstant().toEpochMilli()

        assertEquals(listOf(LiveUpdateSegment(start, end)), payload.segments)
        assertEquals("00:14 - 00:59", payload.timeText)
        assertEquals(start, payload.expiresAtMillis)
        assertFalse(payload.shouldStop(now.toInstant().toEpochMilli()))
        assertEquals(30, payload.statusAt(now.toInstant().toEpochMilli()).minutesToTransition)
        assertFalse(payload.shouldStop(start - 1))
        assertTrue(payload.shouldStop(start))
    }

    @Test
    fun previewWhoseEndCrossesMidnightStillHasAValidCountdown() {
        val now = ZonedDateTime.parse("2026-09-12T23:40:10+08:00[Asia/Shanghai]")
        val payload = NotificationScheduler.liveUpdatePreviewPayload(
            defaultConfig().copy(notificationLeadMinutes = 10), now
        )
        val segment = payload.segments.single()

        assertEquals("23:50 - 00:35", payload.timeText)
        assertEquals(45 * 60_000L, segment.endAtMillis - segment.startAtMillis)
        assertEquals(10, payload.statusAt(now.toInstant().toEpochMilli()).minutesToTransition)
        assertFalse(payload.shouldStop(now.toInstant().toEpochMilli()))
    }

    @Test
    fun previewAtYearBoundaryClampsZeroLeadAndRetainsFutureExpiry() {
        val now = ZonedDateTime.parse("2026-12-31T23:59:59+08:00[Asia/Shanghai]")
        val payload = NotificationScheduler.liveUpdatePreviewPayload(
            defaultConfig().copy(notificationLeadMinutes = 0), now
        )
        val start = ZonedDateTime.parse("2027-01-01T00:00:00+08:00[Asia/Shanghai]")
            .toInstant().toEpochMilli()

        assertEquals(start, payload.expiresAtMillis)
        assertEquals("00:00 - 00:45", payload.timeText)
        assertFalse(payload.shouldStop(now.toInstant().toEpochMilli()))
        assertEquals(1, payload.statusAt(now.toInstant().toEpochMilli()).minutesToTransition)
    }

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
