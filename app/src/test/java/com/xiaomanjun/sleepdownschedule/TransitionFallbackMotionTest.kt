package com.xiaomanjun.sleepdownschedule

import androidx.compose.animation.core.TargetBasedAnimation
import androidx.compose.animation.core.VectorConverter
import androidx.compose.ui.geometry.Rect
import com.xiaomanjun.sleepdownschedule.transition.legacy.PageMorphStyle
import com.xiaomanjun.sleepdownschedule.transition.legacy.SharedViewFallbackMotion
import com.xiaomanjun.sleepdownschedule.transition.legacy.sharedViewGeometry
import com.xiaomanjun.sleepdownschedule.transition.legacy.sharedViewRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TransitionFallbackMotionTest {
    @Test
    fun buttonReturnsToTheExactAnchorWithSeparateSizeAndPositionCurves() {
        val motion = SharedViewFallbackMotion(PageMorphStyle.Button)
        val close = TargetBasedAnimation(motion.close, Float.VectorConverter, 1f, 0f)
        val vertical = TargetBasedAnimation(motion.closeVertical, Float.VectorConverter, 1f, 0f)
        val source = Rect(300f, 710f, 356f, 766f)
        val page = Rect(0f, 0f, 384f, 800f)
        fun boundsAt(time: Long) = sharedViewRect(source, page, close.getValueFromNanos(time), vertical.getValueFromNanos(time))
        assertEquals(page, boundsAt(0))
        val moving = boundsAt(100_000_000L)
        assertTrue(moving.width in source.width..page.width)
        assertTrue(moving.center.y < sharedViewRect(source, page, close.getValueFromNanos(100_000_000L),
            close.getValueFromNanos(100_000_000L)).center.y)
        assertEquals(source, boundsAt(close.durationNanos))
    }

    @Test
    fun openingKeepsAnOpaqueSurfaceWhileSourceAndPageContentCrossOver() {
        val source = Rect(100f, 712f, 284f, 768f)
        val page = Rect(0f, 0f, 384f, 800f)
        fun at(fraction: Float) = sharedViewGeometry(source, page, 28f, 32f, fraction, fraction, PageMorphStyle.Welcome)
        assertEquals(source, at(0f).rect)
        assertEquals(0f, at(0f).contentAlpha)
        assertTrue(at(0.20f).sourceAlpha > 0f && at(0.20f).contentAlpha > 0f)
        assertEquals(1f, at(0.20f).surfaceAlpha)
        assertEquals(page, at(1f).rect)
        assertEquals(1f, at(1f).contentAlpha)
    }
}
