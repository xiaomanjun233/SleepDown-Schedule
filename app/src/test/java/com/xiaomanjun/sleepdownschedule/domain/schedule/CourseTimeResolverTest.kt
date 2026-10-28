package com.xiaomanjun.sleepdownschedule.domain.schedule

import com.xiaomanjun.sleepdownschedule.model.*
import com.xiaomanjun.sleepdownschedule.data.local.toStoredTiming
import com.xiaomanjun.sleepdownschedule.data.local.toCourse
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class CourseTimeResolverTest {
    private val grid = listOf(PeriodEntity(1,"08:00","08:45"), PeriodEntity(2,"08:55","09:40"))
    private val config = defaultConfig().copy(activePeriodSchemeId=1)
    private val course = CourseEntity(name="课程",teacher=null,location="B楼",weekday=1,periods=listOf(1,2),weeks=(1..16).toList(),weekParity=WeekParity.ALL,note=null)
    private val catalog = TimetableCatalog(times=listOf(
        PeriodSchemeTimeEntity(1,1,"08:00","08:45"),PeriodSchemeTimeEntity(1,2,"08:55","09:40"),
        PeriodSchemeTimeEntity(2,1,"08:20","09:05"),PeriodSchemeTimeEntity(2,2,"09:15","10:00")),
        combos=listOf(TimetableComboEntity(10,"夏季",1)),
        rules=listOf(TimetableDateRuleEntity(10,0,2,"2026-05-01","2026-09-30")))

    @Test fun independentBuildingBellsDoNotChangeGridNumbersOrStoredClocks() {
        val source = course.copy(independentPeriodSchemeId=2)
        val result = projectCourseArrangement(source,config,grid,catalog)
        assertEquals(source.periods,result.periods)
        assertNull(result.customStartTime)
        assertEquals("08:20 - 10:00",courseTimeLabel(result,grid))
        assertEquals(source,result.arrangementForWrite())
        assertTrue(result.hasCustomTime())
    }

    @Test fun actualDateSelectsSeasonalBellsAndTheDisplayBaseStaysFixed() {
        val seasonal = config.copy(activeTimetableComboId=10)
        assertEquals("08:20",resolveCourseOccurrence(course,seasonal,grid,catalog,LocalDate.parse("2026-09-30")).segments.first().start.toString())
        assertEquals("08:00",resolveCourseOccurrence(course,seasonal,grid,catalog,LocalDate.parse("2026-10-01")).segments.first().start.toString())
        assertEquals("08:00",grid.first().startTime)
        val exact = course.copy(customStartTime="08:10",customEndTime="09:50")
        assertEquals("08:10",resolveCourseOccurrence(exact,seasonal,grid,catalog,LocalDate.parse("2026-09-30")).segments.single().start.toString())
    }

    @Test fun normalizedStoragePreservesExactIntentAndExpandsParityToEffectiveWeeks() {
        val source = course.copy(weekParity=WeekParity.ODD,customStartTime="08:00",customEndTime="09:40")
        val rows = source.toStoredTiming()
        assertEquals("CUSTOM",rows.course.timingMode)
        val restored = rows.toCourse()
        assertEquals((1..16).filter { it%2==1 },restored.weeks)
        assertEquals("08:00",restored.customStartTime)
        val changed = listOf(PeriodEntity(1,"09:00","09:45"),PeriodEntity(2,"09:55","10:40"))
        assertEquals("08:00 - 09:40",courseTimeLabel(projectCourseArrangement(restored,config,changed),changed))
    }

    @Test fun partialLessonOverridesFollowOnlyUnchangedOrdinaryBells() {
        val source = course.copy(customPeriodTimes="2,09:15-10:00")
        val restored = source.toStoredTiming().toCourse()
        assertEquals("FOLLOW_TABLE",source.toStoredTiming().course.timingMode)
        val shifted = listOf(PeriodEntity(1,"08:10","08:55"),grid[1])
        val result = resolveCourseOccurrence(restored,config,shifted)
        assertEquals(listOf("08:10","09:15"),result.segments.map { it.start.toString() })
    }

    @Test fun missingTimetableReferencesAreExplicitAndNeverUseTheDefaultBells() {
        val result = resolveCourseOccurrence(course.copy(independentPeriodSchemeId=999),config,grid,catalog)
        assertTrue(result.segments.isEmpty())
        assertNotNull(result.reviewReason)
    }
}
