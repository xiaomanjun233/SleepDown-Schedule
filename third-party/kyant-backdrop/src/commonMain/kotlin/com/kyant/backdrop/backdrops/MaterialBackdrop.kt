// SleepDown implementation on the Apache-2.0 Kyant 2.0.0 API; see README.md.
package com.kyant.backdrop.backdrops

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.layer.CompositingStrategy
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.requireGraphicsContext
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.toIntSize
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.BackdropEffectScopeImpl
import com.kyant.backdrop.BackdropLayerDiagnostics
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.internal.BackdropRecordingCache
import com.kyant.backdrop.internal.recordLayer

/** A scene-wide prefix. The card renderer owns only its local lens, rim and foreground. */
data class MaterialBackdropFilter(
    val radiusPx: Float,
    val vibrant: Boolean = false,
    val effect: RenderEffect? = null,
)

/**
 * One source owner, one optional filtered raster, any number of consumers.
 *
 * The source node records content once, prepares its material, then presents the raw scene.
 * Consumers always receive this same handle, including the first frame. There is no readiness
 * switch to a temporary per-card filter and no delayed warm-up/re-record coroutine.
 * Raw and filtered images share the source coordinates; moving a card never dirties the source.
 */
@Stable
class MaterialBackdrop(val source: LayerBackdrop) : Backdrop {
    var filter: MaterialBackdropFilter? by mutableStateOf(null)
    internal var published: GraphicsLayer? = null
    internal var contentRevision by mutableIntStateOf(0)
    override val isCoordinatesDependent = true
    private val mapping = BackdropCoordinateMapping()

    fun sourceModifier(sourceKey: () -> Any?): Modifier = MaterialSourceElement(this, sourceKey)

    override fun DrawScope.drawBackdrop(
        density: Density,
        coordinates: LayoutCoordinates?,
        layerBlock: (GraphicsLayerScope.() -> Unit)?
    ) {
        // Reading the revision also invalidates direct (effect-free) consumers.
        contentRevision
        val image = published ?: return
        mapping.draw(this, image, source.layerCoordinates, coordinates)
    }
}

private data class MaterialSourceElement(
    val backdrop: MaterialBackdrop,
    val sourceKey: () -> Any?,
) : ModifierNodeElement<MaterialSourceNode>() {
    override fun create() = MaterialSourceNode(backdrop, sourceKey)
    override fun update(node: MaterialSourceNode) {
        if (node.backdrop !== backdrop) {
            node.detachSource()
            node.backdrop = backdrop
        }
        node.sourceKey = sourceKey
    }
    override fun InspectorInfo.inspectableProperties() { name = "materialBackdropSource" }
}

private class MaterialSourceNode(
    var backdrop: MaterialBackdrop,
    var sourceKey: () -> Any?,
) : Modifier.Node(), DrawModifierNode, GlobalPositionAwareModifierNode {
    private val cache = BackdropRecordingCache()
    private val sourceDiagnostics = BackdropLayerDiagnostics("MaterialSource")
    private val filterDiagnostics = BackdropLayerDiagnostics("MaterialFilter")
    private var filtered: GraphicsLayer? = null
    private var raster: GraphicsLayer? = null
    private var lastFilter: MaterialBackdropFilter? = null
    private val effects = object : BackdropEffectScopeImpl() { override val shape = RectangleShape }
    private var coordinates: LayoutCoordinates? = null

    override fun ContentDrawScope.draw() {
        val source = backdrop.source
        if (source.layerCoordinates == null) source.layerCoordinates = coordinates
        val dimensions = size.toIntSize()
        if (dimensions.width <= 0 || dimensions.height <= 0) return
        val key = sourceKey()
        val sourceDirty = cache.needsRecord(key, dimensions, density, fontScale, layoutDirection)
        if (sourceDirty) {
            recordLayer(source.graphicsLayer, dimensions) { source.onDraw(this@draw) }
            cache.recorded(key, dimensions, density, fontScale, layoutDirection)
            sourceDiagnostics.recorded(dimensions)
            Snapshot.withoutReadObservation { source.contentVersion++ }
        }

        val recipe = backdrop.filter
        val geometryDirty = effects.update(this)
        if (recipe == null) {
            releaseFilter()
            backdrop.published = source.graphicsLayer
        } else {
            val context = requireGraphicsContext()
            val input = filtered ?: context.createGraphicsLayer().also {
                filtered = it
                filterDiagnostics.created()
            }
            val output = raster ?: context.createGraphicsLayer().also {
                it.compositingStrategy = CompositingStrategy.Offscreen
                raster = it
                filterDiagnostics.created()
            }
            val recipeDirty = geometryDirty || recipe != lastFilter
            if (recipeDirty) {
                effects.apply {
                    if (recipe.vibrant) vibrancy()
                    blur(recipe.radiusPx)
                }
                input.renderEffect = recipe.effect ?: effects.renderEffect
            }
            if (sourceDirty || recipeDirty || backdrop.published !== output) {
                input.record(dimensions) { drawLayer(source.graphicsLayer) }
                output.record(dimensions) { drawLayer(input) }
                filterDiagnostics.recorded(dimensions)
            }
            backdrop.published = output
        }
        if (sourceDirty || recipe != lastFilter || geometryDirty) {
            Snapshot.withoutReadObservation { backdrop.contentRevision++ }
        }
        lastFilter = recipe
        // This replaces the old visible traversal followed by a second producer traversal.
        drawLayer(source.graphicsLayer)
    }

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        this.coordinates = coordinates
        backdrop.source.layerCoordinates = coordinates
    }

    private fun releaseFilter() {
        val context = requireGraphicsContext()
        filtered?.let { context.releaseGraphicsLayer(it); filterDiagnostics.released() }
        raster?.let { context.releaseGraphicsLayer(it); filterDiagnostics.released() }
        filtered = null
        raster = null
    }

    fun detachSource() {
        backdrop.published = null
        backdrop.source.layerCoordinates = null
        releaseFilter()
        cache.clear()
        lastFilter = null
        effects.reset()
        Snapshot.withoutReadObservation { backdrop.contentRevision++ }
    }

    override fun onDetach() {
        detachSource()
        coordinates = null
    }
}
