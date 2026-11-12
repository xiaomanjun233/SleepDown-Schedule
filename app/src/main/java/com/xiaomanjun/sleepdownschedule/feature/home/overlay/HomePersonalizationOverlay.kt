package com.xiaomanjun.sleepdownschedule.feature.home.overlay

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.xiaomanjun.sleepdownschedule.ScheduleConfigEntity
import com.xiaomanjun.sleepdownschedule.core.ui.chrome.HomeLightGlassPanelTintAlpha
import com.xiaomanjun.sleepdownschedule.core.ui.chrome.HomeLightGlassSurfaceColor
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.LocalNativeMaterialSurfaceRenderer
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.NativeMaterialSurfaceRequest
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.NativeMaterialSurfaceRole
import com.xiaomanjun.sleepdownschedule.glass.ui.MorphDialogSurface
import com.xiaomanjun.sleepdownschedule.glass.ui.glassUsesLightStyle
import com.kyant.shapes.RoundedRectangle
import kotlin.math.roundToInt

@Composable
internal fun BoxScope.HomePersonalizationAnimatedOverlay(
    sourceBounds: Rect,
    sourcePressedScale: Float,
    targetRect: Rect,
    motionState: HomeAnchoredMorphState,
    backdrop: Backdrop?,
    config: ScheduleConfigEntity,
    previewProgress: Float,
    onContentLaidOut: () -> Unit,
    onDismissRequest: () -> Unit,
    sourceContent: @Composable (Modifier) -> Unit,
    content: @Composable (Modifier) -> Unit
) {
    val density = LocalDensity.current
    val latestPreview = rememberUpdatedState(previewProgress)
    val targetCorner = with(density) { 28.dp.toPx() }
    val morphSpec = remember(sourceBounds, targetRect, targetCorner, sourcePressedScale) {
        legacyPersonalizationMorphSpec(
            source = sourceBounds,
            target = targetRect,
            targetCornerRadiusPx = targetCorner,
            sourcePressedScale = sourcePressedScale,
            preloadedContent = true,
            earlyContentHandoff = true
        )
    }
    val geometry = remember(morphSpec, motionState) {
        derivedStateOf {
            morphSpec.homeGeometry(sourceBounds, targetRect, motionState.progress.value,
                closing = motionState.phase == HomeAnchoredOverlayPhase.Closing)
        }
    }
    val shape = remember(geometry) {
        object : Shape {
            override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
                val corner = with(density) { geometry.value.cornerRadiusPx.toDp() }
                return RoundedRectangle(corner).createOutline(size, layoutDirection, density)
            }
        }
    }
    val settled = motionState.phase == HomeAnchoredOverlayPhase.Open

    Box(Modifier.fillMaxSize().clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        onClick = onDismissRequest
    ))
    Box(
        modifier = Modifier
            .offset {
                val rect = geometry.value.rect
                IntOffset(rect.left.roundToInt(), rect.top.roundToInt())
            }
            .layout { measurable, _ ->
                val rect = geometry.value.rect
                val width = rect.width.roundToInt().coerceAtLeast(1)
                val height = rect.height.roundToInt().coerceAtLeast(1)
                val child = measurable.measure(Constraints.fixed(width, height))
                layout(width, height) { child.place(0, 0) }
            }
            .clip(shape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {}
            ),
        contentAlignment = Alignment.Center
    ) {
        val surfaceAlpha = remember(geometry, latestPreview) {
            { geometry.value.surfaceAlpha * (1f - latestPreview.value.coerceIn(0f, 1f)) }
        }
        PersonalizationMaterial(Modifier.matchParentSize(), backdrop, config, shape, surfaceAlpha)

        // The form is measured at its resting size. Its scale never enters the backdrop mapping.
        Box(
            Modifier.layout { measurable, constraints ->
                val targetWidth = targetRect.width.roundToInt().coerceAtLeast(1)
                val targetHeight = targetRect.height.roundToInt().coerceAtLeast(1)
                val form = measurable.measure(Constraints.fixed(targetWidth, targetHeight))
                val width = constraints.maxWidth
                val height = constraints.maxHeight
                layout(width, height) {
                    if (settled) {
                        form.place(0, 0)
                    } else {
                        val scale = minOf(width.toFloat() / targetWidth, height.toFloat() / targetHeight, 1f)
                        form.placeWithLayer(0, 0) {
                            transformOrigin = TransformOrigin(0f, 0f)
                            scaleX = scale
                            scaleY = scale
                            translationX = (width - targetWidth * scale) * 0.5f
                            translationY = (height - targetHeight * scale) * 0.5f
                            alpha = geometry.value.contentAlpha
                            compositingStrategy = CompositingStrategy.ModulateAlpha
                        }
                    }
                }
            }
        ) {
            content(Modifier.fillMaxSize().onSizeChanged {
                if (it.width > 0 && it.height > 0) onContentLaidOut()
            }.then(if (settled) Modifier else Modifier.clearAndSetSemantics { }))
        }

        if (!settled) {
            Box(
                Modifier.requiredSize(
                    with(density) { sourceBounds.width.toDp() },
                    with(density) { sourceBounds.height.toDp() }
                ).graphicsLayer {
                    val current = geometry.value
                    alpha = current.sourceAlpha
                    scaleX = current.sourceScale
                    scaleY = current.sourceScale
                }.clearAndSetSemantics { }
            ) {
                sourceContent(Modifier.fillMaxSize())
            }
            Box(Modifier.fillMaxSize().pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) awaitPointerEvent().changes.forEach { it.consume() }
                }
            })
        }
    }
}

@Composable
private fun PersonalizationMaterial(
    modifier: Modifier,
    backdrop: Backdrop?,
    config: ScheduleConfigEntity,
    shape: Shape,
    surfaceAlpha: () -> Float
) {
    val light = glassUsesLightStyle(config)
    val tint = if (light) HomeLightGlassSurfaceColor.copy(alpha = HomeLightGlassPanelTintAlpha)
        else Color(0xFF121212).copy(alpha = 0.30f)
    // The parent supplies the animated clip. Sample in the measured window pixel grid.
    if (LocalNativeMaterialSurfaceRenderer.current?.invoke(
            NativeMaterialSurfaceRequest(
                modifier = modifier,
                shape = RectangleShape,
                dark = !light,
                surfaceAlpha = surfaceAlpha,
                role = NativeMaterialSurfaceRole.Form,
                blurRadius = 22.dp,
                backdropSampleScale = 0.5f,
                onDrawTint = { drawRect(tint) },
                edgeEffects = false,
                followTouch = false,
                backdrop = backdrop
            )
        ) {} == true
    ) return
    MorphDialogSurface(
        surfaceColor = tint,
        shape = shape,
        modifier = modifier,
        backdrop = backdrop,
        surfaceAlpha = surfaceAlpha
    )
}
