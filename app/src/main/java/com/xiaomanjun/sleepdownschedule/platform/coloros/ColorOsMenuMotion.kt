package com.xiaomanjun.sleepdownschedule.platform.coloros

import android.content.Context
import android.view.MotionEvent
import android.view.View
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.NativeMenuMotion

/** Same touch units/configuration as COUI's BasePopupMenuAnimationController.makeAnimator. */
internal class ColorOsMenuMotionBinding(source: ColorOsNativeUi.Source, target: View,
    private val motion: NativeMenuMotion) {
    private val type = source.load("com.coui.appcompat.anim.ability.COUIPopupTouchMotionAnimator")
    private val animator = type.getConstructor(Context::class.java, Boolean::class.javaPrimitiveType)
        .newInstance(target.context, false)
    private val callbackType = source.load("com.coui.appcompat.animation.dynamicanimation.COUIComposeMotionAnimator\$OnAnimationUpdateCallback")
    private val sink = source.load("com.coui.appcompat.animation.touchanimation.COUIIComposeMotionUnit\$ContributeSink")
    private val x = sink.getMethod("getTranslateX")
    private val y = sink.getMethod("getTranslateY")
    private val sx = sink.getMethod("getScaleX")
    private val sy = sink.getMethod("getScaleY")
    private val touch = type.getMethod("handleTouchEvent", MotionEvent::class.java)
    private val size = type.getMethod("setTargetSize", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
    private val callback = source.listener(callbackType.name) { name, args ->
        if (name == "onAnimationUpdate") {
            val value = checkNotNull(args[0])
            motion.update(x.invoke(value) as Float, y.invoke(value) as Float,
                sx.invoke(value) as Float, sy.invoke(value) as Float)
        }
        null
    }
    private val listener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
        size.invoke(animator, target.width, target.height)
    }
    private val view = target

    init {
        type.getMethod("setTouchMotionTarget", View::class.java).invoke(animator, target)
        for (name in listOf("setDeformationCollectorOnly", "setRubberCollectorOnly", "setDeformationEnable", "setRubberEnable"))
            type.getMethod(name, Boolean::class.javaPrimitiveType).invoke(animator, true)
        type.getMethod("setPressScaleEnable", Boolean::class.javaPrimitiveType).invoke(animator, false)
        // The original opening morph already owns the anchor pivot. Touch runs around the centre.
        type.getMethod("setSkipPivotCorrection", Boolean::class.javaPrimitiveType).invoke(animator, true)
        val configType = source.load("com.coui.appcompat.animation.touchanimation.TouchMotionParamConfig")
        val paramsType = source.load("com.coui.appcompat.animation.touchanimation.COUITouchMotionAnimator\$AnimationParamsType")
        val config = configType.getConstructor().newInstance()
        configType.getMethod("setAnimationParamsType", paramsType).invoke(config, paramsType.getField("MAX").get(null))
        type.getMethod("config", configType).invoke(animator, config)
        type.getMethod("addOnAnimationUpdateCallback", callbackType).invoke(animator, callback)
        size.invoke(animator, target.width, target.height)
        target.addOnLayoutChangeListener(listener)
        motion.onTouch = { touch.invoke(animator, it); Unit }
    }

    fun dispose() {
        motion.onTouch = null
        view.removeOnLayoutChangeListener(listener)
        type.getMethod("removeOnAnimationUpdateCallback", callbackType).invoke(animator, callback)
        type.getMethod("release").invoke(animator)
        motion.update(0f, 0f, 1f, 1f)
    }
}
