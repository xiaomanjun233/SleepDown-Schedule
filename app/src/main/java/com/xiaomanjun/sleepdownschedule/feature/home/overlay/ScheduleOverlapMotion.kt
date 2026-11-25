package com.xiaomanjun.sleepdownschedule.feature.home.overlay

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.ui.geometry.Rect
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

private const val OverlapEpsilon = 1e-6

/**
 * Pure time-slot entry used to compute direct week-grid overlaps and stacking order.
 *
 * [start] and [end] use continuous 0-based period-row coordinates so regular period blocks
 * (`period 1..2 -> 0.0..2.0`) and custom-time fractional blocks share one overlap model.
 */
data class ScheduleOverlapSlot(
    val id: String,
    val weekday: Int,
    val start: Double,
    val end: Double,
    val stableOrder: Int = 0
)

data class ScheduleOverlapPlacement(
    val slot: ScheduleOverlapSlot,
    val overlapGroup: List<ScheduleOverlapSlot>,
    val overlapCount: Int,
    val showOverlapBadge: Boolean,
    val stackOrder: Int
)

fun overlapSlotsIntersectDirectly(a: ScheduleOverlapSlot, b: ScheduleOverlapSlot): Boolean =
    a.weekday == b.weekday &&
        a.start < b.end - OverlapEpsilon &&
        a.end > b.start + OverlapEpsilon

/**
 * Resolves per-slot direct overlap groups (excluding adjacent boundary periods and
 * transitive non-touching neighbors) and computes back-to-front draw ordering.
 */
fun layoutScheduleOverlapSlots(
    slots: List<ScheduleOverlapSlot>,
    preferredTopId: String? = null
): List<ScheduleOverlapPlacement> {
    if (slots.isEmpty()) return emptyList()
    val result = mutableListOf<ScheduleOverlapPlacement>()
    val byDay = linkedMapOf<Int, MutableList<ScheduleOverlapSlot>>()
    slots.forEach { slot ->
        byDay.getOrPut(slot.weekday) { mutableListOf() }.add(slot)
    }
    byDay.values.forEach { daySlots ->
        // Draw longer spans below shorter ones so nested courses remain visible at a glance,
        // while keeping any explicitly focused/selected course on top of the stack.
        val ordered = daySlots.sortedWith(
            compareBy<ScheduleOverlapSlot> { it.id == preferredTopId }
                .thenByDescending { it.end - it.start }
                .thenBy { it.start }
                .thenBy { it.end }
                .thenBy { it.stableOrder }
                .thenBy { it.id }
        )
        ordered.forEachIndexed { stackIndex, slot ->
            val directGroup = ordered.filter { other ->
                other.id == slot.id || overlapSlotsIntersectDirectly(slot, other)
            }
            val isTopOfDirectGroup = directGroup.lastOrNull()?.id == slot.id
            result += ScheduleOverlapPlacement(
                slot = slot,
                overlapGroup = directGroup,
                overlapCount = directGroup.size,
                showOverlapBadge = directGroup.size > 1 && isTopOfDirectGroup,
                stackOrder = stackIndex
            )
        }
    }
    return result
}

data class ScheduleOverlapChromeMotionState(
    val progress: Float,
    val opacity: Float,
    val scrimProgress: Float
)

data class ScheduleOverlapCardMotionState(
    val rect: Rect,
    val compactOpacity: Float,
    val compactScale: Float,
    val expandedOpacity: Float,
    val expandedScale: Float,
    val cornerRadiusPx: Float,
    val cardT: Float,
    val sizeT: Float,
    val pulseScale: Float
)

object ScheduleOverlapMotion {
    const val OpenDurationMs = 500
    const val CloseDurationMs = 420
    const val OpenSizeDelay = 0.10f
    const val CloseSizeFinish = 0.78f

    val OpenPositionCurve = CubicBezierEasing(0.18f, 0.72f, 0.18f, 1.0f)
    val OpenSizeCurve = CubicBezierEasing(0.22f, 0.62f, 0.22f, 1.0f)
    val ClosePositionCurve = CubicBezierEasing(0.30f, 0.10f, 0.22f, 1.0f)
    val CloseSizeCurve = CubicBezierEasing(0.16f, 0.86f, 0.28f, 1.0f)
    val ChromeCurve = CubicBezierEasing(0.16f, 0.84f, 0.24f, 1.0f)

    fun smoothStep(edge0: Float, edge1: Float, value: Float): Float {
        if (edge1 <= edge0) return if (value >= edge1) 1f else 0f
        val t = ((value - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    fun staggerDelay(index: Int, totalCards: Int, closing: Boolean): Float {
        if (totalCards <= 1) return 0f
        val maxStagger = min(0.14f, (totalCards - 1) * 0.05f)
        val step = maxStagger / (totalCards - 1)
        val orderFromTop = (totalCards - 1 - index).coerceIn(0, totalCards - 1)
        return if (closing) {
            maxStagger - orderFromTop * step
        } else {
            orderFromTop * step
        }
    }

    fun computeChromeProgress(rawProgress: Float, closing: Boolean): ScheduleOverlapChromeMotionState {
        val raw = rawProgress.coerceIn(0f, 1f)
        val progress = if (closing) {
            1f - ChromeCurve.transform(1f - raw)
        } else {
            ChromeCurve.transform(raw)
        }
        val opacity = if (closing) {
            ((raw - 0.12f) / 0.88f).coerceIn(0f, 1f)
        } else {
            ((raw - 0.16f) / 0.84f).coerceIn(0f, 1f)
        }
        return ScheduleOverlapChromeMotionState(
            progress = progress,
            opacity = opacity,
            scrimProgress = raw
        )
    }

    fun computeCardMotion(
        rawProgress: Float,
        closing: Boolean,
        index: Int,
        totalCards: Int,
        sourceRect: Rect,
        targetRect: Rect,
        sourceCornerPx: Float,
        targetCornerPx: Float,
        interruptedFromRect: Rect? = null,
        interruptedStartRaw: Float? = null
    ): ScheduleOverlapCardMotionState {
        val raw = rawProgress.coerceIn(0f, 1f)
        val stagger = staggerDelay(index = index, totalCards = totalCards, closing = closing)
        val span = (1f - stagger).coerceAtLeast(0.01f)
        val cardT = if (closing) {
            val localReverse = ((1f - raw - stagger) / span).coerceIn(0f, 1f)
            1f - localReverse
        } else {
            ((raw - stagger) / span).coerceIn(0f, 1f)
        }

        val posT: Float
        val sizeT: Float
        if (closing) {
            val revT = (1f - cardT).coerceIn(0f, 1f)
            posT = 1f - ClosePositionCurve.transform(revT)
            val revSizeT = CloseSizeCurve.transform((revT / CloseSizeFinish).coerceIn(0f, 1f))
            sizeT = 1f - revSizeT
        } else {
            posT = OpenPositionCurve.transform(cardT)
            val rawSizeT = ((cardT - OpenSizeDelay) / (1f - OpenSizeDelay)).coerceIn(0f, 1f)
            sizeT = OpenSizeCurve.transform(rawSizeT)
        }

        val effectiveStartRect: Rect
        val effectiveEndRect: Rect
        val arcProgress: Float
        val sizeProgress: Float
        if (interruptedFromRect != null && interruptedStartRaw != null) {
            if (closing) {
                val denom = interruptedStartRaw.coerceAtLeast(0.001f)
                val localCloseT = (1f - (raw / denom)).coerceIn(0f, 1f)
                val easedPos = ClosePositionCurve.transform(localCloseT)
                val easedSize = CloseSizeCurve.transform((localCloseT / CloseSizeFinish).coerceIn(0f, 1f))
                effectiveStartRect = interruptedFromRect
                effectiveEndRect = sourceRect
                arcProgress = easedPos
                sizeProgress = 1f - easedSize
            } else {
                val denom = (1f - interruptedStartRaw).coerceAtLeast(0.001f)
                val localOpenT = ((raw - interruptedStartRaw) / denom).coerceIn(0f, 1f)
                val easedPos = OpenPositionCurve.transform(localOpenT)
                val easedSize = OpenSizeCurve.transform(((localOpenT - OpenSizeDelay) / (1f - OpenSizeDelay)).coerceIn(0f, 1f))
                effectiveStartRect = interruptedFromRect
                effectiveEndRect = targetRect
                arcProgress = easedPos
                sizeProgress = easedSize
            }
        } else {
            effectiveStartRect = sourceRect
            effectiveEndRect = targetRect
            arcProgress = posT
            sizeProgress = sizeT
        }

        val startCenter = effectiveStartRect.center
        val endCenter = effectiveEndRect.center
        val dx = endCenter.x - startCenter.x
        val dy = endCenter.y - startCenter.y
        val maxArc = if (closing) 40f else 48f
        val arcAmplitude = if (dx >= 0f) {
            min(maxArc, abs(dy) * 0.22f)
        } else {
            -min(maxArc, abs(dy) * 0.22f)
        }
        val midControlX = (startCenter.x + endCenter.x) * 0.5f + arcAmplitude * 0.65f
        val midControlY = (startCenter.y + endCenter.y) * 0.5f - abs(dy) * 0.14f - arcAmplitude * 0.25f

        val oneMinusT = 1f - arcProgress
        val centerX = oneMinusT * oneMinusT * startCenter.x +
            2f * oneMinusT * arcProgress * midControlX +
            arcProgress * arcProgress * endCenter.x
        val centerY = oneMinusT * oneMinusT * startCenter.y +
            2f * oneMinusT * arcProgress * midControlY +
            arcProgress * arcProgress * endCenter.y

        val widthLerpT = if (interruptedFromRect != null && closing) 1f - sizeProgress else sizeProgress
        val width = if (interruptedFromRect != null && closing) {
            lerpFloat(effectiveStartRect.width, effectiveEndRect.width, widthLerpT)
        } else {
            lerpFloat(effectiveStartRect.width, effectiveEndRect.width, sizeProgress)
        }
        val height = if (interruptedFromRect != null && closing) {
            lerpFloat(effectiveStartRect.height, effectiveEndRect.height, widthLerpT)
        } else {
            lerpFloat(effectiveStartRect.height, effectiveEndRect.height, sizeProgress)
        }

        val settleBreath = if (!closing && interruptedFromRect == null) {
            1f + 0.008f * sin(smoothStep(0.32f, 0.94f, cardT) * Math.PI.toFloat())
        } else {
            1f
        }

        val finalWidth = max(1f, width * settleBreath)
        val finalHeight = max(1f, height * settleBreath)
        val rect = Rect(
            left = centerX - finalWidth * 0.5f,
            top = centerY - finalHeight * 0.5f,
            right = centerX + finalWidth * 0.5f,
            bottom = centerY + finalHeight * 0.5f
        )

        val contentT = if (interruptedFromRect != null) raw else sizeT
        val compactOpacity = (1f - smoothStep(0.06f, 0.30f, contentT)).coerceIn(0f, 1f)
        val compactScale = lerpFloat(1.08f, 1.0f, smoothStep(0.0f, 0.28f, contentT))
        val expandedOpacity = smoothStep(0.18f, 0.62f, contentT).coerceIn(0f, 1f)
        val expandedScale = lerpFloat(0.86f, 1.0f, smoothStep(0.16f, 0.70f, contentT))
        val cornerRadiusPx = lerpFloat(sourceCornerPx, targetCornerPx, smoothStep(0.04f, 0.90f, contentT))

        return ScheduleOverlapCardMotionState(
            rect = rect,
            compactOpacity = compactOpacity,
            compactScale = compactScale,
            expandedOpacity = expandedOpacity,
            expandedScale = expandedScale,
            cornerRadiusPx = cornerRadiusPx,
            cardT = cardT,
            sizeT = sizeT,
            pulseScale = settleBreath
        )
    }
}

private fun lerpFloat(start: Float, stop: Float, fraction: Float): Float =
    start + (stop - start) * fraction
