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

val LocalCourseGlassOcclusionPhase = staticCompositionLocalOf {
    CourseGlassOcclusionPhase.Live
}

internal fun shouldSuspendCourseGlassMaterials(
    experimentEnabled: Boolean,
    weekMode: Boolean,
    exactCacheCoverActive: Boolean,
    overlayStableOpen: Boolean
): Boolean = experimentEnabled && weekMode && exactCacheCoverActive && overlayStableOpen
