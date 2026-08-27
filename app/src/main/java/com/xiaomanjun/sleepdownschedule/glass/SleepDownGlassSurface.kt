package com.xiaomanjun.sleepdownschedule.glass

import com.xiaomanjun.sleepdownschedule.glass.ui.*

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.unit.IntSize
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.BackdropEffectScope
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.drawPlainBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.ceil

private val GlassSurfaceId = AtomicLong(0)
private val DefaultLayerBackdropDraw: ContentDrawScope.() -> Unit = { drawContent() }
private val DefaultGlassBackdropDraw: DrawScope.(DrawScope.() -> Unit) -> Unit = { drawBackdrop ->
    drawBackdrop()
}

private data class GlassEffectStructure(
    val usesOverride: Boolean,
    val hasHighlight: Boolean,
    val hasShadow: Boolean,
    val hasInnerShadow: Boolean,
    val hasLayerBlock: Boolean
)

@Composable
fun rememberGlassSurfaceDescriptor(
    debugLabel: String,
    domain: GlassBackdropDomain,
    materialRole: GlassMaterialRole,
    requestedRenderer: GlassRendererKind = GlassRendererKind.KyantReference,
    sceneKey: String = debugLabel
): GlassSurfaceDescriptor = remember(
    debugLabel,
    domain,
    materialRole,
    requestedRenderer,
    sceneKey
) {
    GlassSurfaceDescriptor(
        id = "$debugLabel#${GlassSurfaceId.incrementAndGet()}",
        domain = domain,
        materialRole = materialRole,
        requestedRenderer = requestedRenderer,
        sceneKey = sceneKey
    )
}

/**
 * Creates one stable provider object and keeps its draw callback identity stable. Dynamic source
 * state is read through rememberUpdatedState, so unrelated recompositions do not recreate the
 * provider or invalidate every consumer.
 */
@Composable
fun rememberGlassLayerBackdrop(
    domain: GlassBackdropDomain,
    providerId: String,
    sceneState: GlassSceneState? = LocalGlassSceneState.current,
    graphicsLayer: GraphicsLayer = rememberGraphicsLayer(),
    onDraw: ContentDrawScope.() -> Unit = DefaultLayerBackdropDraw
): LayerBackdrop {
    val diagnosticSceneState = sceneState?.takeIf { it.diagnosticsEnabled }
    val usesDefaultDraw = onDraw === DefaultLayerBackdropDraw
    val instanceId = remember(providerId) {
        "$providerId#${GlassSurfaceId.incrementAndGet()}"
    }
    val currentOnDraw = rememberUpdatedState(onDraw)
    val stableOnDraw: ContentDrawScope.() -> Unit = remember(
        diagnosticSceneState,
        domain,
        instanceId,
        usesDefaultDraw
    ) {
        if (diagnosticSceneState == null && usesDefaultDraw) {
            DefaultLayerBackdropDraw
        } else if (diagnosticSceneState == null) {
            ({ currentOnDraw.value.invoke(this) })
        } else {
            ({
                diagnosticSceneState.recordProvider(domain, instanceId)
                currentOnDraw.value.invoke(this)
            })
        }
    }
    return rememberLayerBackdrop(graphicsLayer = graphicsLayer, onDraw = stableOnDraw)
}

@Composable
fun rememberGlassCombinedBackdrop(
    first: Backdrop,
    second: Backdrop
): Backdrop = rememberCombinedBackdrop(first, second)

fun Modifier.glassBackdropProducer(backdrop: LayerBackdrop): Modifier = layerBackdrop(backdrop)

/**
 * The single KyantReference consumption path. It preserves the official effect order while
 * keeping callback identities stable; dynamic values are observed inside the existing node.
 */
@Composable
fun Modifier.sleepDownGlassSurface(
    backdrop: Backdrop,
    descriptor: GlassSurfaceDescriptor,
    material: GlassMaterialSpec,
    shape: () -> Shape,
    effectFrame: GlassEffectFrame,
    sceneState: GlassSceneState? = LocalGlassSceneState.current,
    exportedBackdrop: LayerBackdrop? = null,
    effectsOverride: (BackdropEffectScope.() -> Unit)? = null,
    highlightOverride: (() -> Highlight?)? = null,
    shadowOverride: (() -> Shadow?)? = null,
    innerShadowOverride: (() -> InnerShadow?)? = null,
    additionalLayerBlock: (GraphicsLayerScope.() -> Unit)? = null,
    onDrawBehind: (DrawScope.() -> Unit)? = null,
    onDrawBackdrop: (DrawScope.(DrawScope.() -> Unit) -> Unit)? = null,
    onDrawSurface: (DrawScope.() -> Unit)? = null,
    onDrawFront: (DrawScope.() -> Unit)? = null,
    clipToBounds: Boolean = false
): Modifier {
    if (sceneState?.diagnosticsEnabled == true) {
        check(descriptor.materialRole == material.role) {
            "Glass descriptor ${descriptor.id} uses ${descriptor.materialRole}, but material is ${material.role}."
        }
    }

    val currentShape = rememberUpdatedState(shape)
    val currentFrame = rememberUpdatedState(effectFrame)
    val currentEffectsOverride = rememberUpdatedState(effectsOverride)
    val currentHighlightOverride = rememberUpdatedState(highlightOverride)
    val currentShadowOverride = rememberUpdatedState(shadowOverride)
    val currentInnerShadowOverride = rememberUpdatedState(innerShadowOverride)
    val currentAdditionalLayerBlock = rememberUpdatedState(additionalLayerBlock)
    val currentOnDrawBehind = rememberUpdatedState(onDrawBehind)
    val currentOnDrawBackdrop = rememberUpdatedState(onDrawBackdrop)
    val currentOnDrawSurface = rememberUpdatedState(onDrawSurface)
    val currentOnDrawFront = rememberUpdatedState(onDrawFront)
    val currentClipToBounds = rememberUpdatedState(clipToBounds)
    val diagnosticSceneState = sceneState?.takeIf { it.diagnosticsEnabled }

    val stableShape: () -> Shape = remember { { currentShape.value.invoke() } }
    val stableEffects: BackdropEffectScope.() -> Unit = remember(diagnosticSceneState, descriptor) {
        val applyEffects: BackdropEffectScope.() -> Unit = {
            val override = currentEffectsOverride.value
            if (override != null) {
                override.invoke(this)
            } else {
                val frame = currentFrame.value
                if (frame.useVibrancy) vibrancy()
                frame.blur?.let { blur(it.toPx()) }
                val lensHeight = frame.lensHeight
                val lensAmount = frame.lensAmount
                if (lensHeight != null && lensAmount != null) {
                    lens(
                        lensHeight.toPx(),
                        lensAmount.toPx(),
                        depthEffect = frame.depthEffect,
                        chromaticAberration = frame.chromaticAberration
                    )
                }
            }
        }
        if (diagnosticSceneState == null) {
            applyEffects
        } else {
            ({
                diagnosticSceneState.recordEffectChainEvaluation(descriptor)
                applyEffects()
            })
        }
    }

    val hasHighlight = effectFrame.highlight != null || highlightOverride != null
    val stableHighlight: (() -> Highlight?)? = remember(hasHighlight) {
        if (!hasHighlight) {
            null
        } else {
            {
                val override = currentHighlightOverride.value
                if (override != null) {
                    override.invoke()
                } else {
                    val highlight = currentFrame.value.highlight
                    when (highlight?.style) {
                        GlassHighlightStyle.Plain -> Highlight.Plain
                        GlassHighlightStyle.Default -> Highlight.Default.copy(alpha = highlight.alpha)
                        null -> null
                    }
                }
            }
        }
    }
    val hasShadow = effectFrame.shadowAlpha != null || shadowOverride != null
    val stableShadow: (() -> Shadow?)? = remember(hasShadow) {
        if (!hasShadow) {
            null
        } else {
            ({
                currentShadowOverride.value?.invoke()
                    ?: currentFrame.value.shadowAlpha?.let { Shadow(alpha = it) }
            })
        }
    }
    val hasInnerShadow = effectFrame.innerShadow != null || innerShadowOverride != null
    val stableInnerShadow: (() -> InnerShadow?)? = remember(hasInnerShadow) {
        if (!hasInnerShadow) {
            null
        } else {
            {
                val override = currentInnerShadowOverride.value
                if (override != null) {
                    override.invoke()
                } else {
                    currentFrame.value.innerShadow?.let {
                        InnerShadow(radius = it.radius, alpha = it.alpha)
                    }
                }
            }
        }
    }
    val hasLayerBlock = effectFrame.layerScale != null ||
        additionalLayerBlock != null ||
        clipToBounds
    val stableLayerBlock: (GraphicsLayerScope.() -> Unit)? = remember(hasLayerBlock) {
        if (!hasLayerBlock) {
            null
        } else {
            {
                clip = currentClipToBounds.value
                currentFrame.value.layerScale?.let { scale ->
                    scaleX = scale
                    scaleY = scale
                }
                currentAdditionalLayerBlock.value?.invoke(this)
            }
        }
    }
    val stableOnDrawBackdrop: DrawScope.(DrawScope.() -> Unit) -> Unit =
        remember(diagnosticSceneState, descriptor, onDrawBackdrop != null) {
            if (diagnosticSceneState == null && onDrawBackdrop == null) {
                DefaultGlassBackdropDraw
            } else if (diagnosticSceneState == null) {
                ({ drawBackdrop ->
                    currentOnDrawBackdrop.value?.invoke(this, drawBackdrop) ?: drawBackdrop()
                })
            } else {
                ({ drawBackdrop ->
                    diagnosticSceneState.recordConsumerDraw(
                        descriptor = descriptor,
                        size = IntSize(
                            width = ceil(size.width).toInt().coerceAtLeast(0),
                            height = ceil(size.height).toInt().coerceAtLeast(0)
                        )
                    )
                    currentOnDrawBackdrop.value?.invoke(this, drawBackdrop) ?: drawBackdrop()
                })
            }
        }
    val stableOnDrawBehind: (DrawScope.() -> Unit)? = remember(onDrawBehind != null) {
        if (onDrawBehind == null) null else ({ currentOnDrawBehind.value?.invoke(this) })
    }
    val stableOnDrawSurface: (DrawScope.() -> Unit)? = remember(onDrawSurface != null) {
        if (onDrawSurface == null) null else ({ currentOnDrawSurface.value?.invoke(this) })
    }
    val stableOnDrawFront: (DrawScope.() -> Unit)? = remember(onDrawFront != null) {
        if (onDrawFront == null) null else ({ currentOnDrawFront.value?.invoke(this) })
    }
    val effectStructure = GlassEffectStructure(
        usesOverride = effectsOverride != null,
        hasHighlight = hasHighlight,
        hasShadow = hasShadow,
        hasInnerShadow = hasInnerShadow,
        hasLayerBlock = hasLayerBlock
    )
    if (diagnosticSceneState != null) {
        DisposableEffect(
            diagnosticSceneState,
            descriptor,
            backdrop,
            exportedBackdrop,
            effectStructure
        ) {
            diagnosticSceneState.recordEffectChainRebuild(descriptor)
            onDispose { }
        }
    }

    // Experimental renderers are guarded by empty-by-default allowlists. Until a scene-specific
    // implementation is selected, every path deliberately resolves to the reference backend.
    sceneState?.rendererFor(descriptor)
    return drawBackdrop(
        backdrop = backdrop,
        shape = stableShape,
        effects = stableEffects,
        highlight = stableHighlight,
        shadow = stableShadow,
        innerShadow = stableInnerShadow,
        layerBlock = stableLayerBlock,
        exportedBackdrop = exportedBackdrop,
        onDrawBehind = stableOnDrawBehind,
        onDrawBackdrop = stableOnDrawBackdrop,
        onDrawSurface = stableOnDrawSurface,
        onDrawFront = stableOnDrawFront
    )
}

/**
 * Unified path for effects that intentionally use Kyant's plain renderer (for example a
 * progressive blur shader). It keeps the Shape/effect callbacks stable without upgrading the
 * material to the heavier liquid renderer.
 */
@Composable
fun Modifier.sleepDownPlainGlassSurface(
    backdrop: Backdrop,
    descriptor: GlassSurfaceDescriptor,
    material: GlassMaterialSpec,
    shape: () -> Shape,
    sceneState: GlassSceneState? = LocalGlassSceneState.current,
    effects: BackdropEffectScope.() -> Unit
): Modifier {
    if (sceneState?.diagnosticsEnabled == true) {
        check(descriptor.materialRole == material.role) {
            "Glass descriptor ${descriptor.id} uses ${descriptor.materialRole}, but material is ${material.role}."
        }
    }
    val currentShape = rememberUpdatedState(shape)
    val currentEffects = rememberUpdatedState(effects)
    val diagnosticSceneState = sceneState?.takeIf { it.diagnosticsEnabled }
    val stableShape: () -> Shape = remember { { currentShape.value.invoke() } }
    val stableEffects: BackdropEffectScope.() -> Unit = remember(diagnosticSceneState, descriptor) {
        if (diagnosticSceneState == null) {
            ({ currentEffects.value.invoke(this) })
        } else {
            ({
                diagnosticSceneState.recordEffectChainEvaluation(descriptor)
                currentEffects.value.invoke(this)
            })
        }
    }
    if (diagnosticSceneState != null) {
        DisposableEffect(diagnosticSceneState, descriptor, backdrop) {
            diagnosticSceneState.recordEffectChainRebuild(descriptor)
            onDispose { }
        }
    }
    sceneState?.rendererFor(descriptor)
    return drawPlainBackdrop(
        backdrop = backdrop,
        shape = stableShape,
        effects = stableEffects
    )
}
