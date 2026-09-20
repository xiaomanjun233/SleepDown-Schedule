package com.xiaomanjun.sleepdownschedule.feature.home.overlay

import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.xiaomanjun.sleepdownschedule.*
import com.xiaomanjun.sleepdownschedule.feature.course.editor.CourseEditorWeekGrid
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.*
import org.junit.Test

class CourseCopyPlacementTest {
    private val source = CourseEntity(42, "测试课程", "教师", "教室", 2, listOf(2, 3), listOf(1, 3, 7), WeekParity.ODD, "备注", scheduleId = 5)
    private val periods = defaultPeriods(5)
    private val sourceBounds = Rect(20f, 200f, 100f, 320f)
    private fun request() = CourseShortcutRequest(source, 3, sourceBounds, 12f, 0.5f) {}
    private fun destination() = CourseCopyTarget(
        copiedShortcutCourse(source, null).copy(weekday = 4, periods = listOf(5, 6)),
        3, 5, listOf(Rect(220f, 500f, 300f, 620f)), sourceBounds
    )

    @Test fun relativePatternAndScopeSurvivePlacement() {
        val draft = copiedShortcutCourse(source.copy(periods = listOf(2, 4)), null)
        val placed = requireNotNull(courseCopyAtSlot(draft, 5, 6, periods))
        assertEquals(listOf(6, 8), placed.periods)
        assertEquals(draft, placed.copy(weekday = draft.weekday, periods = draft.periods))
        assertEquals(0L, placed.id)
    }

    @Test fun endOfGridAndMissingDefinitionsNeverTruncateTheCourse() {
        assertNull(courseCopyAtSlot(source, 5, 12, periods))
        assertNull(courseCopyAtSlot(source, 8, 3, periods))
        assertNull(courseCopyAtSlot(source.copy(periods = listOf(2, 99)), 3, 4, periods))
    }

    @Test fun exactTimesKeepDurationAndReceiveMatchingAnchors() {
        val exact = source.copy(customStartTime = "08:10", customEndTime = "09:20")
        val placed = requireNotNull(courseCopyAtSlot(exact, 4, 5, periods))
        assertEquals("14:00", placed.customStartTime)
        assertEquals("15:10", placed.customEndTime)
        assertEquals(listOf(5, 6), placed.periods)
        assertNull(courseCopyAtSlot(exact, 4, 12, periods))
    }

    @Test fun firstTapOnlyPreviewsAndSecondTapWritesExactlyOnce() = runBlocking {
        var writes = 0
        val controller = CourseCopyController(this, { null }) { _, _ -> writes++ }
        controller.begin(request(), copiedShortcutCourse(source, null))
        assertEquals(CourseCopyTap.Selected, controller.select(destination()))
        assertEquals(0, writes)
        assertEquals(CourseCopyTap.Confirmed, controller.select(destination()))
        assertEquals(1, writes)
        assertEquals(CourseCopyTap.Rejected, controller.select(destination()))
        assertEquals(1, writes)
    }

    @Test fun importedSecondPrecisionIsNotDiscarded() {
        val exact = source.copy(customStartTime = "08:10:30", customEndTime = "09:20:45")
        val placed = requireNotNull(courseCopyAtSlot(exact, 4, 5, periods))
        assertEquals("14:00", placed.customStartTime)
        assertEquals("15:10:15", placed.customEndTime)
    }

    @Test fun changedTargetNeedsItsOwnConfirmationAndConflictsAreRechecked() = runBlocking {
        var invalid = false
        var writes = 0
        val controller = CourseCopyController(this, { if (invalid) "课程冲突" else null }) { _, _ -> writes++ }
        controller.begin(request(), copiedShortcutCourse(source, null))
        controller.select(destination())
        val other = destination().copy(course = destination().course.copy(weekday = 5))
        assertEquals(CourseCopyTap.Selected, controller.select(other))
        invalid = true
        assertEquals(CourseCopyTap.Rejected, controller.select(other))
        assertEquals(0, writes)
        assertEquals("课程冲突", controller.message)
    }

    @Test fun failedSaveKeepsSelectionAndNeverStartsTheFlight() = runBlocking {
        val controller = CourseCopyController(this, { null }) { _, done -> done(false) }
        controller.begin(request(), copiedShortcutCourse(source, null))
        controller.select(destination())
        controller.select(destination())
        assertEquals(CourseCopyPhase.Selecting, controller.phase)
        assertFalse(controller.landed)
        assertFalse(controller.hides(source, 3))
    }

    @Test fun lateSaveCallbackCannotResurrectACancelledSession() = runBlocking {
        var callback: ((Boolean) -> Unit)? = null
        val controller = CourseCopyController(this, { null }) { _, done -> callback = done }
        controller.begin(request(), copiedShortcutCourse(source, null))
        controller.select(destination())
        controller.select(destination())
        controller.reset()
        callback!!(true)
        assertEquals(CourseCopyPhase.Idle, controller.phase)
        assertNull(controller.source)
    }

    @Test fun repositoryWeekMergingStillRecognizesTheLandingCard() = runBlocking {
        val controller = CourseCopyController(this, { null }) { _, _ -> }
        controller.begin(request(), copiedShortcutCourse(source, null))
        controller.select(destination())
        val merged = destination().course.copy(id = 99, weeks = listOf(1, 3, 5, 7, 9))
        assertTrue(controller.isTarget(merged, 3))
        assertFalse(controller.isTarget(merged, 2))
        assertFalse(controller.isTarget(source, 3))
    }

    @Test fun landingWaitsForThePersistedCardAndReusesTheImpactWave() = runBlocking {
        val clock = BroadcastFrameClock()
        val controller = CourseCopyController(CoroutineScope(coroutineContext + clock), { null }) { _, done -> done(true) }
        controller.begin(request(), copiedShortcutCourse(source, null))
        controller.select(destination())
        controller.select(destination())
        val saved = destination().course.copy(id = 99)
        var sawImpact = false
        repeat(80) { frame ->
            clock.sendFrame(frame * 16_666_667L); yield()
            if (controller.landed) sawImpact = controller.rippleCenter != null
        }
        assertTrue(sawImpact)
        assertEquals(CourseCopyPhase.AwaitingCard, controller.phase)
        assertTrue(controller.hides(saved, 3))
        assertFalse(controller.hides(source, 3))
        controller.reportTarget(saved, 3)
        assertFalse(controller.active)
        assertFalse(controller.hides(saved, 3))
        repeat(60) { frame -> clock.sendFrame((80 + frame) * 16_666_667L); yield() }
        assertNull(controller.rippleCenter)
    }

    @Test fun rtlGridAndSecondTapInsideTallPlaceholderUseTheSameDestination() = runBlocking {
        var writes = 0
        val controller = CourseCopyController(this, { null }) { _, _ -> writes++ }
        controller.begin(request(), copiedShortcutCourse(source, 3))
        val grid = CourseEditorWeekGrid(Offset(10f, 120f), 500f, 60f, 4f, periods.map { it.periodIndex }, null, 0, true)
        assertEquals(CourseCopyTap.Selected, courseCopyGridTap(controller, 3, grid, (1..5).toList(), periods, Offset(20f, 245f)))
        assertEquals(5, controller.target!!.course.weekday)
        assertEquals(Rect(12f, 362f, 108f, 478f), controller.target!!.bounds.single())
        assertEquals(CourseCopyTap.Confirmed, courseCopyGridTap(controller, 3, grid, (1..5).toList(), periods, Offset(25f, 335f)))
        assertEquals(1, writes)
    }

    @Test fun flightStartsAtTheSourceAndEndsExactlyAtTheDestination() {
        val target = destination().bounds.first()
        val start = courseCopyFlightFrame(0f, sourceBounds, target)
        val end = courseCopyFlightFrame(1f, sourceBounds, target)
        assertEquals(sourceBounds.center, start.center)
        assertEquals(target.center.x, end.center.x, 0.001f)
        assertEquals(target.center.y, end.center.y, 0.001f)
        assertEquals(target.width, end.width, 0.001f)
        assertEquals(target.height, end.height, 0.001f)
        val middle = courseCopyFlightFrame(0.565f, sourceBounds, target)
        assertTrue(middle.center.y < (sourceBounds.center.y + target.center.y) / 2f)
    }
}
