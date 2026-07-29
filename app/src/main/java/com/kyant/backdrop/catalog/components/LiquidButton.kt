// Based on Kyant0/AndroidLiquidGlass catalog components, Apache-2.0.
// Modified for SleepDown-Schedule.
package com.kyant.backdrop.catalog.components

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceAtMost
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.catalog.utils.InteractiveHighlight
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tanh

@Composable
fun LiquidButton(
    onClick: () -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    isInteractive: Boolean = true,
    tint: Color = Color.Unspecified,
    surfaceColor: Color = Color.Unspecified,
    height: Dp = 48f.dp,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16f.dp),
    blurRadius: Dp = 2f.dp,
    lensHeight: Dp = 12f.dp,
    lensAmount: Dp = 24f.dp,
    chromaticAberration: Boolean = false,
    shadowEnabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    val animationScope = rememberCoroutineScope()

    val interactiveHighlight = remember(animationScope) {
        InteractiveHighlight(
            animationScope = animationScope
        )
    }

    Row(
        modifier
            .drawBackdrop(
                backdrop = backdrop,
                shape = { Capsule() },
                effects = {
                    vibrancy()
                    blur(blurRadius.toPx())
                    lens(lensHeight.toPx(), lensAmount.toPx(), chromaticAberration = chromaticAberration)
                },
                shadow = if (shadowEnabled) ({ Shadow.Default }) else null,
                layerBlock = if (isInteractive) {
                    {
                        val width = size.width
                        val height = size.height

                        val progress = interactiveHighlight.pressProgress
                        val scale = lerp(1f, 1f + 4f.dp.toPx() / size.height, progress)

                        val maxOffset = size.minDimension
                        val initialDerivative = 0.05f
                        val offset = interactiveHighlight.offset
                        translationX = maxOffset * tanh(initialDerivative * offset.x / maxOffset)
                        translationY = maxOffset * tanh(initialDerivative * offset.y / maxOffset)

                        val maxDragScale = 4f.dp.toPx() / size.height
                        val offsetAngle = atan2(offset.y, offset.x)
                        scaleX =
                            scale +
                                    maxDragScale * abs(cos(offsetAngle) * offset.x / size.maxDimension) *
                                    (width / height).fastCoerceAtMost(1f)
                        scaleY =
                            scale +
                                    maxDragScale * abs(sin(offsetAngle) * offset.y / size.maxDimension) *
                                    (height / width).fastCoerceAtMost(1f)
                    }
                } else {
                    null
                },
                onDrawSurface = {
                    if (tint.isSpecified) {
                        drawRect(tint.copy(alpha = 0.18f), blendMode = BlendMode.Hue)
                        drawRect(tint.copy(alpha = 0.22f))
                    }
                    if (surfaceColor.isSpecified) {
                        drawRect(surfaceColor)
                    }
                }
            )
            .clickable(
                interactionSource = null,
                indication = if (isInteractive) null else LocalIndication.current,
                role = Role.Button,
                onClick = onClick
            )
            .then(
                if (isInteractive) {
                    Modifier
                        .then(interactiveHighlight.modifier)
                        .then(interactiveHighlight.gestureModifier)
                } else {
                    Modifier
                }
            )
            .height(height)
            .padding(contentPadding),
        horizontalArrangement = Arrangement.spacedBy(8f.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

@Composable
fun Modifier.liquidButtonInteraction(
    onClick: () -> Unit,
    isInteractive: Boolean = true,
    showHighlight: Boolean = true
): Modifier {
    if (!isInteractive) {
        return clickable(role = Role.Button, onClick = onClick)
    }
    val animationScope = rememberCoroutineScope()
    val interactiveHighlight = remember(animationScope) {
        InteractiveHighlight(animationScope = animationScope)
    }
    return graphicsLayer {
        val progress = interactiveHighlight.pressProgress
        val height = size.height.coerceAtLeast(1f)
        val width = size.width.coerceAtLeast(1f)
        val scale = lerp(1f, 1f + 4f.dp.toPx() / height, progress)
        val maxOffset = size.minDimension.coerceAtLeast(1f)
        val offset = interactiveHighlight.offset
        translationX = maxOffset * tanh(0.05f * offset.x / maxOffset)
        translationY = maxOffset * tanh(0.05f * offset.y / maxOffset)
        val maxDragScale = 4f.dp.toPx() / height
        val offsetAngle = atan2(offset.y, offset.x)
        scaleX = scale + maxDragScale * abs(cos(offsetAngle) * offset.x / size.maxDimension.coerceAtLeast(1f)) *
            (width / height).fastCoerceAtMost(1f)
        scaleY = scale + maxDragScale * abs(sin(offsetAngle) * offset.y / size.maxDimension.coerceAtLeast(1f)) *
            (height / width).fastCoerceAtMost(1f)
    }
        .clickable(interactionSource = null, indication = null, role = Role.Button, onClick = onClick)
        .then(if (showHighlight) interactiveHighlight.modifier else Modifier)
        .then(interactiveHighlight.gestureModifier)
}
