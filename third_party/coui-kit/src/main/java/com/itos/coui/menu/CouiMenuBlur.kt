package com.itos.coui.menu

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import android.util.LruCache
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isFinite
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.RenderEffect as ComposeRenderEffect
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.layer.CompositingStrategy
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.os.trace
import com.itos.coui.backdrop.CouiBackdrop
import kotlin.math.ceil

@Composable
fun rememberCouiMenuBlur(backdrop: CouiBackdrop, background: Color): CouiMenuBlur? =
    rememberCouiKawaseBlur(backdrop, background, CouiMenuBlurRadius)

/** 菜单固定 radius160; 弹窗使用原厂 integer/coui_dialog_blur_radius = 200。 */
private const val CouiMenuBlurRadius = 160

/** 原厂 BackgroundBlurDrawable 的 Fast Kawase 载体, radius 为引擎等级而不是像素。 */
@Composable
internal fun rememberCouiKawaseBlur(backdrop: CouiBackdrop, background: Color, radius: Int): CouiMenuBlur? {
    if (Build.VERSION.SDK_INT < 33) return null
    val layers = List(10) { rememberGraphicsLayer() }
    return remember(backdrop, background, radius, layers) {
        CouiMenuBlur(backdrop, background, radius, layers)
    }
}

/** 移植 PJZ110 type2 的 Fast Kawase, radius 使用原厂引擎等级; 每轮保留独立像素网格。 */
@RequiresApi(33)
class CouiMenuBlur internal constructor(
    private val backdrop: CouiBackdrop,
    private val background: Color,
    private val radius: Int,
    private val layers: List<GraphicsLayer>,
) {
    private var firstSize = IntSize.Zero
    private var recordedBlurSize = IntSize.Zero
    private var recordedDensity: Density? = null
    private var recordedLayoutDirection: LayoutDirection? = null
    private var recordedScene: LayoutCoordinates? = null
    private var recordedSceneSize = IntSize.Zero
    private val sceneToTarget = Matrix()

    init {
        layers.forEach {
            it.compositingStrategy = CompositingStrategy.Offscreen
            it.clip = true
        }
    }

    fun draw(scope: DrawScope, coordinates: LayoutCoordinates) = draw(scope, coordinates, 1f, Offset.Zero)

    /** coordinates 为未动画的锚点；变换与消费者的实际绘制使用同一帧数据。 */
    internal fun draw(scope: DrawScope, coordinates: LayoutCoordinates, sampleScale: Float, translation: Offset,
        stableSampleGrid: Boolean = false) {
        fun drawSource(target: DrawScope) {
            target.withTransform({
                scale(1f / sampleScale, 1f / sampleScale, Offset.Zero)
                translate(-translation.x, -translation.y)
            }) { backdrop.draw(this, coordinates) }
        }
        val menuSize = scope.size
        if (!coordinates.isAttached || menuSize.width <= 0f || menuSize.height <= 0f) return
        if (radius == 0) {
            scope.drawRect(background)
            drawSource(scope)
            return
        }
        val origin = coordinates.localToScreen(Offset.Zero)
        val right = coordinates.localToScreen(Offset(menuSize.width, 0f))
        val bottom = coordinates.localToScreen(Offset(0f, menuSize.height))
        val gridScale = if (stableSampleGrid) 1f else sampleScale
        val deviceWidth = (right - origin).getDistance() * gridScale
        val deviceHeight = (bottom - origin).getDistance() * gridScale
        if (!deviceWidth.isFinite() || !deviceHeight.isFinite() || deviceWidth <= 0f || deviceHeight <= 0f) {
            scope.drawRect(background)
            return
        }

        // native 以设备像素决定网格, 而不是 dp 或未变形的菜单尺寸。
        val captureSize = captureSize(deviceWidth, deviceHeight)
        val blurSize = recordCapture(scope, menuSize, captureSize) { drawSource(this) }
        recordedScene = null
        scope.withTransform({
            // native 使用输入区域与实际中间图尺寸的比值, 不是固定乘八。
            scale(menuSize.width / blurSize.width, menuSize.height / blurSize.height, Offset.Zero)
        }) {
            drawLayer(layers[9])
        }
    }

    /** Filter a retained producer in its own pixel space and crop it at the moving window. */
    internal fun drawScene(scope: DrawScope, target: LayoutCoordinates, sampleScale: Float,
        translation: Offset): Boolean {
        val source = backdrop.sceneCoordinates?.invoke() ?: return false
        if (radius == 0 || !target.isAttached || source.size.width <= 0 || source.size.height <= 0) return false
        val size = source.size
        val origin = source.localToScreen(Offset.Zero)
        val right = source.localToScreen(Offset(size.width.toFloat(), 0f))
        val bottom = source.localToScreen(Offset(0f, size.height.toFloat()))
        if (!origin.isFinite || !right.isFinite || !bottom.isFinite) return false
        val captureSize = captureSize((right - origin).getDistance(), (bottom - origin).getDistance())
        val blurSize = if (recordedScene !== source || recordedSceneSize != size || firstSize != captureSize ||
            recordedDensity?.density != scope.density || recordedDensity?.fontScale != scope.fontScale ||
            recordedLayoutDirection != scope.layoutDirection) {
            // This capture retains the producer's RenderNode. Producer updates invalidate its
            // pixels through the dependency; changing the dialog's transform never records it.
            recordCapture(scope, Size(size.width.toFloat(), size.height.toFloat()), captureSize) {
                backdrop.draw(this, source)
            }.also {
                recordedScene = source
                recordedSceneSize = size
            }
        } else recordedBlurSize
        val localOrigin = target.screenToLocal(origin)
        val localRight = target.screenToLocal(right)
        val localBottom = target.screenToLocal(bottom)
        if (!localOrigin.isFinite || !localRight.isFinite || !localBottom.isFinite) return false
        val x = (localRight - localOrigin) / (blurSize.width * sampleScale)
        val y = (localBottom - localOrigin) / (blurSize.height * sampleScale)
        val offset = (localOrigin - translation) / sampleScale
        sceneToTarget[0, 0] = x.x
        sceneToTarget[0, 1] = x.y
        sceneToTarget[1, 0] = y.x
        sceneToTarget[1, 1] = y.y
        sceneToTarget[3, 0] = offset.x
        sceneToTarget[3, 1] = offset.y
        scope.drawRect(background)
        scope.withTransform({ transform(sceneToTarget) }) { drawLayer(layers[9]) }
        return true
    }

    private fun captureSize(width: Float, height: Float): IntSize =
        IntSize(ceil(width / 16f).toInt().coerceAtLeast(1) * 8,
            ceil(height / 16f).toInt().coerceAtLeast(1) * 8)

    private fun recordCapture(scope: DrawScope, sourceSize: Size, captureSize: IntSize,
        drawSource: DrawScope.() -> Unit): IntSize {
        updateEffects(captureSize)
        layers[0].record(scope, scope.layoutDirection, captureSize) {
            drawRect(background)
            withTransform({
                scale(captureSize.width / sourceSize.width, captureSize.height / sourceSize.height, Offset.Zero)
            }, drawSource)
        }
        val blurSize = IntSize(captureSize.width / 4, captureSize.height / 4)
        recordBlurPasses(scope, blurSize)
        return blurSize
    }

    private fun recordBlurPasses(scope: DrawScope, size: IntSize) {
        val density = recordedDensity
        if (recordedBlurSize == size && density != null && density.density == scope.density &&
            density.fontScale == scope.fontScale && recordedLayoutDirection == scope.layoutDirection
        ) return
        // 固定指令只引用前驱层; RenderNode 继续跟踪动态内容, GraphicsLayer 负责恢复被回收的指令。
        // 保存 Density 值, 不让恢复回调持有不断重定向的 DrawScope。
        val recordingDensity = Density(scope.density, scope.fontScale)
        for (index in 1..9) {
            val previous = layers[index - 1]
            layers[index].record(recordingDensity, scope.layoutDirection, size) {
                drawLayer(previous)
            }
        }
        recordedBlurSize = size
        recordedDensity = recordingDensity
        recordedLayoutDirection = scope.layoutDirection
    }

    private fun updateEffects(captureSize: IntSize) {
        if (firstSize == captureSize) return
        val effects = checkNotNull(KawaseEffects.get(KawaseRecipe(radius, captureSize)))
        layers.forEachIndexed { index, layer -> layer.renderEffect = effects[index] }
        firstSize = captureSize
    }
}

private data class KawaseRecipe(val radius: Int, val captureSize: IntSize)

/** Effects capture uniform values. The cache holds no window, sampled pixels or GraphicsLayer. */
@RequiresApi(33)
private object KawaseEffects : LruCache<KawaseRecipe, List<ComposeRenderEffect>>(8) {
    override fun create(key: KawaseRecipe): List<ComposeRenderEffect> = trace("COUI Kawase.buildRecipe") {
        val downsample = RuntimeShader(MenuDownsampleShader)
        val blur = RuntimeShader(MenuKawaseShader)
        val capture = key.captureSize
        downsample.setFloatUniform("inputSize", capture.width.toFloat(), capture.height.toFloat())
        blur.setFloatUniform("inputSize", capture.width / 4f, capture.height / 4f)
        buildList {
            add(RenderEffect.createRuntimeShaderEffect(downsample, "child").asComposeRenderEffect())
            val step = (key.radius - 10f) / (9f * 10f)
            for (pass in 1..9) {
                blur.setFloatUniform("in_blurOffset", pass * step / 8f)
                add(RenderEffect.createRuntimeShaderEffect(blur, "child").asComposeRenderEffect())
            }
        }
    }
}

// librenderengineextimpl.so: 0x2fd60; sampleChild 承接原 child shader 的 0.25 局部矩阵与 Clamp。
private const val MenuDownsampleShader = """
    uniform shader child;
    uniform float2 inputSize;
    half offset = 0.25;

    half4 sampleChild(float2 xy) {
        return child.eval(clamp(xy * 4.0, float2(0.5), inputSize - 0.5));
    }

    half4 main(float2 xy) {
        half4 c = sampleChild(xy + float2(+offset, +offset));
             c += sampleChild(xy + float2(+offset, -offset));
             c += sampleChild(xy + float2(-offset, -offset));
             c += sampleChild(xy + float2(-offset, +offset));
        return half4(c.rgb * 0.25, 1.0);
    }
"""

// librenderengineextimpl.so: 0x33f4a; 只增加逻辑图像边界 Clamp, 不采样 RenderNode 的对齐余量。
private const val MenuKawaseShader = """
    uniform shader child;
    uniform float2 inputSize;
    uniform float in_blurOffset;

    half4 sampleChild(float2 xy) {
        return child.eval(clamp(xy, float2(0.5), inputSize - 0.5));
    }

    half4 main(float2 xy) {
        half4 c = sampleChild(xy);
        c += sampleChild(xy + float2(+in_blurOffset, +in_blurOffset));
        c += sampleChild(xy + float2(+in_blurOffset, -in_blurOffset));
        c += sampleChild(xy + float2(-in_blurOffset, -in_blurOffset));
        c += sampleChild(xy + float2(-in_blurOffset, +in_blurOffset));
        return half4(c.rgb * 0.2, 1.0);
    }
"""
