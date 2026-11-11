package com.xiaomanjun.sleepdownschedule.glass

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.itos.coui.backdrop.CouiBackdrop
import com.itos.coui.backdrop.rememberCouiBackdropAdapter
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.withTransformCompensation

/** 重用已经排除消费者的源；不改用所属页面的完整窗口祖先。 */
@Composable
internal fun rememberCouiGlassBackdrop(backdrop: Backdrop): CouiBackdrop {
    val sources = remember(backdrop) { GlassSourceDemand.sources(backdrop) }
    val drawable = remember(backdrop) {
        if (backdrop is LayerBackdrop) backdrop.withTransformCompensation() else backdrop
    }
    return rememberCouiBackdropAdapter(backdrop,
        retain = { GlassSourceDemand.acquire(sources) },
        release = { GlassSourceDemand.release(sources) },
        draw = { scope, target -> with(drawable) { scope.drawBackdrop(scope, target) } })
}

/** A shared window can feed either material without creating another source or snapshot. */
@Composable
internal fun rememberGlassWindowBackdrop(source: CouiBackdrop): Backdrop {
    source.RetainWhileComposed()
    return remember(source) {
        object : Backdrop {
            override val isCoordinatesDependent = true
            override fun androidx.compose.ui.graphics.drawscope.DrawScope.drawBackdrop(
                density: androidx.compose.ui.unit.Density,
                coordinates: androidx.compose.ui.layout.LayoutCoordinates?,
                layerBlock: (androidx.compose.ui.graphics.GraphicsLayerScope.() -> Unit)?,
            ) {
                coordinates?.takeIf { it.isAttached }?.let { source.draw(this, it) }
            }
        }
    }
}
