package com.xiaomanjun.sleepdownschedule.domain.schedule

import com.xiaomanjun.sleepdownschedule.model.*
import com.xiaomanjun.sleepdownschedule.feature.home.day.weekCourseBuckets
import com.xiaomanjun.sleepdownschedule.feature.home.day.visibleWeekdaysForBuckets
import com.xiaomanjun.sleepdownschedule.feature.agent.buildDayAgentFacts
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class ScheduleAdjustmentsTest {
    @Test fun pickerDisplayDateNormalizesBeforeHolidayPreviewAndPersistence() {
        val entry = scheduleAdjustmentFromInput("2026.10.10", "2026.10.08", "手动校正")
        assertEquals("2026-10-08", entry.sourceDate)
        assertEquals(LocalDate.of(2026, 10, 8), LocalDate.parse(entry.sourceDate))
        assertEquals(listOf(entry), decodeScheduleAdjustments(encodeScheduleAdjustments(listOf(entry))))
    }

    @Test fun changingTargetDateMovesExistingArrangementAndPreservesLabel() {
        val old = ScheduleAdjustment("2026-10-10", "2026-10-08", "国庆")
        val moved = old.copy(date = "2026-10-11")
        assertEquals(listOf(moved), replaceScheduleAdjustment(listOf(old), old.date, moved))
    }

    @Test(expected = IllegalArgumentException::class)
    fun movingToOccupiedDateDoesNotOverwriteAnotherArrangement() {
        val old = ScheduleAdjustment("2026-10-10")
        val occupied = ScheduleAdjustment("2026-10-11")
        replaceScheduleAdjustment(listOf(old, occupied), old.date, old.copy(date = occupied.date))
    }
    private val source = LocalDate.parse("2026-09-07") // week 2, Monday
    private val target = LocalDate.parse("2026-09-19") // week 3, Saturday
    private val regular = CourseEntity(42, "高数", null, "A101", 1, listOf(1), listOf(2), WeekParity.EVEN, null)
    private val config = defaultConfig().copy(autoCurrentWeek = true, termStartDate = "2026-08-31", totalWeeks = 20,
        scheduleAdjustmentsJson = encodeScheduleAdjustments(listOf(
            ScheduleAdjustment(source.toString()), ScheduleAdjustment(target.toString(), source.toString())
        )))
    private val state = AppState(courses = listOf(regular), config = config)

    @Test fun restDayIsEmptyAndMakeupUsesOriginalWeekAndParity() {
        assertTrue(coursesForDate(state, source).isEmpty())
        assertEquals(listOf(regular), coursesForDate(state, target))
        assertTrue(coursesForDate(state, target.minusDays(1)).isEmpty())
    }

    @Test fun weekGridMapsOnlyOccurrenceAndKeepsOriginalCourseUntouched() {
        val bucket = weekCourseBuckets(state.courses, 3, config, target)
        assertEquals(listOf(42L), bucket.byWeekday[6]?.map { it.id })
        assertEquals(1, regular.weekday)
        assertEquals(listOf(2), regular.weeks)
        assertTrue(bucket.weekendHasCourse)
        assertEquals(setOf(6), bucket.makeupWeekdays)
        // The rest day keeps its own cards so the timetable can grey them out in place.
        val restWeek = weekCourseBuckets(state.courses, 2, config, target)
        assertEquals(listOf(42L), restWeek.byWeekday[1]?.map { it.id })
        assertEquals(setOf(1), restWeek.cancelledWeekdays)
    }

    @Test fun cancelledRestDayNeverForcesAnEmptyWeekendIntoView() {
        val restConfig = config.copy(scheduleAdjustmentsJson = encodeScheduleAdjustments(listOf(ScheduleAdjustment(target.toString()))))
        val buckets = weekCourseBuckets(state.courses, 3, restConfig, target)
        assertTrue(buckets.visibleCourses.isEmpty())
        assertEquals(setOf(6), buckets.cancelledWeekdays)
        assertEquals((1..5).toList(), visibleWeekdaysForBuckets(buckets, hideEmptyWeekends = true))
    }

    @Test fun agentUsesSameMakeupCourseOnActualDate() {
        val facts = buildDayAgentFacts(state.courses, state.periods, config, target, null)
        assertEquals(listOf(regular), facts.today.map { it.course })
        assertEquals(target, facts.today.single().date)
        assertEquals(3, facts.currentWeek)
        assertEquals(2, facts.currentTeachingWeek)
        assertEquals(source, facts.currentTeachingDate)
        assertEquals(source, facts.today.single().originalDate)
    }

    @Test fun editingTodayMakeupTargetsOriginalWeekWithoutChangingOtherCourseWeek() {
        val other = regular.copy(id = 43, weekday = 3, weeks = listOf(3), weekParity = WeekParity.ALL)
        val facts = buildDayAgentFacts(listOf(regular, other), state.periods, config, target, null)
        val actions = com.xiaomanjun.sleepdownschedule.feature.agent.parseAgentActions("""
            <agent_actions>[
              {"type":"UPDATE_COURSE","courseId":42,"course":{"name":"修改补课"}},
              {"type":"UPDATE_COURSE","courseId":43,"course":{"name":"修改本周"}}
            ]</agent_actions>
        """.trimIndent(), facts).actions
        assertEquals(listOf(2, 3), actions.map { it.targetWeek })
    }

    @Test fun addingCourseToMakeupSourceUsesSourceWeek() {
        val facts = buildDayAgentFacts(state.courses, state.periods, config, target, null)
        val actions = com.xiaomanjun.sleepdownschedule.feature.agent.parseAgentActions("""
            <agent_actions>[{"type":"ADD_COURSE","course":{"name":"补课新增","weekday":1,"periods":[1]}}]</agent_actions>
        """.trimIndent(), facts).actions
        assertEquals(2, actions.single().targetWeek)
        assertEquals(listOf(2), actions.single().edited?.weeks)
    }

    @Test fun manualWeekSourcesResolveAcrossWeekBoundary() {
        val manual = config.copy(autoCurrentWeek = false, currentWeek = 3)
        assertEquals(2, adjustedTeachingWeekForDate(manual, source, target))
        assertNull(adjustedTeachingWeekForDate(manual, source.minusWeeks(3), target))
    }

    @Test fun sourceAdjustmentNeverChainsIntoAnotherReplacement() {
        val chained = config.copy(scheduleAdjustmentsJson = encodeScheduleAdjustments(listOf(
            ScheduleAdjustment(source.toString(), "2026-09-08"), ScheduleAdjustment(target.toString(), source.toString())
        )))
        assertEquals(source, teachingDateForSchedule(chained, target))
        assertEquals(listOf(regular), coursesForDate(state.copy(config = chained), target))
    }

    @Test fun outsideTermCannotExposeMakeupCourses() {
        val outside = LocalDate.parse("2026-08-30")
        val cfg = config.copy(scheduleAdjustmentsJson = encodeScheduleAdjustments(listOf(ScheduleAdjustment(outside.toString(), source.toString()))))
        assertTrue(coursesForDate(state.copy(config = cfg), outside).isEmpty())
    }

    @Test fun olderConfigAndEmptyBackupValueKeepRegularSchedule() {
        assertTrue(decodeScheduleAdjustments("").isEmpty())
        assertEquals(listOf(regular), coursesForDate(state.copy(config = config.copy(scheduleAdjustmentsJson = "")), source))
    }

    @Test fun adjustmentRoundTripPreservesDatesAndLabels() {
        val original = listOf(ScheduleAdjustment(target.toString(), source.toString(), "国庆补课"), ScheduleAdjustment(source.toString()))
        assertEquals(original.sortedBy { it.date }, decodeScheduleAdjustments(encodeScheduleAdjustments(original)))
    }

    @Test(expected = IllegalArgumentException::class) fun duplicateTargetIsRejected() {
        encodeScheduleAdjustments(listOf(ScheduleAdjustment(source.toString()), ScheduleAdjustment(source.toString(), target.toString())))
    }

    @Test(expected = IllegalArgumentException::class) fun sameDateMappingIsRejected() {
        encodeScheduleAdjustments(listOf(ScheduleAdjustment(source.toString(), source.toString())))
    }
}
