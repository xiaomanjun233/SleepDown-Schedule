package com.xiaomanjun.sleepdownschedule.feature.home.day

import com.xiaomanjun.sleepdownschedule.core.wallpaper.HomeReadabilityContext
import com.xiaomanjun.sleepdownschedule.core.wallpaper.LocalHomeReadability
import com.xiaomanjun.sleepdownschedule.core.wallpaper.sampleVisibleWallpaperColors
import com.xiaomanjun.sleepdownschedule.core.wallpaper.hasAnyWallpaper
import android.graphics.Bitmap
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.get
import com.kyant.backdrop.shadow.Shadow
import com.xiaomanjun.sleepdownschedule.ScheduleConfigEntity
import com.xiaomanjun.sleepdownschedule.core.wallpaper.WallpaperCropState
import com.xiaomanjun.sleepdownschedule.core.wallpaper.WallpaperPreviewOrientation
import com.xiaomanjun.sleepdownschedule.core.wallpaper.calculateFocusCropRect
import com.xiaomanjun.sleepdownschedule.core.wallpaper.wallpaperCropState
import com.xiaomanjun.sleepdownschedule.feature.agent.homeCountdownShockwave
import com.xiaomanjun.sleepdownschedule.core.ui.text.LocalHomeBackgroundFrozen
import com.xiaomanjun.sleepdownschedule.core.ui.text.LocalHomeTextContrastFrozen
import com.xiaomanjun.sleepdownschedule.glass.ui.LocalAdaptiveGlass
import kotlin.math.ceil
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map

private fun sampleVisibleWallpaperLuminances(
    context: HomeReadabilityContext,
    bounds: Rect,
    columns: Int = 7,
    rows: Int = 5
): FloatArray? = sampleVisibleWallpaperColors(context, bounds, columns, rows)
    ?.map { it.luminance() }?.toFloatArray()

internal fun homeStatusBarWallpaperLuminance(context: HomeReadabilityContext): Float? {
    val root = context.rootSize
    if (root.width <= 0 || root.height <= 0) return null
    // Status-bar appearance is global, so sample both icon clusters across the complete top strip.
    // The crop mapping is shared with HomeReadableText and therefore follows the visible wallpaper,
    // including a user-saved portrait/landscape crop rather than the uncropped source bitmap.
    return sampleVisibleWallpaperLuminances(
        context = context,
        bounds = Rect(
            left = 0f,
            top = 0f,
            right = root.width.toFloat(),
            bottom = (root.height * 0.06f).coerceAtLeast(1f)
        ),
        columns = 9,
        rows = 3
    )?.average()?.toFloat()
}

@Composable
fun HomeReadableText(
    text: String,
    modifier: Modifier = Modifier,
    color: ComposeColor,
    style: TextStyle = LocalTextStyle.current,
    fontWeight: FontWeight? = null,
    fontSize: TextUnit = TextUnit.Unspecified,
    lineHeight: TextUnit = TextUnit.Unspecified,
    textAlign: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
    softWrap: Boolean = true,
    overflow: TextOverflow = TextOverflow.Clip
) {
    val readability = LocalHomeReadability.current
    val backgroundFrozen = LocalHomeBackgroundFrozen.current
    val textContrastFrozen = LocalHomeTextContrastFrozen.current
    val hasWallpaper = readability.config?.hasAnyWallpaper() == true
    var targetShadowStrength by remember(color) { mutableFloatStateOf(0f) }
    val shadowStrength by animateFloatAsState(targetShadowStrength, tween(160), label = "home-text-soft-shadow")
    val textLayout = remember { mutableStateOf<TextLayoutResult?>(null) }
    val coordinates = remember { arrayOfNulls<androidx.compose.ui.layout.LayoutCoordinates>(1) }
    // Reuse each decision until the actual text bounds or wallpaper inputs change.
    val lastSample = remember(readability, color) { arrayOfNulls<Rect>(1) }
    fun updateContrast() {
        if (!hasWallpaper) {
            targetShadowStrength = 0f
            return
        }
        if (backgroundFrozen.value || textContrastFrozen.value) return
        val position = coordinates[0]?.takeIf { it.isAttached } ?: return
        val layout = textLayout.value ?: return
        if (layout.lineCount == 0) return
        val textBounds = Rect(
            (0 until layout.lineCount).minOf { layout.getLineLeft(it) }, layout.getLineTop(0),
            (0 until layout.lineCount).maxOf { layout.getLineRight(it) }, layout.getLineBottom(layout.lineCount - 1)
        )
        val origin = position.localToWindow(Offset.Zero) - readability.rootOffsetInWindow
        val bounds = textBounds.translate(origin)
        if (lastSample[0] == bounds) return
        lastSample[0] = bounds
        // Check short spans independently: one dark character must not be hidden by a bright line.
        var requiredStrength = 0f
        for (line in 0 until layout.lineCount) {
            val left = layout.getLineLeft(line)
            val right = layout.getLineRight(line)
            val top = layout.getLineTop(line)
            val bottom = layout.getLineBottom(line)
            val spans = ceil((right - left) / ((bottom - top) * 0.75f).coerceAtLeast(1f)).toInt().coerceIn(1, 32)
            for (span in 0 until spans) {
                val region = Rect(left + (right - left) * span / spans, top,
                    left + (right - left) * (span + 1) / spans, bottom).translate(origin)
                val samples = sampleVisibleWallpaperLuminances(readability, region, columns = 3, rows = 5) ?: continue
                requiredStrength = maxOf(requiredStrength,
                    homeTextShadowStrength(samples, color.luminance(), color.alpha, targetShadowStrength))
            }
        }
        targetShadowStrength = requiredStrength
    }
    val currentUpdateContrast by rememberUpdatedState(::updateContrast)
    LaunchedEffect(readability, color, backgroundFrozen, textContrastFrozen, hasWallpaper) {
        snapshotFlow { backgroundFrozen.value || textContrastFrozen.value }.collect { frozen ->
            if (!frozen) currentUpdateContrast()
        }
    }
    val density = LocalDensity.current
    val lightText = color.luminance() >= 0.5f
    val effectiveFontSize = when {
        fontSize != TextUnit.Unspecified -> fontSize
        style.fontSize != TextUnit.Unspecified -> style.fontSize
        else -> 14.sp
    }
    val shadowStyle = if (!hasWallpaper || shadowStrength <= 0.001f || readability.bitmap == null) style else {
        val radius = with(density) {
            (effectiveFontSize.toPx() * if (lightText) 0.50f else 0.54f)
                .coerceIn(5.dp.toPx(), 12.dp.toPx())
        }
        style.copy(shadow = androidx.compose.ui.graphics.Shadow(
            color = (if (lightText) ComposeColor.Black else ComposeColor.White).copy(
                alpha = (if (lightText) 0.62f else 0.68f) * shadowStrength
            ),
            offset = Offset.Zero,
            blurRadius = radius
        ))
    }
    // Updating contrast/shadow must not replace Text's layout callback and invalidate its
    // paragraph cache. Only real text, typography or constraints should request remeasurement.
    val currentPositioned = rememberUpdatedState<(androidx.compose.ui.layout.LayoutCoordinates) -> Unit> {
        coordinates[0] = it
        updateContrast()
    }
    val currentTextLayout = rememberUpdatedState<(TextLayoutResult) -> Unit> {
        textLayout.value = it
        updateContrast()
    }
    val onPositioned = remember {
        { value: androidx.compose.ui.layout.LayoutCoordinates -> currentPositioned.value(value) }
    }
    val onLayout = remember { { value: TextLayoutResult -> currentTextLayout.value(value) } }
    Box(modifier = modifier.homeCountdownShockwave(0.55f)) {
        Text(
            text = text,
            modifier = Modifier.onGloballyPositioned(onPositioned),
            color = color,
            style = shadowStyle,
            fontWeight = fontWeight,
            fontSize = fontSize,
            lineHeight = lineHeight,
            textAlign = textAlign,
            maxLines = maxLines,
            softWrap = softWrap,
            overflow = overflow,
            onTextLayout = onLayout
        )
    }
}

@Composable
fun homeForegroundColor(config: ScheduleConfigEntity): ComposeColor {
    if (config.wallpaperUri.isNullOrBlank()) return MaterialTheme.colorScheme.onBackground
    return LocalAdaptiveGlass.current.contentColor
}

fun readableOn(background: ComposeColor): ComposeColor {
    val alpha = background.alpha.coerceIn(0f, 1f)
    val blended = if (alpha < 1f) {
        val fallback = if (background.luminance() < 0.5f) ComposeColor.Black else ComposeColor.White
        blendOver(background, fallback)
    } else {
        background
    }
    return if (blended.luminance() < 0.54f) ComposeColor.White else ComposeColor.Black
}

fun deepenColor(color: ComposeColor, amount: Float = 0.18f): ComposeColor {
    val mix = amount.coerceIn(0f, 1f)
    return ComposeColor(
        red = color.red * (1f - mix),
        green = color.green * (1f - mix),
        blue = color.blue * (1f - mix),
        alpha = color.alpha
    )
}

fun blendOver(foreground: ComposeColor, background: ComposeColor): ComposeColor {
    val alpha = foreground.alpha.coerceIn(0f, 1f)
    return ComposeColor(
        red = foreground.red * alpha + background.red * (1f - alpha),
        green = foreground.green * alpha + background.green * (1f - alpha),
        blue = foreground.blue * alpha + background.blue * (1f - alpha),
        alpha = 1f
    )
}

fun ComposeColor.luminance(): Float {
    fun channel(value: Float): Float = if (value <= 0.03928f) value / 12.92f else ((value + 0.055f) / 1.055f).toDouble().pow(2.4).toFloat()
    return 0.2126f * channel(red) + 0.7152f * channel(green) + 0.0722f * channel(blue)
}
