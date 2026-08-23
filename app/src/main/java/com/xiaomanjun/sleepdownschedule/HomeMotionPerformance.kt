package com.xiaomanjun.sleepdownschedule

import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * AOSP's GPU blur pipeline downsamples before applying the blur kernel. Half resolution cuts the
 * full-screen sample surface to one quarter while remaining visually lossless under a 12dp blur.
 */
internal const val HomeFrozenBlurSampleScale = 0.5f

/** Only used by the live preview fallback; reusing these effects avoids per-frame shader churn. */
internal const val HomeLiveBlurStepCount = 12

/** Opening reaches the full blur early enough to cover the material-node handoff. */
internal const val HomeOpeningBlurFullProgress = 0.38f

/** Lets Closing release blur earlier while preserving zero velocity at both endpoints. */
internal const val HomeClosingBlurProgressExponent = 0.55f

/**
 * The personalization shell and its large-screen aura use the same bounded blur progression as
 * the home scene. This keeps their visual timing continuous while preventing a new backdrop
 * effect chain from being configured for every animation frame.
 */
internal const val HomeProgressiveBackdropBlurStepCount = HomeLiveBlurStepCount

internal fun quantizeHomeProgressiveBackdropBlurProgress(progress: Float): Float =
    (progress.coerceIn(0f, 1f) * HomeProgressiveBackdropBlurStepCount)
        .roundToInt()
        .toFloat() / HomeProgressiveBackdropBlurStepCount

/**
 * Decouples background blur timing from the deliberately trailing zoom curve. Opening advances
 * through the existing bounded blur levels immediately. Closing uses a reversible, endpoint-smooth
 * bias: it stays near maximum early, then releases across the rest of the motion instead of
 * dropping all levels in a short tail. The legacy zoom-derived depth remains a lower bound, so no
 * route loses blur that it already had.
 */
internal fun stagedHomeOverlayBlurProgress(
    legacyDepthProgress: Float,
    morphProgress: Float?,
    closing: Boolean
): Float {
    val legacy = legacyDepthProgress.coerceIn(0f, 1f)
    val progress = morphProgress?.coerceIn(0f, 1f) ?: return legacy
    val staged = if (closing) {
        val biasedProgress = if (progress <= 0f) {
            0f
        } else {
            progress.pow(HomeClosingBlurProgressExponent)
        }
        smootherStep(biasedProgress)
    } else {
        smoothStep(0f, HomeOpeningBlurFullProgress, progress)
    }
    return maxOf(legacy, staged).coerceIn(0f, 1f)
}

private fun smoothStep(edge0: Float, edge1: Float, value: Float): Float {
    val width = (edge1 - edge0).coerceAtLeast(0.0001f)
    val t = ((value - edge0) / width).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

private fun smootherStep(value: Float): Float {
    val t = value.coerceIn(0f, 1f)
    return t * t * t * (t * (t * 6f - 15f) + 10f)
}

/**
 * Decides whether the already-recorded home GPU layer can safely stand in for the live week tree.
 * Preview interactions deliberately bypass the cache so sliders still update the real schedule.
 */
internal fun shouldReuseWeekHomeSurface(
    screenIsHome: Boolean,
    homeMode: HomeMode,
    previewActive: Boolean,
    overlayActive: Boolean,
    cachedScheduleId: Int,
    currentScheduleId: Int,
    cachedFrameKey: String?,
    currentFrameKey: String
): Boolean =
    shouldUseFrozenWeekHomeBlur(
        screenIsHome = screenIsHome,
        homeMode = homeMode,
        previewActive = previewActive,
        overlayActive = overlayActive
    ) &&
        cachedScheduleId == currentScheduleId &&
        cachedFrameKey == currentFrameKey

/**
 * The blur cache can be prepared on the same draw that refreshes the underlying week scene, so it
 * intentionally does not wait for the full-resolution scene key to be marked ready.
 */
internal fun shouldUseFrozenWeekHomeBlur(
    screenIsHome: Boolean,
    homeMode: HomeMode,
    previewActive: Boolean,
    overlayActive: Boolean
): Boolean =
    homeMode == HomeMode.Week &&
        shouldUseFrozenHomeMorphBlur(
            screenIsHome = screenIsHome,
            previewActive = previewActive,
            overlayActive = overlayActive
        )

/** Every Home Morph shares the downsampled GPU blur path; live slider preview is the exception. */
internal fun shouldUseFrozenHomeMorphBlur(
    screenIsHome: Boolean,
    previewActive: Boolean,
    overlayActive: Boolean
): Boolean = screenIsHome && !previewActive && overlayActive

/** Slider preview must keep its original live backdrop and may never inherit transition blur. */
internal fun shouldUseStagedHomeOverlayBlur(
    previewActive: Boolean,
    substantialOverlayActive: Boolean
): Boolean = !previewActive && substantialOverlayActive

/** Keeps cached and live background paths on the exact same depth/blur curve. */
internal fun homeOverlayDepthProgress(zoom: Float): Float {
    val depthRange = maxOf(
        abs(BackgroundZoomOpenScale - 1f),
        abs(HomeMenuDestinationEduBackgroundScale - 1f)
    ).coerceAtLeast(0.001f)
    return (abs(zoom - 1f) / depthRange).coerceIn(0f, 1f)
}
