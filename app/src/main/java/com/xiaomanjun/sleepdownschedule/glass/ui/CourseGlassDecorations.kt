package com.xiaomanjun.sleepdownschedule.glass.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.draw.innerShadow
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.shadow.InnerShadow as BackdropInnerShadow
import com.kyant.backdrop.shadow.Shadow as BackdropShadow

/** White Screen is equivalent to white SrcOver; flatten the neutral fills into the tint. */
internal fun courseGlassSurfaceColor(tint: Color, lightGlass: Boolean): Color =
    Color.Black.copy(alpha = if (lightGlass) 0.004f else 0.014f).compositeOver(
        Color.White.copy(alpha = if (lightGlass) 0.012f else 0.008f).compositeOver(tint)
    )

/** Compose caches the shadow masks; these decorations do not own card-sized GraphicsLayers. */
internal fun Modifier.courseGlassOuterShadow(
    shape: Shape,
    alpha: Float,
    enabled: () -> Boolean
): Modifier = drawWithCache {
    val path = Path().apply { addOutline(shape.createOutline(size, layoutDirection, this@drawWithCache)) }
    onDrawWithContent {
        // Kyant's outer shadow excludes the card interior, including translucent surfaces.
        if (enabled()) clipPath(path, ClipOp.Difference) { this@onDrawWithContent.drawContent() }
    }
}.dropShadow(shape, Shadow(
    radius = 24.dp,
    offset = DpOffset(0.dp, 4.dp),
    color = Color.Black.copy(alpha = 0.1f),
    alpha = alpha
))

internal fun Modifier.courseGlassInnerShadow(
    shape: Shape,
    radius: Dp,
    alpha: Float,
    enabled: () -> Boolean
): Modifier = drawWithCache {
    onDrawWithContent { if (enabled()) this@onDrawWithContent.drawContent() }
}.innerShadow(shape, Shadow(
    radius = radius,
    offset = DpOffset(0.dp, radius),
    color = Color.Black.copy(alpha = 0.15f),
    alpha = alpha
))

/** Wallpaper controls share Compose's masks for the default glass shadow recipes. */
internal fun Modifier.wallpaperGlassShadows(
    shape: () -> Shape,
    outerAlpha: () -> Float,
    innerRadius: () -> Dp?,
    innerAlpha: () -> Float,
    enabled: () -> Boolean,
): Modifier = drawWithCache {
    val controlShape = shape()
    val outline = Path().apply {
        addOutline(controlShape.createOutline(size, layoutDirection, this@drawWithCache))
    }
    val shadowContext = obtainShadowContext()
    val outer = BackdropShadow.Default
    val outerPainter = shadowContext.createDropShadowPainter(
        controlShape,
        Shadow(radius = outer.radius, offset = outer.offset, color = outer.color)
    )
    val innerPainter = innerRadius()?.takeIf { it > 0.dp }?.let { radius ->
        val inner = BackdropInnerShadow(radius = radius)
        shadowContext.createInnerShadowPainter(
            controlShape,
            Shadow(radius = inner.radius, offset = inner.offset, color = inner.color)
        )
    }
    onDrawWithContent {
        val drawShadows = enabled()
        if (drawShadows) {
            val alpha = outerAlpha().coerceIn(0f, 1f)
            if (alpha > 0f) clipPath(outline, ClipOp.Difference) {
                with(outerPainter) { draw(size, alpha = alpha) }
            }
        }
        drawContent()
        if (drawShadows && innerPainter != null) {
            val alpha = innerAlpha().coerceIn(0f, 1f)
            if (alpha > 0f) with(innerPainter) { draw(size, alpha = alpha) }
        }
    }
}
