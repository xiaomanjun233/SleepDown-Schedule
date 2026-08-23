package com.xiaomanjun.sleepdownschedule.glass

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Lifecycle of expensive course-card material nodes while an exact cached home frame covers them.
 * Card content, layout, input and semantics stay composed in every phase.
 */
enum class CourseGlassOcclusionPhase {
    Live,
    Suspended,
    Prewarming;

    val mountsMaterialNodes: Boolean
        get() = this != Suspended
}

/**
 * Restore course-card material while the closing background still carries enough blur to hide
 * shader/layer warm-up.  Waiting until the final fifth made the allocation burst visible as a
 * tail hitch even though the cards themselves had already reached their resting geometry.
 */
internal const val CourseGlassClosingPrewarmProgress = 0.40f

val LocalCourseGlassOcclusionPhase = staticCompositionLocalOf {
    CourseGlassOcclusionPhase.Live
}

internal fun shouldSuspendCourseGlassMaterials(
    experimentEnabled: Boolean,
    weekMode: Boolean,
    exactCacheCoverActive: Boolean,
    substantialOverlayActive: Boolean
): Boolean = experimentEnabled && weekMode && exactCacheCoverActive && substantialOverlayActive

internal fun shouldBeginCourseGlassPrewarm(
    phase: CourseGlassOcclusionPhase,
    overlayClosing: Boolean,
    closingProgress: Float?
): Boolean =
    phase == CourseGlassOcclusionPhase.Suspended &&
        overlayClosing &&
        closingProgress != null &&
        closingProgress <= CourseGlassClosingPrewarmProgress
