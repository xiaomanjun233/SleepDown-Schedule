package com.example.courseschedule

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.catalog.components.LiquidPanel
import kotlin.math.ceil
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

private val CourseEditorOpenEasing = Easing { fraction ->
    CubicBezierEasing(0.42f, 0.0f, 0.58f, 1.0f).transform(fraction)
}

private val CourseEditorCloseEasing = Easing { fraction ->
    val base = CubicBezierEasing(0.42f, 0.0f, 0.58f, 1.0f).transform(fraction)
    val rebound = if (fraction > 0.84f) {
        sin(((fraction - 0.84f) / 0.16f).coerceIn(0f, 1f) * PI).toFloat() * 0.008f
    } else {
        0f
    }
    base + rebound
}

data class CourseEditorOverlayRequest(
    val course: CourseEntity,
    val targetWeek: Int?,
    val sourceBoundsInRoot: Rect?
)

@Composable
fun CourseEditorContainerOverlayHost(
    request: CourseEditorOverlayRequest?,
    state: AppState,
    backdrop: Backdrop?,
    config: ScheduleConfigEntity,
    modifier: Modifier = Modifier,
    onDismissRequest: () -> Unit,
    onSave: (original: CourseEntity, edited: CourseEntity, targetWeek: Int?) -> Unit,
    onDelete: (course: CourseEntity, targetWeek: Int?) -> Unit,
    onRenderedCourseIdChange: (Long?) -> Unit = {}
) {
    var renderedRequest by remember { mutableStateOf<CourseEditorOverlayRequest?>(null) }
    var rootSize by remember { mutableStateOf(IntSize.Zero) }
    val progress = remember { Animatable(0f) }
    val latestOnRenderedCourseIdChange by rememberUpdatedState(onRenderedCourseIdChange)

    LaunchedEffect(request) {
        if (request != null) {
            renderedRequest = request
            latestOnRenderedCourseIdChange(request.course.id)
            progress.snapTo(0f)
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 320, easing = CourseEditorOpenEasing)
            )
        } else if (renderedRequest != null) {
            progress.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 320, easing = CourseEditorCloseEasing)
            )
            renderedRequest = null
            latestOnRenderedCourseIdChange(null)
        } else {
            latestOnRenderedCourseIdChange(null)
        }
    }

    val shownRequest = request ?: renderedRequest ?: return
    BackHandler(enabled = request != null || renderedRequest != null) {
        onDismissRequest()
    }

    val density = LocalDensity.current
    val targetRect = remember(rootSize, density) {
        with(density) {
            if (rootSize.width <= 0 || rootSize.height <= 0) {
                Rect.Zero
            } else {
                val maxWidth = minOf(rootSize.width * 0.92f, 600.dp.toPx())
                val maxHeight = minOf(rootSize.height * 0.82f, 600.dp.toPx())
                val left = (rootSize.width - maxWidth) / 2f
                val top = (rootSize.height - maxHeight) / 2f
                Rect(left, top, left + maxWidth, top + maxHeight)
            }
        }
    }
    if (targetRect == Rect.Zero) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .onSizeChanged { rootSize = it }
        )
        return
    }

    val sourceRect = validSourceRect(shownRequest.sourceBoundsInRoot, rootSize) ?: targetRect
    val motionProgress = progress.value.coerceIn(-0.035f, 1.045f)
    val alphaProgress = progress.value.coerceIn(0f, 1f)
    val animatedRect = interpolateRectUnbounded(sourceRect, targetRect, motionProgress)
    val sourceCorner = with(density) { if (sourceRect.width >= 220.dp.toPx()) 16.dp else 8.dp }
    val corner = with(density) { interpolateFloatUnbounded(sourceCorner.toPx(), 32.dp.toPx(), motionProgress).coerceIn(6.dp.toPx(), 36.dp.toPx()).toDp() }
    val scrimAlpha = interpolateFloat(0f, 0.34f, alphaProgress)
    val contentAlpha = smoothStep(0.76f, 0.96f, alphaProgress)
    val dialogSurfaceAlpha = smoothStep(0.58f, 0.88f, alphaProgress)
    val morphSurfaceAlpha = 1f - smoothStep(0.62f, 0.90f, alphaProgress)
    val shellAlpha = 1f - smoothStep(0.26f, 0.62f, alphaProgress)
    val textColor = glassForegroundColor(config)

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { rootSize = it }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = scrimAlpha))
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { onDismissRequest() }
        )
        CourseEditorAnimatedContainer(
            backdrop = backdrop,
            config = config,
            corner = corner,
            alpha = morphSurfaceAlpha,
            modifier = Modifier
                .offset {
                    IntOffset(
                        animatedRect.left.roundToInt(),
                        animatedRect.top.roundToInt()
                    )
                }
                .size(
                    width = with(density) { animatedRect.width.toDp() },
                    height = with(density) { animatedRect.height.toDp() }
                )
        ) {
            CourseEditorSourceShell(
                course = shownRequest.course,
                backdrop = backdrop,
                config = config,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = shellAlpha }
            )
        }
        CourseEditorFinalDialogSurface(
            backdrop = backdrop,
            config = config,
            alpha = dialogSurfaceAlpha,
            modifier = Modifier
                .offset {
                    IntOffset(
                        animatedRect.left.roundToInt(),
                        animatedRect.top.roundToInt()
                    )
                }
                .size(
                    width = with(density) { animatedRect.width.toDp() },
                    height = with(density) { animatedRect.height.toDp() }
                )
        )
        Box(
            modifier = Modifier
                .offset {
                    IntOffset(
                        targetRect.left.roundToInt(),
                        targetRect.top.roundToInt()
                    )
                }
                .size(
                    width = with(density) { targetRect.width.toDp() },
                    height = with(density) { targetRect.height.toDp() }
                )
                .clip(RoundedCornerShape(32.dp))
                .graphicsLayer { alpha = contentAlpha }
        ) {
            CompositionLocalProvider(LocalContentColor provides textColor) {
                NormalizedCourseEditorScreen(
                    state = state,
                    initialCourse = shownRequest.course,
                    onCancel = onDismissRequest,
                    onSave = { edited -> onSave(shownRequest.course, edited, shownRequest.targetWeek) },
                    onDelete = { course -> onDelete(course, shownRequest.targetWeek) },
                    backdrop = backdrop
                )
            }
        }
    }
}

@Composable
private fun CourseEditorAnimatedContainer(
    backdrop: Backdrop?,
    config: ScheduleConfigEntity,
    corner: androidx.compose.ui.unit.Dp,
    alpha: Float,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(corner)
    CourseGlassCard(
        backdrop = backdrop,
        config = config,
        modifier = modifier.graphicsLayer { this.alpha = alpha },
        shape = shape
    ) {
        Box(Modifier.fillMaxSize()) {
            content()
        }
    }
}

@Composable
private fun CourseEditorFinalDialogSurface(
    backdrop: Backdrop?,
    config: ScheduleConfigEntity,
    alpha: Float,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(32.dp)
    val lightGlass = glassUsesLightStyle(config)
    val surfaceColor = if (lightGlass) {
        Color.White.copy(alpha = 0.18f)
    } else {
        Color(0xFF121212).copy(alpha = 0.28f)
    }
    if (backdrop != null) {
        LiquidPanel(
            backdrop = backdrop,
            modifier = modifier.graphicsLayer { this.alpha = alpha },
            shape = shape,
            surfaceColor = surfaceColor
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(shape)
                    .background(Color.Black.copy(alpha = if (lightGlass) 0.12f else 0.20f))
            )
        }
    } else {
        Box(
            modifier = modifier
                .graphicsLayer { this.alpha = alpha }
                .clip(shape)
                .background(if (appUsesDarkTheme(config)) Color(0xFF1C1C1E) else Color.White)
        )
    }
}

@Composable
private fun CourseEditorSourceShell(
    course: CourseEntity,
    backdrop: Backdrop?,
    config: ScheduleConfigEntity,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier) {
        if (maxWidth >= 220.dp) {
            CourseEditorDaySourceContent(course, backdrop, config)
        } else {
            CourseEditorWeekSourceContent(course, backdrop, config)
        }
    }
}

@Composable
private fun CourseEditorDaySourceContent(
    course: CourseEntity,
    backdrop: Backdrop?,
    config: ScheduleConfigEntity
) {
    val cardColor = Color(config.cardColorArgb.toInt()).copy(alpha = config.cardAlpha.coerceIn(0.28f, 1f))
    val textColor =
        if (backdrop != null && config.courseCardGlassEnabled) LocalAdaptiveGlass.current.contentColor
        else readableOn(cardColor)
    val scale = config.courseCardFontScale.coerceIn(0.80f, 1.35f)
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            course.name,
            style = MaterialTheme.typography.titleMedium.scaledCourseEditorSourceStyle(scale),
            color = textColor,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        course.location?.takeIf { it.isNotBlank() }?.let {
            Text(
                "地点：" + it,
                color = textColor.copy(alpha = 0.86f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        course.teacher?.takeIf { it.isNotBlank() }?.let {
            Text(
                "教师：" + it,
                color = textColor.copy(alpha = 0.86f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun CourseEditorWeekSourceContent(
    course: CourseEntity,
    backdrop: Backdrop?,
    config: ScheduleConfigEntity
) {
    val locationText = course.location.orEmpty()
    val hasLocation = locationText.isNotBlank()
    val hasTeacher = !course.teacher.isNullOrBlank()
    val cardColor = Color(config.cardColorArgb.toInt()).copy(alpha = config.cardAlpha.coerceIn(0.28f, 1f))
    val textColor =
        if (backdrop != null && config.courseCardGlassEnabled) LocalAdaptiveGlass.current.contentColor
        else readableOn(cardColor)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val heightDp = maxHeight.value
        val widthDp = maxWidth.value
        val compact = heightDp < 78f
        val tiny = heightDp < 52f
        val verticalPadding = when {
            tiny -> 1.dp
            compact -> 2.dp
            else -> 2.5.dp
        }
        val horizontalPadding = if (widthDp < 54f) 4.dp else 5.dp
        val fontScaleCompensation = density.fontScale.coerceAtLeast(1f)
        val courseFontScale = config.courseCardFontScale.coerceIn(0.80f, 1.35f)
        fun scaledCourseWeekText(value: TextUnit): TextUnit {
            return (value.value * courseFontScale / fontScaleCompensation.coerceAtLeast(1f)).sp
        }
        val nameFont = scaledCourseWeekText(if (tiny) 8.8.sp else if (compact) 9.7.sp else 10.7.sp)
        val nameLineHeight = scaledCourseWeekText(if (tiny) 8.2.sp else if (compact) 9.1.sp else 10.0.sp)
        val locationFont = scaledCourseWeekText(if (tiny) 8.1.sp else if (compact) 8.7.sp else 9.5.sp)
        val locationLineHeight = scaledCourseWeekText(if (tiny) 8.0.sp else if (compact) 8.6.sp else 9.3.sp)
        val teacherFont = scaledCourseWeekText(8.4.sp)
        val teacherLineHeight = scaledCourseWeekText(7.9.sp)
        val contentWidthPx = with(density) { (maxWidth - horizontalPadding * 2f).coerceAtLeast(24.dp).toPx() }
        val availableTextPx = with(density) { (maxHeight - verticalPadding * 2f).coerceAtLeast(0.dp).toPx() }

        fun estimatedLines(text: String, fontSize: TextUnit): Int {
            if (text.isBlank()) return 0
            val averageCharPx = with(density) { fontSize.toPx() } * 1.08f
            val charsPerLine = (contentWidthPx / averageCharPx.coerceAtLeast(1f)).toInt().coerceAtLeast(1)
            return ceil(text.length.toFloat() / charsPerLine).toInt().coerceAtLeast(1)
        }

        val canShowTeacher = hasTeacher && heightDp >= 104f
        val teacherLines = if (canShowTeacher) 1 else 0
        val teacherPx = if (teacherLines > 0) with(density) { teacherLineHeight.toPx() } else 0f
        val usablePx = (availableTextPx - teacherPx).coerceAtLeast(0f)
        val averageLinePx = minOf(with(density) { nameLineHeight.toPx() }, with(density) { locationLineHeight.toPx() }).coerceAtLeast(1f)
        val totalSlots = (usablePx / averageLinePx).toInt().coerceAtLeast(1)
        val maxNameLines = when {
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

        Column(modifier = Modifier.padding(horizontal = horizontalPadding, vertical = verticalPadding), verticalArrangement = Arrangement.spacedBy(0.dp)) {
            Text(
                course.name,
                fontSize = nameFont,
                lineHeight = nameLineHeight,
                fontWeight = FontWeight.SemiBold,
                color = textColor,
                maxLines = nameLines,
                overflow = TextOverflow.Ellipsis
            )
            if (hasLocation && locationLines > 0) {
                Text(
                    locationText,
                    fontSize = locationFont,
                    lineHeight = locationLineHeight,
                    fontWeight = FontWeight.Medium,
                    color = textColor.copy(alpha = 0.78f),
                    maxLines = locationLines,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (canShowTeacher) {
                Text(
                    course.teacher,
                    fontSize = teacherFont,
                    lineHeight = teacherLineHeight,
                    fontWeight = FontWeight.Normal,
                    color = textColor.copy(alpha = 0.58f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun TextStyle.scaledCourseEditorSourceStyle(scale: Float): TextStyle {
    val safeScale = scale.coerceIn(0.80f, 1.35f)
    val scaledFontSize = if (fontSize == TextUnit.Unspecified) fontSize else (fontSize.value * safeScale).sp
    val scaledLineHeight = if (lineHeight == TextUnit.Unspecified) lineHeight else (lineHeight.value * safeScale).sp
    return copy(fontSize = scaledFontSize, lineHeight = scaledLineHeight)
}

private fun validSourceRect(rect: Rect?, rootSize: IntSize): Rect? {
    if (rect == null || rect.width <= 2f || rect.height <= 2f) return null
    if (rootSize.width <= 0 || rootSize.height <= 0) return null
    val root = Rect(0f, 0f, rootSize.width.toFloat(), rootSize.height.toFloat())
    return if (rect.overlaps(root)) rect else null
}

private fun interpolateRectUnbounded(start: Rect, stop: Rect, fraction: Float): Rect {
    return Rect(
        left = interpolateFloatUnbounded(start.left, stop.left, fraction),
        top = interpolateFloatUnbounded(start.top, stop.top, fraction),
        right = interpolateFloatUnbounded(start.right, stop.right, fraction),
        bottom = interpolateFloatUnbounded(start.bottom, stop.bottom, fraction)
    )
}

private fun interpolateFloat(start: Float, stop: Float, fraction: Float): Float {
    val safe = fraction.coerceIn(0f, 1f)
    return start + (stop - start) * safe
}

private fun interpolateFloatUnbounded(start: Float, stop: Float, fraction: Float): Float {
    return start + (stop - start) * fraction
}

private fun smoothStep(edge0: Float, edge1: Float, value: Float): Float {
    val t = ((value - edge0) / (edge1 - edge0).coerceAtLeast(0.0001f)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}
