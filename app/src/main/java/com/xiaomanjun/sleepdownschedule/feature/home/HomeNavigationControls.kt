package com.xiaomanjun.sleepdownschedule.feature.home

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.catalog.components.LiquidButton
import com.kyant.shapes.Capsule
import com.xiaomanjun.sleepdownschedule.DockAlignment
import com.xiaomanjun.sleepdownschedule.MainActivity
import com.xiaomanjun.sleepdownschedule.ScheduleConfigEntity
import com.xiaomanjun.sleepdownschedule.app.ui.Screen
import com.xiaomanjun.sleepdownschedule.core.ui.chrome.HomeLightGlassSurfaceColor
import com.xiaomanjun.sleepdownschedule.core.ui.chrome.homeActionIconPainter
import com.xiaomanjun.sleepdownschedule.core.ui.interaction.dockImeCompensationPx
import com.xiaomanjun.sleepdownschedule.core.ui.interaction.excludeParentPullGesture
import com.xiaomanjun.sleepdownschedule.core.ui.material.LocalHomeMaterialControls
import com.xiaomanjun.sleepdownschedule.glass.ui.GlassLens
import com.xiaomanjun.sleepdownschedule.glass.ui.GlassPill
import com.xiaomanjun.sleepdownschedule.glass.ui.LocalAdaptiveGlass
import com.xiaomanjun.sleepdownschedule.glass.ui.appUsesDarkTheme
import com.xiaomanjun.sleepdownschedule.glass.ui.glassForegroundColor
import com.xiaomanjun.sleepdownschedule.glass.ui.glassUsesLightStyle
import kotlinx.coroutines.delay

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FloatingDock(
    selected: Screen,
    backdrop: Backdrop?,
    config: ScheduleConfigEntity,
    modifier: Modifier = Modifier,
    previewMode: Boolean = false,
    onHome: () -> Unit,
    onConfig: () -> Unit
) {
    val adaptiveGlass = LocalAdaptiveGlass.current
    val lightGlass = if (selected is Screen.Home) {
        adaptiveGlass.lightGlass
    } else {
        !appUsesDarkTheme(config)
    }
    val density = LocalDensity.current
    // MIUI reports the IME-sized bottom edge through safeDrawing for the underlying Activity
    // while a transparent Compose Dialog owns the keyboard. That makes a bottom-aligned Dock jump
    // above the keyboard even though the Activity itself uses adjustNothing. Use the stable
    // navigation-bar inset as the physical screen edge; the separate IME compensation still
    // handles devices that genuinely resize the Activity window.
    val systemBottomPx = if (previewMode) {
        0
    } else {
        WindowInsets.navigationBarsIgnoringVisibility
            .only(WindowInsetsSides.Bottom)
            .getBottom(density)
    }
    val bottomInset = with(density) { systemBottomPx.toDp() }
    val imeCompensationPx = dockImeCompensationPx(
        imeBottomPx = if (previewMode) 0 else WindowInsets.ime.getBottom(density),
        systemBottomPx = systemBottomPx
    )
    val bottomOffset = (bottomInset + 8.dp).coerceAtLeast(8.dp)
    val dockTextColor by animateColorAsState(
        targetValue = if (selected is Screen.Home) {
            adaptiveGlass.contentColor
        } else if (appUsesDarkTheme(config)) {
            ComposeColor.White
        } else {
            ComposeColor.Black
        },
        animationSpec = tween(220),
        label = "FloatingDockContentTheme"
    )
    val dockAlignment = when (config.dockAlignment) {
        DockAlignment.LEFT -> Alignment.BottomStart
        DockAlignment.CENTER -> Alignment.BottomCenter
        DockAlignment.RIGHT -> Alignment.BottomEnd
    }
    val horizontalPadding = when (config.dockAlignment) {
        DockAlignment.LEFT -> PaddingValues(start = 18.dp, bottom = bottomOffset)
        DockAlignment.CENTER -> PaddingValues(start = 18.dp, end = 18.dp, bottom = bottomOffset)
        DockAlignment.RIGHT -> PaddingValues(end = 18.dp, bottom = bottomOffset)
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontalPadding)
            // MainActivity keeps adjustResize for text fields. Counter only the IME portion
            // here so the home dock stays at its physical bottom position behind the keyboard.
            .graphicsLayer { translationY = imeCompensationPx.toFloat() },
        contentAlignment = dockAlignment
    ) {
        CompositionLocalProvider(LocalContentColor provides dockTextColor) {
            HomeDockTabs(
                selectedIndex = if (selected is Screen.Home) 0 else 1, parallel = false,
                config = config, backdrop = backdrop, lightGlass = lightGlass,
                modifier = Modifier.width(LocalHomeMaterialControls.current.ordinaryDockWidth()).excludeParentPullGesture(),
                onSelect = { index -> if (index == 0) onHome() else onConfig() }
            )
        }
    }
}

@Composable
fun ScheduleManagerEntryPill(
    visible: Boolean,
    backdrop: Backdrop?,
    config: ScheduleConfigEntity,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onDismiss: () -> Unit
) {
    BackHandler(enabled = visible) {
        onDismiss()
    }
    LaunchedEffect(visible) {
        if (visible) {
            delay(5_000)
            onDismiss()
        }
    }
    val density = LocalDensity.current
    val bottomInset = with(density) { WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom).getBottom(this).toDp() }
    val dockBottomOffset = (bottomInset + 8.dp).coerceAtLeast(8.dp)
    val pillBottomOffset = dockBottomOffset + 68.dp
    val dockAlignment = when (config.dockAlignment) {
        DockAlignment.LEFT -> Alignment.BottomStart
        DockAlignment.CENTER -> Alignment.BottomCenter
        DockAlignment.RIGHT -> Alignment.BottomEnd
    }
    val horizontalPadding = when (config.dockAlignment) {
        DockAlignment.LEFT -> PaddingValues(start = 18.dp, bottom = pillBottomOffset)
        DockAlignment.CENTER -> PaddingValues(start = 18.dp, end = 18.dp, bottom = pillBottomOffset)
        DockAlignment.RIGHT -> PaddingValues(end = 18.dp, bottom = pillBottomOffset)
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontalPadding),
        contentAlignment = dockAlignment
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = scaleIn(initialScale = 0.4f, animationSpec = tween(320, easing = CubicBezierEasing(0.2f, 0.75f, 0.2f, 1f))),
            exit = scaleOut(targetScale = 0.4f, animationSpec = tween(120))
        ) {
            val entryAlpha by transition.animateFloat(
                transitionSpec = { tween(if (targetState == androidx.compose.animation.EnterExitState.Visible) 320 else 120) },
                label = "schedule settings entry alpha"
            ) { if (it == androidx.compose.animation.EnterExitState.Visible) 1f else 0f }
            val entryModifier = Modifier.graphicsLayer {
                alpha = entryAlpha
                compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.ModulateAlpha
            }
            val lightGlass = glassUsesLightStyle(config)
            if (backdrop != null) {
                LiquidButton(
                    onClick = onClick,
                    backdrop = backdrop,
                    modifier = entryModifier.width(140.dp),
                    height = 48.dp,
                    blurRadius = 2.dp,
                    lensHeight = 12.dp,
                    lensAmount = 24.dp,
                        surfaceColor = if (lightGlass) HomeLightGlassSurfaceColor.copy(alpha = 0.075f) else ComposeColor(0xFF121212).copy(alpha = 0.075f),
                        chromaticAberration = false,
                        shadowEnabled = false,
                        highlightEnabled = true,
                        contentPadding = PaddingValues(horizontal = 18.dp)
                ) {
                    Text(
                        "多课表管理",
                        color = glassForegroundColor(config),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                }
            } else {
                GlassPill(
                    backdrop = null,
                    config = config,
                    modifier = entryModifier
                        .width(140.dp)
                        .height(48.dp),
                    onClick = onClick
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            "多课表管理",
                            color = glassForegroundColor(config),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DockBackdropContinuityPatch(config: ScheduleConfigEntity, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val bottomInset = with(density) { WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom).getBottom(this).toDp() }
    val height = (bottomInset + 92.dp).coerceAtLeast(92.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .background(ComposeColor.Transparent)
    )
}

@Composable
internal fun DockTabContent(iconRes: Int, label: String, iconSize: Dp) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .background(ComposeColor.Transparent),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(1.dp, Alignment.CenterVertically)
    ) {
        Icon(homeActionIconPainter(iconRes), contentDescription = null, modifier = Modifier.size(iconSize))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
fun DockItem(selected: Boolean, backdrop: Backdrop?, config: ScheduleConfigEntity, iconRes: Int, label: String, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    Box(
        modifier = Modifier
            .clip(Capsule())
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .height(60.dp)
            .width(78.dp),
        contentAlignment = Alignment.Center
    ) {
        if (pressed) {
            GlassLens(
                backdrop = backdrop,
                config = config,
                pressProgress = 1f,
                modifier = Modifier.fillMaxSize()
            )
        }
        Column(
            modifier = Modifier.padding(vertical = 7.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically)
        ) {
            Icon(homeActionIconPainter(iconRes), contentDescription = null, modifier = Modifier.size(22.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
        }
    }
}
