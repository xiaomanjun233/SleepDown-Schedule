package com.itos.coui.backdrop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.neverEqualPolicy
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.unit.IntSize

/**
 * LayoutCoordinates 本身会原地变化。保存实际采样变换，只有几何变化才通知绘制；
 * 相邻控件重新布局也会派发 onGloballyPositioned，不能因此重建所有材质的 Shader 图。
 */
class CouiSamplingCoordinates {
    var coordinates: LayoutCoordinates? by mutableStateOf(null, neverEqualPolicy())
        private set
    private var size = IntSize.Zero
    private var rootSize = IntSize.Zero
    private var origin = Offset.Unspecified
    private var right = Offset.Unspecified
    private var bottom = Offset.Unspecified

    fun update(value: LayoutCoordinates?) {
        if (value == null || !value.isAttached) {
            if (coordinates != null) coordinates = null
            return
        }
        val nextSize = value.size
        val nextRootSize = value.findRootCoordinates().size
        val nextOrigin = value.localToScreen(Offset.Zero)
        val nextRight = value.localToScreen(Offset(nextSize.width.toFloat(), 0f))
        val nextBottom = value.localToScreen(Offset(0f, nextSize.height.toFloat()))
        if (coordinates !== value || size != nextSize || rootSize != nextRootSize ||
            origin != nextOrigin || right != nextRight || bottom != nextBottom) {
            size = nextSize
            rootSize = nextRootSize
            origin = nextOrigin
            right = nextRight
            bottom = nextBottom
            coordinates = value
        }
    }
}
