@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.xiaomanjun.sleepdownschedule.feature.home.overlay

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.motionEventSpy
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.catalog.components.liquidButtonVisualTransform
import com.kyant.backdrop.catalog.utils.InteractiveHighlight
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.shapes.RoundedRectangle
import com.xiaomanjun.sleepdownschedule.*
import com.xiaomanjun.sleepdownschedule.app.ui.*
import com.xiaomanjun.sleepdownschedule.core.ui.chrome.HomeHeaderGlassBlur
import com.xiaomanjun.sleepdownschedule.core.ui.chrome.HomeLightGlassSurfaceColor
import com.xiaomanjun.sleepdownschedule.core.ui.chrome.homeChromeBlur
import com.xiaomanjun.sleepdownschedule.core.ui.chrome.homeChromeGlassSurfaceAlpha
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.AddMenuAction
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.LocalNativeMenuSurfaceRenderer
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.NativeMenuSurfaceRequest
import com.xiaomanjun.sleepdownschedule.core.ui.material.LocalHomeMaterialStyle
import com.xiaomanjun.sleepdownschedule.core.ui.material.homeMaterialNeutralTint
import com.xiaomanjun.sleepdownschedule.feature.home.*
import com.xiaomanjun.sleepdownschedule.feature.home.day.*
import com.xiaomanjun.sleepdownschedule.feature.home.week.*
import com.xiaomanjun.sleepdownschedule.glass.premultipliedGlassTint
import com.xiaomanjun.sleepdownschedule.glass.sleepDownDropletGlass
import com.xiaomanjun.sleepdownschedule.glass.ui.*
import com.xiaomanjun.sleepdownschedule.model.HomeMode
import com.xiaomanjun.sleepdownschedule.transition.legacy.*
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
internal fun HomeAnchoredMorphOverlayHost(
    request: HomeAnchoredOverlayRequest?,
    motionState: HomeAnchoredMorphState,
    backdrop: Backdrop?,
    config: ScheduleConfigEntity,
    addActions: List<AddMenuAction>,
    homeMode: HomeMode,
    onHomeModeChange: (HomeMode) -> Unit,
    adaptiveMetrics: HomeAdaptiveMetrics,
    modifier: Modifier = Modifier,
    showModeSwitch: Boolean = true,
    awaitOpeningGate: suspend () -> Unit = {},
    onDismissRequest: () -> Unit,
    onAddMenuBoundsChanged: (Rect) -> Unit = {},
    onSourceFollowThrough: (Rect) -> Unit = {},
    suppressClose: Boolean = false,
    onSilentDisposed: () -> Unit = {},
    personalizePreviewProgress: Float = 0f,
    sourceContent: @Composable BoxScope.(HomeAnchoredOverlayKind, Modifier) -> Unit,
    personalizeContent: @Composable (Modifier) -> Unit
) {
    var renderedRequest by remember { mutableStateOf<HomeAnchoredOverlayRequest?>(null) }
    var reverseAddOpening by remember { mutableStateOf(false) }
    var panelContentPrepared by remember { mutableStateOf(false) }
    val addMenuSurfacePrepared = remember { AtomicBoolean(false) }
    var rootSize by remember { mutableStateOf(IntSize.Zero) }
    val latestOnDismissRequest by rememberUpdatedState(onDismissRequest)
    val latestOnAddMenuBoundsChanged by rememberUpdatedState(onAddMenuBoundsChanged)
    val latestOnSourceFollowThrough by rememberUpdatedState(onSourceFollowThrough)
    val latestOnSilentDisposed by rememberUpdatedState(onSilentDisposed)
    val latestAwaitOpeningGate by rememberUpdatedState(awaitOpeningGate)

    LaunchedEffect(request, adaptiveMetrics.profile) {
        if (request != null) {
            reverseAddOpening = false
            renderedRequest = request
            addMenuSurfacePrepared.set(false)
            panelContentPrepared = request.kind != HomeAnchoredOverlayKind.Personalize
            motionState.renderedKind = request.kind
            motionState.phase = HomeAnchoredOverlayPhase.Preparing
            motionState.progress.snapTo(0f)
            motionState.backgroundZoom.snapTo(1f)
            var waitedFrames = 0
            while (waitedFrames < 12 && (rootSize.width <= 0 || rootSize.height <= 0)) {
                withFrameNanos { }
                waitedFrames++
            }
            // Personalization opens only its shell; form layout cannot gate this animation.
            latestAwaitOpeningGate()
            if (request.kind == HomeAnchoredOverlayKind.Add) {
                // Prepare the actual retained panel in the existing gate. Do not mount its
                // text/blur/lens tree at the first non-zero alpha in the moving animation.
                var surfaceFrames = 0
                while (!addMenuSurfacePrepared.get() && surfaceFrames < 3) {
                    withFrameNanos { }
                    surfaceFrames++
                }
            }
            motionState.phase = HomeAnchoredOverlayPhase.Opening
            coroutineScope {
                launch {
                    motionState.progress.animateTo(
                        1f,
                        tween(
                            durationMillis = if (
                                request.kind == HomeAnchoredOverlayKind.Personalize
                            ) {
                                HomePersonalizeMorphOpenDurationMillis
                            } else {
                                HomeAddMenuMorphOpenDurationMillis
                            },
                            easing = LinearEasing
                        )
                    )
                }
                if (request.kind == HomeAnchoredOverlayKind.Personalize && !adaptiveMetrics.isLargeScreen) {
                    launch {
                        motionState.backgroundZoom.animateTo(
                            HomeAnchoredMorphBackgroundScale,
                            tween(
                                durationMillis = HomePersonalizeMorphOpenDurationMillis,
                                delayMillis = HomeAnchoredMorphBackgroundDelayMillis,
                                easing = HomeAnchoredBackgroundEasing
                            )
                        )
                    }
                }
            }
            motionState.phase = HomeAnchoredOverlayPhase.Open
            // Present the settled shell before building the personalization controls.
            if (request.kind == HomeAnchoredOverlayKind.Personalize) withFrameNanos { }
            panelContentPrepared = true
        } else if (renderedRequest != null) {
            if (suppressClose) {
                // Silent cleanup: the destination owns the button return. Dispose this hidden
                // first-level menu without playing a second visible Close or follow-through.
                motionState.progress.snapTo(0f)
                motionState.backgroundZoom.snapTo(1f)
                motionState.phase = HomeAnchoredOverlayPhase.Disposing
                panelContentPrepared = false
                renderedRequest = null
                motionState.renderedKind = null
                motionState.phase = HomeAnchoredOverlayPhase.Idle
                latestOnSilentDisposed()
            } else {
                if (renderedRequest?.kind == HomeAnchoredOverlayKind.Personalize) {
                    panelContentPrepared = false
                }
                reverseAddOpening = renderedRequest?.kind == HomeAnchoredOverlayKind.Add &&
                    motionState.phase != HomeAnchoredOverlayPhase.Open
                motionState.phase = HomeAnchoredOverlayPhase.Closing
                if (renderedRequest?.kind == HomeAnchoredOverlayKind.Add && !reverseAddOpening) {
                    // The retained native panel covers these stationary frames while the moving
                    // SDF consumer acquires coordinates and records its first backdrop sample.
                    withFrameNanos { }
                    withFrameNanos { }
                }
                coroutineScope {
                    launch {
                        motionState.progress.animateTo(
                            0f,
                            tween(
                                durationMillis = if (
                                    renderedRequest?.kind == HomeAnchoredOverlayKind.Personalize
                                ) {
                                    HomePersonalizeMorphCloseDurationMillis
                                } else {
                                    if (reverseAddOpening) {
                                        (HomeAddMenuMorphOpenDurationMillis * motionState.progress.value).roundToInt()
                                    } else HomeAddMenuMorphCloseDurationMillis
                                },
                                easing = LinearEasing
                            )
                        )
                    }
                    if (motionState.backgroundZoom.value > 1.0001f) {
                        launch {
                            motionState.backgroundZoom.animateTo(
                                1f,
                                tween(
                                    durationMillis = HomePersonalizeMorphCloseDurationMillis,
                                    easing = HomeAnchoredBackgroundEasing
                                )
                            )
                        }
                    }
                }
                motionState.phase = HomeAnchoredOverlayPhase.Disposing
                panelContentPrepared = false
                renderedRequest = null
                motionState.renderedKind = null
                motionState.phase = HomeAnchoredOverlayPhase.Idle
            }
        } else {
            panelContentPrepared = false
            motionState.progress.snapTo(0f)
            motionState.backgroundZoom.snapTo(1f)
            motionState.renderedKind = null
            motionState.phase = HomeAnchoredOverlayPhase.Idle
        }
    }

    val shownRequest = request ?: renderedRequest
    BackHandler(enabled = shownRequest != null) {
        latestOnDismissRequest()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { rootSize = it }
    ) {
        val shown = shownRequest ?: return@Box
        if (rootSize.width <= 0 || rootSize.height <= 0) return@Box

        val density = androidx.compose.ui.platform.LocalDensity.current
        val actionItemHeight = if (shown.fromDock) 40.dp * density.fontScale.coerceAtLeast(1f)
            else HomeAddMenuActionItemHeightDp.dp
        val targetCorner = HomeAddMenuTargetCornerDp * density.density
        val targetRect = remember(
            shown.kind,
            shown.sourceBoundsInRoot,
            rootSize,
            density.density,
            addActions.size,
            adaptiveMetrics,
            showModeSwitch,
            shown.fromDock,
            actionItemHeight
        ) {
            when (shown.kind) {
                HomeAnchoredOverlayKind.Add -> homeAddMenuTargetRect(
                    source = shown.sourceBoundsInRoot,
                    rootSize = rootSize,
                    density = density.density,
                    actionCount = addActions.size,
                    adaptiveMetrics = adaptiveMetrics,
                    showModeSwitch = showModeSwitch,
                    aboveSource = shown.fromDock,
                    actionItemHeightDp = actionItemHeight.value
                )
                HomeAnchoredOverlayKind.Personalize -> homePersonalizeTargetRect(
                    rootSize = rootSize,
                    density = density.density,
                    adaptiveMetrics = adaptiveMetrics
                )
            }
        }
        LaunchedEffect(shown.kind, targetRect) {
            if (shown.kind == HomeAnchoredOverlayKind.Add) {
                latestOnAddMenuBoundsChanged(targetRect)
            }
        }
        if (shown.kind == HomeAnchoredOverlayKind.Personalize) {
            HomePersonalizationAnimatedOverlay(
                sourceBounds = shown.sourceBoundsInRoot,
                sourcePressedScale = shown.sourcePressedScale,
                targetRect = targetRect,
                rootSize = rootSize,
                motionState = motionState,
                backdrop = backdrop,
                config = config,
                adaptiveMetrics = adaptiveMetrics,
                previewProgress = personalizePreviewProgress,
                contentMounted = panelContentPrepared,
                onContentLaidOut = {},
                onDismissRequest = { latestOnDismissRequest() },
                sourceContent = { sourceModifier ->
                    sourceContent(HomeAnchoredOverlayKind.Personalize, sourceModifier)
                },
                content = personalizeContent
            )
            return@Box
        }
        // Personalize is handled above, so this branch is the Add menu. Keep all animation-tick
        // reads inside derived state and deferred modifier lambdas instead of recomposing the host.
        val dropFrame = remember(shown.sourceBoundsInRoot, targetRect, motionState, density, reverseAddOpening, targetCorner) {
            derivedStateOf {
                homeDropletFrame(
                    source = shown.sourceBoundsInRoot,
                    target = targetRect,
                    progress = motionState.progress.value,
                    closing = motionState.phase == HomeAnchoredOverlayPhase.Closing && !reverseAddOpening,
                    density = density.density, targetCorner = targetCorner
                )
            }
        }
        val geometry = remember(dropFrame) { derivedStateOf {
            val frame = dropFrame.value
            HomeAnchoredMorphGeometry(
                rect = frame.glass.drop, cornerRadiusPx = frame.glass.dropRadius,
                sourceAlpha = frame.sourceAlpha,
                sourceScale = minOf(frame.glass.drop.width / shown.sourceBoundsInRoot.width,
                    frame.glass.drop.height / shown.sourceBoundsInRoot.height).coerceIn(0f, 1.04f),
                surfaceAlpha = 1f, contentAlpha = frame.contentAlpha,
                expansionProgress = motionState.progress.value, pathProgress = motionState.progress.value
            )
        } }
        var sourceHandedOff by remember(shown.kind) { mutableStateOf(false) }
        LaunchedEffect(shown.kind, motionState.phase) {
            if (shown.kind != HomeAnchoredOverlayKind.Add) return@LaunchedEffect
            if (shown.fromDock) return@LaunchedEffect
            if (suppressClose) return@LaunchedEffect
            if (motionState.phase != HomeAnchoredOverlayPhase.Closing) return@LaunchedEffect
            // Hand the collapsing shell over to the follow-through button while the droplet is
            // still at the source anchor, so the real button never pops in at rest.
            snapshotFlow { motionState.progress.value }.first { it <= 0.02f }
            if (motionState.phase != HomeAnchoredOverlayPhase.Closing) return@LaunchedEffect
            sourceHandedOff = true
            latestOnSourceFollowThrough(geometry.value.rect)
        }
        val shape = remember(dropFrame) {
            com.xiaomanjun.sleepdownschedule.glass.GlassDropletContentShape { dropFrame.value.glass }
        }
        val settledSurfaceShape = remember(targetCorner, density) {
            RoundedRectangle(with(density) { targetCorner.toDp() })
        }
        val nativeMenuTouch = remember(shown) {
            com.xiaomanjun.sleepdownschedule.core.ui.designsystem.NativeMenuTouchSource()
        }
        fun forwardMenuLight(event: android.view.MotionEvent) {
            if (motionState.phase == HomeAnchoredOverlayPhase.Opening ||
                motionState.phase == HomeAnchoredOverlayPhase.Open) nativeMenuTouch.dispatch(event)
        }
        val sourcePressedScale = shown.sourcePressedScale.coerceIn(1f, 1.16f)
        val menuInteractionScope = rememberCoroutineScope()
        val menuInteraction = remember(menuInteractionScope) {
            InteractiveHighlight(animationScope = menuInteractionScope)
        }
        val maxContentBlurPx = with(density) { 5.dp.toPx() }
        var outsideDragHighlightedIndex by remember(shown.kind) { mutableIntStateOf(-1) }
        val outsideDragHaptic = LocalHapticFeedback.current
        val menuContentTopPaddingPx = with(density) { HomeAddMenuContentTopPaddingDp.dp.toPx() }
        val menuContentHorizontalPaddingPx = with(density) {
            (HomeAddMenuConcentricInsetDp - HomeAddMenuActionColumnInsetDp).dp.toPx()
        }
        val menuModeHeightPx = with(density) { if (showModeSwitch) HomeAddMenuModeHeightDp.dp.toPx() else 0f }
        val menuActionTopPx = with(density) {
            (if (!showModeSwitch) 0f else (
                HomeAddMenuModeHeightDp + HomeAddMenuSectionGapDp * 2f +
                    HomeAddMenuDividerHeightDp
                )).dp.toPx()
        }
        val menuActionStepPx = with(density) {
            (actionItemHeight + HomeAddMenuActionGapDp.dp).toPx()
        }

        fun targetMenuPosition(rootPosition: Offset): Offset? {
            val rect = geometry.value.rect
            if (!rect.contains(rootPosition) || rect.width <= 1f || rect.height <= 1f) return null
            return Offset(
                x = (rootPosition.x - rect.left) / rect.width * targetRect.width,
                y = (rootPosition.y - rect.top) / rect.height * targetRect.height
            )
        }

        fun outsideDragActionIndex(rootPosition: Offset): Int {
            val targetPosition = targetMenuPosition(rootPosition) ?: return -1
            val innerY = targetPosition.y - menuContentTopPaddingPx
            // The divider/section spacing is visual only. Fold its hit area into the
            // nearest action so sliding through them never drops gesture ownership. In particular,
            // the full strip below the mode switch belongs to the first "添加单节课" action.
            return homeAddMenuHitIndex(
                y = innerY,
                modeHeight = menuModeHeightPx,
                actionTop = menuActionTopPx,
                actionStep = menuActionStepPx,
                actionCount = addActions.size
            )
        }

        val outsideToMenuGesture = Modifier.pointerInput(addActions, homeMode, shown.kind, showModeSwitch) {
            awaitEachGesture {
                val down = awaitFirstDown(
                    requireUnconsumed = false,
                    pass = PointerEventPass.Initial
                )
                val downPosition = down.position
                var lastPosition = downPosition
                var enteredMenu = false
                var completedNormally = false
                try {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        lastPosition = change.position
                        val inside = targetMenuPosition(lastPosition) != null
                        enteredMenu = enteredMenu || inside
                        val nextIndex = outsideDragActionIndex(lastPosition)
                        if (nextIndex != outsideDragHighlightedIndex) {
                            outsideDragHighlightedIndex = nextIndex
                            if (nextIndex in addActions.indices) {
                                outsideDragHaptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                        }
                        if (!change.pressed) {
                            completedNormally = change.changedToUpIgnoreConsumed()
                            break
                        }
                    }
                } finally {
                    val targetPosition = targetMenuPosition(lastPosition)
                    val selectedIndex = outsideDragHighlightedIndex
                    outsideDragHighlightedIndex = -1
                    if (completedNormally && targetPosition != null) {
                        val innerX = targetPosition.x - menuContentHorizontalPaddingPx
                        val innerY = targetPosition.y - menuContentTopPaddingPx
                        if (showModeSwitch && innerY in 0f..menuModeHeightPx) {
                            val innerWidth = targetRect.width - menuContentHorizontalPaddingPx * 2f
                            val targetMode = HomeMode.entries[
                                ((innerX / innerWidth) * HomeMode.entries.size)
                                    .toInt()
                                    .coerceIn(HomeMode.entries.indices)
                            ]
                            if (targetMode != homeMode) {
                                outsideDragHaptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onHomeModeChange(targetMode)
                            }
                        } else if (selectedIndex in addActions.indices) {
                            addActions[selectedIndex].onClick()
                        }
                    } else if (completedNormally && !enteredMenu) {
                        val dx = lastPosition.x - downPosition.x
                        val dy = lastPosition.y - downPosition.y
                        val slop = viewConfiguration.touchSlop
                        if (dx * dx + dy * dy <= slop * slop) latestOnDismissRequest()
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(outsideToMenuGesture)
                .motionEventSpy(::forwardMenuLight)
        )

        val nativeMenuSurface = LocalNativeMenuSurfaceRenderer.current
        val nativeMenuMotion = remember(shown) {
            com.xiaomanjun.sleepdownschedule.core.ui.designsystem.NativeMenuMotion()
        }
        LaunchedEffect(motionState.phase) {
            if (motionState.phase != HomeAnchoredOverlayPhase.Open) {
                val now = android.os.SystemClock.uptimeMillis()
                val cancel = android.view.MotionEvent.obtain(now, now, android.view.MotionEvent.ACTION_CANCEL, 0f, 0f, 0)
                try {
                    nativeMenuMotion.onTouch?.invoke(cancel)
                    if (motionState.phase != HomeAnchoredOverlayPhase.Opening) nativeMenuTouch.dispatch(cancel)
                } finally { cancel.recycle() }
            }
        }
        val movingDroplet = backdrop != null && motionState.phase != HomeAnchoredOverlayPhase.Open
        val preparingCloseSurface by remember(motionState) {
            derivedStateOf { motionState.phase == HomeAnchoredOverlayPhase.Closing && motionState.progress.value >= 0.999f }
        }
        val sourceLightGlass = com.xiaomanjun.sleepdownschedule.glass.ui.LocalAdaptiveGlass.current.lightGlass
        val sourceTint = if (shown.fromDock) com.xiaomanjun.sleepdownschedule.feature.home.homeDockImportButtonGlassColor(sourceLightGlass)
            else (if (sourceLightGlass) HomeLightGlassSurfaceColor else Color(0xFF121212))
                .copy(alpha = homeChromeGlassSurfaceAlpha(sourceLightGlass))
        var nativeMovingSurface = false
        if (movingDroplet || nativeMenuSurface != null) {
            val nativeRenderer = nativeMenuSurface
            nativeMovingSurface = nativeRenderer?.invoke(
                NativeMenuSurfaceRequest(
                com.xiaomanjun.sleepdownschedule.core.ui.designsystem.NativeMaterialSurfaceRequest(
                    modifier = Modifier.offset {
                        val rect = geometry.value.rect
                        IntOffset(rect.left.roundToInt(), rect.top.roundToInt())
                    }.layout { measurable, _ ->
                        val rect = geometry.value.rect
                        val width = rect.width.roundToInt().coerceAtLeast(1)
                        val height = rect.height.roundToInt().coerceAtLeast(1)
                        val child = measurable.measure(Constraints.fixed(width, height))
                        layout(width, height) { child.place(0, 0) }
                    }.graphicsLayer {
                        if (motionState.phase == HomeAnchoredOverlayPhase.Open) nativeMenuMotion.applyTo(this)
                        this.shape = shape
                        clip = true
                        compositingStrategy = CompositingStrategy.ModulateAlpha
                        alpha = when {
                            sourceHandedOff -> 0f
                            motionState.phase == HomeAnchoredOverlayPhase.Preparing -> 0.001f
                            else -> 1f - dropFrame.value.sourceAlpha
                        }
                    }.drawWithContent { drawContent(); addMenuSurfacePrepared.set(true) },
                    shape = settledSurfaceShape,
                    shapeProvider = { RoundedRectangle(with(density) { geometry.value.cornerRadiusPx.toDp() }) },
                    dark = LocalHomeMaterialStyle.current?.dark ?: !glassUsesLightStyle(config),
                    followTouch = true
                ), neutral = LocalHomeMaterialStyle.current?.weatherMaterial == true,
                    touchSource = nativeMenuTouch, motion = nativeMenuMotion)
            ) {} == true
            if (!nativeMovingSurface && movingDroplet) {
            val margin = with(density) { 18.dp.toPx() }
            val source = shown.sourceBoundsInRoot
            val envelope = Rect(min(source.left, targetRect.left) - margin, min(source.top, targetRect.top) - margin,
                max(source.right, targetRect.right) + margin, max(source.bottom, targetRect.bottom) + margin)
            val origin = Offset(-envelope.left, -envelope.top)
            val tint = LocalHomeMaterialStyle.current?.let {
                com.xiaomanjun.sleepdownschedule.core.ui.material.homeMaterialNeutralTint(it.dark)
            } ?: (if (glassUsesLightStyle(config)) HomeLightGlassSurfaceColor else Color(0xFF050505))
                .copy(alpha = if (glassUsesLightStyle(config)) 0.28f else 0.40f)
            Box(Modifier.offset { IntOffset(envelope.left.roundToInt(), envelope.top.roundToInt()) }
                .requiredSize(with(density) { envelope.width.toDp() }, with(density) { envelope.height.toDp() })
                .graphicsLayer {
                    alpha = when {
                        sourceHandedOff -> 0f
                        motionState.phase == HomeAnchoredOverlayPhase.Preparing || preparingCloseSurface -> 0.001f
                        else -> 1f - dropFrame.value.sourceAlpha
                    }
                }
                .sleepDownDropletGlass(requireNotNull(backdrop), {
                    dropFrame.value.glass.let { it.copy(drop = it.drop.translate(origin)) }
                }, tint, 8.dp, debugLabel = "HomeThreeDotDroplet",
                    dropColor = {
                        premultipliedGlassTint(sourceTint, tint, dropFrame.value.menuBlend)
                    }, motionBlurPx = { dropFrame.value.motionBlur },
                    baseBlurRadiusPx = {
                        with(density) {
                            lerp(homeChromeBlur(if (shown.fromDock) 1.3.dp else HomeHeaderGlassBlur, config).toPx(),
                                8.dp.toPx(), dropFrame.value.menuBlend)
                        }
                    },
                    lensHeightPx = { with(density) { lerp((if (shown.fromDock) 10.dp else 12.dp).toPx(),
                        12.dp.toPx(), dropFrame.value.menuBlend) } },
                    lensAmountPx = { with(density) { lerp((if (shown.fromDock) 40.dp else 24.dp).toPx(),
                        24.dp.toPx(), dropFrame.value.menuBlend) } })
                .drawWithContent { drawContent(); addMenuSurfacePrepared.set(true) })
            }
        }

        Box(
            modifier = Modifier
                .offset {
                    val current = geometry.value.rect
                    IntOffset(
                        current.left.roundToInt(),
                        current.top.roundToInt()
                    )
                }
                .graphicsLayer {
                    // The moving shell owns clipping only while geometry is changing. At Open the
                    // static Kyant surface below owns its real 34dp outline, so highlight and shadow
                    // remain free to render outside the fill without exposing a stale dynamic shape.
                    clip = homeAddMenuShellClipEnabled(motionState.phase)
                    this.shape = shape
                    if (nativeMovingSurface && motionState.phase == HomeAnchoredOverlayPhase.Open)
                        nativeMenuMotion.applyTo(this)
                }
                .motionEventSpy { event ->
                    forwardMenuLight(event)
                    if (nativeMovingSurface && motionState.phase == HomeAnchoredOverlayPhase.Open)
                        nativeMenuMotion.onTouch?.invoke(event)
                }
                .then(if (movingDroplet || nativeMovingSurface) Modifier else Modifier
                    .liquidButtonVisualTransform(menuInteraction, pressExpansion = 0.dp, dragExpansion = 20.dp)
                    .then(menuInteraction.gestureModifier))
                .layout { measurable, _ ->
                    // Measure the heavy glass subtree once at its final target size. The animated
                    // shell changes its reported size and clips the child around the shell center.
                    val targetWidth = targetRect.width.roundToInt().coerceAtLeast(1)
                    val targetHeight = targetRect.height.roundToInt().coerceAtLeast(1)
                    val placeable = measurable.measure(Constraints.fixed(targetWidth, targetHeight))
                    val current = geometry.value.rect
                    val width = current.width.roundToInt().coerceAtLeast(1)
                    val height = current.height.roundToInt().coerceAtLeast(1)
                    layout(width, height) {
                        placeable.place((width - targetWidth) / 2, (height - targetHeight) / 2)
                    }
                }
                .clickable(
                    interactionSource = remember {
                        androidx.compose.foundation.interaction.MutableInteractionSource()
                    },
                    indication = null,
                    onClick = {}
                ),
            contentAlignment = Alignment.Center
        ) {
            HomeAddMenuMorphPanel(
                backdrop = backdrop,
                config = config,
                actions = addActions,
                showModeSwitch = showModeSwitch,
                actionItemHeight = actionItemHeight,
                homeMode = homeMode,
                onHomeModeChange = onHomeModeChange,
                targetSizeProvider = {
                    IntSize(targetRect.width.roundToInt(), targetRect.height.roundToInt())
                },
                surfaceAlphaProvider = { geometry.value.surfaceAlpha },
                contentAlphaProvider = { geometry.value.contentAlpha },
                contentBlurRadiusPxProvider = {
                    dropFrame.value.motionBlur
                },
                externalHighlightedIndex = outsideDragHighlightedIndex,
                retainSurface = true,
                // Keep one native surface attached through Open and Close. Replacing it with
                // the retained panel at the endpoint restarted blur and changed its layer.
                externalSurface = nativeMovingSurface || (movingDroplet && !preparingCloseSurface),
                warmupSurface = motionState.phase == HomeAnchoredOverlayPhase.Preparing,
                onSurfaceDrawn = { addMenuSurfacePrepared.set(true) },
                interactive = motionState.phase == HomeAnchoredOverlayPhase.Opening ||
                    motionState.phase == HomeAnchoredOverlayPhase.Open,
                shape = settledSurfaceShape,
                modifier = Modifier.fillMaxSize()
            )

            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .requiredSize(
                        width = with(density) {
                            (shown.sourceBoundsInRoot.width * sourcePressedScale).toDp()
                        },
                        height = with(density) {
                            (shown.sourceBoundsInRoot.height * sourcePressedScale).toDp()
                        }
                    )
                    .graphicsLayer {
                        val current = geometry.value
                        alpha = if (sourceHandedOff) 0f else current.sourceAlpha
                        scaleX = current.sourceScale
                        scaleY = current.sourceScale
                        compositingStrategy = CompositingStrategy.Offscreen
                        val sourceContentBlurPx = if (motionState.phase == HomeAnchoredOverlayPhase.Closing) {
                            maxContentBlurPx * (1f - current.sourceAlpha)
                        } else maxContentBlurPx * homeMorphSmoothStep(0f, 0.34f, current.pathProgress)
                        renderEffect = if (sourceContentBlurPx > 0.01f) {
                            BlurEffect(sourceContentBlurPx, sourceContentBlurPx, TileMode.Clamp)
                        } else null
                    }
                    .clearAndSetSemantics { }
            ) {
                sourceContent(shown.kind, Modifier.fillMaxSize())
            }

            if (motionState.phase != HomeAnchoredOverlayPhase.Opening &&
                motionState.phase != HomeAnchoredOverlayPhase.Open
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(motionState.phase) {
                            awaitPointerEventScope {
                                while (true) {
                                    awaitPointerEvent().changes.forEach { it.consume() }
                                }
                            }
                        }
                )
            }
        }
    }
}
