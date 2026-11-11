package com.xiaomanjun.sleepdownschedule.platform.coloros

import androidx.compose.ui.geometry.Rect

internal fun cardLightDrawingBounds(card: Rect, receiver: Rect?, depth: Float, viewport: Rect? = null): Rect {
    val target = receiver?.takeUnless { it.isEmpty }
    val drawing = if (target == null) Rect(
        card.center.x - card.width * .6f, card.bottom,
        card.center.x + card.width * .6f, card.bottom + depth)
    else {
        val halfWidth = maxOf(card.width / 2f, card.center.x - target.left, target.right - card.center.x)
        val extent = halfWidth / .7f
        Rect(card.center.x - extent, card.bottom, card.center.x + extent, card.bottom + depth)
    }
    // 渐隐必须在实际窗口内结束，不能把透明边缘放到屏幕外再依赖父级硬裁切。
    return viewport?.let { drawing.intersect(it) } ?: drawing
}

internal fun cardLightHorizontalScale(cardWidth: Float, projectedCardWidth: Float,
    textureWidth: Float, drawingWidth: Float): Float = maxOf(
    if (projectedCardWidth > 0f) cardWidth / projectedCardWidth else 1f,
    if (textureWidth > 0f) drawingWidth / textureWidth else 1f)
