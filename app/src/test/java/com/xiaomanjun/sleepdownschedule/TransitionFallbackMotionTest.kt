package com.xiaomanjun.sleepdownschedule

import androidx.compose.animation.core.TargetBasedAnimation
import androidx.compose.animation.core.VectorConverter
import androidx.compose.ui.geometry.Rect
import com.xiaomanjun.sleepdownschedule.feature.home.overlay.homePersonalizationTrajectoryGeometry
import com.xiaomanjun.sleepdownschedule.transition.legacy.PageMorphStyle
import com.xiaomanjun.sleepdownschedule.transition.legacy.SharedViewFallbackMotion
import com.xiaomanjun.sleepdownschedule.transition.legacy.floatingAddReturnGeometry
import com.xiaomanjun.sleepdownschedule.transition.legacy.sharedViewGeometry
import com.xiaomanjun.sleepdownschedule.transition.legacy.sharedViewRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TransitionFallbackMotionTest {
    @Test
    fun floatingAddUsesThePersonalizationCurvesWithABoundedRise() {
        val source = Rect(300f, 710f, 356f, 766f)
        val page = Rect(0f, 0f, 384f, 800f)
        val motion = SharedViewFallbackMotion(PageMorphStyle.FloatingAdd)
        val open = TargetBasedAnimation(motion.open, Float.VectorConverter, 0f, 1f)
        fun at(progress: Float) = sharedViewGeometry(source, page, 28f, 32f, progress, progress, PageMorphStyle.FloatingAdd)

        assertEquals(source, at(open.getValueFromNanos(0)).rect)
        val lifted = at(open.getValueFromNanos(16_000_000L))
        assertTrue(lifted.rect.width > source.width && lifted.rect.width < source.width + 2f)
        assertTrue(lifted.rect.center.y in (source.center.y - 16f)..(source.center.y - 7f))
        assertEquals(1f, lifted.sourceAlpha, 0f)
        for (progress in listOf(.08f, .17f, .32f, .55f, .82f)) {
            val panel = homePersonalizationTrajectoryGeometry(source, page, progress, 32f)
            val floating = at(progress)
            assertEquals(panel.pathProgress, floating.pathProgress, .0001f)
            assertEquals(panel.expansionProgress, floating.expansionProgress, .0001f)
            assertEquals(panel.rect.width, floating.rect.width, .001f)
            val centerAfterExpansion = sharedViewRect(source, page, floating.expansionProgress, floating.expansionProgress).center.y
            assertTrue(floating.rect.center.y in (centerAfterExpansion - source.height)..centerAfterExpansion)
            assertEquals(1f, floating.sourceAlpha + floating.contentAlpha, .0001f)
        }
        assertEquals(page, at(open.getValueFromNanos(open.durationNanos)).rect)
    }

    @Test
    fun floatingAddUsesTheOrdinaryButtonReturnFromTheVisibleFrame() {
        val anchor = Rect(300f, 710f, 356f, 766f)
        val page = Rect(0f, 0f, 384f, 800f)
        val motion = SharedViewFallbackMotion(PageMorphStyle.FloatingAdd)
        val button = SharedViewFallbackMotion(PageMorphStyle.Button)
        assertEquals(button.close, motion.close)
        assertEquals(button.closeVertical, motion.closeVertical)
        val close = TargetBasedAnimation(motion.close, Float.VectorConverter, 1f, 0f)
        val vertical = TargetBasedAnimation(motion.closeVertical, Float.VectorConverter, 1f, 0f)

        for (openingProgress in listOf(.30f, 1f)) {
            val start = sharedViewGeometry(anchor, page, 28f, 32f, openingProgress, openingProgress,
                PageMorphStyle.FloatingAdd)
            fun at(time: Long) = floatingAddReturnGeometry(anchor, 28f, start, close.getValueFromNanos(time),
                vertical.getValueFromNanos(time))
            assertEquals(start, at(0L))
            val moving = at(100_000_000L)
            assertEquals(sharedViewRect(anchor, start.rect, close.getValueFromNanos(100_000_000L),
                vertical.getValueFromNanos(100_000_000L)), moving.rect)
            assertTrue(moving.rect.width > anchor.width)
            assertTrue(moving.rect.center.y < anchor.center.y)
            assertEquals(anchor, at(close.durationNanos).rect)
            assertEquals(1f, at(close.durationNanos).sourceAlpha, 0f)
        }
    }

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
