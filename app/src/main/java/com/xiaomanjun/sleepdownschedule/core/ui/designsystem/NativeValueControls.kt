package com.xiaomanjun.sleepdownschedule.core.ui.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.roundToInt

internal data class NativeSliderRequest(
    val value: () -> Float, val range: ClosedFloatingPointRange<Float>,
    val onPreview: (Float) -> Unit, val onPreviewMode: (Boolean) -> Unit,
    val onCommit: (Float) -> Unit, val modifier: Modifier,
    val snapValue: Float? = null, val enabled: Boolean = true
)

internal data class NativeSwitchRequest(
    val checked: Boolean, val onCheckedChange: (Boolean) -> Unit,
    val modifier: Modifier, val enabled: Boolean = true, val compact: Boolean = false
)

internal data class NativeSegmentRequest(
    val labels: List<String>, val selected: Int, val onSelected: (Int) -> Unit,
    val modifier: Modifier, val dark: Boolean,
    val height: Dp = 42.dp,
    val enabled: Boolean = true,
    // Composite navigation owns its shell, light and deformation; this draws tabs/indicator.
    val containerSurfaceEnabled: Boolean = true,
    // Navigation must not disable the native rail by switching pages halfway through a drag.
    val commitOnRelease: Boolean = false
)

internal val LocalNativeSliderRenderer = compositionLocalOf<(@Composable (NativeSliderRequest) -> Boolean)?> { null }
internal val LocalNativeSwitchRenderer = compositionLocalOf<(@Composable (NativeSwitchRequest) -> Boolean)?> { null }
internal val LocalNativeSegmentRenderer = compositionLocalOf<(@Composable (NativeSegmentRequest) -> Boolean)?> { null }

/** Keep the native thumb and preview on the same value; ignore setProgress callback echoes. */
internal class NativeSliderProgressAdapter(
    private val request: () -> NativeSliderRequest,
    private val session: NativeSliderSession,
    private val setProgress: (Int) -> Unit
) {
    fun progressFor(value: Float): Int {
        val range = request().range
        val span = range.endInclusive - range.start
        return if (span > 0f) ((value - range.start) / span * MaxProgress).roundToInt().coerceIn(0, MaxProgress) else 0
    }

    fun changed(progress: Int, fromUser: Boolean, snapToDefault: Boolean = true) {
        if (!fromUser) return
        val current = request()
        val span = current.range.endInclusive - current.range.start
        var value = current.range.start + progress.coerceIn(0, MaxProgress) / MaxProgress.toFloat() * span
        val snap = current.snapValue
        // Keyboard/accessibility steps must be able to leave the default one step at a time.
        if (snapToDefault && session.tracking && snap != null && snap in current.range && abs(value - snap) < span * 0.015f) {
            value = snap
            val target = progressFor(snap)
            if (target != progress) setProgress(target)
        }
        session.change(value)
    }

    companion object { const val MaxProgress = 10000 }
}

/** Native touch callbacks retain the existing preview/commit/cancel transaction. */
internal class NativeSliderSession(private val request: () -> NativeSliderRequest) {
    var tracking = false
        private set
    private var initial = 0f
    private var pending = 0f
    fun start() {
        if (tracking) return
        tracking = true
        initial = request().value().coerceIn(request().range)
        pending = initial
        request().onPreviewMode(true)
    }
    fun change(value: Float) {
        val current = request()
        pending = value.coerceIn(current.range)
        current.onPreview(pending)
        // Keyboard and accessibility changes do not start a touch tracking session.
        if (!tracking) current.onCommit(pending)
    }
    fun finish(cancelled: Boolean) {
        if (!tracking) return
        tracking = false
        if (cancelled) request().onPreview(initial) else request().onCommit(pending)
        request().onPreviewMode(false)
    }
}
