package com.xiaomanjun.sleepdownschedule

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import top.yukonga.miuix.kmp.basic.DropdownColors
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.overlay.OverlayCascadingListPopup
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme
import top.yukonga.miuix.kmp.window.WindowCascadingListPopup
import kotlin.math.max

internal enum class MiuixCascadingPopupHost {
    Overlay,
    Window
}

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
    val dark = !glassUsesLightStyle(config)
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || backdrop == null) {
        return background(settingsPageBackground(settingsVisualConfig(config)))
    }
    return drawBackdrop(
        backdrop = backdrop,
        // Miuix owns the animated primary/secondary clip paths. Keeping this rectangular lets
        // the same material follow both surfaces without fighting the cascade morph geometry.
        shape = { RectangleShape },
        effects = { blur(blurRadius.toPx()) },
        highlight = null,
        shadow = null,
        onDrawSurface = {
            drawRect(
                if (dark) {
                    Color(0xFF111318).copy(alpha = 0.74f)
                } else {
                    Color(0xFFF8FAFD).copy(alpha = 0.72f)
                }
            )
            drawRect(
                brush = Brush.radialGradient(
                    colors = if (dark) {
                        listOf(Color.White.copy(alpha = 0.14f), Color.Transparent)
                    } else {
                        listOf(Color.White.copy(alpha = 0.34f), Color.Transparent)
                    },
                    center = center,
                    radius = max(size.width, size.height) * 0.74f
                )
            )
        }
    )
}

@Composable
internal fun GlassMiuixCascadingPopup(
    show: Boolean,
    entries: List<DropdownEntry>,
    onDismissRequest: () -> Unit,
    config: ScheduleConfigEntity,
    backdrop: Backdrop?,
    dropdownColors: DropdownColors,
    host: MiuixCascadingPopupHost = MiuixCascadingPopupHost.Overlay,
    alignment: PopupPositionProvider.Align = PopupPositionProvider.Align.End,
    minWidth: Dp = 176.dp,
    maxHeight: Dp? = 360.dp,
    renderInRootScaffold: Boolean = false,
    collapseOnSelection: Boolean = true
) {
    val transparent = Color.Transparent
    val popupColors = if (!glassUsesLightStyle(config)) {
        darkColorScheme(
            primary = MaterialTheme.colorScheme.primary,
            background = transparent,
            surface = transparent,
            surfaceVariant = transparent,
            surfaceContainer = transparent,
            dividerLine = transparent,
            windowDimming = transparent
        )
    } else {
        lightColorScheme(
            primary = MaterialTheme.colorScheme.primary,
            background = transparent,
            surface = transparent,
            surfaceVariant = transparent,
            surfaceContainer = transparent,
            dividerLine = transparent,
            windowDimming = transparent
        )
    }
    val surfaceModifier = Modifier.miuixCascadingPopupSurface(
        backdrop = backdrop,
        config = config,
        blurRadius = 28.dp
    )
    MiuixTheme(colors = popupColors) {
        when (host) {
            MiuixCascadingPopupHost.Overlay -> OverlayCascadingListPopup(
                show = show,
                entries = entries,
                onDismissRequest = onDismissRequest,
                surfaceModifier = surfaceModifier,
                primarySurfaceModifier = surfaceModifier,
                popupPositionProvider = UpwardDropdownPositionProvider,
                alignment = alignment,
                enableWindowDim = false,
                renderInRootScaffold = renderInRootScaffold,
                minWidth = minWidth,
                maxHeight = maxHeight,
                dropdownColors = dropdownColors,
                collapseOnSelection = collapseOnSelection
            )

            MiuixCascadingPopupHost.Window -> WindowCascadingListPopup(
                show = show,
                entries = entries,
                onDismissRequest = onDismissRequest,
                surfaceModifier = surfaceModifier,
                primarySurfaceModifier = surfaceModifier,
                popupPositionProvider = UpwardDropdownPositionProvider,
                alignment = alignment,
                enableWindowDim = false,
                minWidth = minWidth,
                maxHeight = maxHeight,
                dropdownColors = dropdownColors,
                collapseOnSelection = collapseOnSelection
            )
        }
    }
}
