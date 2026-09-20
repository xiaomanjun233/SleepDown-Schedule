package com.xiaomanjun.sleepdownschedule
import com.xiaomanjun.sleepdownschedule.app.ui.*
import com.xiaomanjun.sleepdownschedule.feature.home.*
import com.xiaomanjun.sleepdownschedule.feature.home.overlay.*
import com.xiaomanjun.sleepdownschedule.feature.course.editor.*

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
        assertEquals(0f, homeOverlayDepthProgress(1f), 0.0001f)
        assertEquals(1f, homeOverlayDepthProgress(BackgroundZoomOpenScale), 0.0001f)
        assertEquals(1f, homeOverlayDepthProgress(HomeMenuDestinationEduBackgroundScale), 0.0001f)
    }

    @Test
    fun substantialOverlayBlurLeadsOpeningAndReleasesBeforeClosingTail() {
        assertTrue(
            stagedHomeOverlayBlurProgress(
                legacyDepthProgress = 0f,
                morphProgress = 0.2f,
                closing = false
            ) > 0.5f
        )
        assertEquals(
            1f,
            stagedHomeOverlayBlurProgress(
                legacyDepthProgress = 0f,
                morphProgress = HomeOpeningBlurFullProgress,
                closing = false
            ),
            0.0001f
        )
        val earlyClosingBlur = stagedHomeOverlayBlurProgress(
            legacyDepthProgress = 0f,
            morphProgress = 0.8f,
            closing = true
        )
        val middleClosingBlur = stagedHomeOverlayBlurProgress(
            legacyDepthProgress = 0f,
            morphProgress = 0.4f,
            closing = true
        )
        assertTrue(earlyClosingBlur > 0.98f)
        assertTrue(middleClosingBlur in 0.67f..0.71f)
        assertTrue(
            stagedHomeOverlayBlurProgress(
                legacyDepthProgress = 0f,
                morphProgress = 0.88f,
                closing = true
            ) > 0.67f
        )
        assertEquals(
            0f,
            stagedHomeOverlayBlurProgress(
                legacyDepthProgress = 0f,
                morphProgress = 0f,
                closing = true
            ),
            0.0001f
        )
        assertEquals(
            0.4f,
            stagedHomeOverlayBlurProgress(
                legacyDepthProgress = 0.4f,
                morphProgress = null,
                closing = false
            ),
            0.0001f
        )
    }

    @Test
    fun frozenWeekBlurUsesQuarterAreaSurfaceAndBoundedLiveEffects() {
        assertEquals(0.25f, HomeFrozenBlurSampleScale * HomeFrozenBlurSampleScale, 0.0001f)
        assertEquals(32, HomeLiveBlurStepCount)
        assertEquals(12, HomeNonClosingBlurStepCount)
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
    fun frozenBlurRevealsOriginalPixelsContinuouslyAtClearEndpoint() {
        assertEquals(3, quantizeHomeBackgroundBlurStep(0.07f, closing = false))
        assertEquals(2, quantizeHomeBackgroundBlurStep(0.07f, closing = true))
        val handoff = HomeFrozenBlurMinimumStep.toFloat() / HomeLiveBlurStepCount
        assertEquals(0f, homeFrozenBlurAlpha(0f), 0.0001f)
        assertEquals(0.5f, homeFrozenBlurAlpha(handoff / 2f), 0.0001f)
        assertEquals(1f, homeFrozenBlurAlpha(handoff), 0.0001f)
        assertEquals(1f, homeFrozenBlurAlpha(1f), 0.0001f)
        assertEquals(0f, homeFrozenBlurAlpha(-1f), 0.0001f)
        assertEquals(1f, homeFrozenBlurAlpha(2f), 0.0001f)
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
