package com.xiaomanjun.sleepdownschedule.feature.reminder

import android.app.AlarmManager
import android.app.Application
import com.xiaomanjun.sleepdownschedule.domain.schedule.CourseQuietSettings
import com.xiaomanjun.sleepdownschedule.domain.schedule.CourseQuietWindow
import com.xiaomanjun.sleepdownschedule.domain.schedule.courseQuietWindows
import com.xiaomanjun.sleepdownschedule.model.AppState
import com.xiaomanjun.sleepdownschedule.model.CourseEntity
import com.xiaomanjun.sleepdownschedule.model.NotificationMode
import com.xiaomanjun.sleepdownschedule.model.PeriodEntity
import com.xiaomanjun.sleepdownschedule.model.PeriodSchemeTimeEntity
import com.xiaomanjun.sleepdownschedule.model.TimetableCatalog
import com.xiaomanjun.sleepdownschedule.model.TimetableComboEntity
import com.xiaomanjun.sleepdownschedule.model.TimetableDateRuleEntity
import com.xiaomanjun.sleepdownschedule.model.WeekParity
import com.xiaomanjun.sleepdownschedule.model.defaultConfig
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class TimetableReminderSchedulingTest {
    private val zone = ZoneId.systemDefault()
    private val overrideDate = LocalDate.now(zone).plusDays(1)
    private val baselineDate = overrideDate.plusDays(1)
    private val preferences = LiveUpdatePreferencesSnapshot(true, true, false, LocalTime.of(22, 0))
    private val catalog = TimetableCatalog(
        times = listOf(
            PeriodSchemeTimeEntity(10, 1, "08:00", "08:45"),
            PeriodSchemeTimeEntity(20, 1, "09:00", "09:45"),
            PeriodSchemeTimeEntity(30, 1, "11:00", "11:45"),
        ),
        combos = listOf(TimetableComboEntity(1, "组合", 10)),
        rules = listOf(TimetableDateRuleEntity(1, 0, 20, overrideDate.toString(), overrideDate.toString())),
    )
    private val state = AppState(
        config = defaultConfig().copy(
            autoCurrentWeek = true, termStartDate = overrideDate.minusDays(7).toString(),
            activeTimetableComboId = 1, notificationsEnabled = true, notificationLeadMinutes = 10,
        ),
        periods = listOf(PeriodEntity(1, "08:00", "08:45")),
        timetableCatalog = catalog,
        courses = listOf(
            lesson(1, "日期方案", overrideDate),
            lesson(2, "基准方案", baselineDate),
            lesson(3, "独立作息", overrideDate).copy(independentPeriodSchemeId = 30),
        ),
    )

    @Test fun remindersAndLiveUpdateBoundariesUseEachDatesActualClock() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val alarm = shadowOf(context.getSystemService(AlarmManager::class.java))
        for (mode in listOf(NotificationMode.STANDARD, NotificationMode.LIVE_UPDATE)) {
            NotificationScheduler.scheduleToday(context, state.copy(config = state.config.copy(notificationMode = mode)), preferences)
            val triggers = alarm.scheduledAlarms.map { it.triggerAtTime }.toSet()
            for ((date, hour) in listOf(overrideDate to 9, baselineDate to 8, overrideDate to 11)) {
                val start = date.atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()
                assertTrue("pre-class alarm for $date $hour, mode $mode", start - 600_000L in triggers)
                if (mode == NotificationMode.LIVE_UPDATE) {
                    assertTrue("live start for $date $hour", start in triggers)
                    assertTrue("live end for $date $hour", start + 45 * 60_000L in triggers)
                }
                val payloads = NotificationScheduler.coursePayloadsForDate(state, date, preferences, zone)
                assertEquals(start, NotificationScheduler.selectImmediateCoursePayload(payloads, start + 60_000, 10)?.startAtMillis())
            }
        }
    }

    @Test fun automaticQuietWindowsFollowDateRulesAndTheIndependentScheme() {
        val windows = courseQuietWindows(state, CourseQuietSettings(soundEnabled = true), overrideDate, zone)
        fun window(date: LocalDate, hour: Int): CourseQuietWindow {
            val start = date.atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()
            return CourseQuietWindow(start, start + 45 * 60_000L)
        }
        val firstTwoDays = windows.filter { it.start < baselineDate.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() }
        assertEquals(listOf(window(overrideDate, 9), window(overrideDate, 11), window(baselineDate, 8)), firstTwoDays)
    }

    @Test fun signaturesReactToDateRulesAndReferencedBells() {
        fun signature(value: TimetableCatalog) =
            NotificationScheduler.scheduleSignature(state.copy(timetableCatalog = value), overrideDate)
        val original = signature(catalog)
        assertNotEquals(original, signature(catalog.copy(rules = catalog.rules.map {
            it.copy(endDate = baselineDate.toString())
        })))
        for (id in listOf(10L, 20L, 30L)) {
            assertNotEquals(original, signature(catalog.copy(times = catalog.times.map {
                if (it.schemeId == id) it.copy(endTime = it.endTime.replace(":45", ":40")) else it
            })))
        }
        assertEquals(original, signature(catalog.copy(times = catalog.times + PeriodSchemeTimeEntity(99, 1, "07:00", "07:45"))))
    }

    private fun lesson(id: Long, name: String, date: LocalDate) = CourseEntity(
        id = id, name = name, teacher = null, location = null, weekday = date.dayOfWeek.value,
        periods = listOf(1), weeks = (1..4).toList(), weekParity = WeekParity.ALL, note = null,
    )
}
