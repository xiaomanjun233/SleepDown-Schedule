package com.xiaomanjun.sleepdownschedule.glass.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

private const val HueColumns = 12
private const val Columns = HueColumns + 1
private const val Rows = 7

// Preserve the hues and rows of the previous Miuix grid. Both hosts store opaque RGB.
private val palette = run {
    val saturation = floatArrayOf(.10f, .35f, .70f, 1f, 1f, 1f, 1f)
    val value = floatArrayOf(1f, 1f, 1f, .85f, .65f, .45f, .20f)
    List(Rows * Columns) { index ->
        val row = index / Columns
        val column = index % Columns
        if (column == HueColumns) Color.hsv(0f, 0f, 1f - row / (Rows - 1f))
        else Color.hsv(360f * column / HueColumns, saturation[row], value[row])
    }
}

@Composable
internal fun CoursePaletteGrid(color: Color, onColorChanged: (Color) -> Unit) {
    val change = rememberUpdatedState(onColorChanged)
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val selected = remember(color) {
        val rgb = color.copy(alpha = 1f)
        palette.indices.minBy { index ->
            val cell = palette[index]
            val dr = rgb.red - cell.red
            val dg = rgb.green - cell.green
            val db = rgb.blue - cell.blue
            dr * dr + dg * dg + db * db
        }
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Text("当前颜色", style = MaterialTheme.typography.labelMedium)
            Box(Modifier.size(32.dp, 20.dp).background(color, RoundedCornerShape(6.dp)))
        }
        Canvas(Modifier.fillMaxWidth().aspectRatio(Columns.toFloat() / Rows)
            .clip(RoundedCornerShape(16.dp))
            .semantics {
                contentDescription = "调色盘"
                stateDescription = "#" + (color.toArgb() and 0xFFFFFF).toString(16).padStart(6, '0').uppercase()
                customActions = listOf(
                    CustomAccessibilityAction("上一个颜色") {
                        change.value(palette[(selected + palette.lastIndex) % palette.size]); true
                    },
                    CustomAccessibilityAction("下一个颜色") {
                        change.value(palette[(selected + 1) % palette.size]); true
                    },
                )
            }
            .pointerInput(rtl) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    fun cell(point: Offset): Int {
                        val x = (point.x * Columns / size.width).toInt().coerceIn(0, Columns - 1)
                        val y = (point.y * Rows / size.height).toInt().coerceIn(0, Rows - 1)
                        return y * Columns + if (rtl) Columns - 1 - x else x
                    }
                    var last = cell(down.position)
                    change.value(palette[last])
                    down.consume()
                    while (true) {
                        val move = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                        if (!move.pressed || move.isConsumed) break
                        val next = cell(move.position)
                        if (next != last) {
                            last = next
                            change.value(palette[next])
                        }
                        move.consume()
                    }
                }
            }) {
            val w = size.width / Columns
            val h = size.height / Rows
            fun visualColumn(index: Int) = if (rtl) Columns - 1 - index % Columns else index % Columns
            palette.forEachIndexed { i, cell ->
                val column = visualColumn(i)
                val row = i / Columns
                val left = (w * column).roundToInt().toFloat()
                val top = (h * row).roundToInt().toFloat()
                drawRect(cell, Offset(left, top),
                    Size((w * (column + 1)).roundToInt() - left, (h * (row + 1)).roundToInt() - top))
            }
            val center = Offset(w * (visualColumn(selected) + .5f), h * (selected / Columns + .5f))
            val radius = minOf(w, h) * .34f
            drawCircle(Color.Black.copy(alpha = .35f), radius + 1.dp.toPx(), center, style = Stroke(2.dp.toPx()))
            drawCircle(Color.White, radius, center, style = Stroke(2.dp.toPx()))
        }
    }
}
