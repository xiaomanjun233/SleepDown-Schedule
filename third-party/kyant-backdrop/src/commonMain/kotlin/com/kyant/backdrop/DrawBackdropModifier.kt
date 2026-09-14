// Modified for SleepDown on 2026-09-08; upstream 2.0.0, Apache-2.0 (see README.md).
package com.kyant.backdrop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.neverEqualPolicy
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.ObserverModifierNode
import androidx.compose.ui.node.observeReads
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.node.requireGraphicsContext
import androidx.compose.ui.node.requireLayoutDirection
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.SharedBlurBackdrop
import androidx.compose.ui.layout.positionInWindow
import kotlin.math.roundToInt
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.highlight.HighlightElement
import com.kyant.backdrop.internal.ShapeProvider
import com.kyant.backdrop.internal.recordLayer
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.InnerShadowElement
import com.kyant.backdrop.shadow.Shadow
import com.kyant.backdrop.shadow.ShadowElement
import androidx.compose.ui.unit.Density
import kotlin.math.ceil

private val DefaultHighlight = { Highlight.Default }
private val DefaultShadow = { Shadow.Default }
private val DefaultOnDrawBackdrop: DrawScope.(DrawScope.() -> Unit) -> Unit = { it() }

fun Modifier.drawPlainBackdrop(
    backdrop: Backdrop,
    shape: () -> Shape,
    effects: BackdropEffectScope.() -> Unit,
    layerBlock: (GraphicsLayerScope.() -> Unit)? = null,
    exportedBackdrop: LayerBackdrop? = null,
    onDrawBehind: (DrawScope.() -> Unit)? = null,
    onDrawBackdrop: DrawScope.(drawBackdrop: DrawScope.() -> Unit) -> Unit = DefaultOnDrawBackdrop,
    onDrawSurface: (DrawScope.() -> Unit)? = null,
    onDrawFront: (DrawScope.() -> Unit)? = null,
    renderOptions: BackdropRenderOptions = BackdropRenderOptions.Default
): Modifier {
    val shapeProvider = ShapeProvider(shape, renderOptions)
    return this
        .then(
            if (layerBlock != null) {
                Modifier.graphicsLayer(layerBlock)
            } else {
                Modifier
            }
        )
        .then(
            DrawBackdropElement(
                backdrop = backdrop,
                shapeProvider = shapeProvider,
                effects = effects,
                layerBlock = layerBlock,
                exportedBackdrop = exportedBackdrop,
                onDrawBehind = onDrawBehind,
                onDrawBackdrop = onDrawBackdrop,
                onDrawSurface = onDrawSurface,
                onDrawFront = onDrawFront
            )
        )
}

fun Modifier.drawBackdrop(
    backdrop: Backdrop,
    shape: () -> Shape,
    effects: BackdropEffectScope.() -> Unit,
    highlight: (() -> Highlight?)? = DefaultHighlight,
    shadow: (() -> Shadow?)? = DefaultShadow,
    innerShadow: (() -> InnerShadow?)? = null,
    layerBlock: (GraphicsLayerScope.() -> Unit)? = null,
    exportedBackdrop: LayerBackdrop? = null,
    onDrawBehind: (DrawScope.() -> Unit)? = null,
    onDrawBackdrop: DrawScope.(drawBackdrop: DrawScope.() -> Unit) -> Unit = DefaultOnDrawBackdrop,
    onDrawSurface: (DrawScope.() -> Unit)? = null,
    onDrawFront: (DrawScope.() -> Unit)? = null,
    renderOptions: BackdropRenderOptions = BackdropRenderOptions.Default
): Modifier {
    val shapeProvider = ShapeProvider(shape, renderOptions)
    return this
        .then(
            if (layerBlock != null) {
                Modifier.graphicsLayer(layerBlock)
            } else {
                Modifier
            }
        )
        .then(
            if (innerShadow != null) {
                InnerShadowElement(
                    shapeProvider = shapeProvider,
                    shadow = innerShadow
                )
            } else {
                Modifier
            }
        )
        .then(
            if (shadow != null) {
                ShadowElement(
                    shapeProvider = shapeProvider,
                    shadow = shadow
                )
            } else {
                Modifier
            }
        )
        .then(
            if (highlight != null) {
                HighlightElement(
                    shapeProvider = shapeProvider,
                    highlight = highlight
                )
            } else {
                Modifier
            }
        )
        .then(
            DrawBackdropElement(
                backdrop = backdrop,
                shapeProvider = shapeProvider,
                effects = effects,
                layerBlock = layerBlock,
                exportedBackdrop = exportedBackdrop,
                onDrawBehind = onDrawBehind,
                onDrawBackdrop = onDrawBackdrop,
                onDrawSurface = onDrawSurface,
                onDrawFront = onDrawFront
            )
        )
}

private class DrawBackdropElement(
    val backdrop: Backdrop,
    val shapeProvider: ShapeProvider,
    val effects: BackdropEffectScope.() -> Unit,
    val layerBlock: (GraphicsLayerScope.() -> Unit)?,
    val exportedBackdrop: LayerBackdrop?,
    val onDrawBehind: (DrawScope.() -> Unit)?,
    val onDrawBackdrop: DrawScope.(drawBackdrop: DrawScope.() -> Unit) -> Unit,
    val onDrawSurface: (DrawScope.() -> Unit)?,
    val onDrawFront: (DrawScope.() -> Unit)?
) : ModifierNodeElement<DrawBackdropNode>() {

    override fun create(): DrawBackdropNode {
        return DrawBackdropNode(
            backdrop = backdrop,
            shapeProvider = shapeProvider,
            effects = effects,
            layerBlock = layerBlock,
            exportedBackdrop = exportedBackdrop,
            onDrawBehind = onDrawBehind,
            onDrawBackdrop = onDrawBackdrop,
            onDrawSurface = onDrawSurface,
            onDrawFront = onDrawFront
        )
    }

    override fun update(node: DrawBackdropNode) {
        val effectsChanged = node.shapeProvider != shapeProvider || node.effects != effects
        node.backdrop = backdrop
        if (node.shapeProvider != shapeProvider) node.shapeProvider = shapeProvider
        node.effects = effects
        node.layerBlock = layerBlock
        if (node.exportedBackdrop != exportedBackdrop) {
            node.exportedBackdrop?.layerCoordinates = null
            node.exportedBackdrop = exportedBackdrop
        }
        node.onDrawBehind = onDrawBehind
        node.onDrawBackdrop = onDrawBackdrop
        node.onDrawSurface = onDrawSurface
        node.onDrawFront = onDrawFront
        node.synchronizeSampleLayer()
        if (effectsChanged) node.invalidateDrawCache() else node.invalidateDraw()
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "drawBackdrop"
        properties["backdrop"] = backdrop
        properties["shapeProvider"] = shapeProvider
        properties["effects"] = effects
        properties["layerBlock"] = layerBlock
        properties["exportedBackdrop"] = exportedBackdrop
        properties["onDrawBehind"] = onDrawBehind
        properties["onDrawBackdrop"] = onDrawBackdrop
        properties["onDrawSurface"] = onDrawSurface
        properties["onDrawFront"] = onDrawFront
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DrawBackdropElement) return false

        if (backdrop != other.backdrop) return false
        if (shapeProvider != other.shapeProvider) return false
        if (effects != other.effects) return false
        if (layerBlock != other.layerBlock) return false
        if (exportedBackdrop != other.exportedBackdrop) return false
        if (onDrawBehind != other.onDrawBehind) return false
        if (onDrawBackdrop != other.onDrawBackdrop) return false
        if (onDrawSurface != other.onDrawSurface) return false
        if (onDrawFront != other.onDrawFront) return false

        return true
    }

    override fun hashCode(): Int {
        var result = backdrop.hashCode()
        result = 31 * result + shapeProvider.hashCode()
        result = 31 * result + effects.hashCode()
        result = 31 * result + (layerBlock?.hashCode() ?: 0)
        result = 31 * result + (exportedBackdrop?.hashCode() ?: 0)
        result = 31 * result + (onDrawBehind?.hashCode() ?: 0)
        result = 31 * result + onDrawBackdrop.hashCode()
        result = 31 * result + (onDrawSurface?.hashCode() ?: 0)
        result = 31 * result + (onDrawFront?.hashCode() ?: 0)
        return result
    }
}

private class DrawBackdropNode(
    var backdrop: Backdrop,
    var shapeProvider: ShapeProvider,
    var effects: BackdropEffectScope.() -> Unit,
    var layerBlock: (GraphicsLayerScope.() -> Unit)?,
    var exportedBackdrop: LayerBackdrop?,
    var onDrawBehind: (DrawScope.() -> Unit)?,
    var onDrawBackdrop: DrawScope.(drawBackdrop: DrawScope.() -> Unit) -> Unit,
    var onDrawSurface: (DrawScope.() -> Unit)?,
    var onDrawFront: (DrawScope.() -> Unit)?
) : LayoutModifierNode, DrawModifierNode, GlobalPositionAwareModifierNode, ObserverModifierNode, Modifier.Node() {

    private val effectScope =
        object : BackdropEffectScopeImpl() {

            override val shape: Shape get() = shapeProvider.innerShape
        }

    private var graphicsLayer: GraphicsLayer? = null
    private val layerDiagnostics = BackdropLayerDiagnostics("Sample")
    private var lastEffectKey: Any? = null
    private var lastEffectShape: Shape? = null

    private val layoutLayerBlock: GraphicsLayerScope.() -> Unit = {
        clip = true
        shape = shapeProvider.snapshot(size, requireLayoutDirection(), this)
        compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen
    }

    private var layoutCoordinates: LayoutCoordinates? by mutableStateOf(null, neverEqualPolicy())

    private var padding by mutableFloatStateOf(0f)

    private val recordBackdropBlock: (DrawScope.() -> Unit) = {
        val canvas = drawContext.canvas
        val padding = padding
        val sampleScale = shapeProvider.options.sampleScale

        canvas.save()
        canvas.scale(sampleScale, sampleScale)
        if (padding != 0f) {
            canvas.translate(padding / sampleScale, padding / sampleScale)
        }
        onDrawBackdrop {
            with(backdrop) {
                drawBackdrop(
                    density = Density(effectScope.density / sampleScale, effectScope.fontScale),
                    coordinates = layoutCoordinates,
                    layerBlock = layerBlock
                )
            }
        }
        canvas.restore()
    }

    private val drawBackdropLayer: DrawScope.() -> Unit = {
        val layer = graphicsLayer
        if (layer != null) {
            val padding = padding
            val sampleScale = shapeProvider.options.sampleScale
            val allocationPadding = shapeProvider.options.allocationPadding ?: padding
            require(allocationPadding >= padding) { "Fixed allocation must cover effect padding" }

            val shared = backdrop as? SharedBlurBackdrop
            val sharedLayer = shared?.layer
            val sourceCoordinates = shared?.source?.layerCoordinates
            val cardCoordinates = layoutCoordinates
            val directSharedSample = sharedLayer != null && shared?.sampleScale == sampleScale &&
                sourceCoordinates?.isAttached == true && cardCoordinates?.isAttached == true &&
                layerBlock == null && shapeProvider.options.bounds() == null && exportedBackdrop == null
            if (directSharedSample) {
                // Nexio 2971759: shared wallpaper and card buffer use the same resolution.
                // Translate directly in sampled pixels, then apply this card's lens. Avoid the
                // expand-source -> shrink-consumer pair used by the generic Backdrop interface.
                val source = checkNotNull(sourceCoordinates)
                val card = checkNotNull(cardCoordinates)
                val offset = try { source.localPositionOf(card) } catch (_: IllegalArgumentException) {
                    card.positionInWindow() - source.positionInWindow()
                }
                recordLayer(
                    layer,
                    size = IntSize(
                        (size.width * sampleScale + allocationPadding * 2).roundToInt().coerceAtLeast(1),
                        (size.height * sampleScale + allocationPadding * 2).roundToInt().coerceAtLeast(1)
                    )
                ) {
                    val canvas = drawContext.canvas
                    canvas.save()
                    canvas.translate(-offset.x * sampleScale + padding, -offset.y * sampleScale + padding)
                    onDrawBackdrop { drawLayer(checkNotNull(sharedLayer)) }
                    canvas.restore()
                }
                BackdropDiagnostics.event("Sample.SharedDirect")
            } else {
                recordLayer(
                    layer,
                    size = IntSize(
                        if (sampleScale == 1f) size.width.toInt() + allocationPadding.toInt() * 2
                        else ceil(size.width * sampleScale + allocationPadding * 2).toInt(),
                        if (sampleScale == 1f) size.height.toInt() + allocationPadding.toInt() * 2
                        else ceil(size.height * sampleScale + allocationPadding * 2).toInt()
                    ),
                    block = recordBackdropBlock
                )
            }

            layer.topLeft = IntOffset.Zero
            layerDiagnostics.recorded(layer.size)
            drawContext.canvas.save()
            drawContext.canvas.scale(1f / sampleScale, 1f / sampleScale)
            val drawPadding = if (sampleScale == 1f) padding.toInt().toFloat() else padding
            drawContext.canvas.translate(-drawPadding, -drawPadding)
            drawLayer(layer)
            drawContext.canvas.restore()
        }
    }

    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints
    ): MeasureResult {
        val placeable = measurable.measure(constraints)
        return layout(placeable.width, placeable.height) {
            placeable.placeWithLayer(IntOffset.Zero, layerBlock = layoutLayerBlock)
        }
    }

    override fun ContentDrawScope.draw() {
        // Observe freeze/resume here, so resume refreshes the existing sample once even if no
        // layout callback follows. The retained GraphicsLayer still references child RenderNodes:
        // suppressing only the parent's drawContent does not stop their own invalidations.
        shapeProvider.options.coordinatesFrozen()
        if (!shapeProvider.options.enabled()) return drawContent()
        val bounds = shapeProvider.options.bounds()
        val sampleScale = shapeProvider.options.sampleScale
        require(sampleScale > 0f && sampleScale <= 1f)
        check(sampleScale == 1f || (bounds == null && exportedBackdrop == null)) {
            "Downsampling requires ordinary geometry without an exported backdrop"
        }
        check(bounds == null || exportedBackdrop == null) {
            "Fixed geometry does not support exported backdrops"
        }
        if (effectScope.update(this, (bounds?.size ?: size) * sampleScale,
                (bounds?.topLeft ?: Offset.Zero) * sampleScale, sampleScale)) {
            updateEffects(geometryChanged = true)
        }

        drawSurfaceCallback(onDrawBehind)
        drawBackdropLayer()
        drawSurfaceCallback(onDrawSurface)
        drawContent()
        drawSurfaceCallback(onDrawFront)

        exportedBackdrop?.graphicsLayer?.let { layer ->
            recordLayer(layer) {
                onDrawBehind?.invoke(this)
                drawBackdropLayer()
                onDrawSurface?.invoke(this)
                onDrawFront?.invoke(this)
            }
        }
    }

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        if (coordinates.isAttached) {
            if (backdrop.isCoordinatesDependent) {
                // LayoutCoordinates is mutable. Reassigning it with neverEqualPolicy on every
                // outer zoom/menu layout dirtied each frozen card's sample and cached ancestor.
                // Always accept a new node, but retain an existing node's recorded coordinates
                // through motion. Model, size, effect and source changes still invalidate normally.
                if (!shapeProvider.options.coordinatesFrozen() || layoutCoordinates !== coordinates) {
                    layoutCoordinates = coordinates
                }
            } else {
                if (layoutCoordinates != null) {
                    layoutCoordinates = null
                }
            }
            exportedBackdrop?.layerCoordinates = coordinates
        }
    }

    private fun DrawScope.drawSurfaceCallback(block: (DrawScope.() -> Unit)?) {
        if (block == null) return
        val bounds = shapeProvider.options.bounds()
        if (bounds == null) block() else {
            inset(bounds.left, bounds.top, size.width - bounds.right, size.height - bounds.bottom) {
                block()
            }
        }
    }

    override fun onObservedReadsChanged() {
        invalidateDrawCache()
    }

    fun invalidateDrawCache() {
        observeEffects()
    }

    private fun observeEffects() {
        observeReads { updateEffects() }
    }

    private fun updateEffects(geometryChanged: Boolean = false) {
        if (!shapeProvider.options.enabled() || !shapeProvider.options.sampleBackdrop || !isRenderEffectSupported()) return

        val effectKey = shapeProvider.options.effectKey()
        val effectShape = shapeProvider.innerShape
        if (!geometryChanged && effectKey != null && effectKey == lastEffectKey && effectShape == lastEffectShape) return

        effectScope.apply(effects)
        lastEffectKey = effectKey
        lastEffectShape = effectShape
        BackdropDiagnostics.event("Sample.EffectRebuilt")
        val effect = effectScope.renderEffect
        if (graphicsLayer?.renderEffect != effect) graphicsLayer?.renderEffect = effect
        padding = effectScope.padding
    }

    fun synchronizeSampleLayer() {
        if (!isAttached) return
        val context = requireGraphicsContext()
        if (shapeProvider.options.sampleBackdrop) {
            if (graphicsLayer == null) {
                graphicsLayer = context.createGraphicsLayer()
                lastEffectKey = null
                lastEffectShape = null
                layerDiagnostics.created()
            }
        } else {
            graphicsLayer?.let {
                context.releaseGraphicsLayer(it)
                layerDiagnostics.released()
            }
            graphicsLayer = null
        }
    }

    override fun onAttach() {
        synchronizeSampleLayer()
        observeEffects()
    }

    override fun onDetach() {
        val graphicsContext = requireGraphicsContext()
        graphicsLayer?.let { layer ->
            graphicsContext.releaseGraphicsLayer(layer)
            layerDiagnostics.released()
            graphicsLayer = null
        }

        effectScope.reset()
        lastEffectKey = null
        lastEffectShape = null
        layoutCoordinates = null
        exportedBackdrop?.layerCoordinates = null
    }
}
