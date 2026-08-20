// Based on Kyant0/AndroidLiquidGlass catalog components, Apache-2.0.
// Modified for SleepDown-Schedule.
package com.kyant.backdrop.catalog.utils

import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.util.fastCoerceIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class InteractiveHighlight(
    val animationScope: CoroutineScope,
    val position: (size: Size, offset: Offset) -> Offset = { _, offset -> offset },
    val radius: (size: Size) -> Float = { size -> size.minDimension * 1.5f },
    val acceptsGesture: (size: Size, offset: Offset) -> Boolean = { _, _ -> true },
    val ambientAlpha: Float = 0.08f,
    val spotAlpha: Float = 0.15f,
    val fallbackAlpha: Float = 0.25f
) {

    private val pressProgressAnimationSpec =
        spring(0.5f, 300f, 0.001f)
    private val positionAnimationSpec =
        spring(0.5f, 300f, Offset.VisibilityThreshold)

    private val pressProgressAnimation =
        Animatable(0f, 0.001f)
    private val positionAnimation =
        Animatable(Offset.Zero, Offset.VectorConverter, Offset.VisibilityThreshold)

    private var startPosition = Offset.Zero
    private var inputGeneration = 0L
    private var externalPressActive = false
    private var exactExternalPosition by mutableStateOf<Offset?>(null)
    val pressProgress: Float get() = pressProgressAnimation.value
    val offset: Offset get() = positionAnimation.value - startPosition

    private val shader =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            RuntimeShader(
                """
uniform float2 size;
layout(color) uniform half4 color;
uniform float radius;
uniform float2 position;

half4 main(float2 coord) {
    float dist = distance(coord, position);
    float intensity = smoothstep(radius, radius * 0.5, dist);
    return color * intensity;
}"""
            )
        } else {
            null
        }

    val modifier: Modifier =
        Modifier.drawWithContent {
            val progress = pressProgressAnimation.value
            val highlightPosition = exactExternalPosition ?: positionAnimation.value
            if (progress > 0f) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && shader != null) {
                    if (ambientAlpha > 0f) {
                        drawRect(
                            Color.White.copy(ambientAlpha * progress),
                            blendMode = BlendMode.Plus
                        )
                    }
                    shader.apply {
                        val position = position(size, highlightPosition)
                        setFloatUniform("size", size.width, size.height)
                        setColorUniform("color", Color.White.copy(spotAlpha * progress).toArgb())
                        setFloatUniform("radius", radius(size))
                        setFloatUniform(
                            "position",
                            position.x.fastCoerceIn(0f, size.width),
                            position.y.fastCoerceIn(0f, size.height)
                        )
                    }
                    drawRect(
                        ShaderBrush(shader),
                        blendMode = BlendMode.Plus
                    )
                } else {
                    if (ambientAlpha > 0f) {
                        drawRect(
                            Color.White.copy(ambientAlpha * progress),
                            blendMode = BlendMode.Plus
                        )
                    }
                    val resolvedPosition = position(size, highlightPosition)
                    val resolvedRadius = radius(size)
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color.White.copy(fallbackAlpha * progress),
                                Color.Transparent
                            ),
                            center = resolvedPosition,
                            radius = resolvedRadius
                        ),
                        radius = resolvedRadius,
                        center = resolvedPosition,
                        blendMode = BlendMode.Plus
                    )
                }
            }

            drawContent()
        }

    private fun settle(generation: Long = inputGeneration) {
        animationScope.launch {
            // Release work is intentionally asynchronous so the spring can finish after UP. A
            // newer DOWN must invalidate this queued release before it can cancel the new press.
            if (generation != inputGeneration) return@launch
            launch { pressProgressAnimation.animateTo(0f, pressProgressAnimationSpec) }
            launch { positionAnimation.animateTo(startPosition, positionAnimationSpec) }
        }
    }

    /**
     * Drives the same Kyant catalog highlight from a gesture owned by another component. This is
     * useful for lifted/shared overlays: the pointer detector stays on the source card while the
     * light is drawn by a full-screen overlay, so the existing gesture does not need a competing
     * pointerInput modifier.
     */
    fun updateExternal(position: Offset, pressed: Boolean, followPointerExactly: Boolean = false) {
        if (pressed) {
            exactExternalPosition = position.takeIf { followPointerExactly }
            val newPress = !externalPressActive
            val generation = if (newPress) {
                externalPressActive = true
                ++inputGeneration
            } else {
                inputGeneration
            }
            if (newPress) startPosition = position
            animationScope.launch {
                if (generation != inputGeneration) return@launch
                launch { pressProgressAnimation.animateTo(1f, pressProgressAnimationSpec) }
                launch {
                    if (newPress) positionAnimation.snapTo(position)
                    else positionAnimation.animateTo(position, positionAnimationSpec)
                }
            }
        } else if (externalPressActive) {
            externalPressActive = false
            exactExternalPosition = null
            val generation = ++inputGeneration
            settle(generation)
        } else {
            exactExternalPosition = null
        }
    }

    val gestureModifier: Modifier =
        Modifier.pointerInput(animationScope) {
            var gestureAccepted = false
            var gestureGeneration = inputGeneration
            try {
                inspectDragGestures(
                    onDragStart = { down ->
                        gestureGeneration = ++inputGeneration
                        gestureAccepted = acceptsGesture(
                            Size(size.width.toFloat(), size.height.toFloat()),
                            down.position
                        )
                        if (!gestureAccepted) {
                            settle(gestureGeneration)
                            return@inspectDragGestures
                        }
                        startPosition = down.position
                        animationScope.launch {
                            if (gestureGeneration != inputGeneration) return@launch
                            launch { pressProgressAnimation.animateTo(1f, pressProgressAnimationSpec) }
                            launch { positionAnimation.snapTo(startPosition) }
                        }
                    },
                    onDragEnd = {
                        if (gestureAccepted) settle(gestureGeneration)
                        gestureAccepted = false
                    },
                    onDragCancel = {
                        if (gestureAccepted) settle(gestureGeneration)
                        gestureAccepted = false
                    }
                ) { change, _ ->
                    if (gestureAccepted) {
                        val updateGeneration = gestureGeneration
                        animationScope.launch {
                            if (updateGeneration == inputGeneration) {
                                positionAnimation.snapTo(change.position)
                            }
                        }
                    }
                }
            } finally {
                // Window focus changes can cancel pointer input without delivering an up/cancel.
                // Always settle so a resumed LiquidButton cannot keep a stale translation/scale.
                val cancellationGeneration = ++inputGeneration
                settle(cancellationGeneration)
            }
        }
}
