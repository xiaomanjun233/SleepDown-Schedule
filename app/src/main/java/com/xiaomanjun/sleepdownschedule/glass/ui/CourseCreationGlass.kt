package com.xiaomanjun.sleepdownschedule.glass.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.xiaomanjun.sleepdownschedule.ScheduleConfigEntity
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.LocalGlowMaterialSurfaceRenderer
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.NativeMaterialSurfaceRequest
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.NativeMaterialSurfaceRole
import com.xiaomanjun.sleepdownschedule.glass.glassBackdropProducer

/** The slot and its editor share the same neutral Glow surface and wallpaper input. */
@Composable
internal fun CourseCreationGlass(
    backdrop: Backdrop?,
    config: ScheduleConfigEntity,
    shape: Shape,
    modifier: Modifier = Modifier,
    formBackdrop: LayerBackdrop? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val dark = !glassUsesLightStyle(config)
    val renderer = LocalGlowMaterialSurfaceRenderer.current
    Box(modifier.clip(shape)) {
        val surface = Modifier.matchParentSize().then(
            if (formBackdrop != null) Modifier.glassBackdropProducer(formBackdrop) else Modifier
        )
        val rendered = renderer?.invoke(
            NativeMaterialSurfaceRequest(
                modifier = surface, shape = shape, dark = dark,
                role = NativeMaterialSurfaceRole.Form, backdrop = backdrop,
                blurRadius = 14.dp, backdropSampleScale = 0.5f, followTouch = false
            )
        ) {} == true
        if (!rendered) {
            val tint = if (dark) Color(0xFF1C1C1E) else Color.White
            Box(surface.morphDialogMaterial(
                backdrop, shape, tint.copy(alpha = if (backdrop == null) 0.84f else 0.32f),
                blurRadius = 14.dp
            ))
        }
        content()
    }
}
