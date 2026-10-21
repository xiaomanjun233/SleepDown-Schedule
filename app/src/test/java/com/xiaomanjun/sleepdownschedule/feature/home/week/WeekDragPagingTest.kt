package com.xiaomanjun.sleepdownschedule.feature.home.week

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test
import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.Velocity
import com.xiaomanjun.sleepdownschedule.CourseEntity
import com.xiaomanjun.sleepdownschedule.WeekParity
import kotlinx.coroutines.*

class WeekDragPagingTest {
    private val course = CourseEntity(id = 1, name = "数学", teacher = null, location = null,
        weekday = 1, periods = listOf(1), weeks = listOf(3, 4, 5), weekParity = WeekParity.ALL, note = null)
    private fun request(mode: WeekEditOverlayMode = WeekEditOverlayMode.Move) = WeekEditOverlayRequest(
        mode, course, Rect(100f, 0f, 200f, 100f), 1, 1, 1, 3, 100f, 100f,
        listOf(1, 2, 3), listOf(course), 3, 7, Offset(150f, 50f))

    @Test fun dropAfterPagingKeepsSourceWeekAndCommitsDestinationWeek() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined + BroadcastFrameClock())
        try {
            val controller = WeekEditOverlayController(scope, ScrollState(0))
            controller.start(request())
            controller.drag(Offset(100f, 0f), 1000f, 40f, 4f)
            controller.moveToWeek(4, listOf(course))
            var edited: CourseEntity? = null
            var sourceWeek = 0
            assertTrue(controller.finishMove(Velocity.Zero, onUpdateCourseSingleWeek = { _, next, source ->
                edited = next; sourceWeek = source
            }))
            assertEquals(3, sourceWeek)
            assertEquals(listOf(4), edited!!.weeks)
            assertEquals(2, edited!!.weekday)
            assertEquals(4, controller.committedTargetWeek)
        } finally { scope.cancel() }
    }

    @Test fun anotherOccurrenceAtDestinationPreventsSilentOverwriteAndResizeNeverPages() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined + BroadcastFrameClock())
        try {
            val controller = WeekEditOverlayController(scope, ScrollState(0))
            controller.start(request())
            controller.moveToWeek(4, listOf(course))
            assertFalse(controller.finishMove(Velocity.Zero, onUpdateCourseSingleWeek = { _, _, _ -> error("conflict") }))
            controller.start(request(WeekEditOverlayMode.Resize))
            controller.moveToWeek(4, listOf(course))
            assertEquals(3, controller.request!!.destinationWeek)
        } finally { scope.cancel() }
    }
    @Test fun onlyEdgesPageAndSemesterBoundariesStopPaging() {
        assertEquals(-1, weekDragEdgeDirection(85f, 80f, 780f, 28f, 3, 20))
        assertEquals(1, weekDragEdgeDirection(775f, 80f, 780f, 28f, 3, 20))
        assertEquals(0, weekDragEdgeDirection(450f, 80f, 780f, 28f, 3, 20))
        assertEquals(0, weekDragEdgeDirection(85f, 80f, 780f, 28f, 1, 20))
        assertEquals(0, weekDragEdgeDirection(775f, 80f, 780f, 28f, 20, 20))
    }
}
