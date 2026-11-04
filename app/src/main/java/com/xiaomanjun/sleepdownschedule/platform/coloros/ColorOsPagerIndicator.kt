package com.xiaomanjun.sleepdownschedule.platform.coloros

import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ApplicationInfo
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.view.ContextThemeWrapper
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.viewinterop.AndroidView
import com.kyant.shapes.Capsule
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.NativeMaterialSurfaceRequest
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.NativePagerIndicatorRequest
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * OplusPageIndicator's portable view/material boundary. Its full class casts Context to the
 * launcher's ActivityContext/Launcher, so workspace/search state must stay in the launcher.
 * Reuse its native view touch animator and the COUI blur, rim and follow light around our pages.
 */
@Composable
internal fun ColorOsPagerIndicator(request: NativePagerIndicatorRequest): Boolean {
    val native = rememberColorOsStrokeContext(dark = true) ?: return false
    val context = LocalContext.current
    val latest = rememberUpdatedState(request)
    val host = remember(native) {
        ColorOsNativeUi.prepare("create the launcher page indicator material") {
            val surface = NativeMaterialSurface(native.second, native.first, ColorOsSurfaceStyle.PageIndicator)
            LauncherPagerIndicatorHost(native.second, surface).also { target ->
                val motion = LauncherIndicatorMotion.create(context, target)
                target.nativeTouch = motion::touch
                target.pageFeedback = LauncherIndicatorHaptics.create(context, target)
                target.onSelect = { if (latest.value.enabled) latest.value.onSelect(it) }
                target.onScrub = { position, active, cancelled ->
                    if (latest.value.enabled || !active) {
                        val scrub = latest.value.onScrub
                        if (scrub != null) scrub(position, active, cancelled)
                        else if (!cancelled) latest.value.onSelect(position.roundToInt().coerceIn(0, latest.value.pageCount - 1))
                    }
                }
            }
        }
    } ?: return false
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    AndroidView(factory = { host }, modifier = request.modifier, update = {
        host.layoutDirection = if (direction == androidx.compose.ui.unit.LayoutDirection.Rtl) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
        host.setInputEnabled(request.enabled)
        host.material?.let { material ->
            material.updateOutline(Capsule(), density, direction)
            material.configure(NativeMaterialSurfaceRequest(
                modifier = androidx.compose.ui.Modifier, shape = Capsule(), dark = true,
                followTouch = request.enabled
            ), density, direction)
        }
    })
    LaunchedEffect(host) {
        snapshotFlow { Triple(latest.value.pageCount, latest.value.position(), latest.value.selectedPage()) }
            .collect { (count, position, _) -> host.setPages(count, position) }
    }
    DisposableEffect(host) { onDispose { host.release() } }
    return true
}

private class LauncherIndicatorMotion(private val animator: Any, private val event: java.lang.reflect.Method) {
    fun touch(value: MotionEvent) { event.invoke(animator, value) }
    companion object {
        fun create(host: Context, target: View): LauncherIndicatorMotion {
            val packageName = "com.android.launcher"
            val info = host.packageManager.getApplicationInfo(packageName, 0)
            check(info.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0)
            val native = host.applicationContext.createPackageContext(packageName,
                Context.CONTEXT_INCLUDE_CODE or Context.CONTEXT_IGNORE_SECURITY)
            val paired = object : ContextWrapper(host) {
                override fun getResources() = native.resources
                override fun getAssets() = native.assets
                override fun getClassLoader() = native.classLoader
            }
            val theme = native.resources.getIdentifier("Theme.COUI.Main.Dark", "style", packageName)
            val context = ContextThemeWrapper(paired, theme)
            val helper = native.classLoader.loadClass(
                "com.android.launcher3.util.LauncherMaterialEffectUtils\$TouchAnimatorHelper")
            val factory = helper.getMethod("createViewTouchAnimator\$default", Context::class.java, View::class.java)
            val animator = checkNotNull(factory.invoke(null, context, target))
            // Verified launcher 17.3.11: COUITouchMotionAnimator.m(MotionEvent) dispatches DOWN/MOVE/UP.
            return LauncherIndicatorMotion(animator, animator.javaClass.getMethod("m", MotionEvent::class.java))
        }
    }
}

/** Launcher PageIndicatorTouchHelper uses WaveformEffect type 0, with a 65ms plain-motor path. */
private object LauncherIndicatorHaptics {
    fun create(context: Context, target: View): () -> Unit {
        val linear = context.getSystemService("linearmotor")
        val native: (() -> Unit)? = linear?.let {
            ColorOsNativeUi.prepare("bind the launcher page haptic") {
                val builder = Class.forName("com.oplus.os.WaveformEffect\$Builder").getConstructor().newInstance()
                builder.javaClass.getMethod("setEffectType", Int::class.javaPrimitiveType).invoke(builder, 0)
                val effect = checkNotNull(builder.javaClass.getMethod("build").invoke(builder))
                val vibrate = it.javaClass.getMethod("vibrate", effect.javaClass)
                val action: () -> Unit = { vibrate.invoke(it, effect); Unit }
                action
            }
        }
        val vibrator = if (native == null) context.getSystemService(Vibrator::class.java) else null
        return {
            if (target.isHapticFeedbackEnabled && Settings.System.getInt(context.contentResolver,
                    Settings.System.HAPTIC_FEEDBACK_ENABLED, 0) != 0) {
                ColorOsNativeUi.prepare("play the launcher page haptic") {
                    if (native != null) native()
                    else vibrator?.vibrate(VibrationEffect.createOneShot(65L, VibrationEffect.DEFAULT_AMPLITUDE))
                }
            }
        }
    }
}

/** Native material plus launcher-size dots; taps, scrubbing and native press feedback share one host. */
internal class LauncherPagerIndicatorHost(
    context: Context,
    val material: NativeMaterialSurface? = null
) : FrameLayout(context) {
    var nativeTouch: ((MotionEvent) -> Unit)? = null
    var pageFeedback: () -> Unit = {}
    var onSelect: (Int) -> Unit = {}
    var onScrub: (Float, Boolean, Boolean) -> Unit = { _, _, _ -> }
    private val density = resources.displayMetrics.density
    private val padding = 14f * density
    private val dot = 8f * density
    private val gap = 6f * density
    private val barHeight = 28f * density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val focus = RectF()
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private var count = 0
    private var position = 0f
    private var downX = 0f
    private var dragging = false
    private var touching = false
    private var lastScrub = 0f
    private var feedbackPage = 0
    private var released = false
    init {
        isClickable = true
        clipChildren = false
        clipToPadding = false
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        material?.let { addView(it, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)) }
    }
    fun setPages(value: Int, continuous: Float) {
        val changed = count != value
        count = value
        position = continuous.coerceIn(0f, (value - 1).coerceAtLeast(0).toFloat())
        if (changed) requestLayout()
        invalidate()
    }
    fun setInputEnabled(value: Boolean) {
        if (!value && touching) cancelGesture()
        isEnabled = value
    }
    private fun windowSize() = min(count, 22).coerceAtLeast(1)
    private fun windowStart() = (floor(position).toInt() - windowSize() / 2).coerceIn(0, (count - windowSize()).coerceAtLeast(0))
    private fun step() = if (windowSize() > 1) (width - padding * 2f - dot) / (windowSize() - 1) else 0f
    private fun center(page: Float) = padding + dot / 2f + (page - windowStart()) * step()
    private fun pageAt(x: Float): Float {
        val local = if (layoutDirection == View.LAYOUT_DIRECTION_RTL) width - x else x
        return if (step() > 0f) (windowStart() + (local - padding - dot / 2f) / step())
            .coerceIn(0f, (count - 1).coerceAtLeast(0).toFloat()) else 0f
    }
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val slots = windowSize().coerceAtLeast(4)
        setMeasuredDimension(resolveSize((padding * 2f + slots * dot + (slots - 1) * gap).roundToInt(), widthMeasureSpec),
            resolveSize(barHeight.roundToInt(), heightMeasureSpec))
        material?.measure(MeasureSpec.makeMeasureSpec(measuredWidth, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(measuredHeight, MeasureSpec.EXACTLY))
    }
    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        material?.layout(0, 0, width, height)
    }
    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        if (count <= 0) return
        val save = canvas.save()
        if (layoutDirection == View.LAYOUT_DIRECTION_RTL) { canvas.translate(width.toFloat(), 0f); canvas.scale(-1f, 1f) }
        val y = height / 2f
        paint.color = 0x4DFFFFFF
        for (page in windowStart() until windowStart() + windowSize()) canvas.drawCircle(center(page.toFloat()), y, dot / 2f, paint)
        val start = floor(position)
        val fraction = position - start
        val left = center(start) + fraction * fraction * step()
        val right = center(start) + (1f - (1f - fraction) * (1f - fraction)) * step()
        paint.color = android.graphics.Color.WHITE
        focus.set(left - dot / 2f, y - dot / 2f, right + dot / 2f, y + dot / 2f)
        canvas.drawRoundRect(focus, dot / 2f, dot / 2f, paint)
        canvas.restoreToCount(save)
    }
    override fun onInterceptTouchEvent(event: MotionEvent) = true
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled || released || count < 1) return false
        material?.followTouch(event)
        nativeTouch?.invoke(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                touching = true
                dragging = false
                feedbackPage = position.roundToInt()
                lastScrub = pageAt(event.x)
                parent?.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_MOVE -> {
                if (!touching) return false
                if (!dragging && abs(event.x - downX) > slop) dragging = true
                if (dragging) {
                    lastScrub = pageAt(event.x)
                    feedbackFor(lastScrub)
                    onScrub(lastScrub, true, false)
                }
            }
            MotionEvent.ACTION_UP -> {
                feedbackFor(pageAt(event.x))
                if (dragging) onScrub(pageAt(event.x), false, false) else onSelect(pageAt(event.x).roundToInt())
                touching = false; dragging = false
                parent?.requestDisallowInterceptTouchEvent(false)
                performClick()
            }
            MotionEvent.ACTION_CANCEL -> cancelGesture()
        }
        return true
    }
    override fun performClick(): Boolean { super.performClick(); return true }
    private fun feedbackFor(page: Float) {
        val next = page.roundToInt()
        if (next != feedbackPage) {
            feedbackPage = next
            pageFeedback()
        }
    }
    private fun cancelGesture() {
        if (touching) {
            val cancel = MotionEvent.obtain(0L, android.os.SystemClock.uptimeMillis(), MotionEvent.ACTION_CANCEL, downX, height / 2f, 0)
            nativeTouch?.invoke(cancel)
            cancel.recycle()
        }
        if (dragging) onScrub(lastScrub, false, true)
        touching = false; dragging = false
        material?.clearTouch()
        parent?.requestDisallowInterceptTouchEvent(false)
    }
    fun release() {
        cancelGesture()
        released = true
        material?.release()
        nativeTouch = null
        pageFeedback = {}
        onSelect = {}; onScrub = { _, _, _ -> }
    }
    override fun onDetachedFromWindow() { cancelGesture(); super.onDetachedFromWindow() }
}
