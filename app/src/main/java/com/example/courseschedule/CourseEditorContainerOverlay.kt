package com.example.courseschedule

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.RenderEffect
import android.graphics.Shader
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
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
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.ContentScale
import com.kyant.backdrop.Backdrop
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sin

private val CourseEditorOpenPositionEasing = CubicBezierEasing(0.18f, 0.72f, 0.18f, 1.0f)
private val CourseEditorOpenSizeEasing = CubicBezierEasing(0.22f, 0.62f, 0.22f, 1.0f)
private val CourseEditorClosePositionEasing = CubicBezierEasing(0.30f, 0.10f, 0.22f, 1.0f)
private const val CourseEditorOpenDurationMillis = 380
private const val CourseEditorCloseDurationMillis = 440
// The background zoom runs on its own, slower timeline than the card morph so it keeps
// settling after the card has finished expanding/collapsing — reading as inertial pull
// on the home surface behind the editor rather than a motion locked to the card.
internal const val BackgroundZoomOpenScale = 1.08f
private const val BackgroundZoomDelayMillis = 40
private const val BackgroundZoomOpenDurationMillis = 560
private const val BackgroundZoomCloseDurationMillis = 560
private val BackgroundZoomInertialEasing = CubicBezierEasing(0.30f, 0.0f, 0.20f, 1.0f)


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
    val backgroundZoom = Animatable(1f)
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



/**
 * An elliptically compensated outline that still advertises itself as CornerBasedShape, which is
 * the contract required by the liquid lens shader. The clip uses independent X/Y radii so a tall
 * week card and a wide day card both meet their source snapshot without a one-frame corner jump.
 */
internal class CourseEditorMorphCornerShape(
    private val radiusX: Float,
    private val radiusY: Float,
    topStart: CornerSize = CornerSize(minOf(radiusX, radiusY)),
    topEnd: CornerSize = topStart,
    bottomEnd: CornerSize = topStart,
    bottomStart: CornerSize = topStart
) : CornerBasedShape(topStart, topEnd, bottomEnd, bottomStart) {
    override fun createOutline(
        size: androidx.compose.ui.geometry.Size,
        topStart: Float,
        topEnd: Float,
        bottomEnd: Float,
        bottomStart: Float,
        layoutDirection: LayoutDirection
    ): Outline = Outline.Rounded(
        RoundRect(
            left = 0f,
            top = 0f,
            right = size.width,
            bottom = size.height,
            cornerRadius = CornerRadius(radiusX, radiusY)
        )
    )

    override fun copy(
        topStart: CornerSize,
        topEnd: CornerSize,
        bottomEnd: CornerSize,
        bottomStart: CornerSize
    ): CornerBasedShape = CourseEditorMorphCornerShape(
        radiusX = radiusX,
        radiusY = radiusY,
        topStart = topStart,
        topEnd = topEnd,
        bottomEnd = bottomEnd,
        bottomStart = bottomStart
    )
}

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
            motionState.backgroundZoom.snapTo(1f)
            editorContentReady = false
            editorContentMounted = true
            var waitedFrames = 0
            while (waitedFrames < 12 && (rootSize.width <= 0 || rootSize.height <= 0 || !editorContentReady)) {
                withFrameNanos { }
                waitedFrames++
            }
            updatePhase(CourseEditorOverlayPhase.Opening)
            coroutineScope {
                launch {
                    progress.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(
                            durationMillis = CourseEditorOpenDurationMillis,
                            easing = LinearEasing
                        )
                    )
                }
                // The background depth (blur + zoom) trails the card on a longer, gentler
                // ease-out so it keeps settling after the card has opened — the inertial pull
                // on the home surface behind the editor.
                launch {
                    motionState.backgroundZoom.animateTo(
                        targetValue = BackgroundZoomOpenScale,
                        animationSpec = tween(
                            durationMillis = BackgroundZoomOpenDurationMillis,
                            delayMillis = BackgroundZoomDelayMillis,
                            easing = BackgroundZoomInertialEasing
                        )
                    )
                }
            }
            updatePhase(CourseEditorOverlayPhase.Open)
        } else if (renderedRequest != null) {
            updatePhase(CourseEditorOverlayPhase.Closing)
            coroutineScope {
                launch {
                    progress.animateTo(
                        targetValue = 0f,
                        animationSpec = tween(
                            durationMillis = CourseEditorCloseDurationMillis,
                            easing = LinearEasing
                        )
                    )
                }
                // Mirror the open: the background keeps easing back after the card has
                // collapsed, so closing reads as the home surface settling home rather
                // than snapping with the card.
                launch {
                    motionState.backgroundZoom.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(
                            durationMillis = BackgroundZoomCloseDurationMillis,
                            delayMillis = BackgroundZoomDelayMillis,
                            easing = BackgroundZoomInertialEasing
                        )
                    )
                }
            }
            editorContentMounted = false
            editorContentReady = false
            editorContentReveal.snapTo(0f)
            editorContentAlpha.snapTo(0f)
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
    val formData = remember(state.config, state.periods, state.courses) {
        CourseEditorFormData(
            config = state.config,
            periods = state.periods,
            courses = state.courses
        )
    }
    val saveEditedCourse = remember(shownRequest.targetWeek, onSave) {
        { original: CourseEntity, edited: CourseEntity -> onSave(original, edited, shownRequest.targetWeek) }
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
    val rawProgress = progress.value.coerceIn(0f, 1f)
    val visualProgress = courseEditorVisualProgress(rawProgress, overlayPhase)
    val positionProgress = visualProgress.position
    val sizeProgress = visualProgress.size
    val cornerProgress = smoothStep(0.04f, 0.90f, sizeProgress)
    val pulseScale = courseEditorSettleScale(rawProgress, overlayPhase)
    val animatedRect = curvedCourseEditorRect(
        source = sourceRect,
        target = targetRect,
        positionProgress = positionProgress,
        sizeProgress = sizeProgress,
        pulseScale = pulseScale,
        maxArcPx = with(density) { 48.dp.toPx() }
    )
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
        with(density) { if (sourceRect.width >= 220.dp.toPx()) 24.dp.toPx() else 8.dp.toPx() }
    }
    val corner = with(density) {
        interpolateFloat(sourceCornerPx, 32.dp.toPx(), cornerProgress)
            .coerceIn(6.dp.toPx(), 36.dp.toPx())
            .toDp()
    }
    // The source shell and the real form must hand off with one shared opacity curve.
    // Keeping the form fully opaque underneath the fading source was most visible for
    // wide day-view cards: both text layouts were composited for several frames and the
    // form appeared to flicker into place. Make the two layers complementary instead.
    val openingContentHandoff = smoothStep(0.12f, 0.50f, positionProgress)
    val closingContentHandoff = smoothStep(0.04f, 0.18f, positionProgress)
    val contentAlpha = if (editorContentMounted) {
        editorContentAlpha.value * when (overlayPhase) {
            CourseEditorOverlayPhase.Preparing,
            CourseEditorOverlayPhase.Opening -> openingContentHandoff
            CourseEditorOverlayPhase.Closing -> closingContentHandoff
            else -> 1f
        }
    } else {
        0f
    }
    val contentReveal = if (editorContentMounted) editorContentReveal.value.coerceIn(0f, 1f) else 0f
    val morphSurfaceAlpha = 1f
    val sourceCoverAlpha = if (hasSourceTransform) {
        when (overlayPhase) {
            CourseEditorOverlayPhase.Preparing,
            CourseEditorOverlayPhase.Opening -> 1f - smoothStep(0.12f, 0.50f, positionProgress)
            CourseEditorOverlayPhase.Closing,
            CourseEditorOverlayPhase.Disposing -> 1f - closingContentHandoff
            else -> 0f
        }
    } else 0f
    val editorFormBackdrop = backdrop
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
        )
        CourseEditorAnimatedContainer(
            backdrop = backdrop,
            config = config,
            course = shownRequest.course,
            corner = corner,
            progress = sizeProgress,
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
    onSave: (CourseEntity, CourseEntity) -> Unit,
    onDelete: (CourseEntity) -> Unit
) {
    if (targetRect.width <= 1f || targetRect.height <= 1f || animatedRect.width <= 1f || animatedRect.height <= 1f) {
        return
    }
    val density = LocalDensity.current
    // Fit the complete target form inside the animated glass shell. maxOf() behaved like
    // ContentScale.Crop: a narrow/tall week card chose the height ratio and permanently cut both
    // sides of the editor while it expanded. minOf() is the equivalent of ContentScale.Fit.
    val scale = minOf(
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
                    onSave = {},
                    onSaveWithOriginal = onSave,
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
    CourseGlassCard(
        backdrop = backdrop,
        config = config,
        course = course,
        modifier = modifier.graphicsLayer { this.alpha = alpha },
        shape = shape,
        blurOverride = editorBlur
    ) {
        Box(Modifier.fillMaxSize()) {
            content()
        }
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
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            course.name,
            style = MaterialTheme.typography.titleMedium,
            color = textColor
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
        course.note?.takeIf { it.isNotBlank() }?.let {
            Text(
                "备注：" + it,
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

private fun validSourceRect(rect: Rect?, rootSize: IntSize): Rect? {
    if (rect == null || rect.width <= 2f || rect.height <= 2f) return null
    if (rootSize.width <= 0 || rootSize.height <= 0) return null
    val root = Rect(0f, 0f, rootSize.width.toFloat(), rootSize.height.toFloat())
    if (!rect.overlaps(root)) return null
    // The full-screen snapshot is clipped to the Compose root. Keep the Morph geometry on the
    // exact same clipped rectangle; otherwise a partially off-screen card produces a smaller
    // bitmap that is stretched back over its original, larger bounds on the first/last frame.
    val clipped = Rect(
        left = maxOf(rect.left, root.left),
        top = maxOf(rect.top, root.top),
        right = minOf(rect.right, root.right),
        bottom = minOf(rect.bottom, root.bottom)
    )
    return clipped.takeIf { it.width > 2f && it.height > 2f }
}

private fun interpolateFloat(start: Float, stop: Float, fraction: Float): Float {
    val safe = fraction.coerceIn(0f, 1f)
    return start + (stop - start) * safe
}

private data class CourseEditorVisualProgress(
    val position: Float,
    val size: Float
)

private fun courseEditorVisualProgress(
    rawProgress: Float,
    phase: CourseEditorOverlayPhase
): CourseEditorVisualProgress {
    val raw = rawProgress.coerceIn(0f, 1f)
    return when (phase) {
        CourseEditorOverlayPhase.Closing,
        CourseEditorOverlayPhase.Disposing -> {
            // Keep the same reverse parabola, but let its center advance gently at first so the
            // close does not feel faster than the opening. Size contracts earlier and separately,
            // making the return arc visible instead of moving a nearly full-size dialog.
            val closingElapsed = 1f - raw
            val closingPosition = CourseEditorClosePositionEasing.transform(closingElapsed)
            val advancedClosingSize = (closingElapsed / 0.78f).coerceIn(0f, 1f)
            val closingSize = CourseEditorOpenSizeEasing.transform(advancedClosingSize)
            CourseEditorVisualProgress(
                position = (1f - closingPosition).coerceIn(0f, 1f),
                size = (1f - closingSize).coerceIn(0f, 1f)
            )
        }
        CourseEditorOverlayPhase.Open -> CourseEditorVisualProgress(position = 1f, size = 1f)
        else -> {
            val delayedSizeProgress = ((raw - 0.10f) / 0.90f).coerceIn(0f, 1f)
            CourseEditorVisualProgress(
                position = CourseEditorOpenPositionEasing.transform(raw).coerceIn(0f, 1f),
                size = CourseEditorOpenSizeEasing.transform(delayedSizeProgress).coerceIn(0f, 1f)
            )
        }
    }
}

internal fun courseEditorBackgroundBlurProgress(
    rawProgress: Float,
    phase: CourseEditorOverlayPhase
): Float {
    val position = courseEditorVisualProgress(rawProgress, phase).position
    return when (phase) {
        CourseEditorOverlayPhase.Open -> 1f
        CourseEditorOverlayPhase.Closing,
        CourseEditorOverlayPhase.Disposing -> smoothStep(0f, 0.72f, position)
        else -> smoothStep(0f, 0.72f, position)
    }
}

private fun courseEditorSettleScale(
    rawProgress: Float,
    phase: CourseEditorOverlayPhase
): Float {
    val raw = rawProgress.coerceIn(0f, 1f)
    return when (phase) {
        CourseEditorOverlayPhase.Opening -> {
            val pulse = ((raw - 0.78f) / 0.22f).coerceIn(0f, 1f)
            1f + sin(PI.toFloat() * pulse) * 0.008f
        }
        CourseEditorOverlayPhase.Closing -> {
            val closingElapsed = 1f - raw
            val pulse = ((closingElapsed - 0.78f) / 0.22f).coerceIn(0f, 1f)
            1f + sin(PI.toFloat() * pulse) * 0.008f
        }
        else -> 1f
    }
}

private fun curvedCourseEditorRect(
    source: Rect,
    target: Rect,
    positionProgress: Float,
    sizeProgress: Float,
    pulseScale: Float,
    maxArcPx: Float
): Rect {
    val positionT = positionProgress.coerceIn(0f, 1f)
    val sizeT = sizeProgress.coerceIn(0f, 1f)
    val sourceCenter = source.center
    val targetCenter = target.center
    val deltaY = targetCenter.y - sourceCenter.y
    val arcAmplitude = min(maxArcPx, abs(deltaY) * 0.22f)
    val controlX = (sourceCenter.x + targetCenter.x) / 2f
    val controlY = (sourceCenter.y + targetCenter.y) / 2f + sign(deltaY) * arcAmplitude
    val inverse = 1f - positionT
    val centerX =
        inverse * inverse * sourceCenter.x +
            2f * inverse * positionT * controlX +
            positionT * positionT * targetCenter.x
    val centerY =
        inverse * inverse * sourceCenter.y +
            2f * inverse * positionT * controlY +
            positionT * positionT * targetCenter.y
    val width = interpolateFloat(source.width, target.width, sizeT) * pulseScale
    val height = interpolateFloat(source.height, target.height, sizeT) * pulseScale
    return Rect(
        left = centerX - width / 2f,
        top = centerY - height / 2f,
        right = centerX + width / 2f,
        bottom = centerY + height / 2f
    )
}

private fun smoothStep(edge0: Float, edge1: Float, value: Float): Float {
    val t = ((value - edge0) / (edge1 - edge0).coerceAtLeast(0.0001f)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}
