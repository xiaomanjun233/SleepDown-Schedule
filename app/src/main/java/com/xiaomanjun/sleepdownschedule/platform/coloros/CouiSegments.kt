package com.xiaomanjun.sleepdownschedule.platform.coloros

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import com.itos.coui.shape.CouiCapsuleShape
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.LocalNativeDialogSurface
import com.itos.coui.segmentbutton.CouiSegmentedControl
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.NativeSegmentRequest
import com.xiaomanjun.sleepdownschedule.glass.couiSegmentMaterial

@Composable
internal fun CouiSegments(request: NativeSegmentRequest): Boolean {
    if (request.labels.isEmpty()) return false
    val sampled = (request.backdrop != null && !LocalNativeDialogSurface.current) &&
        !com.xiaomanjun.sleepdownschedule.core.performance.AppMaterialPreferences.isSuperPerformance
    CouiSegmentedControl(modifier = request.modifier, labels = request.labels,
        selectedIndex = request.selected.coerceIn(request.labels.indices), onSelected = request.onSelected,
        dark = request.dark, enabled = request.enabled, height = request.height,
        containerSurfaceEnabled = request.containerSurfaceEnabled, commitOnRelease = request.commitOnRelease,
        surfaceModifier = if (!request.containerSurfaceEnabled) Modifier
            else if (sampled) couiSegmentMaterial(request.backdrop, request.dark)
            else Modifier.background(Color(if (request.dark) 0xFF292929 else 0xFFEBEBEB), CouiCapsuleShape))
    return true
}
