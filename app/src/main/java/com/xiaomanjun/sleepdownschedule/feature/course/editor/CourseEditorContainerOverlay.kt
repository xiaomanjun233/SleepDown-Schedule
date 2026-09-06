package com.xiaomanjun.sleepdownschedule.feature.course.editor

import com.xiaomanjun.sleepdownschedule.glass.ui.*
import com.xiaomanjun.sleepdownschedule.feature.home.*
import com.xiaomanjun.sleepdownschedule.feature.home.day.*

import com.xiaomanjun.sleepdownschedule.*

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
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
import androidx.compose.ui.graphics.asComposePath
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.zIndex
import com.kyant.backdrop.Backdrop
import com.kyant.shapes.RoundedRectangle
import com.xiaomanjun.sleepdownschedule.glass.LiquidBackdropDepthFrame
import com.xiaomanjun.sleepdownschedule.glass.LiquidContentHandoffFrame
import com.xiaomanjun.sleepdownschedule.glass.LiquidLayerLifecycleFrame
import com.xiaomanjun.sleepdownschedule.glass.LiquidMorphDirection
import com.xiaomanjun.sleepdownschedule.glass.LiquidMorphController
import com.xiaomanjun.sleepdownschedule.glass.LiquidMorphControllerBridge
import com.xiaomanjun.sleepdownschedule.glass.LiquidMorphFrame
import com.xiaomanjun.sleepdownschedule.glass.LiquidMorphInput
import com.xiaomanjun.sleepdownschedule.glass.LiquidMorphPhase
import com.xiaomanjun.sleepdownschedule.glass.LiquidMorphSpec
import com.xiaomanjun.sleepdownschedule.glass.LiquidMotionSample
import com.xiaomanjun.sleepdownschedule.glass.LiquidProgressKinematics
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
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
internal const val CourseEditorCloseDurationMillis = 440
// The background zoom runs on its own, slower timeline than the card morph so it keeps
// settling after the card has finished expanding/collapsing — reading as inertial pull
// on the home surface behind the editor rather than a motion locked to the card.
internal const val BackgroundZoomOpenScale = 1.08f
private const val BackgroundZoomDelayMillis = 40
private const val BackgroundZoomOpenDurationMillis = 560
private const val BackgroundZoomCloseDurationMillis = 560
private val BackgroundZoomInertialEasing = CubicBezierEasing(0.30f, 0.0f, 0.20f, 1.0f)
// How small the real form starts inside the morphing shell. This is a settle scale, not a
// fit-to-source scale: the shell's clip does the reveal, so keep it close to 1. Lower values
// reintroduce the shrunken-thumbnail look; 1.0 removes the sense of the content growing.
private const val CourseEditorContentSettleScale = 0.94f
private const val CourseEditorPreparedFrameCount = 2
// Each row settles over 340ms; 15ms staggering keeps the full reveal compact at 520ms.
internal const val CourseEditorFormRevealDurationMillis = 520
private val CourseEditorRowRevealEasing = CubicBezierEasing(0.22f, 0f, 0.30f, 1f)
// 需要逐行飞入的行数：固定标题栏 + 表单行（课程名称/教师/地点/星期/节次/周次/单双周/颜色/备注/删除/错误）。
internal const val CourseEditorFormRowCount = 12

internal fun courseEditorContentReadyForMotion(
    rootWidth: Int,
    rootHeight: Int,
    contentLaidOut: Boolean,
    recordedFrameCount: Int
): Boolean =
    rootWidth > 0 &&
        rootHeight > 0 &&
        contentLaidOut &&
        recordedFrameCount >= CourseEditorPreparedFrameCount


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
    private val controllerBridge = LiquidMorphControllerBridge { "course-editor" }
    val liquidMorphController: LiquidMorphController get() = controllerBridge.controller
    private var currentPhase by mutableStateOf(CourseEditorOverlayPhase.Idle)
    var phase: CourseEditorOverlayPhase
        get() = currentPhase
        internal set(value) {
            if (currentPhase == value) return
            currentPhase = value
            controllerBridge.synchronize(
                when (value) {
                    CourseEditorOverlayPhase.Idle -> LiquidMorphPhase.Idle
                    CourseEditorOverlayPhase.Preparing -> LiquidMorphPhase.Preparing
                    CourseEditorOverlayPhase.Opening -> LiquidMorphPhase.Opening
                    CourseEditorOverlayPhase.Open -> LiquidMorphPhase.Open
                    CourseEditorOverlayPhase.Closing -> LiquidMorphPhase.Closing
                    CourseEditorOverlayPhase.Disposing -> LiquidMorphPhase.Released
                }
            )
        }
    var closingSourceBoundsOverride by mutableStateOf<Rect?>(null)
        internal set

    fun retractTo(boundsInRoot: Rect?) {
        closingSourceBoundsOverride = boundsInRoot
    }
}

@Composable
fun rememberCourseEditorMotionState(): CourseEditorMotionState = remember { CourseEditorMotionState() }

data class CourseEditorOverlayRequest(
    val course: CourseEntity,
    val targetWeek: Int?,
    val sourceBoundsInRoot: Rect?,
    val sourceIsDayCard: Boolean = false
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
    ): Outline = Outline.Generic(
        com.xiaomanjun.sleepdownschedule.core.ui.designsystem.continuousRoundedRectPath(
            android.graphics.RectF(0f, 0f, size.width, size.height), radiusX, radiusY
        ).asComposePath()
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
internal fun CourseEditorContainerOverlayHost(
    request: CourseEditorOverlayRequest?,
    state: AppState,
    backdrop: Backdrop?,
    config: ScheduleConfigEntity,
    adaptiveMetrics: HomeAdaptiveMetrics,
    modifier: Modifier = Modifier,
    awaitOpeningGate: suspend () -> Unit = {},
    onDismissRequest: () -> Unit,
    onSave: (originals: List<CourseEntity>, edited: List<CourseEntity>, targetWeek: Int?) -> Unit,
    onDelete: (courses: List<CourseEntity>, targetWeek: Int?) -> Unit,
    motionState: CourseEditorMotionState,
    onRenderedCourseIdChange: (Long?) -> Unit = {},
    onPhaseChange: (CourseEditorOverlayPhase) -> Unit = {}
) {
    var renderedRequest by remember { mutableStateOf<CourseEditorOverlayRequest?>(null) }
    var rootSize by remember { mutableStateOf(IntSize.Zero) }
    var editorContentMounted by remember { mutableStateOf(false) }
    var editorContentReady by remember { mutableStateOf(false) }
    val editorContentRecordedFrames = remember { AtomicInteger(0) }
    val progress = motionState.progress
    val editorContentAlpha = remember { Animatable(0f) }
    val editorContentReveal = remember { Animatable(0f) }
    val latestOnRenderedCourseIdChange by rememberUpdatedState(onRenderedCourseIdChange)
    val latestOnPhaseChange by rememberUpdatedState(onPhaseChange)
    val latestAwaitOpeningGate by rememberUpdatedState(awaitOpeningGate)

    fun updatePhase(phase: CourseEditorOverlayPhase) {
        motionState.phase = phase
        latestOnPhaseChange(phase)
    }

    LaunchedEffect(request) {
        if (request != null) {
            motionState.closingSourceBoundsOverride = null
            updatePhase(CourseEditorOverlayPhase.Preparing)
            renderedRequest = request
            editorContentAlpha.snapTo(1f)
            editorContentReveal.snapTo(1f)
            latestOnRenderedCourseIdChange(request.course.id)
            progress.snapTo(0f)
            motionState.backgroundZoom.snapTo(1f)
            editorContentReady = false
            editorContentRecordedFrames.set(0)
            editorContentMounted = true
            var waitedFrames = 0
            while (
                waitedFrames < 12 &&
                !courseEditorContentReadyForMotion(
                    rootWidth = rootSize.width,
                    rootHeight = rootSize.height,
                    contentLaidOut = editorContentReady,
                    recordedFrameCount = editorContentRecordedFrames.get()
                )
            ) {
                withFrameNanos { }
                waitedFrames++
            }
            latestAwaitOpeningGate()
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
                    // Content may mount as soon as the shell finishes; background depth
                    // continues independently and must not delay the Open handoff.
                    updatePhase(CourseEditorOverlayPhase.Open)
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
            editorContentRecordedFrames.set(0)
            editorContentReveal.snapTo(0f)
            editorContentAlpha.snapTo(0f)
            updatePhase(CourseEditorOverlayPhase.Disposing)
            renderedRequest = null
            latestOnRenderedCourseIdChange(null)
            motionState.closingSourceBoundsOverride = null
            updatePhase(CourseEditorOverlayPhase.Idle)
        } else {
            if (motionState.phase != CourseEditorOverlayPhase.Idle || editorContentMounted || renderedRequest != null) {
                updatePhase(CourseEditorOverlayPhase.Disposing)
            }
            editorContentMounted = false
            editorContentReady = false
            editorContentRecordedFrames.set(0)
            editorContentAlpha.snapTo(0f)
            editorContentReveal.snapTo(0f)
            latestOnRenderedCourseIdChange(null)
            motionState.closingSourceBoundsOverride = null
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
        { originals: List<CourseEntity>, edited: List<CourseEntity> ->
            onSave(originals, edited, shownRequest.targetWeek)
        }
    }
    val deleteEditedCourse = remember(shownRequest.targetWeek, onDelete) {
        { courses: List<CourseEntity> -> onDelete(courses, shownRequest.targetWeek) }
    }
    BackHandler(enabled = isOverlayActive) {
        onDismissRequest()
    }

    val density = LocalDensity.current
    val targetRect = remember(rootSize, density, adaptiveMetrics) {
        with(density) {
            if (rootSize.width <= 0 || rootSize.height <= 0) {
                Rect.Zero
            } else if (adaptiveMetrics.isLargeScreen) {
                val content = adaptiveMetrics.contentRectPx(rootSize, density.density)
                val minWidth = minOf(600.dp.toPx(), content.width)
                val maxWidth = minOf(680.dp.toPx(), content.width).coerceAtLeast(minWidth)
                val maxHeight = minOf(content.height * 0.82f, 640.dp.toPx()).coerceAtLeast(
                    minOf(content.height, 420.dp.toPx())
                )
                val left = content.left + (content.width - maxWidth) / 2f
                val top = content.top + (content.height - maxHeight) / 2f
                Rect(left, top, left + maxWidth, top + maxHeight)
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

    val requestedSourceBounds = if (
        overlayPhase == CourseEditorOverlayPhase.Closing ||
        overlayPhase == CourseEditorOverlayPhase.Disposing
    ) {
        motionState.closingSourceBoundsOverride ?: shownRequest.sourceBoundsInRoot
    } else {
        shownRequest.sourceBoundsInRoot
    }
    val validSource = validSourceRect(requestedSourceBounds, rootSize)
    val sourceRect = validSource ?: targetRect
    val hasSourceTransform = validSource != null
    // targetWeek is also required when a day-view course is edited, so it cannot identify
    // the visual source. Keep the source layout captured with the request instead.
    val sourceIsWeekCard = !shownRequest.sourceIsDayCard
    val previewCornerProgress = LocalPersonalizationPreview.current?.weekCardCornerProgress
        ?: config.weekCardCornerProgress
    val sourceCornerPx = remember(
        sourceRect,
        density,
        adaptiveMetrics,
        sourceIsWeekCard,
        previewCornerProgress
    ) {
        with(density) {
            if (sourceIsWeekCard) {
                adaptiveWeekCardCornerRadius(
                    cardWidth = sourceRect.width.toDp(),
                    cardHeight = sourceRect.height.toDp(),
                    windowWidth = adaptiveMetrics.screenWidth,
                    windowHeight = adaptiveMetrics.screenHeight,
                    progress = previewCornerProgress
                ).toPx()
            } else {
                24.dp.toPx()
            }
        }
    }
    val morphSpec = remember(
        sourceRect,
        targetRect,
        sourceCornerPx,
        adaptiveMetrics,
        density.density,
        hasSourceTransform
    ) {
        legacyCourseEditorMorphSpec(
            source = sourceRect,
            target = targetRect,
            sourceCornerRadiusPx = sourceCornerPx,
            targetCornerRadiusPx = with(density) { 32.dp.toPx() },
            maximumArcPx = with(density) { adaptiveMetrics.animationArc.toPx() },
            sourceBlurMaxPx = with(density) { 6.dp.toPx() },
            destinationBlurMaxPx = with(density) { 5.dp.toPx() },
            hasSourceTransform = hasSourceTransform
        )
    }
    val rawProgress = progress.value.coerceIn(0f, 1f)
    val closingMorph = overlayPhase == CourseEditorOverlayPhase.Closing ||
        overlayPhase == CourseEditorOverlayPhase.Disposing
    val morphFrame = morphSpec.frame(
        LiquidMorphInput(
            source = sourceRect,
            target = targetRect,
            rawProgress = rawProgress,
            direction = if (closingMorph) {
                LiquidMorphDirection.Closing
            } else {
                LiquidMorphDirection.Opening
            },
            backdropScale = motionState.backgroundZoom.value,
            backdropBlurPx = with(density) {
                12.dp.toPx() * homeOverlayDepthProgress(motionState.backgroundZoom.value)
            },
            useCachedBackdrop = overlayPhase == CourseEditorOverlayPhase.Preparing ||
                overlayPhase == CourseEditorOverlayPhase.Opening ||
                overlayPhase == CourseEditorOverlayPhase.Closing ||
                overlayPhase == CourseEditorOverlayPhase.Disposing
        )
    )
    val sizeProgress = morphFrame.shapeProgress
    val animatedRect = morphFrame.rect
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
    val corner = with(density) {
        morphFrame.cornerRadiusPx
            .coerceIn(6.dp.toPx(), 36.dp.toPx())
            .toDp()
    }
    // The source shell and the real form must hand off with one shared opacity curve.
    // Keeping the form fully opaque underneath the fading source was most visible for
    // wide day-view cards: both text layouts were composited for several frames and the
    // form appeared to flicker into place. Make the two layers complementary instead.
    val contentAlpha = if (editorContentMounted) {
        editorContentAlpha.value * when (overlayPhase) {
            CourseEditorOverlayPhase.Preparing,
            CourseEditorOverlayPhase.Opening,
            CourseEditorOverlayPhase.Closing -> morphFrame.content.destinationContentAlpha
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
            CourseEditorOverlayPhase.Opening -> morphFrame.content.sourceAlpha
            CourseEditorOverlayPhase.Closing,
            CourseEditorOverlayPhase.Disposing -> morphFrame.content.sourceAlpha
            else -> 0f
        }
    } else 0f
    val sourceContentBlurPx = morphFrame.content.sourceBlurPx
    val editorContentBlurPx = when (overlayPhase) {
        CourseEditorOverlayPhase.Preparing,
        CourseEditorOverlayPhase.Opening,
        CourseEditorOverlayPhase.Closing,
        CourseEditorOverlayPhase.Disposing -> morphFrame.content.destinationBlurPx
        CourseEditorOverlayPhase.Open,
        CourseEditorOverlayPhase.Idle -> 0f
    }
    val editorFormBackdrop = backdrop
    val textColor = if (backdrop != null) {
        LocalAdaptiveGlass.current.contentColor
    } else {
        glassForegroundColor(config)
    }
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
                    .clip(RoundedRectangle(corner))
            ) {
                if (editorContentMounted) {
                    CourseEditorScaledContentLayer(
                        phase = overlayPhase,
                        animatedRect = animatedRect,
                        targetRect = targetRect,
                        sizeProgress = sizeProgress,
                        corner = corner,
                        contentAlpha = contentAlpha,
                        contentBlurRadiusPx = editorContentBlurPx,
                        contentReveal = contentReveal,
                        revealPath = revealPath,
                        textColor = textColor,
                        formData = formData,
                        course = shownRequest.course,
                        backdrop = editorFormBackdrop,
                        onContentLaidOut = { editorContentReady = true },
                        onContentRecorded = { editorContentRecordedFrames.incrementAndGet() },
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
                        sourceIsWide = !sourceIsWeekCard,
                        adaptiveMetrics = adaptiveMetrics,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                alpha = sourceCoverAlpha
                                val blurPx = sourceContentBlurPx
                                compositingStrategy = CompositingStrategy.Offscreen
                                renderEffect = platformBlurRenderEffect(blurPx)
                            }
                    )
                }
            }
        }
    }
}

@Composable
private fun CourseEditorScaledContentLayer(
    phase: CourseEditorOverlayPhase,
    animatedRect: Rect,
    targetRect: Rect,
    sizeProgress: Float,
    corner: androidx.compose.ui.unit.Dp,
    contentAlpha: Float,
    contentBlurRadiusPx: Float,
    contentReveal: Float,
    revealPath: Path,
    textColor: Color,
    formData: CourseEditorFormData,
    course: CourseEntity,
    backdrop: Backdrop?,
    onContentLaidOut: () -> Unit,
    onContentRecorded: () -> Unit,
    onDismissRequest: () -> Unit,
    onSave: (List<CourseEntity>, List<CourseEntity>) -> Unit,
    onDelete: (List<CourseEntity>) -> Unit
) {
    if (targetRect.width <= 1f || targetRect.height <= 1f || animatedRect.width <= 1f || animatedRect.height <= 1f) {
        return
    }
    val density = LocalDensity.current
    val editorContentLayer = rememberGraphicsLayer()
    var pagerPresentation by remember(formData, course) {
        mutableStateOf<CourseEditorPagerPresentation?>(null)
    }
    // Keep the heavy form unmounted throughout Opening. Open follows the shell completion,
    // independently of the trailing background animation, and mounts the form immediately.
    // 载入：顶栏先出现，向下逐行推进；每行从下方淡入、放大并上移到位。
    // 关闭时卸载表单内容，并回放一帧已卸载表单的空壳，避免输入框残留。
    var formMounted by remember(formData, course) { mutableStateOf(false) }
    // Closing 需要录制一帧“表单已卸载”的空壳用于收回动画回放；打开新表单时重置。
    val formClosingShellRecorded = remember(formData, course) { AtomicBoolean(false) }
    val formStagger = remember(formData, course) { Animatable(0f) }
    LaunchedEffect(phase, formData, course) {
        if (phase == CourseEditorOverlayPhase.Closing) {
            formMounted = false
            return@LaunchedEffect
        }
        if (phase == CourseEditorOverlayPhase.Opening) {
            formClosingShellRecorded.set(false)
        }
        if (phase == CourseEditorOverlayPhase.Open) formMounted = true
    }
    // Keep the reveal alive across Opening -> Open; phase changes must not cancel it.
    LaunchedEffect(formMounted, formData, course) {
        formStagger.snapTo(0f)
        if (formMounted) {
            formStagger.animateTo(
                1f,
                tween(CourseEditorFormRevealDurationMillis, easing = LinearEasing)
            )
        }
    }
    // Top-to-bottom order, with overlapping, independently eased upward entrances.
    val courseEditorFormRowEntrance: (Int) -> Float = { rowIndex ->
        if (!formMounted) {
            1f
        } else {
            val delayMillis = rowIndex.coerceIn(0, CourseEditorFormRowCount) * 15f
            val t = ((formStagger.value * CourseEditorFormRevealDurationMillis - delayMillis) / 340f)
                .coerceIn(0f, 1f)
            CourseEditorRowRevealEasing.transform(t)
        }
    }
    /*
     * Container transform: the form keeps its real layout size and the animated shell's clip
     * reveals a window onto it, so every frame shows correctly proportioned content.
     *
     * Scaling the whole form to fit inside the source rect is what looked wrong at handoff.
     * Neither ratio works: maxOf() crops, and minOf() letterboxes — for a wide day card
     * (~380x90dp against a 378x600dp form) minOf picks the 0.15 height ratio, shrinking the
     * entire editor into an illegible thumbnail flanked by ~162dp of empty band on each side.
     * Only a gentle settle scale remains, so the growth still reads as connected.
     */
    val settleScale = CourseEditorContentSettleScale +
        (1f - CourseEditorContentSettleScale) * sizeProgress.coerceIn(0f, 1f)
    val translateX = (animatedRect.width - targetRect.width * settleScale) / 2f
    val translateY = (animatedRect.height - targetRect.height * settleScale) / 2f
    Box(
        modifier = Modifier
            .fillMaxSize()
            // Follow the shell's animated corner instead of a fixed 32dp, which turned the
            // small early rectangle into a pill and rounded the reveal window too hard.
            .clip(RoundedRectangle(corner))
            .graphicsLayer {
                alpha = contentAlpha
                val blurPx = contentBlurRadiusPx
                compositingStrategy = CompositingStrategy.Offscreen
                renderEffect = platformBlurRenderEffect(blurPx)
            }
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
                    scaleX = settleScale
                    scaleY = settleScale
                    translationX = translateX
                    translationY = translateY
                },
            contentAlignment = Alignment.TopStart
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                .drawWithContent {
                    // Closing：表单已卸载（formMounted=false 生效）后的首个绘制帧录制一次
                    // 空壳，供收回动画回放，避免输入框在收回过程中残留。
                    val formClosingShellRecord =
                        phase == CourseEditorOverlayPhase.Closing &&
                            !formMounted &&
                            formClosingShellRecorded.compareAndSet(false, true)
                    if (phase == CourseEditorOverlayPhase.Preparing ||
                        phase == CourseEditorOverlayPhase.Open ||
                        formClosingShellRecord
                    ) {
                        editorContentLayer.record {
                            this@drawWithContent.drawContent()
                        }
                        if (phase == CourseEditorOverlayPhase.Preparing) {
                            // HorizontalPager needs a second target-size placement before this
                            // layer becomes the moving replay.
                            // Count completed recordings rather than merely the outer size callback
                            // so Opening can never reuse the Pager's provisional first layout.
                            onContentRecorded()
                        }
                    }
                    if (phase == CourseEditorOverlayPhase.Open) {
                        this@drawWithContent.drawContent()
                    } else {
                        // The form keeps its real target-size layout, but Opening/Closing replay
                        // the prepared GPU layer. Text fields, pickers and their backdrop consumers
                        // no longer re-record while the shell's clip and position change.
                        // On Closing the form is unmounted first (formMounted = false), so the
                        // replayed shell is clean and no input field lingers during the collapse.
                        drawLayer(editorContentLayer)
                    }
                }
            ) {
                if (formMounted) {
                    CompositionLocalProvider(LocalContentColor provides textColor) {
                        NormalizedCourseEditorScreen(
                            formData = formData,
                            initialCourse = course,
                            onCancel = onDismissRequest,
                            onSave = {},
                            onSaveGroup = onSave,
                            onDelete = {},
                            onDeleteGroup = onDelete,
                            backdrop = backdrop,
                            renderPagerIndicator = false,
                            rowEntrance = courseEditorFormRowEntrance,
                            onPagerPresentationChange = { presentation ->
                                if (pagerPresentation != presentation) {
                                    pagerPresentation = presentation
                                }
                            }
                        )
                    }
                }
            }

            // Parent-data alignment is intentionally outside editorContentLayer. Recording the
            // Canvas together with the form loses its BoxScope placement on some RenderNode replay
            // paths and places the indicator at layer origin (top-left). This tiny live Canvas uses
            // the same target-size parent and transform, so BottomCenter remains authoritative while
            // the expensive Pager/forms stay cached.
            pagerPresentation?.takeIf { it.visible && it.pageCount > 1 }?.let { presentation ->
                ProjectPagerIndicator(
                    pagerState = presentation.pagerState,
                    pageCount = presentation.pageCount,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .zIndex(20f)
                        .padding(bottom = 10.dp)
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
    val shape = RoundedRectangle(corner)
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
    adaptiveMetrics: HomeAdaptiveMetrics,
    modifier: Modifier = Modifier
) {
    Box(modifier) {
        if (sourceIsWide) {
            CourseEditorDaySourceContent(
                course = course,
                backdrop = backdrop,
                config = config,
                tabletFontScale = if (adaptiveMetrics.isTabletLandscape) 1.10f else 1f
            )
        } else {
            CourseEditorWeekSourceContent(course, backdrop, config)
        }
    }
}

@Composable
private fun CourseEditorDaySourceContent(
    course: CourseEntity,
    backdrop: Backdrop?,
    config: ScheduleConfigEntity,
    tabletFontScale: Float
) {
    val cardColor = courseCardBaseColor(config, course).copy(alpha = config.cardAlpha.coerceIn(0f, 1f))
    val textColor =
        if (backdrop != null && config.courseCardGlassEnabled) LocalAdaptiveGlass.current.contentColor
        else if (config.courseCardGlassEnabled) readableOn(cardColor)
        else glassForegroundColor(config)
    DayCourseCardTextContent(
        course = course,
        periods = emptyList(),
        showTime = false,
        showWeeks = false,
        textColor = textColor,
        tabletFontScale = tabletFontScale
    )
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
        else if (config.courseCardGlassEnabled) readableOn(cardColor)
        else glassForegroundColor(config)
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
        val tabletFontBoost = if (maxWidth >= 120.dp) 1.18f else 1f
        val previewFontScale = LocalPersonalizationPreview.current?.cardFontScale
        val courseFontScale = ((previewFontScale ?: config.courseCardFontScale) * tabletFontBoost)
            .coerceIn(0.80f, 1.35f)
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

internal fun legacyCourseEditorMorphSpec(
    source: Rect,
    target: Rect,
    sourceCornerRadiusPx: Float,
    targetCornerRadiusPx: Float,
    maximumArcPx: Float,
    sourceBlurMaxPx: Float,
    destinationBlurMaxPx: Float,
    hasSourceTransform: Boolean
): LiquidMorphSpec = object : LiquidMorphSpec {
    override val routeKey: String = "course-editor"

    override fun frame(input: LiquidMorphInput): LiquidMorphFrame {
        val closing = input.direction == LiquidMorphDirection.Closing
        val phase = if (closing) CourseEditorOverlayPhase.Closing else CourseEditorOverlayPhase.Opening
        val visualProgress = courseEditorVisualProgress(input.rawProgress, phase)
        val pulseScale = courseEditorSettleScale(input.rawProgress, phase)
        val rect = curvedCourseEditorRect(
            source = source,
            target = target,
            positionProgress = visualProgress.position,
            sizeProgress = visualProgress.size,
            pulseScale = pulseScale,
            maxArcPx = maximumArcPx
        )
        val cornerProgress = smoothStep(0.04f, 0.90f, visualProgress.size)
        val corner = interpolateFloat(
            sourceCornerRadiusPx,
            targetCornerRadiusPx,
            cornerProgress
        )
        val openingHandoff = smoothStep(0.12f, 0.50f, visualProgress.position)
        val closingHandoff = smoothStep(0.04f, 0.18f, visualProgress.position)
        val destinationAlpha = if (closing) closingHandoff else openingHandoff
        val sourceAlpha = if (hasSourceTransform) 1f - destinationAlpha else 0f
        val sourceBlurProgress = if (closing) {
            smoothStep(0f, 0.12f, visualProgress.position)
        } else {
            smoothStep(0f, 0.24f, visualProgress.position)
        }
        val destinationBlur = if (closing) {
            destinationBlurMaxPx
        } else {
            destinationBlurMaxPx * (1f - smoothStep(0.90f, 1f, visualProgress.position))
        }
        val trajectory = LiquidProgressKinematics(visualProgress.position, 0f, 0f)
        val shape = LiquidProgressKinematics(visualProgress.size, 0f, 0f)
        val moving = closing || input.rawProgress < 0.999f
        return LiquidMorphFrame(
            rect = rect,
            cornerRadiusPx = corner,
            trajectoryProgress = visualProgress.position,
            shapeProgress = visualProgress.size,
            motion = LiquidMotionSample(trajectory = trajectory, shape = shape),
            content = LiquidContentHandoffFrame(
                sourceAlpha = sourceAlpha,
                destinationSurfaceAlpha = 1f,
                destinationContentAlpha = destinationAlpha,
                sourceBlurPx = sourceBlurMaxPx * sourceBlurProgress,
                destinationBlurPx = destinationBlur,
                destinationMounted = true,
                destinationInteractive = !moving
            ),
            backdropDepth = LiquidBackdropDepthFrame(
                scale = input.backdropScale,
                blurPx = input.backdropBlurPx,
                useCachedScene = input.useCachedBackdrop
            ),
            layerLifecycle = LiquidLayerLifecycleFrame(
                keepMorphClip = moving,
                keepOffscreenLayer = moving,
                prewarmRequired = !closing && input.rawProgress <= 0f
            )
        )
    }
}

