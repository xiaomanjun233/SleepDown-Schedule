package com.xiaomanjun.sleepdownschedule.feature.home

import androidx.compose.animation.core.spring
import kotlin.math.PI
import kotlin.math.sin

/** Shared timing and reversible curves for Home. Renderers do not select motion policies. */
internal object HomeMotionSpec {
    const val FollowGroups = 6
    val pageSpring = spring<Float>(dampingRatio = 0.90f, stiffness = 320f, visibilityThreshold = 0.0015f)

    /** A bounded spatial stagger from one clock, continuous on reversal and at both endpoints. */
    fun followProgress(progress: Float, group: Int): Float {
        if (progress == 0f || progress == 1f) return progress
        val weight = group.coerceIn(0, FollowGroups - 1).toFloat() / (FollowGroups - 1)
        return progress - sin(PI.toFloat() * progress) * 0.075f * weight
    }

    /** The same curve in both directions; an interrupted overlay never switches blur recipes. */
    fun backdropProgress(progress: Float): Float {
        val p = progress.coerceIn(0f, 1f)
        return p * p * (3f - 2f * p)
    }
}
