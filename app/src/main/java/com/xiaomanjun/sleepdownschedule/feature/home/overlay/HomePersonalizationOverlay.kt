package com.xiaomanjun.sleepdownschedule.feature.home.overlay

import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.catalog.components.LiquidPanel
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.shapes.RoundedRectangle
import com.xiaomanjun.sleepdownschedule.ScheduleConfigEntity
import com.xiaomanjun.sleepdownschedule.core.ui.chrome.HomeLightGlassPanelTintAlpha
import com.xiaomanjun.sleepdownschedule.core.ui.chrome.HomeLightGlassSurfaceColor
import com.xiaomanjun.sleepdownschedule.feature.course.editor.courseEditorOpeningTaper
import com.xiaomanjun.sleepdownschedule.feature.course.editor.courseEditorTaperTransform
import com.xiaomanjun.sleepdownschedule.feature.home.HomeAdaptiveMetrics
import com.xiaomanjun.sleepdownschedule.feature.home.HomeProgressiveBackdropBlurStepCount
import com.xiaomanjun.sleepdownschedule.feature.home.quantizeHomeProgressiveBackdropBlurProgress
import com.xiaomanjun.sleepdownschedule.glass.GlassBackdropDomain
import com.xiaomanjun.sleepdownschedule.glass.GlassMaterialRole
import com.xiaomanjun.sleepdownschedule.glass.GlassMaterialSpec
import com.xiaomanjun.sleepdownschedule.glass.GlassMorphAllocation
import com.xiaomanjun.sleepdownschedule.glass.GlassMotionExperiments
import com.xiaomanjun.sleepdownschedule.glass.GlassRendererKind
import com.xiaomanjun.sleepdownschedule.glass.GlassSceneKeys
import com.xiaomanjun.sleepdownschedule.glass.GlassTransitionEnvelope
import com.xiaomanjun.sleepdownschedule.glass.GlassTransitionGeometry
import com.xiaomanjun.sleepdownschedule.glass.GlassTransitionLayer
import com.xiaomanjun.sleepdownschedule.glass.LocalGlassSceneState
import com.xiaomanjun.sleepdownschedule.glass.glassMorphHost
import com.xiaomanjun.sleepdownschedule.glass.insetRoundedRectLens
import com.xiaomanjun.sleepdownschedule.glass.insetShapeFor
import com.xiaomanjun.sleepdownschedule.glass.isAllocationEfficientFor
import com.xiaomanjun.sleepdownschedule.glass.pixelAligned
import com.xiaomanjun.sleepdownschedule.glass.rememberGlassSurfaceDescriptor
import com.xiaomanjun.sleepdownschedule.glass.sampleGlassTransitionEnvelope
import com.xiaomanjun.sleepdownschedule.glass.sleepDownPlainGlassSurface
import com.xiaomanjun.sleepdownschedule.glass.ui.GlassDialogSurface
import com.xiaomanjun.sleepdownschedule.glass.ui.glassUsesLightStyle
import com.xiaomanjun.sleepdownschedule.transition.legacy.legacyPersonalizationMorphSpec
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.first

internal class DeferredHomeMorphShape(
    private val geometry: State<HomeAnchoredMorphGeometry>,
    private val continuous: Boolean,
    private val density: Density,
    private val taper: State<Float>? = null,
    topStart: CornerSize = CornerSize(0f),
    topEnd: CornerSize = topStart,
    bottomEnd: CornerSize = topStart,
    bottomStart: CornerSize = topStart
) : CornerBasedShape(topStart, topEnd, bottomEnd, bottomStart) {
    override fun createOutline(
        size: Size,
        topStart: Float,
        topEnd: Float,
        bottomEnd: Float,
        bottomStart: Float,
        layoutDirection: LayoutDirection
    ): Outline {
        val corner = (geometry.value.cornerRadiusPx / density.density.coerceAtLeast(0.001f)).dp
        val shape = RoundedRectangle(corner)
        val base = shape.createOutline(size, layoutDirection, density)
        val amount = taper?.value ?: 0f
        if (amount == 0f) return base
        val path = androidx.compose.ui.graphics.Path().apply { addOutline(base) }
        path.asAndroidPath().transform(android.graphics.Matrix().apply {
            setValues(courseEditorTaperTransform(size.width, size.height, amount))
        })
        return Outline.Generic(path)
    }

    override fun copy(
        topStart: CornerSize,
        topEnd: CornerSize,
        bottomEnd: CornerSize,
        bottomStart: CornerSize
    ): CornerBasedShape = DeferredHomeMorphShape(
        geometry = geometry,
        continuous = continuous,
        density = density,
        taper = taper,
        topStart = topStart,
        topEnd = topEnd,
        bottomEnd = bottomEnd,
        bottomStart = bottomStart
    )
}

@Composable
internal fun BoxScope.HomePersonalizationAnimatedOverlay(
    sourceBounds: Rect,
    sourcePressedScale: Float,
    targetRect: Rect,
    rootSize: IntSize,
    motionState: HomeAnchoredMorphState,
    backdrop: Backdrop?,
    config: ScheduleConfigEntity,
    adaptiveMetrics: HomeAdaptiveMetrics,
    previewProgress: Float,
    contentMounted: Boolean,
    onContentLaidOut: () -> Unit,
    onDismissRequest: () -> Unit,
    sourceContent: @Composable (Modifier) -> Unit,
    content: @Composable (Modifier) -> Unit
) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val latestPreviewProgress = rememberUpdatedState(previewProgress)
    val morphSpec = remember(
        sourceBounds,
        sourcePressedScale,
        targetRect,
        adaptiveMetrics,
        density.density
    ) {
        legacyPersonalizationMorphSpec(
            source = sourceBounds,
            target = targetRect,
            pinchDiameterPx = with(density) { 18.dp.toPx() },
            minimumDropPx = with(density) { 36.dp.toPx() },
            maximumDropPx = with(density) { 72.dp.toPx() },
            maximumArcPx = with(density) { adaptiveMetrics.animationArc.toPx() },
            targetCornerRadiusPx = with(density) { 28.dp.toPx() },
            sourcePressedScale = sourcePressedScale,
            verticalReboundAmplitudePx = with(density) {
                ThreeDotMenuMotion.OpenReboundAmplitudeDp.dp.toPx()
            },
            closingSinkOffsetPx = with(density) {
                ThreeDotMenuMotion.CloseSinkOffsetDp.dp.toPx()
            },
            closingControlDropPx = with(density) {
                ThreeDotMenuMotion.CloseControlDropDp.dp.toPx()
            }
        )
    }
    val geometry = remember(morphSpec, motionState) {
        derivedStateOf {
            // Personalization and the three-dot menu are one liquid object choreography. The
            // performance work below only changes recording and backdrop sampling.
            morphSpec.homeGeometry(
                source = sourceBounds,
                target = targetRect,
                rawProgress = motionState.progress.value,
                closing = motionState.phase == HomeAnchoredOverlayPhase.Closing
            )
        }
    }
    val glassSceneState = LocalGlassSceneState.current
    val progressiveEnvelopeDescriptor = rememberGlassSurfaceDescriptor(
        debugLabel = "StableProgressivePersonalizeSurface",
        domain = GlassBackdropDomain.ChromeCombined,
        materialRole = GlassMaterialRole.MorphShell,
        requestedRenderer = GlassRendererKind.StableEnvelopeExperimental,
        sceneKey = GlassSceneKeys.HomePersonalizationProgressiveSurface
    )
    val auraEnvelopeDescriptor = rememberGlassSurfaceDescriptor(
        debugLabel = "StablePersonalizeBackdropAura",
        domain = GlassBackdropDomain.ChromeCombined,
        materialRole = GlassMaterialRole.MorphShell,
        requestedRenderer = GlassRendererKind.StableEnvelopeExperimental,
        sceneKey = GlassSceneKeys.HomePersonalizationBackdropAura
    )
    val stableInsetEligible = backdrop != null &&
        adaptiveMetrics.isLargeScreen &&
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    val showAura = false
    val showStableProgressiveBackdrop = false
    val progressiveEnvelopeRequested = showStableProgressiveBackdrop && stableInsetEligible &&
        glassSceneState?.rendererFor(progressiveEnvelopeDescriptor) ==
        GlassRendererKind.StableEnvelopeExperimental
    val auraEnvelopeRequested = showAura && stableInsetEligible &&
        glassSceneState?.rendererFor(auraEnvelopeDescriptor) ==
        GlassRendererKind.StableEnvelopeExperimental
    val auraLeftFeatherMaximumPx = with(density) { 104.dp.toPx() }
    val auraRightFeatherPx = with(density) { 56.dp.toPx() }
    val auraVerticalFeatherPx = with(density) { 56.dp.toPx() }
    val stableProgressiveGeometry = remember(geometry) {
        { geometry.value.asGlassTransitionGeometry() }
    }
    val stableAuraGeometry = remember(
        geometry,
        auraLeftFeatherMaximumPx,
        auraRightFeatherPx,
        auraVerticalFeatherPx
    ) {
        {
            homePersonalizationAuraGeometry(
                geometry = geometry.value,
                leftFeatherMaximumPx = auraLeftFeatherMaximumPx,
                rightFeatherPx = auraRightFeatherPx,
                verticalFeatherPx = auraVerticalFeatherPx
            )
        }
    }
    val stableProgressiveEnvelope = remember(
        progressiveEnvelopeRequested,
        morphSpec,
        sourceBounds,
        targetRect
    ) {
        if (!progressiveEnvelopeRequested && !GlassMotionExperiments.fixedMorph) {
            null
        } else {
            sampleGlassTransitionEnvelope(
                tracks = listOf(
                    { progress ->
                        morphSpec.homeGeometry(sourceBounds, targetRect, progress, false)
                            .asGlassTransitionGeometry()
                    },
                    { progress ->
                        morphSpec.homeGeometry(sourceBounds, targetRect, progress, true)
                            .asGlassTransitionGeometry()
                    }
                ),
                steps = 256,
                effectPaddingPx = 2f
            )
        }
    }
    val stableAuraEnvelope = remember(
        auraEnvelopeRequested,
        morphSpec,
        sourceBounds,
        targetRect,
        auraLeftFeatherMaximumPx,
        auraRightFeatherPx,
        auraVerticalFeatherPx
    ) {
        if (!auraEnvelopeRequested) {
            null
        } else {
            sampleGlassTransitionEnvelope(
                tracks = listOf(
                    { progress ->
                        homePersonalizationAuraGeometry(
                            morphSpec.homeGeometry(sourceBounds, targetRect, progress, false),
                            auraLeftFeatherMaximumPx,
                            auraRightFeatherPx,
                            auraVerticalFeatherPx
                        )
                    },
                    { progress ->
                        homePersonalizationAuraGeometry(
                            morphSpec.homeGeometry(sourceBounds, targetRect, progress, true),
                            auraLeftFeatherMaximumPx,
                            auraRightFeatherPx,
                            auraVerticalFeatherPx
                        )
                    }
                ),
                steps = 256,
                effectPaddingPx = 2f
            )
        }
    }
    val finalHomeGeometry = remember(morphSpec, sourceBounds, targetRect) {
        morphSpec.homeGeometry(sourceBounds, targetRect, 1f, false)
            .asGlassTransitionGeometry()
    }
    val finalAuraGeometry = remember(
        finalHomeGeometry,
        auraLeftFeatherMaximumPx,
        auraRightFeatherPx,
        auraVerticalFeatherPx
    ) {
        homePersonalizationAuraGeometry(
            morphSpec.homeGeometry(sourceBounds, targetRect, 1f, false),
            auraLeftFeatherMaximumPx,
            auraRightFeatherPx,
            auraVerticalFeatherPx
        )
    }
    val useStableProgressiveSurface = stableProgressiveEnvelope
        ?.isAllocationEfficientFor(finalHomeGeometry, maximumAreaRatio = 1.65f) == true
    val useStableAura = stableAuraEnvelope
        ?.isAllocationEfficientFor(finalAuraGeometry, maximumAreaRatio = 1.45f) == true
    val blurProgress = remember(geometry, latestPreviewProgress) {
        derivedStateOf {
            personalizeBackdropBlurLayerProgress(
                expansionProgress = geometry.value.expansionProgress,
                previewProgress = latestPreviewProgress.value
            )
        }
    }
    val warmupBackdropEffects = motionState.phase == HomeAnchoredOverlayPhase.Preparing
    val progressiveBackdropBlurProgress by remember(blurProgress, warmupBackdropEffects) {
        derivedStateOf {
            val quantized = quantizeHomeProgressiveBackdropBlurProgress(blurProgress.value)
            if (warmupBackdropEffects) {
                // A nearly invisible first bucket gives Android a complete target-sized effect
                // chain before the first visible Opening frame. It is not a replacement bitmap;
                // Open still samples the live backdrop and hosts the real form.
                max(quantized, 1f / HomeProgressiveBackdropBlurStepCount)
            } else {
                quantized
            }
        }
    }
    val taper = remember(motionState, sourceBounds, targetRect) {
        derivedStateOf {
            if (motionState.phase == HomeAnchoredOverlayPhase.Opening ||
                motionState.phase == HomeAnchoredOverlayPhase.Closing) {
                courseEditorOpeningTaper(motionState.progress.value,
                    sourceBounds.center.y - targetRect.center.y, targetRect.height,
                    closing = motionState.phase == HomeAnchoredOverlayPhase.Closing)
            } else 0f
        }
    }
    val shape = remember(geometry, density, taper) {
        DeferredHomeMorphShape(geometry, continuous = true, density = density, taper = taper)
    }
    val fixedAllocation = remember(stableProgressiveEnvelope, motionState.phase, backdrop) {
        if (!GlassMotionExperiments.fixedMorph || backdrop == null ||
            motionState.phase == HomeAnchoredOverlayPhase.Open || !useStableProgressiveSurface) null
        else GlassMorphAllocation(checkNotNull(stableProgressiveEnvelope),
            { geometry.value.asGlassTransitionGeometry() }, with(density) { 10.dp.toPx() })
    }
    val maxContentBlurPx = with(density) { 5.dp.toPx() }
    val formReveal = remember { Animatable(0f) }
    val showForm = contentMounted && motionState.phase == HomeAnchoredOverlayPhase.Open
    val revealForm = showForm && motionState.phase == HomeAnchoredOverlayPhase.Open
    LaunchedEffect(revealForm) {
        formReveal.animateTo(if (revealForm) 1f else 0f, tween(220))
    }
    val targetWidth = with(density) { targetRect.width.toDp() }
    val targetHeight = with(density) { targetRect.height.toDp() }

    Box(
        Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null,
                onClick = onDismissRequest
            )
    )

    if (showAura && backdrop != null) {
        val auraAlphaProvider = {
            val visibleAlpha = geometry.value.surfaceAlpha * blurProgress.value
            if (warmupBackdropEffects) max(visibleAlpha, 0.001f) else visibleAlpha
        }
        if (useStableAura) {
            StableDeferredPersonalizeBackdropAura(
                backdrop = backdrop,
                descriptor = auraEnvelopeDescriptor,
                envelope = checkNotNull(stableAuraEnvelope),
                geometry = stableAuraGeometry,
                leftFeatherPxProvider = {
                    auraLeftFeatherMaximumPx *
                        geometry.value.expansionProgress.coerceIn(0f, 1f)
                },
                blurProgressProvider = { progressiveBackdropBlurProgress },
                alphaProvider = auraAlphaProvider
            )
        } else {
            DeferredPersonalizeBackdropAura(
                backdrop = backdrop,
                leftFeatherPxProvider = {
                    auraLeftFeatherMaximumPx *
                        geometry.value.expansionProgress.coerceIn(0f, 1f)
                },
                blurProgressProvider = { progressiveBackdropBlurProgress },
                alphaProvider = auraAlphaProvider,
                modifier = Modifier
                    .offset {
                        val leftFeatherPx = auraLeftFeatherMaximumPx *
                            geometry.value.expansionProgress.coerceIn(0f, 1f)
                        IntOffset(
                            (geometry.value.rect.left - leftFeatherPx)
                                .coerceAtLeast(0f).roundToInt(),
                            (geometry.value.rect.top - auraVerticalFeatherPx)
                                .coerceAtLeast(0f).roundToInt()
                        )
                    }
                    .layout { measurable, _ ->
                        val leftFeatherPx = auraLeftFeatherMaximumPx *
                            geometry.value.expansionProgress.coerceIn(0f, 1f)
                        val width = (
                            geometry.value.rect.width + leftFeatherPx + auraRightFeatherPx
                            ).roundToInt().coerceAtLeast(1)
                        val height = (
                            geometry.value.rect.height + auraVerticalFeatherPx * 2f
                            ).roundToInt().coerceAtLeast(1)
                        val placeable = measurable.measure(Constraints.fixed(width, height))
                        layout(width, height) { placeable.place(0, 0) }
                    }
            )
        }
    }

    if (showStableProgressiveBackdrop && backdrop != null) {
        StableDeferredProgressivePersonalizeBackdrop(
            backdrop = backdrop,
            descriptor = progressiveEnvelopeDescriptor,
            envelope = checkNotNull(stableProgressiveEnvelope),
            geometry = stableProgressiveGeometry,
            surfaceAlphaProvider = {
                val visibleAlpha = geometry.value.surfaceAlpha
                if (warmupBackdropEffects) max(visibleAlpha, 0.001f) else visibleAlpha
            },
            blurProgressProvider = { progressiveBackdropBlurProgress },
            warmupBackdropEffects = warmupBackdropEffects
        )
    }

    if (fixedAllocation != null && backdrop != null) {
        Box(Modifier.glassMorphHost(fixedAllocation).graphicsLayer {
            clip = true
            this.shape = fixedAllocation.envelope.insetShapeFor(fixedAllocation.geometry())
            compositingStrategy = CompositingStrategy.Offscreen
        }) {
            LiquidPanel(
                backdrop = backdrop,
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    alpha = geometry.value.surfaceAlpha * (1f - latestPreviewProgress.value.coerceIn(0f, 1f))
                },
                shape = shape,
                surfaceColor = if (glassUsesLightStyle(config)) {
                    HomeLightGlassSurfaceColor.copy(alpha = HomeLightGlassPanelTintAlpha)
                } else Color(0xFF121212).copy(alpha = 0.30f),
                lensHeight = 16.dp,
                lensAmount = 24.dp,
                morphAllocation = fixedAllocation
            ) { }
        }
    }

    Box(
        modifier = Modifier
            .offset {
                IntOffset(
                    geometry.value.rect.left.roundToInt(),
                    geometry.value.rect.top.roundToInt()
                )
            }
            .layout { measurable, _ ->
                val current = geometry.value.rect
                val width = current.width.roundToInt().coerceAtLeast(1)
                val height = current.height.roundToInt().coerceAtLeast(1)
                // The shell and Kyant surface remain in the animated rect's coordinate space.
                // DeferredHomePersonalizeMorphPanel already measures only the heavy form content at
                // target size; moving this whole node into target coordinates offsets the Morph.
                val placeable = measurable.measure(Constraints.fixed(width, height))
                layout(width, height) { placeable.place(0, 0) }
            }
            .graphicsLayer {
                val fullOpenEndpoint = motionState.phase == HomeAnchoredOverlayPhase.Open &&
                    geometry.value.pathProgress >= 0.999f
                clip = !fullOpenEndpoint
                this.shape = shape
                compositingStrategy = if (fullOpenEndpoint || fixedAllocation != null) {
                    CompositingStrategy.Auto
                } else {
                    CompositingStrategy.Offscreen
                }
            }
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null,
                onClick = {}
            ),
        contentAlignment = Alignment.Center
    ) {
        DeferredHomePersonalizeMorphPanel(
            backdrop = backdrop,
            config = config,
            targetWidth = targetWidth,
            targetHeight = targetHeight,
            shape = shape,
            progressiveBlur = false,
            renderProgressiveBackdropPass = false,
            renderMaterial = fixedAllocation == null,
            warmupBackdropEffects = warmupBackdropEffects,
            surfaceAlphaProvider = {
                geometry.value.surfaceAlpha *
                    (1f - latestPreviewProgress.value.coerceIn(0f, 1f))
            },
            contentAlphaProvider = { formReveal.value },
            contentBlurRadiusPxProvider = {
                // Blur is now a crossfade between two pre-recorded GPU layers below. Leaving this
                // outer target-sized layer unblurred avoids recomputing a full form RenderEffect on
                // every animation tick.
                0f
            },
            backdropBlurProgressProvider = { progressiveBackdropBlurProgress },
            onContentLaidOut = onContentLaidOut,
            modifier = Modifier.fillMaxSize(),
            content = { contentModifier ->
                if (showForm) {
                    content(
                        contentModifier
                    )
                }
            }
        )

        Box(
            modifier = Modifier
                .requiredSize(
                    width = with(density) { sourceBounds.width.toDp() },
                    height = with(density) { sourceBounds.height.toDp() }
                )
                .graphicsLayer {
                    val current = geometry.value
                    alpha = current.sourceAlpha
                    scaleX = current.sourceScale
                    scaleY = current.sourceScale
                    compositingStrategy = CompositingStrategy.Offscreen
                    val blurPx = maxContentBlurPx * homeMorphSmoothStep(
                        0f,
                        0.34f,
                        current.pathProgress
                    )
                    renderEffect = if (blurPx > 0.01f) {
                        BlurEffect(blurPx, blurPx, TileMode.Clamp)
                    } else null
                }
                .clearAndSetSemantics { }
        ) {
            sourceContent(Modifier.fillMaxSize())
        }

        if (motionState.phase != HomeAnchoredOverlayPhase.Opening &&
            motionState.phase != HomeAnchoredOverlayPhase.Open
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(motionState.phase) {
                        awaitPointerEventScope {
                            while (true) awaitPointerEvent().changes.forEach { it.consume() }
                        }
                    }
            )
        }
    }
}

@Composable
private fun DeferredHomePersonalizeMorphPanel(
    backdrop: Backdrop?,
    config: ScheduleConfigEntity,
    targetWidth: androidx.compose.ui.unit.Dp,
    targetHeight: androidx.compose.ui.unit.Dp,
    shape: Shape,
    progressiveBlur: Boolean,
    renderProgressiveBackdropPass: Boolean,
    renderMaterial: Boolean = true,
    warmupBackdropEffects: Boolean,
    surfaceAlphaProvider: () -> Float,
    contentAlphaProvider: () -> Float,
    contentBlurRadiusPxProvider: () -> Float,
    backdropBlurProgressProvider: () -> Float,
    onContentLaidOut: () -> Unit,
    modifier: Modifier,
    content: @Composable (Modifier) -> Unit
) {
    val lightGlass = glassUsesLightStyle(config)
    val surfaceColor = if (lightGlass) {
        HomeLightGlassSurfaceColor.copy(alpha = HomeLightGlassPanelTintAlpha)
    } else {
        Color(0xFF121212).copy(alpha = 0.30f)
    }
    val showSurface by remember(surfaceAlphaProvider, backdrop, warmupBackdropEffects) {
        derivedStateOf {
            warmupBackdropEffects || backdrop == null || surfaceAlphaProvider() > 0.005f
        }
    }

    // Keep the live form and its surface inside the animated shell even after the
    // outer Kyant host releases its temporary clip at the stable Open endpoint.
    // The form can continue scrolling in Open; without this shared clip its
    // target-sized content layer exposes square corners beyond the glass shell.
    Box(
        modifier = modifier.graphicsLayer {
            this.shape = shape
            clip = true
        }
    ) {
        if (showSurface && renderMaterial) {
            if (backdrop != null) {
                if (progressiveBlur && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    DeferredProgressivePersonalizeSurface(
                        backdrop = backdrop,
                        shape = shape,
                        surfaceColor = surfaceColor,
                        blurProgressProvider = backdropBlurProgressProvider,
                        renderBackdropPass = renderProgressiveBackdropPass,
                        warmupBackdropEffects = warmupBackdropEffects,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                val visibleAlpha = surfaceAlphaProvider()
                                alpha = if (warmupBackdropEffects) {
                                    max(visibleAlpha, 0.001f)
                                } else {
                                    visibleAlpha
                                }
                            }
                    )
                } else {
                    LiquidPanel(
                        backdrop = backdrop,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { alpha = surfaceAlphaProvider() },
                        shape = shape,
                        surfaceColor = surfaceColor,
                        blurRadius = 22.dp,
                        backdropSampleScale = 0.5f,
                        lensHeight = 16.dp,
                        lensAmount = 24.dp
                    ) { }
                }
            } else {
                GlassDialogSurface(
                    backdrop = null,
                    config = config,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = surfaceAlphaProvider() },
                    shape = shape
                ) { }
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .requiredSize(targetWidth, targetHeight)
                .onSizeChanged {
                    if (it.width > 0 && it.height > 0) onContentLaidOut()
                }
                .graphicsLayer {
                    val contentAlpha = contentAlphaProvider()
                    alpha = contentAlpha
                    // Avoid allocating a target-sized offscreen blur layer while the content is
                    // effectively invisible. The original blur curve resumes at the handoff.
                    val blurPx = if (contentAlpha > 0.01f) {
                        contentBlurRadiusPxProvider()
                    } else {
                        0f
                    }
                    compositingStrategy = if (blurPx > 0.01f) {
                        CompositingStrategy.Offscreen
                    } else {
                        CompositingStrategy.Auto
                    }
                    renderEffect = if (blurPx > 0.01f) {
                        BlurEffect(blurPx, blurPx, TileMode.Clamp)
                    } else null
                }
        ) {
            content(Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun DeferredProgressivePersonalizeSurface(
    backdrop: Backdrop,
    shape: Shape,
    surfaceColor: Color,
    blurProgressProvider: () -> Float,
    renderBackdropPass: Boolean,
    warmupBackdropEffects: Boolean,
    modifier: Modifier = Modifier
) {
    val material = remember(surfaceColor) {
        GlassMaterialSpec(
            role = GlassMaterialRole.MorphShell,
            blur = 7.dp,
            lensHeight = 0.dp,
            lensAmount = 0.dp,
            surfaceAlpha = surfaceColor.alpha,
            borderAlpha = 0.28f,
            highlightAlpha = 0f,
            shadowAlpha = 0f,
            innerShadowAlpha = 0f,
            depthEffect = false,
            useVibrancy = false
        )
    }
    val descriptor = rememberGlassSurfaceDescriptor(
        debugLabel = "DeferredProgressivePersonalizeSurface",
        domain = GlassBackdropDomain.ChromeCombined,
        materialRole = GlassMaterialRole.MorphShell,
        sceneKey = "home-personalization-progressive-surface"
    )
    val showBackdropPass by remember(blurProgressProvider, warmupBackdropEffects) {
        derivedStateOf {
            warmupBackdropEffects || blurProgressProvider().coerceIn(0f, 1f) > 0.005f
        }
    }
    Box(
        modifier = modifier.graphicsLayer {
            this.shape = shape
            clip = true
        }
    ) {
        if (renderBackdropPass && showBackdropPass) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val progress = blurProgressProvider().coerceIn(0f, 1f)
                        // Preparing compiles the real blur path without producing a visible
                        // pre-flash. The normal Opening alpha begins on the next frame.
                        alpha = if (warmupBackdropEffects) 0.001f else progress
                    }
                    .sleepDownPlainGlassSurface(
                        backdrop = backdrop,
                        descriptor = descriptor,
                        material = material,
                        shape = { shape },
                        effects = {
                            blur((7.dp * blurProgressProvider().coerceIn(0f, 1f)).toPx())
                        }
                    )
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.horizontalGradient(
                        0f to surfaceColor.copy(
                            alpha = (surfaceColor.alpha + if (surfaceColor.red > 0.5f) 0.08f else 0.07f)
                                .coerceAtMost(0.42f)
                        ),
                        0.56f to surfaceColor,
                        1f to surfaceColor.copy(
                            alpha = (surfaceColor.alpha * 0.72f).coerceAtLeast(0.07f)
                        )
                    ),
                    shape = shape
                )
                .border(
                    width = 1.dp,
                    brush = Brush.linearGradient(
                        listOf(
                            Color.White.copy(alpha = 0.28f),
                            Color.White.copy(alpha = 0.08f)
                        )
                    ),
                    shape = shape
                )
        )
    }
}

@Composable
private fun StableDeferredProgressivePersonalizeBackdrop(
    backdrop: Backdrop,
    descriptor: com.xiaomanjun.sleepdownschedule.glass.GlassSurfaceDescriptor,
    envelope: GlassTransitionEnvelope,
    geometry: () -> GlassTransitionGeometry,
    surfaceAlphaProvider: () -> Float,
    blurProgressProvider: () -> Float,
    warmupBackdropEffects: Boolean
) {
    val material = remember {
        GlassMaterialSpec(
            role = GlassMaterialRole.MorphShell,
            blur = 7.dp,
            lensHeight = 0.dp,
            lensAmount = 0.dp,
            surfaceAlpha = 0f,
            borderAlpha = 0f,
            highlightAlpha = 0f,
            shadowAlpha = 0f,
            innerShadowAlpha = 0f,
            depthEffect = false,
            useVibrancy = false
        )
    }
    val currentGeometry = rememberUpdatedState(geometry)
    val stableGeometry = remember { { currentGeometry.value.invoke() } }
    GlassTransitionLayer(
        envelope = envelope,
        geometry = stableGeometry,
        temporaryClipActive = false
    ) { _, _ ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    shape = envelope.insetShapeFor(stableGeometry())
                    clip = true
                    compositingStrategy = CompositingStrategy.Offscreen
                    alpha = surfaceAlphaProvider().coerceIn(0f, 1f)
                }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val progress = blurProgressProvider().coerceIn(0f, 1f)
                        alpha = if (warmupBackdropEffects) 0.001f else progress
                    }
                    .sleepDownPlainGlassSurface(
                        backdrop = backdrop,
                        descriptor = descriptor,
                        material = material,
                        shape = { envelope.insetShapeFor(stableGeometry()) },
                        effects = {
                            blur((7.dp * blurProgressProvider().coerceIn(0f, 1f)).toPx())
                        }
                    )
            )
        }
    }
}

@Composable
private fun StableDeferredPersonalizeBackdropAura(
    backdrop: Backdrop,
    descriptor: com.xiaomanjun.sleepdownschedule.glass.GlassSurfaceDescriptor,
    envelope: GlassTransitionEnvelope,
    geometry: () -> GlassTransitionGeometry,
    leftFeatherPxProvider: () -> Float,
    blurProgressProvider: () -> Float,
    alphaProvider: () -> Float
) {
    val material = remember {
        GlassMaterialSpec(
            role = GlassMaterialRole.MorphShell,
            blur = 5.dp,
            lensHeight = 16.dp,
            lensAmount = 24.dp,
            surfaceAlpha = 0f,
            borderAlpha = 0f,
            highlightAlpha = 0f,
            shadowAlpha = 0f,
            innerShadowAlpha = 0f,
            chromaticAberration = false,
            depthEffect = false
        )
    }
    val currentGeometry = rememberUpdatedState(geometry)
    val stableGeometry = remember { { currentGeometry.value.invoke() } }
    GlassTransitionLayer(
        envelope = envelope,
        geometry = stableGeometry,
        temporaryClipActive = false
    ) { stableEnvelope, currentGeometryProvider ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    compositingStrategy = CompositingStrategy.Offscreen
                    alpha = alphaProvider().coerceIn(0f, 1f)
                }
                .drawWithContent {
                    drawContent()
                    val current = currentGeometryProvider().pixelAligned()
                    require(stableEnvelope.contains(current)) {
                        "Personalization aura escaped its stable envelope."
                    }
                    val localRect = stableEnvelope.toLocal(current.rectInRoot)
                    val leftStop = (
                        leftFeatherPxProvider() / localRect.width.coerceAtLeast(1f)
                        ).coerceIn(0f, 0.48f)
                    drawRect(
                        brush = Brush.horizontalGradient(
                            colorStops = arrayOf(
                                0f to Color.Transparent,
                                leftStop to Color.White,
                                1f to Color.White
                            ),
                            startX = localRect.left,
                            endX = localRect.right
                        ),
                        topLeft = Offset(localRect.left, localRect.top),
                        size = Size(localRect.width, localRect.height),
                        blendMode = BlendMode.DstIn
                    )
                }
                .sleepDownPlainGlassSurface(
                    backdrop = backdrop,
                    descriptor = descriptor,
                    material = material,
                    shape = { envelope.insetShapeFor(stableGeometry()) },
                    effects = {
                        val progress = blurProgressProvider().coerceIn(0f, 1f)
                        vibrancy()
                        blur((5.dp * progress).toPx())
                        insetRoundedRectLens(
                            envelope = envelope,
                            geometry = stableGeometry,
                            refractionHeight = 16.dp.toPx(),
                            refractionAmount = 24.dp.toPx(),
                            chromaticAberration = false
                        )
                    }
                )
        )
    }
}

@Composable
private fun DeferredPersonalizeBackdropAura(
    backdrop: Backdrop,
    leftFeatherPxProvider: () -> Float,
    blurProgressProvider: () -> Float,
    alphaProvider: () -> Float,
    modifier: Modifier = Modifier
) {
    val material = remember {
        GlassMaterialSpec(
            role = GlassMaterialRole.MorphShell,
            blur = 5.dp,
            lensHeight = 16.dp,
            lensAmount = 24.dp,
            surfaceAlpha = 0f,
            borderAlpha = 0f,
            highlightAlpha = 0f,
            shadowAlpha = 0f,
            innerShadowAlpha = 0f,
            chromaticAberration = false,
            depthEffect = false
        )
    }
    val descriptor = rememberGlassSurfaceDescriptor(
        debugLabel = "DeferredPersonalizeBackdropAura",
        domain = GlassBackdropDomain.ChromeCombined,
        materialRole = GlassMaterialRole.MorphShell,
        sceneKey = "home-personalization-backdrop-aura"
    )
    Box(
        modifier = modifier
            .graphicsLayer {
                compositingStrategy = CompositingStrategy.Offscreen
                alpha = alphaProvider()
            }
            .drawWithContent {
                drawContent()
                val leftStop = (
                    leftFeatherPxProvider() / size.width.coerceAtLeast(1f)
                    ).coerceIn(0f, 0.48f)
                drawRect(
                    brush = Brush.horizontalGradient(
                        0f to Color.Transparent,
                        leftStop to Color.White,
                        1f to Color.White
                    ),
                    blendMode = BlendMode.DstIn
                )
            }
            .sleepDownPlainGlassSurface(
                backdrop = backdrop,
                descriptor = descriptor,
                material = material,
                shape = { RoundedRectangle(0.dp) },
                effects = {
                    val progress = blurProgressProvider().coerceIn(0f, 1f)
                    vibrancy()
                    blur((5.dp * progress).toPx())
                    lens(16.dp.toPx(), 24.dp.toPx(), chromaticAberration = false)
                }
            )
    )
}
