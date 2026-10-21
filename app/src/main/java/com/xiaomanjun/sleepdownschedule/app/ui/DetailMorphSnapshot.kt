package com.xiaomanjun.sleepdownschedule.app.ui

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.WindowManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.unit.IntSize
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.NativeDialogSnapshot
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.NativeDialogSnapshotRegistry
import kotlin.coroutines.resume
import kotlin.math.roundToInt
import kotlinx.coroutines.suspendCancellableCoroutine

internal fun composeDetailMorphSnapshot(underlay: Bitmap, popup: Bitmap): Bitmap? = runCatching {
    val width = underlay.width
    val height = underlay.height
    require(width > 0 && height > 0)
    Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { result ->
        val target = android.graphics.Rect(0, 0, width, height)
        AndroidCanvas(result).apply {
            drawBitmap(underlay, null, target, null)
            drawBitmap(popup, null, target, null)
        }
    }
}.getOrNull()

internal data class DetailMorphWindowSnapshot(
    val bitmap: Bitmap,
    val originInWindow: Offset,
    val dialog: com.xiaomanjun.sleepdownschedule.core.ui.designsystem.NativeDialogSnapshot? = null
)

/**
 * Captures the final pixels of the special schedule-picker scene.
 *
 * That scene is assembled from a nested PickerScene producer, bitmap-backed schedule cards and a
 * root Miuix popup-host sibling. Recording an ancestor GraphicsLayer is still useful as a fallback,
 * but it is not guaranteed to flatten every nested RenderNode on ColorOS. PixelCopy reads the
 * already-composited source window once, before the destination Activity exists.
 */
internal suspend fun captureDetailMorphWindowSnapshot(
    activity: Activity,
    rootPositionInWindow: Offset,
    rootSize: IntSize,
    overlayWindow: android.view.Window? = null
): DetailMorphWindowSnapshot? {
    val decor = activity.window.decorView
    if (decor.width <= 0 || decor.height <= 0 || rootSize.width <= 0 || rootSize.height <= 0) {
        return null
    }
    val left = rootPositionInWindow.x.roundToInt().coerceIn(0, decor.width - 1)
    val top = rootPositionInWindow.y.roundToInt().coerceIn(0, decor.height - 1)
    val right = (rootPositionInWindow.x + rootSize.width)
        .roundToInt()
        .coerceIn(left + 1, decor.width)
    val bottom = (rootPositionInWindow.y + rootSize.height)
        .roundToInt()
        .coerceIn(top + 1, decor.height)
    val sourceRect = android.graphics.Rect(left, top, right, bottom)
    val bitmap = copyDetailWindow(activity.window, sourceRect) ?: return null
    if (overlayWindow != null && overlayWindow !== activity.window) {
        val overlayDecor = overlayWindow.decorView
        if (overlayDecor.width <= 0 || overlayDecor.height <= 0) { bitmap.recycle(); return null }
        val overlay = copyDetailWindow(overlayWindow,
            android.graphics.Rect(0, 0, overlayDecor.width, overlayDecor.height))
        if (overlay == null) { bitmap.recycle(); return null }
        val ownerScreen = IntArray(2).also(decor::getLocationOnScreen)
        val ownerWindow = IntArray(2).also(decor::getLocationInWindow)
        val overlayScreen = IntArray(2).also(overlayDecor::getLocationOnScreen)
        val canvas = android.graphics.Canvas(bitmap)
        if (overlayWindow.attributes.flags and android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND != 0) {
            canvas.drawColor(android.graphics.Color.argb(
                (overlayWindow.attributes.dimAmount.coerceIn(0f, 1f) * 255).roundToInt(), 0, 0, 0))
        }
        val offset = Offset((overlayScreen[0] - ownerScreen[0] + ownerWindow[0] - left).toFloat(),
            (overlayScreen[1] - ownerScreen[1] + ownerWindow[1] - top).toFloat())
        val material = com.xiaomanjun.sleepdownschedule.core.ui.designsystem.NativeDialogSnapshotRegistry.capture(overlayWindow)
        if (material != null) {
            // Keep the copied foreground and the COUI backdrop separate. PixelCopy reads only
            // the window buffer; flattening it here silently drops BackgroundBlurDrawable.
            return DetailMorphWindowSnapshot(bitmap, Offset(left.toFloat(), top.toFloat()),
                com.xiaomanjun.sleepdownschedule.core.ui.designsystem.NativeDialogSnapshot(
                    overlay, Rect(offset, androidx.compose.ui.geometry.Size(overlay.width.toFloat(), overlay.height.toFloat())),
                    material.bounds.translate(offset), material.cornerRadiusPx, material.dark))
        }
        canvas.drawBitmap(overlay,
            (overlayScreen[0] - ownerScreen[0] + ownerWindow[0] - left).toFloat(),
            (overlayScreen[1] - ownerScreen[1] + ownerWindow[1] - top).toFloat(), null)
        overlay.recycle()
    }
    return DetailMorphWindowSnapshot(bitmap, Offset(left.toFloat(), top.toFloat()))
}

private suspend fun copyDetailWindow(window: android.view.Window, sourceRect: android.graphics.Rect): Bitmap? {
    val bitmap = Bitmap.createBitmap(sourceRect.width(), sourceRect.height(), Bitmap.Config.ARGB_8888)
    return suspendCancellableCoroutine { continuation ->
        runCatching {
            PixelCopy.request(
                window,
                sourceRect,
                bitmap,
                { result ->
                    if (!continuation.isActive) {
                        bitmap.recycle()
                    } else if (result == PixelCopy.SUCCESS) {
                        continuation.resume(bitmap)
                    } else {
                        bitmap.recycle()
                        continuation.resume(null)
                    }
                },
                Handler(Looper.getMainLooper())
            )
        }.onFailure {
            bitmap.recycle()
            if (continuation.isActive) continuation.resume(null)
        }
    }
}

internal fun Bitmap.cropToAnchoredBounds(bounds: Rect): Bitmap? = runCatching {
    val left = bounds.left.roundToInt().coerceIn(0, width - 1)
    val top = bounds.top.roundToInt().coerceIn(0, height - 1)
    val cropWidth = bounds.width.roundToInt().coerceIn(1, width - left)
    val cropHeight = bounds.height.roundToInt().coerceIn(1, height - top)
    Bitmap.createBitmap(this, left, top, cropWidth, cropHeight)
}.getOrNull()
