package com.xiaomanjun.sleepdownschedule.core.ui.designsystem

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.itos.coui.segmentbutton.CouiSegmentedControl
import com.itos.coui.shape.CouiCapsuleShape
import com.xiaomanjun.sleepdownschedule.core.performance.AppMaterialPreferences
import com.xiaomanjun.sleepdownschedule.core.ui.material.LocalVisualEffects
import com.xiaomanjun.sleepdownschedule.glass.couiSegmentMaterial
import com.xiaomanjun.sleepdownschedule.glass.liquidSegmentMaterial

internal data class SegmentControlRequest(
    val labels: List<String>, val selected: Int, val onSelected: (Int) -> Unit,
    val modifier: Modifier, val dark: Boolean,
    val height: Dp = 42.dp,
    val enabled: Boolean = true,
    // Composite navigation owns its shell, light and deformation; this draws tabs/indicator.
    val containerSurfaceEnabled: Boolean = true,
    // Navigation must not disable the native rail by switching pages halfway through a drag.
    val commitOnRelease: Boolean = false,
    val backdrop: com.kyant.backdrop.Backdrop? = null
)


/** One label rail and selection session. The material only supplies its shell and indicator. */
@Composable
internal fun ControlSegments(request: SegmentControlRequest, highContrast: Boolean = false) {
    if (request.labels.isEmpty()) return
    val glow = LocalVisualEffects.current.usesGlowMaterials
    val sampled = request.backdrop?.takeUnless { LocalControlDialogSurface.current ||
        AppMaterialPreferences.isSuperPerformance }
    val shell = when {
        !request.containerSurfaceEnabled -> Modifier
        sampled == null -> Modifier.background(Color(if (request.dark) 0xFF292929 else 0xFFEBEBEB), CouiCapsuleShape)
        glow -> couiSegmentMaterial(sampled, request.dark)
        else -> liquidSegmentMaterial(sampled, request.dark, indicator = false, highContrast = highContrast)
    }
    val indicator = if (glow || sampled == null) null else
        liquidSegmentMaterial(sampled, request.dark, indicator = true, highContrast = highContrast)
    if (Build.VERSION.SDK_INT >= 34) {
        CouiSegmentedControl(modifier = request.modifier, labels = request.labels,
            selectedIndex = request.selected.coerceIn(request.labels.indices), onSelected = request.onSelected,
            dark = request.dark, enabled = request.enabled, height = request.height,
            containerSurfaceEnabled = request.containerSurfaceEnabled, commitOnRelease = request.commitOnRelease,
            surfaceModifier = shell, indicatorModifier = indicator,
            indicatorPressScale = if (glow) ({ it }) else ({ 2f - it }))
    } else {
        // API 26–33 uses the same public layout/semantics without the Kit's API-34 gesture rail.
        Row(request.modifier.height(request.height).then(shell).padding(4.dp).selectableGroup()) {
            request.labels.forEachIndexed { index, label ->
                val selected = index == request.selected
                Box(Modifier.weight(1f).fillMaxHeight()
                    .then(if (!selected) Modifier else indicator ?: Modifier.background(
                        Color(if (request.dark) 0xFF555555 else 0xFFFFFFFF), CouiCapsuleShape))
                    .selectable(selected, enabled = request.enabled, role = Role.Tab,
                        onClick = { request.onSelected(index) }), contentAlignment = Alignment.Center) {
                    Text(label, maxLines = 1,
                        color = if (request.dark) Color.White else Color.Black)
                }
            }
        }
    }
}
