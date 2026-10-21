package com.xiaomanjun.sleepdownschedule.core.ui.controls

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.catalog.components.LiquidSlider
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.LocalNativeSliderRenderer
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.NativeSliderRequest
import kotlin.math.abs
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Composable
fun LiquidControlSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    backdrop: Backdrop?,
    modifier: Modifier = Modifier,
    onLiveValueChange: ((Float) -> Unit)? = null,
    snapValue: Float? = null,
    onSliderTouchActiveChange: (Boolean) -> Unit = {},
    visibilityThreshold: Float = 0.01f,
    enabled: Boolean = true,
    compactThumb: Boolean = false
) {
    val haptic = LocalHapticFeedback.current
    val previewDispatchScope = rememberCoroutineScope()
    val latestLiveValueChange by rememberUpdatedState(onLiveValueChange)
    var pendingLiveValue by remember { mutableStateOf<Float?>(null) }
    var previewDispatchJob by remember { mutableStateOf<Job?>(null) }
    val safeSnapValue = snapValue?.coerceIn(valueRange)
    var localValue by remember(valueRange) { mutableFloatStateOf(value.coerceIn(valueRange)) }
    var previewModeActive by remember(valueRange) { mutableStateOf(false) }
    var lastSnapSide by remember(valueRange, safeSnapValue) {
        mutableIntStateOf(snapSide(value.coerceIn(valueRange), safeSnapValue, visibilityThreshold))
    }
    var snapZoneActive by remember(valueRange, safeSnapValue) {
        mutableStateOf(lastSnapSide == 0 && safeSnapValue != null)
    }

    fun updatePreview(candidate: Float) {
        val next = candidate.coerceIn(valueRange)
        val nextSide = snapSide(next, safeSnapValue, visibilityThreshold)
        val enteredSnapZone = safeSnapValue != null && nextSide == 0 && !snapZoneActive
        val crossedSnap = nextSide != 0 && !snapZoneActive &&
            lastSnapSide != 0 && nextSide != lastSnapSide
        if (enteredSnapZone || crossedSnap) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
        snapZoneActive = safeSnapValue != null && nextSide == 0
        if (nextSide != 0) lastSnapSide = nextSide
        localValue = next
        pendingLiveValue = next
        if (previewDispatchJob?.isActive != true) {
            previewDispatchJob = previewDispatchScope.launch {
                withFrameNanos { }
                val latest = pendingLiveValue
                pendingLiveValue = null
                previewDispatchJob = null
                if (latest != null) latestLiveValueChange?.invoke(latest)
            }
        }
    }

    fun commit(candidate: Float) {
        val finalValue = candidate.coerceIn(valueRange)
        localValue = finalValue
        previewDispatchJob?.cancel()
        previewDispatchJob = null
        pendingLiveValue = null
        // The exact release value is never delayed behind frame conflation.
        latestLiveValueChange?.invoke(finalValue)
        onValueChange(finalValue)
    }

    LaunchedEffect(value, valueRange) {
        val externalValue = value.coerceIn(valueRange)
        if (!previewModeActive && abs(localValue - externalValue) > visibilityThreshold) {
            localValue = externalValue
            lastSnapSide = snapSide(externalValue, safeSnapValue, visibilityThreshold)
            snapZoneActive = safeSnapValue != null && lastSnapSide == 0
        }
    }

    val nativeSlider = com.xiaomanjun.sleepdownschedule.core.ui.designsystem.LocalNativeSliderRenderer.current
    if (nativeSlider?.invoke(
            com.xiaomanjun.sleepdownschedule.core.ui.designsystem.NativeSliderRequest(
                value = { localValue }, range = valueRange, onPreview = ::updatePreview,
                onPreviewMode = { active ->
                    previewModeActive = active
                    onSliderTouchActiveChange(active)
                }, onCommit = ::commit, modifier = modifier,
                snapValue = safeSnapValue, enabled = enabled
            )) == true) return

    // Native renderers own marker geometry as well as the track and thumb.
    SliderWithSnapMarker(
        modifier = modifier,
        snapValue = safeSnapValue,
        valueRange = valueRange
    ) {
        if (backdrop != null && enabled) {
            LiquidSlider(
                value = { localValue },
                onPreviewValueChange = ::updatePreview,
                onPreviewModeChange = { active ->
                    previewModeActive = active
                    onSliderTouchActiveChange(active)
                },
                onCommit = ::commit,
                valueRange = valueRange,
                visibilityThreshold = visibilityThreshold,
                backdrop = backdrop,
                snapValue = safeSnapValue,
                compactThumb = compactThumb,
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            Slider(
                value = localValue,
                enabled = enabled,
                onValueChange = {
                    if (!previewModeActive) {
                        previewModeActive = true
                        onSliderTouchActiveChange(true)
                    }
                    updatePreview(it)
                },
                onValueChangeFinished = {
                    commit(localValue)
                    if (previewModeActive) {
                        previewModeActive = false
                        onSliderTouchActiveChange(false)
                    }
                },
                valueRange = valueRange,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

private fun snapSide(candidate: Float, snapValue: Float?, threshold: Float): Int {
    val snap = snapValue ?: return 0
    return when {
        candidate < snap - threshold -> -1
        candidate > snap + threshold -> 1
        else -> 0
    }
}

@Composable
private fun SliderWithSnapMarker(
    modifier: Modifier,
    snapValue: Float?,
    valueRange: ClosedFloatingPointRange<Float>,
    content: @Composable () -> Unit
) {
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        content()
        val snap = snapValue ?: return@BoxWithConstraints
        val fraction = ((snap - valueRange.start) /
            (valueRange.endInclusive - valueRange.start)).coerceIn(0f, 1f)
        Canvas(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxWidth()
                .height(36.dp)
        ) {
            val radius = 2.5.dp.toPx()
            val visualFraction = if (isLtr) fraction else 1f - fraction
            val centerX = radius +
                (size.width - radius * 2f).coerceAtLeast(0f) * visualFraction
            drawCircle(
                color = ComposeColor.White.copy(alpha = 0.78f),
                radius = radius,
                center = Offset(centerX, size.height / 2f)
            )
        }
    }
}
