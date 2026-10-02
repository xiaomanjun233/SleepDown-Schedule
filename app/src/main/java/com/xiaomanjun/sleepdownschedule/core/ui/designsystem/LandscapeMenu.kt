package com.xiaomanjun.sleepdownschedule.core.ui.designsystem

import android.view.WindowManager
import android.graphics.Matrix
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.kyant.backdrop.Backdrop
import com.xiaomanjun.sleepdownschedule.glass.rememberGlassCombinedBackdrop
import com.xiaomanjun.sleepdownschedule.glass.ui.rememberScreenScaledBackdrop
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.utils.MiuixPopupBackdropCapture

/** Configuration remains the Activity's window orientation inside a separate Dialog window. */
@Composable
internal fun isLandscapeMenuWindow(): Boolean {
    val configuration = LocalConfiguration.current
    return configuration.screenWidthDp > configuration.screenHeightDp
}

/** A flight host only: each form keeps its original surface, fields and glass material. */
@Composable
internal fun <T : Any> LandscapeMenuOverlay(
    request: T?,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    backdrop: Backdrop? = LocalCenteredDialogSceneBackdrop.current,
    maximumWidth: Dp = 680.dp,
    maximumHeight: Dp = 640.dp,
    contentInsets: PaddingValues = PaddingValues(0.dp),
    onOpenFinished: () -> Unit = {},
    onDismissFinished: () -> Unit = {},
    content: @Composable BoxScope.(T, Backdrop?) -> Unit
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
    val sceneBackdrop = LocalCenteredDialogSceneBackdrop.current ?: backdrop
    DisposableEffect(sceneBackdrop) {
        // A platform Dialog does not register as a Miuix popup. Keep the page producer alive
        // throughout the retained exit so the form always samples a recorded scene.
        if (sceneBackdrop != null) MiuixPopupBackdropCapture.acquireConsumer()
        onDispose {
            if (sceneBackdrop != null) MiuixPopupBackdropCapture.releaseConsumer()
        }
    }
    val transition = rememberTransition(visibility, label = "LandscapeFormFlight")
    val dim by transition.animateFloat(label = "LandscapeFormDim", transitionSpec = { tween(180) }) {
        if (it) 0.28f else 0f
    }
    val deformation = transition.animateFloat(
        label = "LandscapeFormDeformation",
        transitionSpec = {
            if (targetState) spring(dampingRatio = 0.86f, stiffness = 380f)
            else tween(240, easing = CubicBezierEasing(0.4f, 0f, 0.8f, 0.3f))
        }
    ) { if (it) 0f else 1f }
    val screenHeightPx = LocalWindowInfo.current.containerSize.height
    val interaction = remember { MutableInteractionSource() }
    val sourceView = LocalView.current
    val sourceLocation = remember(sourceView) { IntArray(2) }
    val dialogBackdrop = rememberScreenScaledBackdrop(
        backdrop = sceneBackdrop, scale = { 1f },
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
        val density = LocalDensity.current
        val direction = LocalLayoutDirection.current
        val safeInsets = WindowInsets.safeDrawing
        val horizontalInsets = with(density) {
            PaddingValues(
                start = maxOf(contentInsets.calculateStartPadding(direction),
                    (if (direction == androidx.compose.ui.unit.LayoutDirection.Ltr)
                        safeInsets.getLeft(this, direction) else safeInsets.getRight(this, direction)).toDp()),
                end = maxOf(contentInsets.calculateEndPadding(direction),
                    (if (direction == androidx.compose.ui.unit.LayoutDirection.Ltr)
                        safeInsets.getRight(this, direction) else safeInsets.getLeft(this, direction)).toDp()),
                top = contentInsets.calculateTopPadding(),
                bottom = contentInsets.calculateBottomPadding()
            )
        }
        DisposableEffect(view) {
            val window = (view.parent as? DialogWindowProvider)?.window
            window?.setDimAmount(0f)
            window?.setWindowAnimations(0)
            window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
            onDispose { }
        }
        val formScene = rememberCenteredDialogSceneBackdrop("landscape-form-scene")
        val popupBackdrop = if (dialogBackdrop != null) {
            rememberGlassCombinedBackdrop(dialogBackdrop, formScene)
        } else formScene
        Box(modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize()
                .drawBehind { drawRect(Color.Black.copy(alpha = dim)) }
                .clickable(interactionSource = interaction, indication = null) {
                    if (request != null) onDismissRequest()
                })
            BoxWithConstraints(
                Modifier.fillMaxSize()
                    .padding(horizontalInsets)
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical))
                    .imePadding()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                val heightLimit = maxHeight.coerceAtMost(maximumHeight)
                transition.AnimatedVisibility(
                    visible = { it },
                    enter = slideInVertically(spring(dampingRatio = 0.86f, stiffness = 380f)) {
                        (screenHeightPx + it) / 2
                    } + fadeIn(tween(140)),
                    exit = slideOutVertically(tween(240, easing = CubicBezierEasing(0.4f, 0f, 0.8f, 0.3f))) {
                        (screenHeightPx + it) / 2
                    } + fadeOut(tween(180))
                ) {
                    CompositionLocalProvider(
                        LocalCenteredDialogSceneBackdrop provides popupBackdrop,
                        LocalCenteredDialogRenderInRootScaffold provides false
                    ) {
                        Scaffold(
                            modifier = Modifier.widthIn(max = maximumWidth).fillMaxWidth().height(heightLimit)
                                .then(if (visibility.isIdle) Modifier else Modifier.graphicsLayer {
                                    val amount = deformation.value
                                    transformOrigin = TransformOrigin(0.5f, 1f)
                                    scaleX = 1f - 0.045f * amount
                                    scaleY = 1f + 0.065f * amount
                                }.landscapeFormTaper { deformation.value }),
                            underlayModifier = Modifier.fillMaxSize().centeredDialogSceneProducer(formScene),
                            containerColor = Color.Transparent,
                            contentWindowInsets = WindowInsets(0, 0, 0, 0)
                        ) {
                            Box(Modifier.fillMaxSize()) {
                                // A sibling shield catches blank-area taps without consuming a
                                // child's DOWN before a slider has acquired its drag gesture.
                                Box(Modifier.matchParentSize().clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null, onClick = {}
                                ))
                                Box(Modifier.fillMaxSize()) { content(shown, dialogBackdrop) }
                                // Unmount the animation guard completely at rest. It must not be
                                // reused as the blank-area tap handler when the flight settles.
                                if (!visibility.isIdle) {
                                    Box(Modifier.matchParentSize().clearAndSetSemantics {}
                                        .pointerInput(visibility) {
                                            awaitPointerEventScope {
                                                while (true) awaitPointerEvent(PointerEventPass.Initial)
                                                    .changes.forEach { it.consume() }
                                            }
                                        })
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Project the existing form layers; no extra full-size texture is needed for the taper. */
private fun Modifier.landscapeFormTaper(amount: () -> Float): Modifier = drawWithCache {
    val inset = size.width * 0.14f * amount().coerceIn(0f, 1f)
    val matrix = if (inset < 0.01f) null else Matrix().apply {
        setPolyToPoly(
            floatArrayOf(0f, 0f, size.width, 0f, size.width, size.height, 0f, size.height), 0,
            floatArrayOf(0f, 0f, size.width, 0f, size.width - inset, size.height, inset, size.height), 0, 4
        )
    }
    onDrawWithContent {
        if (matrix == null) drawContent() else {
            val canvas = drawContext.canvas.nativeCanvas
            val checkpoint = canvas.save()
            try { canvas.concat(matrix); drawContent() }
            finally { canvas.restoreToCount(checkpoint) }
        }
    }
}
