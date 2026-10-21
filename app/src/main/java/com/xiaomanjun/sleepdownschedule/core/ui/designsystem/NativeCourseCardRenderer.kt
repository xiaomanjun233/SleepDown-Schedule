package com.xiaomanjun.sleepdownschedule.core.ui.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf

/** Vendor implementation is supplied by app assembly, keeping glass independent of features. */
internal typealias NativeCourseCardRequest = NativeMaterialSurfaceRequest

@Stable
internal class NativeCourseCardMode(
    private val enabledState: State<Boolean>,
    val onEnabledChange: (Boolean) -> Unit,
    private val tintState: State<Float>,
    private val blurScaleState: State<Float>,
    private val defaultBlurRadiusPx: Float,
    val onTintStrengthChange: (Float, Boolean) -> Unit,
    val onBlurRadiusScaleChange: (Float, Boolean) -> Unit,
    val onPreviewFinished: () -> Unit
) {
    val enabled: Boolean get() = enabledState.value
    val tintStrength: Float get() = tintState.value
    val blurRadiusScale: Float get() = blurScaleState.value.coerceIn(0.2f, 2f)
    val blurRadiusPx: Float get() = defaultBlurRadiusPx * blurRadiusScale
    val blurPercent: Float get() = nativeCardBlurPercent(blurRadiusScale)
    fun onBlurPercentChange(percent: Float, commit: Boolean) =
        onBlurRadiusScaleChange(nativeCardBlurScale(percent), commit)
}

/** Keep 50% as the system default while reserving nonzero material blur at 0%. */
internal fun nativeCardBlurScale(percent: Float): Float = percent.coerceIn(0f, 100f).let {
    if (it <= 50f) 0.2f + it / 50f * 0.8f else 1f + (it - 50f) / 50f
}

internal fun nativeCardBlurPercent(scale: Float): Float = scale.coerceIn(0.2f, 2f).let {
    if (it <= 1f) (it - 0.2f) / 0.8f * 50f else 50f + (it - 1f) * 50f
}

internal val LocalNativeCourseCardMode = compositionLocalOf<NativeCourseCardMode?> { null }
internal val LocalNativeCourseCardRenderer =
    compositionLocalOf<(@Composable (NativeCourseCardRequest, @Composable () -> Unit) -> Boolean)?> { null }
