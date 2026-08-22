package com.xiaomanjun.sleepdownschedule

import com.xiaomanjun.sleepdownschedule.transition.TransitionRouteId
import com.xiaomanjun.sleepdownschedule.transition.TransitionSession
import com.xiaomanjun.sleepdownschedule.transition.TransitionSessionId
import com.xiaomanjun.sleepdownschedule.transition.TransitionSessionState
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransitionSessionStateMachineTest {
    @Test
    fun nativeOpenAndReturnFollowTheSessionProtocol() {
        val session = session()
        assertTrue(session.moveTo(TransitionSessionState.SourceReady))
        assertTrue(session.moveTo(TransitionSessionState.NativeRegistered))
        assertTrue(session.moveTo(TransitionSessionState.NativeRunning))
        assertTrue(session.hasReachedNativeRunning)
        assertTrue(session.moveTo(TransitionSessionState.Open))
        assertTrue(session.moveTo(TransitionSessionState.Closing))
        assertTrue(session.moveTo(TransitionSessionState.Finished))
        assertEquals(TransitionSessionState.Finished, session.currentState)
    }

    @Test
    fun registrationCanFallbackExactlyToLegacyButCannotJumpToOpen() {
        val session = session()
        assertFalse(session.moveTo(TransitionSessionState.Open))
        assertTrue(session.moveTo(TransitionSessionState.SourceReady))
        assertTrue(session.moveTo(TransitionSessionState.NativeRegistered))
        assertTrue(session.moveTo(TransitionSessionState.LegacyRunning))
        assertTrue(session.moveTo(TransitionSessionState.Open))
    }

    @Test
    fun invalidatedGenerationRejectsLateVendorCallbacks() {
        val session = session()
        val first = session.nextCallbackGeneration()
        assertTrue(session.isCurrentCallback(first))
        session.invalidateCallbacks()
        assertFalse(session.isCurrentCallback(first))
        val current = session.nextCallbackGeneration()
        assertTrue(session.isCurrentCallback(current))
        session.moveTo(TransitionSessionState.Cancelled)
        assertFalse(session.isCurrentCallback(current))
    }

    @Test
    fun nestedSessionKeepsTheExactParentIdentity() {
        val parentId = TransitionSessionId("parent")
        val child = TransitionSession(
            TransitionSessionId("child"),
            TransitionRouteId.CourseManagementToDetail,
            parentId
        )
        assertEquals(parentId, child.parentSessionId)
    }

    @Test
    fun competingAsyncFallbacksCanClaimNativeRegistrationOnlyOnce() {
        val session = session()
        session.moveTo(TransitionSessionState.SourceReady)
        session.moveTo(TransitionSessionState.NativeRegistered)
        val workers = 12
        val ready = CountDownLatch(workers)
        val start = CountDownLatch(1)
        val completed = CountDownLatch(workers)
        val claims = AtomicInteger(0)
        val executor = Executors.newFixedThreadPool(workers)
        try {
            repeat(workers) {
                executor.execute {
                    ready.countDown()
                    start.await()
                    if (session.moveFrom(
                            TransitionSessionState.NativeRegistered,
                            TransitionSessionState.LegacyRunning
                        )
                    ) {
                        claims.incrementAndGet()
                    }
                    completed.countDown()
                }
            }
            assertTrue(ready.await(2, TimeUnit.SECONDS))
            start.countDown()
            assertTrue(completed.await(2, TimeUnit.SECONDS))
        } finally {
            start.countDown()
            executor.shutdownNow()
        }
        assertEquals(1, claims.get())
        assertEquals(TransitionSessionState.LegacyRunning, session.currentState)
    }

    private fun session() = TransitionSession(
        TransitionSessionId("test"),
        TransitionRouteId.CourseManagementToDetail,
        null
    )
}
