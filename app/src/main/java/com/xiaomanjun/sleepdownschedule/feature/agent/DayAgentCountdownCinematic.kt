package com.xiaomanjun.sleepdownschedule.feature.agent

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xiaomanjun.sleepdownschedule.AppState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin

internal enum class DayAgentCinematicPhase { IDLE, COUNTDOWN, EXPLOSION }
internal enum class DayAgentCinematicEvent { NONE, START, TICK, EXPLODE }

/** Only the observed 4 → 3 boundary starts the full-screen sequence. */
internal fun dayAgentCinematicEvent(
    previousKey: String?, previousSeconds: Long?, activeKey: String?,
    currentKey: String?, currentSeconds: Long?
): DayAgentCinematicEvent = when {
    activeKey != null && (currentKey != activeKey || currentSeconds == null || currentSeconds <= 0L) ->
        DayAgentCinematicEvent.EXPLODE
    activeKey != null && currentSeconds != null && currentSeconds in 1L..3L -> DayAgentCinematicEvent.TICK
    activeKey == null && previousKey != null && previousKey == currentKey &&
        previousSeconds == 4L && currentSeconds == 3L -> DayAgentCinematicEvent.START
    else -> DayAgentCinematicEvent.NONE
}

internal class DayAgentCountdownCinematicState {
    var numberBoundsInWindow: Rect? = null
    var phase by mutableStateOf(DayAgentCinematicPhase.IDLE)
        private set
    var episodeKey by mutableStateOf<String?>(null)
        private set
    var seconds by mutableIntStateOf(3)
        private set
    var launchBounds by mutableStateOf<Rect?>(null)
        private set
    var accent by mutableStateOf(Color.White)
        private set

    fun start(key: String, fromDayCard: Boolean, color: Color) {
        episodeKey = key
        seconds = 3
        launchBounds = numberBoundsInWindow.takeIf { fromDayCard }
        accent = color
        phase = DayAgentCinematicPhase.COUNTDOWN
    }

    fun tick(value: Long) {
        if (phase == DayAgentCinematicPhase.COUNTDOWN) seconds = value.toInt()
    }

    fun explode() {
        if (phase == DayAgentCinematicPhase.COUNTDOWN) {
            seconds = 0
            phase = DayAgentCinematicPhase.EXPLOSION
        }
    }

    fun clear() {
        phase = DayAgentCinematicPhase.IDLE
        episodeKey = null
        launchBounds = null
    }
}

internal val LocalDayAgentCountdownCinematic =
    staticCompositionLocalOf<DayAgentCountdownCinematicState?> { null }

@Composable
internal fun HomeCountdownCinematicOverlay(
    effect: DayAgentCountdownCinematicState,
    state: AppState,
    available: Boolean,
    fromDayCard: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    val active = available && lifecycle.isAtLeast(Lifecycle.State.RESUMED)
    var now by remember(state.config.id) { mutableStateOf(LocalDateTime.now()) }
    val date = now.toLocalDate()
    val facts = remember(state.courses, state.periods, state.config, state.schedules, date) {
        DayAgentRenderCache.facts(
            state, date, null,
            state.schedules.firstOrNull { it.id == state.config.id }?.name,
            context
        )
    }
    LaunchedEffect(active, facts.today, facts.periodDefinitions) {
        if (!active) return@LaunchedEffect
        while (true) {
            val current = LocalDateTime.now()
            now = current
            val remaining = agentLessonFocus(facts.today, facts.periodDefinitions, current)
                ?.secondsRemainingAt(current)
            val interval = when {
                remaining != null && remaining <= 5L -> 80L
                remaining != null && remaining <= 600L -> 1_000L
                else -> 60_000L
            }
            delay(if (interval == 80L) interval else
                (interval - System.currentTimeMillis() % interval).coerceAtLeast(16L))
        }
    }
    val focus = remember(facts.today, facts.periodDefinitions, now) {
        agentLessonFocus(facts.today, facts.periodDefinitions, now)
    }
    val focusKey = focus?.transitionKey
    val remaining = focus?.secondsRemainingAt(now)
    var previousKey by remember(state.config.id, date) { mutableStateOf<String?>(null) }
    var previousSeconds by remember(state.config.id, date) { mutableStateOf<Long?>(null) }
    LaunchedEffect(active, focusKey, remaining, fromDayCard) {
        if (!active) {
            effect.clear()
            previousKey = null
            previousSeconds = null
            return@LaunchedEffect
        }
        if (effect.phase != DayAgentCinematicPhase.EXPLOSION) {
            when (dayAgentCinematicEvent(
                previousKey, previousSeconds, effect.episodeKey, focusKey, remaining
            )) {
                DayAgentCinematicEvent.START -> if (focusKey != null) effect.start(
                    focusKey, fromDayCard,
                    if (focus.phase == AgentLessonPhase.IN_CLASS) Color(0xFFFF6478) else Color(0xFFFFB461)
                )
                DayAgentCinematicEvent.TICK -> if (remaining != null) effect.tick(remaining)
                DayAgentCinematicEvent.EXPLODE -> effect.explode()
                DayAgentCinematicEvent.NONE -> Unit
            }
        }
        previousKey = focusKey
        previousSeconds = remaining
    }

    val phase = effect.phase
    if (phase == DayAgentCinematicPhase.IDLE) return
    val reducedMotion = remember { !ValueAnimator.areAnimatorsEnabled() }
    val entry = remember { Animatable(0f) }
    val blast = remember { Animatable(0f) }
    val vibrator = remember(context) { context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator }
    LaunchedEffect(phase, effect.episodeKey) {
        when (phase) {
            DayAgentCinematicPhase.COUNTDOWN -> {
                blast.snapTo(0f)
                entry.snapTo(0f)
                if (reducedMotion) entry.snapTo(1f)
                else entry.animateTo(1f, tween(360, easing = CubicBezierEasing(0.16f, 0.88f, 0.24f, 1f)))
            }
            DayAgentCinematicPhase.EXPLOSION -> {
                entry.snapTo(1f)
                blast.snapTo(0f)
                vibrateCinematicBlast(vibrator)
                delay(if (reducedMotion) 40L else 85L)
                blast.animateTo(1f, tween(if (reducedMotion) 260 else 1_450, easing = LinearEasing))
                effect.clear()
            }
            DayAgentCinematicPhase.IDLE -> Unit
        }
    }
    LaunchedEffect(phase, effect.seconds) {
        if (phase == DayAgentCinematicPhase.COUNTDOWN) vibrateCinematicBeat(vibrator, effect.seconds)
    }

    val density = LocalDensity.current
    var overlayWindowPosition by remember { mutableStateOf(Offset.Zero) }
    BoxWithConstraints(
        modifier.fillMaxSize()
            .onGloballyPositioned { overlayWindowPosition = it.positionInWindow() }
            .clearAndSetSemantics {
                contentDescription = if (phase == DayAgentCinematicPhase.EXPLOSION) "倒计时结束"
                    else "倒计时 ${effect.seconds} 秒"
            }
    ) {
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }
        val screenCenter = Offset(widthPx / 2f, heightPx / 2f)
        val fallbackOrigin = Offset(
            widthPx / 2f,
            WindowInsets.statusBars.getTop(density) + with(density) { 32.dp.toPx() }
        )
        val source = effect.launchBounds?.takeIf { bounds ->
            bounds.width > 0f && bounds.height > 0f &&
                (bounds.center.x - overlayWindowPosition.x) in 0f..widthPx &&
                (bounds.center.y - overlayWindowPosition.y) in 0f..heightPx
        }
        val origin = source?.center?.minus(overlayWindowPosition) ?: fallbackOrigin
        val fontSize = (minOf(maxWidth.value * 0.48f, maxHeight.value * 0.30f, 184f) /
            density.fontScale).sp
        val fontPx = with(density) { fontSize.toPx() }
        val startScale = source?.let { (it.height / (fontPx * 1.15f)).coerceIn(0.10f, 0.40f) } ?: 0.14f
        val blastProgress = blast.value
        val dim = if (phase == DayAgentCinematicPhase.COUNTDOWN) {
            0.48f + (3 - effect.seconds).coerceIn(0, 2) * 0.11f
        } else 0.74f * (1f - cinematicSmoothStep(0.38f, 1f, blastProgress))
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = dim)))

        if (phase == DayAgentCinematicPhase.COUNTDOWN) {
            var shakeX = 0f
            var shakeY = 0f
            if (!reducedMotion) {
                val transition = rememberInfiniteTransition(label = "cinematic-countdown-shake")
                val horizontal by transition.animateFloat(
                    initialValue = -1f, targetValue = 1f,
                    animationSpec = infiniteRepeatable(tween(43, easing = LinearEasing), RepeatMode.Reverse),
                    label = "cinematic-countdown-shake-x"
                )
                val vertical by transition.animateFloat(
                    initialValue = 1f, targetValue = -1f,
                    animationSpec = infiniteRepeatable(tween(67, easing = LinearEasing), RepeatMode.Reverse),
                    label = "cinematic-countdown-shake-y"
                )
                shakeX = horizontal
                shakeY = vertical
            }
            val intensity = (3 - effect.seconds).coerceIn(0, 2)
            val style = MaterialTheme.typography.displayLarge.copy(
                fontSize = fontSize, fontFeatureSettings = "tnum",
                shadow = Shadow(effect.accent.copy(alpha = 0.88f), Offset.Zero, 28f + intensity * 14f)
            )
            Box(
                modifier = Modifier.align(Alignment.Center).graphicsLayer {
                    val p = entry.value.coerceIn(0f, 1f)
                    val scale = startScale + (1f - startScale) * p
                    scaleX = scale * (1f + intensity * 0.035f)
                    scaleY = scaleX
                    translationX = (origin.x - screenCenter.x) * (1f - p) +
                        shakeX * with(density) { (3 + intensity * 5).dp.toPx() }
                    translationY = (origin.y - screenCenter.y) * (1f - p) +
                        shakeY * with(density) { (2 + intensity * 3).dp.toPx() }
                    rotationZ = (shakeX - shakeY) * (0.35f + intensity * 0.60f)
                },
                contentAlignment = Alignment.Center
            ) {
                CinematicCountdownDigit(
                    effect.seconds, style,
                    lerp(effect.accent, Color.White, entry.value.coerceIn(0f, 1f))
                )
            }
        } else {
            val style = MaterialTheme.typography.displayLarge.copy(
                fontSize = fontSize, fontFeatureSettings = "tnum",
                shadow = Shadow(effect.accent, Offset.Zero, 46f)
            )
            Text("0", color = Color.White, style = style, fontWeight = FontWeight.Black,
                modifier = Modifier.align(Alignment.Center).graphicsLayer {
                    alpha = 1f - cinematicSmoothStep(0.01f, 0.16f, blast.value)
                })
            if (!reducedMotion) {
                CinematicBlastCanvas(
                    progress = { blast.value },
                    accent = effect.accent,
                    fontPx = fontPx,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        if (phase == DayAgentCinematicPhase.EXPLOSION) {
            Box(Modifier.fillMaxSize().background(Color.White.copy(
                alpha = 0.56f * (1f - cinematicSmoothStep(0f, 0.10f, blastProgress))
            )))
        }
    }
}

@Composable
private fun CinematicCountdownDigit(seconds: Int, style: androidx.compose.ui.text.TextStyle, color: Color) {
    val motion = remember { Animatable(1f) }
    var displayed by remember { mutableIntStateOf(seconds) }
    var previous by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(seconds) {
        if (displayed == seconds) return@LaunchedEffect
        previous = displayed
        displayed = seconds
        motion.snapTo(0f)
        motion.animateTo(1f, tween(260, easing = CubicBezierEasing(0.20f, 0.72f, 0.26f, 1f)))
        previous = null
    }
    Box(contentAlignment = Alignment.Center) {
        previous?.let { old ->
            Text(old.toString(), color = color, style = style, fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center, modifier = Modifier.graphicsLayer {
                    val p = motion.value.coerceIn(0f, 1f)
                    alpha = 1f - p
                    translationY = -36.dp.toPx() * p
                })
        }
        Text(displayed.toString(), color = color, style = style, fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center, modifier = Modifier.graphicsLayer {
                val p = motion.value.coerceIn(0f, 1f)
                alpha = p
                translationY = 36.dp.toPx() * (1f - p)
            })
    }
}

@Composable
private fun CinematicBlastCanvas(progress: () -> Float, accent: Color, fontPx: Float, modifier: Modifier) {
    val paint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
            textAlign = Paint.Align.LEFT
        }
    }
    Canvas(modifier) {
        val p = progress().coerceIn(0f, 1f)
        val center = Offset(size.width / 2f, size.height / 2f)
        val maxRadius = hypot(center.x, center.y) * 1.08f
        val wave = 1f - (1f - p) * (1f - p)
        val light = 1f - cinematicSmoothStep(0.02f, 0.36f, p)
        // The light stays at the impact point while only the number fragments fly outward.
        drawCircle(accent, radius = 118.dp.toPx() * (0.28f + p), center = center,
            alpha = 0.23f * light)
        drawCircle(Color.White, radius = 32.dp.toPx() * (0.35f + p), center = center,
            alpha = 0.90f * light)
        drawCircle(Color.White, radius = maxRadius * wave, center = center,
            alpha = 0.90f * (1f - p), style = Stroke(width = (11f - 8f * p).dp.toPx()))
        drawCircle(accent, radius = maxRadius * (wave * 0.82f).coerceAtLeast(0f), center = center,
            alpha = 0.62f * (1f - p), style = Stroke(width = 5.dp.toPx()))
        drawCircle(Color.White, radius = maxRadius * (wave * 0.58f).coerceAtLeast(0f), center = center,
            alpha = 0.36f * (1f - p), style = Stroke(width = 2.5.dp.toPx()))
        repeat(72) { index ->
            val angle = index * (Math.PI * 2.0 / 72.0) + (index % 5) * 0.09
            val unit = Offset(cos(angle).toFloat(), sin(angle).toFloat())
            val travel = maxRadius * wave * (0.72f + (index % 7) * 0.055f)
            val tip = center + unit * travel
            drawLine(
                color = if (index % 4 == 0) Color.White else accent,
                start = tip - unit * (12.dp.toPx() + (index % 4) * 9.dp.toPx()),
                end = tip,
                strokeWidth = (1.4f + index % 3).dp.toPx(),
                alpha = (1f - p) * 0.9f
            )
        }
        val electricReach = maxRadius * (1f - (1f - p.coerceIn(0f, 0.65f) / 0.65f).let { it * it })
        val electricAlpha = 0.95f * (1f - cinematicSmoothStep(0.30f, 0.78f, p))
        repeat(14) { bolt ->
            val angle = bolt * (Math.PI * 2.0 / 14.0) + (bolt % 3) * 0.10
            val direction = Offset(cos(angle).toFloat(), sin(angle).toFloat())
            val side = Offset(-direction.y, direction.x)
            var previousPoint = center + direction * 21.dp.toPx()
            repeat(7) { segment ->
                val radial = electricReach * (segment + 1) / 7f
                val kink = (((bolt * 19 + segment * 7) % 9) - 4) * 5.dp.toPx() * (0.5f + p)
                val next = center + direction * radial + side * kink
                drawLine(accent, previousPoint, next, strokeWidth = 7.dp.toPx(), alpha = electricAlpha * 0.48f)
                drawLine(Color.White, previousPoint, next, strokeWidth = 2.2.dp.toPx(), alpha = electricAlpha)
                if (segment == 3 || segment == 5) {
                    val branch = next + direction * (electricReach * 0.12f) +
                        side * (if ((bolt + segment) % 2 == 0) 1f else -1f) * 32.dp.toPx()
                    drawLine(accent, next, branch, strokeWidth = 4.dp.toPx(), alpha = electricAlpha * 0.44f)
                    drawLine(Color.White, next, branch, strokeWidth = 1.4.dp.toPx(), alpha = electricAlpha * 0.82f)
                }
                previousPoint = next
            }
        }
        paint.textSize = fontPx
        paint.color = Color.White.toArgb()
        paint.alpha = (255f * (1f - cinematicSmoothStep(0.50f, 1f, p))).toInt().coerceIn(0, 255)
        val glyphWidth = paint.measureText("0")
        val glyphHeight = fontPx * 1.30f
        val left = center.x - glyphWidth / 2f
        val top = center.y - glyphHeight / 2f
        val baseline = center.y - (paint.ascent() + paint.descent()) / 2f
        val native = drawContext.canvas.nativeCanvas
        repeat(4) { row ->
            repeat(3) { column ->
                val dx = column - 1f + (row % 2) * 0.22f
                val dy = row - 1.5f + (column % 2) * 0.16f
                val magnitude = max(0.35f, hypot(dx, dy))
                val travel = maxRadius * 0.64f * wave * (0.60f + (row * 3 + column) % 4 * 0.13f)
                val shardLeft = left + column * glyphWidth / 3f
                val shardTop = top + row * glyphHeight / 4f
                native.save()
                native.translate(dx / magnitude * travel, dy / magnitude * travel)
                native.clipRect(shardLeft, shardTop, shardLeft + glyphWidth / 3f, shardTop + glyphHeight / 4f)
                native.drawText("0", left, baseline, paint)
                native.restore()
            }
        }
    }
}

private fun cinematicSmoothStep(start: Float, end: Float, value: Float): Float {
    val t = ((value - start) / (end - start)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

private fun vibrateCinematicBeat(vibrator: Vibrator?, seconds: Int) {
    if (vibrator?.hasVibrator() != true) return
    val amplitude = when (seconds) { 3 -> 150; 2 -> 205; else -> 255 }
    val duration = when (seconds) { 3 -> 75L; 2 -> 115L; else -> 160L }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        vibrator.vibrate(VibrationEffect.createOneShot(duration, amplitude))
    } else {
        @Suppress("DEPRECATION")
        vibrator.vibrate(duration)
    }
}

private fun vibrateCinematicBlast(vibrator: Vibrator?) {
    if (vibrator?.hasVibrator() != true) return
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        vibrator.vibrate(VibrationEffect.createWaveform(
            longArrayOf(0, 95, 35, 170, 45, 250),
            intArrayOf(0, 255, 0, 225, 0, 255),
            -1
        ))
    } else {
        @Suppress("DEPRECATION")
        vibrator.vibrate(595L)
    }
}
