package com.xiaomanjun.sleepdownschedule.transition.legacy

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.kyant.shapes.RoundedRectangle
import com.xiaomanjun.sleepdownschedule.*
import com.xiaomanjun.sleepdownschedule.glass.ui.*
import kotlin.math.abs

@Composable
internal fun MirroredEdgeSnapshot(
    bitmap: Bitmap,
    insetFraction: Float,
    blurPx: Float,
    alphaProvider: () -> Float,
    modifier: Modifier = Modifier
) {
    val edgeEffect = remember(blurPx) { platformBlurRenderEffect(blurPx) }
    val shader = remember(bitmap) {
        BitmapShader(bitmap, Shader.TileMode.MIRROR, Shader.TileMode.MIRROR)
    }
    val paint = remember(shader) {
        Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            this.shader = shader
        }
    }
    Box(
        modifier = modifier
            .graphicsLayer {
                this.alpha = alphaProvider().coerceIn(0f, 1f)
                renderEffect = edgeEffect
            }
            .drawWithCache {
                val insetX = size.width * insetFraction.coerceIn(0f, 0.49f)
                val insetY = size.height * insetFraction.coerceIn(0f, 0.49f)
                val shaderMatrix = Matrix().apply {
                    setRectToRect(
                        RectF(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat()),
                        RectF(insetX, insetY, size.width - insetX, size.height - insetY),
                        Matrix.ScaleToFit.FILL
                    )
                }
                shader.setLocalMatrix(shaderMatrix)
                onDrawBehind {
                    drawContext.canvas.nativeCanvas.drawRect(
                        0f,
                        0f,
                        size.width,
                        size.height,
                        paint
                    )
                }
            }
    )
}

/**
 * The single frozen-home background used by every card-to-overlay Morph.
 * Keeping this here prevents Agent/editor variants from drifting in blur timing, edge fill,
 * corner feathering, or scale geometry.
 */
@Composable
internal fun MorphSnapshotBackground(
    bitmap: Bitmap,
    backgroundScale: Float,
    modifier: Modifier = Modifier
) = MorphSnapshotBackground(
    bitmap = bitmap,
    backgroundScaleProvider = { backgroundScale },
    modifier = modifier
)

/** Draw-time variant that does not recompose its parent for every motion frame. */
@Composable
internal fun MorphSnapshotBackground(
    bitmap: Bitmap,
    backgroundScaleProvider: () -> Float,
    nativeRoundClip: Boolean = false,
    dialog: com.xiaomanjun.sleepdownschedule.core.ui.designsystem.NativeDialogSnapshot? = null,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val edgeFillBlurPx = 12f * density.density

    Box(modifier) {
        MirroredEdgeSnapshot(
            bitmap = bitmap,
            insetFraction = 0.04f,
            blurPx = edgeFillBlurPx,
            // The center snapshot starts shrinking on the first non-zero frame. Its rounded clear
            // must always reveal an opaque blurred edge, never a partially transparent black gap.
            alphaProvider = { 1f },
            modifier = Modifier.fillMaxSize()
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val scale = backgroundScaleProvider().coerceIn(0.92f, 1.08f)
                    val blurProgress = morphSnapshotDepthProgress(scale)
                    val blurPx = blurProgress * 12f * density.density
                    scaleX = scale
                    scaleY = scale
                    renderEffect = platformBlurRenderEffect(blurPx)
                }
                .drawWithContent {
                    drawContent()
                    val scale = backgroundScaleProvider().coerceIn(0.92f, 1.08f)
                    val shrinkProgress = ((1f - scale) / 0.08f).coerceIn(0f, 1f)
                    if (shrinkProgress > 0.001f) {
                        val radiusPx = 24.dp.toPx() * shrinkProgress
                        // All eleven draws share the same continuous outline. Building it inside
                        // each feather stroke multiplies path construction during every frame.
                        val roundedShape = if (nativeRoundClip) androidx.compose.foundation.shape.RoundedCornerShape(radiusPx.toDp())
                            else RoundedRectangle(radiusPx.toDp())
                        val roundedOutline = roundedShape.createOutline(
                            size, layoutDirection, this@drawWithContent
                        )
                        val roundedPath = Path().apply { addOutline(roundedOutline) }
                        val outside = Path().apply {
                            fillType = PathFillType.EvenOdd
                            addRect(Rect(0f, 0f, size.width, size.height))
                            addPath(roundedPath)
                        }
                        drawPath(outside, Color.Black, blendMode = BlendMode.Clear)

                        val featherPx = 18.dp.toPx() * shrinkProgress
                        val featherSteps = 10
                        repeat(featherSteps) { index ->
                            val linear = 1f - index / featherSteps.toFloat()
                            val remaining = linear * linear * (3f - 2f * linear)
                            if (nativeRoundClip) drawRoundRect(
                                color = Color.Black.copy(alpha = 0.115f),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(radiusPx, radiusPx),
                                style = Stroke(width = featherPx * 2f * remaining),
                                blendMode = BlendMode.DstOut
                            ) else drawPath(
                                path = roundedPath,
                                color = Color.Black.copy(alpha = 0.115f),
                                style = Stroke(width = featherPx * 2f * remaining),
                                blendMode = BlendMode.DstOut
                            )
                        }
                    }
                }
        ) {
            Image(bitmap = bitmap.asImageBitmap(), contentDescription = null,
                contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize())
            dialog?.let { NativeDialogSnapshotLayer(it, bitmap.width, bitmap.height) }
        }
    }
}

internal fun morphSnapshotDepthProgress(scale: Float): Float =
    (abs(scale.coerceIn(0.92f, 1.08f) - 1f) / 0.08f).coerceIn(0f, 1f)



/** Reattach COUI's real material between the source page and the copied dialog text/buttons. */
@Composable
private fun NativeDialogSnapshotLayer(
    snapshot: com.xiaomanjun.sleepdownschedule.core.ui.designsystem.NativeDialogSnapshot,
    sourceWidth: Int, sourceHeight: Int
) {
    val density = LocalDensity.current
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
        val sx = constraints.maxWidth.toFloat() / sourceWidth.coerceAtLeast(1)
        val sy = constraints.maxHeight.toFloat() / sourceHeight.coerceAtLeast(1)
        fun positioned(rect: androidx.compose.ui.geometry.Rect): Modifier = Modifier
            .nativeSnapshotPosition(rect.left * sx, rect.top * sy, rect.width * sx, rect.height * sy)
        val surface = positioned(snapshot.materialBounds)
        val shape = androidx.compose.foundation.shape.RoundedCornerShape(
            with(density) { (snapshot.cornerRadiusPx * minOf(sx, sy)).toDp() })
        com.xiaomanjun.sleepdownschedule.core.ui.designsystem.LocalNativeMaterialSurfaceRenderer.current?.invoke(
            com.xiaomanjun.sleepdownschedule.core.ui.designsystem.NativeMaterialSurfaceRequest(
                modifier = surface, shape = shape, dark = snapshot.dark,
                role = com.xiaomanjun.sleepdownschedule.core.ui.designsystem.NativeMaterialSurfaceRole.Dialog,
                edgeEffects = false, followTouch = false
            )
        ) {}
        Image(snapshot.foreground.asImageBitmap(), contentDescription = null,
            contentScale = ContentScale.FillBounds, modifier = positioned(snapshot.windowBounds))
    }
}

private fun Modifier.nativeSnapshotPosition(x: Float, y: Float, width: Float, height: Float): Modifier = this
    .graphicsLayer { translationX = x; translationY = y }
    .layout { measurable, _ ->
        val w = width.toInt().coerceAtLeast(1)
        val h = height.toInt().coerceAtLeast(1)
        val placeable = measurable.measure(androidx.compose.ui.unit.Constraints.fixed(w, h))
        layout(w, h) { placeable.place(0, 0) }
    }
