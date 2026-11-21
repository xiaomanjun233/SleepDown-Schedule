package com.xiaomanjun.sleepdownschedule.glass

import android.os.Build
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withContext
import com.xiaomanjun.sleepdownschedule.core.wallpaper.WallpaperTexturePyramid
import com.xiaomanjun.sleepdownschedule.core.wallpaper.buildWallpaperTexturePyramid
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.IntSize
import android.graphics.Bitmap
import androidx.compose.ui.layout.LayoutCoordinates
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.BackdropRenderOptions
import com.kyant.backdrop.BackdropViewport
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.MaterialBackdrop
import com.kyant.backdrop.backdrops.MaterialBackdropFilter
import com.kyant.backdrop.drawBackdrop
import com.xiaomanjun.sleepdownschedule.core.wallpaper.WallpaperCropState
import com.xiaomanjun.sleepdownschedule.core.wallpaper.WallpaperPixels
import com.xiaomanjun.sleepdownschedule.core.wallpaper.calculateFocusCropRect
import kotlin.math.roundToInt
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.xiaomanjun.sleepdownschedule.core.performance.AppMaterialPreferences
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.LocalCourseBackdropFilter
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.LocalNativeCourseCardMode
import com.xiaomanjun.sleepdownschedule.core.ui.material.PersonalizationPreviewState
import com.xiaomanjun.sleepdownschedule.core.wallpaper.hasAnyWallpaper
import com.xiaomanjun.sleepdownschedule.model.ScheduleConfigEntity
import com.xiaomanjun.sleepdownschedule.model.SimpleCourseCardBlurMax
import com.xiaomanjun.sleepdownschedule.glass.ui.*

internal data class CourseWallpaperFrame(
    val bitmap: Bitmap, val coordinates: LayoutCoordinates, val crop: Rect, val dim: Float,
)

internal val LocalCourseBackdropViewport = compositionLocalOf<BackdropViewport?> { null }

/** One wallpaper source for both course materials, with a shared filtered surface for the bodies. */
internal class CourseMaterialSampling(
    val filtered: MaterialBackdrop,
    private val pixels: State<WallpaperPixels?>,
    val texturePyramid: State<WallpaperTexturePyramid?> = mutableStateOf(null),
    val textureRadiusPx: State<Float> = mutableStateOf(filtered.filter?.radiusPx ?: 0f),
    private val samplingDim: () -> Float,
) : Backdrop by filtered {
    val textureRenderer by lazy {
        if (Build.VERSION.SDK_INT >= 33) CourseTextureRenderer() else null
    }
    val contourPrograms by lazy {
        if (Build.VERSION.SDK_INT >= 33)
            com.itos.coui.effect.CouiContourPrograms(com.itos.coui.effect.CouiContourStyle.LauncherFolder)
        else null
    }
    val causticPrograms by lazy {
        if (Build.VERSION.SDK_INT >= 34) com.itos.coui.effect.CouiCausticPrograms() else null
    }
    val supportsTexture: Boolean get() = Build.VERSION.SDK_INT >= 33 && hasStaticBitmap
    val hasStaticBitmap: Boolean get() = pixels.value?.previewBlur == null
    val textureReady: Boolean get() = supportsTexture && pixels.value?.let {
        texturePyramid.value?.source === it.bitmap
    } == true
    private var previous: CourseWallpaperFrame? = null
    private var previousCrop: WallpaperCropState? = null
    private var previousSize = IntSize.Zero

    fun textureFrame(): CourseWallpaperFrame? {
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
        return CourseWallpaperFrame(frame.bitmap, coordinates, pixelRect, dim).also { previous = it }
    }
}

/** Resolve the prefix once for the entire timetable, including the Gaussian-only preference. */
@Composable
internal fun rememberCourseMaterialSampling(
    source: LayerBackdrop,
    config: ScheduleConfigEntity,
    quality: Float,
    pixels: State<WallpaperPixels?>,
    dark: Boolean,
    preview: PersonalizationPreviewState,
    samplingDim: () -> Float,
): CourseMaterialSampling {
    val nativeMode = LocalNativeCourseCardMode.current
    val nativeFilter = LocalCourseBackdropFilter.current
    val density = LocalDensity.current
    val blur = preview.cardBlur ?: config.courseCardBlur
    val recipe = when {
        !AppMaterialPreferences.policy.denseMaterials || !config.hasAnyWallpaper() -> null
        nativeMode != null -> if (nativeMode.enabled && nativeFilter != null) {
            val radius = nativeMode.blurRadiusPx
            remember(nativeFilter, dark, radius) { MaterialBackdropFilter(radius, effect = nativeFilter(dark, radius)) }
        } else null
        config.courseCardGlassEnabled -> {
            val frame = courseCardGlassEffectFrame(GlassTokens.courseCard(blur), blur, quality, true)
            MaterialBackdropFilter(with(density) { (frame.blur ?: 0.dp).toPx() }, frame.useVibrancy)
        }
        config.courseCardGaussianBlurEnabled -> MaterialBackdropFilter(
            with(density) { (blur.coerceIn(0f, SimpleCourseCardBlurMax) * quality).dp.toPx() })
        else -> null
    }
    val filtered = remember(source) { MaterialBackdrop(source) }
    val pyramid = remember(source) { mutableStateOf<WallpaperTexturePyramid?>(null) }
    LaunchedEffect(pixels) {
        snapshotFlow { pixels.value?.bitmap }.collectLatest { bitmap ->
            pyramid.value = if (bitmap == null || Build.VERSION.SDK_INT < 33) null
                else withContext(Dispatchers.Default) { buildWallpaperTexturePyramid(bitmap) }
        }
    }
    val currentPixels = pixels.value
    val ready = currentPixels != null && currentPixels.previewBlur == null &&
        pyramid.value?.source === currentPixels.bitmap
    filtered.filter = recipe.takeUnless { ready && Build.VERSION.SDK_INT >= 33 }
    val radius = rememberUpdatedState(recipe?.radiusPx ?: 0f)
    val dim = rememberUpdatedState(samplingDim)
    return remember(filtered, pixels) {
        CourseMaterialSampling(filtered, pixels, pyramid, radius) { dim.value() }
    }
}

private val PreparedCourseRenderOptions = BackdropRenderOptions(
    placementLayer = false,
    clipGenericOutlineInDraw = false,
)

/** Draw the already filtered source directly inside the caller's rounded clip. */
internal fun Modifier.drawPreparedCourseBackdrop(backdrop: Backdrop, redraw: () -> Any?): Modifier = drawBackdrop(
    backdrop = (backdrop as? CourseMaterialSampling)?.filtered ?: backdrop,
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
