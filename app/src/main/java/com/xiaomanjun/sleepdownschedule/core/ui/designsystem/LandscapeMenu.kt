package com.xiaomanjun.sleepdownschedule.core.ui.designsystem

import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.kyant.shapes.RoundedRectangle
import com.kyant.backdrop.Backdrop
import com.xiaomanjun.sleepdownschedule.ScheduleConfigEntity
import com.xiaomanjun.sleepdownschedule.glass.ui.appUsesDarkTheme
import com.xiaomanjun.sleepdownschedule.glass.ui.rememberScreenScaledBackdrop

internal val LocalLandscapeMenuHosted = compositionLocalOf { false }

/** Configuration remains the Activity's window orientation inside a separate Dialog window. */
@Composable
internal fun isLandscapeMenuWindow(): Boolean {
    val configuration = LocalConfiguration.current
    return configuration.screenWidthDp > configuration.screenHeightDp
}

@Composable
internal fun landscapeMenuBackground(config: ScheduleConfigEntity): Color =
    if (appUsesDarkTheme(config)) Color(0xFF1F1F1F) else Color(0xFFF2F3F7)

@Composable
internal fun landscapeMenuGroupBackground(config: ScheduleConfigEntity): Color =
    if (appUsesDarkTheme(config)) Color(0xFF2E2E2E) else Color.White

/** Frosted menu retained through exit; a single placement spring supplies the flight. */
@Composable
internal fun <T : Any> LandscapeMenuOverlay(
    request: T?,
    config: ScheduleConfigEntity,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    backdrop: Backdrop? = LocalCenteredDialogSceneBackdrop.current,
    maxWidth: Dp = 680.dp,
    fillHeight: Boolean = true,
    onOpenFinished: () -> Unit = {},
    onDismissFinished: () -> Unit = {},
    content: @Composable BoxScope.(T) -> Unit
) {
    var retainedRequest by remember { mutableStateOf(request) }
    val visibility = remember { MutableTransitionState(false) }
    val latestOnOpenFinished by rememberUpdatedState(onOpenFinished)
    val latestOnDismissFinished by rememberUpdatedState(onDismissFinished)
    LaunchedEffect(request) {
        if (request != null) retainedRequest = request
        visibility.targetState = request != null
    }
    LaunchedEffect(visibility.isIdle, visibility.currentState, request) {
        if (!visibility.isIdle) return@LaunchedEffect
        if (visibility.currentState) {
            latestOnOpenFinished()
        } else if (request == null && retainedRequest != null) {
            retainedRequest = null
            latestOnDismissFinished()
        }
    }
    val shown = retainedRequest ?: return
    val transition = rememberTransition(visibility, label = "LandscapeMenu")
    val dim by transition.animateFloat(label = "LandscapeMenuDim", transitionSpec = { tween(180) }) {
        if (it) 0.28f else 0f
    }
    val screenHeightPx = LocalWindowInfo.current.containerSize.height
    val interaction = remember { MutableInteractionSource() }
    val ink = if (appUsesDarkTheme(config)) Color.White else Color(0xFF191A1E)
    val sourceView = LocalView.current
    val sourceLocation = remember(sourceView) { IntArray(2) }
    val dialogBackdrop = rememberScreenScaledBackdrop(
        backdrop = LocalCenteredDialogSceneBackdrop.current ?: backdrop, scale = { 1f },
        rootPositionOnScreen = {
            sourceView.getLocationOnScreen(sourceLocation)
            Offset(sourceLocation[0].toFloat(), sourceLocation[1].toFloat())
        },
        rootSize = { IntSize(sourceView.width, sourceView.height) }
    )
    Dialog(
        onDismissRequest = { if (request != null) onDismissRequest() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
            dismissOnClickOutside = false
        )
    ) {
        val view = LocalView.current
        DisposableEffect(view) {
            val window = (view.parent as? DialogWindowProvider)?.window
            window?.setDimAmount(0f)
            window?.setWindowAnimations(0)
            window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
            onDispose { }
        }
        Box(modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize()
                .drawBehind { drawRect(Color.Black.copy(alpha = dim)) }
                .clickable(interactionSource = interaction, indication = null) {
                    if (request != null) onDismissRequest()
                })
            BoxWithConstraints(
                Modifier.fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .imePadding()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                val heightLimit = maxHeight.coerceAtMost(640.dp)
                transition.AnimatedVisibility(
                    visible = { it },
                    enter = slideInVertically(spring(dampingRatio = 0.86f, stiffness = 380f)) {
                        (screenHeightPx + it) / 2
                    } + fadeIn(tween(140)),
                    exit = slideOutVertically(tween(240, easing = CubicBezierEasing(0.4f, 0f, 0.8f, 0.3f))) {
                        (screenHeightPx + it) / 2
                    } + fadeOut(tween(180))
                ) {
                    CompositionLocalProvider(LocalContentColor provides ink, LocalLandscapeMenuHosted provides true) {
                        Box(
                            Modifier.widthIn(max = maxWidth).fillMaxWidth()
                                .then(if (fillHeight) Modifier.height(heightLimit) else Modifier.heightIn(max = heightLimit))
                                .quickSheetBackdropModifier(dialogBackdrop, config, 28.dp, centered = true)
                                .pointerInput(Unit) { detectTapGestures { } }
                                .then(if (visibility.isIdle) Modifier else Modifier.clearAndSetSemantics {}.pointerInput(Unit) {
                                    awaitPointerEventScope {
                                        while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                                    }
                                })
                        ) { content(shown) }
                    }
                }
            }
        }
    }
}

/** A full form window for existing dialogs; their original content remains in place. */
@Composable
internal fun SleepDownFormWindow(
    show: Boolean,
    backdrop: Backdrop?,
    config: ScheduleConfigEntity,
    onDismissRequest: () -> Unit,
    content: @Composable () -> Unit
) {
    if (isLandscapeMenuWindow()) {
        LandscapeMenuOverlay(request = Unit.takeIf { show }, config = config, backdrop = backdrop,
            onDismissRequest = onDismissRequest) { content() }
    } else {
        Dialog(onDismissRequest = onDismissRequest, properties = DialogProperties(usePlatformDefaultWidth = false)) { content() }
    }
}

/** Quick settings keeps its header and fields, moving the host to the window centre. */
@Composable
internal fun SleepDownAdaptiveBottomSheet(
    show: Boolean,
    title: String,
    backdrop: Backdrop?,
    config: ScheduleConfigEntity,
    startAction: @Composable () -> Unit,
    endAction: @Composable () -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    surfaceModifier: Modifier = Modifier,
    backgroundColor: Color = Color.Transparent,
    allowDismiss: Boolean = true,
    onDismissFinished: () -> Unit = {},
    content: @Composable () -> Unit
) {
    if (isLandscapeMenuWindow()) {
        LandscapeMenuOverlay(request = Unit.takeIf { show }, config = config, backdrop = backdrop,
            onDismissRequest = { if (allowDismiss) onDismissRequest() }, onDismissFinished = onDismissFinished,
            fillHeight = false, maxWidth = 600.dp) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
                androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    startAction()
                    androidx.compose.material3.Text(title, Modifier.weight(1f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        style = androidx.compose.material3.MaterialTheme.typography.titleLarge)
                    endAction()
                }
                Column(Modifier.fillMaxWidth().weight(1f, fill = false)
                    .verticalScroll(androidx.compose.foundation.rememberScrollState())) { content() }
            }
        }
    } else top.yukonga.miuix.kmp.overlay.OverlayBottomSheet(
        show = show, title = title, startAction = startAction, endAction = endAction,
        onDismissRequest = onDismissRequest, onDismissFinished = onDismissFinished,
        allowDismiss = allowDismiss, modifier = modifier, surfaceModifier = surfaceModifier,
        backgroundColor = backgroundColor, content = content
    )
}

/** Shared picker/alert host: portrait keeps Miuix's existing renderer. */
@Composable
internal fun SleepDownOverlayDialog(
    show: Boolean,
    config: ScheduleConfigEntity,
    backdrop: Backdrop? = LocalCenteredDialogSceneBackdrop.current,
    modifier: Modifier = Modifier,
    surfaceModifier: Modifier = Modifier,
    backgroundModifier: Modifier = Modifier,
    title: String? = null,
    backgroundColor: Color = Color.Transparent,
    enableWindowDim: Boolean = false,
    onDismissRequest: (() -> Unit)? = null,
    onDismissFinished: (() -> Unit)? = null,
    outsideMargin: DpSize,
    insideMargin: DpSize,
    forceCenter: Boolean = true,
    animationProgressState: MutableFloatState? = null,
    enablePredictiveBackAnimation: Boolean = false,
    excludeFromBackdropCapture: Boolean = true,
    renderInRootScaffold: Boolean = true,
    maxWidth: Dp = 600.dp,
    content: @Composable () -> Unit
) {
    if (isLandscapeMenuWindow()) {
        LandscapeMenuOverlay(
            request = Unit.takeIf { show }, config = config, backdrop = backdrop,
            onDismissRequest = { onDismissRequest?.invoke() },
            onDismissFinished = { onDismissFinished?.invoke() },
            maxWidth = maxWidth, fillHeight = false
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = insideMargin.width, vertical = insideMargin.height)) {
                content()
            }
        }
    } else {
        top.yukonga.miuix.kmp.overlay.OverlayDialog(
            show = show, modifier = modifier, surfaceModifier = surfaceModifier,
            backgroundModifier = backgroundModifier, title = title, backgroundColor = backgroundColor,
            enableWindowDim = enableWindowDim, onDismissRequest = onDismissRequest,
            onDismissFinished = onDismissFinished, outsideMargin = outsideMargin, insideMargin = insideMargin,
            forceCenter = forceCenter, animationProgressState = animationProgressState,
            enablePredictiveBackAnimation = enablePredictiveBackAnimation,
            excludeFromBackdropCapture = excludeFromBackdropCapture, renderInRootScaffold = renderInRootScaffold,
            content = content
        )
    }
}
