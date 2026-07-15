package com.example.courseschedule

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.catalog.components.LiquidPanel
import kotlinx.coroutines.launch
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.roundToInt

private val CourseEditorPrimaryEasing = CubicBezierEasing(0.20f, 0.0f, 0.10f, 1.0f)
private val CourseEditorSettleEasing = CubicBezierEasing(0.24f, 0.0f, 0.30f, 1.0f)
private const val CourseEditorOpenDurationMillis = 340
private const val CourseEditorCloseDurationMillis = 350

enum class CourseEditorOverlayPhase {
    Idle,
    Preparing,
    Opening,
    Open,
    Closing,
    Disposing
}

@Stable
class CourseEditorMotionState internal constructor() {
    val progress = Animatable(0f)
    var phase by mutableStateOf(CourseEditorOverlayPhase.Idle)
        internal set
}

@Composable
fun rememberCourseEditorMotionState(): CourseEditorMotionState = remember { CourseEditorMotionState() }

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
    motionState: CourseEditorMotionState,
    onRenderedCourseIdChange: (Long?) -> Unit = {},
    onPhaseChange: (CourseEditorOverlayPhase) -> Unit = {}
) {
    var renderedRequest by remember { mutableStateOf<CourseEditorOverlayRequest?>(null) }
    var rootSize by remember { mutableStateOf(IntSize.Zero) }
    var editorContentMounted by remember { mutableStateOf(false) }
    var editorContentReady by remember { mutableStateOf(false) }
    val progress = motionState.progress
    val editorContentAlpha = remember { Animatable(0f) }
    val editorContentReveal = remember { Animatable(0f) }
    val latestOnRenderedCourseIdChange by rememberUpdatedState(onRenderedCourseIdChange)
    val latestOnPhaseChange by rememberUpdatedState(onPhaseChange)

    fun updatePhase(phase: CourseEditorOverlayPhase) {
        motionState.phase = phase
        latestOnPhaseChange(phase)
    }

    LaunchedEffect(request) {
        if (request != null) {
            updatePhase(CourseEditorOverlayPhase.Preparing)
            renderedRequest = request
            editorContentAlpha.snapTo(1f)
            editorContentReveal.snapTo(1f)
            latestOnRenderedCourseIdChange(request.course.id)
            progress.snapTo(0f)
            editorContentReady = false
            editorContentMounted = true
            var waitedFrames = 0
            while (
                waitedFrames < 30 &&
                (rootSize.width <= 0 || rootSize.height <= 0 || !editorContentReady)
            ) {
                withFrameNanos { }
                waitedFrames++
            }
            repeat(3) { withFrameNanos { } }
            updatePhase(CourseEditorOverlayPhase.Opening)
            progress.animateTo(
                targetValue = 1f,
                animationSpec = keyframes {
                    durationMillis = CourseEditorOpenDurationMillis
                    0f at 0 using CourseEditorPrimaryEasing
                    1.018f at 286 using CourseEditorSettleEasing
                    1f at CourseEditorOpenDurationMillis
                }
            )
            updatePhase(CourseEditorOverlayPhase.Open)
        } else if (renderedRequest != null) {
            updatePhase(CourseEditorOverlayPhase.Closing)
            if (editorContentMounted) {
                kotlinx.coroutines.coroutineScope {
                    launch {
                        editorContentReveal.animateTo(
                            targetValue = 0f,
                            animationSpec = tween(durationMillis = 120, easing = CubicBezierEasing(0.22f, 0.0f, 0.18f, 1.0f))
                        )
                    }
                    launch {
                        editorContentAlpha.animateTo(
                            targetValue = 0f,
                            animationSpec = tween(durationMillis = 90, easing = CubicBezierEasing(0.22f, 0.0f, 0.18f, 1.0f))
                        )
                    }
                }
                editorContentMounted = false
                editorContentReady = false
                withFrameNanos { }
            } else {
                editorContentReveal.snapTo(0f)
                editorContentAlpha.snapTo(0f)
                editorContentReady = false
            }
            progress.animateTo(
                targetValue = 0f,
                animationSpec = keyframes {
                    durationMillis = CourseEditorCloseDurationMillis
                    1f at 0 using CourseEditorPrimaryEasing
                    -0.012f at 296 using CourseEditorSettleEasing
                    0f at CourseEditorCloseDurationMillis
                }
            )
            updatePhase(CourseEditorOverlayPhase.Disposing)
            renderedRequest = null
            latestOnRenderedCourseIdChange(null)
            updatePhase(CourseEditorOverlayPhase.Idle)
        } else {
            if (motionState.phase != CourseEditorOverlayPhase.Idle || editorContentMounted || renderedRequest != null) {
                updatePhase(CourseEditorOverlayPhase.Disposing)
            }
            editorContentMounted = false
            editorContentReady = false
            editorContentAlpha.snapTo(0f)
            editorContentReveal.snapTo(0f)
            latestOnRenderedCourseIdChange(null)
            if (motionState.phase != CourseEditorOverlayPhase.Idle) {
                updatePhase(CourseEditorOverlayPhase.Idle)
            }
        }
    }

    val overlayPhase = motionState.phase
    val isOverlayActive = overlayPhase != CourseEditorOverlayPhase.Idle
    val shownRequest = request ?: renderedRequest ?: return
    val formData = remember(state.config, state.periods) {
        CourseEditorFormData(
            config = state.config,
            periods = state.periods
        )
    }
    val saveEditedCourse = remember(shownRequest.course, shownRequest.targetWeek, onSave) {
        { edited: CourseEntity -> onSave(shownRequest.course, edited, shownRequest.targetWeek) }
    }
    val deleteEditedCourse = remember(shownRequest.targetWeek, onDelete) {
        { deleteCourse: CourseEntity -> onDelete(deleteCourse, shownRequest.targetWeek) }
    }
    BackHandler(enabled = isOverlayActive) {
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

    val validSource = validSourceRect(shownRequest.sourceBoundsInRoot, rootSize)
    val sourceRect = validSource ?: targetRect
    val hasSourceTransform = validSource != null
    val motionProgress = progress.value.coerceIn(-0.035f, 1.045f)
    val alphaProgress = progress.value.coerceIn(0f, 1f)
    val animatedRect = interpolateRectUnbounded(sourceRect, targetRect, motionProgress)
    val animatedModifier = Modifier
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
    val sourceCornerPx = remember(sourceRect, density) {
        with(density) { if (sourceRect.width >= 220.dp.toPx()) 16.dp.toPx() else 8.dp.toPx() }
    }
    val corner = with(density) {
        interpolateFloatUnbounded(sourceCornerPx, 32.dp.toPx(), motionProgress)
            .coerceIn(6.dp.toPx(), 36.dp.toPx())
            .toDp()
    }
    val backgroundDepthProgress = smoothStep(0.04f, 0.86f, alphaProgress)
    val contentAlpha = if (editorContentMounted) editorContentAlpha.value else 0f
    val contentReveal = if (editorContentMounted) editorContentReveal.value.coerceIn(0f, 1f) else 0f
    val morphSurfaceAlpha = 1f
    val editorFormBackdrop = backdrop
    val sourceCoverAlpha = if (hasSourceTransform) {
        when (overlayPhase) {
            CourseEditorOverlayPhase.Opening,
            CourseEditorOverlayPhase.Preparing -> 0f
            CourseEditorOverlayPhase.Closing,
            CourseEditorOverlayPhase.Disposing -> 1f - smoothStep(0.28f, 0.66f, alphaProgress)
            else -> 0f
        }
    } else {
        0f
    }
    val textColor = glassForegroundColor(config)
    val revealPath = remember { Path() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { rootSize = it }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { onDismissRequest() }
        ) {
            CourseEditorBackgroundDepthLayer(
                backdrop = backdrop,
                progress = backgroundDepthProgress,
                modifier = Modifier.fillMaxSize()
            )
        }
        CourseEditorAnimatedContainer(
            backdrop = backdrop,
            config = config,
            course = shownRequest.course,
            corner = corner,
            progress = alphaProgress,
            alpha = morphSurfaceAlpha,
            modifier = animatedModifier
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(corner))
            ) {
                if (editorContentMounted) {
                    CourseEditorScaledContentLayer(
                        animatedRect = animatedRect,
                        targetRect = targetRect,
                        contentAlpha = contentAlpha,
                        contentReveal = contentReveal,
                        revealPath = revealPath,
                        textColor = textColor,
                        formData = formData,
                        course = shownRequest.course,
                        backdrop = editorFormBackdrop,
                        onContentLaidOut = { editorContentReady = true },
                        onDismissRequest = onDismissRequest,
                        onSave = saveEditedCourse,
                        onDelete = deleteEditedCourse
                    )
                }
                if (sourceCoverAlpha > 0.001f) {
                    CourseEditorSourceShell(
                        course = shownRequest.course,
                        backdrop = backdrop,
                        config = config,
                        sourceIsWide = sourceRect.width >= with(density) { 220.dp.toPx() },
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { alpha = sourceCoverAlpha }
                    )
                }
            }
        }
    }
}

@Composable
private fun CourseEditorScaledContentLayer(
    animatedRect: Rect,
    targetRect: Rect,
    contentAlpha: Float,
    contentReveal: Float,
    revealPath: Path,
    textColor: Color,
    formData: CourseEditorFormData,
    course: CourseEntity,
    backdrop: Backdrop?,
    onContentLaidOut: () -> Unit,
    onDismissRequest: () -> Unit,
    onSave: (CourseEntity) -> Unit,
    onDelete: (CourseEntity) -> Unit
) {
    if (targetRect.width <= 1f || targetRect.height <= 1f || animatedRect.width <= 1f || animatedRect.height <= 1f) {
        return
    }
    val density = LocalDensity.current
    val scale = maxOf(
        animatedRect.width / targetRect.width,
        animatedRect.height / targetRect.height
    ).coerceAtLeast(0.001f)
    val translateX = (animatedRect.width - targetRect.width * scale) / 2f
    val translateY = (animatedRect.height - targetRect.height * scale) / 2f
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(32.dp))
            .graphicsLayer { alpha = contentAlpha }
            .drawWithContent {
                if (contentReveal >= 0.999f) {
                    drawContent()
                } else {
                    val radius = hypot(size.width / 2f, size.height / 2f) * contentReveal.coerceIn(0f, 1f)
                    revealPath.reset()
                    revealPath.addOval(
                        Rect(
                            left = size.width / 2f - radius,
                            top = size.height / 2f - radius,
                            right = size.width / 2f + radius,
                            bottom = size.height / 2f + radius
                        )
                    )
                    clipPath(revealPath) {
                        this@drawWithContent.drawContent()
                    }
                }
            }
    ) {
        Box(
            modifier = Modifier
                .size(
                    width = with(density) { targetRect.width.toDp() },
                    height = with(density) { targetRect.height.toDp() }
                )
                .onSizeChanged {
                    if (it.width > 0 && it.height > 0) onContentLaidOut()
                }
                .graphicsLayer {
                    transformOrigin = TransformOrigin(0f, 0f)
                    scaleX = scale
                    scaleY = scale
                    translationX = translateX
                    translationY = translateY
                }
        ) {
            CompositionLocalProvider(LocalContentColor provides textColor) {
                NormalizedCourseEditorScreen(
                    formData = formData,
                    initialCourse = course,
                    onCancel = onDismissRequest,
                    onSave = onSave,
                    onDelete = onDelete,
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
    course: CourseEntity,
    corner: androidx.compose.ui.unit.Dp,
    progress: Float,
    alpha: Float,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(corner)
    val finalDialogBlur = 10f
    val editorBlur = interpolateFloat(
        config.courseCardBlur,
        finalDialogBlur,
        smoothStep(0.62f, 1f, progress)
    )
    val lightGlass = glassUsesLightStyle(config)
    val dialogMaskAlpha = (if (lightGlass) 0.12f else 0.20f) * smoothStep(0.42f, 1f, progress)
    CourseGlassCard(
        backdrop = backdrop,
        config = config,
        course = course,
        modifier = modifier.graphicsLayer { this.alpha = alpha },
        shape = shape,
        blurOverride = editorBlur
    ) {
        Box(Modifier.fillMaxSize()) {
            if (dialogMaskAlpha > 0.001f) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = dialogMaskAlpha))
                )
            }
            content()
        }
    }
}

@Composable
private fun CourseEditorBackgroundDepthLayer(
    backdrop: Backdrop?,
    progress: Float,
    modifier: Modifier = Modifier
) {
    val safeProgress = progress.coerceIn(0f, 1f)
    if (safeProgress <= 0.001f) return

    val dimAlpha = interpolateFloat(0f, 0.42f, safeProgress)
    Box(modifier.background(Color.Black.copy(alpha = dimAlpha)))
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
    sourceIsWide: Boolean,
    modifier: Modifier = Modifier
) {
    Box(modifier) {
        if (sourceIsWide) {
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
    val cardColor = courseCardBaseColor(config, course).copy(alpha = config.cardAlpha.coerceIn(0f, 1f))
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
    val cardColor = courseCardBaseColor(config, course).copy(alpha = config.cardAlpha.coerceIn(0f, 1f))
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

        val canShowTeacher = hasTeacher && heightDp >= 52f
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = horizontalPadding, vertical = verticalPadding)
        ) {
            if (hasLocation && locationLines > 0) {
                Text(
                    locationText,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth(),
                    fontSize = locationFont,
                    lineHeight = locationLineHeight,
                    fontWeight = FontWeight.Medium,
                    color = textColor.copy(alpha = 0.78f),
                    maxLines = locationLines,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }
            Text(
                course.name,
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .padding(vertical = centerReserve),
                fontSize = nameFont,
                lineHeight = nameLineHeight,
                fontWeight = FontWeight.SemiBold,
                color = textColor,
                maxLines = nameLines,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
            if (canShowTeacher) {
                Text(
                    course.teacher,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth(),
                    fontSize = teacherFont,
                    lineHeight = teacherLineHeight,
                    fontWeight = FontWeight.Normal,
                    color = textColor.copy(alpha = 0.58f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
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
