package com.itos.coui.menu

import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupPositionProvider

/** 保留原 AndroidX Below 定位的候选顺序和安全区；触点纵向避让仍由 motion provider 处理。 */
internal class CouiMenuPositionProvider(
    offset: DpOffset,
    density: Density,
    private val placement: CouiMenuPlacement = CouiMenuPlacement.Default,
    private val visibleBounds: () -> IntRect? = { null },
) : PopupPositionProvider {
    private val offsetX = with(density) { offset.x.roundToPx() }
    private val offsetY = with(density) { offset.y.roundToPx() }
    private val horizontalMargin = with(density) { 8.dp.roundToPx() }
    private val verticalMargin = with(density) { 48.dp.roundToPx() }
    private val actionHorizontalMargin = with(density) { 16.dp.roundToPx() }
    private val actionTopMargin = with(density) { 16.dp.roundToPx() }
    private val actionBottomMargin = with(density) { 32.dp.roundToPx() }

    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize,
        layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
        if (placement != CouiMenuPlacement.Default) {
            val visible = visibleBounds() ?: IntRect(IntOffset.Zero, windowSize)
            val left = visible.left + actionHorizontalMargin
            val right = (visible.right - actionHorizontalMargin).coerceAtLeast(left)
            val top = visible.top + actionTopMargin
            val bottom = (visible.bottom - actionBottomMargin).coerceAtLeast(top)
            val width = popupContentSize.width
            val height = popupContentSize.height
            val rtl = layoutDirection == LayoutDirection.Rtl
            val x = when (placement) {
                CouiMenuPlacement.AnchorStart -> if (rtl) anchorBounds.right - width else anchorBounds.left
                CouiMenuPlacement.AnchorEnd -> if (rtl) anchorBounds.left else anchorBounds.right - width
                else -> anchorBounds.center.x - width / 2
            } + if (rtl) -offsetX else offsetX
            val below = maxOf(anchorBounds.bottom + offsetY, top)
            val above = minOf(anchorBounds.top - offsetY, bottom) - height
            // PopupMenuLocateHelper: 先下后上，两侧都容不下时留在可用区顶部。
            val y = when {
                below + height <= bottom -> below
                above >= top -> above
                else -> top
            }
            return IntOffset(x.coerceIn(left, (right - width).coerceAtLeast(left)), y)
        }
        val rtl = layoutDirection == LayoutDirection.Rtl
        val width = popupContentSize.width
        val height = popupContentSize.height
        val xCandidates = intArrayOf(
            if (rtl) anchorBounds.right - width else anchorBounds.left,
            if (rtl) anchorBounds.left else anchorBounds.right - width,
            if (anchorBounds.center.x < windowSize.width / 2) 0 else windowSize.width - width,
        )
        val yCandidates = intArrayOf(
            anchorBounds.bottom,
            anchorBounds.top - height,
            anchorBounds.top - Alignment.CenterVertically.align(0, height),
            if (anchorBounds.center.y < windowSize.height / 2) 0 else windowSize.height - height,
        )
        return IntOffset(
            position(xCandidates, offsetX * if (rtl) -1 else 1, width, windowSize.width, horizontalMargin),
            position(yCandidates, offsetY, height, windowSize.height, verticalMargin),
        )
    }

    private fun position(candidates: IntArray, offset: Int, size: Int, window: Int, margin: Int): Int {
        for (candidate in candidates) {
            val value = candidate + offset
            if (value >= margin && value + size <= window - margin) return value
        }
        return if (size >= window - 2 * margin) Alignment.CenterVertically.align(size, window)
        else (candidates.last() + offset).coerceIn(margin, window - margin - size)
    }
}
