package com.xiaomanjun.sleepdownschedule.feature.home.personalization

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.xiaomanjun.sleepdownschedule.core.ui.controls.LiquidControlSlider
import com.xiaomanjun.sleepdownschedule.core.wallpaper.wallpaperBlurPercent
import kotlin.math.roundToInt

internal fun Modifier.personalizePreviewVisibility(
    activeKey: String?,
    previewProgress: Float,
    ownKey: String? = null
): Modifier {
    val selectedSlider = activeKey != null && activeKey == ownKey
    val effectiveAlpha = if (selectedSlider) 1f else 1f - previewProgress.coerceIn(0f, 1f)
    return graphicsLayer { alpha = effectiveAlpha }
        .drawWithContent {
            // Alpha alone does not guarantee nested Liquid controls skip backdrop work. Keep the
            // measured slot intact, but stop their draw traversal once they are truly invisible.
            if (effectiveAlpha > 0.005f) drawContent()
        }
}

@Composable
private fun PersonalizeSliderLabel(
    value: State<Float>,
    label: (Float) -> String
) {
    Text(label(value.value), style = MaterialTheme.typography.bodyMedium)
}

@Composable
internal fun PersonalizeValueSlider(
    sliderKey: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    backdrop: Backdrop?,
    label: (Float) -> String,
    onCommit: (Float) -> Unit,
    onPreviewValueChange: (Float) -> Unit,
    previewSliderKey: String?,
    previewProgress: Float,
    onSliderPreviewActiveChange: (String, Boolean) -> Unit,
    snapValue: Float? = null,
    benchmarkDescription: String? = null,
    onTouchActiveChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val displayValue = remember(valueRange) { mutableFloatStateOf(value.coerceIn(valueRange)) }
    LaunchedEffect(value, valueRange) {
        displayValue.floatValue = value.coerceIn(valueRange)
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .personalizePreviewVisibility(previewSliderKey, previewProgress, sliderKey),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        PersonalizeSliderLabel(displayValue, label)
        LiquidControlSlider(
            compactThumb = true,
            value = value,
            onValueChange = onCommit,
            valueRange = valueRange,
            backdrop = backdrop,
            onLiveValueChange = {
                displayValue.floatValue = it
                onPreviewValueChange(it)
            },
            snapValue = snapValue,
            modifier = if (benchmarkDescription == null) Modifier else Modifier.semantics {
                contentDescription = benchmarkDescription
            },
            onSliderTouchActiveChange = { active ->
                onTouchActiveChange(active)
                onSliderPreviewActiveChange(sliderKey, active)
            }
        )
    }
}

@Composable
internal fun WallpaperBlurControl(
    blurDp: Float,
    backdrop: Backdrop?,
    onCommit: (Float) -> Unit,
    onPreviewValueChange: (Float) -> Unit,
    previewSliderKey: String?,
    previewProgress: Float,
    onSliderPreviewActiveChange: (String, Boolean) -> Unit,
    onTouchActiveChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    PersonalizeValueSlider(
        sliderKey = PersonalizeWallpaperBlurSlider,
        modifier = modifier,
        value = wallpaperBlurPercent(blurDp),
        valueRange = 0f..100f,
        backdrop = backdrop,
        label = { "壁纸模糊 ${it.roundToInt()}%" },
        onCommit = onCommit,
        onPreviewValueChange = onPreviewValueChange,
        previewSliderKey = previewSliderKey,
        previewProgress = previewProgress,
        onSliderPreviewActiveChange = onSliderPreviewActiveChange,
        snapValue = 0f,
        benchmarkDescription = "benchmark_personalization_blur_slider",
        onTouchActiveChange = onTouchActiveChange
    )
}
