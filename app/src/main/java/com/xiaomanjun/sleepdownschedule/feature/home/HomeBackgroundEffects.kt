package com.xiaomanjun.sleepdownschedule.feature.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.effects.blur
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.roundToInt

@Composable
internal fun HomeBackgroundZoomLayer(
    zoom: () -> Float,
    dimProgress: () -> Float,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
            .drawWithContent {
                drawContent()
                // Draw after the blurred/zoomed home, outside its cached sampling layers.
                // One shared scrim avoids stacking darkness when an overlay hands off to another.
                val dimAlpha = 0.08f * dimProgress().coerceIn(0f, 1f)
                if (dimAlpha > 0f) drawRect(ComposeColor.Black.copy(alpha = dimAlpha))
            }
            .graphicsLayer {
                val currentZoom = zoom()
                scaleX = currentZoom
                scaleY = currentZoom
            },
        content = content
    )
}

/**
 * A clear scene and one fixed blur endpoint. Animation changes only their blend, never a
 * sequence of RenderEffects. The blur resources exist only while an overlay needs them.
 */
@Composable
internal fun HomeBackgroundBlurLayer(
    blurProgress: () -> Float,
    useFrozenHomeScene: () -> Boolean,
    retainedScene: GraphicsLayer,
    prepareScene: ContentDrawScope.(frozen: Boolean) -> Boolean,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val latestProgress = rememberUpdatedState(blurProgress)
    val active by remember { derivedStateOf { latestProgress.value() > 0f } }
    val sampling = if (active) {
        val liveScene = rememberGraphicsLayer()
        val blurred = rememberGraphicsLayer()
        val sampleScale = HomeFrozenBlurSampleScale
        val radius = with(LocalDensity.current) { 22.dp.toPx() } * sampleScale
        val effect = remember(radius) { BlurEffect(radius, radius, TileMode.Clamp) }
        val recordedSize = remember { AtomicReference(IntSize.Zero) }
        val recordedSource = remember { AtomicReference<GraphicsLayer?>(null) }
        Modifier.drawWithContent {
            val scene = if (prepareScene(useFrozenHomeScene())) retainedScene else {
                liveScene.record { this@drawWithContent.drawContent() }
                liveScene
            }
            val fullSize = scene.size
            if (fullSize.width <= 0 || fullSize.height <= 0) return@drawWithContent
            if (recordedSize.get() != fullSize || recordedSource.get() !== scene) {
                val reduced = IntSize(
                    (fullSize.width * sampleScale).roundToInt().coerceAtLeast(1),
                    (fullSize.height * sampleScale).roundToInt().coerceAtLeast(1)
                )
                val sx = reduced.width.toFloat() / fullSize.width
                val sy = reduced.height.toFloat() / fullSize.height
                blurred.record(size = reduced) {
                    withTransform({ scale(sx, sy, Offset.Zero) }) { drawLayer(scene) }
                }
                blurred.renderEffect = effect
                blurred.pivotOffset = Offset.Zero
                blurred.scaleX = 1f / sx
                blurred.scaleY = 1f / sy
                recordedSize.set(fullSize)
                recordedSource.set(scene)
            }
            val alpha = latestProgress.value().coerceIn(0f, 1f)
            blurred.alpha = alpha
            if (alpha < 1f) drawLayer(scene)
            drawLayer(blurred)
        }
    } else Modifier.drawWithContent {
        if (prepareScene(false)) drawLayer(retainedScene) else drawContent()
    }
    Box(modifier.then(sampling), content = content)
}
