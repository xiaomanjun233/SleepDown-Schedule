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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.itos.coui.button.CouiButton
import com.xiaomanjun.sleepdownschedule.R
import com.xiaomanjun.sleepdownschedule.transition.LiveTransitionButton
import java.io.IOException
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val WelcomeTypingMillis = 4_500f
private const val TitlePauseMillis = 200f
private const val ContinueDelayMillis = 100f
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
) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val reducedMotion = rememberWelcomeReducedMotion()
    val name = stringResource(R.string.app_product_name)
    val headline = stringResource(R.string.version_welcome_headline)
    val letterMillis = (WelcomeTypingMillis - TitlePauseMillis) / (name.length + headline.length)
    val headlineStart = WelcomePanelSettleMillis + name.length * letterMillis + TitlePauseMillis
    val actionStart = WelcomePanelSettleMillis + WelcomeTypingMillis + ContinueDelayMillis
    val duration = maxOf(MascotEndMillis, actionStart + 250f)
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
    val showTitle by remember(clock) { derivedStateOf { clock.value >= WelcomePanelSettleMillis } }
    val showAction by remember(clock, actionStart) { derivedStateOf { clock.value >= actionStart } }
    val lightNavigation by remember(clock) {
        derivedStateOf { clock.value >= WelcomeWhiteFrameMillis }
    }
    LaunchedEffect(lightNavigation) { onLightBackground(lightNavigation) }
    BoxWithConstraints(
        Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        val maxDrop = if (maxWidth >= 600.dp) 96.dp else 72.dp
        val density = androidx.compose.ui.platform.LocalDensity.current
        val statusTop = with(density) { WindowInsets.statusBarsIgnoringVisibility.getTop(this).toDp() }
        val panelDrop = maxOf(statusTop, (maxHeight * 0.15f).coerceAtMost(maxDrop))
        val margin = if (maxWidth >= 450.dp) 72.dp else 32.dp
        val titleTop = panelDrop + (maxHeight * 0.16f).coerceAtMost(72.dp)
        val titleSize = ((maxWidth - margin * 2).value * 0.123f).coerceIn(28f, 40f).sp
        val rail = Modifier.widthIn(max = 680.dp).fillMaxWidth()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
            .padding(horizontal = margin)

        Box(
            Modifier.matchParentSize(),
            contentAlignment = Alignment.TopCenter,
        ) {
            Box(Modifier.matchParentSize().versionWelcomeBackdrop(panelDrop, elapsedMillis))
            WelcomeMascot(
                rail.height(panelDrop + 8.dp),
                elapsedMillis,
                reducedMotion,
            )
            if (showTitle) {
                val style = TextStyle(
                    color = Color(0xFF171719),
                    fontSize = titleSize,
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = titleSize * 1.2f,
                )
                Box(
                    rail.fillMaxHeight()
                        .padding(top = titleTop)
                        .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))
                        .padding(bottom = 104.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Column(Modifier.semantics(mergeDescendants = true) { heading() }) {
                        WelcomeTypedLine(name, style, WelcomePanelSettleMillis, letterMillis, elapsedMillis)
                        WelcomeTypedLine(headline, style, headlineStart, letterMillis, elapsedMillis)
                    }
                }
            }
        }
        Box(
            rail.align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))
                .padding(bottom = 24.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (showAction) LiveTransitionButton(modifier = Modifier.graphicsLayer {
                alpha = WelcomeTextEasing.transform(segment(elapsedMillis(), actionStart, actionStart + 250f))
            }) { source ->
                CouiButton(
                    modifier = Modifier.widthIn(min = 160.dp).heightIn(min = 56.dp),
                    text = stringResource(R.string.version_welcome_continue),
                    dark = false,
                    onClick = { if (!opening) onContinue(source.takeUnless { reducedMotion }) },
                )
            }
        }
    }
}

private val WelcomeTextEasing = CubicBezierEasing(0.30f, 0f, 0.10f, 1f)

@Composable
private fun WelcomeTypedLine(
    text: String,
    style: TextStyle,
    startMillis: Float,
    letterMillis: Float,
    elapsedMillis: () -> Float,
) {
    val revealed by remember(text, startMillis, letterMillis, elapsedMillis) {
        derivedStateOf { ((elapsedMillis() - startMillis) / letterMillis).toInt().coerceIn(0, text.length) }
    }
    // Measure the complete line from the first character so wrapping stays fixed during typing.
    val visibleText = remember(text, revealed) {
        buildAnnotatedString {
            append(text)
            if (revealed < text.length) addStyle(SpanStyle(color = Color.Transparent), revealed, text.length)
        }
    }
    BasicText(visibleText, style = style)
}

@Composable
private fun WelcomeMascot(modifier: Modifier, elapsedMillis: () -> Float, reduceMotion: Boolean) {
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
                update = {},
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
