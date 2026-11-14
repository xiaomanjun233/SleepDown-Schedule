package com.itos.coui.effect

import android.graphics.BlendMode
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.util.DisplayMetrics
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode as ComposeBlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.CompositingStrategy
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.core.os.trace
import com.itos.coui.backdrop.CouiBackdrop
import com.itos.coui.backdrop.CouiSamplingCoordinates
import com.itos.coui.diagnostic.CouiRenderDiagnostics
import com.itos.coui.shape.CouiCapsuleShape
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.min

data class CouiFloatingMaterial(
    val firstColor: Color,
    val firstBlendMode: BlendMode,
    val secondColor: Color,
    val edgeAlpha: Float,
    val innerGlowAlpha: Float,
    val secondBlendMode: BlendMode = BlendMode.LUMINOSITY,
    val blurRadius: Float = 25f,
    val strokeType: Int = 1,
    val index: Int = 2,
    val extension: Int = 0,
    val lightColor: Color = Color.White,
    val themeTint: Color = Color.Transparent,
    val edgeWidthDp: Float = 2.5f,
    val innerGlowScale: Float = 1f,
    /** 只压暗采样输入; 两层颜色及混合模式保持原值。 */
    val sampleBrightness: Float = 1f,
)

/** 长度均为像素; lightZ=0 保留原厂 renderer 默认高度, smoothCorner 对应底栏的 fullsmooth Outline。 */
data class CouiFloatingShadow(
    val elevation: Float,
    val color: Color,
    val lightY: Float,
    val lightZ: Float,
    val lightRadius: Float,
    val causticColor: Color,
    val layout: Rect,
    val sdf: Float,
    val power: Float = 0.6f,
    val powerMix: Float = 0.6f,
    val speed: Float = 18f,
    val smoothCorner: Boolean = false,
)

/** 创建时直接验证原生片段能否编译; 宿主负责报告失败, 不在绘制时吞没错误。 */
@RequiresApi(34)
class CouiFloatingRenderer private constructor(internal val surfaceCount: Int) {
    constructor() : this(3)

    internal val standalone = surfaceCount == 1
    internal val hasThirdBall = surfaceCount == 3
    internal val shaders = if (standalone) {
        listOf(RuntimeShader(couiFloatingStandaloneShader()))
    } else {
        val source = couiFloatingShader(hasThirdBall)
        List(2) { slot ->
            RuntimeShader(source).apply { setIntUniform("MATERIAL_INDEX", slot + 1) }
        }
    }
    internal val causticShaders = List(surfaceCount) { RuntimeShader(CouiCausticShader) }
    internal val shadowRenderer = CouiShadowRenderer.createOrNull(surfaceCount)
    internal val destinationEffect = RenderEffect.createRuntimeShaderEffect(
        RuntimeShader("uniform shader image; half4 main(float2 p) { return image.eval(p); }"), "image",
    )

    companion object {
        fun standalone(): CouiFloatingRenderer = CouiFloatingRenderer(1)
        /** 搜索框与取消按钮的真实双主体链；不创建第三个布局或内容节点。 */
        fun pair(): CouiFloatingRenderer = CouiFloatingRenderer(2)
    }
}

@RequiresApi(34)
private fun floatingMaterialEffect(
    renderer: CouiFloatingRenderer,
    material: CouiFloatingMaterial,
    inputEffect: RenderEffect,
    index: Int,
    slots: List<Int>,
    nodes: List<CouiFloatingSurface>,
    bounds: Rect,
    density: Float,
    contourGlowEnabled: Boolean,
    foregroundComposition: Boolean,
    useMenuLightEdge: Boolean,
    verticalFusion: Boolean,
): RenderEffect {
    val node = nodes[index]
    val height = bounds.height
    val origin = bounds.topLeft
    val ball = node.ball(height, origin)
    val shader = renderer.shaders[if (renderer.standalone) 0 else material.index - 1]
    shader.setFloatUniform("u_size", bounds.width, height)
    shader.setFloatUniform("u_alpha", node.alpha)
    shader.setFloatUniform("uUseMenuLightEdge", if (useMenuLightEdge) 1f else 0f)
    shader.setFloatUniform("u_commonArray", floatArrayOf(bounds.width * .5f, height * .5f,
        ball[0], ball[1], ball[2], ball[3], ball[4], ball[5], ball[6], ball[7], height, 0f))
    if (renderer.standalone) {
        shader.setFloatUniform("u_baseShapeCtxArray", floatingShapeContext(ball[2], ball[3], ball[5], ball[4]))
    } else {
        val balls = List(renderer.surfaceCount) { slot ->
            val linked = slots.getOrNull(slot)?.let(nodes::get)
            if (linked?.visible == true) linked.ball(height, origin) else FloatArray(8)
        }
        balls.forEachIndexed { slot, linked ->
            shader.setFloatUniform("u_ball${slot + 1}Array", linked)
            shader.setFloatUniform("u_ball${slot + 1}ShapeCtxArray",
                floatingShapeContext(linked[2], linked[3], linked[5], linked[4]))
        }
        shader.setFloatUniform("u_preCmpArray12", floatingPair(balls[0], balls[1], 4f * density, verticalFusion))
        if (renderer.hasThirdBall) {
            shader.setFloatUniform("u_preCmpArray13", floatingPair(balls[0], balls[2], 4f * density))
            shader.setFloatUniform("u_preCmpArray23", floatingPair(balls[1], balls[2], 4f * density))
        }
    }
    val edge = floatingEdge(ball, material.edgeWidthDp * density,
        if (contourGlowEnabled) material.edgeAlpha else 0f, material.strokeType)
    val glow = floatingInnerGlow(ball, if (contourGlowEnabled) material.innerGlowAlpha else 0f,
        material.strokeType, material.innerGlowScale)
    for (parameters in listOf(edge, glow)) {
        parameters[0] = material.lightColor.red
        parameters[1] = material.lightColor.green
        parameters[2] = material.lightColor.blue
    }
    shader.setFloatUniform("u_edgeArray", edge)
    shader.setFloatUniform("u_shadowArray", glow)
    shader.setFloatUniform("u_shadowShapeCtxArray", floatingShadowShapeContext(ball, glow))
    shader.setColorUniform("u_color", Color.Transparent.toArgb())
    shader.setColorUniform("u_themeTint", material.themeTint.toArgb())
    val effect = RenderEffect.createChainEffect(
        RenderEffect.createRuntimeShaderEffect(shader, "u_originShader"), inputEffect)
    return if (foregroundComposition) effect else
        RenderEffect.createBlendModeEffect(renderer.destinationEffect, effect, BlendMode.SRC_OVER)
}

private fun CouiFloatingSurface.causticBounds(shadow: CouiFloatingShadow): Rect = mapRect(
    Rect(shadow.layout.left, shadow.layout.top, bounds.width + shadow.layout.right,
        bounds.height + shadow.layout.bottom),
)

@RequiresApi(34)
private fun floatingCausticEffect(
    shader: RuntimeShader,
    node: CouiFloatingSurface,
    shadow: CouiFloatingShadow,
    bounds: Rect,
    foregroundComposition: Boolean,
    shadowOpacity: Float,
): RenderEffect {
    shader.setIntUniform("u_foregroundComposition", if (foregroundComposition) 1 else 0)
    shader.setFloatUniform("u_offset", bounds.left - node.bounds.left, bounds.top - node.bounds.top)
    shader.setFloatUniform("u_invScale", 1f / node.scaleX, 1f / node.scaleY)
    shader.setFloatUniform("u_commonArray", floatArrayOf(0f, 0f, 0f, 0f, node.bounds.width,
        node.bounds.height, node.resolvedCornerRadius, 0f, 0f, 0f, 0f, 0f))
    val color = shadow.causticColor
    shader.setFloatUniform("u_pyrolysisArray", floatArrayOf(color.red, color.green, color.blue,
        color.alpha * node.alpha * shadowOpacity, shadow.layout.left, shadow.layout.top,
        shadow.layout.right, shadow.layout.bottom, shadow.sdf, shadow.power, shadow.powerMix,
        shadow.speed, 0.45f, 0.6f))
    return RenderEffect.createRuntimeShaderEffect(shader, "u_originShader")
}

/**
 * 单主体 BaseShader; 不绑定底栏, 不创建融球资源。
 * foregroundComposition 为 true 时材质和光晕仅输出前景; 共用 backdrop, 源须覆盖不透明 SDR 页面。
 * beforeContent 在材质之后、内容之前直接混合到目标；接收内容的缓存裁剪路径，调用方不得修改。
 * 回调坐标仍为节点本地坐标，变换及裁剪由调用方提供。
 * renderer、contentLayer 与返回的 Modifier 绑定一个绘制节点，不跨节点复用。
 */
@RequiresApi(34)
@Composable
fun Modifier.couiFloatingSurface(
    backdrop: CouiBackdrop,
    renderer: CouiFloatingRenderer,
    material: CouiFloatingMaterial,
    shadow: CouiFloatingShadow,
    shape: Shape,
    contentLayer: GraphicsLayer,
    contourGlowEnabled: Boolean,
    foregroundComposition: Boolean = false,
    beforeContent: (DrawScope.(Path) -> Unit)? = null,
    surface: () -> CouiFloatingSurface,
): Modifier = couiFloatingSurface(backdrop, renderer, material, shadow, shape, contentLayer,
    contourGlowEnabled, foregroundComposition, beforeContent, useMenuLightEdge = false, surface = surface)

@RequiresApi(34)
@Composable
internal fun Modifier.couiFloatingSurface(
    backdrop: CouiBackdrop,
    renderer: CouiFloatingRenderer,
    material: CouiFloatingMaterial,
    shadow: CouiFloatingShadow,
    shape: Shape,
    contentLayer: GraphicsLayer,
    contourGlowEnabled: Boolean,
    foregroundComposition: Boolean,
    beforeContent: (DrawScope.(Path) -> Unit)?,
    useMenuLightEdge: Boolean,
    surface: () -> CouiFloatingSurface,
): Modifier {
    require(renderer.standalone)
    return floatingSurfaces(backdrop, renderer, listOf(material), listOf(shadow), listOf(shape),
        listOf(contentLayer), contourGlowEnabled, foregroundComposition,
        useMenuLightEdge = useMenuLightEdge,
        beforeContent = beforeContent?.let { draw -> { _, path -> draw(path) } }) { listOf(surface()) }
}

/** 三主体在同一节点按 Z 排序，同 Z 保留宿主子项顺序；资源与返回的 Modifier 不跨节点复用。菜单 index 1，两侧附件 index 2。 */
@RequiresApi(34)
@Composable
fun Modifier.couiFloatingSurfaces(
    backdrop: CouiBackdrop,
    renderer: CouiFloatingRenderer,
    materials: List<CouiFloatingMaterial>,
    shadows: List<CouiFloatingShadow>,
    shapes: List<Shape>,
    contentLayers: List<GraphicsLayer>,
    contourGlowEnabled: Boolean,
    surfaces: () -> List<CouiFloatingSurface>,
): Modifier {
    require(renderer.surfaceCount == 3)
    require(materials.size == 3 && shadows.size == 3 && shapes.size == 3 && contentLayers.size == 3)
    require(materials.map { it.index }.sorted() == listOf(1, 2, 2))
    return floatingSurfaces(backdrop, renderer, materials, shadows, shapes, contentLayers, contourGlowEnabled, surfaces = surfaces)
}

/**
 * 双主体的采样、融球和光效；顺序与 material.index 必须分别为 1、2。
 * 如果宿主已经画了第一个主体，宿主裁掉重叠区域，本节点只在连接期间补画它的材质。
 */
@RequiresApi(34)
@Composable
fun Modifier.couiFloatingPair(
    backdrop: CouiBackdrop,
    renderer: CouiFloatingRenderer,
    materials: List<CouiFloatingMaterial>,
    shadows: List<CouiFloatingShadow>,
    shapes: List<Shape>,
    contentLayers: List<GraphicsLayer>,
    contourGlowEnabled: Boolean,
    beforeContent: (DrawScope.(Int, Path) -> Unit)? = null,
    drawOrder: List<Int> = listOf(0, 1),
    foregroundComposition: Boolean = false,
    shadowAlpha: (() -> Float)? = null,
    verticalFusion: Boolean = false,
    firstSurfaceDrawnByHost: Boolean = false,
    surfaces: () -> List<CouiFloatingSurface>,
): Modifier {
    require(renderer.surfaceCount == 2)
    require(materials.size == 2 && shadows.size == 2 && shapes.size == 2 && contentLayers.size == 2)
    require(materials.map { it.index } == listOf(1, 2))
    require(drawOrder.sorted() == listOf(0, 1))
    return floatingSurfaces(backdrop, renderer, materials, shadows, shapes, contentLayers, contourGlowEnabled,
        foregroundComposition = foregroundComposition,
        beforeContent = beforeContent, drawOrder = drawOrder,
        shadowAlpha = shadowAlpha, verticalFusion = verticalFusion,
        firstSurfaceDrawnByHost = firstSurfaceDrawnByHost, surfaces = surfaces)
}

@RequiresApi(34)
@Composable
private fun Modifier.floatingSurfaces(
    backdrop: CouiBackdrop,
    renderer: CouiFloatingRenderer,
    materials: List<CouiFloatingMaterial>,
    shadows: List<CouiFloatingShadow>,
    shapes: List<Shape>,
    contentLayers: List<GraphicsLayer>,
    contourGlowEnabled: Boolean,
    foregroundComposition: Boolean = false,
    useMenuLightEdge: Boolean = false,
    beforeContent: (DrawScope.(Int, Path) -> Unit)? = null,
    drawOrder: List<Int>? = null,
    shadowAlpha: (() -> Float)? = null,
    verticalFusion: Boolean = false,
    firstSurfaceDrawnByHost: Boolean = false,
    surfaces: () -> List<CouiFloatingSurface>,
): Modifier {
    backdrop.RetainWhileComposed()
    val recordingDensity = LocalDensity.current
    val density = recordingDensity.density
    val view = LocalView.current
    val display = view.display
    val defaultLightZ = remember(display, density) {
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        display?.getRealMetrics(metrics)
        // The light is calibrated to the short display edge, not to the animated bounds.
        val shortEdgeDp = if (metrics.widthPixels > 0 && metrics.heightPixels > 0 && metrics.density > 0f)
            min(metrics.widthPixels, metrics.heightPixels) / metrics.density else 450f
        500f * density * (2f + shortEdgeDp / 450f) / 3f
    }
    val shadowLayers = List(renderer.surfaceCount) { rememberGraphicsLayer() }
    val causticLayers = List(renderer.surfaceCount) { rememberGraphicsLayer() }
    val materialLayers = List(renderer.surfaceCount) { rememberGraphicsLayer() }
    val underlayLayer = if (renderer.surfaceCount == 2) rememberGraphicsLayer().apply {
        compositingStrategy = CompositingStrategy.Offscreen
        clip = true
    } else null
    // Capsule 轮廓只依赖像素尺寸; 每个槽位仅保留当前轮廓, 不积累动画历史。
    val capsuleOutlines = remember(renderer) {
        arrayOfNulls<Pair<Size, Outline>>(renderer.surfaceCount)
    }
    val materialInputs = remember(materials) {
        val recipes = mutableMapOf<CouiFloatingMaterial, RenderEffect>()
        materials.map { material ->
            // The slot index affects the mask. Identical Search materials share the input graph.
            recipes.getOrPut(material.copy(index = 1)) { couiControlMaterialEffect(material) }
        }
    }
    val materialSlots = remember(renderer, materials) {
        val indices = materials.map { it.index }
        indices.indices.map { if (renderer.standalone) emptyList() else floatingBallSlots(it, indices) }
    }
    val drawGroups = remember(shadows, drawOrder) {
        floatingDrawGroups(shadows.map { it.elevation }).map { group ->
            if (drawOrder == null) group else group.sortedBy(drawOrder::indexOf)
        }
    }
    val samplingCoordinates = remember { CouiSamplingCoordinates() }
    return onGloballyPositioned(samplingCoordinates::update).drawWithCache {
        CouiRenderDiagnostics.event("floating.material.build")
        val target = samplingCoordinates.coordinates
        val surfacesToDraw = surfaces()
        require(surfacesToDraw.size == renderer.surfaceCount)
        val nodes = if (firstSurfaceDrawnByHost) {
            val (anchor, attachment) = surfacesToDraw
            val connected = floatingPairIsConnected(anchor, attachment, 4f * density, verticalFusion)
            if (connected) surfacesToDraw else listOf(anchor.copy(visible = false), attachment)
        } else surfacesToDraw
        val shadowOpacity = shadowAlpha?.invoke()?.coerceIn(0f, 1f) ?: 1f
        if (target?.isAttached == true && size.width > 0f && size.height > 0f &&
            nodes.all { it.bounds.width > 0f && it.bounds.height > 0f }
        ) {
            val viewport = target.localBoundingBoxOf(target.findRootCoordinates(), false)
            val commands = mutableListOf<GraphicsLayer>()
            val patches = mutableListOf<FloatingPatch>()
            fun appendPatch(layer: GraphicsLayer, crop: Rect,
                blendMode: ComposeBlendMode = ComposeBlendMode.Src,
                includePrefix: Boolean = true, effect: (Rect) -> RenderEffect) {
                val clipped = crop.intersect(viewport)
                if (clipped.width <= 0f || clipped.height <= 0f) return
                val bounds = Rect(floor(clipped.left), floor(clipped.top), ceil(clipped.right), ceil(clipped.bottom))
                layer.topLeft = IntOffset(bounds.left.toInt(), bounds.top.toInt())
                layer.clip = true
                // 完整 patch 用 Src; 分离后的透明前景只使用 SrcOver。
                layer.blendMode = blendMode
                layer.renderEffect = effect(bounds).asComposeRenderEffect()
                patches.add(FloatingPatch(layer, bounds, if (includePrefix) commands.toList() else emptyList()))
                commands.add(layer)
            }
            val outlines = nodes.mapIndexed { index, node ->
                val shape = shapes[index]
                val outlineSize = node.bounds.size
                if (shape === CouiCapsuleShape) {
                    val cached = capsuleOutlines[index]
                    if (cached?.first == outlineSize) {
                        cached.second
                    } else {
                        shape.createOutline(outlineSize, layoutDirection, this).also {
                            capsuleOutlines[index] = outlineSize to it
                        }
                    }
                } else {
                    capsuleOutlines[index] = null
                    shape.createOutline(outlineSize, layoutDirection, this)
                }
            }
            val beforeClips = if (beforeContent != null) outlines.map { outline ->
                if (outline is Outline.Generic) outline.path else Path().apply { addOutline(outline) }
            } else emptyList()
            val windowOrigin = target.localToWindow(Offset.Zero)
            nodes.forEachIndexed { index, node ->
                if (!node.visible) return@forEachIndexed
                contentLayers[index].apply {
                    translationX = node.bounds.left
                    translationY = node.bounds.top
                    scaleX = node.scaleX
                    scaleY = node.scaleY
                    alpha = node.alpha
                }
                shadowLayers[index].apply {
                    translationX = node.bounds.center.x - node.bounds.width * node.scaleX * 0.5f
                    translationY = node.bounds.center.y - node.bounds.height * node.scaleY * 0.5f
                    scaleX = 1f
                    scaleY = 1f
                    alpha = 1f
                    clip = false
                    // alpha 不触发按主体尺寸离屏, 避免收放时裁掉外部半影。
                    compositingStrategy = CompositingStrategy.ModulateAlpha
                }
                contentLayers[index].setOutline(outlines[index])
                contentLayers[index].clip = true
            }
            val shadowRenderer = renderer.shadowRenderer
            fun castsShadow(index: Int) = nodes[index].visible && shadows[index].color.alpha > 0f
            if (shadowRenderer != null && shadowOpacity > 0f) {
                shadowLayers.forEachIndexed { index, layer ->
                    if (!castsShadow(index)) return@forEachIndexed
                    val node = nodes[index]
                    val bounds = node.bounds
                    val shadow = shadows[index]
                    val shadowColor = if (shadowOpacity == 1f) shadow.color else
                        shadow.color.copy(alpha = shadow.color.alpha * shadowOpacity)
                    val recordSize = IntSize(ceil(bounds.width * node.scaleX).toInt().coerceAtLeast(1),
                        ceil(bounds.height * node.scaleY).toInt().coerceAtLeast(1))
                    val windowX = windowOrigin.x + layer.translationX
                    val windowY = windowOrigin.y + layer.translationY
                    // The shadow depends on the caster and its position. Backdrop-only redraws
                    // can replay these commands while refreshing the sampled patches below.
                    trace("COUI floating.recordShadow") {
                        layer.record(density = recordingDensity, layoutDirection = layoutDirection, size = recordSize) {
                            drawIntoCanvas { canvas ->
                                val roundedRect = node.cornerRadius != null &&
                                    node.resolvedCornerRadius < min(bounds.width, bounds.height) * 0.5f
                                shadowRenderer.draw(canvas.nativeCanvas, index, bounds.width, bounds.height,
                                    node.scaleX, node.scaleY, windowX, windowY, node.alpha,
                                    shadowColor.toArgb(), shadow.elevation, shadow.lightY,
                                    if (shadow.lightZ > 0f) shadow.lightZ else defaultLightZ,
                                    shadow.lightRadius,
                                    if (roundedRect) CouiShadowOutline.RoundRect
                                    else if (shadow.smoothCorner) CouiShadowOutline.SmoothCapsule else CouiShadowOutline.Oval,
                                    radius = node.resolvedCornerRadius, clipping = !roundedRect)
                            }
                        }
                    }
                }
            }
            drawGroups.forEach { indices ->
                val group = indices.filter { nodes[it].visible }
                group.forEach { index ->
                    if (shadowRenderer != null && shadowOpacity > 0f && castsShadow(index)) {
                        commands.add(shadowLayers[index])
                    }
                    // 前景模式下, 原 Shader 在此阈值内严格输出透明, 无需录制背景或创建光晕图层。
                    // 完整 destination 模式仍需回填, 不能套用这一短路。
                    if (contourGlowEnabled && (!foregroundComposition ||
                            shadows[index].causticColor.alpha * nodes[index].alpha * shadowOpacity > 0.001f)) {
                        val node = nodes[index]
                        val shadow = shadows[index]
                        // 顶栏不回填背景; 默认路径仍保留完整 destination, 不改成 PLUS 光贡献。
                        appendPatch(causticLayers[index], node.causticBounds(shadow),
                            blendMode = if (foregroundComposition) ComposeBlendMode.SrcOver else ComposeBlendMode.Src) { bounds ->
                            trace("COUI floating.causticEffect") {
                                floatingCausticEffect(renderer.causticShaders[index], node, shadow, bounds,
                                    foregroundComposition, shadowOpacity)
                            }
                        }
                    }
                }
                group.forEach { index ->
                    val node = nodes[index]
                    val material = materials[index]
                    appendPatch(materialLayers[index], node.materialRect(material.extension, verticalFusion),
                        blendMode = if (foregroundComposition) ComposeBlendMode.SrcOver else ComposeBlendMode.Src,
                        includePrefix = !foregroundComposition) { bounds ->
                        trace("COUI floating.materialEffect") {
                            floatingMaterialEffect(renderer, material, materialInputs[index], index, materialSlots[index],
                                nodes, bounds, density, contourGlowEnabled, foregroundComposition, useMenuLightEdge, verticalFusion)
                        }
                    }
                    commands.add(contentLayers[index])
                }
            }
            val drawCommands = commands.toList()
            // Both bodies and their caustics read the same scene. Rasterize their union once
            // instead of replaying the timetable or top-bar blur for every material patch.
            val sharedUnderlay = underlayLayer?.takeIf { patches.size > 1 }
            val sampleBounds = sharedUnderlay?.let {
                Rect(patches.minOf { it.bounds.left }, patches.minOf { it.bounds.top },
                    patches.maxOf { it.bounds.right }, patches.maxOf { it.bounds.bottom })
            }
            onDrawWithContent {
                // 子项只录制透明前景; 真实输出遵守 shadow/caustic -> material/content 的 Z 分组顺序。
                drawContent()
                trace("COUI floating.recordSamples") {
                    if (sharedUnderlay != null && sampleBounds != null) {
                        sharedUnderlay.topLeft = IntOffset(sampleBounds.left.toInt(), sampleBounds.top.toInt())
                        sharedUnderlay.record(size = IntSize(sampleBounds.width.toInt(), sampleBounds.height.toInt())) {
                            translate(-sampleBounds.left, -sampleBounds.top) { backdrop.draw(this, target) }
                        }
                    }
                    patches.forEach { patch ->
                        patch.layer.record(size = IntSize(patch.bounds.width.toInt(), patch.bounds.height.toInt())) {
                            translate(-patch.bounds.left, -patch.bounds.top) {
                                if (sharedUnderlay != null) drawLayer(sharedUnderlay) else backdrop.draw(this, target)
                                patch.prefix.forEach { drawLayer(it) }
                            }
                        }
                    }
                }
                drawCommands.forEach { layer ->
                    if (beforeContent != null) {
                        val index = contentLayers.indexOf(layer)
                        if (index >= 0) beforeContent.invoke(this, index, beforeClips[index])
                    }
                    drawLayer(layer)
                }
            }
        } else {
            onDrawWithContent { drawContent() }
        }
    }
}

private data class FloatingPatch(val layer: GraphicsLayer, val bounds: Rect, val prefix: List<GraphicsLayer>)

private fun GraphicsLayer.setOutline(outline: Outline) {
    when (outline) {
        is Outline.Rectangle -> setRectOutline(outline.rect.topLeft, outline.rect.size)
        is Outline.Rounded -> setPathOutline(Path().apply { addRoundRect(outline.roundRect) })
        is Outline.Generic -> setPathOutline(outline.path)
    }
}
