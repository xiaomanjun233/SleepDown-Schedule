package com.xiaomanjun.sleepdownschedule.platform.coloros

import android.os.Build
import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.ControlMaterialRenderers
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.NativeCapsuleButtonRequest
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.NativeSliderRequest
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.NativeSwitchRequest
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.NativeAlertRequest
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.NativeSegmentRequest
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.PickerDialogRequest
import com.xiaomanjun.sleepdownschedule.core.ui.material.HomeMaterialControls
import androidx.compose.runtime.remember
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.ProvideControlMaterials

private val ColorOsRenderers = ControlMaterialRenderers(
    home = ColorOsHomeMaterialControls,
    alert = { ColorOsAlertDialog(it) },
    button = { request, content -> ColorOsCapsuleButton(request, content) },
    followLight = { ColorOsFollowLight(it) },
    courseCard = { request, content -> ColorOsCourseCard(request, content) },
    surface = { request, content -> ColorOsMaterialSurface(request, content) },
    strokeOverlay = { request -> ColorOsCardOuterStroke(request) },
    cardLight = { request -> ColorOsCardLight(request) },
    pageIndicator = { request -> ColorOsPagerIndicator(request) },
    slider = { ColorOsSlider(it) },
    switch = { ColorOsSwitch(it) },
    segments = { ColorOsSegments(it) },
    menuRow = { ColorOsMenuRow(it) },
    menuList = { ColorOsMenuList(it) },
    menuSurface = { request, content -> ColorOsMenuSurface(request, content) }
)

@Composable
internal fun ProvideColorOsMaterials(
    enabled: Boolean,
    couiControlsEnabled: Boolean = false,
    content: @Composable () -> Unit,
) {
    val baseHome = if (enabled) ColorOsHomeMaterialControls else ControlMaterialRenderers.LiquidGlass.home
    val homeControls = remember(baseHome, couiControlsEnabled, enabled) {
        if (!couiControlsEnabled) baseHome else object : HomeMaterialControls by baseHome {
            @Composable override fun IconButton(iconRes: Int, contentDescription: String, dark: Boolean,
                interactive: Boolean, selected: Boolean, modifier: androidx.compose.ui.Modifier,
                onClick: () -> Unit, toolbarNavigation: Boolean, enabled: Boolean): Boolean {
                if (Build.VERSION.SDK_INT >= 34 && LocalCouiHomeTopBarActive.current) {
                    CouiHomeIconButton(iconRes, contentDescription, dark, interactive, selected, modifier,
                        onClick, enabled)
                    return true
                }
                return baseHome.IconButton(iconRes, contentDescription, dark, interactive, selected, modifier,
                    onClick, toolbarNavigation, enabled)
            }
            @Composable override fun ShortcutMenu(session: Any, bounds: androidx.compose.ui.geometry.Rect,
                dark: Boolean, actions: List<com.xiaomanjun.sleepdownschedule.core.ui.designsystem.AddMenuAction>,
                closing: Boolean, onDismiss: () -> Unit, bindDismiss: (((() -> Unit) -> Unit)?) -> Unit): Boolean {
                GlowCourseShortcutMenu(session, bounds, dark, actions, closing, onDismiss, bindDismiss)
                return true
            }
            @Composable override fun OverflowMenuButton(dark: Boolean, interactive: Boolean,
                actions: List<com.xiaomanjun.sleepdownschedule.core.ui.designsystem.AddMenuAction>,
                modifier: androidx.compose.ui.Modifier, content: @Composable (onClick: () -> Unit) -> Unit): Boolean {
                GlowOverflowMenuButton(dark, interactive, actions, modifier, content)
                return true
            }
            @Composable override fun NameDialog(title: String, initialName: String, dark: Boolean, maxLength: Int?,
                onDismiss: () -> Unit, onConfirm: (String) -> Unit, onDraftChange: (String) -> Unit): Boolean =
                CouiNameDialog(title, initialName, dark, maxLength, onDismiss, onConfirm, onDraftChange) ||
                    (enabled && ColorOsNameDialog(title, initialName, dark, maxLength, onDismiss, onConfirm, onDraftChange))
        }
    }
    val alertRenderer: (@Composable (NativeAlertRequest) -> Boolean)? = when {
        couiControlsEnabled -> { request -> CouiAlertDialog(request) || (enabled && ColorOsAlertDialog(request)) }
        enabled -> { request -> ColorOsAlertDialog(request) }
        else -> null
    }
    val pickerRenderer: (@Composable (PickerDialogRequest) -> Boolean)? =
        if (couiControlsEnabled) { request -> CouiPickerDialog(request) } else null
    val segmentRenderer: (@Composable (NativeSegmentRequest) -> Boolean)? = when {
        couiControlsEnabled -> { request -> CouiSegments(request) || (enabled && ColorOsSegments(request)) }
        enabled -> { request -> ColorOsSegments(request) }
        else -> null
    }
    val buttonRenderer: (@Composable (NativeCapsuleButtonRequest, @Composable RowScope.() -> Unit) -> Boolean)? =
        when {
            couiControlsEnabled -> { request, buttonContent ->
                if (CouiCapsuleButtonRenderer(request, buttonContent)) true
                else if (enabled) ColorOsCapsuleButton(request, buttonContent) else false
            }
            enabled -> { request, buttonContent -> ColorOsCapsuleButton(request, buttonContent) }
            else -> null
        }
    val sliderRenderer: (@Composable (NativeSliderRequest) -> Boolean)? = when {
        couiControlsEnabled -> { request ->
            if (CouiValueSlider(request)) true else if (enabled) ColorOsSlider(request) else false
        }
        enabled -> { request -> ColorOsSlider(request) }
        else -> null
    }
    val switchRenderer: (@Composable (NativeSwitchRequest) -> Boolean)? = when {
        couiControlsEnabled -> { request ->
            if (CouiValueSwitch(request)) true else if (enabled) ColorOsSwitch(request) else false
        }
        enabled -> { request -> ColorOsSwitch(request) }
        else -> null
    }
    val renderers = if (enabled) {
        ColorOsRenderers.copy(
            home = homeControls, alert = alertRenderer, pickerDialog = pickerRenderer, segments = segmentRenderer,
            button = buttonRenderer,
            slider = sliderRenderer,
            switch = switchRenderer,
        )
    } else {
        // Liquid glass still uses the native outer stroke on capable devices; every other native
        // capability stays disabled.
        ControlMaterialRenderers.LiquidGlass.copy(
            home = homeControls, alert = alertRenderer, pickerDialog = pickerRenderer, segments = segmentRenderer,
            button = buttonRenderer,
            slider = sliderRenderer,
            switch = switchRenderer,
            strokeOverlay = ColorOsRenderers.strokeOverlay,
            pageIndicator = ColorOsRenderers.pageIndicator,
        )
    }
    ProvideControlMaterials(
        renderers = renderers,
        courseCardMode = if (enabled) rememberColorOsCourseCardMode() else null,
        progressiveBlur = if (enabled) rememberColorOsProgressiveBlur() else null,
        content = content
    )
}
