package com.example.courseschedule

import android.graphics.Bitmap
import android.os.Build
import android.os.PowerManager
import android.util.Log
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
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
import kotlin.math.roundToInt
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
    // Glass quality is lowered elsewhere during heavy transitions; no CPU/GPU performance hint is requested here.
}

@Composable
fun StartupJankStats(
    phase: StartupPhase,
    screen: String,
    animation: String
) {
    val view = LocalView.current
    val context = LocalContext.current
    val activity = context as? ComponentActivity
    var courseEditorOpenCount by remember { mutableIntStateOf(0) }
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
            if (animation == "CourseEditorPrepare") {
                courseEditorOpenCount += 1
            }
            val thermalStatus = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                context.getSystemService(PowerManager::class.java)?.currentThermalStatus
                    ?: PowerManager.THERMAL_STATUS_NONE
            } else {
                PowerManager.THERMAL_STATUS_NONE
            }
            val state = PerformanceMetricsState.getHolderForHierarchy(view).state
            state?.putState("startup_phase", phase.name)
            state?.putState("screen", screen)
            state?.putState("animation", animation)
            state?.putState("thermal_status", thermalStatus.toString())
            if (animation.startsWith("CourseEditor")) {
                state?.putState(
                    "course_editor_open_kind",
                    if (courseEditorOpenCount <= 1) "First" else "Repeat"
                )
            }
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
    Box(modifier = modifier) { content() }
}

@Composable
fun ContentEntranceContainer(
    phase: StartupPhase,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(modifier = modifier) { content() }
}

@Composable
fun DockEntranceContainer(
    phase: StartupPhase,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(modifier = modifier) { content() }
}

data class HomeWallpaperImages(
    val source: Bitmap?,
    val reducedSource: Bitmap?,
    val blurredSource: Bitmap?,
    val blurBucket: Int,
    val representativeColors: List<Long> = DefaultCourseCardPalette,
    val readabilityBitmap: Bitmap? = null
)

private data class WallpaperSourceImages(
    val source: Bitmap,
    val reducedSource: Bitmap?,
    val representativeColors: List<Long>,
    val readabilityBitmap: Bitmap?
)

private val wallpaperSourceCache = object : LinkedHashMap<String, WallpaperSourceImages>(6, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, WallpaperSourceImages>?): Boolean = size > 3
}

private val wallpaperRenderCache = object : LinkedHashMap<String, HomeWallpaperImages>(8, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, HomeWallpaperImages>?): Boolean = size > 4
}

private fun HomeWallpaperImages.prepareToDraw() = apply {
    source?.prepareToDraw()
    reducedSource?.prepareToDraw()
    blurredSource?.prepareToDraw()
    readabilityBitmap?.prepareToDraw()
}

@Composable
fun rememberHomeWallpaperImages(config: ScheduleConfigEntity): State<HomeWallpaperImages> {
    val context = LocalContext.current.applicationContext
    val useDarkDefaultWallpaper = appUsesDarkTheme(config)
    val blurBucket = bucketWallpaperBlur(config.wallpaperBlur)
    val sourceKey = remember(config.wallpaperUri, config.defaultWallpaperStyle, useDarkDefaultWallpaper) {
        "${config.wallpaperUri}|${config.defaultWallpaperStyle}|$useDarkDefaultWallpaper"
    }
    val renderKey = remember(sourceKey, blurBucket) { "$sourceKey|blur=$blurBucket" }
    // Keep the last rendered image while only the blur bucket is changing.  Keying
    // this state by renderKey used to replace it with an empty bitmap for one frame,
    // which was visible as a flash whenever the blur slider crossed an integer.
    val images = remember(sourceKey) { mutableStateOf(synchronized(wallpaperRenderCache) {
        wallpaperRenderCache[renderKey] ?: HomeWallpaperImages(null, null, null, blurBucket)
    }) }
    LaunchedEffect(renderKey) {
        val cached = synchronized(wallpaperRenderCache) { wallpaperRenderCache[renderKey] }
        if (cached != null) {
            images.value = cached
            return@LaunchedEffect
        }
        val loaded = withContext(Dispatchers.IO) {
            val cachedSource = synchronized(wallpaperSourceCache) { wallpaperSourceCache[sourceKey] }
            val sourceEntry = cachedSource ?: loadWallpaperBitmap(context, config, useDarkDefaultWallpaper)?.let { source ->
                WallpaperSourceImages(
                    source = source,
                    reducedSource = createReducedWallpaperBitmap(source),
                    representativeColors = extractRepresentativeWallpaperColors(source),
                    readabilityBitmap = createWallpaperReadabilityBitmap(source)
                ).also { entry ->
                    synchronized(wallpaperSourceCache) { wallpaperSourceCache[sourceKey] = entry }
                }
            }
            HomeWallpaperImages(
                source = sourceEntry?.source,
                reducedSource = sourceEntry?.reducedSource,
                blurredSource = createBlurredWallpaperBitmap(sourceEntry?.source, blurBucket),
                blurBucket = blurBucket,
                representativeColors = sourceEntry?.representativeColors ?: DefaultCourseCardPalette,
                readabilityBitmap = sourceEntry?.readabilityBitmap
            ).prepareToDraw()
        }
        synchronized(wallpaperRenderCache) { wallpaperRenderCache[renderKey] = loaded }
        images.value = loaded
    }
    return images
}

fun bucketWallpaperBlur(blur: Float): Int {
    val clampedBlur = blur.coerceIn(0f, WallpaperBlurMaxDp)
    return clampedBlur.roundToInt()
}

@Composable
fun WaitForPrewarmFrames(onReady: () -> Unit) {
    LaunchedEffect(Unit) {
        withFrameNanos { }
        withFrameNanos { }
        onReady()
    }
}
