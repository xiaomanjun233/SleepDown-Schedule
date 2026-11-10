package com.xiaomanjun.sleepdownschedule.feature.home

import com.xiaomanjun.sleepdownschedule.transition.LiveTransitionSourceView

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.xiaomanjun.sleepdownschedule.core.ui.material.DockImportSource
import com.xiaomanjun.sleepdownschedule.platform.coloros.findActivityOrNull
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.catalog.utils.InteractiveHighlight
import com.xiaomanjun.sleepdownschedule.ScheduleConfigEntity
import com.xiaomanjun.sleepdownschedule.core.ui.chrome.homeChromeBlur
import com.xiaomanjun.sleepdownschedule.core.ui.chrome.homeChromeGlassSurfaceAlpha
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.SleepDownFloatingAddButton
import com.xiaomanjun.sleepdownschedule.core.ui.interaction.dockImeCompensationPx
import com.xiaomanjun.sleepdownschedule.core.ui.interaction.excludeParentPullGesture
import com.xiaomanjun.sleepdownschedule.core.ui.material.LocalHomeMaterialControls
import com.xiaomanjun.sleepdownschedule.glass.ui.LocalAdaptiveGlass
import com.xiaomanjun.sleepdownschedule.core.ui.theme.appUsesDarkTheme
import com.xiaomanjun.sleepdownschedule.glass.withRecordingDensity
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

internal val HomeImportButtonColor = Color(0xFF007AFF)
internal fun homeImportButtonGlassColor(light: Boolean): Color =
    HomeImportButtonColor.copy(alpha = homeChromeGlassSurfaceAlpha(light))

@Composable
internal fun homeDockImportButtonGlassColor(light: Boolean): Color {
    val nativeStyle = com.xiaomanjun.sleepdownschedule.core.ui.material.LocalHomeMaterialStyle.current != null
    val accent = com.xiaomanjun.sleepdownschedule.core.ui.material.LocalHomeButtonAccent.current
        ?.let { Color(it) }
        ?: HomeImportButtonColor
    // Native FAB's custom theme layer is opaque. Match it before blending toward the menu tint.
    return accent.copy(alpha = if (nativeStyle)
        1f else homeChromeGlassSurfaceAlpha(light))
}

/** Owns the Dock split and supplies the live plus source to the page transition. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ParallelHomeDock(
    selectedIndex: Int, config: ScheduleConfigEntity, backdrop: Backdrop,
    onSelect: (Int) -> Unit, onOpenMenu: (DockImportSource) -> Unit,
    buttonHidden: Boolean = false,
    modifier: Modifier = Modifier
) {
    val homeSelected = selectedIndex < 2
    val adaptiveGlass = LocalAdaptiveGlass.current
    val light = if (homeSelected) adaptiveGlass.lightGlass else !appUsesDarkTheme(config)
    if (LocalHomeMaterialControls.current.ParallelDock(
            selectedIndex = selectedIndex, dark = !light, buttonHidden = buttonHidden,
            modifier = modifier, onSelect = onSelect, onOpenMenu = onOpenMenu
        )) return
    val ink by animateColorAsState(
        if (homeSelected) adaptiveGlass.contentColor else if (light) Color.Black else Color.White,
        tween(220), label = "ParallelDockInk")
    val plusSurface by animateColorAsState(
        homeDockImportButtonGlassColor(light), tween(220), label = "ParallelImportSurface")
    val split = remember { Animatable(if (homeSelected) 1f else 0f) }
    val splitReady by remember { derivedStateOf { !split.isRunning && split.value >= 0.999f } }
    LaunchedEffect(homeSelected) {
        val target = if (homeSelected) 1f else 0f
        if (split.value == target) return@LaunchedEffect
        split.animateTo(target, tween(460, easing = HomeDropletEasing))
    }
    var buttonBounds by remember { mutableStateOf(Rect.Zero) }
    val returnButtonLayer = rememberGraphicsLayer()
    val interactionScope = rememberCoroutineScope()
    val plusInteraction = remember(interactionScope) { InteractiveHighlight(interactionScope) }
    val density = LocalDensity.current
    val recordingDensity = density
    val direction = androidx.compose.ui.platform.LocalLayoutDirection.current
    val activity = androidx.compose.ui.platform.LocalContext.current.findActivityOrNull()
    val plusView = remember(activity, returnButtonLayer) {
        activity?.let { LiveTransitionSourceView(it, returnButtonLayer, density, direction) }
    }
    val systemBottom = WindowInsets.navigationBarsIgnoringVisibility.getBottom(density)
    val bottomInset = with(density) { systemBottom.toDp() }
    val imeCompensation = dockImeCompensationPx(WindowInsets.ime.getBottom(density), systemBottom)
    BoxWithConstraints(modifier.fillMaxSize()) {
        // COUI's expansion view uses a 12dp edge-to-edge gap. Keep the same resting
        // spacing in liquid glass, including when a narrow window reduces the dock.
        val groupWidth = minOf(276.dp, maxWidth - 36.dp)
        val dockWidth = minOf(210.dp, groupWidth - 54.dp - 12.dp)
        val dockTop = 24.dp
        val dockBounds = remember(density, groupWidth, dockWidth) {
            derivedStateOf { with(density) {
                val s = split.value.coerceIn(0f, 1f)
                val dockX = ((groupWidth - dockWidth) / 2).toPx() * (1f - s)
                Rect(dockX, dockTop.toPx(), dockX + dockWidth.toPx(), dockTop.toPx() + 54.dp.toPx())
            } }
        }
        val buttonInteractive = homeSelected && splitReady && !buttonHidden
        val buttonOffset: Density.() -> IntOffset = {
            val s = split.value.coerceIn(0f, 1f)
            val body = dockBounds.value
            IntOffset(
                lerp(body.right - 54.dp.toPx(), (groupWidth - 54.dp).toPx(), s).roundToInt(),
                (body.top - 3.dp.toPx() * sin(PI * s).toFloat()).roundToInt()
            )
        }
        Box(Modifier.align(Alignment.BottomCenter).padding(bottom = bottomInset + 8.dp)
            .width(groupWidth).height(dockTop + 54.dp).graphicsLayer { translationY = imeCompensation.toFloat() }) {
            // Keep the original LiquidButton mounted throughout the split. Its glass, shadow,
            // refraction and press response use the same renderer at rest and while travelling.
            // Clip its returning part behind the Dock even though the Dock itself is translucent.
            Box(Modifier.matchParentSize().drawWithContent {
                val body = dockBounds.value
                val mask = Path().apply { addRoundRect(RoundRect(body, CornerRadius(body.height / 2f))) }
                clipPath(mask, ClipOp.Difference) { this@drawWithContent.drawContent() }
            }) {
                if (plusView != null) AndroidView(
                    factory = { plusView },
                    modifier = Modifier.offset(buttonOffset).size(54.dp),
                    update = { it.updatePresentation(density, direction, render = !buttonHidden) },
                )
                SleepDownFloatingAddButton(onClick = {
                    if (buttonBounds.width > 1f && buttonBounds.height > 1f) {
                        onOpenMenu(DockImportSource(
                            buttonBounds, returnButtonLayer, plusView
                        ))
                    }
                }, backdrop = backdrop, contentDescription = "导入课表", enabled = buttonInteractive,
                    modifier = Modifier.offset(buttonOffset).size(54.dp)
                        .onGloballyPositioned { buttonBounds = it.boundsInRoot() }
                        .drawWithContent {
                            // The attached View and the Legacy return use this same button layer.
                            returnButtonLayer.record {
                                // Pin LocalDensity: record's delegated density otherwise recurses.
                                withRecordingDensity(recordingDensity) {
                                    this@drawWithContent.drawContent()
                                }
                            }
                            if (!buttonHidden && (plusView == null || !plusView.isAttachedToWindow || plusView.width <= 0)) {
                                drawLayer(returnButtonLayer)
                            }
                        }
                        .then(if (buttonInteractive) plusInteraction.gestureModifier else Modifier)
                        .excludeParentPullGesture()
                        .then(if (buttonInteractive) Modifier.semantics { testTag = "parallel_import_button" }
                            else Modifier.clearAndSetSemantics {}),
                    surfaceColor = plusSurface, sharedInteractiveHighlight = plusInteraction,
                    blurRadius = homeChromeBlur(1.3.dp, config))
            }
            CompositionLocalProvider(LocalContentColor provides ink) {
                HomeDockTabs(selectedIndex, true, config, backdrop, light,
                    modifier = Modifier.offset {
                        IntOffset((((groupWidth - dockWidth) / 2).toPx() * (1f - split.value)).roundToInt(), dockTop.roundToPx())
                    }.width(dockWidth).excludeParentPullGesture().semantics { testTag = "parallel_home_dock" },
                    onSelect = onSelect)
            }
        }
    }
}
