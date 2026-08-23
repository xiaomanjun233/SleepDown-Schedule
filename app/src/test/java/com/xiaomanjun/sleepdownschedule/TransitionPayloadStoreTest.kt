package com.xiaomanjun.sleepdownschedule

import androidx.compose.ui.geometry.Rect
import com.xiaomanjun.sleepdownschedule.transition.StaticTransitionAnchorProvider
import com.xiaomanjun.sleepdownschedule.transition.TransitionAnchorFrame
import com.xiaomanjun.sleepdownschedule.transition.TransitionNativeSourceViewProvider
import com.xiaomanjun.sleepdownschedule.transition.TransitionPayload
import com.xiaomanjun.sleepdownschedule.transition.TransitionPayloadStore
import com.xiaomanjun.sleepdownschedule.transition.TransitionRouteId
import com.xiaomanjun.sleepdownschedule.transition.TransitionSessionId
import java.util.concurrent.atomic.AtomicInteger
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TransitionPayloadStoreTest {
    @Before
    fun setUp() = TransitionPayloadStore.clearForTests()

    @After
    fun tearDown() = TransitionPayloadStore.clearForTests()

    @Test
    fun cleanupIsSessionScopedAndRunsExactlyOnce() {
        val first = TransitionPayloadStore.create(
            TransitionRouteId.CourseManagementToDetail,
            payload = null
        )
        val second = TransitionPayloadStore.create(
            TransitionRouteId.ManualImportToHistory,
            payload = null
        )
        val firstCleanup = AtomicInteger()
        val secondCleanup = AtomicInteger()
        assertTrue(TransitionPayloadStore.registerCleanup(first.id) { firstCleanup.incrementAndGet() })
        assertTrue(TransitionPayloadStore.registerCleanup(second.id) { secondCleanup.incrementAndGet() })

        TransitionPayloadStore.remove(first.id)
        TransitionPayloadStore.remove(first.id)

        assertEquals(1, firstCleanup.get())
        assertEquals(0, secondCleanup.get())
        assertSame(second, TransitionPayloadStore.session(second.id))

        TransitionPayloadStore.remove(second.id)
        assertEquals(1, secondCleanup.get())
    }

    @Test
    fun sourceReleaseIsSessionScopedAndRunsExactlyOnce() {
        val firstRelease = AtomicInteger()
        val secondRelease = AtomicInteger()
        val first = TransitionPayloadStore.create(
            TransitionRouteId.CourseManagementToDetail,
            TransitionPayload(
                openingAnchor = null,
                onSourceReleased = { firstRelease.incrementAndGet() }
            )
        )
        val second = TransitionPayloadStore.create(
            TransitionRouteId.AiProgressToHistory,
            TransitionPayload(
                openingAnchor = null,
                onSourceReleased = { secondRelease.incrementAndGet() }
            )
        )

        TransitionPayloadStore.remove(first.id)
        TransitionPayloadStore.remove(first.id)

        assertEquals(1, firstRelease.get())
        assertEquals(0, secondRelease.get())
        TransitionPayloadStore.remove(second.id)
        assertEquals(1, secondRelease.get())
    }

    @Test
    fun openingSourceHandoffRunsExactlyOnceAndIsForcedDuringCleanup() {
        val handoff = AtomicInteger()
        val session = TransitionPayloadStore.create(
            TransitionRouteId.HomeToCourseManagement,
            TransitionPayload(
                openingAnchor = null,
                onOpeningSourceHandoff = { handoff.incrementAndGet() },
                nativeSourceLeashAlphaOutOnOpen = true
            )
        )

        TransitionPayloadStore.handoffOpeningSource(session.id)
        TransitionPayloadStore.handoffOpeningSource(session.id)
        TransitionPayloadStore.remove(session.id)

        assertEquals(1, handoff.get())
    }

    @Test
    fun unclaimedOpeningSourceHandoffIsReleasedWhenSessionIsRemoved() {
        val handoff = AtomicInteger()
        val session = TransitionPayloadStore.create(
            TransitionRouteId.HomeToEduImport,
            TransitionPayload(
                openingAnchor = null,
                onOpeningSourceHandoff = { handoff.incrementAndGet() }
            )
        )

        TransitionPayloadStore.remove(session.id)
        TransitionPayloadStore.remove(session.id)

        assertEquals(1, handoff.get())
    }

    @Test
    fun lateCleanupRegistrationIsExecutedInsteadOfLeaked() {
        val session = TransitionPayloadStore.create(
            TransitionRouteId.CourseManagementToDetail,
            payload = null
        )
        TransitionPayloadStore.remove(session.id)
        val cleanup = AtomicInteger()

        assertFalse(TransitionPayloadStore.registerCleanup(session.id) { cleanup.incrementAndGet() })
        assertEquals(1, cleanup.get())
    }

    @Test
    fun nestedSessionRemovalDoesNotRemoveItsParent() {
        val parent = TransitionPayloadStore.create(
            TransitionRouteId.HomeToCourseManagement,
            payload = null
        )
        val child = TransitionPayloadStore.create(
            routeId = TransitionRouteId.CourseManagementToDetail,
            payload = null,
            parentSessionId = parent.id
        )

        assertEquals(parent.id, child.parentSessionId)
        TransitionPayloadStore.remove(child.id)
        assertSame(parent, TransitionPayloadStore.session(parent.id))
    }

    @Test
    fun unknownSessionAfterProcessRecreationHasNoTransientPayload() {
        val restoredId = TransitionSessionId("not-in-this-process")
        assertNull(TransitionPayloadStore.session(restoredId))
        assertNull(TransitionPayloadStore.payload(restoredId))
    }

    @Test
    fun openingAndReturnAnchorsRemainIndependent() {
        val opening = TransitionAnchorFrame(Rect(10f, 20f, 110f, 80f), 30f, bitmap = null)
        val returning = TransitionAnchorFrame(Rect(300f, 40f, 342f, 82f), 21f, bitmap = null)
        val payload = TransitionPayload(
            openingAnchor = opening,
            returnAnchorProvider = StaticTransitionAnchorProvider(returning)
        )

        assertSame(opening, payload.openingAnchor)
        assertSame(returning, payload.returnAnchorProvider?.resolve())
        assertFalse(opening.boundsInWindow == returning.boundsInWindow)
    }

    @Test
    fun nativeSourceViewProviderRemainsSessionScopedAndLive() {
        val resolutions = AtomicInteger()
        val provider = TransitionNativeSourceViewProvider {
            resolutions.incrementAndGet()
            null
        }
        val payload = TransitionPayload(
            openingAnchor = null,
            nativeSourceViewProvider = provider
        )
        val session = TransitionPayloadStore.create(
            TransitionRouteId.CourseManagementToDetail,
            payload
        )

        val stored = TransitionPayloadStore.payload(session.id)
        assertSame(provider, stored?.nativeSourceViewProvider)
        assertNull(stored?.nativeSourceViewProvider?.resolve())
        assertNull(stored?.nativeSourceViewProvider?.resolve())
        assertEquals(2, resolutions.get())
    }

    @Test
    fun boundsWithoutASoftwareSnapshotAreNotNativeReady() {
        val geometryOnly = TransitionAnchorFrame(Rect(0f, 0f, 100f, 100f), 20f, bitmap = null)
        assertFalse(geometryOnly.isValid)
    }
}
