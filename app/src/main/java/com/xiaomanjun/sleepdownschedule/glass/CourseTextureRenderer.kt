package com.xiaomanjun.sleepdownschedule.glass

import android.graphics.BitmapShader
import android.graphics.Paint
import android.graphics.RuntimeShader
import android.graphics.Shader
import androidx.compose.foundation.shape.AbsoluteRoundedCornerShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.LayoutDirection
import com.itos.coui.backdrop.CouiSamplingCoordinates
import com.kyant.backdrop.effects.textureLensShaderSource
import com.kyant.shapes.RoundedRectangularShape
import com.xiaomanjun.sleepdownschedule.core.wallpaper.WallpaperTexturePyramid
import kotlin.math.sqrt

/**
 * Page-owned programs. Every card records an immutable shader draw, not a RenderEffect or
 * sampled GraphicsLayer. The original Kyant lens formula consumes the shared texture shader.
 * This class is draw-thread confined; it must never be used while building mip pixels.
 */
@RequiresApi(33)
internal class CourseTextureRenderer {
    private val sampler = RuntimeShader(TextureSamplingShader)
    private val lenses = arrayOfNulls<RuntimeShader>(2)
    private val matrix = Matrix()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var pyramid: WallpaperTexturePyramid? = null
    private var raw: WallpaperTexturePyramid? = null
    private var textures = emptyList<BitmapShader>()
    private var first = -1
    private var second = -1

    fun shader(
        source: CourseMaterialSampling,
        coordinates: LayoutCoordinates,
        radiusPx: Float,
        sampleOffset: Offset = Offset.Zero,
        brightness: Float = 1f,
        vibrant: Boolean = false,
    ): Shader? {
        val pixels = source.textureFrame() ?: return null
        val prepared = source.texturePyramid.value?.takeIf { it.source === pixels.bitmap }
            ?: if (radiusPx > 0f) return null else {
                raw?.takeIf { it.source === pixels.bitmap } ?: WallpaperTexturePyramid(
                    pixels.bitmap, listOf(pixels.bitmap), floatArrayOf(0f)).also { raw = it }
            }
        if (!coordinates.isAttached || !pixels.coordinates.isAttached) return null
        if (pyramid !== prepared) {
            pyramid = prepared
            textures = prepared.levels.map { bitmap ->
                BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
                    setFilterMode(BitmapShader.FILTER_MODE_LINEAR)
                }
            }
            first = -1
            second = -1
        }
        matrix.reset()
        pixels.coordinates.transformFrom(coordinates, matrix)
        val kx = prepared.source.width / pixels.crop.width
        val ky = prepared.source.height / pixels.crop.height
        sampler.setFloatUniform("rowX", matrix[0, 0] * kx, matrix[1, 0] * kx,
            (matrix[3, 0] + matrix[0, 0] * sampleOffset.x + matrix[1, 0] * sampleOffset.y - pixels.crop.left) * kx)
        sampler.setFloatUniform("rowY", matrix[0, 1] * ky, matrix[1, 1] * ky,
            (matrix[3, 1] + matrix[0, 1] * sampleOffset.x + matrix[1, 1] * sampleOffset.y - pixels.crop.top) * ky)
        // Match HWUI's blur-radius to Gaussian-sigma conversion in source pixel units.
        val sigma = if (radiusPx > 0f) (radiusPx * 0.57735f + 0.5f) * sqrt(kx * ky) else 0f
        val pair = prepared.levelPair(sigma)
        if (first != pair.first) {
            first = pair.first
            sampler.setInputShader("textureA", textures[first])
        }
        if (second != pair.second) {
            second = pair.second
            sampler.setInputShader("textureB", textures[second])
        }
        val a = prepared.levels[first]
        val b = prepared.levels[second]
        sampler.setFloatUniform("scaleA", a.width.toFloat() / prepared.source.width,
            a.height.toFloat() / prepared.source.height)
        sampler.setFloatUniform("scaleB", b.width.toFloat() / prepared.source.width,
            b.height.toFloat() / prepared.source.height)
        sampler.setFloatUniform("blend", pair.mix)
        sampler.setFloatUniform("dim", pixels.dim)
        sampler.setFloatUniform("brightness", brightness)
        sampler.setFloatUniform("saturation", if (vibrant) 1.5f else 1f)
        return sampler
    }

    fun DrawScope.draw(
        source: CourseMaterialSampling,
        coordinates: LayoutCoordinates,
        shape: Shape,
        frame: GlassEffectFrame?,
        shaderTransform: ((Shader) -> Shader)?,
        radiusPx: Float = source.textureRadiusPx.value,
        brightness: Float = 1f,
    ): Boolean {
        val input = shader(source, coordinates, radiusPx, brightness = brightness,
            vibrant = frame?.useVibrancy == true) ?: return false
        val height = frame?.lensHeight?.toPx() ?: 0f
        val amount = frame?.lensAmount?.toPx() ?: 0f
        val output = if (height > 0f && amount > 0f) {
            val corners = when (shape) {
                is RoundedRectangularShape -> shape.corners(size, layoutDirection, this).let {
                    floatArrayOf(it.topLeft, it.topRight, it.bottomRight, it.bottomLeft)
                }
                is CornerBasedShape -> {
                    // Generic outlines such as the resize corner still expose the same corner
                    // geometry used by Kyant's lens. The caller retains the exact outline clip.
                    val absolute = shape is AbsoluteRoundedCornerShape || layoutDirection == LayoutDirection.Ltr
                    val maximum = size.minDimension / 2f
                    val startTop = shape.topStart.toPx(size, this).coerceAtMost(maximum)
                    val endTop = shape.topEnd.toPx(size, this).coerceAtMost(maximum)
                    val endBottom = shape.bottomEnd.toPx(size, this).coerceAtMost(maximum)
                    val startBottom = shape.bottomStart.toPx(size, this).coerceAtMost(maximum)
                    if (absolute) floatArrayOf(startTop, endTop, endBottom, startBottom)
                    else floatArrayOf(endTop, startTop, startBottom, endBottom)
                }
                else -> (shape.createOutline(size, layoutDirection, this) as? Outline.Rounded)?.roundRect?.let {
                    floatArrayOf(it.topLeftCornerRadius.x, it.topRightCornerRadius.x,
                        it.bottomRightCornerRadius.x, it.bottomLeftCornerRadius.x)
                }
            } ?: return false
            val dispersion = frame?.chromaticAberration == true
            val index = if (dispersion) 1 else 0
            val lens = lenses[index] ?: RuntimeShader(textureLensShaderSource(dispersion)).also { lenses[index] = it }
            lens.setInputShader("content", input)
            lens.setFloatUniform("size", size.width, size.height)
            lens.setFloatUniform("offset", 0f, 0f)
            lens.setFloatUniform("cornerRadii", corners)
            lens.setFloatUniform("refractionHeight", height)
            lens.setFloatUniform("refractionAmount", -amount)
            lens.setFloatUniform("depthEffect", if (frame?.depthEffect == true) 1f else 0f)
            if (dispersion) lens.setFloatUniform("chromaticAberration", 1f)
            lens
        } else input
        paint.shader = shaderTransform?.invoke(output) ?: output
        drawContext.canvas.nativeCanvas.drawRect(0f, 0f, size.width, size.height, paint)
        return true
    }
}

private const val TextureSamplingShader = """
uniform shader textureA;
uniform shader textureB;
uniform float3 rowX;
uniform float3 rowY;
uniform float2 scaleA;
uniform float2 scaleB;
uniform float blend;
uniform float dim;
uniform float brightness;
uniform float saturation;
half4 main(float2 position) {
    float3 p = float3(position, 1.0);
    float2 texel = float2(dot(p, rowX), dot(p, rowY));
    half4 c = mix(textureA.eval(texel * scaleA), textureB.eval(texel * scaleB), blend);
    c.rgb *= 1.0 - dim;
    c.a = mix(c.a, 1.0, dim);
    c.rgb *= brightness;
    float luminance = dot(c.rgb, half3(0.213, 0.715, 0.072));
    c.rgb = clamp(mix(half3(luminance), c.rgb, saturation), 0.0, c.a);
    return c;
}
"""

/**
 * The node stays attached while pixels prepare. Until publication it reads the one shared
 * GPU prefix; it never creates a temporary per-card blur or changes the component hierarchy.
 */
@RequiresApi(33)
@Composable
internal fun Modifier.courseTexture(
    source: CourseMaterialSampling,
    shape: () -> Shape,
    frame: GlassEffectFrame? = null,
    shaderTransform: ((Shader) -> Shader)? = null,
    enabled: () -> Boolean = { true },
    onSurface: (DrawScope.() -> Unit)? = null,
    radiusPx: Float? = null,
    brightness: Float = 1f,
): Modifier {
    val coordinates = remember { CouiSamplingCoordinates() }
    val redraw = rememberUpdatedState(LocalGlassSampleRecordKey.current)
    val viewport = LocalCourseBackdropViewport.current
    val latestShape = rememberUpdatedState(shape)
    val latestFrame = rememberUpdatedState(frame)
    val latestEnabled = rememberUpdatedState(enabled)
    val latestShaderTransform = rememberUpdatedState(shaderTransform)
    val latestSurface = rememberUpdatedState(onSurface)
    val latestRadius = rememberUpdatedState(radiusPx)
    val latestBrightness = rememberUpdatedState(brightness)
    return onGloballyPositioned(coordinates::update).drawWithCache {
        val outline = latestShape.value().createOutline(size, layoutDirection, this)
        val path = Path().apply { addOutline(outline) }
        onDrawWithContent {
            if (latestEnabled.value()) {
                redraw.value()
                val target = coordinates.coordinates?.takeIf { it.isAttached }
                if (target != null && viewport?.intersects(target) != false) clipPath(path) {
                    val drawn = source.textureRenderer?.run {
                        draw(source, target, latestShape.value(), latestFrame.value, latestShaderTransform.value,
                            latestRadius.value ?: source.textureRadiusPx.value, latestBrightness.value)
                    } == true
                    if (!drawn) with(source.filtered) { drawBackdrop(this@onDrawWithContent, target, null) }
                    latestSurface.value?.invoke(this)
                }
            }
            drawContent()
        }
    }
}
