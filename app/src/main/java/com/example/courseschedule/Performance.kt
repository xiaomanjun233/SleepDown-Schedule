package com.example.courseschedule

import android.graphics.Bitmap
import android.os.Build
import android.os.PerformanceHintManager
import android.util.Log
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.metrics.performance.JankStats
import androidx.metrics.performance.PerformanceMetricsState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.LinkedHashMap
import kotlin.math.max
import kotlin.math.sqrt

enum class StartupPhase {
    Loading,
    Prewarm,
    Reveal,
    Entrance,
    Settle,
    FullQuality
}

val LocalStartupPhase = staticCompositionLocalOf { StartupPhase.FullQuality }
val LocalGlassQuality = staticCompositionLocalOf { 1f }

fun animatedGlassQuality(phase: StartupPhase): Float = when (phase) {
    StartupPhase.Loading,
    StartupPhase.Prewarm -> 0.45f
    StartupPhase.Reveal -> 0.55f
    StartupPhase.Entrance -> 0.70f
    StartupPhase.Settle -> 0.85f
    StartupPhase.FullQuality -> 1f
}

val StartupPhase.isAtLeastEntrance: Boolean
    get() = this == StartupPhase.Entrance || this == StartupPhase.Settle || this == StartupPhase.FullQuality

@Composable
fun StartupPerformanceBoost(active: Boolean) {
    val view = LocalView.current
    DisposableEffect(active, view) {
        if (!active || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            onDispose {}
        } else {
            val session = runCatching {
                val manager = view.context.getSystemService(PerformanceHintManager::class.java)
                val refreshRate = view.display?.refreshRate?.takeIf { it > 0f } ?: 60f
                val targetDurationNanos = (1_000_000_000f / refreshRate).toLong().coerceAtLeast(4_000_000L)
                manager?.createHintSession(intArrayOf(android.os.Process.myTid()), targetDurationNanos)
                    ?.also { it.updateTargetWorkDuration(targetDurationNanos) }
            }.getOrNull()
            onDispose {
                runCatching { session?.close() }
            }
        }
    }
}

@Composable
fun StartupJankStats(
    phase: StartupPhase,
    screen: String,
    animation: String
) {
    val view = LocalView.current
    val activity = LocalContext.current as? ComponentActivity
    DisposableEffect(view, activity) {
        var jankStats: JankStats? = null
        fun startTracking() {
            if (!BuildConfig.DEBUG || activity == null || jankStats != null) return
            jankStats = runCatching {
                JankStats.createAndTrack(activity.window) { frameData ->
                    if (frameData.isJank) {
                        Log.d(
                            "ScheduleJank",
                            "jank frame=${frameData.frameDurationUiNanos / 1_000_000f}ms states=${frameData.states}"
                        )
                    }
                }
            }.getOrNull()
        }
        val attachListener = object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) = startTracking()
            override fun onViewDetachedFromWindow(v: View) = Unit
        }
        if (view.isAttachedToWindow) {
            startTracking()
        } else {
            view.addOnAttachStateChangeListener(attachListener)
        }
        onDispose {
            view.removeOnAttachStateChangeListener(attachListener)
            runCatching { jankStats?.isTrackingEnabled = false }
        }
    }

    LaunchedEffect(view, phase, screen, animation) {
        runCatching {
            val state = PerformanceMetricsState.getHolderForHierarchy(view).state
            state?.putState("startup_phase", phase.name)
            state?.putState("screen", screen)
            state?.putState("animation", animation)
        }
    }
}

@Composable
fun PerformanceAnimationState(animation: String, active: Boolean) {
    val view = LocalView.current
    LaunchedEffect(view, animation, active) {
        runCatching {
            val state = PerformanceMetricsState.getHolderForHierarchy(view).state
            if (active) {
                state?.putState("animation", animation)
            } else {
                state?.putState("animation", "Idle")
            }
        }
    }
}

@Composable
fun StartupRevealOverlay(
    phase: StartupPhase,
    splashColor: Color,
    onRevealFinished: () -> Unit
) {
    val revealRadius = remember { androidx.compose.animation.core.Animatable(0f) }
    val path = remember { Path().apply { fillType = PathFillType.EvenOdd } }
    var diagonal by remember { mutableStateOf(0f) }
    val visible = phase == StartupPhase.Loading || phase == StartupPhase.Prewarm || phase == StartupPhase.Reveal

    LaunchedEffect(phase, diagonal) {
        when {
            phase == StartupPhase.Loading || phase == StartupPhase.Prewarm -> revealRadius.snapTo(0f)
            phase == StartupPhase.Reveal && diagonal > 0f -> {
                revealRadius.snapTo(0f)
                revealRadius.animateTo(
                    diagonal * 1.2f,
                    animationSpec = androidx.compose.animation.core.tween(
                        durationMillis = 350,
                        easing = androidx.compose.animation.core.FastOutSlowInEasing
                    )
                )
                onRevealFinished()
            }
        }
    }

    if (visible) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    compositingStrategy = CompositingStrategy.Offscreen
                }
                .onSizeChanged { size ->
                    val halfW = size.width / 2f
                    val halfH = size.height / 2f
                    diagonal = sqrt(halfW * halfW + halfH * halfH)
                }
                .drawWithContent {
                    val radius = revealRadius.value
                    if (radius <= 0f) {
                        drawRect(splashColor)
                    } else {
                        path.reset()
                        path.fillType = PathFillType.EvenOdd
                        path.addRect(Rect(0f, 0f, size.width, size.height))
                        path.addOval(
                            Rect(
                                center = Offset(size.width / 2f, size.height / 2f),
                                radius = radius
                            )
                        )
                        drawPath(path, splashColor)
                    }
                }
        )
    }
}

@Composable
fun TopBarEntranceContainer(
    phase: StartupPhase,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    StartupEntranceContainer(
        phase = phase,
        startOffsetY = -200f,
        delayMillis = 0,
        dampingRatio = 0.68f,
        stiffness = 520f,
        modifier = modifier,
        content = content
    )
}

@Composable
fun ContentEntranceContainer(
    phase: StartupPhase,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    StartupEntranceContainer(
        phase = phase,
        startOffsetY = 80f,
        delayMillis = 95,
        dampingRatio = 0.62f,
        stiffness = 430f,
        modifier = modifier,
        content = content
    )
}

@Composable
fun DockEntranceContainer(
    phase: StartupPhase,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    StartupEntranceContainer(
        phase = phase,
        startOffsetY = 200f,
        delayMillis = 190,
        dampingRatio = 0.70f,
        stiffness = 500f,
        modifier = modifier,
        content = content
    )
}

@Composable
private fun StartupEntranceContainer(
    phase: StartupPhase,
    startOffsetY: Float,
    delayMillis: Long,
    dampingRatio: Float,
    stiffness: Float,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val offsetY = remember { Animatable(if (phase.isAtLeastEntrance) 0f else startOffsetY) }
    val alpha = remember { Animatable(if (phase.isAtLeastEntrance) 1f else 0.72f) }
    LaunchedEffect(phase) {
        when {
            phase == StartupPhase.Entrance -> {
                delay(delayMillis)
                launch {
                    offsetY.animateTo(0f, spring(dampingRatio = dampingRatio, stiffness = stiffness))
                }
                alpha.animateTo(1f, spring(dampingRatio = 0.78f, stiffness = stiffness + 80f))
            }
            phase == StartupPhase.FullQuality && offsetY.value != 0f -> {
                offsetY.snapTo(0f)
                alpha.snapTo(1f)
            }
        }
    }
    Box(
        modifier = modifier.graphicsLayer {
            translationY = offsetY.value
            this.alpha = alpha.value
        }
    ) {
        content()
    }
}

data class HomeWallpaperImages(
    val source: Bitmap?,
    val blurred: Bitmap?,
    val blurBucket: Int
)

private val wallpaperCache = object : LinkedHashMap<String, HomeWallpaperImages>(8, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, HomeWallpaperImages>?): Boolean = size > 4
}

@Composable
fun rememberHomeWallpaperImages(config: ScheduleConfigEntity): State<HomeWallpaperImages> {
    val context = LocalContext.current.applicationContext
    val useDarkDefaultWallpaper = appUsesDarkTheme(config)
    val blurBucket = bucketWallpaperBlur(config.wallpaperBlur)
    val cacheKey = remember(config.wallpaperUri, config.defaultWallpaperStyle, useDarkDefaultWallpaper, blurBucket, config.wallpaperBrightness) {
        "${config.wallpaperUri}|${config.defaultWallpaperStyle}|$useDarkDefaultWallpaper|$blurBucket|${config.wallpaperBrightness}"
    }
    return produceState(initialValue = synchronized(wallpaperCache) {
        wallpaperCache[cacheKey] ?: HomeWallpaperImages(null, null, blurBucket)
    }, cacheKey) {
        val cached = synchronized(wallpaperCache) { wallpaperCache[cacheKey] }
        if (cached != null) {
            value = cached
            return@produceState
        }
        val loaded = withContext(Dispatchers.IO) {
            val source = loadWallpaperBitmap(context, config, useDarkDefaultWallpaper)
            val blurred = withContext(Dispatchers.Default) {
                source?.let { createStartupBlurredBitmap(it, blurBucket) }
            }
            HomeWallpaperImages(source = source, blurred = blurred, blurBucket = blurBucket)
        }
        synchronized(wallpaperCache) { wallpaperCache[cacheKey] = loaded }
        value = loaded
    }
}

fun bucketWallpaperBlur(blur: Float): Int {
    val buckets = intArrayOf(0, 4, 8, 12, 18, 24, 30)
    return buckets.minBy { kotlin.math.abs(it - blur.toInt()) }
}

private fun createStartupBlurredBitmap(source: Bitmap, blurBucket: Int): Bitmap? {
    if (source.width <= 0 || source.height <= 0) return null
    if (blurBucket <= 0) return source
    return runCatching {
        val scale = when {
            blurBucket >= 24 -> 10
            blurBucket >= 18 -> 8
            blurBucket >= 12 -> 6
            else -> 4
        }
        val smallWidth = max(48, source.width / scale)
        val smallHeight = max(48, source.height / scale)
        Bitmap.createScaledBitmap(source, smallWidth, smallHeight, true)
    }.getOrNull()
}

@Composable
fun waitForPrewarmFrames(onReady: () -> Unit) {
    LaunchedEffect(Unit) {
        withFrameNanos { }
        withFrameNanos { }
        onReady()
    }
}
