package com.xiaomanjun.sleepdownschedule.platform.coloros

import android.content.Context
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.viewinterop.AndroidView
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.NativeFollowLightRequest

/** A light-only View: no backdrop, blur, stroke or gesture interception on the full-screen host. */
@Composable
internal fun ColorOsFollowLight(request: NativeFollowLightRequest): Boolean {
    val native = rememberNativeContext(request.dark) ?: return false
    val light = remember(native) {
        ColorOsNativeUi.prepare("create external gesture follow light") { FollowLightView(native.second, native.first) }
    } ?: return false
    AndroidView(factory = { light }, modifier = request.modifier, update = { it.update(request) })
    DisposableEffect(light) { onDispose { light.release() } }
    return true
}

private class FollowLightView(context: Context, private val source: ColorOsNativeUi.Source) : View(context) {
    private val light = source.load("com.coui.appcompat.spotlight.COUISpotLightEffectDrawable")
        .getConstructor().newInstance() as Drawable
    private val touch = light.javaClass.getMethod("onTouchEvent", MotionEvent::class.java)
    private val cancel = light.javaClass.getMethod("cancel")
    private val managerType = source.load("com.coui.appcompat.COUIMaterialStateManager")
    private val manager = managerType.getField("INSTANCE").get(null)
    private val listenerType = source.load("com.coui.appcompat.OnMaterialStateChangeListener")
    private val listener = source.listener(listenerType.name) { name, _ ->
        if (name == "onMaterialStateChange") updateEnabled()
        null
    }
    private var enabledBySystem = false
    private var downTime = 0L
    private var pressed = false
    private var position = Offset.Zero
    private var request: NativeFollowLightRequest? = null
    private var radius = 0f
    private val radiusRatio: Float

    init {
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        foreground = light
        val type = source.load("com.coui.appcompat.spotlight.COUISpotLightEffect\$SpotLightType")
        light.javaClass.getMethod("setSpotLightType", Context::class.java, type)
            .invoke(light, context, type.getField("TYPE_OPAQUE_MEDIUM_2").get(null))
        source.setFollowLightIntensity(light, 1.2f)
        val effect = light.javaClass.getDeclaredField("mEffect").apply { isAccessible = true }.get(light)
        val start = effect.javaClass.getDeclaredField("mStartRadius").apply { isAccessible = true }.getFloat(effect)
        val end = effect.javaClass.getDeclaredField("mEndRadius").apply { isAccessible = true }.getFloat(effect)
        radiusRatio = start / end
    }

    override fun dispatchTouchEvent(event: MotionEvent) = false

    fun update(value: NativeFollowLightRequest) {
        request = value
        if (radius != value.radiusPx) {
            radius = value.radiusPx
            light.javaClass.getMethod("setRadiusRange", Float::class.javaPrimitiveType, Float::class.javaPrimitiveType)
                .invoke(light, radius * radiusRatio, radius)
        }
        if (!isAttachedToWindow || !enabledBySystem) return
        val active = value.pressed && value.position.x.isFinite() && value.position.y.isFinite()
        if (!active && !pressed) return
        if (active && pressed && position == value.position) return
        val now = SystemClock.uptimeMillis()
        val action = when { !pressed -> MotionEvent.ACTION_DOWN; active -> MotionEvent.ACTION_MOVE; else -> MotionEvent.ACTION_UP }
        if (!pressed) downTime = now
        position = if (active) value.position else position
        val event = MotionEvent.obtain(downTime, now, action, position.x, position.y, 0)
        try { touch.invoke(light, event) } finally { event.recycle() }
        pressed = active
    }

    private fun updateEnabled() {
        enabledBySystem = managerType.getMethod("isSpotLightEffectEnable").invoke(manager) == true
        light.javaClass.getMethod("setEnabled", Boolean::class.javaPrimitiveType).invoke(light, enabledBySystem)
        if (!enabledBySystem) release() else request?.let(::update)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        managerType.getMethod("addOnMaterialStateChangedListener", listenerType).invoke(manager, listener)
        updateEnabled()
    }
    override fun onDetachedFromWindow() {
        managerType.getMethod("removeOnMaterialStateChangedListener", listenerType).invoke(manager, listener)
        release()
        super.onDetachedFromWindow()
    }
    override fun draw(canvas: Canvas) { light.setBounds(0, 0, width, height); super.draw(canvas) }
    fun release() { cancel.invoke(light); pressed = false }
}
