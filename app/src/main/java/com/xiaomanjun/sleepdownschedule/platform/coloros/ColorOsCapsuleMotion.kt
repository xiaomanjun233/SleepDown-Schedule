package com.xiaomanjun.sleepdownschedule.platform.coloros

import android.view.View
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.GraphicsLayerScope

/** Observed by the content's graphics-layer block only, never by the button composition. */
@Stable
internal class ColorOsCapsuleMotion {
    private data class Transform(val x: Float = 0f, val y: Float = 0f,
        val scaleX: Float = 1f, val scaleY: Float = 1f)
    private var transform by mutableStateOf(Transform())

    fun update(x: Float, y: Float, scaleX: Float, scaleY: Float) {
        transform = Transform(x, y, scaleX, scaleY)
    }

    fun applyTo(layer: GraphicsLayerScope) {
        val value = transform
        layer.translationX = value.x
        layer.translationY = value.y
        layer.scaleX = value.scaleX
        layer.scaleY = value.scaleY
    }
}

/** Subscribe to the same sink as COUIButton; no second animator and no frame polling. */
internal class ColorOsCapsuleMotionBinding(source: ColorOsNativeUi.Source, button: View,
    motion: ColorOsCapsuleMotion) {
    private val animator = source.load("com.coui.appcompat.button.COUIButton")
        .getDeclaredField("mTouchMotionAnimator").apply { isAccessible = true }.get(button)
    private val animatorType = source.load("com.coui.appcompat.animation.dynamicanimation.COUIComposeMotionAnimator")
    private val callbackType = source.load("${animatorType.name}\$OnAnimationUpdateCallback")
    private val sinkType = source.load("com.coui.appcompat.animation.touchanimation.COUIIComposeMotionUnit\$ContributeSink")
    private val translateX = sinkType.getMethod("getTranslateX")
    private val translateY = sinkType.getMethod("getTranslateY")
    private val scaleX = sinkType.getMethod("getScaleX")
    private val scaleY = sinkType.getMethod("getScaleY")
    private val remove = animatorType.getMethod("removeOnAnimationUpdateCallback", callbackType)
    private val callback = source.listener(callbackType.name) { name, arguments ->
        if (name == "onAnimationUpdate") {
            val sink = checkNotNull(arguments[0])
            motion.update(translateX.invoke(sink) as Float, translateY.invoke(sink) as Float,
                scaleX.invoke(sink) as Float, scaleY.invoke(sink) as Float)
        }
        null
    }

    init {
        motion.update(button.translationX, button.translationY, button.scaleX, button.scaleY)
        animatorType.getMethod("addOnAnimationUpdateCallback", callbackType).invoke(animator, callback)
    }

    fun dispose() { remove.invoke(animator, callback) }
}
