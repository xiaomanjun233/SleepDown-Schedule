package com.xiaomanjun.sleepdownschedule.core.ui.controls

import android.graphics.BlendMode
import android.os.Build
import android.os.SystemClock
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.calculateTargetValue
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.rememberSplineBasedDecay
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.itos.coui.segmentbutton.CouiSegmentedControlDefaults
import com.itos.coui.segmentbutton.SegmentedControl
import com.itos.coui.shape.CouiCapsuleShape
import com.kyant.backdrop.Backdrop
import com.xiaomanjun.sleepdownschedule.core.performance.AppMaterialPreferences
import com.xiaomanjun.sleepdownschedule.glass.rememberCouiGlassBackdrop

private val LightFormSegmentTrack = Color(0xFFE2E3E4)

/** Two options share the import form's draggable COUI capsule and its older Android fallback. */
@Composable
internal fun CouiFormTabs(
    labels: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    dark: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    backdrop: Backdrop? = null,
) {
    val track = if (dark) CouiSegmentedControlDefaults.containerColor(true) else LightFormSegmentTrack
    if (Build.VERSION.SDK_INT >= 34) {
        val material = if (backdrop != null && !AppMaterialPreferences.isSuperPerformance) {
            rememberCouiGlassBackdrop(backdrop)
        } else null
        val lightTrackMaterial = remember {
            CouiSegmentedControlDefaults.material(false).copy(
                secondColor = LightFormSegmentTrack.copy(alpha = 0.92f),
                secondBlendMode = BlendMode.SRC_OVER,
            )
        }
        SegmentedControl(
            modifier = modifier.fillMaxWidth(), labels = labels, selectedIndex = selectedIndex,
            onSelected = onSelected, dark = dark, enabled = enabled, backdrop = material,
            commitOnRelease = true, flingOnRelease = true,
            containerColor = track, containerMaterial = lightTrackMaterial.takeUnless { dark },
        )
        return
    }
    var dragPosition by remember { mutableStateOf<Float?>(null) }
    val indicator by animateFloatAsState(
        dragPosition ?: selectedIndex.toFloat(),
        animationSpec = if (dragPosition == null) spring() else snap(),
        label = "form-tab",
    )
    val select by rememberUpdatedState(onSelected)
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val decay = rememberSplineBasedDecay<Float>()
    BoxWithConstraints(
        modifier.fillMaxWidth().height(CouiSegmentedControlDefaults.height)
            .clip(CouiCapsuleShape).background(track)
            .selectableGroup()
            .pointerInput(enabled, rtl, decay) {
                if (!enabled) return@pointerInput
                val tracker = VelocityTracker()
                var lastMove = 0L
                var position = 0f
                detectHorizontalDragGestures(
                    onDragStart = { tracker.resetTracking() },
                    onDragEnd = {
                        if (dragPosition != null) {
                            val speed = if (SystemClock.uptimeMillis() - lastMove < 40)
                                tracker.calculateVelocity().x else 0f
                            val end = decay.calculateTargetValue(position, speed)
                            select(if ((end < size.width / 2f) != rtl) 0 else 1)
                        }
                        dragPosition = null
                    },
                    onDragCancel = { dragPosition = null },
                ) { change, _ ->
                    change.consume()
                    tracker.addPointerInputChange(change)
                    position = change.position.x
                    lastMove = change.uptimeMillis
                    val fraction = (change.position.x * 2f / size.width.coerceAtLeast(1) - 0.5f).coerceIn(0f, 1f)
                    dragPosition = if (rtl) 1f - fraction else fraction
                }
            }
            .padding(4.dp)
    ) {
        val halfWidth = maxWidth / 2
        Box(
            Modifier.offset(x = halfWidth * indicator).width(halfWidth).fillMaxHeight()
                .background(CouiSegmentedControlDefaults.indicatorColor(dark), CouiCapsuleShape)
        )
        Row(Modifier.fillMaxSize()) {
            labels.forEachIndexed { index, label ->
                Box(
                    Modifier.weight(1f).fillMaxHeight().selectable(
                        selected = selectedIndex == index, enabled = enabled, role = Role.Tab,
                        onClick = { onSelected(index) }
                    ).padding(horizontal = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}
