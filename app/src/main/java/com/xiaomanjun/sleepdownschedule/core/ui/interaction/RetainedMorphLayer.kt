package com.xiaomanjun.sleepdownschedule.core.ui.interaction

import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt

/** Returning visuals stay above the page; their hit-test tree goes behind it. The same GPU
 * display list and live content survive a reversal, without a bitmap readback or a second form. */
@Composable
internal fun RetainedMorphLayer(
    returning: Boolean, zIndex: Float, moving: Boolean = true,
    directVisual: Boolean = false,
    returnAnchor: Rect? = null, onReturnAnchorTap: () -> Unit = {},
    content: @Composable (Modifier) -> Unit
) {
    if (directVisual) {
        // Liquid shells already draw over the shared blurred underlay. Record/replay can lose
        // their producer/highlight pass; keep the one live shell and route movement taps above.
        content(Modifier.zIndex(zIndex))
        return
    }
    val layer = rememberGraphicsLayer()
    content(Modifier.zIndex(if (returning) -1f else zIndex).drawWithContent {
        if (!moving) drawContent() else {
            layer.record { this@drawWithContent.drawContent() }
            if (!returning) drawLayer(layer)
        }
    })
    if (returning) Box(Modifier.fillMaxSize().zIndex(zIndex - 0.5f).drawWithContent { drawLayer(layer) })
    // A return is still interruptible at its original station, even while the visual is elsewhere.
    if (returning && returnAnchor != null && returnAnchor.width > 0f && returnAnchor.height > 0f) {
        val density = LocalDensity.current
        Box(Modifier
            .offset { IntOffset(returnAnchor.left.roundToInt(), returnAnchor.top.roundToInt()) }
            .requiredSize(with(density) { returnAnchor.width.toDp() }, with(density) { returnAnchor.height.toDp() })
            .zIndex(zIndex - 0.25f)
            .pointerInput(onReturnAnchorTap) { detectTapGestures { onReturnAnchorTap() } })
    }
}
