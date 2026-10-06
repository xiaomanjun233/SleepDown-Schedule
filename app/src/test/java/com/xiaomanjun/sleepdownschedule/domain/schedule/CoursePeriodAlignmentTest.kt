package com.xiaomanjun.sleepdownschedule.domain.schedule

import com.xiaomanjun.sleepdownschedule.model.*
import com.xiaomanjun.sleepdownschedule.defaultConfig
import com.xiaomanjun.sleepdownschedule.feature.course.editor.courseEditorDraft
import com.xiaomanjun.sleepdownschedule.feature.course.editor.toCourses
import com.xiaomanjun.sleepdownschedule.feature.home.day.weekCourseBuckets
import com.xiaomanjun.sleepdownschedule.feature.widget.providers.MiuixTodayWidgetRenderer
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class CoursePeriodAlignmentTest {
    private val config = defaultConfig().copy(currentWeek = 1, autoCurrentWeek = false)
    private val bells = listOf(PeriodEntity(1, "08:00", "08:45"), PeriodEntity(2, "08:55", "09:40"),
        PeriodEntity(3, "10:00", "10:45"), PeriodEntity(4, "10:55", "11:40"))
    private fun course(indices: List<Int> = listOf(3, 4), start: String? = null, end: String? = null,
        custom: String? = null) = captureOriginalPeriodTimes(CourseEntity(7, "课程", null, null, 1,
        indices, listOf(1, 2), WeekParity.ALL, null, start, end, customPeriodTimes = custom), bells)
    private fun time(course: CourseEntity, targets: List<PeriodEntity>) = projectCourseArrangement(course,
        config.copy(periodAlignmentMode = PeriodAlignmentMode.TIME), targets)

    @Test fun indexUsesCompleteTargetBellsAndPreservesTheSource() {
        val source = course()
        val target = listOf(PeriodEntity(3, "13:00", "14:00"))
        val displayed = projectCourseArrangement(source, config, target)
        assertEquals(listOf(3), displayed.periods)
        assertEquals(listOf(4), displayed.arrangementProjection!!.hiddenPeriods)
        assertEquals(LocalTime.parse("13:00"), courseStartTime(displayed, target))
        assertEquals(LocalTime.parse("14:00"), courseEndTime(displayed, target))
        assertEquals(source, displayed.originalArrangement())
        assertEquals(source.originalTimeSegments(), displayed.originalTimeSegments())
        assertEquals(listOf(3, 4), projectCourseArrangement(displayed, config, bells).periods)
    }

    @Test fun timeUsesTheCompleteOriginalIntervalAndExtendsToFullLessons() {
        val source = course().copy(originalPeriodTimes = "3,08:30-08:40;4,09:00-09:10")
        val displayed = time(source, bells)
        assertEquals(listOf(1, 2), displayed.periods)
        assertEquals(listOf("08:00-08:45", "08:55-09:40"),
            courseTimeSegments(displayed, bells).map { "${it.start}-${it.end}" })
        assertEquals(source.originalPeriodTimes, displayed.originalArrangement().originalPeriodTimes)
    }

    @Test fun onlyPositiveOverlapMatchesAndBreaksNeverSnapToTheLastLesson() {
        listOf("08:45-08:55", "06:00-07:00", "12:00-13:00", "09:40-10:00").forEach { range ->
            val displayed = time(course(listOf(3)).copy(originalPeriodTimes = "3,$range"), bells)
            assertTrue(range, displayed.isHiddenByPeriodAlignment())
            assertTrue(displayed.periods.isEmpty())
            assertNull(courseStartTime(displayed, bells))
            assertTrue(courseReminderSessions(displayed, bells).isEmpty())
        }
        assertEquals(listOf(1), time(course(listOf(3)).copy(originalPeriodTimes = "3,08:44-08:55"), bells).periods)
    }

    @Test fun partialCustomOverrideDoesNotFillAnUnmatchedMiddleLesson() {
        val source = course(listOf(1, 2, 3), custom = "2,12:01-12:19")
        val displayed = time(source, bells)
        val normal = displayed.arrangementProjection!!.times.filterNot { it.exact }.map { it.time.index }
        assertEquals(listOf(1, 3), normal)
        val exact = displayed.arrangementProjection!!.times.single { it.exact }.time
        assertEquals(LocalTime.parse("12:01"), exact.start)
        assertEquals(LocalTime.parse("12:19"), exact.end)
        val fragments = courseAlignmentFragments(displayed, bells)
        assertEquals(3, fragments.size)
        assertTrue(fragments.any { courseNeedsSupplementaryWeekRow(it, bells) })
        assertFalse(fragments.filterNot { it.hasCustomTime() }.any { 2 in it.periods })
    }

    @Test fun wholeCustomTimeRemainsVisibleEvenWithoutItsOriginalNumber() {
        val source = course(listOf(14), "06:01", "06:29")
        PeriodAlignmentMode.entries.forEach { mode ->
            val displayed = projectCourseArrangement(source, config.copy(periodAlignmentMode = mode), bells)
            assertFalse(displayed.isHiddenByPeriodAlignment())
            assertEquals("06:01 - 06:29", courseTimeLabel(displayed, bells))
            assertTrue(courseNeedsSupplementaryWeekRow(displayed, bells))
            assertEquals(source, displayed.originalArrangement())
        }
    }

    @Test fun completePerLessonCustomClocksRemainExactInBothModes() {
        val source = course(listOf(13, 14), "12:01", "14:29", "13,12:01-12:29;14,14:01-14:29")
        PeriodAlignmentMode.entries.forEach { mode ->
            val displayed = projectCourseArrangement(source, config.copy(periodAlignmentMode = mode), bells)
            assertEquals(listOf("12:01-12:29", "14:01-14:29"),
                courseTimeSegments(displayed, bells).map { "${it.start}-${it.end}" })
            assertEquals(2, courseReminderSessions(displayed, bells).size)
            assertTrue(courseReminderSessions(displayed, bells).all { courseNeedsSupplementaryWeekRow(it, bells) })
        }
    }

    @Test fun exactClocksSharingOneAnchorKeepDistinctProviderIds() {
        val source = course(listOf(13, 14), custom = "13,12:01-12:29;14,14:01-14:29")
        val state = AppState(courses = listOf(source), config = config, periods = bells).withEffectiveCourseArrangements()
        val exported = ColorOSCourseMapper.export(LocalDate.parse("2026-10-05"), state)
        val rows = Json.parseToJsonElement(exported.json).jsonArray
        assertEquals(2, rows.size)
        assertEquals(2, rows.map { it.jsonObject.getValue("id").jsonPrimitive.content }.distinct().size)
        assertEquals(listOf("12:01", "14:01"), rows.map { it.jsonObject.getValue("startTime").jsonPrimitive.content })
    }

    @Test fun partialCustomNormalizationAcceptsOnlyNamedLessons() {
        assertEquals(NormalizedCourseClock(null, null, "2,09:03-09:22"),
            normalizeCourseClock(null, null, "2,09:03-09:22", listOf(1, 2, 3)))
        assertThrows(IllegalArgumentException::class.java) {
            normalizeCourseClock(null, null, "4,09:03-09:22", listOf(1, 2, 3))
        }
    }

    @Test fun editPreviewKeepsMetadataSourceAndCapturesExplicitNewPeriods() {
        val raw = course(listOf(2))
        val target = listOf(PeriodEntity(1, "08:20", "09:15"), PeriodEntity(2, "09:25", "10:10"))
        val state = AppState(courses = listOf(raw), config = config.copy(periodAlignmentMode = PeriodAlignmentMode.TIME),
            periods = target).withEffectiveCourseArrangements()
        val renamed = state.previewCourseEdits(listOf(raw.copy(name = "改名"))).single()
        assertEquals(listOf(1, 2), renamed.periods)
        assertEquals(raw.originalPeriodTimes, renamed.originalArrangement().originalPeriodTimes)
        val edited = state.previewCourseEdits(listOf(raw.copy(periods = listOf(1), originalPeriodTimes = null))).single()
        assertEquals(listOf(1), edited.periods)
        assertEquals("1,08:20-09:15", edited.originalArrangement().originalPeriodTimes)
    }

    @Test fun metadataEditingKeepsHiddenLessonsAndNonContiguousSourceNumbers() {
        val source = course(listOf(1, 3, 4))
        val displayed = projectCourseArrangement(source, config, bells.take(2))
        assertEquals(source.copy(name = "改名"), displayed.copy(name = "改名").arrangementForWrite())
        val original = displayed.originalArrangement()
        val values = listOf(1, 2, 3, 4)
        val draft = courseEditorDraft(listOf(original), values, 16)
        assertEquals(4, draft.periodEnd)
        val renamed = draft.copy(name = "改名").toCourses(listOf(original), values, true).single()
        assertEquals(source.copy(name = "改名"), renamed)
        val explicit = draft.copy(periodEnd = 2).toCourses(listOf(original), values, true).single()
        assertEquals(listOf(1, 2), explicit.periods)
        assertNull(explicit.originalPeriodTimes)
    }

    @Test fun originalClockSurvivesRenamingRetimingOrRemovingTheOldScheme() {
        val source = course()
        val targets = bells.map { it.copy(startTime = LocalTime.parse(it.startTime).plusHours(3).toString(),
            endTime = LocalTime.parse(it.endTime).plusHours(3).toString()) }
        val byIndex = projectCourseArrangement(source, config, targets)
        val byTime = time(byIndex, bells)
        assertEquals(listOf(3, 4), byTime.periods)
        assertEquals(source, byTime.originalArrangement())
    }

    @Test fun everyReadConsumerUsesTheSameEffectiveClockAndSkipsHiddenOrdinaryCourses() {
        val source = course()
        val hidden = course(listOf(4)).copy(id = 8)
        val target = bells.take(3)
        val state = AppState(courses = listOf(source, hidden), config = config, periods = target).withEffectiveCourseArrangements()
        val monday = LocalDate.parse("2026-10-05")
        val today = coursesForDate(state, monday)
        assertEquals(listOf(7L), today.map { it.id })
        assertEquals(today, MiuixTodayWidgetRenderer.coursesForDate(state, monday))
        assertEquals(today, weekCourseBuckets(state.courses, 1).visibleCourses)
        assertEquals(LocalTime.parse("10:00"), courseStartTime(today.single(), target))
        assertEquals(LocalTime.parse("10:45"), courseEndTime(today.single(), target))
        val zone = ZoneId.of("Asia/Shanghai")
        val windows = courseQuietWindows(state, CourseQuietSettings(doNotDisturbEnabled = true), monday, zone)
        val window = windows.first()
        assertEquals(monday.atTime(10, 0).atZone(zone).toInstant().toEpochMilli(), window.start)
        assertEquals(monday.atTime(10, 45).atZone(zone).toInstant().toEpochMilli(), window.end)
        val exported = ColorOSCourseMapper.export(monday, state, zone)
        assertEquals(1, exported.exportedCount)
    }

    @Test fun newConflictsAreVisibleInPreviewWithoutChangingEitherSource() {
        val first = course(listOf(1)).copy(originalPeriodTimes = "1,08:05-08:20")
        val second = course(listOf(2)).copy(id = 8, name = "第二门", originalPeriodTimes = "2,08:25-08:40")
        val impact = previewCourseAlignment(listOf(first, second), config, bells,
            config.copy(periodAlignmentMode = PeriodAlignmentMode.TIME), bells)
        assertEquals(1, impact.conflicts.size)
        assertEquals(first, impact.courses.first().originalArrangement())
        assertTrue(impact.summary().contains("新增 1 处时间冲突"))
    }
}
