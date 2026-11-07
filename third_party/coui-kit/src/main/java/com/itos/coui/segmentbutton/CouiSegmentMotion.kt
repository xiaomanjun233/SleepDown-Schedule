package com.itos.coui.segmentbutton

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import com.itos.coui.motion.CouiSpring
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sign

/** 原厂先分配自然宽度，再对超出均值的项目做水位分配；宽度不足时保留短标签。 */
internal fun segmentWidths(natural: List<Float>, available: Float): List<Float> {
    val order = natural.indices.sortedBy { natural[it] }
    val result = MutableList(natural.size) { 0f }
    val mean = available / natural.size
    var remaining = available
    var limit = mean
    var index = 0
    var last = -1
    while (index < order.size) {
        val item = order[index]
        if (natural[item] < limit) {
            result[item] = natural[item]
            remaining -= natural[item]
            index++
        } else if (last == index) {
            while (index < order.size) result[order[index++]] = limit
            remaining = 0f
            break
        } else {
            limit = remaining / (order.size - index)
            last = index
        }
    }
    if (last == -1) return natural.map { mean.roundToInt().toFloat() }
    if (remaining > 0f && order.size > 1) {
        order.dropLast(1).forEach { result[it] += remaining / (order.size - 1) }
    }
    return result.map { it.roundToInt().toFloat().coerceAtLeast(0f) }
}

internal data class SegmentGeometry(
    val width: Float, val height: Float, val padding: Float,
    val widths: List<Float>, val rtl: Boolean, val small: Boolean,
) {
    val starts = widths.indices.map { padding + widths.take(it).sum() }
    val indicatorHeight get() = (height - padding * 2f).coerceAtLeast(0f)
    fun center(index: Int): Float {
        val logical = starts[index] + widths[index] / 2f
        return if (rtl) width - logical else logical
    }
    fun hit(x: Float): Int? {
        val logical = if (rtl) width - x else x
        return widths.indices.firstOrNull { logical >= starts[it] && logical < starts[it] + widths[it] }
    }
}

internal class SegmentSelectionSession(selected: Int) {
    var allowLight = true
    var touching by mutableStateOf(false)
        private set
    var preview by mutableIntStateOf(selected)
        private set
    private var emitted = selected

    fun sync(selected: Int) { if (!touching) preview = selected }
    fun begin(selected: Int) { touching = true; preview = selected; emitted = selected }
    fun select(index: Int, onRelease: Boolean, submit: (Int) -> Unit) {
        preview = index
        if (!onRelease && index != emitted) { emitted = index; submit(index) }
    }
    fun finish(cancelled: Boolean, selected: Int, onRelease: Boolean, submit: (Int) -> Unit) {
        if (!touching) return
        touching = false
        if (!cancelled && onRelease && preview != selected) submit(preview)
        if (cancelled) preview = selected
    }
}

internal class CouiSegmentMotion {
    private var disposed = false
    private val position = CouiSpring(0f, .5f)
    private val width = CouiSpring(0f, .5f)
    private val height = CouiSpring(0f, .5f)
    private val rubber = CouiSpring(0f, .5f)
    private var geometry: SegmentGeometry? = null
    private var widthLimit = Float.MAX_VALUE
    var resetRubberOrigin = true
    val moving get() = position.isRunning
    val indicatorWidth get() = width.value.coerceIn(0f, widthLimit)

    fun select(index: Int, next: SegmentGeometry, density: Float) {
        if (disposed) return
        if (geometry != next) {
            geometry = next
            widthLimit = Float.MAX_VALUE
            position.setBounds(-Float.MAX_VALUE, Float.MAX_VALUE)
            width.setBounds(-Float.MAX_VALUE, Float.MAX_VALUE)
            height.setBounds(-Float.MAX_VALUE, Float.MAX_VALUE)
            position.snapTo(next.center(index))
            width.snapTo(next.widths[index])
            height.snapTo(next.indicatorHeight)
            rubber.snapTo(0f)
            return
        }
        val targetWidth = next.widths[index]
        val growing = targetWidth > width.value
        widthLimit = if (growing) targetWidth + (if (next.small) 5f else 7f) * density else Float.MAX_VALUE
        rubber.snapTo(0f)
        position.setBounds(min(next.center(0), next.center(next.widths.lastIndex)),
            max(next.center(0), next.center(next.widths.lastIndex)))
        width.setBounds(0f, widthLimit)
        height.setBounds(next.indicatorHeight * .9f, Float.MAX_VALUE)
        position.animateTo(next.center(index), .4f,
            if (growing && next.small) dynamic(width.value, targetWidth, .1f, .03f, 1f) else .1f,
            onEnd = { resetRubberOrigin = true })
        width.animateTo(targetWidth, dynamic(width.value, targetWidth, .6f, .4f, 2f),
            if (growing) .5f else dynamic(width.value, targetWidth, .5f, .1f, 2f), velocity = 1000f)
        height.animateTo(next.indicatorHeight, .6f, .5f, velocity = -500f)
    }

    fun rect(geometry: SegmentGeometry, scale: Float): Rect {
        val first = geometry.center(0)
        val last = geometry.center(geometry.widths.lastIndex)
        val center = position.value.coerceIn(min(first, last), max(first, last)) + rubber.value
        val halfWidth = indicatorWidth * scale / 2f
        val halfHeight = height.value.coerceAtLeast(geometry.indicatorHeight * .9f) * scale / 2f
        return Rect((center - halfWidth).coerceAtLeast(geometry.padding), geometry.height / 2f - halfHeight,
            (center + halfWidth).coerceAtMost(geometry.width - geometry.padding), geometry.height / 2f + halfHeight)
    }

    fun dragRubber(delta: Float, scale: Float, edge: Boolean, switchDistance: Float) {
        if (disposed) return
        val distance = ((1f - scale) * indicatorWidth / 2f).coerceAtLeast(0f) + if (edge) 0f else switchDistance
        val offset = if (distance == 0f) 0f else
            (1f - 1f / (abs(delta) * .12f / distance + 1f)) * distance * sign(delta)
        rubber.animateTo(offset, .6f)
    }

    fun release() { if (!disposed) rubber.animateTo(0f, .6f) }
    fun dispose() {
        disposed = true
        listOf(position, width, height, rubber).forEach { it.cancelComplete() }
    }

    private fun dynamic(from: Float, to: Float, normal: Float, minimum: Float, exponent: Float): Float =
        if (from <= 0f || to <= 0f) normal else
            (normal - (normal - minimum) * (abs(from - to) / max(from, to)).pow(exponent)).coerceIn(0f, 1f)
}
