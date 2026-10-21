package com.xiaomanjun.sleepdownschedule.core.ui.controls

import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.catalog.components.LiquidToggle
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.LocalNativeSwitchRenderer
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.NativeSwitchRequest

@Composable
fun LiquidControlToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    backdrop: Backdrop?,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    if (com.xiaomanjun.sleepdownschedule.core.ui.designsystem.LocalNativeSwitchRenderer.current?.invoke(
            com.xiaomanjun.sleepdownschedule.core.ui.designsystem.NativeSwitchRequest(
                checked, onCheckedChange, modifier, enabled, compact
            )) == true) return
    val alpha = if (enabled) 1f else 0.40f
    if (backdrop != null) {
        LiquidToggle(
            selected = { checked },
            onSelect = { if (enabled) onCheckedChange(it) },
            backdrop = backdrop,
            compact = compact,
            enabled = enabled,
            modifier = modifier.graphicsLayer { this.alpha = alpha }
        )
    } else {
        Switch(
            checked = checked,
            onCheckedChange = { if (enabled) onCheckedChange(it) },
            modifier = modifier.graphicsLayer {
                this.alpha = alpha
                scaleX = if (compact) 0.8f else 1f
                scaleY = if (compact) 0.8f else 1f
            }
        )
    }
}
