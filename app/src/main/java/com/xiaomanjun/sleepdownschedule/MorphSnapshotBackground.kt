package com.xiaomanjun.sleepdownschedule

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.ContentScale
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.catalog.components.LiquidPanel
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.roundToInt

@Composable
internal fun MirroredEdgeSnapshot(
    bitmap: Bitmap,
    insetFraction: Float,
    blurPx: Float,
    alphaProvider: () -> Float,
    modifier: Modifier = Modifier
) {
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
                renderEffect = platformBlurRenderEffect(blurPx)
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
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val scale = backgroundScaleProvider().coerceIn(0.92f, 1f)
                    val blurProgress = ((1f - scale) / 0.08f).coerceIn(0f, 1f)
                    val blurPx = blurProgress * 12f * density.density
                    scaleX = scale
                    scaleY = scale
                    renderEffect = platformBlurRenderEffect(blurPx)
                }
                .drawWithContent {
                    drawContent()
                    val scale = backgroundScaleProvider().coerceIn(0.92f, 1f)
                    val blurProgress = ((1f - scale) / 0.08f).coerceIn(0f, 1f)
                    if (blurProgress > 0.001f) {
                        val radiusPx = 24.dp.toPx() * blurProgress
                        val outside = Path().apply {
                            fillType = PathFillType.EvenOdd
                            addRect(Rect(0f, 0f, size.width, size.height))
                            addRoundRect(
                                RoundRect(
                                    rect = Rect(0f, 0f, size.width, size.height),
                                    cornerRadius = CornerRadius(radiusPx, radiusPx)
                                )
                            )
                        }
                        drawPath(outside, Color.Black, blendMode = BlendMode.Clear)

                        val featherPx = 18.dp.toPx() * blurProgress
                        val featherSteps = 10
                        repeat(featherSteps) { index ->
                            val linear = 1f - index / featherSteps.toFloat()
                            val remaining = linear * linear * (3f - 2f * linear)
                            drawRoundRect(
                                color = Color.Black.copy(alpha = 0.115f),
                                cornerRadius = CornerRadius(radiusPx, radiusPx),
                                style = Stroke(width = featherPx * 2f * remaining),
                                blendMode = BlendMode.DstOut
                            )
                        }
                    }
                }
        )
    }
}


