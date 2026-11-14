package com.xiaomanjun.sleepdownschedule.transition

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Matrix
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect as AndroidRect
import android.graphics.RectF
import android.content.res.Configuration
import android.view.View
import android.view.ViewOutlineProvider
import android.view.Window
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.DialogWindowProvider
import com.xiaomanjun.sleepdownschedule.BuildConfig
import com.xiaomanjun.sleepdownschedule.core.ui.interaction.findActivity
import com.xiaomanjun.sleepdownschedule.glass.requestGlassWindowRegion
import com.xiaomanjun.sleepdownschedule.glass.withRecordingDensity
import kotlinx.coroutines.CompletableDeferred

private const val PressFrameRetentionMillis = 500L

/** Presents the real control from its first frame and serves as the SharedViewKit source. */
internal class LiveTransitionSourceView(
    private val activity: Activity,
    private val drawing: GraphicsLayer,
    private var drawingDensity: Density,
    private var direction: LayoutDirection,
    private val cornerRadiusPx: Float? = null,
) : View(activity) {
    private val scope = CanvasDrawScope()
    private val softwarePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val softwareBounds = RectF()
    private val softwareOutline = Path()
    private var softwareFrame: Bitmap? = null
    private var beforePress: RestFrame? = null
    private val discardRestFrame = Runnable { clearRestFrame() }
    private val supportsRestCapture by lazy(LazyThreadSafetyMode.NONE) {
        BuildConfig.SLEEPDOWN_EXPERIMENTAL_FEATURES &&
            !BuildConfig.SLEEPDOWN_SHARED_VIEW_KIT_DISABLED_FOR_BUILD &&
            runCatching { PlatformSharedViewKitApi.supportedProtocol() > 0 }.getOrDefault(false)
    }
    private var cropLeft = 0f
    private var renderButton = true
    private var nativeVisible by mutableStateOf(true)
    var hiddenForLegacyMorph by mutableStateOf(false)
        private set
    val showsOverflow: Boolean get() = nativeVisible && !hiddenForLegacyMorph

    private class RestFrame(val window: Window, val boundsOnScreen: AndroidRect) {
        val ready = CompletableDeferred<Unit>()
        var bitmap: Bitmap? = null
    }

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, radius(view.width, view.height))
            }
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        softwareBounds.set(0f, 0f, w.toFloat(), h.toFloat())
        softwareOutline.reset()
        val radius = radius(w, h)
        softwareOutline.addRoundRect(softwareBounds, radius, radius, Path.Direction.CW)
        invalidateOutline()
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(discardRestFrame)
        clearRestFrame()
        super.onDetachedFromWindow()
    }

    override fun setAlpha(alpha: Float) {
        super.setAlpha(alpha)
        // The ROM hides/restores the View, including the shadow drawn outside it by Compose.
        nativeVisible = alpha > 0f
    }

    /** Keep the source mounted so its bounds can still be used as the return target. */
    fun hideForLegacyMorph(): (() -> Unit)? {
        if (hiddenForLegacyMorph) return null
        hiddenForLegacyMorph = true
        invalidate()
        return {
            hiddenForLegacyMorph = false
            invalidate()
        }
    }

    private fun radius(w: Int, h: Int): Float {
        val maximum = minOf(w, h) / 2f
        return cornerRadiusPx?.coerceIn(0f, maximum) ?: maximum
    }

    val sourceRadiusPx: Float get() = radius(width, height)
    val presentsButton: Boolean get() = renderButton

    fun updatePresentation(density: Density, layoutDirection: LayoutDirection, render: Boolean) {
        if (drawingDensity == density && direction == layoutDirection && renderButton == render) return
        drawingDensity = density
        direction = layoutDirection
        renderButton = render
        invalidate()
    }

    fun updateCrop(left: Float) {
        if (cropLeft == left) return
        cropLeft = left
        invalidate()
    }

    fun captureBeforePress(window: Window) {
        if (!supportsRestCapture || activity.isInMultiWindowMode ||
            activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) return
        val bounds = liveTransitionBoundsOnScreen(activity) ?: return
        removeCallbacks(discardRestFrame)
        // Reuse the same neutral pixels for a rapid second tap while the first touch is settling.
        val existing = beforePress
        if (existing != null && existing.window === window && existing.boundsOnScreen == bounds) return
        clearRestFrame()
        val capture = RestFrame(window, bounds)
        beforePress = capture
        val windowBounds = window.decorView.screenBoundsInWindow(bounds)
        requestGlassWindowRegion(window, Rect(windowBounds.left.toFloat(), windowBounds.top.toFloat(),
            windowBounds.right.toFloat(), windowBounds.bottom.toFloat())) { bitmap ->
            if (beforePress === capture) {
                capture.bitmap = bitmap
                capture.ready.complete(Unit)
            } else bitmap?.recycle()
        }
    }

    fun finishPress() {
        if (beforePress != null) postDelayed(discardRestFrame, PressFrameRetentionMillis)
    }

    suspend fun copyRestFrame(window: Window, bounds: AndroidRect): Bitmap? {
        val capture = beforePress?.takeIf { it.window === window && it.boundsOnScreen == bounds } ?: return null
        capture.ready.await()
        return capture.bitmap?.softwareCopyForKit(sourceRadiusPx)
    }

    private fun clearRestFrame() {
        val capture = beforePress ?: return
        beforePress = null
        capture.bitmap?.recycle()
        capture.bitmap = null
        capture.ready.complete(Unit)
    }

    /** The SDK can call View.draw with a software canvas. Sample the original button's pixels. */
    fun prepareSoftwareFrame(bitmap: Bitmap?): Boolean {
        softwareFrame = bitmap?.takeUnless { it.isRecycled }?.let {
            if (it.config == Bitmap.Config.HARDWARE) {
                runCatching { it.copy(Bitmap.Config.ARGB_8888, false) }.getOrNull()
            } else it
        }
        return softwareFrame != null
    }

    fun clearSoftwareFrame() {
        softwareFrame = null
    }

    override fun onDraw(canvas: AndroidCanvas) {
        if (!renderButton || hiddenForLegacyMorph) return
        if (!canvas.isHardwareAccelerated || drawing.isReleased) {
            softwareFrame?.takeUnless { it.isRecycled }?.let { frame ->
                val count = canvas.save()
                canvas.clipPath(softwareOutline)
                canvas.drawBitmap(frame, null, softwareBounds, softwarePaint)
                canvas.restoreToCount(count)
            }
            return
        }
        val count = canvas.save()
        canvas.clipRect(0, 0, width, height)
        scope.draw(drawingDensity, direction, Canvas(canvas), Size(width.toFloat(), height.toFloat())) {
            translate(-cropLeft, 0f) { drawLayer(drawing) }
        }
        canvas.restoreToCount(count)
    }
}

/** Draw the Compose control in its original backdrop coordinates, then present it in its View. */
@Composable
internal fun LiveTransitionButton(
    modifier: Modifier = Modifier,
    cornerRadius: Dp? = null,
    show: Boolean = true,
    content: @Composable (View?) -> Unit,
) {
    val activity = LocalContext.current.findActivity()
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val window = (LocalView.current.parent as? DialogWindowProvider)?.window ?: activity?.window
    val layer = rememberGraphicsLayer()
    val radius = cornerRadius?.let { with(density) { it.toPx() } }
    val source = remember(activity, layer, radius) {
        activity?.let { LiveTransitionSourceView(it, layer, density, direction, radius) }
    }
    Box(modifier.captureTransitionPress(source, window)) {
        if (source != null) key(source) {
            AndroidView(factory = { source }, modifier = Modifier.matchParentSize(),
                update = { it.updatePresentation(density, direction, render = show) })
        }
        Box(Modifier.drawWithContent {
            if (show && source?.hiddenForLegacyMorph != true) {
                if (source == null || !source.isAttachedToWindow || source.width <= 0) {
                    drawContent()
                } else {
                    layer.record { withRecordingDensity(density) { this@drawWithContent.drawContent() } }
                    // The View owns the button bounds. Shadows and glow can extend into
                    // adjacent Compose space without changing the SDK's source rectangle.
                    if (source.showsOverflow) {
                        clipRect(0f, 0f, size.width, size.height, ClipOp.Difference) { drawLayer(layer) }
                    }
                }
            }
        }) { content(source) }
    }
}

/** Observe the presented frame before the child handles DOWN and starts its touch animation. */
internal fun Modifier.captureTransitionPress(source: LiveTransitionSourceView?, window: Window?): Modifier =
    if (source == null || window == null) this else pointerInput(source, window) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            source.captureBeforePress(window)
            try {
                do {
                    val event = awaitPointerEvent(PointerEventPass.Final)
                } while (event.changes.any { it.pressed })
            } finally {
                source.finishPress()
            }
        }
    }

private fun View.isMountedTransitionSource(activity: Activity): Boolean =
    context === activity && isAttachedToWindow && width > 1 && height > 1 &&
        windowToken != null && display == activity.window.decorView.display &&
        (this !is LiveTransitionSourceView || presentsButton)

internal fun View.isLiveTransitionSource(activity: Activity): Boolean =
    isMountedTransitionSource(activity) && isShown

/** Resolve the mounted View, including its Android ancestors and window position. */
internal fun View.liveTransitionBoundsOnScreen(activity: Activity, coveredWindow: Boolean = false): AndroidRect? {
    if (coveredWindow) {
        if (!isTransitionReturnTarget(activity)) return null
    } else if (!isLiveTransitionSource(activity)) return null
    val matrix = Matrix().also(::transformMatrixToGlobal)
    val bounds = RectF(0f, 0f, width.toFloat(), height.toFloat())
    if (!matrix.mapRect(bounds)) return null
    val screen = AndroidRect().also(bounds::round)
    val visible = AndroidRect()
    if (screen.width() < 2 || screen.height() < 2) return null
    if (!coveredWindow && (!getGlobalVisibleRect(visible) ||
        visible.width() + 1 < screen.width() || visible.height() + 1 < screen.height())) return null
    return screen
}

internal fun View.isTransitionReturnTarget(activity: Activity): Boolean {
    if (!isMountedTransitionSource(activity)) return false
    // The system can hide the source decor while the destination covers it.
    val root = rootView
    var node = this
    while (node !== root) {
        if (node.visibility != View.VISIBLE) return false
        node = node.parent as? View ?: return false
    }
    return true
}

internal fun View.screenBoundsInWindow(screenBounds: AndroidRect): AndroidRect {
    val screen = IntArray(2).also(::getLocationOnScreen)
    val window = IntArray(2).also(::getLocationInWindow)
    return AndroidRect(screenBounds).apply { offset(window[0] - screen[0], window[1] - screen[1]) }
}

/** Legacy and PixelCopy use the owner Activity's window even for a button inside a dialog. */
internal fun View.liveTransitionBoundsInActivity(activity: Activity, coveredWindow: Boolean = false): Rect? =
    liveTransitionBoundsOnScreen(activity, coveredWindow)?.let {
        val window = activity.window.decorView.screenBoundsInWindow(it)
        Rect(window.left.toFloat(), window.top.toFloat(), window.right.toFloat(), window.bottom.toFloat())
    }
