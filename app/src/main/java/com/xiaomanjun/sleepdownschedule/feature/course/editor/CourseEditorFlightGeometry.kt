package com.xiaomanjun.sleepdownschedule.feature.course.editor

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.staticCompositionLocalOf
import com.xiaomanjun.sleepdownschedule.CourseEntity
import kotlin.math.abs
import kotlin.math.sign
import kotlin.math.roundToInt

internal val LocalCourseEditorFlightRegistry = staticCompositionLocalOf<CourseEditorFlightRegistry?> { null }

internal class CourseEditorFlightRegistry {
    var frozen = false
    private val grids = mutableMapOf<Int, CourseEditorWeekGrid>()
    fun record(week: Int, grid: CourseEditorWeekGrid) { if (!frozen) grids[week] = grid }
    fun grid(week: Int) = grids[week]
    fun remove(week: Int) { grids.remove(week) }
}

/** Captured before background depth transforms; independent of a particular course's shape. */
data class CourseEditorWeekGrid(
    val origin: Offset,
    val width: Float,
    val rowHeight: Float,
    val gap: Float,
    val periodIndexes: List<Int>,
    val scroll: ScrollState?,
    val scrollAtCapture: Int,
    val rightToLeft: Boolean
) {
    suspend fun reveal(course: CourseEntity, weekdays: List<Int>): Rect? {
        val column = weekdays.indexOf(course.weekday).takeIf { it >= 0 } ?: return null
        val positions = course.periods.map(periodIndexes::indexOf).filter { it >= 0 }.distinct().sorted()
        val first = positions.firstOrNull() ?: return null
        val firstRun = positions.takeWhile { it - first == positions.indexOf(it) }.size
        val cellWidth = width / weekdays.size
        val physicalColumn = if (rightToLeft) weekdays.lastIndex - column else column
        val left = origin.x + physicalColumn * cellWidth + gap / 2f
        // Bring the selected period near the viewport center while preserving the grid's
        // configured header offset. scrollTo clamps against the actual measured scroll range.
        if (scroll != null && scroll.viewportSize > 0) {
            val rowCenter = rowHeight * (first + firstRun / 2f)
            scroll.scrollTo((rowCenter - scroll.viewportSize * 0.45f).roundToInt().coerceAtLeast(0))
        }
        val top = origin.y + rowHeight * first + gap / 2f - ((scroll?.value ?: scrollAtCapture) - scrollAtCapture)
        return Rect(left, top, left + cellWidth - gap, top + (rowHeight * firstRun - gap).coerceAtLeast(1f))
    }
}

/** Establish a gentle taper early, then restore it evenly over the rest of either animation. */
internal fun courseEditorOpeningTaper(
    progress: Float, sourceDeltaY: Float, targetHeight: Float, closing: Boolean = false
): Float {
    val p = progress.coerceIn(0f, 1f)
    val elapsed = if (closing) 1f - p else p
    val entry = (elapsed / 0.12f).coerceIn(0f, 1f)
    val envelope = entry * entry * (3f - 2f * entry) * (1f - elapsed)
    val travel = (abs(sourceDeltaY) / targetHeight.coerceAtLeast(1f)).coerceIn(0f, 1f)
    return -sign(sourceDeltaY) * 0.16f * envelope * kotlin.math.sqrt(travel)
}

/** Shared projective transform for the shell outline and everything drawn inside it. */
internal fun courseEditorTaperTransform(width: Float, height: Float, taper: Float): FloatArray {
    if (width <= 0f || height <= 0f || taper == 0f) {
        return floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f)
    }
    val topInset = taper.coerceIn(0f, 0.45f)
    val bottomInset = (-taper).coerceIn(0f, 0.45f)
    val topWidth = 1f - 2f * topInset
    val perspective = topWidth / (1f - 2f * bottomInset)
    return floatArrayOf(
        topWidth, width * (bottomInset * perspective - topInset) / height, topInset * width,
        0f, perspective, 0f,
        0f, (perspective - 1f) / height, 1f
    )
}

/** Uses the measured source column, so density, hidden weekends and scroll offset stay aligned. */
internal fun courseEditorWeekLandingBounds(
    source: Rect,
    original: CourseEntity,
    destination: CourseEntity,
    periodIndexes: List<Int>,
    rowHeight: Float,
    gap: Float
): Rect? {
    val start = original.periods.map(periodIndexes::indexOf).filter { it >= 0 }.minOrNull() ?: return null
    val positions = destination.periods.map(periodIndexes::indexOf).filter { it >= 0 }.sorted()
    val targetStart = positions.firstOrNull() ?: return null
    val left = source.left + (destination.weekday - original.weekday) * (source.width + gap)
    val top = source.top + (targetStart - start) * rowHeight
    val height = rowHeight * (positions.last() - targetStart + 1) - gap
    return Rect(left, top, left + source.width, top + height.coerceAtLeast(1f))
}
