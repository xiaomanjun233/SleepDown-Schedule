// Modified for SleepDown on 2026-09-08; upstream 2.0.0, Apache-2.0 (see README.md).
package com.kyant.backdrop.backdrops

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.requireGraphicsContext
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.toIntSize
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.BackdropEffectScopeImpl
import com.kyant.backdrop.BackdropLayerDiagnostics
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.internal.BackdropRecordingCache
import com.kyant.backdrop.internal.InverseLayerScope
import kotlin.math.roundToInt

/**
 * One blurred wallpaper prefix shared by all course cards; consumers retain their own lens.
 *
 * The prefix is recorded once at [SharedBlurSampleScale] of the screen resolution with the
 * blur/vibrancy effect retained on that layer. GraphicsLayer records commands, not flattened
 * pixels, so the effect must remain attached for consumers to draw the blurred source.
 */
class SharedBlurBackdrop(val source: LayerBackdrop, val radiusPx: Float, val vibrant: Boolean) : Backdrop {
    internal var layer: GraphicsLayer? by mutableStateOf(null)
    internal var sampleScale: Float = 1f
    // A recorder can publish its layer before the wallpaper producer has reported its
    // coordinates. Consumers created in that frame would otherwise record an empty sample
    // and keep it until the first page gesture invalidates their draw nodes.
    val ready: Boolean get() = layer != null && source.layerCoordinates?.isAttached == true
    override val isCoordinatesDependent = true
    private val inverse = InverseLayerScope()

    fun preRenderModifier(sourceKey: () -> Any?): Modifier = RecorderElement(this, sourceKey)

    override fun DrawScope.drawBackdrop(density: Density, coordinates: LayoutCoordinates?, layerBlock: (GraphicsLayerScope.() -> Unit)?) {
        val recorded = layer
        if (recorded == null || sampleScale <= 0f) {
            return with(source) { drawBackdrop(density, coordinates, layerBlock) }
        }
        val target = coordinates ?: return
        val origin = source.layerCoordinates ?: return
        val bakedScale = sampleScale
        withTransform({
            val offset = try { origin.localPositionOf(target) } catch (_: IllegalArgumentException) {
                target.positionInWindow() - origin.positionInWindow()
            }
            // Match LayerBackdrop's inverse/offset order, then expand source pixels.
            // I * T(-offset) * S(1/sampleScale): do not scale the full-resolution offset.
            if (layerBlock != null) with(inverse.apply { reset() }) { inverseTransform(density, layerBlock) }
            translate(-offset.x, -offset.y)
            scale(1f / bakedScale, 1f / bakedScale, pivot = Offset.Zero)
        }) { drawLayer(recorded) }
    }
}

/** Fixed sampling ratio for the shared prefix; blur radius is scaled by the same factor. */
const val SharedBlurSampleScale = 0.48f

private data class RecorderElement(val backdrop: SharedBlurBackdrop, val sourceKey: () -> Any?) : ModifierNodeElement<RecorderNode>() {
    override fun create() = RecorderNode(backdrop, sourceKey)
    override fun update(node: RecorderNode) {
        if (node.backdrop !== backdrop) {
            node.backdrop.layer = null
            node.backdrop.sampleScale = 1f
            node.cache.clear()
            node.resetEffectTracking()
            node.backdrop = backdrop
        }
        node.sourceKey = sourceKey
    }
    override fun InspectorInfo.inspectableProperties() { name = "sharedWallpaperBlur" }
}

private class RecorderNode(var backdrop: SharedBlurBackdrop, var sourceKey: () -> Any?) : Modifier.Node(), DrawModifierNode {
    val cache = BackdropRecordingCache()
    private var rawLayer: GraphicsLayer? = null
    private val diagnostics = BackdropLayerDiagnostics("SharedWallpaper")
    private val effects = object : BackdropEffectScopeImpl() { override val shape = RectangleShape }
    private var lastRadius = Float.NaN
    private var lastVibrant: Boolean? = null

    fun resetEffectTracking() {
        lastRadius = Float.NaN
        lastVibrant = null
        effects.reset()
    }

    override fun ContentDrawScope.draw() {
        drawContent()
        val raw = rawLayer ?: return
        val key = sourceKey()
        val fullSize = size.toIntSize()
        if (fullSize.width <= 0 || fullSize.height <= 0) return
        val sampled = IntSize(
            width = (fullSize.width * SharedBlurSampleScale).roundToInt().coerceAtLeast(1),
            height = (fullSize.height * SharedBlurSampleScale).roundToInt().coerceAtLeast(1)
        )
        val sourceDirty = cache.needsRecord(key, fullSize, density, fontScale, layoutDirection)
        val effectDirty = lastRadius != backdrop.radiusPx || lastVibrant != backdrop.vibrant
        if (sourceDirty) {
            // Downsampled copy of the full-resolution wallpaper prefix.
            raw.record(sampled) {
                drawContext.canvas.save()
                drawContext.canvas.scale(SharedBlurSampleScale, SharedBlurSampleScale)
                drawLayer(backdrop.source.graphicsLayer)
                drawContext.canvas.restore()
            }
            cache.recorded(key, fullSize, density, fontScale, layoutDirection)
            diagnostics.recorded(sampled)
        }
        if (effectDirty) {
            // Keep the effect on the actual shared layer. A second recorded drawLayer(raw)
            // retains a reference to raw; clearing raw.renderEffect would remove its blur.
            effects.update(this)
            effects.apply {
                if (backdrop.vibrant) vibrancy()
                blur(backdrop.radiusPx * SharedBlurSampleScale)
            }
            raw.renderEffect = effects.renderEffect
            lastRadius = backdrop.radiusPx
            lastVibrant = backdrop.vibrant
        }
        backdrop.layer = raw
        backdrop.sampleScale = SharedBlurSampleScale
    }

    override fun onAttach() {
        val context = requireGraphicsContext()
        rawLayer = context.createGraphicsLayer()
        diagnostics.created()
    }

    override fun onDetach() {
        backdrop.layer = null
        backdrop.sampleScale = 1f
        val context = requireGraphicsContext()
        rawLayer?.let { context.releaseGraphicsLayer(it); diagnostics.released() }
        rawLayer = null
        cache.clear()
        resetEffectTracking()
    }
}
