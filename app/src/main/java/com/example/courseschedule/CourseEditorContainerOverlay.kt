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
import androidx.compose.animation.core.keyframes
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
import androidx.compose.ui.text.TextStyle
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
import com.kyant.backdrop.catalog.components.LiquidPanel
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.roundToInt

private val CourseEditorPrimaryEasing = CubicBezierEasing(0.20f, 0.0f, 0.10f, 1.0f)
private val CourseEditorSettleEasing = CubicBezierEasing(0.24f, 0.0f, 0.30f, 1.0f)
private const val CourseEditorOpenDurationMillis = 340
private const val CourseEditorCloseDurationMillis = 350

@Composable
internal fun MirroredEdgeSnapshot(
    bitmap: Bitmap,
    insetFraction: Float,
    blurPx: Float,
    alphaProvider: () -> Float,
    modifier: Modifier = Modifier
) {
    val shader = remember(bitmap) {
        BitmapShader(bitmap, Shader.TileMode.MIRROR, Shader.TileMode.MIRROR)
    }
    val paint = remember(shader) {
        Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            this.shader = shader
        }
    }
    Box(
        modifier = modifier
            .graphicsLayer {
                this.alpha = alphaProvider().coerceIn(0f, 1f)
                renderEffect = if (blurPx > 0.01f) {
                    RenderEffect.createBlurEffect(
                        blurPx,
                        blurPx,
                        Shader.TileMode.CLAMP
                    ).asComposeRenderEffect()
                } else {
                    null
                }
            }
            .drawWithCache {
                val insetX = size.width * insetFraction.coerceIn(0f, 0.49f)
                val insetY = size.height * insetFraction.coerceIn(0f, 0.49f)
                val shaderMatrix = Matrix().apply {
                    setRectToRect(
                        RectF(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat()),
                        RectF(insetX, insetY, size.width - insetX, size.height - insetY),
                        Matrix.ScaleToFit.FILL
                    )
                }
                shader.setLocalMatrix(shaderMatrix)
                onDrawBehind {
                    drawContext.canvas.nativeCanvas.drawRect(
                        0f,
                        0f,
                        size.width,
                        size.height,
                        paint
                    )
                }
            }
    )
}

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
    val sourceBoundsInRoot: Rect?,
    val backgroundSnapshot: Bitmap? = null,
    val sourceCardSnapshot: Bitmap? = null
)

/**
 * An elliptically compensated outline that still advertises itself as CornerBasedShape, which is
 * the contract required by the liquid lens shader. The clip uses independent X/Y radii so a tall
 * week card and a wide day card both meet their source snapshot without a one-frame corner jump.
 */
private class CourseEditorMorphCornerShape(
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
    val progress = motionState.progress
    val backgroundScale = remember { Animatable(1f) }
    var closeDurationMillis by remember { mutableStateOf(DETAIL_SYSTEM_BACK_DURATION) }
    var deferredCompletion by remember { mutableStateOf<(() -> Unit)?>(null) }
    val latestOnRenderedCourseIdChange by rememberUpdatedState(onRenderedCourseIdChange)
    val latestOnPhaseChange by rememberUpdatedState(onPhaseChange)
    val latestOnSave by rememberUpdatedState(onSave)
    val latestOnDelete by rememberUpdatedState(onDelete)

    fun updatePhase(phase: CourseEditorOverlayPhase) {
        motionState.phase = phase
        latestOnPhaseChange(phase)
    }

    LaunchedEffect(request) {
        if (request != null) {
            updatePhase(CourseEditorOverlayPhase.Preparing)
            renderedRequest = request
            latestOnRenderedCourseIdChange(request.course.id)
            progress.snapTo(0f)
            backgroundScale.snapTo(1f)
            while (rootSize.width <= 0 || rootSize.height <= 0) withFrameNanos { }
            // Precompose and measure the backdrop-heavy editor while it is still transparent.
            repeat(2) { withFrameNanos { } }
            updatePhase(CourseEditorOverlayPhase.Opening)
            coroutineScope {
                launch {
                    backgroundScale.animateTo(
                        0.92f,
                        tween(BACKGROUND_OPEN_DURATION, easing = BackgroundOpenEasing)
                    )
                }
                launch {
                    progress.animateTo(
                        1f,
                        tween(DETAIL_OPEN_DURATION, easing = DetailOpenEasing)
                    )
                }
            }
            updatePhase(CourseEditorOverlayPhase.Open)
        } else if (renderedRequest != null) {
            updatePhase(CourseEditorOverlayPhase.Closing)
            coroutineScope {
                launch {
                    backgroundScale.animateTo(
                        1f,
                        tween(BACKGROUND_EXIT_DURATION, easing = BackgroundExitEasing)
                    )
                }
                launch {
                    progress.animateTo(
                        0f,
                        tween(closeDurationMillis, easing = DetailExitEasing)
                    )
                }
            }
            updatePhase(CourseEditorOverlayPhase.Disposing)
            renderedRequest = null
            latestOnRenderedCourseIdChange(null)
            closeDurationMillis = DETAIL_SYSTEM_BACK_DURATION
            updatePhase(CourseEditorOverlayPhase.Idle)
            deferredCompletion?.also { completion ->
                deferredCompletion = null
                completion()
            }
        } else {
            if (motionState.phase != CourseEditorOverlayPhase.Idle || renderedRequest != null) {
                updatePhase(CourseEditorOverlayPhase.Disposing)
            }
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
    fun dismiss(useToolbarDuration: Boolean) {
        if (motionState.phase == CourseEditorOverlayPhase.Closing || motionState.phase == CourseEditorOverlayPhase.Disposing) return
        closeDurationMillis = if (useToolbarDuration) DETAIL_TOOLBAR_BACK_DURATION else DETAIL_SYSTEM_BACK_DURATION
        onDismissRequest()
    }
    val saveEditedCourse: (CourseEntity) -> Unit = { edited ->
        if (deferredCompletion == null) {
            deferredCompletion = {
                latestOnSave(shownRequest.course, edited, shownRequest.targetWeek)
            }
            dismiss(useToolbarDuration = true)
        }
    }
    val deleteEditedCourse: (CourseEntity) -> Unit = { deleteCourse ->
        if (deferredCompletion == null) {
            deferredCompletion = {
                latestOnDelete(deleteCourse, shownRequest.targetWeek)
            }
            dismiss(useToolbarDuration = true)
        }
    }
    BackHandler(enabled = isOverlayActive) { dismiss(useToolbarDuration = false) }

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
    val p = progress.value.coerceIn(0f, 1f)
    val initialScaleX = (sourceRect.width / targetRect.width).coerceAtLeast(0.001f)
    val initialScaleY = (sourceRect.height / targetRect.height).coerceAtLeast(0.001f)
    val scaleX = initialScaleX + (1f - initialScaleX) * p
    val scaleY = initialScaleY + (1f - initialScaleY) * p
    val translationX = (sourceRect.left - targetRect.left) * (1f - p)
    val translationY = (sourceRect.top - targetRect.top) * (1f - p)
    val sourceCornerPx = remember(sourceRect, density) {
        with(density) { if (sourceRect.width >= 220.dp.toPx()) 24.dp.toPx() else 8.dp.toPx() }
    }
    val targetCornerPx = with(density) { 32.dp.toPx() }
    val visualCornerPx = sourceCornerPx + (targetCornerPx - sourceCornerPx) * p
    val morphShape = remember(visualCornerPx, scaleX, scaleY) {
        CourseEditorMorphCornerShape(
            radiusX = visualCornerPx / scaleX.coerceAtLeast(0.001f),
            radiusY = visualCornerPx / scaleY.coerceAtLeast(0.001f)
        )
    }
    val contentAlpha = ((p - 0.1f) / 0.5f).coerceIn(0f, 1f)
    val sourceCoverAlpha = (1f - p * 3f).coerceIn(0f, 1f)
    val editorFormBackdrop = backdrop
    val textColor = glassForegroundColor(config)
    val blurProgress = ((1f - backgroundScale.value) / 0.08f).coerceIn(0f, 1f)
    val blurPx = blurProgress * 12f * density.density
    // Keep the reflected edge equal to the primary 12dp blur. A radius difference creates
    // a visible contour exactly where the hard rounded clip hands off to the edge extension.
    val edgeFillBlurPx = 12f * density.density
    val backgroundCorner = (24f * blurProgress).dp
    // The morph window itself scales independently on X/Y, but the live glass must remain in
    // final-screen coordinates. Fully invert both the scale and translation so the moving clip
    // reveals a stationary backdrop instead of stretching or zooming it. This is required for
    // both tall cards and short/wide cards; a uniform max-axis scale only fixed the tall case.
    val glassCounterScaleX = 1f / scaleX.coerceAtLeast(0.001f)
    val glassCounterScaleY = 1f / scaleY.coerceAtLeast(0.001f)
    val glassCounterTranslationX = -translationX / scaleX.coerceAtLeast(0.001f)
    val glassCounterTranslationY = -translationY / scaleY.coerceAtLeast(0.001f)

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { rootSize = it }
    ) {
        shownRequest.backgroundSnapshot?.let { background ->
            // Map the snapshot into exactly the same inset rect as the shrinking primary image.
            // MIRROR tiling reflects its innermost edge ring out into the uncovered gutters, so
            // the rounded boundary stays continuous instead of exposing a scaled copy's seams.
            MirroredEdgeSnapshot(
                bitmap = background,
                insetFraction = 0.04f,
                blurPx = edgeFillBlurPx,
                alphaProvider = { blurProgress },
                modifier = Modifier
                    .fillMaxSize()
            )
            Image(
                bitmap = background.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        this.scaleX = backgroundScale.value
                        this.scaleY = backgroundScale.value
                        renderEffect = if (blurPx > 0.01f) {
                            RenderEffect.createBlurEffect(blurPx, blurPx, Shader.TileMode.CLAMP)
                                .asComposeRenderEffect()
                        } else null
                    }
                    .drawWithContent {
                        drawContent()
                        if (blurProgress > 0.001f) {
                            val radiusPx = backgroundCorner.toPx()
                            val outside = Path().apply {
                                fillType = PathFillType.EvenOdd
                                addRect(Rect(0f, 0f, size.width, size.height))
                                addRoundRect(
                                    RoundRect(
                                        rect = Rect(0f, 0f, size.width, size.height),
                                        cornerRadius = CornerRadius(radiusPx, radiusPx)
                                    )
                                )
                            }
                            drawPath(outside, Color.Black, blendMode = BlendMode.Clear)

                            // Feather the inside of the rounded boundary instead of handing the
                            // primary image to the mirrored gutter with a one-pixel hard clip.
                            val featherPx = 18.dp.toPx() * blurProgress
                            val featherSteps = 10
                            repeat(featherSteps) { index ->
                                val linear = 1f - index / featherSteps.toFloat()
                                val remaining = linear * linear * (3f - 2f * linear)
                                drawRoundRect(
                                    color = Color.Black.copy(alpha = 0.115f),
                                    cornerRadius = CornerRadius(radiusPx, radiusPx),
                                    style = Stroke(width = featherPx * 2f * remaining),
                                    blendMode = BlendMode.DstOut
                                )
                            }
                        }
                    }
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = (p * 0.5f).coerceIn(0f, 0.5f)))
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { dismiss(useToolbarDuration = false) }
        )
        Box(
            modifier = Modifier
                .offset { IntOffset(targetRect.left.roundToInt(), targetRect.top.roundToInt()) }
                .size(
                    width = with(density) { targetRect.width.toDp() },
                    height = with(density) { targetRect.height.toDp() }
                )
                .graphicsLayer {
                    transformOrigin = TransformOrigin(0f, 0f)
                    this.scaleX = scaleX
                    this.scaleY = scaleY
                    this.translationX = translationX
                    this.translationY = translationY
                }
                .clip(morphShape)
        ) {
            // Render the liquid editor once at its final, stationary coordinates and transform
            // that composed layer as a whole. Passing the moving morph shape directly into
            // drawBackdrop makes its wallpaper sample window move every frame, producing the
            // impression that the wallpaper is sliced and dragged inside the glass.
            CourseEditorAnimatedContainer(
                backdrop = backdrop,
                config = config,
                course = shownRequest.course,
                shape = RoundedCornerShape(32.dp),
                progress = 1f,
                alpha = 1f,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        // Keep the real glass locked to its final coordinates while only the outer
                        // clip window morphs from the source card rectangle.
                        transformOrigin = TransformOrigin(0f, 0f)
                        this.scaleX = glassCounterScaleX
                        this.scaleY = glassCounterScaleY
                        this.translationX = glassCounterTranslationX
                        this.translationY = glassCounterTranslationY
                    }
            ) {
                Box(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize().graphicsLayer { alpha = contentAlpha }) {
                        CompositionLocalProvider(LocalContentColor provides textColor) {
                            NormalizedCourseEditorScreen(
                                formData = formData,
                                initialCourse = shownRequest.course,
                                onCancel = { dismiss(useToolbarDuration = true) },
                                onSave = saveEditedCourse,
                                onDelete = deleteEditedCourse,
                                backdrop = editorFormBackdrop
                            )
                        }
                    }
                }
            }
            if (shownRequest.sourceCardSnapshot != null && sourceCoverAlpha > 0.001f) {
                // Keep the source pixels on the geometry transform only. They must remain an
                // exact FillBounds match and must not inherit the glass counter-scale above.
                Image(
                    bitmap = shownRequest.sourceCardSnapshot.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = sourceCoverAlpha }
                )
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
    shape: Shape,
    progress: Float,
    alpha: Float,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
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
