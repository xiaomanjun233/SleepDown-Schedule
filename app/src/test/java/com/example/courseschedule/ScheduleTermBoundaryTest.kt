package com.example.courseschedule

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleTermBoundaryTest {
    private val config = defaultConfig().copy(
        autoCurrentWeek = true,
        termStartDate = "2026-09-02",
        totalWeeks = 2
    )

    @Test
    fun datesBeforeActualOpeningDayDoNotBecomeWeekOne() {
        assertNull(scheduleWeekForDateOrNull(config, LocalDate.of(2026, 9, 1)))
        assertEquals(1, scheduleWeekForDateOrNull(config, LocalDate.of(2026, 9, 2)))
    }

    @Test
    fun finalTeachingWeekEndsOnItsSunday() {
        assertEquals(LocalDate.of(2026, 9, 13), scheduleTermEndDate(config))
        assertEquals(2, scheduleWeekForDateOrNull(config, LocalDate.of(2026, 9, 13)))
        assertNull(scheduleWeekForDateOrNull(config, LocalDate.of(2026, 9, 14)))
        assertFalse(isAfterScheduleTerm(config, LocalDate.of(2026, 9, 13)))
        assertTrue(isAfterScheduleTerm(config, LocalDate.of(2026, 9, 14)))
    }

    @Test
    fun automaticModeKeepsManualFallbackBeforeTheTerm() {
        val storedWeek = resolveScheduleCurrentWeek(
            baseConfig = config,
            totalWeeks = 2,
            manualCurrentWeek = 2,
            termStartDate = "2026-09-02",
            autoCurrentWeek = true,
            today = LocalDate.of(2026, 9, 1)
        )
        assertEquals(2, storedWeek)
        assertEquals("暂未开学", scheduleTermStatusLabel(config, LocalDate.of(2026, 9, 1)))
        assertEquals("学期已结束", scheduleTermStatusLabel(config, LocalDate.of(2026, 9, 14)))
    }

    @Test
    fun periodTimeSettingUpdatesOnlyAValidNonOverlappingPeriod() {
        val periods = listOf(
            PeriodEntity(1, "08:00", "08:45"),
            PeriodEntity(2, "08:55", "09:40")
        )
        val updated = AgentSettingRegistry.applyPeriodTime(periods, "PERIOD_1_TIME", "08:05-08:50")
        assertEquals("08:05", updated?.first()?.startTime)
        assertEquals("08:50", updated?.first()?.endTime)
        assertNull(AgentSettingRegistry.applyPeriodTime(periods, "PERIOD_1_TIME", "08:30-09:00"))
    }
}
