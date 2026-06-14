package com.example.courseschedule

import android.content.Context
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.pow

enum class StartupMotionTier {
    Full,
    Reduced,
    Disabled
}

data class StartupEntranceSpec(
    val progress: Float,
    val tier: StartupMotionTier,
    val maxAnimatedCards: Int
)

enum class StartupFlyOrigin {
    Top,
    Bottom,
    Left,
    Right,
    TopLeft,
    TopRight,
    BottomLeft,
    BottomRight,
    Center
}

val LocalStartupEntranceSpec = compositionLocalOf {
    StartupEntranceSpec(
        progress = 1f,
        tier = StartupMotionTier.Full,
        maxAnimatedCards = 36
    )
}

@Composable
fun rememberStartupEntranceSpec(
    phase: StartupPhase,
    courseCount: Int,
    animationsEnabled: Boolean
): StartupEntranceSpec {
    val progress = remember { Animatable(if (phase.isAtLeastEntrance || !animationsEnabled) 1f else 0f) }
    LaunchedEffect(phase, animationsEnabled) {
        if (!animationsEnabled) {
            progress.snapTo(1f)
            return@LaunchedEffect
        }
        when (phase) {
            StartupPhase.Entrance -> {
                progress.snapTo(0f)
                progress.animateTo(
                    1f,
                    animationSpec = tween(
                        durationMillis = 820,
                        easing = FastOutSlowInEasing
                    )
                )
            }
            StartupPhase.Settle,
            StartupPhase.FullQuality -> progress.snapTo(1f)
            else -> progress.snapTo(0f)
        }
    }
    val tier = when {
        !animationsEnabled -> StartupMotionTier.Disabled
        courseCount > 64 -> StartupMotionTier.Reduced
        else -> StartupMotionTier.Full
    }
    val maxAnimatedCards = when {
        !animationsEnabled -> 0
        courseCount > 64 -> 0
        else -> 36
    }
    return StartupEntranceSpec(
        progress = progress.value,
        tier = tier,
        maxAnimatedCards = maxAnimatedCards
    )
}

fun Modifier.startupFlyIn(
    key: Any,
    index: Int,
    totalCount: Int,
    origin: StartupFlyOrigin,
    intensity: Float = 1f,
    delayFactor: Float = 0f,
    alphaStart: Float = 0f,
    respectCardLimit: Boolean = true
): Modifier = composed(inspectorInfo = {
    name = "startupFlyIn"
    properties["key"] = key
    properties["index"] = index
    properties["totalCount"] = totalCount
    properties["origin"] = origin
}) {
    val spec = LocalStartupEntranceSpec.current
    if (spec.tier == StartupMotionTier.Disabled || spec.progress >= 0.999f) {
        this
    } else {
        val cardMotionDisabled = respectCardLimit && spec.maxAnimatedCards == 0
        if (cardMotionDisabled) return@composed this
        val density = LocalDensity.current
        val configuration = LocalConfiguration.current
        val screenWidthDp = configuration.screenWidthDp.dp
        val screenHeightDp = configuration.screenHeightDp.dp
        val largeScreenBoost = if (configuration.screenWidthDp >= 600) 1.12f else 1f
        val fadeOnly = respectCardLimit && index >= spec.maxAnimatedCards
        val safeIntensity = intensity.coerceIn(0.35f, 1.5f) * largeScreenBoost
        val startXdp = when (origin) {
            StartupFlyOrigin.Left,
            StartupFlyOrigin.TopLeft,
            StartupFlyOrigin.BottomLeft -> -screenWidthDp * 0.92f
            StartupFlyOrigin.Right,
            StartupFlyOrigin.TopRight,
            StartupFlyOrigin.BottomRight -> screenWidthDp * 0.92f
            else -> 0.dp
        }
        val startYdp = when (origin) {
            StartupFlyOrigin.Top,
            StartupFlyOrigin.TopLeft,
            StartupFlyOrigin.TopRight -> -screenHeightDp * 0.64f
            StartupFlyOrigin.Bottom,
            StartupFlyOrigin.BottomLeft,
            StartupFlyOrigin.BottomRight -> screenHeightDp * 0.64f
            StartupFlyOrigin.Center -> screenHeightDp * 0.16f
            else -> 0.dp
        }
        val startX = with(density) { startXdp.toPx() } * safeIntensity
        val startY = with(density) { startYdp.toPx() } * safeIntensity
        val stagger = (delayFactor + index.coerceAtLeast(0) * 0.025f).coerceIn(0f, 0.42f)
        val rawProgress = ((spec.progress - stagger) / (1f - stagger).coerceAtLeast(0.01f)).coerceIn(0f, 1f)
        val eased = easeOutQuint(rawProgress)
        val alphaProgress = easeOutCubic(rawProgress)
        val scaleProgress = softBackOut(rawProgress)
        val baseScale = if (origin == StartupFlyOrigin.Center) 0.96f else 0.965f
        val scale = if (fadeOnly) 1f else lerpFloat(baseScale, 1f, scaleProgress)
        val motionScale = if (fadeOnly) 0f else 1f
        this.graphicsLayer {
            translationX = startX * (1f - eased) * motionScale
            translationY = startY * (1f - eased) * motionScale
            alpha = lerpFloat(alphaStart.coerceIn(0f, 1f), 1f, alphaProgress)
            scaleX = scale
            scaleY = scale
        }
    }
}

fun startupOriginForWeekCard(
    dayIndex: Int,
    periodIndex: Int,
    weekdayCount: Int,
    periodCount: Int
): StartupFlyOrigin {
    val safeWeekdayCount = weekdayCount.coerceAtLeast(1)
    val safePeriodCount = periodCount.coerceAtLeast(1)
    val midDay = (safeWeekdayCount + 1) / 2f
    val midPeriod = (safePeriodCount + 1) / 2f
    val nearCenterDay = abs(dayIndex - midDay) <= 0.65f
    val nearCenterPeriod = abs(periodIndex - midPeriod) <= (safePeriodCount * 0.18f).coerceAtLeast(1f)
    if (nearCenterDay && nearCenterPeriod) return StartupFlyOrigin.Center
    val left = dayIndex <= midDay
    val top = periodIndex <= midPeriod
    return when {
        left && top -> StartupFlyOrigin.TopLeft
        !left && top -> StartupFlyOrigin.TopRight
        left && !top -> StartupFlyOrigin.BottomLeft
        else -> StartupFlyOrigin.BottomRight
    }
}

fun animationsEnabled(context: Context): Boolean {
    return runCatching {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f
    }.getOrDefault(true)
}

private fun easeOutCubic(progress: Float): Float {
    val p = progress.coerceIn(0f, 1f)
    return 1f - (1f - p).pow(3)
}

private fun easeOutQuint(progress: Float): Float {
    val p = progress.coerceIn(0f, 1f)
    return 1f - (1f - p).pow(5)
}

private fun softBackOut(progress: Float): Float {
    val p = progress.coerceIn(0f, 1f)
    val c1 = 0.72f
    val c3 = c1 + 1f
    val t = p - 1f
    return 1f + c3 * t * t * t + c1 * t * t
}

private fun lerpFloat(start: Float, stop: Float, fraction: Float): Float {
    return start + (stop - start) * fraction
}
