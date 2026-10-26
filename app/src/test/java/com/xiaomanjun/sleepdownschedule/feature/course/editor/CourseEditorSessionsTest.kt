package com.xiaomanjun.sleepdownschedule.feature.course.editor

import com.xiaomanjun.sleepdownschedule.CourseEntity
import com.xiaomanjun.sleepdownschedule.WeekParity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class CourseEditorSessionsTest {
    @Test fun sameCardTapDuringCloseIsNotConsumedByTheDuplicateOpenGuard() {
        val registry = CourseEditorSessions()
        val a = open(registry, 1)
        val original = a.request
        a.retainedRequest = original!!.copy(sourceBoundsInRoot = androidx.compose.ui.geometry.Rect(10f, 10f, 110f, 110f))
        assertTrue(registry.consumeActiveTap(1))
        assertNull(a.request)
        a.motion.phase = CourseEditorOverlayPhase.Closing
        assertFalse(registry.consumeActiveTap(1))
        assertSame(a, registry.atOriginalAnchor(androidx.compose.ui.geometry.Offset(30f, 30f)))
        assertTrue(registry.reopen(a))
        assertSame(a.retainedRequest, a.request)
        assertSame(a, registry.active)
    }

    @Test fun movingSurfaceTapUsesOnlyAttachedTargetsFromTheVisibleModeAndWeek() {
        val targets = CourseEditorFlightRegistry()
        val point = androidx.compose.ui.geometry.Offset(30f, 30f)
        val bounds = androidx.compose.ui.geometry.Rect(10f, 10f, 60f, 90f)
        var hit = ""
        val a = Any()
        targets.registerTarget(a, 4, false, { bounds }) { hit = "A" }
        targets.registerTarget(Any(), 5, false, { bounds }) { hit = "offscreen week" }
        targets.registerTarget(Any(), 4, true, { bounds }) { hit = "day" }
        targets.registerTarget(Any(), 4, false, { null }) { hit = "detached" }
        assertTrue(targets.openTargetAt(point, 4, false))
        assertEquals("A", hit)
        assertTrue(targets.openTargetAt(point, 4, true))
        assertEquals("day", hit)
        targets.removeTarget(a)
        assertFalse(targets.openTargetAt(point, 4, false))
        assertFalse(targets.openTargetAt(androidx.compose.ui.geometry.Offset.Zero, 4, true))
    }

    private fun request(id: Long) = CourseEditorOverlayRequest(CourseEntity(
        id = id, name = "课程$id", teacher = null, location = null, weekday = 1,
        periods = listOf(1, 2), weeks = listOf(1), weekParity = WeekParity.ALL,
        note = null, scheduleId = 1), 1, null)

    private fun open(registry: CourseEditorSessions, id: Long): CourseEditorSession {
        val session = checkNotNull(registry.select(id, null))
        if (session.request == null) {
            session.retainedRequest = request(id)
            session.request = session.retainedRequest
            session.motion.phase = CourseEditorOverlayPhase.Preparing
            session.motion.phase = CourseEditorOverlayPhase.Opening
        } else if (session.motion.phase == CourseEditorOverlayPhase.Closing) {
            session.motion.phase = CourseEditorOverlayPhase.Opening
        }
        return session
    }

    private fun interrupt(session: CourseEditorSession) = runBlocking {
        session.motion.progress.snapTo(0.5f)
        session.request = null
        session.motion.phase = CourseEditorOverlayPhase.Closing
        assertTrue(session.motion.hasInterrupted)
    }

    @Test fun normalOpeningAndClosingBlockOtherStationsWithoutChangingTheirOwner() = runBlocking {
        val registry = CourseEditorSessions()
        val a = open(registry, 1)
        val original = a.request
        assertNull(registry.select(2, null))
        assertSame(original, a.request)
        a.motion.progress.snapTo(1f)
        a.motion.phase = CourseEditorOverlayPhase.Open
        a.request = null
        a.motion.phase = CourseEditorOverlayPhase.Closing
        assertFalse(a.motion.hasInterrupted)
        assertNull(registry.select(2, null))
        assertSame(a, registry.active)
        assertEquals(1, registry.sessions.size)
    }

    @Test fun pendingInitialSnapshotAlsoOwnsTheFirstStation() {
        val registry = CourseEditorSessions()
        val a = checkNotNull(registry.select(1, null))
        a.retainedRequest = request(1)
        assertNull(registry.select(2, null))
        assertSame(a, registry.active)
    }

    @Test fun aToBToAToCReusesAAndRetainsEveryOtherFlightUntilItsOwnFinish() {
        val registry = CourseEditorSessions()
        val a = open(registry, 1)
        val aForm = a.request
        val aController = a.motion.liquidMorphController.state.token
        interrupt(a)
        val b = open(registry, 2)
        assertEquals(listOf(a, b), registry.sessions)
        assertNull(registry.select(1, null))
        interrupt(b)
        assertSame(a, open(registry, 1))
        assertSame(aForm, a.request)
        assertSame(aController, a.motion.liquidMorphController.state.token)
        assertNull(b.request)
        val c = open(registry, 3)
        assertEquals(listOf(a, b, c), registry.sessions)
        assertSame(c, registry.active)
        assertSame(aForm, a.request)
        assertEquals(CourseEditorOverlayPhase.Opening, a.motion.phase)
        b.motion.phase = CourseEditorOverlayPhase.Idle
        registry.finish(b)
        assertEquals(listOf(a, c), registry.sessions)
        assertSame(aForm, a.retainedRequest)
        a.request = null
        a.motion.phase = CourseEditorOverlayPhase.Idle
        registry.finish(a)
        assertEquals(listOf(c), registry.sessions)
        assertSame(c.request, c.retainedRequest)
    }

    @Test fun finishingTheActiveFlightKeepsAnotherVisibleFormActive() {
        val registry = CourseEditorSessions()
        val a = open(registry, 1)
        interrupt(a)
        val b = open(registry, 2)
        b.request = null
        b.motion.phase = CourseEditorOverlayPhase.Idle
        registry.finish(b)
        assertSame(a, registry.active)
        assertEquals(listOf(a), registry.sessions)
    }

    @Test fun staleFinishDoesNotReleaseAReopenedForm() {
        val registry = CourseEditorSessions()
        val a = open(registry, 1)
        val original = a.request
        a.request = null
        a.motion.phase = CourseEditorOverlayPhase.Closing
        assertSame(a, open(registry, 1))
        registry.finish(a)
        assertSame(original, a.request)
        assertSame(original, a.retainedRequest)
    }

    @Test fun controllerCanReverseRepeatedlyWithoutReleasingItsSessionResources() {
        val a = open(CourseEditorSessions(), 1)
        val controller = a.motion.liquidMorphController
        val token = controller.state.token!!
        var releases = 0
        controller.registerCleanup(token, "form") { releases++ }
        repeat(12) {
            a.motion.phase = CourseEditorOverlayPhase.Closing
            a.motion.phase = CourseEditorOverlayPhase.Opening
            assertEquals(token, controller.state.token)
            assertEquals(com.xiaomanjun.sleepdownschedule.glass.LiquidMorphPhase.Opening, controller.state.phase)
            assertEquals(0, releases)
        }
        a.motion.phase = CourseEditorOverlayPhase.Closing
        a.motion.phase = CourseEditorOverlayPhase.Disposing
        assertEquals(1, releases)
    }
}
