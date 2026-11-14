package com.itos.coui.switch

import android.animation.AnimatorSet
import android.animation.ValueAnimator
import android.view.animation.LinearInterpolator
import android.view.animation.PathInterpolator
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue

/** COUISwitch 非换肤分支；业务完成与 checked 始终由宿主提交。 */
internal class CouiSwitchLoadingMotion {
    var thumbScale by mutableFloatStateOf(1f)
    var scale by mutableFloatStateOf(.5f)
    var alpha by mutableFloatStateOf(0f)
    var rotation by mutableFloatStateOf(0f)
    private var loading = false
    private val easing = PathInterpolator(.3f, 0f, .1f, 1f)
    private fun animate(from: Float, to: Float, millis: Long, update: (Float) -> Unit) =
        ValueAnimator.ofFloat(from, to).apply {
            duration = millis; interpolator = easing
            addUpdateListener { update(it.animatedValue as Float) }
        }
    private val startAnimation = AnimatorSet().apply {
        playTogether(animate(1f, 0f, 433) { thumbScale = it },
            animate(.5f, 1f, 550) { scale = it }, animate(0f, 1f, 550) { alpha = it },
            ValueAnimator.ofFloat(0f, 360f).apply {
                duration = 800; repeatCount = ValueAnimator.INFINITE; interpolator = LinearInterpolator()
                addUpdateListener { rotation = it.animatedValue as Float }
            })
    }
    private val stopAnimation = AnimatorSet().apply {
        play(ValueAnimator.ofFloat(1f, 0f).apply {
            duration = 100; interpolator = easing
            addUpdateListener { alpha = it.animatedValue as Float }
        })
    }
    fun update(value: Boolean) {
        if (loading == value) return
        loading = value
        if (value) {
            // startLoading 不取消仍在执行的 stop alpha；两组动画的生命周期独立。
            startAnimation.start()
        } else {
            startAnimation.cancel()
            stopAnimation.start()
            thumbScale = 1f
        }
    }
    fun dispose() { startAnimation.cancel(); stopAnimation.cancel() }
}
