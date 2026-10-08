package com.xiaomanjun.sleepdownschedule.core.ui.settings

import com.xiaomanjun.sleepdownschedule.ScheduleConfigEntity
import com.xiaomanjun.sleepdownschedule.glass.GlassBackdropDomain
import com.xiaomanjun.sleepdownschedule.glass.GlassEffectFrame
import com.xiaomanjun.sleepdownschedule.glass.GlassMaterialRole
import com.xiaomanjun.sleepdownschedule.glass.GlassMaterialSpec
import com.xiaomanjun.sleepdownschedule.glass.rememberGlassSurfaceDescriptor
import com.xiaomanjun.sleepdownschedule.glass.rememberGlassCombinedBackdrop
import com.xiaomanjun.sleepdownschedule.glass.rememberGlassLayerBackdrop
import com.xiaomanjun.sleepdownschedule.glass.glassBackdropProducer
import com.xiaomanjun.sleepdownschedule.glass.sleepDownGlassSurface
import com.xiaomanjun.sleepdownschedule.glass.ui.appUsesDarkTheme
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.sleepDownPanelForegroundColor
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.LocalCenteredDialogRenderInRootScaffold
import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Arrangement
import com.kyant.shapes.RoundedRectangle
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.zIndex
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.withTransformCompensation
import com.kyant.backdrop.catalog.utils.InteractiveHighlight
import com.kyant.backdrop.catalog.components.liquidButtonVisualTransform
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import top.yukonga.miuix.kmp.basic.DropdownDefaults
import top.yukonga.miuix.kmp.basic.DropdownColors
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.basic.ListPopupVisualStyle
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.overlay.OverlayCascadingListPopup
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Business model only; Miuix owns popup layout, input and cascading motion. */
@Immutable
internal data class SleepDownLiquidMenuItem(
    val key: String,
    val text: String,
    val iconRes: Int? = null,
    val summary: String? = null,
    val selected: Boolean = false,
    val enabled: Boolean = true,
    val accent: Boolean = false,
    val children: List<SleepDownLiquidMenuItem> = emptyList(),
    val onClick: () -> Unit = {}
)

private class UpwardDropdownPositionProvider(
    horizontalSafeInset: Dp
) : PopupPositionProvider {
    private val margins = PaddingValues(horizontal = horizontalSafeInset, vertical = 8.dp)

    override fun calculatePosition(
        anchorBounds: IntRect,
        windowBounds: IntRect,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
        popupMargin: IntRect,
        alignment: PopupPositionProvider.Align
    ): IntOffset {
        val endAligned = when (alignment) {
            PopupPositionProvider.Align.End,
            PopupPositionProvider.Align.TopEnd,
            PopupPositionProvider.Align.BottomEnd -> layoutDirection == LayoutDirection.Ltr

            else -> layoutDirection == LayoutDirection.Rtl
        }
        val preferredX = if (endAligned) {
            anchorBounds.right - popupContentSize.width - popupMargin.right
        } else {
            anchorBounds.left + popupMargin.left
        }
        val minX = windowBounds.left
        val maxX = (windowBounds.right - popupContentSize.width - popupMargin.right)
            .coerceAtLeast(minX)
        val minY = windowBounds.top + popupMargin.top
        val maxY = (windowBounds.bottom - popupContentSize.height - popupMargin.bottom)
            .coerceAtLeast(minY)
        return IntOffset(
            x = preferredX.coerceIn(minX, maxX),
            y = (anchorBounds.top - popupContentSize.height - popupMargin.top)
                .coerceIn(minY, maxY)
        )
    }

    override fun getMargins(): PaddingValues = margins
}

@Composable
private fun Modifier.miuixCascadingPopupSurface(
    backdrop: Backdrop?,
    config: ScheduleConfigEntity,
    blurRadius: Dp
): Modifier {
    val dark = appUsesDarkTheme(config)
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || backdrop == null) {
        return background(if (dark) Color(0xFF242424) else Color.White)
    }
    val effectiveBlur = blurRadius.coerceAtMost(24.dp)
    // Keep the lens gentle: an overlarge lens/refraction band refracts content against the
    // rounded popup corners and reads as torn glass lines at the bottom corners.
    val lensHeight = 12.dp
    val lensAmount = 24.dp
    // Match the Home menu's translucent material so the moving light is not buried in white.
    val surfaceAlpha = if (dark) 0.40f else 0.28f
    val surfaceColor = if (dark) Color(0xFF050505) else Color(0xFFF2F4F8)
    val material = GlassMaterialSpec.popup(effectiveBlur).copy(
        lensHeight = lensHeight,
        lensAmount = lensAmount,
        surfaceAlpha = surfaceAlpha,
        borderAlpha = 0f,
        highlightAlpha = 0f,
        shadowAlpha = 0f,
        innerShadowAlpha = 0f,
        depthEffect = false,
        useVibrancy = true
    )
    val descriptor = rememberGlassSurfaceDescriptor(
        debugLabel = "MiuixCascadingPopup",
        domain = GlassBackdropDomain.DialogBridge,
        materialRole = GlassMaterialRole.Popup
    )
    val sampleBackdrop = remember(backdrop) {
        if (backdrop is LayerBackdrop) backdrop.withTransformCompensation() else backdrop
    }
    return sleepDownGlassSurface(
        backdrop = sampleBackdrop,
        descriptor = descriptor,
        material = material,
        // Backdrop's lens shader requires a CornerBasedShape. A zero-radius rounded rect is
        // pixel-identical to RectangleShape while satisfying that runtime contract; Miuix still
        // owns the animated primary/secondary clip paths outside this material layer.
        shape = { RoundedRectangle(0.dp) },
        effectFrame = GlassEffectFrame(
            blur = effectiveBlur,
            lensHeight = lensHeight,
            lensAmount = lensAmount,
            useVibrancy = true,
            chromaticAberration = false,
            highlight = null,
            shadowAlpha = null,
            innerShadow = null,
            depthEffect = false
        ),
        // Keep the Nexio/Miuix effect order and let the surface tint stay light enough for the
        // stronger lens to remain visible through both primary and cascading popup layers.
        effectInputKey = material,
        effectsOverride = remember(effectiveBlur, lensHeight, lensAmount) { {
            vibrancy()
            blur(effectiveBlur.toPx())
            lens(
                lensHeight.toPx(),
                lensAmount.toPx(),
                depthEffect = false,
                chromaticAberration = false
            )
        } },
        onDrawSurface = {
            drawRect(surfaceColor.copy(alpha = surfaceAlpha))
        }
    )
}

@Composable
private fun rememberMiuixListPopupStyle(
    backdrop: Backdrop?,
    config: ScheduleConfigEntity,
    cornerRadius: Dp = 25.dp
): ListPopupVisualStyle {
    val scope = rememberCoroutineScope()
    val dark = appUsesDarkTheme(config)
    val radiusCapPx = with(LocalDensity.current) { 90.dp.toPx() }
    val highlight = remember(scope, radiusCapPx, dark) {
        InteractiveHighlight(scope, radius = { minOf(it.minDimension * 0.65f, radiusCapPx) },
            ambientAlpha = if (dark) 0.08f else 0.025f,
            spotAlpha = 0.20f, fallbackAlpha = 0.25f,
            contrastHalo = if (dark) Color.Transparent else Color.Black.copy(alpha = 0.09f),
            fadeOutAtReleasePosition = true)
    }
    // Kyant's observer does not consume input: Miuix continues to own selection and dismissal.
    val interaction = Modifier.liquidButtonVisualTransform(highlight).then(highlight.gestureModifier)
    val rim = remember(dark) {
        BorderStroke(1.dp, Brush.verticalGradient(
            0f to Color.White.copy(alpha = if (dark) 0.38f else 0.66f),
            0.07f to Color.White.copy(alpha = 0.10f),
            0.18f to Color.Transparent,
            0.82f to Color.Transparent,
            0.93f to Color.White.copy(alpha = 0.10f),
            1f to Color.White.copy(alpha = if (dark) 0.38f else 0.66f)
        ))
    }
    return ListPopupVisualStyle(
    // Independent size/origin tracks follow Nexio's current popup motion. Sampling remains
    // in the stationary canvas, with our own material and neutral interaction feedback.
    surfaceModifier = Modifier.miuixCascadingPopupSurface(
        backdrop = backdrop,
        config = config,
        blurRadius = 10.dp
    ).then(highlight.modifier),
    backgroundColor = Color.Transparent,
    cornerRadius = cornerRadius,
    itemTextStyle = MiuixTheme.textStyles.main.copy(fontSize = 14.sp, fontWeight = FontWeight.Medium),
    itemVerticalPadding = 7.dp,
    itemMinHeight = 40.dp,
    border = rim,
    morphAnimation = true,
    slideSelection = true,
    dimBackground = false,
    holdAnchor = true,
    // Broad ambient shadow separates the light panel from white settings cards without
    // adding a second blurred backdrop or a dark, hard outline.
    shadowElevation = if (dark) 16.dp else 24.dp,
    ambientShadowColor = Color.Black.copy(alpha = if (dark) 0.125f else 0.22f),
    spotShadowColor = Color.Black.copy(alpha = 0.125f),
    interactionModifier = interaction
    )
}

@Composable
private fun rememberSleepDownPopupRowColors(contentColor: Color? = null): DropdownColors {
    val defaults = DropdownDefaults.dropdownColors()
    val indicatorColor = MiuixTheme.colorScheme.onSurfaceVariantActions
    return remember(defaults, contentColor, indicatorColor) {
        defaults.copy(
            contentColor = contentColor ?: defaults.contentColor,
            summaryColor = contentColor?.copy(alpha = 0.62f) ?: defaults.summaryColor,
            containerColor = Color.Transparent,
            selectedContentColor = contentColor ?: defaults.selectedContentColor,
            selectedSummaryColor = contentColor?.copy(alpha = 0.72f)
                ?: defaults.selectedSummaryColor,
            selectedContainerColor = Color.Transparent,
            selectedIndicatorColor = indicatorColor
        )
    }
}

@Composable
internal fun SleepDownLiquidDropdownPreference(
    items: List<String>,
    selectedIndex: Int,
    title: String,
    backdrop: Backdrop?,
    config: ScheduleConfigEntity,
    modifier: Modifier = Modifier,
    summary: String? = null,
    selectedBadgeText: String? = null,
    insideMargin: PaddingValues = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
    maxHeight: Dp = 318.dp,
    compactTextStyle: TextStyle? = null,
    @Suppress("UNUSED_PARAMETER") expanded: Boolean? = null,
    enabled: Boolean = true,
    showAnchorPressFeedback: Boolean = true,
    onExpandedChange: (Boolean) -> Unit = {},
    onSelectedIndexChange: (Int) -> Unit
) {
    // The dropdown host is rendered by the root Miuix host as a sibling after the page's
    // underlay producer, so it may sample the complete Scaffold underlay (TopBar, large title,
    // content and low-level overlays) instead of only the flat background passed by the caller.
    val completeUnderlayBackdrop = LocalSettingsPopupBackdrop.current ?: backdrop
    val renderInRootScaffold = LocalCenteredDialogRenderInRootScaffold.current
    val popupVisualStyle = rememberMiuixListPopupStyle(completeUnderlayBackdrop, config)
        .copy(holdAnchor = showAnchorPressFeedback).let { style ->
        if (compactTextStyle == null) style else style.copy(
            itemTextStyle = compactTextStyle,
            itemVerticalPadding = 6.dp,
            itemMinHeight = 44.dp
        )
    }
    val popupRowColors = rememberSleepDownPopupRowColors(sleepDownPanelForegroundColor(config))
    val preferenceContent: @Composable () -> Unit = { OverlayDropdownPreference(
        items = items,
        selectedIndex = selectedIndex,
        title = if (selectedBadgeText == null && compactTextStyle == null) title else "",
        modifier = modifier,
        summary = summary,
        startAction = if (selectedBadgeText != null || compactTextStyle != null) {
            {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val colors = BasicComponentDefaults.titleColor()
                    MiuixText(
                        text = title,
                        style = compactTextStyle ?: MiuixTheme.textStyles.main,
                        fontSize = compactTextStyle?.fontSize ?: MiuixTheme.textStyles.headline1.fontSize,
                        fontWeight = if (compactTextStyle == null) FontWeight.Medium
                            else compactTextStyle.fontWeight,
                        color = if (enabled) colors.color else colors.disabledColor
                    )
                    selectedBadgeText?.let { PreferenceBadge(it) }
                }
            }
        } else null,
        insideMargin = insideMargin,
        maxHeight = maxHeight,
        enabled = enabled,
        renderInRootScaffold = renderInRootScaffold,
        excludeFromBackdropCapture = true,
        popupVisualStyle = popupVisualStyle,
        dropdownColors = popupRowColors,
        onExpandedChange = onExpandedChange,
        onSelectedIndexChange = onSelectedIndexChange
    ) }
    if (compactTextStyle == null) {
        preferenceContent()
    } else {
        MiuixTheme(textStyles = MiuixTheme.textStyles.copy(
            main = compactTextStyle,
            body2 = compactTextStyle
        )) { preferenceContent() }
    }
}

private fun SleepDownLiquidMenuItem.asMiuixDropdownItem(iconColor: Color): DropdownItem = DropdownItem(
    text = text,
    enabled = enabled,
    selected = selected || accent,
    onClick = onClick,
    icon = iconRes?.let { resourceId ->
        { modifier ->
            Icon(
                painter = painterResource(resourceId),
                contentDescription = null,
                tint = iconColor,
                modifier = modifier.size(20.dp)
            )
        }
    },
    summary = summary,
    children = children.takeIf { it.isNotEmpty() }?.map { it.asMiuixDropdownItem(iconColor) }
)

@Composable
internal fun SleepDownLiquidCascadingPopup(
    show: Boolean,
    @Suppress("UNUSED_PARAMETER") anchorBounds: Rect,
    items: List<SleepDownLiquidMenuItem>,
    onDismissRequest: () -> Unit,
    backdrop: Backdrop?,
    config: ScheduleConfigEntity,
    panelMinWidth: Dp = 168.dp,
    menuMaxHeight: Dp? = null,
    horizontalSafeInset: Dp = 0.dp,
    contentColor: Color? = null,
    collapseOnSelection: Boolean = true
) {
    val completeUnderlayBackdrop = LocalSettingsPopupBackdrop.current ?: backdrop
    val renderInRootScaffold = LocalCenteredDialogRenderInRootScaffold.current
    val primaryPopupBackdrop = rememberGlassLayerBackdrop(
        domain = GlassBackdropDomain.Content,
        providerId = "miuix-cascade-primary"
    )
    val secondaryUnderlayBackdrop: Backdrop = if (completeUnderlayBackdrop != null) {
        rememberGlassCombinedBackdrop(completeUnderlayBackdrop, primaryPopupBackdrop)
    } else {
        primaryPopupBackdrop
    }
    val popupContentColor = contentColor ?: sleepDownPanelForegroundColor(config)
    val entry = remember(items, popupContentColor) {
        DropdownEntry(items.map { it.asMiuixDropdownItem(popupContentColor) })
    }
    val basePrimaryVisualStyle = rememberMiuixListPopupStyle(
        backdrop = completeUnderlayBackdrop,
        config = config,
        cornerRadius = 25.dp
    )
    val popupVisualStyle = basePrimaryVisualStyle.copy(
        surfaceModifier = Modifier
            .glassBackdropProducer(primaryPopupBackdrop)
            .then(basePrimaryVisualStyle.surfaceModifier)
    )
    val secondaryPopupVisualStyle = rememberMiuixListPopupStyle(
        backdrop = secondaryUnderlayBackdrop,
        config = config,
        cornerRadius = 25.dp
    )
    val popupRowColors = rememberSleepDownPopupRowColors(
        contentColor ?: sleepDownPanelForegroundColor(config)
    )
    val popupPositionProvider = remember(horizontalSafeInset) {
        UpwardDropdownPositionProvider(horizontalSafeInset)
    }
    OverlayCascadingListPopup(
        show = show,
        entries = listOf(entry),
        onDismissRequest = onDismissRequest,
        popupPositionProvider = popupPositionProvider,
        alignment = PopupPositionProvider.Align.End,
        enableWindowDim = false,
        minWidth = panelMinWidth,
        maxHeight = menuMaxHeight,
        renderInRootScaffold = renderInRootScaffold,
        excludeFromBackdropCapture = true,
        visualStyle = popupVisualStyle,
        secondaryVisualStyle = secondaryPopupVisualStyle,
        dropdownColors = popupRowColors,
        // Miuix renders the animated entry in the root scaffold, but the composer/field can still
        // be a later sibling during the first reveal frame. Keep the popup entry above that input
        // layer for the entire enter/exit handoff.
        popupModifier = Modifier.zIndex(1000f),
        collapseOnSelection = collapseOnSelection
    )
}
