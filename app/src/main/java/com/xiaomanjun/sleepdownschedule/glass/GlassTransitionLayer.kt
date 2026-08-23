package com.xiaomanjun.sleepdownschedule.glass

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.ceil
import kotlin.math.roundToInt

data class GlassTransitionGeometry(
    val rectInRoot: Rect,
    val cornerRadiusPx: Float
) {
    init {
        require(rectInRoot.width > 0f && rectInRoot.height > 0f) {
            "Glass transition geometry must have a positive size."
        }
        require(
            rectInRoot.left.isFinite() &&
                rectInRoot.top.isFinite() &&
                rectInRoot.right.isFinite() &&
                rectInRoot.bottom.isFinite() &&
                cornerRadiusPx.isFinite() &&
                cornerRadiusPx >= 0f
        ) {
            "Glass transition geometry requires finite coordinates and a non-negative radius."
        }
    }
}

data class GlassTransitionEnvelope(val boundsInRoot: Rect) {
    init {
        require(
            boundsInRoot.width > 0f &&
                boundsInRoot.height > 0f &&
                boundsInRoot.left.isFinite() &&
                boundsInRoot.top.isFinite() &&
                boundsInRoot.right.isFinite() &&
                boundsInRoot.bottom.isFinite()
        ) {
            "A glass transition envelope must have a positive size."
        }
    }

    fun contains(geometry: GlassTransitionGeometry): Boolean =
        geometry.rectInRoot.left >= boundsInRoot.left &&
            geometry.rectInRoot.top >= boundsInRoot.top &&
            geometry.rectInRoot.right <= boundsInRoot.right &&
            geometry.rectInRoot.bottom <= boundsInRoot.bottom

    fun toLocal(rectInRoot: Rect): Rect = Rect(
        left = rectInRoot.left - boundsInRoot.left,
        top = rectInRoot.top - boundsInRoot.top,
        right = rectInRoot.right - boundsInRoot.left,
        bottom = rectInRoot.bottom - boundsInRoot.top
    )

    companion object {
        fun covering(
            geometries: Iterable<GlassTransitionGeometry>,
            effectPaddingPx: Float = 0f
        ): GlassTransitionEnvelope {
            require(effectPaddingPx.isFinite()) { "Effect padding must be finite." }
            val iterator = geometries.iterator()
            require(iterator.hasNext()) { "At least one transition geometry is required." }
            var union = iterator.next().rectInRoot
            while (iterator.hasNext()) {
                val next = iterator.next().rectInRoot
                union = Rect(
                    left = minOf(union.left, next.left),
                    top = minOf(union.top, next.top),
                    right = maxOf(union.right, next.right),
                    bottom = maxOf(union.bottom, next.bottom)
                )
            }
            val padding = effectPaddingPx.coerceAtLeast(0f)
            return GlassTransitionEnvelope(
                Rect(
                    left = union.left - padding,
                    top = union.top - padding,
                    right = union.right + padding,
                    bottom = union.bottom + padding
                )
            )
        }
    }
}

@Stable
class GlassTransitionLayerState internal constructor(
    val envelope: GlassTransitionEnvelope,
    initialGeometry: GlassTransitionGeometry
) {
    var geometry by mutableStateOf(initialGeometry)
        private set

    val localRect: Rect get() = envelope.toLocal(geometry.rectInRoot)
    val stableTargetWidthPx: Int get() = ceil(envelope.boundsInRoot.width).toInt()
    val stableTargetHeightPx: Int get() = ceil(envelope.boundsInRoot.height).toInt()

    init {
        require(envelope.contains(initialGeometry)) {
            "Initial glass geometry must fit inside its stable envelope."
        }
    }

    fun updateGeometry(next: GlassTransitionGeometry) {
        require(envelope.contains(next)) {
            "Animated glass geometry escaped its stable envelope."
        }
        geometry = next
    }
}

@Composable
fun rememberGlassTransitionLayerState(
    envelope: GlassTransitionEnvelope,
    initialGeometry: GlassTransitionGeometry
): GlassTransitionLayerState = remember(envelope) {
    GlassTransitionLayerState(envelope, initialGeometry)
}

/**
 * Stable clip-only Shape whose outline moves inside the fixed host.
 *
 * Do not pass this shape to Kyant's rounded-rectangle lens. Backdrop 2.0 derives the lens SDF
 * from the full modifier size and only accepts a single [androidx.compose.foundation.shape.CornerBasedShape]
 * (or Kyant rounded rectangle), so it cannot represent an inset moving rectangle yet.
 */
class GlassEnvelopeClipShape(private val state: GlassTransitionLayerState) : Shape {
    override fun createOutline(
        size: androidx.compose.ui.geometry.Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val rect = state.localRect
        val radius = state.geometry.cornerRadiusPx.coerceIn(
            minimumValue = 0f,
            maximumValue = minOf(rect.width, rect.height) / 2f
        )
        return Outline.Rounded(
            RoundRect(
                rect = rect,
                cornerRadius = CornerRadius(radius, radius)
            )
        )
    }
}

/**
 * Fixed-size host for the experimental transition renderer. [content] must position source and
 * destination content at real measured sizes; this host never scales either page to fill the
 * envelope.
 */
@Composable
fun GlassTransitionLayer(
    state: GlassTransitionLayerState,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.(GlassTransitionLayerState) -> Unit
) {
    val density = LocalDensity.current
    val bounds = state.envelope.boundsInRoot
    Box(
        modifier = modifier
            .offset {
                IntOffset(
                    x = bounds.left.roundToInt(),
                    y = bounds.top.roundToInt()
                )
            }
            .requiredSize(
                width = with(density) { state.stableTargetWidthPx.toDp() },
                height = with(density) { state.stableTargetHeightPx.toDp() }
            )
    ) {
        content(state)
    }
}
