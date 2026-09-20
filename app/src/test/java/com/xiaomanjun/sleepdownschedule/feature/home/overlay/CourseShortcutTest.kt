package com.xiaomanjun.sleepdownschedule.feature.home.overlay

import androidx.compose.ui.geometry.Rect
import androidx.compose.runtime.BroadcastFrameClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import com.xiaomanjun.sleepdownschedule.CourseEntity
import com.xiaomanjun.sleepdownschedule.WeekParity
import org.junit.Assert.*
import org.junit.Test

class CourseShortcutTest {
    private val course = CourseEntity(
        id = 42, name = "测试课程", teacher = "教师", location = "教室", weekday = 3,
        periods = listOf(2, 3), weeks = listOf(1, 3, 7), weekParity = WeekParity.ODD,
        note = "备注", customStartTime = "08:10", customEndTime = "09:20",
        customColorArgb = 0xff112233, scheduleId = 5
    )

    @Test fun copyAllWeeksPreservesGapsAndEveryCourseFieldExceptIdentity() {
        val copy = copiedShortcutCourse(course, null)
        assertEquals(0L, copy.id)
        assertEquals(course, copy.copy(id = course.id))
        assertEquals(listOf(1, 3, 7), copy.weeks)
    }

    @Test fun copySingleWeekDoesNotInheritParityOrChangeOriginal() {
        val copy = copiedShortcutCourse(course, 3)
        assertEquals(listOf(3), copy.weeks)
        assertEquals(WeekParity.ALL, copy.weekParity)
        assertEquals(course, copy.copy(id = course.id, weeks = course.weeks, weekParity = course.weekParity))
        assertEquals(listOf(1, 3, 7), course.weeks)
    }

    @Test fun leftCenterAndRightUseTheirPhysicalBottomAnchors() {
        val available = Rect(8f, 24f, 892f, 900f)
        val anchors = listOf(Rect(20f, 300f, 100f, 400f), Rect(410f, 300f, 490f, 400f), Rect(800f, 300f, 880f, 400f))
        anchors.zip(listOf(0f, 0.5f, 1f)).forEach { (anchor, pivot) ->
            val result = courseShortcutPlacement(anchor, available, 220f, 108f, 12f, pivot)
            assertEquals(pivot, result.pivotX, 0f)
            assertFalse(result.belowAnchor)
            assertEquals(1f, result.pivotY, 0f)
            assertEquals(anchor.top - 12f, result.bounds.bottom, 0.001f)
            assertEquals(anchor.left + anchor.width * pivot,
                result.bounds.left + result.bounds.width * pivot, 0.001f)
        }
    }

    @Test fun firstRowAndNarrowWindowKeepAllActionsInsideSafeArea() {
        val available = Rect(16f, 40f, 196f, 340f)
        val result = courseShortcutPlacement(Rect(170f, 45f, 190f, 95f), available, 330f, 156f, 12f, 1f)
        assertEquals(available.left, result.bounds.left, 0f)
        assertEquals(available.right, result.bounds.right, 0f)
        assertEquals(107f, result.bounds.top, 0f)
        assertTrue(result.belowAnchor)
        assertEquals(0f, result.pivotY, 0f)
        assertTrue(result.bounds.bottom <= available.bottom)
    }

    @Test fun topRowOpensBelowForEveryHorizontalOrigin() {
        val available = Rect(8f, 24f, 892f, 900f)
        val anchor = Rect(410f, 50f, 490f, 150f)
        for (pivot in listOf(0f, 0.5f, 1f)) {
            val result = courseShortcutPlacement(anchor, available, 220f, 176f, 12f, pivot)
            assertEquals(anchor.bottom + 12f, result.bounds.top, 0f)
            assertEquals(pivot, result.pivotX, 0f)
            assertEquals(0f, result.pivotY, 0f)
        }
        val smallWindow = Rect(8f, 24f, 200f, 174f)
        val result = courseShortcutPlacement(anchor, smallWindow, 220f, 176f, 12f, 0.5f)
        assertEquals(smallWindow, result.bounds)
    }

    @Test fun editActionWaitsForMenuToRetract() = runBlocking {
        val clock = BroadcastFrameClock()
        val controller = CourseShortcutController(CoroutineScope(coroutineContext + clock))
        var entered = false
        controller.open(CourseShortcutRequest(course, 3, Rect(0f, 200f, 80f, 300f), 12f, 0.5f) { entered = true })
        repeat(90) { clock.sendFrame(it * 16_666_667L); yield() }
        controller.close { entered = true }
        assertFalse(entered)
        assertNotNull(controller.request)
        repeat(30) { clock.sendFrame((90 + it) * 16_666_667L); yield() }
        assertTrue(entered)
        assertNull(controller.request)
        assertEquals(0f, controller.progress.value, 0f)
    }

    @Test fun pressSinksBeforeMenuThenLiftAndMenuAdvanceTogether() = runBlocking {
        val clock = BroadcastFrameClock()
        val controller = CourseShortcutController(CoroutineScope(coroutineContext + clock))
        controller.open(CourseShortcutRequest(course, 3, Rect(0f, 200f, 80f, 300f), 12f, 0.5f) {})
        var sawPress = false
        var sawSharedLift = false
        repeat(90) { frame ->
            clock.sendFrame(frame * 8_333_333L)
            yield()
            if (controller.cardScale.value < 0.995f && controller.progress.value == 0f) sawPress = true
            if (controller.cardScale.value > 1f && controller.progress.value > 0f) sawSharedLift = true
        }
        assertTrue(sawPress)
        assertTrue(sawSharedLift)
    }

    @Test fun dragTakeoverCancelsPendingMenuAction() = runBlocking {
        val clock = BroadcastFrameClock()
        val controller = CourseShortcutController(CoroutineScope(coroutineContext + clock))
        var actionCalled = false
        controller.open(CourseShortcutRequest(course, 3, Rect(0f, 200f, 80f, 300f), 12f, 0.5f) {})
        repeat(30) { clock.sendFrame(it * 16_666_667L); yield() }
        controller.close { actionCalled = true }
        controller.takeOverDrag()
        assertNull(controller.request)
        repeat(30) { clock.sendFrame((30 + it) * 16_666_667L); yield() }
        assertFalse(actionCalled)
        assertNull(controller.copyRequest)
        assertEquals(0f, controller.progress.value, 0f)
    }
}
