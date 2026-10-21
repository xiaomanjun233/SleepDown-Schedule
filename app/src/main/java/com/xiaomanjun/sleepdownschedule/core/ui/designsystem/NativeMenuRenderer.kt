package com.xiaomanjun.sleepdownschedule.core.ui.designsystem

import android.view.MotionEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

/** Menu data and body rendering only. The caller keeps positioning, transitions and navigation. */
internal data class NativeMenuItem(
    val title: String,
    val iconRes: Int? = null,
    val description: String? = null,
    val enabled: Boolean = true,
    val selected: Boolean = false,
    val checkable: Boolean = false,
    val foreground: Int? = null,
    val iconTint: Int? = null,
    val children: List<NativeMenuItem> = emptyList(),
    val onClick: () -> Unit = {}
)

internal data class NativeMenuRowRequest(
    val item: NativeMenuItem,
    val dark: Boolean,
    val modifier: Modifier,
    val pressed: Boolean = false,
    val first: Boolean = false,
    val last: Boolean = false,
    val contentVisible: Boolean = true,
    val indicatorRotation: () -> Float = { 0f }
)

internal data class NativeMenuSurfaceRequest(
    val surface: NativeMaterialSurfaceRequest,
    val neutral: Boolean = false,
    val touchSource: NativeMenuTouchSource? = null,
    val motion: NativeMenuMotion? = null
)

/** Surface and labels read one native collector in their draw layers, without recomposition. */
internal class NativeMenuMotion {
    private data class Transform(val x: Float = 0f, val y: Float = 0f, val sx: Float = 1f, val sy: Float = 1f)
    private var transform by mutableStateOf(Transform())
    var onTouch: ((MotionEvent) -> Unit)? = null
    fun update(x: Float, y: Float, sx: Float, sy: Float) { transform = Transform(x, y, sx, sy) }
    fun applyTo(layer: androidx.compose.ui.graphics.GraphicsLayerScope) {
        val value = transform
        layer.translationX = value.x; layer.translationY = value.y
        layer.scaleX = value.sx; layer.scaleY = value.sy
    }
}

/** A popup draws its surface and rows as siblings, so its common host forwards touch here. */
internal class NativeMenuTouchSource {
    var onTouch: ((MotionEvent) -> Unit)? = null
    fun dispatch(event: MotionEvent) { onTouch?.invoke(event) }
}

internal val LocalNativeMenuRowRenderer =
    compositionLocalOf<(@Composable (NativeMenuRowRequest) -> Boolean)?> { null }

internal data class NativeMenuListRequest(
    val items: List<NativeMenuItem>, val dark: Boolean, val modifier: Modifier,
    val enabled: Boolean,
    val rowHeight: androidx.compose.ui.unit.Dp,
    val rowGap: androidx.compose.ui.unit.Dp,
    val externalHighlightedIndex: Int = -1,
    val rowContent: @Composable (Int) -> Unit
)
internal val LocalNativeMenuListRenderer =
    compositionLocalOf<(@Composable (NativeMenuListRequest) -> Boolean)?> { null }
internal val LocalNativeMenuSurfaceRenderer =
    compositionLocalOf<(@Composable (NativeMenuSurfaceRequest, @Composable () -> Unit) -> Boolean)?> { null }
