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
 * The source is recorded independently of the animation. A retained blur surface changes
 * radius in the layer phase; only the near-zero radius blends with the clear scene.
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
        val clear = rememberGraphicsLayer()
        val blurred = rememberGraphicsLayer()
        val sampleScale = HomeFrozenBlurSampleScale
        val radius = with(LocalDensity.current) { 22.dp.toPx() } * sampleScale
        val effects = remember(radius) {
            List(HomeSceneBlurSteps + 1) { step ->
                val r = radius * step.coerceAtLeast(HomeSceneMinimumBlurStep) / HomeSceneBlurSteps
                BlurEffect(r, r, TileMode.Clamp)
            }
        }
        val recordedSize = remember { AtomicReference(IntSize.Zero) }
        val recordedSource = remember { AtomicReference<GraphicsLayer?>(null) }
        Modifier.graphicsLayer {
            val progress = latestProgress.value().coerceIn(0f, 1f)
            val step = (progress * HomeSceneBlurSteps).roundToInt().coerceIn(0, HomeSceneBlurSteps)
            val blend = (progress * HomeSceneBlurSteps / HomeSceneMinimumBlurStep).coerceIn(0f, 1f)
            clear.alpha = if (blend < 1f) 1f else 0f
            blurred.alpha = blend
            blurred.renderEffect = effects[step]
        }.drawWithContent {
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
                clear.record(size = fullSize) { drawLayer(scene) }
                blurred.record(size = reduced) {
                    withTransform({ scale(sx, sy, Offset.Zero) }) { drawLayer(scene) }
                }
                blurred.pivotOffset = Offset.Zero
                blurred.scaleX = 1f / sx
                blurred.scaleY = 1f / sy
                recordedSize.set(fullSize)
                recordedSource.set(scene)
            }
            // Do not read the animation here: a radius update must not re-record the source.
            drawLayer(clear)
            drawLayer(blurred)
        }
    } else Modifier.drawWithContent {
        if (prepareScene(false)) drawLayer(retainedScene) else drawContent()
    }
    Box(modifier.then(sampling), content = content)
}
