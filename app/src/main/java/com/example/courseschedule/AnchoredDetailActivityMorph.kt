package com.example.courseschedule

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.roundToInt

private const val AnchoredMorphSnapshotTokenExtra = "anchored_morph_snapshot_token"

internal enum class AnchoredDetailMotionStyle {
    Liquid,
    DetailSettings,
    Parabolic
}

internal data class AnchoredMorphSnapshots(
    val background: Bitmap,
    val source: Bitmap? = null
)

internal object AnchoredMorphSnapshotStore {
    private val nextToken = AtomicLong(1L)
    private val snapshots = ConcurrentHashMap<Long, AnchoredMorphSnapshots>()

    fun put(value: AnchoredMorphSnapshots): Long {
        val token = nextToken.getAndIncrement()
        snapshots[token] = value
        if (snapshots.size > 6) {
            snapshots.keys.sorted().dropLast(6).forEach(snapshots::remove)
        }
        return token
    }

    fun get(token: Long?): AnchoredMorphSnapshots? = token?.let(snapshots::get)

    fun remove(token: Long?) {
        token?.let(snapshots::remove)
    }
}

internal fun Intent.putAnchoredMorphSnapshots(value: AnchoredMorphSnapshots): Intent = apply {
    putExtra(AnchoredMorphSnapshotTokenExtra, AnchoredMorphSnapshotStore.put(value))
}

internal fun Intent.anchoredMorphSnapshotTokenOrNull(): Long? =
    getLongExtra(AnchoredMorphSnapshotTokenExtra, 0L).takeIf { it > 0L }

fun Activity.startActivityWithAnchoredMorph(intent: Intent) {
    startActivity(intent)
    @Suppress("DEPRECATION")
    overridePendingTransition(0, 0)
}

private data class AnchoredDetailMorphValues(
    val backgroundAlpha: Float,
    val sourceAlpha: Float,
    val contentAlpha: Float,
    val translationX: Float,
    val translationY: Float,
    val scale: Float,
    val clipBottom: Float,
    val progress: Float
)

private class AnchoredDetailClipShape(
    private val screenWidth: Float,
    private val screenCornerRadiusPx: Float,
    private val sourceCornerRadiusPx: Float,
    private val values: State<AnchoredDetailMorphValues>
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val current = values.value
        val radiusPx = sourceCornerRadiusPx +
            (screenCornerRadiusPx - sourceCornerRadiusPx) * current.progress
        val compensatedRadius = (radiusPx / current.scale.coerceAtLeast(0.001f) / density.density).dp
        return RoundedCornerShape(compensatedRadius).createOutline(
            size = Size(screenWidth, current.clipBottom.coerceAtLeast(1f)),
            layoutDirection = layoutDirection,
            density = density
        )
    }
}

@Composable
internal fun AnchoredDetailActivityMorph(
    sourceBounds: Rect?,
    sourceCornerRadius: Dp,
    onFinished: () -> Unit,
    onSourceHandoff: () -> Unit = {},
    sourceContent: @Composable BoxScope.() -> Unit,
    backgroundSnapshot: Bitmap? = null,
    sourceSnapshot: Bitmap? = null,
    motionStyle: AnchoredDetailMotionStyle = AnchoredDetailMotionStyle.Liquid,
    content: @Composable (requestClose: () -> Unit) -> Unit
) {
    var rootSize by remember { mutableStateOf(IntSize.Zero) }
    val progress = remember(sourceBounds) { Animatable(if (sourceBounds == null) 1f else 0f) }
    val backgroundScale = remember(backgroundSnapshot) { Animatable(1f) }
    var closing by remember(sourceBounds) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val usesPageMotion = motionStyle != AnchoredDetailMotionStyle.Liquid

    fun close() {
        if (closing) return
        if (sourceBounds == null) {
            onFinished()
            return
        }
        closing = true
        scope.launch {
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
                        if (usesPageMotion) {
                            tween(DETAIL_SYSTEM_BACK_DURATION, easing = DetailExitEasing)
                        } else {
                            tween(HomeAnchoredMorphCloseDurationMillis, easing = LinearEasing)
                        }
                    )
                }
            }
            onFinished()
        }
    }

    BackHandler(onBack = ::close)
    LaunchedEffect(sourceBounds, rootSize, motionStyle) {
        if (sourceBounds != null && rootSize.width > 0 && rootSize.height > 0 && progress.value < 1f) {
            // Precompose the destination before motion starts. This matches the detailed-settings
            // transition and avoids a backdrop-heavy page entering composition mid-animation.
            withFrameNanos { }
            withFrameNanos { }
            onSourceHandoff()
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
                        if (usesPageMotion) {
                            tween(DETAIL_OPEN_DURATION, easing = DetailOpenEasing)
                        } else {
                            tween(HomeAnchoredMorphOpenDurationMillis, easing = LinearEasing)
                        }
                    )
                }
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .onSizeChanged { rootSize = it }
    ) {
        backgroundSnapshot?.let { bitmap ->
            MorphSnapshotBackground(
                bitmap = bitmap,
                backgroundScaleProvider = { backgroundScale.value },
                modifier = Modifier.fillMaxSize()
            )
        }
        if (rootSize.width <= 0 || rootSize.height <= 0) return@Box
        if (usesPageMotion) {
            AnchoredSettingsStyleMorph(
                sourceBounds = sourceBounds,
                sourceCornerRadius = sourceCornerRadius,
                sourceSnapshot = sourceSnapshot,
                progress = progress.value,
                closing = closing,
                parabolic = motionStyle == AnchoredDetailMotionStyle.Parabolic,
                onClose = ::close,
                sourceContent = sourceContent,
                content = content
            )
        } else {
            AnchoredLiquidStyleMorph(
                sourceBounds = sourceBounds,
                rootSize = rootSize,
                sourceCornerRadius = sourceCornerRadius,
                sourceSnapshot = sourceSnapshot,
                progress = progress.value,
                closing = closing,
                onClose = ::close,
                sourceContent = sourceContent,
                content = content
            )
        }
    }
}

@Composable
private fun BoxScope.AnchoredLiquidStyleMorph(
    sourceBounds: Rect?,
    rootSize: IntSize,
    sourceCornerRadius: Dp,
    sourceSnapshot: Bitmap?,
    progress: Float,
    closing: Boolean,
    onClose: () -> Unit,
    sourceContent: @Composable BoxScope.() -> Unit,
    content: @Composable (requestClose: () -> Unit) -> Unit
) {
    val density = LocalDensity.current
    val full = Rect(0f, 0f, rootSize.width.toFloat(), rootSize.height.toFloat())
    val source = sourceBounds ?: full
    val targetCornerRadiusPx = deviceScreenCornerRadiusPx()
    val geometry = homeAnchoredMorphGeometry(
        source = source,
        target = full,
        rawProgress = progress,
        closing = closing,
        sourceCornerRadiusPx = with(density) { sourceCornerRadius.toPx() },
        pinchDiameterPx = with(density) { 22.dp.toPx() },
        minimumDropPx = with(density) { 10.dp.toPx() },
        maximumDropPx = with(density) { 58.dp.toPx() },
        maximumArcPx = with(density) { 48.dp.toPx() },
        targetCornerRadiusPx = targetCornerRadiusPx
    )
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.24f * geometry.expansionProgress))
            .pointerInput(closing) {
                awaitPointerEventScope {
                    while (true) awaitPointerEvent().changes.forEach { it.consume() }
                }
            }
    )
    Box(
        Modifier
            .offset {
                IntOffset(geometry.rect.left.roundToInt(), geometry.rect.top.roundToInt())
            }
            .size(
                with(density) { geometry.rect.width.toDp() },
                with(density) { geometry.rect.height.toDp() }
            )
            .graphicsLayer {
                clip = true
                shape = RoundedCornerShape(with(density) { geometry.cornerRadiusPx.toDp() })
                compositingStrategy = CompositingStrategy.Offscreen
                renderEffect = platformBlurRenderEffect(
                    detailMotionBlurRadiusDp(geometry.expansionProgress) * density.density
                )
            }
    ) {
        if (geometry.sourceAlpha > 0.001f) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = geometry.sourceAlpha
                        scaleX = geometry.sourceScale
                        scaleY = geometry.sourceScale
                        shape = RoundedCornerShape(sourceCornerRadius)
                        clip = true
                    }
            ) {
                if (sourceSnapshot != null) {
                    Image(
                        bitmap = sourceSnapshot.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.FillBounds
                    )
                } else {
                    sourceContent()
                }
            }
        }
        if (geometry.contentAlpha > 0.001f) {
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = geometry.contentAlpha }) {
                content(onClose)
            }
        }
    }
}

@Composable
private fun BoxScope.AnchoredSettingsStyleMorph(
    sourceBounds: Rect?,
    sourceCornerRadius: Dp,
    sourceSnapshot: Bitmap?,
    progress: Float,
    closing: Boolean,
    parabolic: Boolean,
    onClose: () -> Unit,
    sourceContent: @Composable BoxScope.() -> Unit,
    content: @Composable (requestClose: () -> Unit) -> Unit
) {
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val screenWidth = with(density) { maxWidth.toPx() }
        val screenHeight = with(density) { maxHeight.toPx() }
        val source = sourceBounds ?: Rect(0f, 0f, screenWidth, screenHeight)
        val initialScale = (source.width / screenWidth).coerceAtLeast(0.001f)
        val p = progress.coerceIn(0f, 1f)
        val scale = initialScale + (1f - initialScale) * p
        val sourceCenterX = source.left + source.width / 2f
        val targetCornerRadiusPx = deviceScreenCornerRadiusPx()
        val arcHeight = if (parabolic) {
            minOf(screenHeight * 0.10f, with(density) { 96.dp.toPx() })
        } else {
            0f
        }
        val initialClipBottom = source.height / initialScale
        val values = AnchoredDetailMorphValues(
            backgroundAlpha = (p * 0.22f).coerceIn(0f, 0.22f),
            sourceAlpha = (1f - p * 3f).coerceIn(0f, 1f),
            contentAlpha = ((p - 0.1f) / 0.5f).coerceIn(0f, 1f),
            translationX = (sourceCenterX - screenWidth / 2f) * (1f - p),
            // A quadratic arc keeps both endpoints exact while lifting the page through the
            // middle of the transition. This is intentionally page motion, without liquid pinch.
            translationY = source.top * (1f - p) - 4f * arcHeight * p * (1f - p),
            scale = scale,
            clipBottom = initialClipBottom + (screenHeight - initialClipBottom) * p,
            progress = p
        )
        val valuesState = rememberUpdatedState(values)
        val clipShape = remember(
            source,
            screenWidth,
            sourceCornerRadius,
            targetCornerRadiusPx,
            density.density
        ) {
            AnchoredDetailClipShape(
                screenWidth = screenWidth,
                screenCornerRadiusPx = targetCornerRadiusPx,
                sourceCornerRadiusPx = with(density) { sourceCornerRadius.toPx() },
                values = valuesState
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = values.backgroundAlpha))
                .pointerInput(closing) {
                    awaitPointerEventScope {
                        while (true) awaitPointerEvent().changes.forEach { it.consume() }
                    }
                }
        )
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    transformOrigin = TransformOrigin(0.5f, 0f)
                    scaleX = values.scale
                    scaleY = values.scale
                    translationX = values.translationX
                    translationY = values.translationY
                    shape = clipShape
                    clip = true
                    compositingStrategy = CompositingStrategy.Offscreen
                    renderEffect = platformBlurRenderEffect(
                        detailMotionBlurRadiusDp(values.progress) * density.density
                    )
                }
        ) {
            if (values.sourceAlpha > 0.001f) {
                if (sourceSnapshot != null) {
                    Image(
                        bitmap = sourceSnapshot.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(with(density) { initialClipBottom.toDp() })
                            .graphicsLayer {
                                alpha = values.sourceAlpha
                                shape = RoundedCornerShape(
                                    sourceCornerRadius / initialScale
                                )
                                clip = true
                            },
                        contentScale = ContentScale.FillBounds
                    )
                } else {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(with(density) { (source.height / initialScale).toDp() })
                            .graphicsLayer { alpha = values.sourceAlpha },
                        content = sourceContent
                    )
                }
            }
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = values.contentAlpha }) {
                content(onClose)
            }
        }
    }
}
