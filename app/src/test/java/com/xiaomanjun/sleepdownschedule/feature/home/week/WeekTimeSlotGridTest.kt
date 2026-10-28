package com.xiaomanjun.sleepdownschedule.feature.home.week

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.dp
import com.xiaomanjun.sleepdownschedule.model.*
import com.xiaomanjun.sleepdownschedule.domain.course.WeekCourseSegment
import com.xiaomanjun.sleepdownschedule.domain.schedule.TimeSlotCourse
import com.xiaomanjun.sleepdownschedule.feature.course.editor.CourseEditorWeekGrid
import com.xiaomanjun.sleepdownschedule.feature.home.overlay.courseCopyBounds
import org.junit.Assert.*
import org.junit.Test

class WeekTimeSlotGridTest {
    @Test fun realClockBandsKeepLunchAndLateCoursesVisibleWithoutAddingLessonNumbers() {
        val start = java.time.LocalTime.parse("12:00")
        val end = java.time.LocalTime.parse("13:00")
        val grid = weekTimeSlotGrid(emptyList(),periods.size,100f,periods=periods,clockIntervals=listOf(start to end))
        assertEquals(3,grid.rowTops.size)
        assertEquals(1,grid.clockBands.size)
        val source = course.copy(periods=emptyList(),customStartTime="12:00",customEndTime="13:00")
        val projected = com.xiaomanjun.sleepdownschedule.domain.schedule.projectCourseArrangement(source,defaultConfig(),periods)
        val segment = com.xiaomanjun.sleepdownschedule.domain.course.buildWeekConflictGroups(listOf(projected),periods.map { it.periodIndex },periods).single().segments.single()
        val bounds = weekCardVerticalBounds(segment,periods,100.dp,grid)
        assertTrue(bounds.first >= 300f)
        assertTrue(bounds.second > bounds.first)
        assertEquals(grid.totalHeight,bounds.second,0.01f)
        assertEquals(emptyList<Int>(),projected.periods)
    }

    @Test fun anIndependentClockUsesTheFixedGridWithPreciseOffsets() {
        val start = java.time.LocalTime.parse("08:20")
        val end = java.time.LocalTime.parse("09:05")
        val grid = weekTimeSlotGrid(emptyList(),periods.size,100f,periods=periods,clockIntervals=listOf(start to end))
        assertEquals(100f * 20/45,grid.clockPosition(start)!!,0.01f)
        assertTrue(grid.clockPosition(end)!! > grid.rowTops[1])
        assertEquals(listOf(1,2,3),periods.map { it.periodIndex })
    }
    private val course = CourseEntity(name = "课程", teacher = null, location = null, weekday = 1,
        periods = listOf(1), weeks = listOf(1), weekParity = WeekParity.ALL, note = null)
    private val periods = listOf(PeriodEntity(1, "08:00", "08:45"), PeriodEntity(2, "08:55", "09:40"),
        PeriodEntity(3, "10:00", "10:45"))
    private fun slot(id: String, after: Int) = TimeSlotCourse(id, "晚自修", "19:00", "20:00", after)
    private fun grid() = weekTimeSlotGrid(listOf(slot("early", 0), slot("mid", 1), slot("tail", 3)), 3, 100f)

    @Test fun bandsHaveTheirOwnSpaceIncludingBeforeFirstAndAfterLastLesson() {
        val grid = grid()
        assertEquals(listOf(32f, 164f, 264f), grid.rowTops)
        assertEquals(listOf(0f, 132f, 364f), grid.bands.map { it.top })
        assertEquals(396f, grid.totalHeight, 0f)
        assertEquals(132f, grid.bottom(1f), 0f)
        assertEquals(164f, grid.top(1f), 0f)
        assertEquals(100f, grid.bottom(2f) - grid.top(1f), 0f)
        // Exact-clock courses can extend beyond the last numbered row; retain that geometry.
        assertEquals(389f, grid.top(3.25f), 0f)
        assertEquals(464f, grid.bottom(4f), 0f)
    }

    @Test fun stackedBandsNeverCompressLessonCardsOrOverlapTheLastLesson() {
        val grid = weekTimeSlotGrid((1..12).map { slot("$it", 2) }, 3, 44f)
        assertEquals(12, grid.bands.size)
        assertEquals(44f, grid.bottom(3f) - grid.top(2f), 0f)
        grid.bands.zipWithNext().forEach { (a, b) -> assertTrue(a.top + a.height <= b.top) }
        val segment = WeekCourseSegment(course.copy(periods = listOf(3)), 2, 2, listOf(3))
        val (top, bottom) = weekCardVerticalBounds(segment, periods, 44.dp, grid)
        assertEquals(40f, bottom - top, 0f)
        assertTrue(top >= grid.bands.last().top + grid.bands.last().height)
    }

    @Test fun sundayFirstColumnsAndHiddenWeekendsKeepTheirActualWeekdayIdentity() {
        assertEquals(listOf(1..5), timeSlotColumnRuns(listOf(7, 1, 2, 3, 4, 5, 6), (1..5).toSet()))
        assertEquals(listOf(0..4), timeSlotColumnRuns((1..5).toList(), (1..5).toSet()))
        assertEquals(listOf(0..0, 2..3), timeSlotColumnRuns(listOf(7, 1, 2, 3), setOf(7, 2, 3)))
        val config = defaultConfig().copy(hideEmptyWeekends = true, weekStartsOnSunday = true,
            timeSlotCoursesJson = com.xiaomanjun.sleepdownschedule.domain.schedule.encodeTimeSlotCourses(
                listOf(slot("sunday", 0).copy(weekdays = listOf(7)))))
        val buckets = com.xiaomanjun.sleepdownschedule.feature.home.day.weekCourseBuckets(emptyList(), 1, config)
        assertEquals(listOf(7, 1, 2, 3, 4, 5, 6), weekPresentationWeekdays(buckets, emptyList(), config, 1))
    }

    @Test fun copyHitRegionsAndBoundsUseTheSameAxisAsTheVisibleCards() {
        val axis = grid()
        val grid = CourseEditorWeekGrid(Offset.Zero, 700f, 100f, 4f, listOf(1, 2, 3),
            null, 0, false, axis.rowTops)
        assertFalse(grid.isLessonY(150f))
        assertTrue(grid.isLessonY(200f))
        assertEquals(12f, grid.rowTop(-0.2f), 0.001f)
        val bounds = courseCopyBounds(course.copy(periods = listOf(2)), grid, (1..7).toList(), periods).single()
        assertEquals(166f, bounds.top, 0f)
        assertEquals(262f, bounds.bottom, 0f)
    }

    @Test fun moveResizeAndSundayTargetsUseActualRowAndColumnPositions() {
        val axis = grid()
        val request = WeekEditOverlayRequest(WeekEditOverlayMode.Move, course.copy(weekday = 7),
            Rect(0f, 32f, 100f, 132f), 7, 1, 1, 3, 100f, 100f,
            listOf(1, 2, 3), emptyList(), 1, 7, Offset.Zero,
            rowTopsPx = axis.rowTops, weekdays = listOf(7, 1, 2, 3, 4, 5, 6))
        assertEquals(2, request.startPeriodAfterMove(132f))
        assertEquals(1, request.weekdayAfterMove(100f))
        assertEquals(1, request.columnDeltaTo(1))
        assertEquals(7, request.weekdayAfterMove(-100f))
        assertEquals(232f, request.heightForSpan(2), 0f)
        assertEquals(2, request.spanForHeight(228f))
    }
}
