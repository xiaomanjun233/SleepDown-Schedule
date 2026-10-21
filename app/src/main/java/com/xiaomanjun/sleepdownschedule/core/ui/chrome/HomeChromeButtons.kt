package com.xiaomanjun.sleepdownschedule.core.ui.chrome

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.catalog.components.LiquidButton
import com.kyant.backdrop.catalog.components.LiquidButtonPressSnapshot
import com.xiaomanjun.sleepdownschedule.R
import com.xiaomanjun.sleepdownschedule.ScheduleConfigEntity
import com.xiaomanjun.sleepdownschedule.core.ui.material.LocalHomeMaterialControls
import com.xiaomanjun.sleepdownschedule.glass.ui.GlassPill
import com.xiaomanjun.sleepdownschedule.glass.ui.LocalAdaptiveGlass
import com.xiaomanjun.sleepdownschedule.model.HomeMode

private fun performButtonHaptic(view: android.view.View) {
    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
}

@Composable
private fun HomeActionCapsule(
    backdrop: Backdrop?,
    config: ScheduleConfigEntity,
    homeMode: HomeMode,
    visible: Boolean,
    onPersonalize: (Float) -> Unit,
    onMore: (Float) -> Unit,
    onCapsulePositioned: (androidx.compose.ui.geometry.Rect) -> Unit
) {
    val view = LocalView.current
    val pressSnapshot = remember { LiquidButtonPressSnapshot() }
    fun capturedScale(): Float = 1f + (3f / 42f) * pressSnapshot.progress.coerceIn(0f, 1f)
    Box(
        modifier = Modifier
            // Keep the visual right edge on the same inset as the Today Agent surface. Width
            // changes therefore trim only the leading side instead of shifting the shared edge.
            .padding(end = if (homeMode == HomeMode.Week) 4.dp else 7.dp)
            .size(width = 100.dp, height = 42.dp)
            .onGloballyPositioned { onCapsulePositioned(it.boundsInRoot()) }
            .graphicsLayer { alpha = if (visible) 1f else 0f }
    ) {
        HomeActionCapsuleVisual(
            backdrop = backdrop,
            config = config,
            modifier = Modifier.fillMaxSize(),
            isInteractive = visible,
            pressSnapshot = pressSnapshot,
            onPersonalize = {
                performButtonHaptic(view)
                onPersonalize(capturedScale())
            },
            onMore = {
                performButtonHaptic(view)
                onMore(capturedScale())
            }
        )
    }
}

@Composable
internal fun HomeActionCapsuleVisual(
    backdrop: Backdrop?,
    config: ScheduleConfigEntity,
    modifier: Modifier = Modifier,
    isInteractive: Boolean = true,
    pressSnapshot: LiquidButtonPressSnapshot? = null,
    onPersonalize: () -> Unit = {},
    onMore: () -> Unit = {}
) {
    val adaptiveGlass = LocalAdaptiveGlass.current
    val lightGlass = adaptiveGlass.lightGlass
    val personalizeInteraction = remember { MutableInteractionSource() }
    val moreInteraction = remember { MutableInteractionSource() }
    val interactionModifier: (String, MutableInteractionSource, () -> Unit) -> Modifier =
        { description, interaction, action ->
        Modifier
            .fillMaxHeight()
            .semantics { contentDescription = description }
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = isInteractive,
                onClick = action
            )
    }
    val content: @Composable () -> Unit = {
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = interactionModifier(
                    "benchmark_personalize_button",
                    personalizeInteraction,
                    onPersonalize
                ).let { Modifier.weight(1f).then(it) },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    homeActionIconPainter(R.drawable.ic_edit),
                    contentDescription = null,
                    modifier = Modifier.size(19.dp),
                    tint = adaptiveGlass.contentColor
                )
            }
            Box(
                modifier = interactionModifier("添加菜单", moreInteraction, onMore)
                    .let { Modifier.weight(1f).then(it) },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    homeActionIconPainter(R.drawable.ic_more_horizontal),
                    contentDescription = null,
                    modifier = Modifier.size(21.dp),
                    tint = adaptiveGlass.contentColor
                )
            }
        }
    }
    if (backdrop != null) {
        LiquidButton(
            onClick = {},
            backdrop = backdrop,
            modifier = modifier,
            isInteractive = isInteractive,
            clickTargetEnabled = false,
            height = 42.dp,
            contentPadding = PaddingValues(0.dp),
            blurRadius = homeChromeBlur(HomeHeaderGlassBlur, config),
            lensHeight = HomeHeaderGlassLensHeight,
            lensAmount = HomeHeaderGlassLensAmount,
            chromaticAberration = false,
            shadowEnabled = lightGlass,
            shadowStyle = LightTopBarButtonShadow,
            pressExpansion = 3.dp,
            highlightRadiusMultiplier = 0.9f,
            pressSnapshot = pressSnapshot,
            surfaceColor = (
                if (lightGlass) HomeLightGlassSurfaceColor else ComposeColor(0xFF121212)
                ).copy(alpha = homeChromeGlassSurfaceAlpha(lightGlass))
        ) { content() }
    } else {
        GlassPill(backdrop = null, config = config, modifier = modifier) { content() }
    }
}

@Composable
fun HomeIconButton(
    backdrop: Backdrop?,
    config: ScheduleConfigEntity,
    iconRes: Int,
    contentDescription: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    accentColor: ComposeColor = ComposeColor.Unspecified,
    visible: Boolean = true,
    onClick: (Float) -> Unit,
    onButtonPositioned: ((androidx.compose.ui.geometry.Rect) -> Unit)? = null
) {
    val view = LocalView.current
    val pressSnapshot = remember { LiquidButtonPressSnapshot() }
    fun capturedScale(): Float = 1f + (3f / 42f) * pressSnapshot.progress.coerceIn(0f, 1f)
    Box(
        modifier = modifier
            .padding(end = 7.dp)
            .size(42.dp)
            .then(
                if (onButtonPositioned != null) {
                    Modifier.onGloballyPositioned { onButtonPositioned(it.boundsInRoot()) }
                } else {
                    Modifier
                }
            )
            .graphicsLayer { alpha = if (visible) 1f else 0f }
    ) {
        HomeIconButtonVisual(
            backdrop = backdrop,
            config = config,
            iconRes = iconRes,
            contentDescription = contentDescription,
            selected = selected,
            accentColor = accentColor,
            modifier = Modifier.fillMaxSize(),
            isInteractive = visible,
            pressSnapshot = pressSnapshot,
            // COUI owns touch animation and haptics; only forward the business action.
            onNativeClick = { onClick(1f) },
            onClick = {
                performButtonHaptic(view)
                onClick(capturedScale())
            }
        )
    }
}

@Composable
internal fun HomeIconButtonVisual(
    backdrop: Backdrop?,
    config: ScheduleConfigEntity,
    iconRes: Int,
    contentDescription: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    accentColor: ComposeColor = ComposeColor.Unspecified,
    isInteractive: Boolean = true,
    pressSnapshot: LiquidButtonPressSnapshot? = null,
    onNativeClick: (() -> Unit)? = null,
    onClick: () -> Unit = {}
) {
    val adaptiveGlass = LocalAdaptiveGlass.current
    val lightGlass = adaptiveGlass.lightGlass
    if (LocalHomeMaterialControls.current.IconButton(
            iconRes = iconRes,
            contentDescription = contentDescription,
            dark = !lightGlass,
            interactive = isInteractive,
            selected = selected,
            modifier = modifier,
            onClick = onNativeClick ?: onClick
        )) return
    val baseSurfaceColor = if (lightGlass) HomeLightGlassSurfaceColor else ComposeColor(0xFF121212)
    val buttonSurfaceColor = if (accentColor.isSpecified) {
        accentColor.copy(alpha = if (lightGlass) HomeLightGlassAccentTintAlpha else 0.32f)
    } else {
        baseSurfaceColor.copy(alpha = homeChromeGlassSurfaceAlpha(lightGlass))
    }
    if (backdrop != null) {
        LiquidButton(
            onClick = if (isInteractive) onClick else ({}),
            backdrop = backdrop,
            modifier = modifier,
            height = 42.dp,
            tint = if (accentColor.isSpecified) accentColor else ComposeColor.Unspecified,
            surfaceColor = buttonSurfaceColor,
            contentPadding = PaddingValues(0.dp),
            blurRadius = homeChromeBlur(HomeHeaderGlassBlur, config),
            lensHeight = HomeHeaderGlassLensHeight,
            lensAmount = HomeHeaderGlassLensAmount,
            chromaticAberration = false,
            shadowEnabled = lightGlass,
            shadowStyle = LightTopBarButtonShadow,
            pressExpansion = 3.dp,
            pressSnapshot = pressSnapshot
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    homeActionIconPainter(iconRes),
                    contentDescription = contentDescription,
                    modifier = Modifier.size(20.dp),
                    tint = adaptiveGlass.contentColor
                )
            }
        }
    } else {
        GlassPill(
            backdrop = null,
            config = config,
            modifier = modifier,
            selected = selected,
            onClick = if (isInteractive) onClick else ({})
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(homeActionIconPainter(iconRes), contentDescription = contentDescription, modifier = Modifier.size(20.dp))
            }
        }
    }
}
