package com.xiaomanjun.sleepdownschedule.feature.home

import com.xiaomanjun.sleepdownschedule.transition.legacy.HomeMenuDestinationEduBackgroundScale

import com.xiaomanjun.sleepdownschedule.app.ui.*
import com.xiaomanjun.sleepdownschedule.feature.course.editor.*
import com.xiaomanjun.sleepdownschedule.feature.home.*
import com.xiaomanjun.sleepdownschedule.feature.home.overlay.*
import com.xiaomanjun.sleepdownschedule.model.HomeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeMotionPerformancePolicyTest {
    @Test
    fun matchingHomeFrameIsReusedWhileOverlayIsActive() {
        assertTrue(reuse(overlayActive = true))
    }

    @Test
    fun idleHomeStaysLive() {
        assertTrue(reuse(overlayActive = true))
        assertFalse(reuse(overlayActive = false))
    }

    @Test
    fun personalizationPreviewAlwaysUsesLiveHome() {
        assertFalse(reuse(overlayActive = true, previewActive = true))
        assertFalse(
            shouldUseFrozenWeekHomeBlur(
                screenIsHome = true,
                homeMode = HomeMode.Week,
                previewActive = true,
                overlayActive = true
            )
        )
    }

    @Test
    fun staleScheduleOrFrameIsNeverReused() {
        assertFalse(reuse(overlayActive = true, cachedScheduleId = 8))
        assertFalse(reuse(overlayActive = true, cachedFrameKey = "old"))
    }

    @Test
    fun cachedAndLiveDepthCurveUsesTheSameEndpoints() {
        assertEquals(0f, homeOverlayDepthProgress(1f, CourseEditorMotion.BackgroundZoomOpenScale), 0.0001f)
        assertEquals(1f, homeOverlayDepthProgress(CourseEditorMotion.BackgroundZoomOpenScale, CourseEditorMotion.BackgroundZoomOpenScale), 0.0001f)
        assertEquals(1f, homeOverlayDepthProgress(HomeMenuDestinationEduBackgroundScale, CourseEditorMotion.BackgroundZoomOpenScale), 0.0001f)
        // Changing the editor's depth must also update the shared backdrop normalization.
        assertEquals(0.5f, homeOverlayDepthProgress(1.08f, editorBackgroundScale = 1.16f), 0.0001f)
    }

    @Test
    fun overlayBlurHasOneContinuousCurveInBothDirections() {
        val opening = (0..100).map { stagedHomeOverlayBlurProgress(0f, it / 100f) }
        val closing = (100 downTo 0).map { stagedHomeOverlayBlurProgress(0f, it / 100f) }
        assertEquals(opening, closing.reversed())
        assertEquals(0f, opening.first(), 0f)
        assertEquals(1f, opening.last(), 0f)
        assertTrue(opening.zipWithNext().all { (a, b) -> b >= a && b - a < 0.04f })
        assertEquals(0.4f, stagedHomeOverlayBlurProgress(0.4f, null), 0f)
        assertEquals(1f, stagedHomeOverlayBlurProgress(0f, 0.38f), 0f)
        assertEquals(0.8f, stagedHomeOverlayBlurProgress(0.8f, 0.05f), 0f)
    }

    @Test
    fun frozenWeekBlurUsesOneNinthAreaSurfaceAndBoundedLiveEffects() {
        assertEquals(1f / 9f, HomeFrozenBlurSampleScale * HomeFrozenBlurSampleScale, 0.0001f)
        assertEquals(12, HomeProgressiveBackdropBlurStepCount)
        assertTrue(
            shouldUseFrozenWeekHomeBlur(
                screenIsHome = true,
                homeMode = HomeMode.Week,
                previewActive = false,
                overlayActive = true
            )
        )
        assertTrue(
            shouldUseFrozenHomeMorphBlur(
                screenIsHome = true,
                previewActive = false,
                overlayActive = true
            )
        )
    }

    @Test
    fun dayAndWeekMorphsShareFrozenBlurButIdleHomeDoesNot() {
        assertTrue(
            shouldUseFrozenHomeMorphBlur(
                screenIsHome = true,
                previewActive = false,
                overlayActive = true
            )
        )
        assertFalse(
            shouldUseFrozenHomeMorphBlur(
                screenIsHome = true,
                previewActive = false,
                overlayActive = false
            )
        )
    }

    @Test
    fun personalizationSliderPreviewNeverUsesTransitionBlur() {
        assertFalse(
            shouldUseStagedHomeOverlayBlur(
                previewActive = true,
                substantialOverlayActive = true
            )
        )
        assertTrue(
            shouldUseStagedHomeOverlayBlur(
                previewActive = false,
                substantialOverlayActive = true
            )
        )
        assertFalse(
            shouldUseStagedHomeOverlayBlur(
                previewActive = false,
                substantialOverlayActive = false
            )
        )
    }

    @Test
    fun progressivePersonalizationBlurUsesStableBoundedBuckets() {
        assertEquals(0f, quantizeHomeProgressiveBackdropBlurProgress(-1f), 0.0001f)
        assertEquals(1f, quantizeHomeProgressiveBackdropBlurProgress(2f), 0.0001f)
        assertEquals(0.5f, quantizeHomeProgressiveBackdropBlurProgress(0.5f), 0.0001f)
        assertEquals(
            1f / HomeProgressiveBackdropBlurStepCount,
            quantizeHomeProgressiveBackdropBlurProgress(0.07f),
            0.0001f
        )
    }

    private fun reuse(
        overlayActive: Boolean,
        previewActive: Boolean = false,
        cachedScheduleId: Int = 7,
        cachedFrameKey: String = "frame"
    ): Boolean = shouldReuseHomeSurface(
        screenIsHome = true,
        previewActive = previewActive,
        overlayActive = overlayActive,
        cachedScheduleId = cachedScheduleId,
        currentScheduleId = 7,
        cachedFrameKey = cachedFrameKey,
        currentFrameKey = "frame"
    )
}
