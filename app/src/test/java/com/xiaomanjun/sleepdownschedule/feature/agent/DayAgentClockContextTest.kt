package com.xiaomanjun.sleepdownschedule.feature.agent

import com.xiaomanjun.sleepdownschedule.model.defaultConfig
import com.xiaomanjun.sleepdownschedule.model.defaultPeriods
import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertTrue
import org.junit.Test

class DayAgentClockContextTest {
    @Test
    fun trustedClockPinsDateTimeZoneAndRelativeDates() {
        val facts = buildDayAgentFacts(
            courses = emptyList(),
            periods = defaultPeriods(),
            config = defaultConfig(),
            date = LocalDate.of(2026, 9, 22),
            weather = null,
            now = LocalDateTime.of(2026, 9, 22, 9, 37, 6)
        ).copy(
            timeZoneId = "Asia/Shanghai",
            utcOffset = "+08:00"
        )

        val context = DayAgentPrompts.runtimeClock(facts)

        assertTrue(context.contains("当前本地日期：2026-09-22"))
        assertTrue(context.contains("当前本地时间：09:37:06"))
        assertTrue(context.contains("星期：星期二"))
        assertTrue(context.contains("时区：Asia/Shanghai（UTC+08:00）"))
        assertTrue(context.contains("“今天”固定指 2026-09-22"))
        assertTrue(context.contains("“明天”固定指 2026-09-23"))
        assertTrue(context.contains("“本周”固定指 2026-09-21 至 2026-09-27"))
    }
}
