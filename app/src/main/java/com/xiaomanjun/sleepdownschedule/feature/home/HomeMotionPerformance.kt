package com.xiaomanjun.sleepdownschedule.feature.home

import com.xiaomanjun.sleepdownschedule.transition.legacy.HomeMenuDestinationEduBackgroundScale

import com.xiaomanjun.sleepdownschedule.*
import com.xiaomanjun.sleepdownschedule.feature.home.overlay.*
import com.xiaomanjun.sleepdownschedule.glass.ui.*
import com.xiaomanjun.sleepdownschedule.model.HomeMode
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Keep one one-third-resolution blur surface throughout the frozen transition. The clear
 * endpoint is drawn from the original full-resolution scene.
 */
internal const val HomeFrozenBlurSampleScale = 1f / 3f
internal const val HomeSceneBlurSteps = 32
internal const val HomeSceneMinimumBlurStep = 3

/**
 * The personalization shell and its large-screen aura use the same bounded blur progression as
 * the home scene. This keeps their visual timing continuous while preventing a new backdrop
 * effect chain from being configured for every animation frame.
 */
internal const val HomeProgressiveBackdropBlurStepCount = 12

internal fun quantizeHomeProgressiveBackdropBlurProgress(progress: Float): Float =
    (progress.coerceIn(0f, 1f) * HomeProgressiveBackdropBlurStepCount)
        .roundToInt()
        .toFloat() / HomeProgressiveBackdropBlurStepCount

/** The overlay's geometry is the clock; entering and leaving use the same material curve. */
internal fun stagedHomeOverlayBlurProgress(
    legacyDepthProgress: Float,
    morphProgress: Float?,
): Float = maxOf(
    legacyDepthProgress.coerceIn(0f, 1f),
    morphProgress?.let { HomeMotionSpec.backdropProgress(it / 0.38f) } ?: 0f
)

/**
 * Decides whether the already-recorded home GPU layer can safely stand in for the live week tree.
 * Preview interactions deliberately bypass the cache so sliders still update the real schedule.
 */
internal fun shouldReuseHomeSurface(
    screenIsHome: Boolean,
    previewActive: Boolean,
    overlayActive: Boolean,
    cachedScheduleId: Int,
    currentScheduleId: Int,
    cachedFrameKey: String?,
    currentFrameKey: String
): Boolean =
    shouldUseFrozenHomeMorphBlur(
        screenIsHome = screenIsHome,
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

/** The host supplies editor depth; Home does not read another feature's motion parameters. */
internal fun homeOverlayDepthProgress(zoom: Float, editorBackgroundScale: Float): Float {
    val depthRange = maxOf(
        abs(editorBackgroundScale - 1f),
        abs(HomeMenuDestinationEduBackgroundScale - 1f)
    ).coerceAtLeast(0.001f)
    return (abs(zoom - 1f) / depthRange).coerceIn(0f, 1f)
}
