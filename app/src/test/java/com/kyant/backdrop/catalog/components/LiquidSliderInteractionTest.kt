package com.kyant.backdrop.catalog.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiquidSliderInteractionTest {
    @Test
    fun slowDragMapsEveryAbsolutePositionWithoutLag() {
        val values = (10..90).map { x ->
            LiquidSliderMath.valueFromPosition(x.toFloat(), 100f, 10f, 0f, 1f, true)
        }
        values.zipWithNext().forEach { (previous, next) -> assertTrue(next > previous) }
        assertEquals(0f, values.first(), 0.0001f)
        assertEquals(1f, values.last(), 0.0001f)
    }

    @Test
    fun rapidJumpUsesLatestPointerPositionExactly() {
        val start = LiquidSliderMath.valueFromPosition(18f, 100f, 10f, 0f, 100f, true)
        val end = LiquidSliderMath.valueFromPosition(82f, 100f, 10f, 0f, 100f, true)
        assertEquals(10f, start, 0.0001f)
        assertEquals(90f, end, 0.0001f)
    }

    @Test
    fun rightToLeftMappingIsMirrored() {
        assertEquals(1f, LiquidSliderMath.valueFromPosition(10f, 100f, 10f, 0f, 1f, false), 0.0001f)
        assertEquals(0f, LiquidSliderMath.valueFromPosition(90f, 100f, 10f, 0f, 1f, false), 0.0001f)
    }

    @Test
    fun valueAndPositionMappingRoundTrips() {
        val position = LiquidSliderMath.positionForValue(0.37f, 320f, 10f, 0f, 1f, true)
        val value = LiquidSliderMath.valueFromPosition(position, 320f, 10f, 0f, 1f, true)
        assertEquals(0.37f, value, 0.0001f)
    }

    @Test
    fun interruptedAnimationCannotCommitItsOldTarget() {
        val gate = LiquidSliderCommitGate()
        val oldAnimation = gate.next()
        val newGesture = gate.next()
        assertFalse(gate.isCurrent(oldAnimation))
        assertTrue(gate.isCurrent(newGesture))
    }
}
