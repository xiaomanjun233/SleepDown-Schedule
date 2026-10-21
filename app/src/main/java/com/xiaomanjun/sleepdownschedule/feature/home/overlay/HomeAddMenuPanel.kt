package com.xiaomanjun.sleepdownschedule.feature.home.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.catalog.components.LiquidButton
import com.xiaomanjun.sleepdownschedule.R
import com.xiaomanjun.sleepdownschedule.ScheduleConfigEntity
import com.xiaomanjun.sleepdownschedule.core.ui.chrome.HomeLightGlassSurfaceColor
import com.xiaomanjun.sleepdownschedule.core.ui.chrome.homeActionIconPainter
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.AddMenuAction
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.AddMenuLiquidItem
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.LocalNativeMenuRowRenderer
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.LocalNativeMenuSurfaceRenderer
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.NativeMenuItem
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.NativeMenuRowRequest
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.NativeMenuSurfaceRequest
import com.xiaomanjun.sleepdownschedule.core.ui.material.LocalHomeMaterialStyle
import com.xiaomanjun.sleepdownschedule.core.ui.material.homeMaterialNeutralTint
import com.xiaomanjun.sleepdownschedule.glass.ui.appUsesDarkTheme
import com.xiaomanjun.sleepdownschedule.glass.ui.glassForegroundColor
import com.xiaomanjun.sleepdownschedule.glass.ui.glassUsesLightStyle
import com.xiaomanjun.sleepdownschedule.model.HomeMode

@Composable
internal fun HomeAddMenuMorphPanel(
    backdrop: Backdrop?,
    config: ScheduleConfigEntity,
    actions: List<AddMenuAction>,
    homeMode: HomeMode,
    onHomeModeChange: (HomeMode) -> Unit,
    targetSizeProvider: () -> IntSize,
    surfaceAlphaProvider: () -> Float,
    contentAlphaProvider: () -> Float,
    contentBlurRadiusPxProvider: () -> Float = { 0f },
    externalHighlightedIndex: Int = -1,
    retainSurface: Boolean = false,
    warmupSurface: Boolean = false,
    onSurfaceDrawn: () -> Unit = {},
    interactive: Boolean,
    shape: Shape,
    modifier: Modifier,
    showModeSwitch: Boolean = true,
    actionItemHeight: Dp = HomeAddMenuActionItemHeightDp.dp,
    compactActions: Boolean = false,
    shadowEnabled: Boolean = true,
    externalSurface: Boolean = false
) {
    var highlightedIndex by remember { mutableIntStateOf(-1) }
    var nativeListActive by remember { mutableStateOf(false) }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val itemStepPx = with(density) {
        (actionItemHeight + HomeAddMenuActionGapDp.dp).toPx()
    }
    val contentTopPadding = if (compactActions) CourseShortcutContentPaddingDp.dp else HomeAddMenuContentTopPaddingDp.dp
    val contentTopPaddingPx = with(density) { contentTopPadding.toPx() }
    val modeHeightPx = with(density) { if (showModeSwitch) HomeAddMenuModeHeightDp.dp.toPx() else 0f }
    val actionTopPx = with(density) {
        if (!showModeSwitch) 0f else (
            HomeAddMenuModeHeightDp + HomeAddMenuSectionGapDp * 2f +
                HomeAddMenuDividerHeightDp
            ).dp.toPx()
    }
    val homeStyle = LocalHomeMaterialStyle.current.takeIf { !compactActions }
    val menuConfig = remember(config, homeStyle) {
        if (homeStyle != null) config.copy(wallpaperUri = null) else config
    }
    val lightGlass = glassUsesLightStyle(menuConfig)
    val textColor = homeStyle?.foreground?.let { Color(it) } ?: glassForegroundColor(menuConfig)
    val showSurface by remember(retainSurface, surfaceAlphaProvider, backdrop) {
        derivedStateOf { retainSurface || backdrop == null || surfaceAlphaProvider() > 0.005f }
    }

    fun hitIndex(y: Float): Int {
        if (!interactive) return -1
        // Keep one continuous action hit region below the mode switch. The divider is decoration,
        // not a separate pointer target, and row spacing is assigned to the preceding row.
        return homeAddMenuHitIndex(
            // Shortcut input is attached inside the padded content column.
            y = y - if (compactActions) 0f else contentTopPaddingPx,
            modeHeight = modeHeightPx,
            actionTop = actionTopPx,
            actionStep = itemStepPx,
            actionCount = actions.size
        )
    }

    val unifiedMenuGestureModifier = if (interactive && !nativeListActive) {
        Modifier.pointerInput(actions, homeMode, showModeSwitch, actionItemHeight, compactActions) {
            awaitEachGesture {
                val down = awaitFirstDown(
                    requireUnconsumed = false,
                    pass = PointerEventPass.Initial
                )
                highlightedIndex = hitIndex(down.position.y)
                if (!showModeSwitch) down.consume()
                var lastPosition = down.position
                var completedNormally = false
                try {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id }
                            ?: break
                        lastPosition = change.position
                        if (!showModeSwitch) change.consume()
                        if (!change.pressed) {
                            completedNormally = change.changedToUpIgnoreConsumed()
                            break
                        }
                        val nextIndex = if (!showModeSwitch && change.position.x !in 0f..size.width.toFloat()) {
                            -1
                        } else hitIndex(change.position.y)
                        if (nextIndex != highlightedIndex) {
                            highlightedIndex = nextIndex
                            if (nextIndex in actions.indices) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                        }
                    }
                } finally {
                    val index = if (!showModeSwitch) {
                        if (lastPosition.x in 0f..size.width.toFloat()) hitIndex(lastPosition.y) else -1
                    } else highlightedIndex
                    highlightedIndex = -1
                    if (completedNormally) {
                        val innerY = lastPosition.y - contentTopPaddingPx
                        if (showModeSwitch && innerY in 0f..modeHeightPx) {
                            val targetMode = HomeMode.entries[
                                ((lastPosition.x / size.width) * HomeMode.entries.size)
                                    .toInt()
                                    .coerceIn(HomeMode.entries.indices)
                            ]
                            if (targetMode != homeMode) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onHomeModeChange(targetMode)
                            }
                        } else if (index in actions.indices) {
                            actions[index].onClick()
                        }
                    }
                }
            }
        }
    } else {
        Modifier
    }

    @Composable
    fun MenuContent() {
        @Composable
        fun RowScope.ModeTile(mode: HomeMode, iconRes: Int, label: String) {
            val selected = homeMode == mode
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable(enabled = interactive && nativeListActive) {
                        if (homeMode != mode) onHomeModeChange(mode)
                    }
                    .graphicsLayer { alpha = if (selected) 1f else 0.62f }
                    .clearAndSetSemantics { },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Icon(homeActionIconPainter(iconRes), contentDescription = null, modifier = Modifier.size(21.dp))
                    Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                }
            }
        }
        Column(
            modifier = Modifier
                .layout { measurable, _ ->
                    val targetSize = targetSizeProvider()
                    val width = targetSize.width.coerceAtLeast(1)
                    val height = targetSize.height.coerceAtLeast(1)
                    val placeable = measurable.measure(Constraints.fixed(width, height))
                    layout(width, height) { placeable.place(0, 0) }
                }
                .graphicsLayer {
                    val contentAlpha = contentAlphaProvider()
                    alpha = contentAlpha
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
                    } else {
                        null
                    }
                }
                .padding(
                    start = if (compactActions) (CourseShortcutContentPaddingDp + 1f - HomeAddMenuActionColumnInsetDp).dp else (HomeAddMenuConcentricInsetDp - HomeAddMenuActionColumnInsetDp).dp,
                    top = contentTopPadding,
                    end = if (compactActions) (CourseShortcutContentPaddingDp + 1f - HomeAddMenuActionColumnInsetDp).dp else (HomeAddMenuConcentricInsetDp - HomeAddMenuActionColumnInsetDp).dp,
                    bottom = if (compactActions) CourseShortcutContentPaddingDp.dp else (HomeAddMenuConcentricInsetDp - HomeAddMenuSelectionVerticalInsetDp).dp
                )
                .then(unifiedMenuGestureModifier)
        ) {
            CompositionLocalProvider(LocalContentColor provides textColor) {
                if (showModeSwitch) {
                    Row(
                        modifier = Modifier
                            .width(150.dp)
                            .align(Alignment.CenterHorizontally)
                            .height(HomeAddMenuModeHeightDp.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        ModeTile(HomeMode.Day, R.drawable.ic_day_view, "日视图")
                        ModeTile(HomeMode.Week, R.drawable.ic_week_view, "周视图")
                    }
                    Spacer(Modifier.height(HomeAddMenuSectionGapDp.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .height(HomeAddMenuDividerHeightDp.dp)
                            .background(textColor.copy(alpha = 0.14f))
                    )
                    Spacer(Modifier.height(HomeAddMenuSectionGapDp.dp))
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = HomeAddMenuActionColumnInsetDp.dp),
                    verticalArrangement = Arrangement.spacedBy(HomeAddMenuActionGapDp.dp)
                ) {
                    val nativeList = !compactActions &&
                        com.xiaomanjun.sleepdownschedule.core.ui.designsystem.LocalNativeMenuListRenderer.current?.invoke(
                            com.xiaomanjun.sleepdownschedule.core.ui.designsystem.NativeMenuListRequest(
                                items = actions.map { action -> NativeMenuItem(
                                    title = action.label, iconRes = action.iconRes,
                                    foreground = (action.textTint ?: textColor).toArgb(),
                                    iconTint = (action.iconTint ?: textColor).toArgb(), onClick = action.onClick
                                ) },
                                dark = homeStyle?.dark ?: !lightGlass,
                                modifier = Modifier.fillMaxWidth().height(actionItemHeight * actions.size +
                                    HomeAddMenuActionGapDp.dp * (actions.size - 1).coerceAtLeast(0)),
                                enabled = interactive,
                                rowHeight = actionItemHeight,
                                rowGap = HomeAddMenuActionGapDp.dp,
                                externalHighlightedIndex = externalHighlightedIndex,
                                rowContent = { index ->
                                    AddMenuLiquidItem(config = menuConfig, action = actions[index],
                                        itemHeight = actionItemHeight, highlighted = false, foreground = textColor)
                                }
                            )
                        ) == true
                    nativeListActive = nativeList
                    if (!nativeList) {
                    actions.forEachIndexed { index, action ->
                        val itemModifier = Modifier.semantics {
                            contentDescription = action.label
                            onClick(action.label) {
                                if (interactive) action.onClick()
                                interactive
                            }
                        }
                        val highlighted = externalHighlightedIndex == index || highlightedIndex == index
                        val nativeRow = (action.imageVector == null || action.iconRes != null) &&
                            LocalNativeMenuRowRenderer.current?.invoke(NativeMenuRowRequest(
                                item = NativeMenuItem(title = action.label, iconRes = action.iconRes,
                                    foreground = (action.textTint ?: textColor).toArgb(),
                                    iconTint = (action.iconTint ?: textColor).toArgb()),
                                dark = homeStyle?.dark ?: !lightGlass,
                                modifier = itemModifier.fillMaxWidth().height(actionItemHeight),
                                pressed = highlighted
                            )) == true
                        if (!nativeRow) {
                        AddMenuLiquidItem(
                            config = menuConfig,
                            action = action,
                            itemHeight = actionItemHeight,
                            compactCapsule = compactActions,
                            foreground = textColor,
                            modifier = if (!showModeSwitch) itemModifier else Modifier,
                            highlighted = highlighted
                        )
                        }
                    }
                    }
                }
            }
        }
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.TopEnd
    ) {
        if (showSurface || externalSurface) {
            val surfaceModifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val surfaceAlpha = surfaceAlphaProvider()
                    alpha = if (warmupSurface) maxOf(0.001f, surfaceAlpha) else surfaceAlpha
                }
                .drawWithContent {
                    drawContent()
                    onSurfaceDrawn()
                }
            val nativeSurface = LocalNativeMenuSurfaceRenderer.current
            val nativeRendered = nativeSurface?.invoke(
                NativeMenuSurfaceRequest(
                com.xiaomanjun.sleepdownschedule.core.ui.designsystem.NativeMaterialSurfaceRequest(
                    modifier = surfaceModifier, shape = shape,
                    dark = homeStyle?.dark ?: !lightGlass, renderSurface = !externalSurface
                ), neutral = homeStyle?.weatherMaterial == true)
            ) { MenuContent() } == true
            if (nativeRendered) {
                // Input and row animation remain with the existing menu host.
            } else if (backdrop != null) {
                LiquidButton(
                    onClick = {},
                    backdrop = backdrop,
                    modifier = surfaceModifier,
                    // Keep the same visual modifier nodes throughout the retained menu session.
                    // The host and unified action gesture still gate input by the actual phase.
                    isInteractive = (retainSurface || interactive) && !externalSurface,
                    clickTargetEnabled = false,
                    surfaceEnabled = !externalSurface,
                    // fillMaxSize already fixes the surface dimensions. Reading the animated
                    // target here used to recompose the entire source menu during handoff.
                    contentPadding = PaddingValues(0.dp),
                    blurRadius = 8.dp,
                    lensHeight = 12.dp,
                    lensAmount = 24.dp,
                    shadowEnabled = shadowEnabled,
                    pressExpansion = if (compactActions) 1.5.dp else 3.dp,
                    highlightRadiusMultiplier = 0.65f,
                    shape = shape,
                    surfaceColor = homeStyle?.let {
                        com.xiaomanjun.sleepdownschedule.core.ui.material.homeMaterialNeutralTint(it.dark)
                    } ?: (
                        if (lightGlass) HomeLightGlassSurfaceColor else Color(0xFF050505)
                        ).copy(alpha = if (lightGlass) 0.28f else 0.40f)
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        MenuContent()
                    }
                }
            } else {
                Box(
                    modifier = surfaceModifier.background(
                        color = if (appUsesDarkTheme(config)) Color(0xFF1C1C1E) else Color.White,
                        shape = shape
                    ),
                    contentAlignment = Alignment.Center
                ) {
                    MenuContent()
                }
            }
        }
    }
}
