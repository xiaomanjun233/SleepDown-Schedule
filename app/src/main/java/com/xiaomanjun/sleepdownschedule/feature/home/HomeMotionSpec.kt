package com.xiaomanjun.sleepdownschedule.feature.home

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

/** Timing belongs to the scene, independent of material and the number of cards. */
internal object HomeMotionSpec {
    const val FollowGroups = 6
    const val FollowDelayMillis = 18
    val page = tween<Float>(360, easing = CubicBezierEasing(0.22f, 0.72f, 0.20f, 1f))
    val follower = spring<Float>(dampingRatio = 0.74f, stiffness = 260f, visibilityThreshold = 0.0015f)

    fun backdropProgress(progress: Float): Float {
        val p = progress.coerceIn(0f, 1f)
        return p * p * (3f - 2f * p)
    }
}
