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
