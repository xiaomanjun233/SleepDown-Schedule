package com.xiaomanjun.sleepdownschedule.glass

import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.IntSize
import com.itos.coui.effect.CouiCausticBitmapSource
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.BackdropRenderOptions
import com.kyant.backdrop.BackdropViewport
import com.kyant.backdrop.backdrops.SharedBlurBackdrop
import com.kyant.backdrop.drawBackdrop
import com.xiaomanjun.sleepdownschedule.core.wallpaper.WallpaperCropState
import com.xiaomanjun.sleepdownschedule.core.wallpaper.WallpaperPixels
import com.xiaomanjun.sleepdownschedule.core.wallpaper.calculateFocusCropRect
import kotlin.math.roundToInt

internal val LocalSharedCourseBackdrop = compositionLocalOf<CourseWallpaperSampling?> { null }
internal val LocalCourseBackdropViewport = compositionLocalOf<BackdropViewport?> { null }

/** One wallpaper source for both course materials, with a shared filtered surface for the bodies. */
internal class CourseWallpaperSampling(
    val filtered: SharedBlurBackdrop,
    private val pixels: State<WallpaperPixels?>,
    private val samplingDim: () -> Float,
) : Backdrop by filtered {
    val hasStaticBitmap: Boolean get() = pixels.value?.let { it.previewBlur == null } == true
    private var previous: CouiCausticBitmapSource? = null
    private var previousCrop: WallpaperCropState? = null
    private var previousSize = IntSize.Zero

    fun bitmapSource(): CouiCausticBitmapSource? {
        val frame = pixels.value?.takeIf { it.previewBlur == null } ?: return null
        val coordinates = filtered.source.sourceCoordinates ?: return null
        val size = coordinates.size
        val dim = samplingDim()
        previous?.takeIf {
            it.bitmap === frame.bitmap && it.coordinates === coordinates && it.dim == dim &&
                previousCrop == frame.crop && previousSize == size
        }?.let { return it }
        val crop = calculateFocusCropRect(frame.bitmap.width, frame.bitmap.height,
            size.width.toFloat(), size.height.toFloat(), frame.crop)
        if (crop == Rect.Zero) return null
        // Same pixel rounding as the visible FocusCroppedBitmap.
        val pixelRect = Rect(Offset(crop.left.roundToInt().toFloat(), crop.top.roundToInt().toFloat()),
            androidx.compose.ui.geometry.Size(crop.width.roundToInt().toFloat(), crop.height.roundToInt().toFloat()))
        previousCrop = frame.crop
        previousSize = size
        return CouiCausticBitmapSource(frame.bitmap, coordinates, pixelRect, dim).also { previous = it }
    }
}

private val PreparedCourseRenderOptions = BackdropRenderOptions(
    placementLayer = false,
    clipGenericOutlineInDraw = false,
)

/** Draw the already filtered source directly inside the caller's rounded clip. */
internal fun Modifier.drawPreparedCourseBackdrop(backdrop: Backdrop, redraw: () -> Any?): Modifier = drawBackdrop(
    backdrop = (backdrop as? CourseWallpaperSampling)?.filtered ?: backdrop,
    shape = { RectangleShape },
    effects = {},
    highlight = null,
    shadow = null,
    onDrawBackdrop = { sample ->
        redraw()
        sample()
    },
    renderOptions = PreparedCourseRenderOptions,
)
