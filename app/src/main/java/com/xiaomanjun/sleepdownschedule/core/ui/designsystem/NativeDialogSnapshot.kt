package com.xiaomanjun.sleepdownschedule.core.ui.designsystem

import android.graphics.Bitmap
import android.view.Window
import androidx.compose.ui.geometry.Rect

/** Window PixelCopy omits the compositor's backdrop. Preserve that layer separately. */
data class NativeDialogSnapshot(
    val foreground: Bitmap,
    val windowBounds: Rect,
    val materialBounds: Rect,
    val cornerRadiusPx: Float,
    val dark: Boolean
)

internal data class NativeDialogMaterialFrame(val bounds: Rect, val cornerRadiusPx: Float, val dark: Boolean)

/** Only live app-owned dialogs register; capture reads their current coordinates after IME resize. */
internal object NativeDialogSnapshotRegistry {
    private val windows = mutableMapOf<Window, () -> NativeDialogMaterialFrame?>()
    fun register(window: Window, capture: () -> NativeDialogMaterialFrame?) { windows[window] = capture }
    fun capture(window: Window): NativeDialogMaterialFrame? = windows[window]?.invoke()
    fun remove(window: Window) { windows.remove(window) }
}
