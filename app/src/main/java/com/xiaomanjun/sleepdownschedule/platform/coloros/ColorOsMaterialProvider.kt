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
    slider = { ColorOsSlider(it) },
    switch = { ColorOsSwitch(it) },
    segments = { ColorOsSegments(it) },
    menuRow = { ColorOsMenuRow(it) },
    menuList = { ColorOsMenuList(it) },
    menuSurface = { request, content -> ColorOsMenuSurface(request, content) }
)

@Composable
internal fun ProvideColorOsMaterials(enabled: Boolean, content: @Composable () -> Unit) {
    ProvideControlMaterials(
        renderers = if (enabled) ColorOsRenderers else ControlMaterialRenderers.LiquidGlass,
        courseCardMode = if (enabled) rememberColorOsCourseCardMode() else null,
        progressiveBlur = if (enabled) rememberColorOsProgressiveBlur() else null,
        content = content
    )
}
