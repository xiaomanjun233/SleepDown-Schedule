package com.xiaomanjun.sleepdownschedule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeMotionPerformancePolicyTest {
    @Test
    fun matchingWeekFrameIsReusedWhileOverlayIsActive() {
        assertTrue(reuse(mode = HomeMode.Week, overlayActive = true))
    }

    @Test
    fun dayModeAndIdleWeekStayLive() {
        assertFalse(reuse(mode = HomeMode.Day, overlayActive = true))
        assertFalse(reuse(mode = HomeMode.Week, overlayActive = false))
    }

    @Test
    fun personalizationPreviewAlwaysUsesLiveHome() {
        assertFalse(reuse(mode = HomeMode.Week, overlayActive = true, previewActive = true))
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
        assertFalse(reuse(mode = HomeMode.Week, overlayActive = true, cachedScheduleId = 8))
        assertFalse(reuse(mode = HomeMode.Week, overlayActive = true, cachedFrameKey = "old"))
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
    fun closingReturnsToFullResolutionBeforeBlurReachesClearEndpoint() {
        assertEquals(3, quantizeHomeBackgroundBlurStep(0.07f, closing = false))
        assertEquals(2, quantizeHomeBackgroundBlurStep(0.07f, closing = true))
        assertFalse(
            shouldUseFullResolutionClosingBlur(
                frozenHomeScene = true,
                closing = false,
                blurProgress = 0.2f
            )
        )
        assertFalse(
            shouldUseFullResolutionClosingBlur(
                frozenHomeScene = true,
                closing = true,
                blurProgress = HomeClosingFullResolutionBlurHandoffProgress + 0.01f
            )
        )
        assertTrue(
            shouldUseFullResolutionClosingBlur(
                frozenHomeScene = true,
                closing = true,
                blurProgress = HomeClosingFullResolutionBlurHandoffProgress
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

    @Test
    fun courseEditorWaitsForTwoRecordedTargetFrames() {
        assertFalse(
            courseEditorContentReadyForMotion(
                rootWidth = 1080,
                rootHeight = 2400,
                contentLaidOut = true,
                recordedFrameCount = 1
            )
        )
        assertTrue(
            courseEditorContentReadyForMotion(
                rootWidth = 1080,
                rootHeight = 2400,
                contentLaidOut = true,
                recordedFrameCount = 2
            )
        )
    }

    @Test
    fun courseEditorNeverStartsBeforeTargetLayout() {
        assertFalse(
            courseEditorContentReadyForMotion(
                rootWidth = 1080,
                rootHeight = 2400,
                contentLaidOut = false,
                recordedFrameCount = 3
            )
        )
        assertFalse(
            courseEditorContentReadyForMotion(
                rootWidth = 0,
                rootHeight = 2400,
                contentLaidOut = true,
                recordedFrameCount = 3
            )
        )
    }

    private fun reuse(
        mode: HomeMode,
        overlayActive: Boolean,
        previewActive: Boolean = false,
        cachedScheduleId: Int = 7,
        cachedFrameKey: String = "frame"
    ): Boolean = shouldReuseWeekHomeSurface(
        screenIsHome = true,
        homeMode = mode,
        previewActive = previewActive,
        overlayActive = overlayActive,
        cachedScheduleId = cachedScheduleId,
        currentScheduleId = 7,
        cachedFrameKey = cachedFrameKey,
        currentFrameKey = "frame"
    )
}
