package com.xiaomanjun.sleepdownschedule.platform.coloros

import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.LiquidAlertActionStyle
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.verticalOrder

internal enum class ColorOsAlertSlot { Positive, Negative, Neutral }

/** Map semantic order onto COUI's neutral → positive → negative vertical positions. */
internal fun colorOsAlertSlots(styles: List<LiquidAlertActionStyle>): List<ColorOsAlertSlot> {
    require(styles.size <= 3)
    if (styles.isEmpty()) return emptyList()
    val order = styles.indices.sortedBy { styles[it].verticalOrder() }
    val positions = if (styles.size == 3) listOf(ColorOsAlertSlot.Neutral, ColorOsAlertSlot.Positive, ColorOsAlertSlot.Negative)
        else listOf(ColorOsAlertSlot.Positive, ColorOsAlertSlot.Negative)
    return styles.indices.map { positions[order.indexOf(it)] }
}
