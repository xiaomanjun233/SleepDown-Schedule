package com.itos.coui.segmentbutton

import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import com.itos.coui.shape.CouiCapsuleShape

/** SleepDown 兼容门面：保留尺寸、所属材质域与仅指示器装配，输入和运动委托 Kit。 */
@RequiresApi(34)
@Composable
fun CouiSegmentedControl(
    modifier: Modifier = Modifier,
    labels: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    dark: Boolean,
    enabled: Boolean = true,
    height: Dp = CouiSegmentedControlDefaults.height,
    containerSurfaceEnabled: Boolean = true,
    commitOnRelease: Boolean = false,
    indicatorModifier: Modifier? = null,
    indicatorPressScale: (Float) -> Float = { it },
    surfaceModifier: Modifier = Modifier.background(
        Color(if (dark) 0xFF292929 else 0xFFEBEBEB), CouiCapsuleShape),
) {
    SegmentedControl(
        modifier = modifier.fillMaxWidth().height(height),
        labels = labels,
        selectedIndex = selectedIndex,
        onSelected = onSelected,
        backdrop = null,
        dark = dark,
        enabled = enabled,
        commitOnRelease = commitOnRelease,
        containerSurfaceEnabled = containerSurfaceEnabled,
        surfaceModifier = surfaceModifier,
        indicatorModifier = indicatorModifier,
        indicatorPressScale = indicatorPressScale,
    )
}
