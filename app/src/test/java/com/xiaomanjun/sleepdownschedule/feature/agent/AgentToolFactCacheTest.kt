package com.xiaomanjun.sleepdownschedule.feature.agent

import com.xiaomanjun.sleepdownschedule.model.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class AgentToolFactCacheTest {
    private val facts = buildDayAgentFacts(emptyList(), defaultPeriods(), defaultConfig(),
        LocalDate.of(2026, 9, 7), null, now = LocalDateTime.of(2026, 9, 7, 9, 0))
    private val call = AgentToolCall("first", AgentToolName.GET_WEEK_SCHEDULE)
    private val result = AgentToolResult("first", call.name, true, "当天没有课程")

    @Test fun overviewReadIncludesEditableIdentityAndOccurrenceScope() {
        val course = CourseEntity(
            id = 42L, name = "专业英语", teacher = "原教师", location = "原教室",
            weekday = 1, periods = listOf(1), weeks = listOf(1, 2),
            weekParity = WeekParity.ALL, note = "保留备注", scheduleId = 1
        )
        val current = facts.copy(today = listOf(AgentCourseSlot(
            course, facts.date, LocalTime.of(8, 0), LocalTime.of(8, 45),
            originalDate = facts.date.minusDays(7), teachingWeek = 1
        )), semesterCourses = listOf(course))
        val overview = AgentToolCall("overview", AgentToolName.GET_CURRENT_OVERVIEW)
        val first = executeAgentReadTools(listOf(overview), current).single()
        assertTrue(first.content.contains("ID=42"))
        assertTrue(first.content.contains("教师=原教师"))
        assertTrue(first.content.contains("地点=原教室"))
        assertTrue(first.content.contains("teachingWeek=1"))
        assertTrue(first.content.contains("originalDate=2026-08-31"))
        val later = current.copy(today = emptyList(), now = current.now.plusMinutes(1))
        val next = executeAgentReadTools(listOf(overview), later).single()
        assertTrue(next.content.contains("今日=无课"))
        assertFalse(next.content.contains("ID=42"))
    }

    @Test fun followUpReusesUnchangedFactsButNotOtherSchedulesOrVersions() {
        val cache = AgentToolFactCache()
        cache.put(facts, call, result, 1_000)
        assertEquals(result, cache.read(facts, 2_000)[call.cacheKey()])
        assertTrue(cache.read(facts.copy(scheduleId = 2), 2_000).isEmpty())
        assertTrue(cache.read(facts.copy(sourceHash = "modified"), 2_000).isEmpty())
        assertTrue(cache.read(facts, 3_000).isEmpty())
    }

    @Test fun expiredOrFailedCallsNeverSuppressAnotherRead() {
        val cache = AgentToolFactCache()
        cache.put(facts, call, result.copy(success = false), 1_000)
        assertTrue(cache.read(facts, 2_000).isEmpty())
        cache.put(facts, call, result, 1_000)
        assertTrue(cache.read(facts, 301_001).isEmpty())
        cache.put(facts, call, result, 1_000)
        assertTrue(cache.read(facts, 999).isEmpty())
    }

    @Test fun clockDependentOverviewExpiresOnMinuteChangeWithoutDroppingWeekFacts() {
        val cache = AgentToolFactCache()
        val overview = AgentToolCall("now", AgentToolName.GET_CURRENT_OVERVIEW)
        cache.put(facts, call, result, 1_000)
        cache.put(facts, overview, result.copy(name = overview.name), 1_000)
        val next = cache.read(facts.copy(now = facts.now.plusMinutes(1)), 2_000)
        assertEquals(setOf(call.cacheKey()), next.keys)
    }

    @Test fun changedPeriodSchemeInvalidatesCachedTimes() {
        val cache = AgentToolFactCache()
        cache.put(facts, call, result, 1_000)
        assertTrue(cache.read(facts.copy(activePeriodSchemeId = 9L), 2_000).isEmpty())
    }

    @Test fun changedTimeZoneInvalidatesCachedFacts() {
        val cache = AgentToolFactCache()
        cache.put(facts, call, result, 1_000)
        assertTrue(
            cache.read(
                facts.copy(timeZoneId = "America/Los_Angeles", utcOffset = "-07:00"),
                2_000
            ).isEmpty()
        )
    }

    @Test fun memoryWritesAreNeverReplayedFromCache() {
        val cache = AgentToolFactCache()
        val memory = AgentToolCall("memory", AgentToolName.UPDATE_MEMORY)
        cache.put(facts, memory, result.copy(name = memory.name), 1_000)
        assertTrue(cache.read(facts, 2_000).isEmpty())
    }

    @Test fun cacheIsBoundedAndIncludesSearchArgumentsAsData() {
        val cache = AgentToolFactCache()
        repeat(20) { index ->
            val search = AgentToolCall("s$index", AgentToolName.SEARCH_COURSES, mapOf("query" to "课程$index"))
            cache.put(facts, search, result.copy(name = search.name), 1_000)
        }
        val entries = cache.read(facts, 2_000)
        assertEquals(12, entries.size)
        val payload = agentCachedFactsMessage(facts, entries)
        assertTrue(payload.contains("query=课程19"))
        assertTrue(payload.contains("untrusted_local_data"))
    }

    @Test fun directAnswerRequiresContentAndCannotLeakToolSentinel() {
        assertEquals("今天没有课程。", usableAgentAnswer(" 今天没有课程。 "))
        assertNull(usableAgentAnswer("FINAL_ANSWER_READY"))
        assertNull(usableAgentAnswer(" "))
    }
}
