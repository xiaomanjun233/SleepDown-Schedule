package com.itos.coui.sheet

import android.graphics.Paint
import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.neverEqualPolicy
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.IntSize
import com.itos.coui.backdrop.CouiBackdrop
import com.itos.coui.backdrop.couiBackdrop
import com.itos.coui.effect.CouiContour
import com.itos.coui.dialog.CouiDialogColors
import com.itos.coui.dialog.CouiDialogDefaults
import com.itos.coui.dialog.CouiDialogMaterial
import com.itos.coui.effect.rememberCouiMaterialEffect
import com.itos.coui.menu.rememberCouiKawaseBlur

@Composable
internal fun CouiSheetSurface(
    modifier: Modifier,
    material: CouiDialogMaterial?,
    backdrop: CouiBackdrop?,
    colors: CouiDialogColors,
    shape: Shape,
    edge: CouiContour?,
    dark: Boolean,
    radiusPx: Float,
    edgeWeight: Float = CouiDialogDefaults.edgeWeight,
    innerGlowAlpha: Float = if (dark) .03f else .4f,
    lineAlpha: Float = if (dark) .3f else .6f,
    extendBottomEdge: Boolean = false,
    contourOnTop: Boolean = false,
    surfaceBackdrop: CouiBackdrop? = null,
    sampleCoordinates: (() -> LayoutCoordinates?)? = null,
    sampleTranslation: () -> Offset = { Offset.Zero },
    content: @Composable BoxScope.() -> Unit,
) {
    // 表面在组合期间保留外层窗口源，退场结束后与面板一并释放。
    if (material != null) checkNotNull(backdrop).RetainWhileComposed()
    val blur = if (material != null) rememberCouiKawaseBlur(checkNotNull(backdrop), colors.surface, material.blurRadius) else null
    val materialEffect = if (material != null) rememberCouiMaterialEffect(material.mix, material.blend, material.blendMode) else null
    val surfaceEffect = remember(materialEffect) { materialEffect?.asComposeRenderEffect() }
    val layer = rememberGraphicsLayer()
    val recordingDensity = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val edgePaint = remember { Paint(Paint.ANTI_ALIAS_FLAG) }
    var coordinates: LayoutCoordinates? by remember { mutableStateOf(null, neverEqualPolicy()) }
    fun DrawScope.drawContour() {
        if (edge != null) drawIntoCanvas { canvas ->
            canvas.nativeCanvas.drawRect(0f, 0f, size.width, size.height, edgePaint)
        }
    }
    Box(modifier.clip(shape).drawWithCache {
        val edgeSize = if (extendBottomEdge) size.copy(height = size.height + radiusPx) else size
        edgePaint.shader = if (Build.VERSION.SDK_INT >= 33) {
            edge?.shader(edgeSize, density, radiusPx, edgeWeight,
                Color.Transparent, innerGlowAlpha, lineAlpha, 1f, (if (dark) 2f else 4f) * density)
        } else null
        onDrawWithContent {
            drawContent()
            // PanelPercentFrameLayout.draw 在清晰内容之后绘制白光。
            if (contourOnTop) drawContour()
        }
    }) {
    // 采样位移每帧使背景失效；独立绘制节点保留清晰内容和轮廓的显示列表。
    Box(Modifier.matchParentSize().graphicsLayer()
        .then(if (surfaceBackdrop != null) Modifier.couiBackdrop(surfaceBackdrop) else Modifier)
        .onGloballyPositioned { coordinates = it }.drawBehind {
        val target = sampleCoordinates?.invoke() ?: coordinates
        if (Build.VERSION.SDK_INT >= 33 && blur != null && materialEffect != null &&
            target != null && target.isAttached && size.width > 0f && size.height > 0f) {
            layer.renderEffect = surfaceEffect
            layer.record(recordingDensity, layoutDirection,
                IntSize(size.width.toInt().coerceAtLeast(1), size.height.toInt().coerceAtLeast(1))) {
                blur.draw(this, target, 1f, sampleTranslation())
            }
            drawLayer(layer)
        } else drawRect(colors.surface)
        if (!contourOnTop) drawContour()
    })
    content()
    }
}
