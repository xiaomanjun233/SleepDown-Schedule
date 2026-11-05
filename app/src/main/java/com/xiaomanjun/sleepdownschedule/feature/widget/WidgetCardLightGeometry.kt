package com.xiaomanjun.sleepdownschedule.feature.widget

import androidx.compose.ui.geometry.Rect

/** 投光止于实际操作行下沿；不同组件比例、窗口和字体尺寸不共用固定深度。 */
internal fun widgetLightProjectionDepth(card: Rect, receiver: Rect, trailingFade: Float): Float {
    if (card.isEmpty || receiver.isEmpty) return 0f
    return (receiver.bottom - card.bottom + trailingFade).coerceAtLeast(0f)
}
