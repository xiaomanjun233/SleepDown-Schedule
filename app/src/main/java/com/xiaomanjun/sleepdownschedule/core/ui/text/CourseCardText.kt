package com.xiaomanjun.sleepdownschedule.core.ui.text

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit

/** Follow the card's resolved light/dark foreground without reading wallpaper pixels per frame. */
@Composable
internal fun CourseCardText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color,
    themeColor: Color?,
    style: TextStyle = LocalTextStyle.current,
    fontWeight: FontWeight? = null,
    fontSize: TextUnit = TextUnit.Unspecified,
    lineHeight: TextUnit = TextUnit.Unspecified,
    textAlign: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip
) {
    val foreground = remember(themeColor, color) {
        themeColor?.let {
            val useLightText = color.luminance() > 0.5f
            val contrastColor = if (useLightText) Color.White else Color.Black
            val base = it.copy(alpha = 1f)
            // Add a white component on dark backgrounds; on light backgrounds move toward ink.
            // Keep as much theme color as possible within the readable foreground brightness band.
            var minimumMix = if (useLightText) 0.28f else 0.18f
            var maximumMix = 1f
            repeat(8) {
                val mix = (minimumMix + maximumMix) / 2f
                val luminance = lerp(base, contrastColor, mix).luminance()
                if (if (useLightText) luminance >= 0.72f else luminance <= 0.08f) maximumMix = mix
                else minimumMix = mix
            }
            lerp(base, contrastColor, maximumMix)
        } ?: color
    }
    Text(
        text = text,
        modifier = modifier,
        color = foreground,
        style = style,
        fontWeight = if (themeColor != null) maxOf(fontWeight ?: style.fontWeight ?: FontWeight.Normal, FontWeight.Bold) else fontWeight,
        fontSize = fontSize,
        lineHeight = lineHeight,
        textAlign = textAlign,
        maxLines = maxLines,
        overflow = overflow
    )
}
