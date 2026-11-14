package com.xiaomanjun.sleepdownschedule.feature.onboarding

import android.animation.ValueAnimator
import android.content.res.Resources
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.view.View
import android.view.accessibility.AccessibilityManager
import android.widget.ImageView
import androidx.activity.compose.BackHandler
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.itos.coui.button.CouiButton
import com.itos.coui.button.CouiButtonDefaults
import com.xiaomanjun.sleepdownschedule.R
import com.xiaomanjun.sleepdownschedule.glass.GlassBackdropDomain
import com.xiaomanjun.sleepdownschedule.glass.couiIlluminatedCapsuleMaterial
import com.xiaomanjun.sleepdownschedule.glass.glassBackdropProducer
import com.xiaomanjun.sleepdownschedule.glass.rememberGlassLayerBackdrop
import com.xiaomanjun.sleepdownschedule.glass.retainGlassSources
import com.xiaomanjun.sleepdownschedule.transition.LiveTransitionButton
import java.io.IOException
import java.text.BreakIterator
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal const val WelcomeFirstLineMillis = WelcomePanelSettleMillis + 80f
internal const val WelcomeSecondLineMillis = WelcomeFirstLineMillis + 445f
internal const val WelcomeContinueMillis = WelcomeSecondLineMillis + 485f
private const val LineRevealMillis = 440f
private const val ContinueRevealMillis = 220f
internal val WelcomeContinueColors = CouiButtonDefaults.secondaryColors(dark = false, material = true)
    .copy(surface = Color.White.copy(alpha = 0.47f))
// The 3.583 s character animation has a short hold before releasing its decoder.
private const val MascotEndMillis = WelcomePanelSettleMillis + 3_750f
private val WelcomeClockSaver = Saver<Animatable<Float, AnimationVector1D>, Float>(
    save = { it.value },
    restore = { Animatable(it) },
)

@Composable
private fun rememberWelcomeReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        !ValueAnimator.areAnimatorsEnabled() ||
            context.getSystemService(AccessibilityManager::class.java)?.isTouchExplorationEnabled == true
    }
}

@Composable
internal fun VersionWelcomeScreen(
    opening: Boolean,
    onLightBackground: (Boolean) -> Unit,
    onContinue: (View?) -> Unit,
    onPrepareHome: () -> Unit = {},
) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val reducedMotion = rememberWelcomeReducedMotion()
    val name = stringResource(R.string.app_product_name)
    val headline = stringResource(R.string.version_welcome_headline)
    val duration = MascotEndMillis
    val clock = rememberSaveable(saver = WelcomeClockSaver) {
        Animatable(if (reducedMotion) duration else 0f)
    }
    val elapsedMillis = remember(clock) { { clock.value } }
    BackHandler { if (!opening) onContinue(null) }
    LaunchedEffect(lifecycle, clock, duration, reducedMotion, opening) {
        if (opening) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            val remaining = (duration - clock.value).roundToInt()
            if (remaining > 0) {
                if (reducedMotion) clock.snapTo(duration)
                else clock.animateTo(duration, tween(remaining, easing = LinearEasing))
            }
        }
    }
    val showTitle by remember(clock) { derivedStateOf { clock.value >= WelcomeFirstLineMillis } }
    val showAction by remember(clock) { derivedStateOf { clock.value >= WelcomeContinueMillis } }
    val lightVisible by remember(clock) { derivedStateOf { clock.value >= WelcomePanelSettleMillis } }
    val latestPrepareHome by rememberUpdatedState(onPrepareHome)
    LaunchedEffect(lightVisible) { if (lightVisible) latestPrepareHome() }
    val lightNavigation by remember(clock) {
        derivedStateOf { clock.value >= WelcomeWhiteFrameMillis }
    }
    LaunchedEffect(lightNavigation) { onLightBackground(lightNavigation) }
    var lightPhase by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(lifecycle, lightVisible, reducedMotion, opening) {
        if (!lightVisible || reducedMotion || opening) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var previous = withFrameNanos { it }
            while (true) {
                withFrameNanos { time ->
                    lightPhase = (lightPhase + (time - previous) / (WelcomeLightBreathMillis * 1_000_000f)) % 1f
                    previous = time
                }
            }
        }
    }
    val lightBackdrop = rememberGlassLayerBackdrop(GlassBackdropDomain.Background, "welcome-light")
    BoxWithConstraints(
        Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        val maxDrop = if (maxWidth >= 600.dp) 96.dp else 72.dp
        val density = LocalDensity.current
        val statusTop = with(density) { WindowInsets.statusBarsIgnoringVisibility.getTop(this).toDp() }
        val panelDrop = maxOf(statusTop, (maxHeight * 0.15f).coerceAtMost(maxDrop))
        val margin = (maxWidth * 0.085f).coerceIn(12.dp, 64.dp)
        val titleTop = panelDrop + (maxHeight * 0.16f).coerceAtMost(72.dp)
        val rail = Modifier.widthIn(max = 680.dp).fillMaxWidth()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
            .padding(horizontal = margin)

        Box(
            Modifier.matchParentSize(),
            contentAlignment = Alignment.TopCenter,
        ) {
            Box(Modifier.matchParentSize()
                .glassBackdropProducer(lightBackdrop, enabled = showAction && Build.VERSION.SDK_INT >= 31)
                .versionWelcomeBackdrop(panelDrop, elapsedMillis, lightPhase = { lightPhase }))
            WelcomeMascot(
                rail.height(panelDrop + 8.dp),
                elapsedMillis,
                reducedMotion,
                paused = opening,
            )
            if (showTitle) {
                WelcomeHeading(
                    name, headline, elapsedMillis,
                    modifier = rail.fillMaxHeight()
                        .padding(top = titleTop)
                        .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))
                        .padding(bottom = 100.dp)
                        .verticalScroll(rememberScrollState()),
                )
            }
        }
        Box(
            rail.align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))
                .padding(bottom = 24.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (showAction) {
                val lightMaterial = if (Build.VERSION.SDK_INT >= 31) {
                    retainGlassSources(lightBackdrop)
                    couiIlluminatedCapsuleMaterial(lightBackdrop, CouiButtonDefaults.shape)
                } else Modifier
                LiveTransitionButton(modifier = Modifier.graphicsLayer {
                    alpha = WelcomeTextEasing.transform(segment(
                        elapsedMillis(), WelcomeContinueMillis, WelcomeContinueMillis + ContinueRevealMillis,
                    ))
                }) { source ->
                    CouiButton(
                        modifier = Modifier.widthIn(min = 164.dp).heightIn(min = 52.dp),
                        text = stringResource(R.string.version_welcome_continue),
                        dark = false,
                        colors = WelcomeContinueColors,
                        backgroundModifier = lightMaterial,
                        onClick = { if (!opening) onContinue(source.takeUnless { reducedMotion }) },
                    )
                }
            }
        }
    }
}

private val WelcomeTextEasing = CubicBezierEasing(0.30f, 0f, 0.10f, 1f)

@Composable
internal fun WelcomeHeading(name: String, headline: String, elapsedMillis: () -> Float, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(modifier) {
        val availableWidth = Constraints(maxWidth = (constraints.maxWidth * 0.95f).toInt())
        val preferred = (maxWidth.value * 0.125f).coerceIn(26f, 40f)
        val style = remember(name, headline, availableWidth, density, measurer) {
            val base = TextStyle(
                color = Color(0xFF171719),
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.SemiBold,
            )
            fun fits(size: Float): Boolean {
                val font = base.copy(fontSize = size.sp, lineHeight = (size * 1.2f).sp)
                return listOf(name, headline).all { line ->
                    val layout = measurer.measure(AnnotatedString(line), font, softWrap = false, maxLines = 1,
                        constraints = availableWidth, density = density)
                    !layout.hasVisualOverflow && layout.getLineEnd(0) == line.length
                }
            }
            val fontSize = if (fits(preferred)) preferred else {
                var lower = 1f
                var upper = preferred
                repeat(9) {
                    val mid = (lower + upper) / 2f
                    if (fits(mid)) lower = mid else upper = mid
                }
                lower
            }
            base.copy(fontSize = fontSize.sp, lineHeight = (fontSize * 1.2f).sp)
        }
        Column(Modifier.semantics(mergeDescendants = true) { heading() }) {
            WelcomeRevealLine(name, style, WelcomeFirstLineMillis, elapsedMillis)
            WelcomeRevealLine(headline, style, WelcomeSecondLineMillis, elapsedMillis)
        }
    }
}

@Composable
private fun WelcomeRevealLine(
    text: String,
    style: TextStyle,
    startMillis: Float,
    elapsedMillis: () -> Float,
) {
    val elapsed by remember(startMillis, elapsedMillis) {
        derivedStateOf { (elapsedMillis() - startMillis).coerceIn(-1f, LineRevealMillis) }
    }
    if (elapsed < 0f) return
    if (elapsed >= LineRevealMillis) {
        BasicText(AnnotatedString(text), style = style, maxLines = 1, softWrap = false, overflow = TextOverflow.Clip)
        return
    }
    val ends = remember(text) {
        val boundaries = BreakIterator.getCharacterInstance().apply { setText(text) }
        generateSequence { boundaries.next().takeUnless { it == BreakIterator.DONE } }.toList()
    }
    val blurRadius = with(LocalDensity.current) { 5.dp.toPx() }
    val visibleText = buildAnnotatedString {
        append(text)
        var start = 0
        for ((index, end) in ends.withIndex()) {
            val delay = 150f * index / (ends.size - 1).coerceAtLeast(1)
            val sharp = smoothSegment(elapsed, delay, delay + 255f)
            addStyle(
                SpanStyle(
                    color = style.color.copy(alpha = 0.10f + sharp * 0.90f),
                    shadow = if (sharp < 1f) Shadow(
                        color = style.color.copy(alpha = 0.70f * (1f - sharp)),
                        offset = Offset.Zero, blurRadius = blurRadius * (1f - sharp),
                    ) else Shadow.None,
                ),
                start, end,
            )
            start = end
        }
    }
    BasicText(
        visibleText,
        modifier = Modifier.graphicsLayer { alpha = WelcomeTextEasing.transform(segment(elapsed, 0f, 270f)) },
        style = style, maxLines = 1, softWrap = false, overflow = TextOverflow.Clip,
    )
}

@Composable
private fun WelcomeMascot(modifier: Modifier, elapsedMillis: () -> Float, reduceMotion: Boolean, paused: Boolean) {
    val resources = LocalContext.current.resources
    val showMascot by remember(elapsedMillis) { derivedStateOf { elapsedMillis() >= WelcomePanelSettleMillis } }
    val settled by remember(elapsedMillis) { derivedStateOf { elapsedMillis() >= MascotEndMillis } }
    val motion = if (!reduceMotion && !settled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        produceState<Drawable?>(null, resources) {
            value = withContext(Dispatchers.IO) { decodeMascotMotion(resources) }
        }.value
    } else null
    if (!showMascot) return

    val entranceDrawable = motion
    val poster = painterResource(R.drawable.version_welcome_mascot)
    Box(modifier.clipToBounds(), contentAlignment = Alignment.CenterStart) {
        val sprite = Modifier.fillMaxHeight().aspectRatio(
            poster.intrinsicSize.width / poster.intrinsicSize.height,
            matchHeightConstraintsFirst = true,
        )
        if (entranceDrawable != null) {
            AndroidView(
                factory = { context ->
                    ImageView(context).apply {
                        scaleType = ImageView.ScaleType.FIT_CENTER
                        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                        setImageDrawable(entranceDrawable)
                        (entranceDrawable as? android.graphics.drawable.Animatable)?.start()
                    }
                },
                modifier = sprite,
                onRelease = { view ->
                    (view.drawable as? android.graphics.drawable.Animatable)?.stop()
                    view.setImageDrawable(null)
                },
                update = { view ->
                    if (paused) (view.drawable as? android.graphics.drawable.Animatable)?.stop()
                },
            )
        } else {
            Image(
                painter = poster,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = sprite.graphicsLayer {
                    val reveal = WelcomeTextEasing.transform(segment(
                        elapsedMillis(), WelcomePanelSettleMillis, WelcomePanelSettleMillis + 400f,
                    ))
                    translationY = size.height * (1f - reveal)
                },
            )
        }
    }
}

@RequiresApi(Build.VERSION_CODES.P)
private fun decodeMascotMotion(resources: Resources): AnimatedImageDrawable? = try {
    val source = ImageDecoder.createSource(resources, R.raw.version_welcome_mascot_motion)
    (ImageDecoder.decodeDrawable(source) as? AnimatedImageDrawable)?.apply { repeatCount = 0 }
} catch (_: IOException) {
    null
}
