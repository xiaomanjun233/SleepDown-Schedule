package com.xiaomanjun.sleepdownschedule.platform.coloros

import androidx.compose.runtime.Composable
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.ControlMaterialRenderers
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
internal fun ProvideColorOsMaterials(enabled: Boolean, content: @Composable () -> Unit) {
    val renderers = if (enabled) {
        ColorOsRenderers
    } else {
        // Liquid glass still uses the native outer stroke on capable devices; every other native
        // capability stays disabled.
        ControlMaterialRenderers.LiquidGlass.copy(strokeOverlay = ColorOsRenderers.strokeOverlay,
            pageIndicator = ColorOsRenderers.pageIndicator)
    }
    ProvideControlMaterials(
        renderers = renderers,
        courseCardMode = if (enabled) rememberColorOsCourseCardMode() else null,
        progressiveBlur = if (enabled) rememberColorOsProgressiveBlur() else null,
        content = content
    )
}
