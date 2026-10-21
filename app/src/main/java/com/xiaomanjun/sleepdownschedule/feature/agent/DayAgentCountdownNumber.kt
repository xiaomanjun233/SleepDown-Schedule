package com.xiaomanjun.sleepdownschedule.feature.agent

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateTo
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
internal fun AgentCountdownNumber(text: String, seconds: Long, color: Color, transitionKey: String?) {
    val cinematic = LocalDayAgentCountdownCinematic.current
    val shake = if (seconds in 1L..60L) {
        val transition = rememberInfiniteTransition(label = "agent-countdown-shake")
        val offset by transition.animateFloat(
            initialValue = -1f, targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(55, easing = LinearEasing), RepeatMode.Reverse),
            label = "agent-countdown-shake-x"
        )
        offset
    } else 0f
    Row(Modifier.clearAndSetSemantics { contentDescription = text }
        .onGloballyPositioned { coordinates -> cinematic?.numberBoundsInWindow = coordinates.boundsInWindow() }
        .graphicsLayer {
        alpha = if (cinematic != null && cinematic.phase != DayAgentCinematicPhase.IDLE &&
            cinematic.episodeKey == transitionKey) 0f else 1f
        val force = if (seconds <= 10L) 3.2.dp.toPx() else 1.5.dp.toPx()
        translationX = shake * force
        translationY = shake * force * 0.35f
        scaleX = if (seconds in 1L..10L) 1.03f else 1f
        scaleY = scaleX
    }, verticalAlignment = Alignment.CenterVertically) {
        text.forEach { character ->
            if (character.isDigit()) {
                AgentCountdownDigit(character, color)
            } else {
                Text(character.toString(), color = color,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold, maxLines = 1)
            }
        }
    }
}

@Composable
private fun AgentCountdownDigit(digit: Char, color: Color) {
    val motion = remember { Animatable(1f) }
    var displayed by remember { mutableStateOf(digit) }
    var previous by remember { mutableStateOf<Char?>(null) }
    LaunchedEffect(digit) {
        if (digit == displayed) return@LaunchedEffect
        previous = displayed
        displayed = digit
        motion.snapTo(0f)
        motion.animateTo(1f, tween(300, easing = CubicBezierEasing(0.20f, 0.72f, 0.26f, 1f)))
        previous = null
    }
    val style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum")
    Box(contentAlignment = Alignment.Center) {
        previous?.let { outgoing ->
            Text(outgoing.toString(),
                modifier = Modifier.graphicsLayer {
                    val p = motion.value.coerceIn(0f, 1f)
                    alpha = 1f - p
                    translationY = -8.dp.toPx() * p
                    val blur = 5.dp.toPx() * p
                    renderEffect = if (blur > 0.01f) BlurEffect(blur, blur, TileMode.Clamp) else null
                }, color = color, style = style,
                fontWeight = FontWeight.Bold, maxLines = 1)
        }
        Text(displayed.toString(),
            modifier = Modifier.graphicsLayer {
                val p = motion.value.coerceIn(0f, 1f)
                alpha = p
                translationY = 8.dp.toPx() * (1f - p)
                val blur = 5.dp.toPx() * (1f - p)
                renderEffect = if (blur > 0.01f) BlurEffect(blur, blur, TileMode.Clamp) else null
            }, color = color, style = style,
            fontWeight = FontWeight.Bold, maxLines = 1)
    }
}
