package com.xiaomanjun.sleepdownschedule

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * AOSP's GPU blur pipeline downsamples before applying the blur kernel. Half resolution cuts the
 * full-screen sample surface to one quarter while remaining visually lossless under a 12dp blur.
 */
internal const val HomeFrozenBlurSampleScale = 0.5f

/** Only used by the live preview fallback; reusing these effects avoids per-frame shader churn. */
internal const val HomeLiveBlurStepCount = 12

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

/** Keeps cached and live background paths on the exact same depth/blur curve. */
internal fun homeOverlayDepthProgress(zoom: Float): Float {
    val depthRange = maxOf(
        abs(BackgroundZoomOpenScale - 1f),
        abs(HomeMenuDestinationEduBackgroundScale - 1f)
    ).coerceAtLeast(0.001f)
    return (abs(zoom - 1f) / depthRange).coerceIn(0f, 1f)
}
