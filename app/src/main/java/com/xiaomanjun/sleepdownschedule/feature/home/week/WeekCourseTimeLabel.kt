package com.xiaomanjun.sleepdownschedule.feature.home.week

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.xiaomanjun.sleepdownschedule.feature.home.day.HomeReadableText

/** A complete clock interval must fit even in seven narrow columns and with custom fonts. */
@Composable
internal fun WeekCourseTimeLabel(text: String, color: Color, modifier: Modifier = Modifier,
    fontSize: TextUnit = 8.sp, lineHeight: TextUnit = 9.sp) {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold, fontSize = fontSize, lineHeight = lineHeight, letterSpacing = 0.sp)
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val widthPx = with(density) { maxWidth.toPx() }
        val naturalWidth = remember(text, style, measurer) {
            measurer.measure(text, style = style, softWrap = false, maxLines = 1).size.width
        }
        val fit = if (naturalWidth > 0) ((widthPx - 1f) / naturalWidth).coerceIn(0.01f, 1f) else 1f
        HomeReadableText(text, color = color, modifier = Modifier.fillMaxWidth(),
            style = style, fontSize = fontSize * fit, lineHeight = lineHeight,
            textAlign = TextAlign.Center, maxLines = 1, softWrap = false)
    }
}
