package com.xiaomanjun.sleepdownschedule.glass

import androidx.compose.ui.geometry.Rect
import org.junit.Assert.*
import org.junit.Test

class GlassMotionAllocationTest {
    @Test fun decorationChangesKeepMaterialInputsButBlurAndLensChangesDoNot() {
        val frame = GlassEffectFrame(blur = androidx.compose.ui.unit.Dp(12f), lensHeight = androidx.compose.ui.unit.Dp(20f))
        assertEquals(frame.materialEffectsOnly(), frame.copy(shadowAlpha = 0.4f, layerScale = 0.9f).materialEffectsOnly())
        assertNotEquals(frame.materialEffectsOnly(), frame.copy(blur = androidx.compose.ui.unit.Dp(13f)).materialEffectsOnly())
        assertNotEquals(frame.materialEffectsOnly(), frame.copy(lensHeight = androidx.compose.ui.unit.Dp(21f)).materialEffectsOnly())
        assertNotEquals(frame.materialEffectsOnly(), frame.copy(chromaticAberration = true).materialEffectsOnly())
    }

    @Test fun restoringOneCardDoesNotMountItsSameColumnNeighbours() {
        val first = courseGlassRestoreGroupKey(3, listOf("monday-first"))
        val next = courseGlassRestoreGroupKey(3, listOf("monday-next"))
        val adjacentPage = courseGlassRestoreGroupKey(4, listOf("monday-first"))
        val registry = CourseGlassRestoreRegistry()
        registry.replacePage(3, listOf(
            CourseGlassRestoreGroup(first, 3, 0.1f),
            CourseGlassRestoreGroup(next, 3, 0.1f)
        ), false)
        registry.replacePage(4, listOf(CourseGlassRestoreGroup(adjacentPage, 4, 0.1f)), false)
        val ordered = registry.orderedGroupKeys(3)
        assertEquals(setOf(first, next), ordered.take(2).toSet())
        assertEquals(adjacentPage, ordered.last())
        assertEquals(setOf(first, next), registry.pageKeys(3))
        val revealing = CourseGlassRestorePlan(CourseGlassOcclusionPhase.Revealing, setOf(first, next))
        assertTrue(revealing.mountsGroup(first))
        assertFalse(revealing.mountsGroup(adjacentPage))
        val restoring = CourseGlassRestorePlan(CourseGlassOcclusionPhase.PostCloseRestore, setOf(first))
        assertTrue(restoring.mountsGroup(first))
        assertFalse(restoring.mountsGroup(next))
        assertFalse(restoring.mountsGroup(adjacentPage))
        registry.removePage(4)
        assertEquals(setOf(first, next), registry.orderedGroupKeys(3).toSet())
    }

    @Test fun reversingAtViewportEdgeKeepsMountedMaterialUntilExitBand() {
        val viewport = Rect(0f, 0f, 1000f, 2000f)
        fun decide(distance: Float, mounted: Boolean, previous: Float) = decideCourseGlassViewportMaterial(
            true, mounted, previous, Rect(1000f + distance, 100f, 1100f + distance, 300f),
            viewport, 100f
        )
        assertTrue(decide(0f, true, 0f).mountMaterial)
        assertTrue(decide(180f, true, 150f).mountMaterial)
        assertFalse(decide(201f, true, 180f).mountMaterial)
        assertFalse(decide(150f, false, 201f).mountMaterial)
        assertTrue(decide(99f, false, 150f).mountMaterial)
        assertTrue(decide(150f, true, 99f).mountMaterial)
    }

    @Test fun visibilityNeverRepacksAGroupOrChangesItsTextureBounds() {
        val candidates = (0..11).map { i ->
            GlassGroupCandidate("c$i", GlassBackdropDomain.Content, "same",
                Rect((i % 4) * 120f, (i / 4) * 180f, (i % 4) * 120f + 110f, (i / 4) * 180f + 170f), 12f)
        }
        val plans = GlassGroupPlanner.planSpatialChunks(Rect(0f, 0f, 500f, 600f), candidates,
            maxMembersPerPlan = 8, minimumFillRatio = 0.34f, maximumLayerAreaFraction = 0.58f)
        for (plan in plans) {
            for (member in plan.members) {
                val visible = visibleGlassGroupPlans(plans, setOf(member.id))
                assertEquals(1, visible.size)
                assertSame(plan, visible.single())
                assertEquals(plan.toTightLayerPlan(), visible.single().toTightLayerPlan())
            }
        }
        assertTrue(visibleGlassGroupPlans(plans, emptySet()).isEmpty())
        assertSame(plans, visibleGlassGroupPlans(plans, null))
    }

    @Test fun insetGeometryUsesLegacyRoundedPixelGridAndFixedAllocation() {
        var current = GlassTransitionGeometry(Rect(30.4f, 50.6f, 150.8f, 240.2f), 24f)
        val envelope = GlassTransitionEnvelope(Rect(10f, 20f, 710f, 920f))
        val allocation = GlassMorphAllocation(envelope, { current }, 30f)
        assertEquals(Rect(20f, 31f, 140f, 221f), allocation.localBounds())
        current = GlassTransitionGeometry(Rect(50f, 80f, 650f, 880f), 32f)
        assertEquals(Rect(40f, 60f, 640f, 860f), allocation.localBounds())
        assertSame(envelope, allocation.envelope)
        assertEquals(30f, allocation.paddingPx)
    }

    @Test(expected = IllegalStateException::class)
    fun escapedGeometryCannotSilentlyClipTheMaterial() {
        GlassMorphAllocation(GlassTransitionEnvelope(Rect(0f, 0f, 100f, 100f)),
            { GlassTransitionGeometry(Rect(0f, 0f, 101f, 100f), 10f) }, 10f).localBounds()
    }
}
