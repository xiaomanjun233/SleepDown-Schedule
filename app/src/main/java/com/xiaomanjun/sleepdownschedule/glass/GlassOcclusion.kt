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
 * Eight spatial restore waves leave enough Closing frames to warm the current week first and the
 * two retained Pager neighbours afterwards.  The overlay is still substantially blurred when the
 * final wave is reached, so the live handoff no longer owns any Kyant node allocation work.
 */
internal const val CourseGlassRestoreWaveCount = 8
internal const val CourseGlassClosingPrewarmProgress = 0.88f

private val CourseGlassRestoreThresholds = floatArrayOf(
    0.88f,
    0.78f,
    0.68f,
    0.58f,
    0.48f,
    0.40f,
    0.34f,
    0.28f
)

internal fun courseGlassRestoreWave(closingProgress: Float?): Int {
    val progress = closingProgress ?: return 0
    return CourseGlassRestoreThresholds.count { progress <= it }
        .coerceIn(0, CourseGlassRestoreWaveCount)
}

/**
 * Centre-out within a page, then the same centre-out order for the retained adjacent pages.
 * This keeps the page currently revealed by Closing complete before warming invisible neighbours.
 */
internal fun courseGlassColumnRestoreWave(
    targetWeek: Int,
    pageWeek: Int,
    columnIndex: Int,
    columnCount: Int
): Int {
    val count = columnCount.coerceAtLeast(1)
    val index = columnIndex.coerceIn(0, count - 1)
    val centre = (count - 1) / 2f
    val nearestDistance = if (count % 2 == 0) 0.5f else 0f
    val centreOutWave = 1 + kotlin.math.round(
        (kotlin.math.abs(index - centre) - nearestDistance).coerceAtLeast(0f)
    ).toInt()
    return if (pageWeek == targetWeek) {
        centreOutWave.coerceIn(1, CourseGlassRestoreWaveCount / 2)
    } else {
        (centreOutWave + CourseGlassRestoreWaveCount / 2)
            .coerceIn(1, CourseGlassRestoreWaveCount)
    }
}

data class CourseGlassRestorePlan(
    val phase: CourseGlassOcclusionPhase = CourseGlassOcclusionPhase.Live,
    val targetWeek: Int = 1,
    val restoredWave: Int = CourseGlassRestoreWaveCount
) {
    fun mountsColumn(pageWeek: Int, columnIndex: Int, columnCount: Int): Boolean = when (phase) {
        CourseGlassOcclusionPhase.Live -> true
        CourseGlassOcclusionPhase.Suspended -> false
        CourseGlassOcclusionPhase.Prewarming ->
            courseGlassColumnRestoreWave(targetWeek, pageWeek, columnIndex, columnCount) <=
                restoredWave
    }
}

val LocalCourseGlassOcclusionPhase = staticCompositionLocalOf {
    CourseGlassOcclusionPhase.Live
}

val LocalCourseGlassRestorePlan = staticCompositionLocalOf {
    CourseGlassRestorePlan()
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
