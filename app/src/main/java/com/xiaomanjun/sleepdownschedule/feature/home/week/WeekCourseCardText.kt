package com.xiaomanjun.sleepdownschedule.feature.home.week

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xiaomanjun.sleepdownschedule.CourseEntity
import com.xiaomanjun.sleepdownschedule.ScheduleConfigEntity
import com.xiaomanjun.sleepdownschedule.WeekCardContentLayout
import com.xiaomanjun.sleepdownschedule.WeekCardTextAlignment
import com.xiaomanjun.sleepdownschedule.core.ui.text.CourseCardText
import com.xiaomanjun.sleepdownschedule.feature.home.LocalPersonalizationPreview
import com.xiaomanjun.sleepdownschedule.feature.home.day.homeCourseTextColor
import com.xiaomanjun.sleepdownschedule.feature.home.day.homeForegroundColor
import com.xiaomanjun.sleepdownschedule.model.effectiveWeekCardTextAlignment
import kotlin.math.ceil

@Composable
internal fun WeekCourseCardTextContent(
    course: CourseEntity, config: ScheduleConfigEntity, cardLayoutWidth: Dp, textHeight: Dp,
    gridColumnWidth: Dp, muted: Boolean, adaptiveContrast: Boolean = true
) {
    val density = LocalDensity.current
    val coloredText = !muted && config.courseCardColoredTextEnabled
    val cardTextAlign = config.weekCardTextAlign()
    val courseTextColor = homeCourseTextColor(config, course, homeForegroundColor(config), muted)
    val locationText = course.location.orEmpty()
    val hasLocation = config.weekCardShowLocation && locationText.isNotBlank()
    val hasTeacher = config.weekCardShowTeacher && !course.teacher.isNullOrBlank()
    val heightDp = textHeight.value
    val widthDp = cardLayoutWidth.value
    val compact = heightDp < 78f
    val tiny = heightDp < 52f
    val verticalPadding = when {
        tiny -> 1.dp
        compact -> 2.dp
        else -> 2.5.dp
    }
    val horizontalPadding = if (widthDp < 54f) 4.dp else 5.dp
    val fontScaleCompensation = density.fontScale.coerceAtLeast(1f)
    val tabletFontBoost = if (gridColumnWidth >= 120.dp) 1.18f else 1f
    val previewFontScale = LocalPersonalizationPreview.current?.cardFontScale
    val courseFontScale = ((previewFontScale ?: config.courseCardFontScale) * tabletFontBoost)
        .coerceIn(0.80f, 1.35f)
    fun scaledCourseWeekText(value: TextUnit): TextUnit = scaledWeekText((value.value * courseFontScale).sp, fontScaleCompensation)
    val nameFont = scaledCourseWeekText(if (tiny) 8.8.sp else if (compact) 9.7.sp else 10.7.sp)
    val nameLineHeight = (nameFont.value * 1.15f).sp
    val locationFont = scaledCourseWeekText(if (tiny) 8.1.sp else if (compact) 8.7.sp else 9.5.sp)
    val locationLineHeight = (locationFont.value * 1.15f).sp
    val teacherFont = scaledCourseWeekText(8.4.sp)
    val teacherLineHeight = (teacherFont.value * 1.15f).sp
    val contentWidthPx = with(density) { (cardLayoutWidth - horizontalPadding * 2f).coerceAtLeast(24.dp).toPx() }
    val availableTextPx = with(density) { (textHeight - verticalPadding * 2f).coerceAtLeast(0.dp).toPx() }

    fun estimatedLines(text: String, fontSize: TextUnit): Int {
        if (text.isBlank()) return 0
        val averageCharPx = with(density) { fontSize.toPx() } * 1.08f
        val charsPerLine = (contentWidthPx / averageCharPx.coerceAtLeast(1f)).toInt().coerceAtLeast(1)
        return ceil(text.length.toFloat() / charsPerLine).toInt().coerceAtLeast(1)
    }

    val canShowTeacher = hasTeacher && heightDp >= 52f
    val teacherLines = if (canShowTeacher) 1 else 0
    val teacherPx = if (teacherLines > 0) with(density) { teacherLineHeight.toPx() } else 0f
    val usablePx = (availableTextPx - teacherPx).coerceAtLeast(0f)
    val averageLinePx = minOf(with(density) { nameLineHeight.toPx() }, with(density) { locationLineHeight.toPx() }).coerceAtLeast(1f)
    val totalSlots = (usablePx / averageLinePx).toInt().coerceAtLeast(1)
    val maxNameLines = if (config.weekCardContentLayout in listOf(WeekCardContentLayout.TOP_DOWN, WeekCardContentLayout.TOP)) totalSlots else when {
        heightDp >= 150f -> 12
        heightDp >= 112f -> 9
        heightDp >= 78f -> 6
        else -> 4
    }
    val wantedNameLines = estimatedLines(course.name, nameFont).coerceIn(1, maxNameLines)
    val wantedLocationLines = if (hasLocation) {
        estimatedLines(locationText, locationFont).coerceIn(1, if (heightDp >= 150f) 4 else if (heightDp >= 96f) 3 else 2)
    } else {
        0
    }
    val nameMinimum = 1
    val locationMinimum = if (hasLocation && (totalSlots >= 2 || tiny)) 1 else 0
    var remainingSlots = (totalSlots - nameMinimum - locationMinimum).coerceAtLeast(0)
    var nameLines = nameMinimum
    var locationLines = locationMinimum
    var nameNeed = (wantedNameLines - nameLines).coerceAtLeast(0)
    var locationNeed = (wantedLocationLines - locationLines).coerceAtLeast(0)
    while (remainingSlots > 0 && (nameNeed > 0 || locationNeed > 0)) {
        if (nameNeed >= locationNeed && nameNeed > 0) {
            nameLines += 1
            nameNeed -= 1
        } else if (locationNeed > 0) {
            locationLines += 1
            locationNeed -= 1
        } else {
            nameLines += 1
            nameNeed -= 1
        }
        remainingSlots -= 1
    }
    if (remainingSlots > 0 && nameLines < maxNameLines) {
        val extraNameLines = minOf(remainingSlots, maxNameLines - nameLines)
        nameLines += extraNameLines
        remainingSlots -= extraNameLines
    }
    if (remainingSlots > 0 && hasLocation) {
        locationLines += remainingSlots
    }
    if (tiny && hasLocation) {
        locationLines = 1
        nameLines = (totalSlots - locationLines).coerceAtLeast(1)
    }

    val renderedLocationLines = minOf(locationLines, wantedLocationLines).coerceAtLeast(0)
    val locationReserve = if (hasLocation && renderedLocationLines > 0) {
        with(density) { (locationLineHeight.toPx() * renderedLocationLines).toDp() }
    } else {
        0.dp
    }
    val teacherReserve = if (canShowTeacher) {
        with(density) { teacherLineHeight.toPx().toDp() }
    } else {
        0.dp
    }
    val centerReserve = maxOf(locationReserve, teacherReserve) + 1.dp
    WeekCardTextBody(
        course = course, locationText = locationText, locationLines = locationLines,
        nameLines = nameLines, showTeacher = canShowTeacher,
        nameFont = nameFont, nameLineHeight = nameLineHeight,
        locationFont = locationFont, locationLineHeight = locationLineHeight,
        teacherFont = teacherFont, teacherLineHeight = teacherLineHeight,
        textColor = courseTextColor, coloredText = coloredText,
        currentTextAlign = cardTextAlign, layout = config.weekCardContentLayout,
        horizontalPadding = horizontalPadding, verticalPadding = verticalPadding,
        centerReserve = centerReserve, adaptiveContrast = adaptiveContrast
    )
}

internal fun ScheduleConfigEntity.weekCardTextAlign(): TextAlign = when (effectiveWeekCardTextAlignment()) {
    WeekCardTextAlignment.START -> TextAlign.Start
    WeekCardTextAlignment.CENTER -> TextAlign.Center
    WeekCardTextAlignment.END -> TextAlign.End
}

@Composable
private fun WeekCardTextBody(
    course: CourseEntity,
    locationText: String,
    locationLines: Int,
    nameLines: Int,
    showTeacher: Boolean,
    nameFont: TextUnit,
    nameLineHeight: TextUnit,
    locationFont: TextUnit,
    locationLineHeight: TextUnit,
    teacherFont: TextUnit,
    teacherLineHeight: TextUnit,
    textColor: ComposeColor,
    coloredText: Boolean,
    currentTextAlign: TextAlign,
    layout: WeekCardContentLayout,
    horizontalPadding: Dp,
    verticalPadding: Dp,
    centerReserve: Dp,
    adaptiveContrast: Boolean = true
) {
    Box(Modifier.fillMaxSize().padding(horizontal = horizontalPadding, vertical = verticalPadding)) {
        if (layout == WeekCardContentLayout.CURRENT) {
            Column(Modifier.fillMaxSize()) {
            if (locationLines > 0) {
                CourseCardText(locationText, coloredText = coloredText,
                    modifier = Modifier.fillMaxWidth(),
                    fontSize = locationFont, lineHeight = locationLineHeight,
                    fontWeight = if (coloredText) FontWeight.Bold else FontWeight.Medium,
                    color = textColor.copy(alpha = 0.78f), maxLines = locationLines,
                    overflow = TextOverflow.Ellipsis, textAlign = currentTextAlign,
                    adaptiveContrast = adaptiveContrast)
            }
            CourseCardText(course.name, coloredText = coloredText,
                modifier = Modifier.fillMaxWidth().weight(1f).wrapContentHeight(Alignment.CenterVertically),
                fontSize = nameFont, lineHeight = nameLineHeight,
                fontWeight = if (coloredText) FontWeight.ExtraBold else FontWeight.Bold,
                color = textColor, maxLines = nameLines,
                overflow = TextOverflow.Ellipsis, textAlign = currentTextAlign,
                adaptiveContrast = adaptiveContrast)
            if (showTeacher) {
                CourseCardText(course.teacher.orEmpty(), coloredText = coloredText,
                    modifier = Modifier.fillMaxWidth(),
                    fontSize = teacherFont, lineHeight = teacherLineHeight,
                    fontWeight = if (coloredText) FontWeight.Bold else FontWeight.Normal,
                    color = textColor.copy(alpha = 0.58f), maxLines = 1,
                    overflow = TextOverflow.Ellipsis, textAlign = currentTextAlign,
                    adaptiveContrast = adaptiveContrast)
            }
            }
        } else {
            val textAlign = currentTextAlign
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = if (layout == WeekCardContentLayout.CENTERED || layout == WeekCardContentLayout.MIDDLE) Arrangement.Center else Arrangement.Top
            ) {
                if (locationLines > 0) {
                    CourseCardText(locationText, coloredText = coloredText,
                        modifier = Modifier.fillMaxWidth(),
                        fontSize = locationFont, lineHeight = locationLineHeight,
                        fontWeight = if (coloredText) FontWeight.Bold else FontWeight.Medium,
                        color = textColor.copy(alpha = 0.78f), maxLines = locationLines,
                        overflow = TextOverflow.Ellipsis, textAlign = textAlign,
                        adaptiveContrast = adaptiveContrast)
                    Spacer(Modifier.height(1.dp))
                }
                CourseCardText(course.name, coloredText = coloredText,
                    modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                    fontSize = nameFont, lineHeight = nameLineHeight,
                    fontWeight = if (coloredText) FontWeight.ExtraBold else FontWeight.Bold,
                    color = textColor, maxLines = nameLines,
                    overflow = TextOverflow.Ellipsis, textAlign = textAlign,
                    adaptiveContrast = adaptiveContrast)
                if (showTeacher) {
                    Spacer(Modifier.height(1.dp))
                    CourseCardText(course.teacher.orEmpty(), coloredText = coloredText,
                        modifier = Modifier.fillMaxWidth(),
                        fontSize = teacherFont, lineHeight = teacherLineHeight,
                        fontWeight = if (coloredText) FontWeight.Bold else FontWeight.Normal,
                        color = textColor.copy(alpha = 0.58f), maxLines = 1,
                        overflow = TextOverflow.Ellipsis, textAlign = textAlign,
                        adaptiveContrast = adaptiveContrast)
                }
            }
        }
    }
}

private fun scaledWeekText(value: TextUnit, fontScale: Float): TextUnit {
    return (value.value / fontScale.coerceAtLeast(1f)).sp
}

internal fun androidx.compose.ui.text.TextStyle.scaledCourseCardStyle(scale: Float): androidx.compose.ui.text.TextStyle {
    val safeScale = scale.coerceIn(0.80f, 1.35f)
    val scaledFontSize = if (fontSize == TextUnit.Unspecified) fontSize else (fontSize.value * safeScale).sp
    val scaledLineHeight = if (lineHeight == TextUnit.Unspecified) lineHeight else (lineHeight.value * safeScale).sp
    return copy(fontSize = scaledFontSize, lineHeight = scaledLineHeight)
}
