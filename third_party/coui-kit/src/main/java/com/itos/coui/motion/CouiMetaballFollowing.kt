package com.itos.coui.motion

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import com.itos.coui.swipereveal.CouiSwipeRevealDefaults
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch

/** 距离递增时跟随, 越过主体尺寸与跟随距离之和后从当前位置回弹; 距离归零时重置。 */
@Composable
fun rememberCouiMetaballFollowingTranslation(
    distancePx: () -> Float,
    subjectSizePx: Float,
    followingDistancePx: Float,
    reboundSpec: AnimationSpec<Float> = CouiSwipeRevealDefaults.menuReboundSpec,
): State<Float> {
    require(subjectSizePx.isFinite() && subjectSizePx >= 0f) { "跟随主体尺寸必须是有限的非负值" }
    require(followingDistancePx.isFinite() && followingDistancePx >= 0f) { "跟随距离必须是有限的非负值" }
    val totalDistance = subjectSizePx + followingDistancePx
    require(totalDistance.isFinite() && totalDistance > 0f) { "主体尺寸与跟随距离之和必须是有限的正值" }
    val translation = remember { Animatable(0f) }
    val currentReveal by rememberUpdatedState(distancePx)
    LaunchedEffect(subjectSizePx, followingDistancePx, reboundSpec) {
        val followEnd = subjectSizePx + followingDistancePx
        val followRatio = followingDistancePx / followEnd
        var previousReveal = 0f
        var rebound: Job? = null
        snapshotFlow { currentReveal() }.collect { reveal ->
            require(reveal.isFinite()) { "主体距离必须是有限值" }
            val delta = reveal - previousReveal
            if (reveal <= 0f) {
                rebound?.cancelAndJoin()
                rebound = null
                translation.snapTo(0f)
            } else if (delta > 0f && rebound?.isActive != true) {
                if (reveal <= followEnd) {
                    translation.snapTo(translation.value - delta * followRatio)
                }
                if ((previousReveal < followEnd && reveal >= followEnd) ||
                    translation.value <= -followingDistancePx
                ) {
                    rebound = launch {
                        translation.animateTo(0f, reboundSpec, initialVelocity = 0f)
                    }
                }
            }
            previousReveal = reveal
        }
    }
    return translation.asState()
}
