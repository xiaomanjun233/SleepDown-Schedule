package com.itos.coui.swipereveal

import androidx.compose.animation.core.spring
import androidx.compose.ui.unit.dp

/** SystemUI 侧滑的融合、跟随与停靠预设; 不处理删除确认或业务提交。 */
object CouiSwipeRevealDefaults {
    val fusedActionSize = 38.dp
    const val fusedRevealMaxBlurPx: Float = 15f
    const val fusedRevealStart: Float = 0.5f
    val menuFollowingDistance = 8.dp
    val menuMinimumGap = 10.dp
    val openTriggerDistance = 4.dp
    val itemMaxVelocity = 4000.dp
    val metaballBlendRange = 3.5.dp

    // COUISpringForce: damping=1-bounce, k=(2π/response)²。
    val menuReboundSpec = spring<Float>(
        dampingRatio = 0.45f,
        stiffness = 246.74011f,
        visibilityThreshold = 0.00075f,
    )
    val itemSettleSpec = spring<Float>(dampingRatio = 0.75f, stiffness = 200f)
}
