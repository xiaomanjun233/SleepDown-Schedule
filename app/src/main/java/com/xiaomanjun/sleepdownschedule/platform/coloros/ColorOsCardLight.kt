package com.xiaomanjun.sleepdownschedule.platform.coloros

import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Shader
import android.graphics.SurfaceTexture
import android.graphics.drawable.Drawable
import android.util.Log
import android.view.TextureView
import android.view.View
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.NativeCardLightRequest

/** Use the installed personalisation page's TextureView, COE engine and shadow_card.coz together. */
@Composable
internal fun ColorOsCardLight(request: NativeCardLightRequest): Boolean {
    val context = LocalContext.current
    val source = remember { WallpaperCardLightSource.get(context.applicationContext) } ?: return false
    val host = remember(context, source) {
        ColorOsNativeUi.prepare("create the system card light") {
            CardLightHost(context, source.create(context), source.bandHeight)
        }
    } ?: return false
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val latest = rememberUpdatedState(request)
    val cornerRadius = with(LocalDensity.current) { request.cornerRadius.toPx() }
    val latestCornerRadius = rememberUpdatedState(cornerRadius)
    val projectionDepth = with(LocalDensity.current) { request.projectionDepth?.toPx() }
    val latestDepth = rememberUpdatedState(projectionDepth)
    AndroidView(factory = { host }, modifier = request.modifier, onRelease = { it.release() })
    DisposableEffect(host, lifecycle) {
        fun updateLifecycle() = host.setStarted(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
        val observer = LifecycleEventObserver { _, _ -> updateLifecycle() }
        lifecycle.addObserver(observer)
        updateLifecycle()
        onDispose {
            lifecycle.removeObserver(observer)
            host.setStarted(false)
        }
    }
    LaunchedEffect(host) {
        snapshotFlow {
            val current = latest.value
            CardLightFrame(current.bitmap, current.cardBounds(), current.strength(),
                latestCornerRadius.value, current.receiverBounds?.invoke(), current.revealProgress(),
                latestDepth.value, current.fitToCardWidth)
        }.collect(host::update)
    }
    return true
}

internal data class CardLightButtonShadow(val drawable: Drawable, val buttonSize: Size, val topOffset: Float)

@Composable
internal fun rememberCardLightButtonShadow(): CardLightButtonShadow? {
    val context = LocalContext.current
    val density = LocalDensity.current
    val source = remember { WallpaperCardLightSource.get(context.applicationContext) } ?: return null
    return remember(source, density) {
        ColorOsNativeUi.prepare("load the original illuminated button shadow") { source.buttonShadow() }
    }
}

private object WallpaperCardLightSource {
    private var searched = false
    private var cached: WallpaperLightSource? = null

    @Synchronized
    fun get(context: Context): WallpaperLightSource? {
        if (searched) return cached
        searched = true
        cached = ColorOsNativeUi.prepare("load the installed system card light") {
            val packageName = "com.oplus.wallpapers"
            val info = context.packageManager.getApplicationInfo(packageName, 0)
            check(info.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0)
            val native = context.createPackageContext(
                packageName, Context.CONTEXT_INCLUDE_CODE or Context.CONTEXT_IGNORE_SECURITY
            )
            val type = native.classLoader.loadClass("com.oplus.wallpapers.personal.views.WallpaperShadowView")
            val heightId = native.resources.getIdentifier("wallpaper_shadow_height", "dimen", packageName)
            check(heightId != 0)
            // Fail the optional capability before adding a View if this system changed its API.
            type.getMethod("setBitmap", Bitmap::class.java)
            type.getMethod("setViewAlpha", Float::class.javaPrimitiveType)
            type.getMethod("setViewProcess", Float::class.javaPrimitiveType)
            type.getMethod("d") // onStart in the verified system APK
            type.getMethod("e") // onStop
            WallpaperLightSource(native, type, native.resources.getDimensionPixelSize(heightId))
        }
        return cached
    }
}

private class WallpaperLightSource(
    private val native: Context,
    private val type: Class<*>,
    val bandHeight: Int
) {
    fun buttonShadow(): CardLightButtonShadow {
        fun dimension(name: String): Float {
            val id = native.resources.getIdentifier(name, "dimen", "com.oplus.wallpapers")
            check(id != 0)
            return native.resources.getDimension(id)
        }
        val id = native.resources.getIdentifier("all_theme_button_bottom_shadow", "drawable", "com.oplus.wallpapers")
        check(id != 0)
        return CardLightButtonShadow(native.resources.getDrawable(id, null).mutate(),
            Size(dimension("all_theme_button_width"), dimension("all_theme_button_height")),
            dimension("all_theme_button_shadow_mt"))
    }

    fun create(host: Context): CardLightDriver {
        // The COE constructor uses applicationContext.assets, not just the View's resources.
        // Keep both paired with the system class loader while retaining this app's attribution.
        val context = object : ContextWrapper(host) {
            override fun getResources() = native.resources
            override fun getAssets() = native.assets
            override fun getClassLoader() = native.classLoader
            override fun getApplicationContext(): Context = application
            private val application = object : ContextWrapper(host.applicationContext) {
                override fun getResources() = native.resources
                override fun getAssets() = native.assets
                override fun getClassLoader() = native.classLoader
                override fun getApplicationContext(): Context = this
            }
        }
        val nativeView = type.getConstructor(Context::class.java).newInstance(context) as View
        val bitmap = type.getMethod("setBitmap", Bitmap::class.java)
        val alpha = type.getMethod("setViewAlpha", Float::class.javaPrimitiveType)
        val progress = type.getMethod("setViewProcess", Float::class.javaPrimitiveType)
        val start = type.getMethod("d")
        val stop = type.getMethod("e")
        val layout = type.getDeclaredMethod("getLayoutWithDevice").apply { isAccessible = true }.invoke(nativeView) as Int
        // Original shadow_card.coz layout table, in its 3px/dp design coordinates.
        val stockCardWidth = floatArrayOf(616f, 1085f, 614f, 550f)[layout.coerceIn(0, 3)] *
            native.resources.displayMetrics.density / 3f
        // Match the original fragment: hide through the shader, keeping the TextureView drawable
        // so it can create its Surface and finish the engine's first frame.
        alpha.invoke(nativeView, 0f)
        progress.invoke(nativeView, 0f)
        return object : CardLightDriver {
            override val view = nativeView
            override val projectedCardWidth = stockCardWidth
            private var textureBitmap: Bitmap? = null
            override fun setBitmap(value: Bitmap) {
                // Compose snapshots can be HARDWARE bitmaps. This COE JNI uses
                // AndroidBitmap_lockPixels, which cannot read them and yields a null texture.
                val pixels = cardLightTextureBitmap(value)
                textureBitmap = pixels
                Log.i("SleepDownCOUI", "Card light texture: ${value.config} -> ${pixels.config}")
                bitmap.invoke(nativeView, pixels)
            }
            override fun prepareSurface() { start.invoke(nativeView) }
            override fun setProgress(value: Float) {
                progress.invoke(nativeView, value)
            }
            override fun start() {
                alpha.invoke(nativeView, 1f)
                start.invoke(nativeView)
            }
            override fun stop() { stop.invoke(nativeView) }
        }
    }
}

internal interface CardLightDriver {
    val view: View
    val projectedCardWidth: Float get() = 0f
    fun setBitmap(value: Bitmap)
    fun prepareSurface() {}
    fun setProgress(value: Float) {}
    fun start()
    fun stop()
}

internal fun cardLightTextureBitmap(source: Bitmap): Bitmap =
    if (source.config == Bitmap.Config.ARGB_8888) source
    else checkNotNull(source.copy(Bitmap.Config.ARGB_8888, false)) { "Cannot read card light bitmap pixels" }

internal data class CardLightFrame(
    val bitmap: Bitmap?, val bounds: Rect, val strength: Float,
    val cornerRadius: Float = 0f, val receiverBounds: Rect? = null,
    val revealProgress: Float = strength,
    val projectionDepth: Float? = null,
    val fitToCardWidth: Boolean = false
)

/** Full picker viewport: the band may reach the buttons outside the pager's measured bounds. */
internal class CardLightHost(
    context: Context,
    private val driver: CardLightDriver,
    private val bandHeight: Int
) : FrameLayout(context) {
    private var frame = CardLightFrame(null, Rect.Zero, 0f)
    private var started = false
    private var running = false
    private var uploaded: Bitmap? = null
    private var failed = false
    private var released = false
    private var surfaceReady = driver.view !is TextureView
    private var preparingSurface = false
    private var appliedProgress = -1f
    private val edgeMask = Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) }
    private var maskWidth = -1f

    init {
        clipChildren = false
        clipToPadding = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        driver.view.alpha = if (surfaceReady) 0f else 1f
        (driver.view as? TextureView)?.let { texture ->
            val original = requireNotNull(texture.surfaceTextureListener)
            texture.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                    original.onSurfaceTextureAvailable(surface, width, height)
                    // The native callback binds the Surface. Render one hidden frame before any
                    // bitmap upload: the COE material is created on its render thread.
                    preparingSurface = ColorOsNativeUi.prepare("prepare the system card light surface") {
                        driver.prepareSurface()
                        true
                    } == true
                }

                override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
                    original.onSurfaceTextureSizeChanged(surface, width, height)
                }

                override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
                    original.onSurfaceTextureUpdated(surface)
                    if (!surfaceReady) {
                        surfaceReady = true
                        Log.i("SleepDownCOUI", "System card light Surface first frame ready")
                        sync()
                    }
                }

                override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                    stop()
                    surfaceReady = false
                    uploaded = null
                    return original.onSurfaceTextureDestroyed(surface)
                }
            }
        }
        addView(driver.view, LayoutParams(LayoutParams.WRAP_CONTENT, bandHeight))
    }

    fun update(value: CardLightFrame) {
        frame = value
        sync()
        invalidate()
    }

    fun setStarted(value: Boolean) {
        started = value
        sync()
    }

    private fun sync() {
        if (released || failed) return
        if (!surfaceReady) {
            // View alpha=0 can prevent TextureView.draw from creating the Surface at all.
            // The native shader stays at alpha=0 until the first Surface update instead.
            positionLight()
            return
        }
        val source = frame.bitmap?.takeUnless { it.isRecycled }
        val visible = started && isAttachedToWindow && source != null &&
            !frame.bounds.isEmpty && frame.strength > 0.002f
        val success = ColorOsNativeUi.prepare("update the system card light") {
            if (visible) {
                if (uploaded !== source) {
                    driver.setBitmap(requireNotNull(source))
                    uploaded = source
                }
                if (!running) {
                    driver.start()
                    running = true
                }
                val progress = frame.revealProgress.coerceIn(0f, 1f)
                if (appliedProgress != progress) {
                    driver.setProgress(progress)
                    appliedProgress = progress
                }
            } else if (running || preparingSurface) {
                driver.stop()
                running = false
            }
            preparingSurface = false
            driver.view.alpha = if (visible) frame.strength.coerceIn(0f, 1f) else 0f
            positionLight()
            true
        }
        if (success != true) {
            failed = true
            driver.view.alpha = 0f
            ColorOsNativeUi.prepare("stop an unavailable system card light") { driver.stop() }
        }
    }

    private fun positionLight() {
        driver.view.translationX = frame.bounds.center.x - driver.view.measuredWidth / 2f
        // The accepted 355e15d7 build places the unscaled native band at the card bottom.
        // Moving its whole rectangular texture up by the corner radius exposed a hard top
        // edge beside the card; stretching it to the action row also distorted the fan.
        val top = frame.bounds.bottom
        driver.view.translationY = top
        driver.view.pivotY = 0f
        driver.view.scaleY = frame.projectionDepth?.let { it / bandHeight } ?: 1f
        driver.view.scaleX = if (frame.fitToCardWidth && driver.projectedCardWidth > 0f && frame.bounds.width > 0f) {
            frame.bounds.width / driver.projectedCardWidth * 1.15f
        } else 1f
    }

    override fun dispatchDraw(canvas: Canvas) {
        if (!frame.fitToCardWidth || frame.bounds.isEmpty) {
            super.dispatchDraw(canvas)
            return
        }
        val width = frame.bounds.width * 1.2f
        val left = frame.bounds.center.x - width / 2f
        val top = driver.view.translationY
        val bottom = top + (frame.projectionDepth ?: bandHeight.toFloat())
        val save = canvas.saveLayer(left, top, left + width, bottom, null)
        canvas.clipRect(left, top, left + width, bottom)
        super.dispatchDraw(canvas)
        if (maskWidth != width) {
            edgeMask.shader = LinearGradient(0f, 0f, width, 0f,
                intArrayOf(0x00FFFFFF, -1, -1, 0x00FFFFFF), floatArrayOf(0f, 0.15f, 0.85f, 1f), Shader.TileMode.CLAMP)
            maskWidth = width
        }
        canvas.translate(left, 0f)
        canvas.drawRect(0f, top, width, bottom, edgeMask)
        canvas.restoreToCount(save)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        // WallpaperShadowView measures its own device-specific width. Preserve that geometry,
        // centre it on the actual card and let the full viewport contain the 136dp light band.
        driver.view.layout(0, 0, driver.view.measuredWidth, driver.view.measuredHeight)
        positionLight()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        sync()
    }

    override fun onDetachedFromWindow() {
        stop()
        // The native View releases its COE engine on detach; it must never be pooled/reused.
        released = true
        uploaded = null
        frame = CardLightFrame(null, Rect.Zero, 0f)
        super.onDetachedFromWindow()
    }

    fun release() {
        stop()
        released = true
        uploaded = null
        frame = CardLightFrame(null, Rect.Zero, 0f)
        removeAllViews()
    }

    private fun stop() {
        driver.view.alpha = 0f
        if (running || preparingSurface) ColorOsNativeUi.prepare("stop the system card light") { driver.stop() }
        running = false
        preparingSurface = false
    }
}
