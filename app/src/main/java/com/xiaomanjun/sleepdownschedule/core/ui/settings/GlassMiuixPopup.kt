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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import top.yukonga.miuix.kmp.basic.DropdownDefaults
import top.yukonga.miuix.kmp.basic.DropdownColors
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.ListPopupVisualStyle
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.overlay.OverlayCascadingListPopup
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference

/** Business model only; Miuix owns popup layout, input and cascading motion. */
@Immutable
internal data class SleepDownLiquidMenuItem(
    val key: String,
    val text: String,
    val summary: String? = null,
    val selected: Boolean = false,
    val enabled: Boolean = true,
    val accent: Boolean = false,
    val children: List<SleepDownLiquidMenuItem> = emptyList(),
    val onClick: () -> Unit = {}
)

private val UpwardDropdownPositionProvider = object : PopupPositionProvider {
    private val margins = PaddingValues(vertical = 8.dp)

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
    val effectiveBlur = blurRadius.coerceAtMost(9.dp)
    val lensHeight = 24.dp
    val lensAmount = 44.dp
    val surfaceAlpha = if (dark) 0.74f else 0.64f
    val surfaceColor = if (dark) Color(0xFF242424) else Color.White
    val topHighlightAlpha = if (dark) 0.10f else 0.07f
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
    return sleepDownGlassSurface(
        backdrop = backdrop,
        descriptor = descriptor,
        material = material,
        // Backdrop's lens shader requires a CornerBasedShape. A zero-radius rounded rect is
        // pixel-identical to RectangleShape while satisfying that runtime contract; Miuix still
        // owns the animated primary/secondary clip paths outside this material layer.
        shape = { RoundedCornerShape(0.dp) },
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
        effectsOverride = {
            vibrancy()
            blur(effectiveBlur.toPx())
            lens(
                lensHeight.toPx(),
                lensAmount.toPx(),
                depthEffect = false,
                chromaticAberration = false
            )
        },
        onDrawSurface = {
            drawRect(surfaceColor.copy(alpha = surfaceAlpha))
            drawRect(
                brush = Brush.verticalGradient(
                    colorStops = arrayOf(
                        0f to Color.White.copy(alpha = topHighlightAlpha),
                        0.22f to Color.White.copy(alpha = topHighlightAlpha * 0.34f),
                        1f to Color.Transparent
                    ),
                    endY = size.height * 0.52f
                )
            )
        }
    )
}

@Composable
private fun rememberMiuixListPopupStyle(
    backdrop: Backdrop?,
    config: ScheduleConfigEntity,
    cornerRadius: Dp = 25.dp
): ListPopupVisualStyle = ListPopupVisualStyle(
    // Keep the stock Miuix reveal geometry; only the pixels painted inside that surface are
    // replaced by the SleepDown glass material. Corner radius follows the NexioSchedule
    // liquid-glass dropdown (25dp continuous).
    surfaceModifier = Modifier.miuixCascadingPopupSurface(
        backdrop = backdrop,
        config = config,
        blurRadius = 8.dp
    ),
    backgroundColor = Color.Transparent,
    cornerRadius = cornerRadius
)

@Composable
private fun rememberSleepDownPopupRowColors(contentColor: Color? = null): DropdownColors {
    val defaults = DropdownDefaults.dropdownColors()
    return remember(defaults, contentColor) {
        defaults.copy(
            contentColor = contentColor ?: defaults.contentColor,
            summaryColor = contentColor?.copy(alpha = 0.62f) ?: defaults.summaryColor,
            containerColor = Color.Transparent,
            selectedContentColor = contentColor ?: defaults.selectedContentColor,
            selectedSummaryColor = contentColor?.copy(alpha = 0.72f)
                ?: defaults.selectedSummaryColor,
            selectedContainerColor = Color.Transparent
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
    insideMargin: PaddingValues = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
    maxHeight: Dp = 318.dp,
    @Suppress("UNUSED_PARAMETER") expanded: Boolean? = null,
    enabled: Boolean = true,
    onExpandedChange: (Boolean) -> Unit = {},
    onSelectedIndexChange: (Int) -> Unit
) {
    // The dropdown host is rendered by the root Miuix host as a sibling after the page's
    // underlay producer, so it may sample the complete Scaffold underlay (TopBar, large title,
    // content and low-level overlays) instead of only the flat background passed by the caller.
    val completeUnderlayBackdrop = LocalSettingsPopupBackdrop.current ?: backdrop
    val renderInRootScaffold = LocalCenteredDialogRenderInRootScaffold.current
    val popupVisualStyle = rememberMiuixListPopupStyle(completeUnderlayBackdrop, config)
    val popupRowColors = rememberSleepDownPopupRowColors(sleepDownPanelForegroundColor(config))
    OverlayDropdownPreference(
        items = items,
        selectedIndex = selectedIndex,
        title = title,
        modifier = modifier,
        summary = summary,
        insideMargin = insideMargin,
        maxHeight = maxHeight,
        enabled = enabled,
        renderInRootScaffold = renderInRootScaffold,
        excludeFromBackdropCapture = true,
        popupVisualStyle = popupVisualStyle,
        dropdownColors = popupRowColors,
        onExpandedChange = onExpandedChange,
        onSelectedIndexChange = onSelectedIndexChange
    )
}

private fun SleepDownLiquidMenuItem.asMiuixDropdownItem(): DropdownItem = DropdownItem(
    text = text,
    enabled = enabled,
    selected = selected || accent,
    onClick = onClick,
    summary = summary,
    children = children.takeIf { it.isNotEmpty() }?.map { it.asMiuixDropdownItem() }
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
    menuMaxHeight: Dp = 360.dp,
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
    val entry = remember(items) { DropdownEntry(items.map { it.asMiuixDropdownItem() }) }
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
    OverlayCascadingListPopup(
        show = show,
        entries = listOf(entry),
        onDismissRequest = onDismissRequest,
        popupPositionProvider = UpwardDropdownPositionProvider,
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
