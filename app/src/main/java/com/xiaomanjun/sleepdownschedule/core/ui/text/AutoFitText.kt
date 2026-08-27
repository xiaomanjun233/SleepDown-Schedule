package com.xiaomanjun.sleepdownschedule.core.ui.text

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

@Composable
internal fun AutoFitSingleLineText(
    text: String,
    color: Color,
    style: TextStyle
) {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    BoxWithConstraints(contentAlignment = Alignment.CenterStart) {
        val availableWidthPx = with(density) { maxWidth.toPx() }.roundToInt().coerceAtLeast(1)
        val fontSize = remember(text, availableWidthPx, density.fontScale, style) {
            listOf(style.fontSize, 15.sp, 14.sp, 13.sp, 12.sp)
                .distinct()
                .firstOrNull { candidate ->
                    textMeasurer.measure(
                        text = AnnotatedString(text),
                        style = style.copy(fontSize = candidate),
                        softWrap = false,
                        maxLines = 1,
                        constraints = Constraints()
                    ).size.width <= availableWidthPx
                } ?: 12.sp
        }
        Text(
            text = text,
            color = color,
            style = style.copy(fontSize = fontSize),
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
    }
}
