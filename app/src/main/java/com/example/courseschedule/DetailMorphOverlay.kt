package com.example.courseschedule

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

val DetailOpenEasing = Easing { fraction ->
    val inverse = 1f - fraction
    1f - inverse * inverse * inverse * inverse
}

val DetailExitEasing = Easing { fraction ->
    1f - (1f - fraction) *
        (1f - fraction) *
        (1f - fraction)
}

val BackgroundOpenEasing = CubicBezierEasing(0.3f, 0.72f, 0.2f, 1.0f)
val BackgroundExitEasing = CubicBezierEasing(0.3f, 0.65f, 0.35f, 1.0f)

const val DETAIL_OPEN_DURATION = 520
const val DETAIL_SYSTEM_BACK_DURATION = 370
const val DETAIL_TOOLBAR_BACK_DURATION = 400
const val BACKGROUND_OPEN_DURATION = 520
const val BACKGROUND_EXIT_DURATION = 370

sealed interface DetailMorphState {
    data object Idle : DetailMorphState
    data object Capturing : DetailMorphState
    data object Opening : DetailMorphState
    data object Opened : DetailMorphState
    data object Closing : DetailMorphState
}

data class DetailMorphRequest(
    val scheduleId: Int,
    val sourceBounds: Rect,
    val backgroundSnapshot: Bitmap,
    val sourceCardSnapshot: Bitmap
)

data class DetailMorphValues(
    val backgroundAlpha: Float,
    val sourceSnapshotAlpha: Float,
    val contentAlpha: Float,
    val translationX: Float,
    val translationY: Float,
    val scale: Float,
    val clipBottom: Float,
    val progress: Float
)

private class DetailMorphClipShape(
    private val screenWidth: Float,
    private val screenCornerRadiusPx: Float,
    private val sourceCornerRadiusPx: Float,
    private val morphValues: State<DetailMorphValues>
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val state = morphValues.value
        val radiusPx = if (state.progress >= 1f) {
            0f
        } else {
            sourceCornerRadiusPx +
                (screenCornerRadiusPx - sourceCornerRadiusPx) * state.progress
        }
        val compensatedRadiusDp = (radiusPx / state.scale / density.density).dp
        return RoundedCornerShape(compensatedRadiusDp).createOutline(
            size = Size(
                width = screenWidth,
                height = state.clipBottom.coerceAtLeast(1f)
            ),
            layoutDirection = layoutDirection,
            density = density
        )
    }
}

@Composable
fun DetailScheduleMorphOverlay(
    request: DetailMorphRequest,
    detailState: AppState,
    onMorphStateChange: (DetailMorphState) -> Unit,
    onSave: (ScheduleConfigEntity, List<PeriodEntity>) -> Unit,
    onPreviewLiveUpdate: () -> Unit,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val morphProgress = remember(request) { Animatable(0f) }
    val backgroundScale = remember(request) { Animatable(1f) }
    val closing = remember(request) { androidx.compose.runtime.mutableStateOf(false) }
    var exitCommitRequest by remember(request) { mutableIntStateOf(0) }
    var exitUsesToolbarDuration by remember(request) { mutableStateOf(false) }

    fun close(useToolbarDuration: Boolean) {
        if (closing.value) return
        closing.value = true
        onMorphStateChange(DetailMorphState.Closing)
        scope.launch {
            coroutineScope {
                launch {
                    backgroundScale.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(
                            durationMillis = BACKGROUND_EXIT_DURATION,
                            easing = BackgroundExitEasing
                        )
                    )
                }
                launch {
                    morphProgress.animateTo(
                        targetValue = 0f,
                        animationSpec = tween(
                            durationMillis = if (useToolbarDuration) {
                                DETAIL_TOOLBAR_BACK_DURATION
                            } else {
                                DETAIL_SYSTEM_BACK_DURATION
                            },
                            easing = DetailExitEasing
                        )
                    )
                }
            }
            onFinished()
        }
    }

    BackHandler(enabled = !closing.value) {
        exitUsesToolbarDuration = false
        exitCommitRequest++
    }

    LaunchedEffect(request) {
        onMorphStateChange(DetailMorphState.Opening)
        // Compose, measure and record the real settings page while it is still alpha 0.
        // Creating the backdrop-heavy page after p > 0.1 caused a large mid-animation frame.
        withFrameNanos { }
        withFrameNanos { }
        coroutineScope {
            launch {
                backgroundScale.animateTo(
                    targetValue = 0.92f,
                    animationSpec = tween(
                        durationMillis = BACKGROUND_OPEN_DURATION,
                        easing = BackgroundOpenEasing
                    )
                )
            }
            launch {
                morphProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(
                        durationMillis = DETAIL_OPEN_DURATION,
                        easing = DetailOpenEasing
                    )
                )
            }
        }
        onMorphStateChange(DetailMorphState.Opened)
    }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val screenWidth = with(density) { maxWidth.toPx() }
        val screenHeight = with(density) { maxHeight.toPx() }
        val source = request.sourceBounds
        val initialContainerScale = (source.width / screenWidth).coerceAtLeast(0.001f)
        val sourceSnapshotCorner = (
            source.height / 2f / initialContainerScale / density.density
            ).dp
        val morphValues = remember(request, screenWidth, screenHeight) {
            derivedStateOf {
                val p = morphProgress.value
                val backgroundAlpha = (p * 0.5f).coerceIn(0f, 0.5f)
                val sourceSnapshotAlpha = (1f - p * 3f).coerceIn(0f, 1f)
                val contentAlpha = ((p - 0.1f) / 0.5f).coerceIn(0f, 1f)
                val initialScale = source.width / screenWidth
                val scale = initialScale + (1f - initialScale) * p
                val sourceCenterX = source.left + source.width / 2f
                val screenCenterX = screenWidth / 2f
                val translationX = (sourceCenterX - screenCenterX) * (1f - p)
                val translationY = source.top * (1f - p)
                val clipBottom = source.height + 20f +
                    (screenHeight - source.height - 20f) * p
                DetailMorphValues(
                    backgroundAlpha = backgroundAlpha,
                    sourceSnapshotAlpha = sourceSnapshotAlpha,
                    contentAlpha = contentAlpha,
                    translationX = translationX,
                    translationY = translationY,
                    scale = scale,
                    clipBottom = clipBottom,
                    progress = p
                )
            }
        }
        val values by morphValues
        // Threshold booleans are derived so composition only invalidates when they flip,
        // instead of on every animation frame that merely changes the float values.
        val showSourceSnapshot by remember(morphValues) {
            derivedStateOf { morphValues.value.sourceSnapshotAlpha > 0f }
        }
        val morphStillAnimating by remember(morphValues) {
            derivedStateOf { morphValues.value.progress < 0.999f }
        }
        val animatedClipShape = remember(request, screenWidth, morphValues) {
            DetailMorphClipShape(
                screenWidth = screenWidth,
                screenCornerRadiusPx = with(density) { 32.dp.toPx() },
                sourceCornerRadiusPx = source.height / 2f,
                morphValues = morphValues
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    // Read the Animatable here so each frame only invalidates this layer;
                    // the blur radius shares the same deferred read instead of being
                    // recomputed in composition every frame.
                    val bgScale = backgroundScale.value
                    scaleX = bgScale
                    scaleY = bgScale
                    shape = RoundedCornerShape(20.dp)
                    clip = bgScale < 0.999f
                    val blurProgress = ((1f - bgScale) / (1f - 0.92f)).coerceIn(0f, 1f)
                    val blurPx = (blurProgress * 6f).coerceIn(0f, 6f) * density.density
                    renderEffect = platformBlurRenderEffect(blurPx)
                }
        ) {
            Image(
                bitmap = request.backgroundSnapshot.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.fillMaxSize()
            )
        }

        Box(
            Modifier
                .fillMaxSize()
                // drawBehind defers the scrim alpha read to the draw phase, so the fade
                // no longer recomposes this subtree every frame. Rendering is identical.
                .drawBehind {
                    drawRect(Color.Black.copy(alpha = morphValues.value.backgroundAlpha))
                }
                .pointerInput(request) {
                    awaitPointerEventScope {
                        while (true) {
                            awaitPointerEvent().changes.forEach { it.consume() }
                        }
                    }
                }
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    transformOrigin = TransformOrigin(0.5f, 0f)
                    scaleX = values.scale
                    scaleY = values.scale
                    translationX = values.translationX
                    translationY = values.translationY
                    shape = animatedClipShape
                    clip = values.progress < 1f
                }
        ) {
            if (showSourceSnapshot) {
                Image(
                    bitmap = request.sourceCardSnapshot.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .fillMaxWidth()
                        .graphicsLayer {
                            shape = RoundedCornerShape(sourceSnapshotCorner)
                            clip = true
                        }
                        .graphicsLayer { alpha = values.sourceSnapshotAlpha },
                    contentScale = ContentScale.FillWidth
                )
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = values.contentAlpha }
            ) {
                DetailActivityScaffold(
                    title = "课表详细设置",
                    config = detailState.config,
                    onBack = {
                        exitUsesToolbarDuration = true
                        exitCommitRequest++
                    }
                ) { backdrop ->
                    ScheduleConfigScreen(
                        state = detailState,
                        backdrop = backdrop,
                        section = SettingsSection.Schedule,
                        onSave = onSave,
                        onPreviewLiveUpdate = onPreviewLiveUpdate,
                        exitCommitRequest = exitCommitRequest,
                        onExitCommitFinished = { saved ->
                            if (saved) close(useToolbarDuration = exitUsesToolbarDuration)
                        }
                    )
                }
            }
        }
        if (morphStillAnimating || closing.value) {
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(request, closing.value) {
                        awaitPointerEventScope {
                            while (true) {
                                awaitPointerEvent().changes.forEach { it.consume() }
                            }
                        }
                    }
            )
        }
    }
}
