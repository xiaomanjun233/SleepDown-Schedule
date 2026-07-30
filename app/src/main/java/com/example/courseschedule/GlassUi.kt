package com.example.courseschedule

import android.os.Build
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow
import com.kyant.backdrop.shadow.InnerShadow
import kotlin.math.roundToInt

const val MulticolorCourseCardArgb = 0x00000000L

val DefaultCourseCardPalette = listOf(
    0xFF64B5F6L,
    0xFFF48FB1L,
    0xFF81C784L,
    0xFFFFD166L,
    0xFFB39DDBL,
    0xFF4DD0E1L
)

val LocalCourseCardPalette = compositionLocalOf { DefaultCourseCardPalette }
val LocalCourseCardColorAssignments = compositionLocalOf<Map<String, Long>> { emptyMap() }

fun courseCardColorKey(course: CourseEntity): String =
    course.name.trim().lowercase().ifBlank { "course:${course.id}" }


private fun courseCardHueDistance(first: Float, second: Float): Float {
    val raw = kotlin.math.abs(first - second) % 360f
    return minOf(raw, 360f - raw)
}

/** Oklab protects overall separation; this also rewards saturation/value contrast. */
internal fun courseCardAppearanceDistance(first: Long, second: Long): Double {
    val a = courseCardHsv(first)
    val b = courseCardHsv(second)
    val hue = courseCardHueDistance(a.hue, b.hue) / 180f
    val saturation = kotlin.math.abs(a.saturation - b.saturation)
    val value = kotlin.math.abs(a.value - b.value)
    return kotlin.math.sqrt(
        (hue * 0.72f) * (hue * 0.72f) +
            (saturation * 0.92f) * (saturation * 0.92f) +
            (value * 1.15f) * (value * 1.15f)
    ).toDouble()
}
private data class CourseCardHsv(val hue: Float, val saturation: Float, val value: Float)

private fun courseCardHsv(argb: Long): CourseCardHsv {
    val red = ((argb shr 16) and 0xFF).toFloat() / 255f
    val green = ((argb shr 8) and 0xFF).toFloat() / 255f
    val blue = (argb and 0xFF).toFloat() / 255f
    val max = maxOf(red, green, blue)
    val min = minOf(red, green, blue)
    val delta = max - min
    val hue = when {
        delta == 0f -> 0f
        max == red -> 60f * (((green - blue) / delta) % 6f)
        max == green -> 60f * ((blue - red) / delta + 2f)
        else -> 60f * ((red - green) / delta + 4f)
    }.let { if (it < 0f) it + 360f else it }
    return CourseCardHsv(hue, if (max == 0f) 0f else delta / max, max)
}

private fun courseCardArgb(hsv: CourseCardHsv): Long {
    val hue = ((hsv.hue % 360f) + 360f) % 360f
    val saturation = hsv.saturation.coerceIn(0f, 1f)
    val value = hsv.value.coerceIn(0f, 1f)
    val chroma = value * saturation
    val x = chroma * (1f - kotlin.math.abs((hue / 60f) % 2f - 1f))
    val m = value - chroma
    val (r1, g1, b1) = when (hue) {
        in 0f..<60f -> Triple(chroma, x, 0f)
        in 60f..<120f -> Triple(x, chroma, 0f)
        in 120f..<180f -> Triple(0f, chroma, x)
        in 180f..<240f -> Triple(0f, x, chroma)
        in 240f..<300f -> Triple(x, 0f, chroma)
        else -> Triple(chroma, 0f, x)
    }
    fun channel(value: Float) = ((value + m) * 255f).roundToInt().coerceIn(0, 255)
    return (0xFF000000L or (channel(r1).toLong() shl 16) or
        (channel(g1).toLong() shl 8) or channel(b1).toLong())
}

/** Oklab distance is stable for both muted wallpaper colors and highly saturated fallbacks. */
internal fun courseCardPerceptualDistance(first: Long, second: Long): Double {
    fun linear(channel: Long): Double {
        val value = channel.toDouble() / 255.0
        return if (value <= 0.04045) value / 12.92 else Math.pow((value + 0.055) / 1.055, 2.4)
    }
    fun lab(color: Long): DoubleArray {
        val red = linear((color shr 16) and 0xFF)
        val green = linear((color shr 8) and 0xFF)
        val blue = linear(color and 0xFF)
        val l = Math.cbrt(0.4122214708 * red + 0.5363325363 * green + 0.0514459929 * blue)
        val m = Math.cbrt(0.2119034982 * red + 0.6806995451 * green + 0.1073969566 * blue)
        val s = Math.cbrt(0.0883024619 * red + 0.2817188376 * green + 0.6299787005 * blue)
        return doubleArrayOf(
            0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s,
            1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s,
            0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s
        )
    }
    val a = lab(first)
    val b = lab(second)
    return kotlin.math.sqrt(
        (a[0] - b[0]) * (a[0] - b[0]) +
            (a[1] - b[1]) * (a[1] - b[1]) +
            (a[2] - b[2]) * (a[2] - b[2])
    )
}

fun buildCourseCardColorAssignments(
    courses: List<CourseEntity>,
    representativeColors: List<Long>
): Map<String, Long> {
    val keys = courses.map(::courseCardColorKey).distinct().sorted()
    val coursesByKey = courses.groupBy(::courseCardColorKey)
    fun areCardsLikelyAdjacent(firstKey: String, secondKey: String): Boolean {
        return coursesByKey[firstKey].orEmpty().any { first ->
            coursesByKey[secondKey].orEmpty().any { second ->
                val firstStart = first.periods.minOrNull() ?: return@any false
                val firstEnd = first.periods.maxOrNull() ?: return@any false
                val secondStart = second.periods.minOrNull() ?: return@any false
                val secondEnd = second.periods.maxOrNull() ?: return@any false
                val periodGap = maxOf(firstStart, secondStart) - minOf(firstEnd, secondEnd) - 1
                when (kotlin.math.abs(first.weekday - second.weekday)) {
                    0 -> periodGap <= 1
                    1 -> periodGap <= 0
                    else -> false
                }
            }
        }
    }
    val adjacentKeys = keys.associateWith { key ->
        keys.asSequence().filter { it != key && areCardsLikelyAdjacent(key, it) }.toSet()
    }
    if (keys.isEmpty()) return emptyMap()
    val bases = representativeColors.ifEmpty { DefaultCourseCardPalette }
        .map { color ->
            val hsv = courseCardHsv(color)
            courseCardArgb(
                hsv.copy(
                    saturation = hsv.saturation.coerceIn(0.38f, 0.82f),
                    value = hsv.value.coerceIn(0.74f, 0.94f)
                )
            )
        }
        .distinct()
        .ifEmpty { DefaultCourseCardPalette }

    val requiredCandidates = maxOf(36, keys.size * 4)
    val candidates = ArrayList<Long>(requiredCandidates)
    var generation = 0
    while (candidates.size < requiredCandidates && generation < requiredCandidates * 4) {
        val baseIndex = generation % bases.size
        val cycle = generation / bases.size
        val baseHsv = courseCardHsv(bases[baseIndex])
        val saturationSteps = floatArrayOf(-0.22f, 0.18f, -0.10f, 0.28f, 0f)
        val valueSteps = floatArrayOf(0.10f, -0.12f, 0.04f, -0.06f, 0f)
        val candidate = if (cycle == 0) {
            bases[baseIndex]
        } else {
            courseCardArgb(
                baseHsv.copy(
                    hue = (baseHsv.hue + cycle * 137.50776f + baseIndex * 11f) % 360f,
                    saturation = (baseHsv.saturation + saturationSteps[(cycle - 1) % saturationSteps.size]).coerceIn(0.34f, 0.88f),
                    value = (baseHsv.value + valueSteps[(cycle - 1) % valueSteps.size]).coerceIn(0.68f, 0.96f)
                )
            )
        }
        if (candidates.none { courseCardPerceptualDistance(it, candidate) < 0.035 }) {
            candidates += candidate
        }
        generation++
    }
    DefaultCourseCardPalette.forEach { fallback ->
        if (candidates.size < requiredCandidates && candidates.none { courseCardPerceptualDistance(it, fallback) < 0.035 }) {
            candidates += fallback
        }
    }

    val available = candidates.toMutableList()
    val assignedByKey = linkedMapOf<String, Long>()
    val allocationOrder = keys.sortedWith(compareByDescending<String> { adjacentKeys[it].orEmpty().size }.thenBy { it })
    allocationOrder.forEachIndexed { index, key ->
        if (available.isEmpty()) return@forEachIndexed
        val preferred = (key.hashCode() and Int.MAX_VALUE) % available.size
        val selectedIndex = if (assignedByKey.isEmpty()) {
            preferred
        } else {
            available.indices.maxByOrNull { candidateIndex ->
                val candidate = available[candidateIndex]
                val separation = assignedByKey.values.minOf { courseCardPerceptualDistance(it, candidate) }
                val adjacentAssigned = adjacentKeys[key].orEmpty().mapNotNull(assignedByKey::get)
                val adjacentAppearance = adjacentAssigned.minOfOrNull { courseCardAppearanceDistance(it, candidate) } ?: 1.0
                val sameFamilyPenalty = adjacentAssigned.count { neighbor ->
                    val a = courseCardHsv(neighbor)
                    val b = courseCardHsv(candidate)
                    courseCardHueDistance(a.hue, b.hue) < 42f &&
                        kotlin.math.abs(a.saturation - b.saturation) < 0.14f &&
                        kotlin.math.abs(a.value - b.value) < 0.12f
                }
                val preferenceDistance = kotlin.math.abs(candidateIndex - preferred).toDouble() / available.size
                separation + adjacentAppearance * 0.18 - sameFamilyPenalty * 0.20 -
                    preferenceDistance * 0.004 - index * 0.0000001
            } ?: 0
        }
        assignedByKey[key] = available.removeAt(selectedIndex)
    }
    return keys.associateWith { assignedByKey.getValue(it) }
}
@Composable
fun courseCardBaseColor(config: ScheduleConfigEntity, course: CourseEntity? = null): Color {
    if (config.cardColorArgb != MulticolorCourseCardArgb) return Color(config.cardColorArgb.toInt())
    val palette = LocalCourseCardPalette.current.ifEmpty { DefaultCourseCardPalette }
    val stableKey = course?.let(::courseCardColorKey) ?: "default"
    LocalCourseCardColorAssignments.current[stableKey]?.let { return Color(it.toInt()) }
    return Color(palette[(stableKey.hashCode() and Int.MAX_VALUE) % palette.size].toInt())
}

data class GlassTokens(
    val blur: Dp,
    val lensHeight: Dp,
    val lensAmount: Dp,
    val surfaceAlpha: Float,
    val borderAlpha: Float,
    val highlightAlpha: Float = 0.06f,
    val shadowAlpha: Float = 0.16f,
    val innerShadowAlpha: Float = 0.12f,
    val chromaticAberration: Boolean = false,
    val depthEffect: Boolean = true,
    val useVibrancy: Boolean = true
) {
    companion object {
        fun pill(intensity: Float = 1f, reduceTransparency: Boolean = false) = GlassTokens(
            blur = if (reduceTransparency) 0.dp else (2.5f * intensity.coerceIn(0.4f, 1.5f)).dp,
            lensHeight = if (reduceTransparency) 0.dp else (12f * intensity.coerceIn(0.4f, 1.5f)).dp,
            lensAmount = if (reduceTransparency) 0.dp else (24f * intensity.coerceIn(0.4f, 1.5f)).dp,
            surfaceAlpha = if (reduceTransparency) 0.86f else 0.18f,
            borderAlpha = if (reduceTransparency) 0.18f else 0.32f,
            highlightAlpha = if (reduceTransparency) 0.04f else 0.055f,
            shadowAlpha = if (reduceTransparency) 0.08f else 0.14f,
            innerShadowAlpha = if (reduceTransparency) 0.05f else 0.09f
        )

        fun dialog(intensity: Float = 1f, reduceTransparency: Boolean = false) = GlassTokens(
            blur = if (reduceTransparency) 0.dp else (4f * intensity.coerceIn(0.4f, 1.5f)).dp,
            lensHeight = if (reduceTransparency) 0.dp else (16f * intensity.coerceIn(0.4f, 1.5f)).dp,
            lensAmount = if (reduceTransparency) 0.dp else (32f * intensity.coerceIn(0.4f, 1.5f)).dp,
            surfaceAlpha = if (reduceTransparency) 0.92f else 0.40f,
            borderAlpha = if (reduceTransparency) 0.16f else 0.28f,
            highlightAlpha = if (reduceTransparency) 0.04f else 0.06f,
            shadowAlpha = if (reduceTransparency) 0.08f else 0.18f,
            innerShadowAlpha = if (reduceTransparency) 0.05f else 0.11f
        )

        fun courseCard(blur: Float, reduceTransparency: Boolean = false) = GlassTokens(
            blur = if (reduceTransparency) 0.dp else blur.coerceIn(0f, 10f).dp,
            lensHeight = if (reduceTransparency) 0.dp else 10.dp,
            lensAmount = if (reduceTransparency) 0.dp else 20.dp,
            surfaceAlpha = if (reduceTransparency) 0.92f else 0.52f,
            borderAlpha = if (reduceTransparency) 0.14f else 0.24f,
            highlightAlpha = if (reduceTransparency) 0.035f else 0.045f,
            shadowAlpha = if (reduceTransparency) 0.08f else 0.14f,
            innerShadowAlpha = if (reduceTransparency) 0.05f else 0.10f
        )
    }
}

@Composable
fun appUsesDarkTheme(config: ScheduleConfigEntity): Boolean {
    val systemDark = isSystemInDarkTheme()
    return if (config.followSystemDarkMode) systemDark else config.darkMode
}

@Composable
fun glassUsesLightStyle(config: ScheduleConfigEntity): Boolean {
    if (config.wallpaperUri.isNullOrBlank()) return !appUsesDarkTheme(config)
    return when {
        config.wallpaperBrightness < 0.72f -> false
        config.homeTextLight -> false
        else -> true
    }
}

@Composable
fun GlassSurface(
    backdrop: Backdrop?,
    config: ScheduleConfigEntity,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(50),
    tokens: GlassTokens = GlassTokens.pill(),
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
    baseSurfaceColorOverride: Color? = null,
    content: @Composable () -> Unit
) {
    val glassBackdrop = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) backdrop else null
    val useGlass = glassBackdrop != null
    val quality = LocalGlassQuality.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressProgress by animateFloatAsState(if (pressed) 1f else 0f, label = "glass-press")
    val lightGlass = glassUsesLightStyle(config)
    val base = baseSurfaceColorOverride ?: if (lightGlass) Color.White else Color(0xFF050505)
    val selectedColor = if (useGlass) {
        if (lightGlass) Color.Black.copy(alpha = 0.07f) else Color.White.copy(alpha = 0.08f)
    } else {
        MaterialTheme.colorScheme.primaryContainer
    }
    val clearAlpha = tokens.surfaceAlpha * quality
    val surfaceColor = if (selected) selectedColor else base.copy(alpha = clearAlpha)
    val contentModifier = if (useGlass) {
        modifier.drawBackdrop(
            backdrop = glassBackdrop,
            shape = { shape },
            effects = {
                if (tokens.useVibrancy) vibrancy()
                blur((tokens.blur * quality).toPx())
                lens(
                    (tokens.lensHeight * quality).toPx() * (0.7f + 0.3f * pressProgress),
                    (tokens.lensAmount * quality).toPx() * (0.85f + 0.35f * pressProgress),
                    depthEffect = tokens.depthEffect,
                    chromaticAberration = tokens.chromaticAberration
                )
            },
            highlight = {
                val alpha = if (selected) tokens.highlightAlpha + 0.10f * pressProgress else tokens.highlightAlpha * 0.65f * pressProgress
                if (alpha <= 0.001f) Highlight.Plain else Highlight.Default.copy(alpha = alpha)
            },
            shadow = {
                Shadow(alpha = if (selected) tokens.shadowAlpha + 0.12f * pressProgress else tokens.shadowAlpha * pressProgress)
            },
            innerShadow = {
                InnerShadow(
                    radius = if (selected) 6.dp else 3.dp * pressProgress,
                    alpha = if (selected) tokens.innerShadowAlpha + 0.10f * pressProgress else tokens.innerShadowAlpha * pressProgress
                )
            },
            layerBlock = {
                val scale = 1f + 0.055f * pressProgress
                scaleX = scale
                scaleY = scale
            },
            onDrawSurface = {
                drawRect(surfaceColor)
                if (lightGlass) {
                    drawRect(Color.White.copy(alpha = 0.014f + 0.018f * pressProgress), blendMode = BlendMode.Screen)
                } else {
                    drawRect(Color.Black.copy(alpha = 0.014f + 0.018f * pressProgress))
                    drawRect(Color.White.copy(alpha = 0.006f + 0.010f * pressProgress), blendMode = BlendMode.Screen)
                }
            }
        )
    } else {
        modifier
            .clip(shape)
            .background(surfaceColor.copy(alpha = surfaceColor.alpha.coerceAtLeast(0.86f)))
            .graphicsLayer {
                val scale = 1f + 0.04f * pressProgress
                scaleX = scale
                scaleY = scale
            }
    }
        .then(
            if (onClick == null) Modifier else Modifier.clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick
            )
        )

    Box(modifier = contentModifier) {
        content()
    }
}

@Composable
fun GlassPill(
    backdrop: Backdrop?,
    config: ScheduleConfigEntity,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    GlassSurface(
        backdrop = backdrop,
        config = config,
        modifier = modifier,
        shape = RoundedCornerShape(50),
        tokens = GlassTokens.pill(),
        selected = selected,
        onClick = onClick,
        content = content
    )
}

@Composable
fun GlassLens(
    backdrop: Backdrop?,
    config: ScheduleConfigEntity,
    modifier: Modifier = Modifier,
    pressProgress: Float = 1f,
    content: @Composable () -> Unit = {}
) {
    val glassBackdrop = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) backdrop else null
    val useGlass = glassBackdrop != null
    val quality = LocalGlassQuality.current
    val lightGlass = glassUsesLightStyle(config)
    val surfaceColor = if (lightGlass) Color.Black.copy(alpha = 0.07f * quality) else Color.White.copy(alpha = 0.08f * quality)
    val shape = RoundedCornerShape(50)
    val contentModifier = if (useGlass) {
        modifier.drawBackdrop(
            backdrop = glassBackdrop,
            shape = { shape },
            effects = {
                vibrancy()
                blur((3.dp * quality).toPx())
                lens(
                    (8.dp * quality).toPx() + (16.dp * quality).toPx() * pressProgress,
                    (14.dp * quality).toPx() + (20.dp * quality).toPx() * pressProgress,
                    chromaticAberration = false
                )
            },
            highlight = { Highlight.Default.copy(alpha = 0.08f + 0.10f * pressProgress) },
            shadow = { Shadow(alpha = 0.18f + 0.16f * pressProgress) },
            innerShadow = { InnerShadow(radius = 6.dp, alpha = 0.18f + 0.16f * pressProgress) },
            layerBlock = {
                val scale = 1f + 0.04f * pressProgress
                scaleX = scale
                scaleY = scale
            },
            onDrawSurface = {
                drawRect(surfaceColor)
                if (lightGlass) {
                    drawRect(Color.White.copy(alpha = 0.014f), blendMode = BlendMode.Screen)
                } else {
                    drawRect(Color.Black.copy(alpha = 0.014f))
                    drawRect(Color.White.copy(alpha = 0.006f), blendMode = BlendMode.Screen)
                }
            }
        )
    } else {
        modifier.clip(shape).background(MaterialTheme.colorScheme.primaryContainer)
    }
    Box(modifier = contentModifier) {
        content()
    }
}

@Composable
fun GlassDialogSurface(
    backdrop: Backdrop?,
    config: ScheduleConfigEntity,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(26.dp),
    content: @Composable () -> Unit
) {
    GlassSurface(
        backdrop = backdrop,
        config = config,
        modifier = modifier,
        shape = shape,
        tokens = GlassTokens.dialog(),
        content = content
    )
}

@Composable
fun CourseGlassCard(
    backdrop: Backdrop?,
    config: ScheduleConfigEntity,
    modifier: Modifier = Modifier,
    course: CourseEntity? = null,
    shape: Shape = RoundedCornerShape(12.dp),
    blurOverride: Float? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val glassBackdrop = if (config.courseCardGlassEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) backdrop else null
    val simpleBlurBackdrop = if (!config.courseCardGlassEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) backdrop else null
    val useGlass = glassBackdrop != null
    val quality = LocalGlassQuality.current
    val clickInteractionSource = remember { MutableInteractionSource() }
    var pressed by remember { mutableStateOf(false) }
    val baseColor = courseCardBaseColor(config, course)
    val glassTint = baseColor.copy(alpha = ((config.cardAlpha.coerceIn(0f, 1f) * 0.68f) * quality).coerceIn(0f, 0.68f))
    val solidColor = baseColor.copy(alpha = config.cardAlpha.coerceIn(0f, 1f))
    val tokens = GlassTokens.courseCard(blurOverride ?: config.courseCardBlur)
    val lightGlass = glassUsesLightStyle(config)
    val cardModifier = modifier
        .then(
            if (onClick == null) Modifier else Modifier
                .pointerInput(onClick) {
                    awaitEachGesture {
                        awaitFirstDown(
                            requireUnconsumed = false,
                            pass = PointerEventPass.Initial
                        )
                        pressed = true
                        try {
                            waitForUpOrCancellation(pass = PointerEventPass.Initial)
                        } finally {
                            pressed = false
                        }
                    }
                }
                .clickable(
                    interactionSource = clickInteractionSource,
                    indication = null,
                    role = Role.Button,
                    onClick = onClick
                )
        )
    Box(modifier = cardModifier) {
        val surfaceModifier = if (useGlass) {
            Modifier
                .matchParentSize()
                .drawBackdrop(
                        backdrop = glassBackdrop,
                        shape = { shape },
                        effects = {
                            if (tokens.useVibrancy) vibrancy()
                            blur((tokens.blur * quality).toPx())
                            lens(
                                (tokens.lensHeight * quality).toPx(),
                                (tokens.lensAmount * quality).toPx(),
                                depthEffect = tokens.depthEffect,
                                chromaticAberration = tokens.chromaticAberration
                            )
                        },
                        highlight = { Highlight.Default.copy(alpha = tokens.highlightAlpha) },
                        shadow = { Shadow(alpha = tokens.shadowAlpha) },
                        innerShadow = { InnerShadow(radius = 5.dp, alpha = tokens.innerShadowAlpha) },
                        onDrawSurface = {
                            drawRect(glassTint)
                            drawRect(
                                Color.White.copy(alpha = if (lightGlass) 0.012f else 0.008f),
                                blendMode = BlendMode.Screen
                            )
                            drawRect(Color.Black.copy(alpha = if (lightGlass) 0.004f else 0.014f))
                        }
                )
        } else if (simpleBlurBackdrop != null) {
            // Non-liquid mode still samples the content behind the course card, but
            // deliberately omits lens/refraction/vibrancy.  This is a cheap Gaussian
            // material rather than falling all the way back to an opaque rectangle.
            Modifier
                .matchParentSize()
                .drawBackdrop(
                    backdrop = simpleBlurBackdrop,
                    shape = { shape },
                    effects = {
                        blur(((blurOverride ?: config.courseCardBlur).coerceIn(0f, 24f) * quality).dp.toPx())
                    },
                    highlight = { Highlight.Default.copy(alpha = 0.10f) },
                    shadow = { Shadow(alpha = 0.12f) },
                    innerShadow = { InnerShadow(radius = 3.dp, alpha = 0.08f) },
                    onDrawSurface = {
                        drawRect(
                            baseColor.copy(
                                alpha = (config.cardAlpha.coerceIn(0f, 1f) * 0.72f)
                                    .coerceIn(0.28f, 0.78f)
                            )
                        )
                    }
                )
        } else {
            Modifier
                .matchParentSize()
                .clip(shape)
                .background(solidColor.copy(alpha = solidColor.alpha.coerceAtLeast(0.86f)))
        }
        Box(surfaceModifier)
        content()
        if (pressed) {
            Box(
                Modifier
                    .matchParentSize()
                    .clip(shape)
                    .background(Color.Black.copy(alpha = 0.13f))
            )
        }
    }
}
