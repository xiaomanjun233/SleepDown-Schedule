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
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.effects.blur
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

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

@Composable
internal fun HomeBackgroundBlurLayer(
    blurProgress: () -> Float,
    useFrozenHomeScene: () -> Boolean,
    closing: () -> Boolean,
    sceneKey: Any,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val density = LocalDensity.current
    // Auto composition: this is just a display-list wrapper, never another full-screen texture.
    // Its alpha can hide the sharp pass without changing the scene shared by foreground glass.
    val clearSceneLayer = rememberGraphicsLayer()
    val frozenBlurLayer = rememberGraphicsLayer()
    val liveSceneLayer = rememberGraphicsLayer()
    val latestBlurProgress = rememberUpdatedState(blurProgress)
    val latestUseFrozenScene = rememberUpdatedState(useFrozenHomeScene)
    val recordLiveScene by remember {
        derivedStateOf { !latestUseFrozenScene.value() && latestBlurProgress.value() > 0f }
    }
    val frozenRecordKey = remember { AtomicReference<Any?>(null) }
    val frozenRecordSize = remember { AtomicReference(IntSize.Zero) }
    val sampleScale = HomeFrozenBlurSampleScale
    val maximumBlurPx = with(density) { 22.dp.toPx() }
    val frozenBlurEffects = remember(maximumBlurPx, sampleScale) {
        List(HomeLiveBlurStepCount + 1) { index ->
            if (index == 0) null else {
                val radius = maximumBlurPx * sampleScale * index / HomeLiveBlurStepCount.toFloat()
                BlurEffect(radius, radius, TileMode.Clamp)
            }
        }
    }
    // The live path is kept for idle Home and per-frame personalization preview. Reuse a small
    // bounded set of RenderEffect instances so those exceptional paths no longer compile a new
    // full-screen blur effect for every animation frame.
    val liveBlurEffects = remember(maximumBlurPx) {
        List(HomeLiveBlurStepCount + 1) { index ->
            if (index == 0) null else {
                val radius = maximumBlurPx * index / HomeLiveBlurStepCount.toFloat()
                BlurEffect(radius, radius, TileMode.Clamp)
            }
        }
    }
    Box(
        modifier = modifier
            .graphicsLayer {
                val frozenHomeScene = useFrozenHomeScene()
                val progress = blurProgress().coerceIn(0f, 1f)
                val isClosing = closing()
                val step = quantizeHomeBackgroundBlurStep(progress, isClosing)
                if (frozenHomeScene) {
                    renderEffect = null
                    liveSceneLayer.renderEffect = null
                    val blurAlpha = homeFrozenBlurAlpha(progress)
                    clearSceneLayer.alpha = if (blurAlpha < 1f) 1f else 0f
                    frozenBlurLayer.alpha = blurAlpha
                    frozenBlurLayer.renderEffect =
                        frozenBlurEffects[step.coerceAtLeast(HomeFrozenBlurMinimumStep)]
                } else {
                    // Keep native Views under a neutral parent while personalization is live.
                    // Blur a full-size recorded layer, as the frozen path already does, instead
                    // of repeatedly changing the ancestor effect used by COUI's own sampling.
                    renderEffect = null
                    liveSceneLayer.renderEffect = liveBlurEffects[step]
                    clearSceneLayer.alpha = 0f
                    frozenBlurLayer.alpha = 0f
                    frozenBlurLayer.renderEffect = null
                }
            }
            .drawWithContent {
                // Animation state is read only by graphicsLayer above. A zoom/blur/alpha tick
                // changes RenderNode properties without invalidating this display list.
                val frozenHomeScene = useFrozenHomeScene()
                if (!frozenHomeScene) {
                    frozenRecordKey.set(null)
                    if (recordLiveScene) {
                        liveSceneLayer.record { this@drawWithContent.drawContent() }
                        drawLayer(liveSceneLayer)
                    } else {
                        drawContent()
                    }
                    return@drawWithContent
                }

                val fullSize = IntSize(size.width.roundToInt(), size.height.roundToInt())
                val reducedSize = IntSize(
                    width = (size.width * sampleScale).roundToInt().coerceAtLeast(1),
                    height = (size.height * sampleScale).roundToInt().coerceAtLeast(1)
                )
                // Both wrappers refer to the same retained scene. Record in Preparing, including
                // at zero blur, rather than doing this work on the first moving frame. The blur
                // has one fixed allocation from Opening through Closing; no bitmap readback or
                // blur pyramid is created. Rounded sample dimensions map back to exact source
                // dimensions so the sharp/blurred layers stay aligned on odd-sized windows.
                if (
                    frozenRecordKey.get() != sceneKey ||
                    frozenRecordSize.get() != fullSize
                ) {
                    val scaleX = reducedSize.width / size.width
                    val scaleY = reducedSize.height / size.height
                    clearSceneLayer.record(size = fullSize) {
                        this@drawWithContent.drawContent()
                    }
                    frozenBlurLayer.record(size = reducedSize) {
                        withTransform({
                            scale(
                                scaleX = scaleX,
                                scaleY = scaleY,
                                pivot = Offset.Zero
                            )
                        }) {
                            this@drawWithContent.drawContent()
                        }
                    }
                    frozenBlurLayer.pivotOffset = Offset.Zero
                    frozenBlurLayer.scaleX = 1f / scaleX
                    frozenBlurLayer.scaleY = 1f / scaleY
                    frozenRecordKey.set(sceneKey)
                    frozenRecordSize.set(fullSize)
                }

                drawLayer(clearSceneLayer)
                drawLayer(frozenBlurLayer)
            },
        content = content
    )
}
