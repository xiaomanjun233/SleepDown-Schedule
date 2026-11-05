package com.xiaomanjun.sleepdownschedule.feature.importing.progress

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.*
import org.junit.Test

class EduBrowserWindowMotionTest {
    @Test fun bodyHasSmallDragLagThenSettlesAtTheFinger() {
        val motion = EduBrowserWindowMotion()
        motion.updateBounds(Offset(300f, 600f))
        motion.moveToFraction(Offset.Zero)
        motion.beginDrag()
        motion.dragBy(Offset(100f, 80f))
        motion.advance(1f / 60f)
        assertTrue(motion.position.x > 0 && motion.position.x < 100)
        repeat(120) { motion.advance(1f / 60f) }
        assertEquals(100f, motion.position.x, 0.5f)
        assertEquals(80f, motion.position.y, 0.5f)
        assertFalse(motion.active)
    }

    @Test fun flingReflectsAtWallsAndDampsWithoutLeavingTheAllowedArea() {
        val motion = EduBrowserWindowMotion()
        motion.updateBounds(Offset(300f, 600f))
        motion.moveToFraction(Offset(0.9f, 0.9f))
        motion.release(Offset(4000f, 5000f), 6000f)
        var previous = motion.position
        var bouncedX = false
        var bouncedY = false
        repeat(240) {
            motion.advance(1f / 60f)
            val point = motion.position
            assertTrue(point.x in 0f..300f && point.y in 0f..600f)
            if (point.x < previous.x) bouncedX = true
            if (point.y < previous.y) bouncedY = true
            previous = point
        }
        assertTrue(bouncedX && bouncedY)
        assertFalse(motion.active)
    }

    @Test fun keyboardOrWindowResizeClampsTheBodyAndStopsAnOldFlight() {
        val motion = EduBrowserWindowMotion()
        motion.updateBounds(Offset(300f, 600f))
        motion.moveToFraction(Offset(1f, 1f))
        motion.release(Offset(5000f, 5000f), 6000f)
        motion.updateBounds(Offset(80f, 120f))
        assertEquals(Offset(80f, 120f), motion.position)
        assertFalse(motion.active)
        motion.advance(1f)
        assertEquals(Offset(80f, 120f), motion.position)
    }
}
