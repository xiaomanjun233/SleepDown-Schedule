package com.xiaomanjun.sleepdownschedule.feature.agent

import android.animation.ValueAnimator
import android.content.Context
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
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
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
    val blast = Animatable(0f)
    var numberBoundsInWindow: Rect? = null
    var screenBoundsInWindow: Rect? = null
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
        screenBoundsInWindow = null
    }
}

internal val LocalDayAgentCountdownCinematic =
    staticCompositionLocalOf<DayAgentCountdownCinematicState?> { null }

/** A single soft impact travels through the real home elements, rather than overpainting them. */
@Composable
internal fun Modifier.homeCountdownShockwave(strength: Float = 1f): Modifier {
    val effect = LocalDayAgentCountdownCinematic.current ?: return this
    if (effect.phase != DayAgentCinematicPhase.EXPLOSION || !ValueAnimator.areAnimatorsEnabled()) return this
    val restingBounds = remember { arrayOfNulls<Rect>(1) }
    return this.onGloballyPositioned { coordinates ->
        if (restingBounds[0] == null) restingBounds[0] = coordinates.boundsInWindow()
    }.graphicsLayer {
        val viewport = effect.screenBoundsInWindow
        val bounds = restingBounds[0]
        val p = effect.blast.value.coerceIn(0f, 1f)
        if (viewport == null || bounds == null || viewport.width <= 0f || viewport.height <= 0f) {
            translationX = 0f
            translationY = 0f
            scaleX = 1f
            scaleY = 1f
        } else {
            val center = viewport.center
            val dx = bounds.center.x - center.x
            val dy = bounds.center.y - center.y
            val distance = hypot(dx, dy)
            val reach = hypot(
                max(center.x - viewport.left, viewport.right - center.x),
                max(center.y - viewport.top, viewport.bottom - center.y)
            ).coerceAtLeast(1f)
            val arrival = (distance / reach).coerceIn(0f, 1f) * 0.60f
            val elapsed = ((p - arrival) / 0.30f).coerceIn(0f, 1f)
            val impulse = sin(Math.PI * elapsed).toFloat().coerceAtLeast(0f) *
                (1f - 0.25f * distance / reach) * strength
            val displacement = 16.dp.toPx() * impulse
            translationX = if (distance > 1f) dx / distance * displacement else 0f
            translationY = if (distance > 1f) dy / distance * displacement else 0f
            scaleX = 1f + 0.025f * impulse
            scaleY = scaleX
        }
    }
}

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
    val vibrator = remember(context) { context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator }
    LaunchedEffect(phase, effect.episodeKey) {
        when (phase) {
            DayAgentCinematicPhase.COUNTDOWN -> {
                effect.blast.snapTo(0f)
                entry.snapTo(0f)
                if (reducedMotion) entry.snapTo(1f)
                else entry.animateTo(1f, tween(360, easing = CubicBezierEasing(0.16f, 0.88f, 0.24f, 1f)))
            }
            DayAgentCinematicPhase.EXPLOSION -> {
                entry.snapTo(1f)
                effect.blast.snapTo(0f)
                vibrateCinematicBlast(vibrator)
                delay(if (reducedMotion) 24L else 40L)
                effect.blast.animateTo(1f, tween(if (reducedMotion) 260 else 1_450, easing = LinearEasing))
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
            .onGloballyPositioned {
                overlayWindowPosition = it.positionInWindow()
                effect.screenBoundsInWindow = it.boundsInWindow()
            }
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
        val blastProgress = effect.blast.value
        val dim = if (phase == DayAgentCinematicPhase.COUNTDOWN) {
            0.48f + (3 - effect.seconds).coerceIn(0, 2) * 0.11f
        } else 0.38f * (1f - cinematicSmoothStep(0f, 0.25f, blastProgress))
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
                    val p = effect.blast.value
                    alpha = 1f - cinematicSmoothStep(0.02f, 0.24f, p)
                    scaleX = 1f + 0.18f * cinematicSmoothStep(0f, 0.24f, p)
                    scaleY = scaleX
                    val blur = 7.dp.toPx() * cinematicSmoothStep(0.04f, 0.24f, p)
                    renderEffect = if (blur > 0.01f) BlurEffect(blur, blur, TileMode.Clamp) else null
                })
            if (!reducedMotion) {
                CinematicBlastCanvas(
                    progress = { effect.blast.value },
                    accent = effect.accent,
                    modifier = Modifier.fillMaxSize()
                )
            }
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
private fun CinematicBlastCanvas(progress: () -> Float, accent: Color, modifier: Modifier) {
    Canvas(modifier) {
        val p = progress().coerceIn(0f, 1f)
        val center = Offset(size.width / 2f, size.height / 2f)
        val maxRadius = hypot(center.x, center.y)
        val radius = maxRadius * (p / 0.60f)
        val light = 1f - cinematicSmoothStep(0.02f, 0.34f, p)
        // Keep the impact light centered; the wave carries motion through the home.
        drawCircle(accent, radius = 100.dp.toPx() * (0.28f + p), center = center,
            alpha = 0.17f * light)
        drawCircle(Color.White, radius = 25.dp.toPx() * (0.35f + p), center = center,
            alpha = 0.62f * light)
        val ringAlpha = 0.34f * (1f - cinematicSmoothStep(0.52f, 0.90f, p))
        drawCircle(accent, radius = radius, center = center,
            alpha = ringAlpha * 0.32f, style = Stroke(width = 13.dp.toPx()))
        drawCircle(Color.White, radius = radius, center = center,
            alpha = ringAlpha, style = Stroke(width = 1.7.dp.toPx()))
        repeat(32) { index ->
            val angle = index * (2.0 * Math.PI / 32.0) + (index % 3) * 0.13
            val travel = 88.dp.toPx() * cinematicSmoothStep(0f, 0.62f, p) *
                (0.58f + (index % 5) * 0.10f)
            val point = Offset(
                center.x + cos(angle).toFloat() * travel,
                center.y + sin(angle).toFloat() * travel
            )
            drawCircle(
                color = if (index % 3 == 0) Color.White else accent,
                radius = (1.2.dp.toPx() + (index % 4) * 0.7.dp.toPx()) * (1f - p * 0.55f),
                center = point,
                alpha = 1f - cinematicSmoothStep(0.25f, 0.80f, p)
            )
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
            longArrayOf(0, 175, 35, 80),
            intArrayOf(0, 225, 0, 100),
            -1
        ))
    } else {
        @Suppress("DEPRECATION")
        vibrator.vibrate(290L)
    }
}
