package com.xiaomanjun.sleepdownschedule.feature.course.editor
import android.graphics.Matrix
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.kyant.backdrop.Backdrop
import com.xiaomanjun.sleepdownschedule.glass.glassBackdropProducer
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.shapes.RoundedRectangle
import com.xiaomanjun.sleepdownschedule.*
import com.xiaomanjun.sleepdownschedule.feature.home.*
import com.xiaomanjun.sleepdownschedule.feature.home.day.*
import com.xiaomanjun.sleepdownschedule.glass.DeferredGlassCornerSize
import com.xiaomanjun.sleepdownschedule.glass.GlassBackdropDomain
import com.xiaomanjun.sleepdownschedule.glass.GlassMorphAllocation
import com.xiaomanjun.sleepdownschedule.glass.GlassMotionExperiments
import com.xiaomanjun.sleepdownschedule.glass.GlassTransitionGeometry
import com.xiaomanjun.sleepdownschedule.glass.LiquidMorphController
import com.xiaomanjun.sleepdownschedule.glass.LiquidMorphControllerBridge
import com.xiaomanjun.sleepdownschedule.glass.LiquidMorphDirection
import com.xiaomanjun.sleepdownschedule.glass.LiquidMorphInput
import com.xiaomanjun.sleepdownschedule.glass.LiquidMorphFrame
import com.xiaomanjun.sleepdownschedule.glass.LiquidMorphPhase
import com.xiaomanjun.sleepdownschedule.glass.glassMorphContent
import com.xiaomanjun.sleepdownschedule.glass.glassMorphHost
import com.xiaomanjun.sleepdownschedule.glass.isAllocationEfficientFor
import com.xiaomanjun.sleepdownschedule.glass.rememberGlassLayerBackdrop
import com.xiaomanjun.sleepdownschedule.glass.sampleGlassTransitionEnvelope
import com.xiaomanjun.sleepdownschedule.glass.ui.*
import com.xiaomanjun.sleepdownschedule.glass.ui.glassForegroundColor
import kotlin.math.roundToInt
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first

import com.xiaomanjun.sleepdownschedule.core.ui.material.LocalPersonalizationPreview
import com.xiaomanjun.sleepdownschedule.core.performance.LocalGlassQuality
import com.xiaomanjun.sleepdownschedule.core.wallpaper.hasAnyWallpaper
import com.xiaomanjun.sleepdownschedule.core.ui.interaction.morphDismissSwipe
import com.xiaomanjun.sleepdownschedule.core.ui.interaction.MorphCurveContinuity
import com.xiaomanjun.sleepdownschedule.glass.curveValues
import com.xiaomanjun.sleepdownschedule.glass.withCurveContinuity
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
    internal val curveContinuity = MorphCurveContinuity()
    var hasInterrupted by mutableStateOf(false)
        private set
    internal val retarget = com.xiaomanjun.sleepdownschedule.core.ui.interaction.MorphRetargetState()
    val backgroundZoom = Animatable(1f)
    internal val backgroundRetarget = com.xiaomanjun.sleepdownschedule.core.ui.interaction.MorphRetargetState(
        1f..CourseEditorMotion.BackgroundZoomOpenScale)
    private val controllerBridge = LiquidMorphControllerBridge { "course-editor" }
    val liquidMorphController: LiquidMorphController get() = controllerBridge.controller
    private var currentPhase by mutableStateOf(CourseEditorOverlayPhase.Idle)
    var phase: CourseEditorOverlayPhase
        get() = currentPhase
        internal set(value) {
            if (currentPhase == value) return
            if ((currentPhase == CourseEditorOverlayPhase.Opening && value == CourseEditorOverlayPhase.Closing ||
                    currentPhase == CourseEditorOverlayPhase.Closing && value == CourseEditorOverlayPhase.Opening) &&
                progress.value > 0.00001f && progress.value < 0.99999f) hasInterrupted = true
            when (value) {
                CourseEditorOverlayPhase.Preparing, CourseEditorOverlayPhase.Idle -> {
                    curveContinuity.reset()
                    hasInterrupted = false
                }
                CourseEditorOverlayPhase.Opening, CourseEditorOverlayPhase.Closing ->
                    curveContinuity.redirect(progress.value, value == CourseEditorOverlayPhase.Closing,
                        if (value == CourseEditorOverlayPhase.Closing) CourseEditorMotion.CloseDurationMillis else CourseEditorMotion.OpenDurationMillis)
                else -> Unit
            }
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
    var closingCourseOverride by mutableStateOf<CourseEntity?>(null)
        internal set

    fun retractTo(boundsInRoot: Rect?, course: CourseEntity? = null) {
        closingSourceBoundsOverride = boundsInRoot
        closingCourseOverride = course
    }
}

@Composable
fun rememberCourseEditorMotionState(): CourseEditorMotionState = remember { CourseEditorMotionState() }

data class CourseEditorOverlayRequest(
    val course: CourseEntity,
    val targetWeek: Int?,
    val sourceBoundsInRoot: Rect?,
    val sourceIsDayCard: Boolean = false,
    val copyDraft: CourseEntity? = null,
    internal val sourceGrid: CourseEditorWeekGrid? = null,
    val contextMessage: String? = null,
    val sourceAdjustmentLabel: String? = null,
    val sourceClipBoundsInRoot: Rect? = null,
    val sourceDayAppearance: CourseEditorDayAppearance? = null
)



/**
 * Keep the same continuous rounded outline at every progress, including the settled endpoints.
 * CornerBasedShape also supplies density-aware radii to the downsampled liquid lens shader.
 */
internal class CourseEditorMorphCornerShape(
    private val radiusX: Float,
    private val radiusY: Float,
    private val taper: Float = 0f,
    private val sourceDensity: Float = 1f,
    private val radiusProvider: (() -> Float)? = null,
    private val taperProvider: (() -> Float)? = null,
    topStart: CornerSize = radiusProvider?.let { DeferredGlassCornerSize(sourceDensity, it) }
        ?: CornerSize((minOf(radiusX, radiusY) / sourceDensity).dp),
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
    ): Outline {
        val base = RoundedRectangle(topStart.dp)
            .createOutline(size, layoutDirection, Density(1f))
        val currentTaper = taperProvider?.invoke() ?: taper
        if (currentTaper == 0f) return base
        val path = Path().apply { addOutline(base) }.asAndroidPath()
        if (size.width > 0f && size.height > 0f) {
            path.transform(Matrix().apply {
                setValues(courseEditorTaperTransform(size.width, size.height, currentTaper))
            })
        }
        return Outline.Generic(path.asComposePath())
    }

    override fun copy(
        topStart: CornerSize,
        topEnd: CornerSize,
        bottomEnd: CornerSize,
        bottomStart: CornerSize
    ): CornerBasedShape = CourseEditorMorphCornerShape(
        radiusX = radiusX,
        radiusY = radiusY,
        taper = taper,
        sourceDensity = sourceDensity,
        radiusProvider = radiusProvider,
        taperProvider = taperProvider,
        topStart = topStart,
        topEnd = topEnd,
        bottomEnd = bottomEnd,
        bottomStart = bottomStart
    )
}

internal fun Modifier.courseEditorContentTaper(taperProvider: () -> Float): Modifier = drawWithCache {
    val taper = taperProvider()
    val transform = if (taper == 0f) null else Matrix().apply {
        setValues(courseEditorTaperTransform(size.width, size.height, taper))
    }
    onDrawWithContent {
        if (transform == null) {
            drawContent()
        } else {
            // Apply the shell's projection to existing draw layers, without remeasuring fields
            // or allocating another full-form snapshot/offscreen texture.
            val canvas = drawContext.canvas.nativeCanvas
            val saveCount = canvas.save()
            try {
                canvas.concat(transform)
                drawContent()
            } finally {
                canvas.restoreToCount(saveCount)
            }
        }
    }
}

@Composable
internal fun CourseEditorContainerOverlayHost(
    request: CourseEditorOverlayRequest?,
    state: AppState,
    backdrop: Backdrop?,
    config: ScheduleConfigEntity,
    adaptiveMetrics: HomeAdaptiveMetrics,
    sourceCardBackdrop: Backdrop? = backdrop,
    modifier: Modifier = Modifier,
    landscapeContentInsets: PaddingValues = PaddingValues(0.dp),
    awaitOpeningGate: suspend () -> Unit = {},
    onDismissRequest: () -> Unit,
    onSave: (originals: List<CourseEntity>, edited: List<CourseEntity>, targetWeek: Int?) -> Unit,
    onCopy: (List<CourseEntity>, (Boolean) -> Unit) -> Unit,
    onDelete: (courses: List<CourseEntity>, targetWeek: Int?) -> Unit,
    motionState: CourseEditorMotionState,
    onMovingCourseTap: (androidx.compose.ui.geometry.Offset) -> Boolean = { false },
    onRenderedCourseIdChange: (Long?) -> Unit = {},
    onPhaseChange: (CourseEditorOverlayPhase) -> Unit = {}
) {
    val preloadContent = true
    val earlyContentHandoff = !com.xiaomanjun.sleepdownschedule.core.ui.designsystem.usesNativeMaterialMotion()
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    if (com.xiaomanjun.sleepdownschedule.core.ui.designsystem.isLandscapeMenuWindow()) {
        var dismissHandler by remember { mutableStateOf<(() -> Unit)?>(null) }
        var copySaving by remember { mutableStateOf(false) }
        LaunchedEffect(request) {
            motionState.retractTo(null)
            motionState.backgroundZoom.snapTo(1f)
            if (request != null) copySaving = false
            val phase = if (request != null) CourseEditorOverlayPhase.Opening else
                if (motionState.phase != CourseEditorOverlayPhase.Idle) CourseEditorOverlayPhase.Closing else CourseEditorOverlayPhase.Idle
            motionState.phase = phase
            onPhaseChange(phase)
            motionState.progress.animateTo(
                if (request != null) 1f else 0f,
                CourseEditorMotion.centeredSpec(opening = request != null)
            )
        }
        DisposableEffect(Unit) {
            onDispose {
                motionState.phase = CourseEditorOverlayPhase.Idle
                onPhaseChange(CourseEditorOverlayPhase.Idle)
                onRenderedCourseIdChange(null)
            }
        }
        com.xiaomanjun.sleepdownschedule.core.ui.designsystem.LandscapeMenuOverlay(
            request = request, backdrop = backdrop,
            contentInsets = landscapeContentInsets,
            onDismissRequest = { if (!copySaving) dismissHandler?.invoke() ?: onDismissRequest() },
            onOpenFinished = {
                motionState.phase = CourseEditorOverlayPhase.Open
                onPhaseChange(CourseEditorOverlayPhase.Open)
            },
            onDismissFinished = {
                motionState.phase = CourseEditorOverlayPhase.Idle
                onPhaseChange(CourseEditorOverlayPhase.Idle)
                onRenderedCourseIdChange(null)
            }
        ) { shown, dialogBackdrop ->
            val editorSurface = rememberGlassLayerBackdrop(
                domain = GlassBackdropDomain.Content, providerId = "landscape-course-editor-shell"
            )
            val save: (List<CourseEntity>, List<CourseEntity>) -> Unit = { originals, edited ->
                if (shown.copyDraft == null) onSave(originals, edited, shown.targetWeek)
                else if (!copySaving) {
                    copySaving = true
                    onCopy(edited.map { it.copy(id = 0, scheduleId = shown.copyDraft.scheduleId) }) { success ->
                        if (!success) copySaving = false
                    }
                }
            }
            CourseEditorAnimatedContainer(
                backdrop = dialogBackdrop, config = config, course = shown.copyDraft ?: shown.course,
                muted = shown.sourceAdjustmentLabel == "停",
                expandedOutlineLight = shown.sourceIsDayCard,
                shape = RoundedRectangle(32.dp), progressProvider = { 1f }, alpha = 1f,
                surfaceBackdrop = editorSurface, modifier = Modifier.fillMaxSize()
            ) {
            NormalizedCourseEditorScreen(
                formData = CourseEditorFormData(state.config, state.periods, state.courses, state.timetableCatalog),
                initialCourse = shown.course.takeIf { shown.copyDraft == null },
                copyDraft = shown.copyDraft, contextMessage = shown.contextMessage,
                onCancel = { if (!copySaving) onDismissRequest() },
                onDismissHandlerChange = { dismissHandler = it },
                onSave = {}, onSaveCourses = { save(emptyList(), it) }, onSaveGroup = save,
                onDelete = {}, onDeleteGroup = { onDelete(it, shown.targetWeek) },
                backdrop = editorSurface, pickerRenderInRootScaffold = false
            )
            }
        }
        return
    }
    var renderedRequest by remember { mutableStateOf<CourseEditorOverlayRequest?>(null) }
    var copySaving by remember(request) { mutableStateOf(false) }
    var rootSize by remember { mutableStateOf(IntSize.Zero) }
    var editorContentMounted by remember { mutableStateOf(false) }
    var editorContentLaidOut by remember { mutableStateOf(false) }
    val progress = motionState.progress
    val latestOnRenderedCourseIdChange by rememberUpdatedState(onRenderedCourseIdChange)
    val latestOnPhaseChange by rememberUpdatedState(onPhaseChange)
    val latestAwaitOpeningGate by rememberUpdatedState(awaitOpeningGate)

    fun updatePhase(phase: CourseEditorOverlayPhase) {
        motionState.phase = phase
        latestOnPhaseChange(phase)
    }

    LaunchedEffect(request) {
        if (request != null) {
            // Reuse the same form, geometry and clock when its closing session is retargeted.
            val resuming = renderedRequest?.course?.id == request.course.id &&
                (motionState.phase == CourseEditorOverlayPhase.Closing ||
                    motionState.phase == CourseEditorOverlayPhase.Disposing)
            if (resuming) {
                renderedRequest = request
                latestOnRenderedCourseIdChange(request.course.id)
                editorContentMounted = preloadContent || editorContentMounted
            } else {
                motionState.retarget.reset()
                motionState.backgroundRetarget.reset()
                editorContentMounted = preloadContent
                editorContentLaidOut = false
                motionState.retractTo(null)
                updatePhase(CourseEditorOverlayPhase.Preparing)
                renderedRequest = request
                latestOnRenderedCourseIdChange(request.course.id)
                progress.snapTo(0f)
                motionState.backgroundZoom.snapTo(1f)
                var waitedFrames = 0
                while (waitedFrames < 12 && (rootSize.width <= 0 || rootSize.height <= 0)) {
                    withFrameNanos { }
                    waitedFrames++
                }
                latestAwaitOpeningGate()
                if (preloadContent) {
                    snapshotFlow { editorContentLaidOut }.first { it }
                    // The first layout is sufficient; the same form stays mounted through motion.
                }
            }
            updatePhase(CourseEditorOverlayPhase.Opening)
            coroutineScope {
                launch {
                    motionState.retarget.animate(progress, 1f, CourseEditorMotion.OpenDurationMillis,
                        continuation = motionState.curveContinuity)
                }
                // The background depth (blur + zoom) trails the card on a longer, gentler
                // ease-out so it keeps settling after the card has opened — the inertial pull
                // on the home surface behind the editor.
                launch {
                    motionState.backgroundRetarget.animate(motionState.backgroundZoom,
                        CourseEditorMotion.BackgroundZoomOpenScale, CourseEditorMotion.BackgroundZoomOpenDurationMillis,
                        CourseEditorMotion.BackgroundZoomInertialEasing,
                        if (resuming) 0 else CourseEditorMotion.BackgroundZoomDelayMillis)
                }
            }
            updatePhase(CourseEditorOverlayPhase.Open)
            if (!preloadContent) {
                withFrameNanos { }
                editorContentMounted = true
            }
        } else if (renderedRequest != null) {
            // Native content stays with its shell through the return; inputs are blocked below.
            if (preloadContent) {
                focusManager.clearFocus(force = true)
                keyboard?.hide()
            }
            if (!preloadContent) editorContentMounted = false
            updatePhase(CourseEditorOverlayPhase.Closing)
            coroutineScope {
                launch {
                    motionState.retarget.animate(progress, 0f, CourseEditorMotion.CloseDurationMillis,
                        continuation = motionState.curveContinuity)
                }
                // Finish the background together with the card so material restoration does
                // not wait for an additional invisible Closing tail.
                launch {
                    motionState.backgroundRetarget.animate(motionState.backgroundZoom, 1f,
                        CourseEditorMotion.BackgroundZoomCloseDurationMillis, CourseEditorMotion.BackgroundZoomInertialEasing)
                }
            }
            editorContentMounted = false
            updatePhase(CourseEditorOverlayPhase.Disposing)
            renderedRequest = null
            latestOnRenderedCourseIdChange(null)
            motionState.retractTo(null)
            updatePhase(CourseEditorOverlayPhase.Idle)
        } else {
            if (motionState.phase != CourseEditorOverlayPhase.Idle || editorContentMounted || renderedRequest != null) {
                updatePhase(CourseEditorOverlayPhase.Disposing)
            }
            editorContentMounted = false
            latestOnRenderedCourseIdChange(null)
            motionState.retractTo(null)
            if (motionState.phase != CourseEditorOverlayPhase.Idle) {
                updatePhase(CourseEditorOverlayPhase.Idle)
            }
        }
    }

    val overlayPhase = motionState.phase
    val isOverlayActive = overlayPhase != CourseEditorOverlayPhase.Idle
    val shownRequest = request ?: renderedRequest ?: return
    val formData = remember(state.config, state.periods, state.courses, state.timetableCatalog) {
        CourseEditorFormData(
            config = state.config,
            periods = state.periods,
            courses = state.courses,
            timetableCatalog = state.timetableCatalog
        )
    }
    var contentDismissHandler by remember(shownRequest) { mutableStateOf<(() -> Unit)?>(null) }
    val onDismissHandlerChange = remember(shownRequest) {
        { handler: (() -> Unit)? -> contentDismissHandler = handler }
    }
    val dismissEditor = {
        if (!copySaving) contentDismissHandler?.invoke() ?: onDismissRequest()
    }
    val saveEditedCourse = remember(shownRequest, onSave, onCopy) {
        { originals: List<CourseEntity>, edited: List<CourseEntity> ->
            if (shownRequest.copyDraft != null) {
                if (!copySaving) {
                    copySaving = true
                    onCopy(edited.map { it.copy(id = 0, scheduleId = shownRequest.copyDraft.scheduleId) }) {
                        success -> if (!success) copySaving = false
                    }
                }
            } else {
                onSave(originals, edited, shownRequest.targetWeek)
            }
        }
    }
    val deleteEditedCourse = remember(shownRequest.targetWeek, onDelete) {
        { courses: List<CourseEntity> -> onDelete(courses, shownRequest.targetWeek) }
    }
    BackHandler(enabled = isOverlayActive && request != null) {
        dismissEditor()
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
    val dayAppearance = shownRequest.sourceDayAppearance
    val previewCornerProgress = LocalPersonalizationPreview.current?.weekCardCornerProgress
        ?: config.weekCardCornerProgress
    val sourceCornerPx = remember(
        sourceRect,
        density,
        adaptiveMetrics,
        sourceIsWeekCard,
        dayAppearance?.cornerRadius,
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
                (dayAppearance?.cornerRadius ?: DayCourseCardCornerRadius).toPx()
            }
        }
    }
    // A copy can land elsewhere, but its upper/lower taper follows the opening source.
    val openingSource = validSourceRect(shownRequest.sourceBoundsInRoot, rootSize)
    val morphSpec = remember(
        sourceRect,
        targetRect,
        sourceCornerPx,
        adaptiveMetrics,
        density.density,
        hasSourceTransform,
        preloadContent,
        earlyContentHandoff
    ) {
        legacyCourseEditorMorphSpec(
            source = sourceRect,
            target = targetRect,
            sourceCornerRadiusPx = sourceCornerPx,
            targetCornerRadiusPx = with(density) { 32.dp.toPx() },
            maximumArcPx = with(density) { adaptiveMetrics.animationArc.toPx() },
            sourceBlurMaxPx = with(density) { 6.dp.toPx() },
            destinationBlurMaxPx = with(density) { 5.dp.toPx() },
            hasSourceTransform = hasSourceTransform,
            preloadedContent = preloadContent,
            earlyContentHandoff = earlyContentHandoff,
            earlyOpeningContentHandoff = true
        )
    }
    DisposableEffect(morphSpec, openingSource, targetRect) {
        val sampler: (Float, Boolean) -> FloatArray = { p, closing ->
            val frame = morphSpec.frame(LiquidMorphInput(sourceRect, targetRect, p,
                if (closing) LiquidMorphDirection.Closing else LiquidMorphDirection.Opening))
            val taper = openingSource?.let {
                courseEditorOpeningTaper(p, it.center.y - targetRect.center.y, targetRect.height, closing)
            } ?: 0f
            frame.curveValues(taper)
        }
        motionState.curveContinuity.bind(sampler) { current, closing ->
            val captured = Rect(current[0], current[1], current[2], current[3])
            val from = if (closing) sourceRect else captured
            val to = if (closing) captured else targetRect
            val rebased = legacyCourseEditorMorphSpec(
                from, to, if (closing) sourceCornerPx else current[4],
                if (closing) current[4] else with(density) { 32.dp.toPx() },
                with(density) { adaptiveMetrics.animationArc.toPx() },
                with(density) { 6.dp.toPx() }, with(density) { 5.dp.toPx() },
                hasSourceTransform, preloadContent, earlyContentHandoff, earlyOpeningContentHandoff = true)
            val frames: (Float) -> FloatArray = { p ->
                rebased.frame(LiquidMorphInput(from, to, p,
                    if (closing) LiquidMorphDirection.Closing else LiquidMorphDirection.Opening))
                    .curveValues(courseEditorOpeningTaper(p, from.center.y - to.center.y, to.height, closing))
            }
            frames
        }
        onDispose { motionState.curveContinuity.unbind(sampler) }
    }
    val materialEnvelope = remember(morphSpec, sourceRect, targetRect) {
        // A tapered shell cannot be represented by the fixed envelope's rounded-rectangle SDF.
        if (!GlassMotionExperiments.fixedMorph || hasSourceTransform) null else {
            sampleGlassTransitionEnvelope(
                tracks = LiquidMorphDirection.entries.map { direction ->
                    { p: Float ->
                        val frame = morphSpec.frame(LiquidMorphInput(sourceRect, targetRect, p, direction))
                        GlassTransitionGeometry(frame.rect, frame.cornerRadiusPx)
                    }
                },
                steps = 256,
                effectPaddingPx = 2f
            ).takeIf { it.isAllocationEfficientFor(GlassTransitionGeometry(targetRect, 0f), 1.65f) }
        }
    }
    val closingMorph = overlayPhase == CourseEditorOverlayPhase.Closing ||
        overlayPhase == CourseEditorOverlayPhase.Disposing
    val sourceCourse = dayAppearance?.course ?: shownRequest.course
    val shellCourse = if (closingMorph) motionState.closingCourseOverride ?: sourceCourse else sourceCourse
    val sourceMuted = dayAppearance?.muted ?: (shownRequest.sourceAdjustmentLabel == "停")
    val sourceBadge = dayAppearance?.adjustmentLabel ?: shownRequest.sourceAdjustmentLabel
    val frameState = remember(morphSpec, sourceRect, targetRect, closingMorph, overlayPhase, density, motionState) {
        derivedStateOf {
            morphSpec.frame(
                LiquidMorphInput(
                    source = sourceRect,
                    target = targetRect,
                    rawProgress = progress.value.coerceIn(0f, 1f),
                    direction = if (closingMorph) LiquidMorphDirection.Closing else LiquidMorphDirection.Opening,
                    backdropScale = motionState.backgroundZoom.value,
                    backdropBlurPx = with(density) {
                        22.dp.toPx() * homeOverlayDepthProgress(motionState.backgroundZoom.value, CourseEditorMotion.BackgroundZoomOpenScale)
                    },
                    useCachedBackdrop = overlayPhase == CourseEditorOverlayPhase.Preparing ||
                        overlayPhase == CourseEditorOverlayPhase.Opening ||
                        overlayPhase == CourseEditorOverlayPhase.Closing ||
                        overlayPhase == CourseEditorOverlayPhase.Disposing
                )
            ).withCurveContinuity(motionState.curveContinuity, progress.value)
        }
    }
    val morphFrame by frameState
    val materialGeometry = remember(frameState, density) {
        derivedStateOf {
            GlassTransitionGeometry(
                morphFrame.rect,
                with(density) { morphFrame.cornerRadiusPx.coerceIn(6.dp.toPx(), 36.dp.toPx()) }
            )
        }
    }
    val fixedMaterialEligible = com.xiaomanjun.sleepdownschedule.core.ui.designsystem.usesNativeMaterialMotion() &&
        com.xiaomanjun.sleepdownschedule.core.performance.effectiveAppMaterialPolicy().denseMaterials &&
        config.courseCardGlassEnabled && config.hasAnyWallpaper() &&
        backdrop != null && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S
    val materialAllocation = remember(materialEnvelope, materialGeometry, overlayPhase, config.courseCardBlur, density.density, fixedMaterialEligible) {
        if (materialEnvelope == null || overlayPhase == CourseEditorOverlayPhase.Open ||
            !fixedMaterialEligible) null else {
            GlassMorphAllocation(materialEnvelope, { materialGeometry.value },
                with(density) { maxOf(config.courseCardBlur, 10f).dp.toPx() })
        }
    }
    val animatedModifier = Modifier
        .offset {
            val animatedRect = morphFrame.rect
            IntOffset(
                animatedRect.left.roundToInt(),
                animatedRect.top.roundToInt()
            )
        }
        .layout { measurable, constraints ->
            val rect = morphFrame.rect
            val width = constraints.constrainWidth(rect.width.roundToInt().coerceAtLeast(1))
            val height = constraints.constrainHeight(rect.height.roundToInt().coerceAtLeast(1))
            val placeable = measurable.measure(Constraints.fixed(width, height))
            layout(width, height) { placeable.place(0, 0) }
        }
    val corner = remember(frameState, density, sourceIsWeekCard, sourceCornerPx, sourceRect) {
        derivedStateOf {
            with(density) {
                val radius = if (sourceIsWeekCard) morphFrame.cornerRadiusPx else {
                    courseEditorDayCornerRadius(
                        sourceCornerPx, courseEditorDaySourceScale(sourceRect, morphFrame.rect),
                        32.dp.toPx(), morphFrame.content.destinationContentAlpha
                    )
                }
                radius.coerceIn(6.dp.toPx(), 36.dp.toPx()).toDp()
            }
        }
    }
    val taper = remember(openingSource, targetRect, overlayPhase, closingMorph, progress) {
        derivedStateOf {
            if (openingSource != null && (overlayPhase == CourseEditorOverlayPhase.Opening || closingMorph)) {
                motionState.curveContinuity.value(13, progress.value, courseEditorOpeningTaper(
                    progress.value.coerceIn(0f, 1f),
                    openingSource.center.y - targetRect.center.y,
                    targetRect.height,
                    closing = closingMorph
                ))
            } else 0f
        }
    }
    val shellShape = remember(corner, density.density, taper) {
        with(density) {
            CourseEditorMorphCornerShape(
                32.dp.toPx(), 32.dp.toPx(), sourceDensity = density.density,
                radiusProvider = { corner.value.toPx() }, taperProvider = { taper.value }
            )
        }
    }
    // Day cards sample only wallpaper. Start from those same pixels before introducing the
    // complete page underlay, rather than replacing a stopped card's material at the first frame.
    val blendedMorphBackdrop = com.xiaomanjun.sleepdownschedule.glass.ui.rememberCrossfadeBackdrop(
        source = if (shownRequest.sourceIsDayCard && hasSourceTransform) sourceCardBackdrop else backdrop,
        destination = backdrop,
        progress = { courseEditorSmoothStep(0.12f, 0.62f, morphFrame.shapeProgress) }
    )
    // A wrapper around the wallpaper breaks CourseGlassCard's shared-blur identity check.
    // Start with the clicked day card's actual input and sampling path, then hand off to
    // the screen-space scene. This also keeps stopped cards' complete material at frame 0.
    val morphBackdrop = if (shownRequest.sourceIsDayCard && morphFrame.shapeProgress <= 0.12f &&
        dayAppearance != null) dayAppearance.backdrop else blendedMorphBackdrop
    val morphSurfaceAlpha = 1f
    val sourceCoverAlpha = remember(frameState, hasSourceTransform, overlayPhase) {
        derivedStateOf {
            if (hasSourceTransform) {
                when (overlayPhase) {
                    CourseEditorOverlayPhase.Preparing,
                    CourseEditorOverlayPhase.Opening,
                    CourseEditorOverlayPhase.Closing,
                    CourseEditorOverlayPhase.Disposing -> morphFrame.content.sourceAlpha
                    else -> 0f
                }
            } else 0f
        }
    }
    val showSourceCover by remember(sourceCoverAlpha) {
        derivedStateOf { sourceCoverAlpha.value > 0.001f }
    }
    val editorFormBackdrop = rememberGlassLayerBackdrop(
        domain = GlassBackdropDomain.Content,
        providerId = "course-editor-shell"
    )
    val textColor = if (backdrop != null) {
        LocalAdaptiveGlass.current.contentColor
    } else {
        glassForegroundColor(config)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { rootSize = it }
            .morphDismissSwipe(isOverlayActive && overlayPhase != CourseEditorOverlayPhase.Open,
                onTapTarget = onMovingCourseTap) { dismissEditor() }
            .drawWithContent {
                val sourceClip = shownRequest.sourceClipBoundsInRoot
                    ?.takeIf { sourceRect == shownRequest.sourceBoundsInRoot }
                if (sourceClip == null || sourceClip == sourceRect || morphFrame.shapeProgress >= 0.999f) {
                    drawContent()
                } else {
                    // Keep the LazyColumn's cropped edges at the source endpoint, then release
                    // them with the morph. Geometry and text still use the full original card.
                    val rect = morphFrame.rect
                    val remaining = 1f - morphFrame.shapeProgress.coerceIn(0f, 1f)
                    clipRect(
                        left = if (sourceClip.left > sourceRect.left)
                            rect.left + (sourceClip.left - sourceRect.left) * remaining else 0f,
                        top = if (sourceClip.top > sourceRect.top)
                            rect.top + (sourceClip.top - sourceRect.top) * remaining else 0f,
                        right = if (sourceClip.right < sourceRect.right)
                            rect.right - (sourceRect.right - sourceClip.right) * remaining else size.width,
                        bottom = if (sourceClip.bottom < sourceRect.bottom)
                            rect.bottom - (sourceRect.bottom - sourceClip.bottom) * remaining else size.height
                    ) { this@drawWithContent.drawContent() }
                }
            }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(if (request != null) Modifier.clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { dismissEditor() } else Modifier)
        )
        CourseEditorAnimatedContainer(
            backdrop = morphBackdrop,
            config = config,
            course = shellCourse,
            muted = sourceMuted,
            expandedOutlineLight = shownRequest.sourceIsDayCard,
            shape = shellShape,
            progressProvider = { morphFrame.shapeProgress },
            alpha = morphSurfaceAlpha,
            surfaceBackdrop = editorFormBackdrop,
            modifier = if (materialAllocation == null) animatedModifier else Modifier.glassMorphHost(materialAllocation),
            morphAllocation = materialAllocation
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (materialAllocation != null) Modifier.glassMorphContent(materialAllocation) else Modifier)
                    .clip(shellShape)
                    .courseEditorContentTaper { taper.value }
            ) {
                if (editorContentMounted && (preloadContent ||
                        request != null && overlayPhase == CourseEditorOverlayPhase.Open)) {
                    key(shownRequest.course.id, shownRequest.copyDraft) {
                        CourseEditorFormLayer(
                            targetRect = targetRect,
                            cornerProvider = remember(corner) { { corner.value } },
                            textColor = textColor,
                            formData = formData,
                            course = shownRequest.course,
                            copyDraft = shownRequest.copyDraft,
                            contextMessage = shownRequest.contextMessage,
                            backdrop = editorFormBackdrop,
                            onDismissRequest = { if (!copySaving) onDismissRequest() },
                            onDismissHandlerChange = onDismissHandlerChange,
                            onSave = saveEditedCourse,
                            onDelete = deleteEditedCourse,
                            liveFrame = remember(frameState) { { frameState.value } },
                            interactive = overlayPhase == CourseEditorOverlayPhase.Open,
                            onContentLaidOut = { editorContentLaidOut = true }
                        )
                    }
                }
                if (showSourceCover) {
                    CourseEditorSourceContent(
                        course = shellCourse,
                        config = config,
                        sourceIsWide = !sourceIsWeekCard,
                        adaptiveMetrics = adaptiveMetrics,
                        adjustmentLabel = sourceBadge,
                        dayAppearance = dayAppearance,
                        muted = sourceMuted,
                        sourceCorner = with(density) { sourceCornerPx.toDp() },
                        modifier = Modifier
                            // Measure the source once at its original card size. In particular,
                            // stopped day cards must not rewrap their text as the shell widens.
                            .wrapContentSize(Alignment.TopStart, unbounded = true)
                            .requiredSize(
                                with(density) { sourceRect.width.toDp() },
                                with(density) { sourceRect.height.toDp() }
                            )
                            .graphicsLayer {
                                transformOrigin = TransformOrigin(0f, 0f)
                                scaleX = morphFrame.rect.width / sourceRect.width
                                // A day card is much shorter than the destination. Scaling Y
                                // independently changes glyph proportions and line spacing.
                                scaleY = if (sourceIsWeekCard) morphFrame.rect.height / sourceRect.height
                                    else courseEditorDaySourceScale(sourceRect, morphFrame.rect)
                                alpha = sourceCoverAlpha.value
                                val blurPx = morphFrame.content.sourceBlurPx
                                compositingStrategy = CompositingStrategy.Offscreen
                                renderEffect = platformBlurRenderEffect(blurPx)
                            }
                    )
                }
                if (preloadContent && overlayPhase != CourseEditorOverlayPhase.Open) {
                    Box(Modifier.fillMaxSize().pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) awaitPointerEvent().changes.forEach { it.consume() }
                        }
                    })
                }
            }
        }
        if (showSourceCover && sourceBadge != null) {
            // The original badge straddles the card's rounded edge. Keep its clone outside
            // the shell clip too, otherwise a stopped/makeup card starts with a cut-off badge.
            Box(animatedModifier.graphicsLayer {
                alpha = sourceCoverAlpha.value
                // Modulate the vector label directly so alpha does not crop its small outset.
                compositingStrategy = CompositingStrategy.ModulateAlpha
            }.courseEditorContentTaper { taper.value }) {
                // The day source's text keeps its original height and scales uniformly. Keep
                // its badge on that same source card, instead of pinning it to the growing
                // editor bottom and sending it along a different path from the source glyphs.
                val badgeSourceModifier = if (sourceIsWeekCard) Modifier.fillMaxSize() else Modifier
                    .wrapContentSize(Alignment.TopStart, unbounded = true)
                    .requiredSize(with(density) { sourceRect.width.toDp() }, with(density) { sourceRect.height.toDp() })
                    .graphicsLayer {
                        transformOrigin = TransformOrigin(0f, 0f)
                        scaleX = courseEditorDaySourceScale(sourceRect, morphFrame.rect)
                        scaleY = scaleX
                    }
                Box(badgeSourceModifier) {
                    com.xiaomanjun.sleepdownschedule.feature.home.CourseAdjustmentBadge(
                        sourceBadge, backdrop, config,
                        Modifier.align(Alignment.BottomEnd)
                            .courseBadgeCornerAnchor(if (sourceIsWeekCard) corner.value else with(density) { sourceCornerPx.toDp() })
                    )
                }
            }
        }
    }
}

@Composable
private fun CourseEditorFormLayer(
    targetRect: Rect,
    cornerProvider: () -> androidx.compose.ui.unit.Dp,
    textColor: Color,
    formData: CourseEditorFormData,
    course: CourseEntity,
    copyDraft: CourseEntity?,
    contextMessage: String?,
    backdrop: Backdrop?,
    onDismissRequest: () -> Unit,
    onDismissHandlerChange: ((() -> Unit)?) -> Unit,
    onSave: (List<CourseEntity>, List<CourseEntity>) -> Unit,
    onDelete: (List<CourseEntity>) -> Unit,
    liveFrame: (() -> LiquidMorphFrame)? = null,
    interactive: Boolean = true,
    onContentLaidOut: () -> Unit = {}
) {
    if (targetRect.width <= 1f || targetRect.height <= 1f) {
        return
    }
    val density = LocalDensity.current
    var pagerPresentation by remember(formData, course) {
        mutableStateOf<CourseEditorPagerPresentation?>(null)
    }
    val nativeMaterial = com.xiaomanjun.sleepdownschedule.core.ui.designsystem.usesNativeMaterialMotion()
    val formStagger = remember { Animatable(if (liveFrame != null) 1f else 0f) }
    var contentLaidOut by remember { mutableStateOf(false) }
    LaunchedEffect(contentLaidOut) {
        if (contentLaidOut && liveFrame == null) {
            withFrameNanos { }
            formStagger.animateTo(
                1f,
                tween(CourseEditorMotion.FormRevealDurationMillis, easing = LinearEasing)
            )
        }
    }
    // Top-to-bottom order, with overlapping, independently eased upward entrances.
    val courseEditorFormRowEntrance: (Int) -> Float = { rowIndex ->
        CourseEditorMotion.formRowEntrance(rowIndex, formStagger.value)
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(if (interactive) Modifier else Modifier.clearAndSetSemantics { })
            .graphicsLayer {
                shape = RoundedRectangle(cornerProvider())
                clip = true
            }
    ) {
        Box(
            modifier = Modifier
                // The layout is now created at its final size, outside all shell motion.
                .wrapContentSize(align = Alignment.TopStart, unbounded = true)
                .requiredSize(
                    width = with(density) { targetRect.width.toDp() },
                    height = with(density) { targetRect.height.toDp() }
                )
                .onSizeChanged {
                    if (it.width > 0 && it.height > 0) {
                        contentLaidOut = true
                        onContentLaidOut()
                    }
                }
                .graphicsLayer {
                    liveFrame?.invoke()?.let { frame ->
                        transformOrigin = TransformOrigin(0f, 0f)
                        scaleX = frame.rect.width / targetRect.width
                        scaleY = frame.rect.height / targetRect.height
                        alpha = frame.content.destinationContentAlpha
                        // Only the liquid handoff needs a temporary image blur. Native forms
                        // keep sampling directly; stable liquid forms release this effect.
                        val blurPx = if (nativeMaterial || interactive) 0f else frame.content.destinationBlurPx *
                            (1f - courseEditorSmoothStep(0.90f, 1f, frame.shapeProgress))
                        renderEffect = platformBlurRenderEffect(blurPx)
                        compositingStrategy = if (blurPx > 0.01f) CompositingStrategy.Offscreen
                            else CompositingStrategy.ModulateAlpha
                    }
                },
            contentAlignment = Alignment.TopStart
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                run {
                    CompositionLocalProvider(LocalContentColor provides textColor) {
                        NormalizedCourseEditorScreen(
                            formData = formData,
                            initialCourse = course.takeIf { copyDraft == null },
                            copyDraft = copyDraft,
                            contextMessage = contextMessage,
                            onCancel = onDismissRequest,
                            onDismissHandlerChange = onDismissHandlerChange,
                            onSave = {},
                            onSaveCourses = { onSave(emptyList(), it) },
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

            pagerPresentation?.takeIf { it.visible && it.pageCount > 1 }?.let { presentation ->
                ProjectPagerIndicator(
                    pagerState = presentation.pagerState,
                    pageCount = presentation.pageCount,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .zIndex(20f)
                        .graphicsLayer { alpha = formStagger.value }
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
    muted: Boolean,
    expandedOutlineLight: Boolean,
    shape: androidx.compose.ui.graphics.Shape,
    progressProvider: () -> Float,
    alpha: Float,
    surfaceBackdrop: LayerBackdrop,
    modifier: Modifier = Modifier,
    morphAllocation: GlassMorphAllocation? = null,
    content: @Composable () -> Unit
) {
    if (!com.xiaomanjun.sleepdownschedule.core.ui.designsystem.usesNativeMaterialMotion()) {
        val base = if (muted) MutedCourseLightColor else courseCardBaseColor(config, course)
        val tintAlpha = courseGlassTintAlpha(config.cardAlpha, LocalGlassQuality.current, config.hasAnyWallpaper())
        MorphDialogSurface(
            surfaceColor = base.copy(alpha = tintAlpha), shape = shape,
            modifier = modifier.graphicsLayer { this.alpha = alpha },
            surfaceBackdrop = surfaceBackdrop,
            bottomLitTint = true,
            // Keep a plain local blur through motion and Open, on the existing crossfaded scene.
            backdrop = backdrop,
            blurRadius = config.courseCardBlur.coerceIn(20f, 30f).dp * LocalGlassQuality.current
        ) {
            CompositionLocalProvider(
                com.xiaomanjun.sleepdownschedule.core.ui.designsystem.LocalNativeMaterialForeground provides glassForegroundColor(config)
            ) { content() }
        }
        return
    }
    val finalDialogBlur = 22f
    val currentProgress = rememberUpdatedState(progressProvider)
    val editorBlur by remember(config.courseCardBlur) {
        derivedStateOf {
            courseEditorInterpolate(config.courseCardBlur, finalDialogBlur,
                quantizeHomeProgressiveBackdropBlurProgress(
                    courseEditorSmoothStep(0.62f, 1f, currentProgress.value.invoke())
                ))
        }
    }
    val flatEditorOpacity by remember(config.courseCardBlur, config.courseCardGlassEnabled) {
        derivedStateOf {
            courseEditorInterpolate(
                com.xiaomanjun.sleepdownschedule.core.performance.AppMaterialPreferences.policy.opacity(
                    config.courseCardBlur, com.xiaomanjun.sleepdownschedule.model.courseCardBlurMaximum(config.courseCardGlassEnabled)),
                com.xiaomanjun.sleepdownschedule.glass.FlatControlOpacity,
                courseEditorSmoothStep(0.12f, 0.62f, currentProgress.value.invoke())
            )
        }
    }
    Box(modifier = modifier.graphicsLayer { this.alpha = alpha }.drawWithCache {
        val bounds = morphAllocation?.localBounds() ?: Rect(0f, 0f, size.width, size.height)
        val outline = Path().apply {
            addOutline(shape.createOutline(bounds.size, layoutDirection, this@drawWithCache))
            translate(bounds.topLeft)
        }
        onDrawWithContent { clipPath(outline) { this@onDrawWithContent.drawContent() } }
    }) {
    CourseGlassCard(
        backdrop = backdrop,
        config = config,
        course = course,
        muted = muted,
        flatOpacityOverride = flatEditorOpacity,
        expandedOutlineLight = expandedOutlineLight,
        followTouch = false,
        nativeEdgeEffects = false,
        modifier = Modifier.fillMaxSize().glassBackdropProducer(surfaceBackdrop),
        shape = shape,
        blurOverride = editorBlur,
        editorSampleScale = if (expandedOutlineLight && currentProgress.value.invoke() <= 0.12f) 1f else 0.5f,
        morphAllocation = morphAllocation
    ) { }
    // Export only the native shell. The scrolling body is combined later, without recording
    // its own fixed-header consumer into the same producer.
    Box(Modifier.fillMaxSize()) { content() }
    }
}

@Composable
private fun CourseEditorSourceContent(
    course: CourseEntity,
    config: ScheduleConfigEntity,
    sourceIsWide: Boolean,
    adaptiveMetrics: HomeAdaptiveMetrics,
    adjustmentLabel: String?,
    dayAppearance: CourseEditorDayAppearance?,
    muted: Boolean,
    sourceCorner: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier
) {
    Box(modifier) {
        val badgeInset = if (adjustmentLabel != null) com.xiaomanjun.sleepdownschedule.feature.home.courseBadgeContentInset(
            with(LocalDensity.current) { courseAdjustmentBadgeHeight() }, sourceCorner
        ) else 0.dp
        if (sourceIsWide) {
            DayCourseCardContent(
                course = course,
                periods = dayAppearance?.periods.orEmpty(),
                showTime = dayAppearance?.showTime ?: false,
                showWeeks = dayAppearance?.showWeeks ?: false,
                config = config,
                tabletFontScale = dayAppearance?.tabletFontScale ?: if (adaptiveMetrics.isTabletLandscape) 1.10f else 1f,
                muted = muted,
                adjustmentLabel = adjustmentLabel
            )
        } else {
            com.xiaomanjun.sleepdownschedule.feature.home.week.WeekCourseOverlayCardContent(
                course, config, muted = muted, badgeInset = badgeInset)
        }
    }
}

private fun validSourceRect(rect: Rect?, rootSize: IntSize): Rect? {
    if (rect == null || rect.width <= 2f || rect.height <= 2f) return null
    if (rootSize.width <= 0 || rootSize.height <= 0) return null
    val root = Rect(0f, 0f, rootSize.width.toFloat(), rootSize.height.toFloat())
    if (!rect.overlaps(root)) return null
    // Preserve the original geometry even if part of a scrolling card is outside the window.
    // Such sources use the measured content clone instead of stretching a cropped snapshot.
    return rect
}
