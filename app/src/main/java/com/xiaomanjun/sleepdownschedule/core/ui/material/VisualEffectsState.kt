package com.xiaomanjun.sleepdownschedule.core.ui.material

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import com.xiaomanjun.sleepdownschedule.model.VisualEffectMode

@Immutable
internal data class VisualEffectsState(
    val selectedMode: VisualEffectMode = VisualEffectMode.LiquidGlass,
    val effectiveMode: VisualEffectMode = VisualEffectMode.LiquidGlass,
    /** COUI-Kit 可创建（Android 14+）；渲染器创建失败由 Dock 路由另行记录。 */
    val couiKitAvailable: Boolean = false,
    val selectMode: (VisualEffectMode) -> Unit = {}
) {
    val glowEnabled: Boolean get() = effectiveMode == VisualEffectMode.ColorOsGlow

    /** 跨品牌 COUI 控件（Dock、FAB、按钮等）共用 Android 14+ 能力门控。 */
    val usesCouiControls: Boolean get() = glowEnabled && couiKitAvailable

    val usesGlowMaterials: Boolean get() = usesCouiControls

    val usesCouiDock: Boolean get() = usesCouiControls
}

internal val LocalVisualEffects = staticCompositionLocalOf { VisualEffectsState() }
