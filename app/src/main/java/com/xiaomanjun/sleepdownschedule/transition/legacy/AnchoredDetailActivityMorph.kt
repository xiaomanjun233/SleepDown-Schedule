package com.xiaomanjun.sleepdownschedule.transition.legacy

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.Canvas
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import com.xiaomanjun.sleepdownschedule.core.ui.interaction.deviceScreenCornerRadiusPx
import com.xiaomanjun.sleepdownschedule.glass.DeferredGlassRoundedRectangle
import com.xiaomanjun.sleepdownschedule.glass.LocalGlassCoordinatesFrozen
import com.xiaomanjun.sleepdownschedule.transition.AnchoredLegacyProfileId
import com.xiaomanjun.sleepdownschedule.transition.TransitionAnchorFrame
import com.xiaomanjun.sleepdownschedule.transition.transitionSurfaceColor
import kotlin.math.roundToInt
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

internal data class AnchoredMorphSnapshots(
    val background: Bitmap,
    val source: Bitmap? = null,
    val collapse: Bitmap? = null,
)

internal fun detailMorphUsesTransientClip(progress: Float, closing: Boolean): Boolean =
    closing || progress < 1f

internal fun anchoredStableContentOffsetPx(
    positionInRootPx: Float,
    shellStartPx: Float,
    rootExtentPx: Float,
    shellExtentPx: Float,
): Float = positionInRootPx - shellStartPx + (rootExtentPx - shellExtentPx) / 2f

internal enum class AnchoredDetailOpeningMode { AnimateLegacy, HoldSourceFrame, ShowDestination }

private data class LegacyReturn(
    val anchor: TransitionAnchorFrame?,
    val fromPresentedPage: Boolean,
    val floatingAddStart: HomeAnchoredMorphGeometry?,
)

@Composable
internal fun AnchoredDetailActivityMorph(
    openingAnchor: TransitionAnchorFrame?,
    resolvedReturnAnchor: () -> TransitionAnchorFrame?,
    profileId: AnchoredLegacyProfileId?,
    backgroundSnapshot: Bitmap?,
    animateWithoutAnchor: Boolean,
    destinationFirstOpening: Boolean,
    openingMode: AnchoredDetailOpeningMode,
    openingReady: Boolean,
    floatingAddSource: Boolean,
    onLegacyMotionStart: () -> Unit,
    onOpened: () -> Unit,
    onCloseRequested: () -> Boolean,
    onFinished: () -> Unit,
    handleSystemBack: Boolean,
    nativeRoundClip: Boolean,
    sourceContent: @Composable BoxScope.() -> Unit,
    content: @Composable (requestClose: () -> Unit) -> Unit,
) {
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    var windowOffset by remember { mutableStateOf(Offset.Zero) }
    val showDestination = openingMode == AnchoredDetailOpeningMode.ShowDestination
    val hasOpeningMotion = openingAnchor != null || animateWithoutAnchor
    val initial = if (hasOpeningMotion) 0f else 1f
    val extent = remember { Animatable(initial) }
    val vertical = remember { Animatable(initial) }
    val backgroundScale = remember { Animatable(1f) }
    var returnFlight by remember { mutableStateOf<LegacyReturn?>(null) }
    val closing = returnFlight != null
    var legacyReturnStarted by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val latestOpened by rememberUpdatedState(onOpened)
    val latestFinished by rememberUpdatedState(onFinished)
    val activeAnchor = if (closing) returnFlight?.anchor else openingAnchor
    val style = pageMorphStyle(profileId, activeAnchor?.boundsInWindow, activeAnchor?.cornerRadiusPx ?: 0f, floatingAddSource)
    val motion = remember(style) { SharedViewFallbackMotion(style) }
    val targetRadius = deviceScreenCornerRadiusPx()
    val floatingAddStart = returnFlight?.floatingAddStart
    val showPresentedPage = !legacyReturnStarted && (floatingAddStart != null ||
        (returnFlight?.fromPresentedPage ?: showDestination))
    val currentProgress = { if (showPresentedPage) 1f else extent.value }
    val currentVertical = { if (showPresentedPage) 1f else vertical.value }
    val latestProgress = rememberUpdatedState(currentProgress)
    val settled by remember(closing) { derivedStateOf { !detailMorphUsesTransientClip(latestProgress.value(), closing) } }

    fun close() {
        if (returnFlight != null) return
        val fromPresentedPage = showDestination
        if (onCloseRequested()) return
        val target = resolvedReturnAnchor()
        if (!hasOpeningMotion && target == null && !showDestination) {
            latestFinished()
            return
        }
        val returnStyle = pageMorphStyle(profileId, target?.boundsInWindow, target?.cornerRadiusPx ?: 0f, floatingAddSource)
        val returnMotion = SharedViewFallbackMotion(returnStyle)
        val start = if (floatingAddSource && hasOpeningMotion && viewport != IntSize.Zero) {
            val full = Rect(0f, 0f, viewport.width.toFloat(), viewport.height.toFloat())
            val source = openingAnchor?.boundsInWindow?.translate(-windowOffset) ?: unanchoredPageStart(full)
            // Back can arrive before the button finishes expanding. Start the ordinary button
            // return from the visible frame so switching curves cannot jump to a different size.
            val progress = currentProgress()
            sharedViewGeometry(source, full, openingAnchor?.cornerRadiusPx ?: targetRadius, targetRadius,
                progress, progress, PageMorphStyle.FloatingAdd)
        } else null
        returnFlight = LegacyReturn(target, fromPresentedPage, start)
        onLegacyMotionStart()
        scope.launch {
            if (fromPresentedPage || start != null) {
                extent.snapTo(1f)
                vertical.snapTo(1f)
            }
            legacyReturnStarted = true
            coroutineScope {
                if (backgroundSnapshot != null) launch { backgroundScale.animateTo(1f, returnMotion.backgroundClose) }
                launch { extent.animateTo(0f, if (target == null) UnanchoredPageMotion else returnMotion.close) }
                if (!floatingAddSource || start != null) launch {
                    vertical.animateTo(0f, if (target == null) UnanchoredPageMotion else returnMotion.closeVertical)
                }
            }
            latestFinished()
        }
    }

    BackHandler(enabled = handleSystemBack, onBack = ::close)
    LaunchedEffect(openingMode, openingReady, viewport, closing) {
        if (closing || !openingReady || viewport == IntSize.Zero || openingMode == AnchoredDetailOpeningMode.HoldSourceFrame) {
            return@LaunchedEffect
        }
        if (showDestination || !hasOpeningMotion) {
            // A late native rejection restarts at the retained source bounds. There is no
            // progress to commit until the native backend confirms completion.
            if (!showDestination) latestOpened()
            return@LaunchedEffect
        }
        if (extent.value < 1f) {
            onLegacyMotionStart()
            withFrameNanos { }
            coroutineScope {
                if (backgroundSnapshot != null && profileId != AnchoredLegacyProfileId.Welcome) {
                    launch { backgroundScale.animateTo(if (style == PageMorphStyle.Menu) 1.08f else 0.96f, motion.backgroundOpen) }
                }
                launch { extent.animateTo(1f, if (openingAnchor == null) UnanchoredPageMotion else motion.open) }
                if (!floatingAddSource) launch {
                    vertical.animateTo(1f, if (openingAnchor == null) UnanchoredPageMotion else motion.openVertical)
                }
            }
        }
        latestOpened()
    }

    Box(Modifier.fillMaxSize().onGloballyPositioned {
        viewport = it.size
        windowOffset = it.positionInWindow()
    }) {
        if (!settled && backgroundSnapshot != null) {
            MorphSnapshotBackground(backgroundSnapshot, { backgroundScale.value }, nativeRoundClip, Modifier.fillMaxSize())
        }
        if (viewport != IntSize.Zero) AnchoredPageSurface(
            anchor = activeAnchor,
            viewport = viewport,
            windowOffset = windowOffset,
            targetRadius = targetRadius,
            style = style,
            progress = currentProgress,
            vertical = if (floatingAddSource && floatingAddStart == null) currentProgress else currentVertical,
            floatingAddStart = floatingAddStart,
            settled = settled,
            waitForFloatingAdd = floatingAddSource && !closing && !showDestination &&
                (openingMode == AnchoredDetailOpeningMode.HoldSourceFrame || !openingReady || !hasOpeningMotion),
            destinationFirstOpening = destinationFirstOpening && !closing,
            nativeRoundClip = nativeRoundClip,
            sourceContent = sourceContent,
            content = { content(::close) },
        )
        if (!settled) {
            Box(Modifier.fillMaxSize().pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) awaitPointerEvent().changes.forEach { it.consume() }
                }
            })
        }
    }
}

@Composable
private fun AnchoredPageSurface(
    anchor: TransitionAnchorFrame?,
    viewport: IntSize,
    windowOffset: Offset,
    targetRadius: Float,
    style: PageMorphStyle,
    progress: () -> Float,
    vertical: () -> Float,
    floatingAddStart: HomeAnchoredMorphGeometry?,
    settled: Boolean,
    waitForFloatingAdd: Boolean,
    destinationFirstOpening: Boolean,
    nativeRoundClip: Boolean,
    sourceContent: @Composable BoxScope.() -> Unit,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val full = Rect(0f, 0f, viewport.width.toFloat(), viewport.height.toFloat())
    val source = anchor?.boundsInWindow?.translate(-windowOffset) ?: unanchoredPageStart(full)
    val sourceRadius = anchor?.cornerRadiusPx ?: targetRadius
    val currentProgress by rememberUpdatedState(progress)
    val currentVertical by rememberUpdatedState(vertical)
    val geometry by remember(source, full, sourceRadius, targetRadius, style, floatingAddStart) {
        derivedStateOf {
            if (floatingAddStart != null) {
                floatingAddReturnGeometry(source, sourceRadius, floatingAddStart, currentProgress(), currentVertical())
            } else {
                sharedViewGeometry(source, full, sourceRadius, targetRadius, currentProgress(), currentVertical(), style)
            }
        }
    }
    val currentRadius = rememberUpdatedState { geometry.cornerRadiusPx }
    val shape = remember(density.density, nativeRoundClip) {
        if (nativeRoundClip) object : Shape {
            override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density) =
                RoundedCornerShape(with(density) { currentRadius.value().toDp() }).createOutline(size, layoutDirection, density)
        } else DeferredGlassRoundedRectangle({ currentRadius.value() }, density.density)
    }
    val background = MaterialTheme.colorScheme.background
    val defaultAnchorColor = if (style == PageMorphStyle.FloatingAdd) MaterialTheme.colorScheme.primary else background
    val anchorColor = remember(anchor, background, style) {
        anchor?.surfaceColor?.let { Color(it).takeIf { color -> color.alpha >= 0.99f } }
            ?: anchor?.bitmap?.takeIf { style == PageMorphStyle.Button || style == PageMorphStyle.FloatingAdd }
                ?.let { Color(transitionSurfaceColor(it, defaultAnchorColor.toArgb())) }
            ?: defaultAnchorColor
    }
    val currentSettled = rememberUpdatedState(settled)
    val freezeCoordinates = remember { { !currentSettled.value } }
    val showSource by remember(anchor, settled, destinationFirstOpening) {
        derivedStateOf { anchor != null && !settled && !destinationFirstOpening && geometry.sourceAlpha > 0f }
    }
    if (style == PageMorphStyle.FloatingAdd || style == PageMorphStyle.Welcome) {
        FixedLayoutPageSurface(
            anchor, source, full, background, anchorColor,
            style = style,
            geometry = { geometry },
            settled = settled,
            waitForSource = waitForFloatingAdd && !settled,
            freezeCoordinates = freezeCoordinates,
            content = content,
        )
        return
    }
    Box(
        Modifier
            .absoluteOffset { IntOffset(geometry.rect.left.roundToInt(), geometry.rect.top.roundToInt()) }
            .graphicsLayer {
                clip = !settled
                this.shape = shape
                alpha = if (!settled && anchor == null) currentProgress() else 1f
                compositingStrategy = if (settled) CompositingStrategy.Auto else CompositingStrategy.Offscreen
            }
            .layout { measurable, _ ->
                val rect = geometry.rect
                val width = rect.width.roundToInt().coerceAtLeast(1)
                val height = rect.height.roundToInt().coerceAtLeast(1)
                val page = measurable.measure(Constraints.fixed(viewport.width, viewport.height))
                layout(width, height) { page.place((width - viewport.width) / 2, (height - viewport.height) / 2) }
            }
    ) {
        // The opaque shell owns the pixels during both content fades. The page always measures
        // at its final dimensions and scales uniformly inside the shell.
        Box(Modifier.fillMaxSize().drawBehind {
            drawRect(if (settled) background else lerp(anchorColor, background, (geometry.expansionProgress * 2f).coerceIn(0f, 1f)))
        })
        Box(Modifier.fillMaxSize().graphicsLayer {
            val rect = geometry.rect
            val scale = maxOf(rect.width / full.width, rect.height / full.height).coerceAtLeast(0.001f)
            transformOrigin = TransformOrigin(0.5f, 0f)
            scaleX = scale
            scaleY = scale
            translationY = (full.height - rect.height) / 2f
            alpha = if (settled || anchor == null || destinationFirstOpening) 1f else geometry.contentAlpha
        }.then(if (settled) Modifier else Modifier.clearAndSetSemantics {})) {
            CompositionLocalProvider(LocalGlassCoordinatesFrozen provides freezeCoordinates, content = content)
        }
        if (showSource) {
            val sourceShape = remember(sourceRadius, density.density) {
                DeferredGlassRoundedRectangle({ sourceRadius }, density.density)
            }
            Box(
                Modifier.absoluteOffset {
                    val rect = geometry.rect
                    IntOffset(
                        anchoredStableContentOffsetPx(rect.center.x - source.width / 2f, rect.left, full.width, rect.width).roundToInt(),
                        anchoredStableContentOffsetPx(rect.center.y - source.height / 2f, rect.top, full.height, rect.height).roundToInt(),
                    )
                }
                    .requiredSize(with(density) { source.width.toDp() }, with(density) { source.height.toDp() })
                    .graphicsLayer {
                        alpha = geometry.sourceAlpha
                        scaleX = geometry.sourceScale
                        scaleY = geometry.sourceScale
                    }
                    .clip(sourceShape)
                    .clearAndSetSemantics {},
            ) {
                val bitmap = anchor?.bitmap
                if (bitmap != null) Image(bitmap.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
                else sourceContent()
            }
        }
    }
}

/** Animate the moving mask while the page keeps its final constraints. */
@Composable
private fun FixedLayoutPageSurface(
    anchor: TransitionAnchorFrame?,
    source: Rect,
    viewport: Rect,
    background: Color,
    anchorColor: Color,
    style: PageMorphStyle,
    geometry: () -> HomeAnchoredMorphGeometry,
    settled: Boolean,
    waitForSource: Boolean,
    freezeCoordinates: () -> Boolean,
    content: @Composable () -> Unit,
) {
    val clip = remember { Path() }
    val welcome = style == PageMorphStyle.Welcome
    Box(Modifier.fillMaxSize().then(if (settled) Modifier else Modifier.drawWithContent {
        if (waitForSource) return@drawWithContent
        val frame = geometry()
        val radius = minOf(frame.cornerRadiusPx, frame.rect.width / 2f, frame.rect.height / 2f)
        clip.rewind()
        clip.addRoundRect(RoundRect(frame.rect, CornerRadius(radius)))
        clipPath(clip) { this@drawWithContent.drawContent() }
    })) {
        Canvas(Modifier.fillMaxSize()) {
            val color = if (settled) background else {
                val progress = geometry().expansionProgress
                val fill = if (welcome) (progress * 2f).coerceAtMost(1f) else homeMorphSmoothStep(0f, 0.53f, progress)
                lerp(anchorColor, background, fill)
            }
            drawRect(color)
        }
        Box(Modifier.fillMaxSize().graphicsLayer {
            if (settled) {
                alpha = 1f
                scaleX = 1f
                scaleY = 1f
                translationX = 0f
                translationY = 0f
            } else {
                val frame = geometry()
                val rect = frame.rect
                val scale = maxOf(rect.width / viewport.width, rect.height / viewport.height)
                transformOrigin = if (welcome) TransformOrigin(0.5f, 0f) else TransformOrigin.Center
                alpha = frame.contentAlpha
                scaleX = scale
                scaleY = scale
                translationX = rect.center.x - viewport.center.x
                translationY = if (welcome) rect.top else rect.center.y - viewport.center.y
            }
        }.then(if (settled) Modifier else Modifier.clearAndSetSemantics {})) {
            CompositionLocalProvider(LocalGlassCoordinatesFrozen provides freezeCoordinates, content = content)
        }
        if (!settled && anchor != null && (!welcome || anchor.bitmap != null)) {
            val density = LocalDensity.current
            val bitmap = remember(anchor.bitmap) { anchor.bitmap?.asImageBitmap() }
            val sourceShape = remember(anchor.cornerRadiusPx, density) {
                RoundedCornerShape(with(density) { anchor.cornerRadiusPx.toDp() })
            }
            Box(
                Modifier.requiredSize(with(density) { source.width.toDp() }, with(density) { source.height.toDp() })
                    .graphicsLayer {
                        val frame = geometry()
                        translationX = frame.rect.center.x - source.width / 2f
                        translationY = frame.rect.center.y - source.height / 2f
                        alpha = frame.sourceAlpha
                        scaleX = frame.sourceScale
                        scaleY = frame.sourceScale
                        shape = sourceShape
                        // Clip the captured button, whose square corners include the source page.
                        this.clip = bitmap != null
                    }.clearAndSetSemantics {},
                contentAlignment = Alignment.Center,
            ) {
                if (bitmap != null) Image(bitmap, null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
                else Icon(Icons.Rounded.Add, null, Modifier.requiredSize(24.dp), tint = Color.White)
            }
        }
    }
}
