package com.xiaomanjun.sleepdownschedule.core.ui.material

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import com.xiaomanjun.sleepdownschedule.model.VisualEffectMode

@Immutable
internal data class VisualEffectsState(
    val selectedMode: VisualEffectMode = VisualEffectMode.LiquidGlass,
    val effectiveMode: VisualEffectMode = VisualEffectMode.LiquidGlass,
    /** 私有 ColorOS 系统控件可用；与凝光偏好相互独立。 */
    val colorOsAvailable: Boolean = false,
    /** COUI-Kit 可创建（Android 14+）；渲染器创建失败由 Dock 路由另行记录。 */
    val couiKitAvailable: Boolean = false,
    val selectMode: (VisualEffectMode) -> Unit = {}
) {
    val glowEnabled: Boolean get() = effectiveMode == VisualEffectMode.ColorOsGlow

    /** 未迁移控件继续走原有能力分发，只在私有系统控件可用时启用。 */
    val usesNativeControls: Boolean get() = glowEnabled && colorOsAvailable

    val usesCouiDock: Boolean get() = glowEnabled && couiKitAvailable
}

internal val LocalVisualEffects = staticCompositionLocalOf { VisualEffectsState() }
