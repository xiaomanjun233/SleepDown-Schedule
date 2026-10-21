package com.xiaomanjun.sleepdownschedule.glass.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import com.xiaomanjun.sleepdownschedule.CourseCardColorMode
import com.xiaomanjun.sleepdownschedule.CourseEntity
import com.xiaomanjun.sleepdownschedule.ScheduleConfigEntity
import com.xiaomanjun.sleepdownschedule.feature.home.day.hasAnyWallpaper
import kotlin.math.roundToInt
import kotlin.math.sqrt

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

fun encodeCourseCardPalette(colors: List<Long>): String = colors.asSequence()
    .map { it and 0xFFFFFFFFL }
    .filter { it ushr 24 != 0L }
    .distinct()
    .take(8)
    .joinToString(",") { it.toString(16).uppercase().padStart(8, '0') }

fun decodeCourseCardPalette(value: String): List<Long> = value
    .split(',')
    .asSequence()
    .mapNotNull { token ->
        token.trim().removePrefix("#").takeIf { it.length in 6..8 }
            ?.toLongOrNull(16)
            ?.let { parsed -> if (token.trim().removePrefix("#").length == 6) parsed or 0xFF000000L else parsed }
    }
    .map { it and 0xFFFFFFFFL }
    .filter { it ushr 24 != 0L }
    .distinct()
    .take(8)
    .toList()

fun courseCardUsesAssignments(config: ScheduleConfigEntity): Boolean =
    config.courseCardColorMode != CourseCardColorMode.SOLID

fun courseCardAllowsCustomOverrides(config: ScheduleConfigEntity): Boolean =
    config.courseCardColorMode == CourseCardColorMode.COLORFUL

fun resolvedCourseCardPalette(
    config: ScheduleConfigEntity,
    wallpaperColors: List<Long>
): List<Long> = when (config.courseCardColorMode) {
    CourseCardColorMode.SOLID,
    CourseCardColorMode.GRADIENT -> {
        listOf(
            config.cardColorArgb.takeUnless { it == MulticolorCourseCardArgb } ?: 0xFFD6E9FFL
        )
    }
    CourseCardColorMode.COLORFUL -> decodeCourseCardPalette(config.courseCardPalette)
        .ifEmpty {
            if (config.hasAnyWallpaper()) wallpaperColors else DefaultCourseCardPalette
        }
        .ifEmpty { DefaultCourseCardPalette }
}

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
internal data class CourseCardHsv(val hue: Float, val saturation: Float, val value: Float)

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
    representativeColors: List<Long>,
    tonalFamily: Boolean = representativeColors.size == 1
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
                    saturation = if (tonalFamily) {
                        hsv.saturation.coerceIn(0.36f, 0.74f)
                    } else {
                        hsv.saturation.coerceIn(0.34f, 0.70f)
                    },
                    value = if (tonalFamily) {
                        hsv.value.coerceIn(0.74f, 0.92f)
                    } else {
                        hsv.value.coerceIn(0.82f, 0.95f)
                    }
                )
            )
        }
        .distinct()
        .ifEmpty { DefaultCourseCardPalette }

    val requiredCandidates = maxOf(36, keys.size * 4)
    val candidates = ArrayList<Long>(requiredCandidates)
    var generation = 0
    val hueOffsets = if (tonalFamily) {
        // Tonal mode must remain recognisably inside one colour family. The tiny offsets only
        // prevent large schedules from exhausting otherwise-identical candidates.
        floatArrayOf(0f, -1f, 1f, -2f, 2f)
    } else {
        floatArrayOf(0f, -9f, 9f, -18f, 18f, -28f, 28f)
    }
    val saturationSteps = if (tonalFamily) {
        floatArrayOf(0f, -0.18f, 0.16f, -0.28f, 0.24f)
    } else {
        floatArrayOf(0f, -0.10f, 0.08f, -0.17f, 0.14f)
    }
    val valueSteps = if (tonalFamily) {
        // Tonal mode should read as an actual light-to-dark family even through translucent
        // glass.  Value is deliberately the fastest-changing axis so a normal six-course week
        // does not need dozens of generated candidates before any visible contrast appears.
        floatArrayOf(0f, 0.16f, -0.16f, 0.25f, -0.28f, 0.08f, -0.36f)
    } else {
        floatArrayOf(0f, 0.07f, -0.05f, 0.11f, -0.09f)
    }
    fun generatedCandidate(generation: Int): Long {
        val baseIndex = generation % bases.size
        val cycle = generation / bases.size
        val baseHsv = courseCardHsv(bases[baseIndex])
        if (cycle == 0) return bases[baseIndex]

        val hueIndex: Int
        val saturationIndex: Int
        val valueIndex: Int
        if (tonalFamily) {
            valueIndex = cycle % valueSteps.size
            saturationIndex = (cycle / valueSteps.size) % saturationSteps.size
            hueIndex = (cycle / (valueSteps.size * saturationSteps.size)) % hueOffsets.size
        } else {
            hueIndex = cycle % hueOffsets.size
            saturationIndex = (cycle / hueOffsets.size) % saturationSteps.size
            valueIndex =
                (cycle / (hueOffsets.size * saturationSteps.size)) % valueSteps.size
        }
        return courseCardArgb(
            baseHsv.copy(
                // Keep generated courses recognisably inside the wallpaper's colour family.
                // The old golden-angle rotation could turn a blue wallpaper green or purple.
                hue = (baseHsv.hue + hueOffsets[hueIndex] + 360f) % 360f,
                saturation = if (tonalFamily && valueSteps[valueIndex] < 0f) {
                    // A deep member is a richer form of the seed hue, never a low-value grey
                    // that reads as black through the glass tint.
                    (baseHsv.saturation + kotlin.math.abs(valueSteps[valueIndex]) * 0.42f)
                        .coerceIn(0.52f, 0.88f)
                } else {
                    (baseHsv.saturation + saturationSteps[saturationIndex])
                        .coerceIn(if (tonalFamily) 0.28f else 0.30f, if (tonalFamily) 0.86f else 0.74f)
                },
                value = (baseHsv.value + valueSteps[valueIndex])
                    .coerceIn(if (tonalFamily) 0.62f else 0.80f, if (tonalFamily) 0.99f else 0.97f)
            )
        )
    }
    while (candidates.size < requiredCandidates && generation < requiredCandidates * 16) {
        val candidate = generatedCandidate(generation)
        if (candidates.none { courseCardPerceptualDistance(it, candidate) < 0.022 }) {
            candidates += candidate
        }
        generation++
    }
    if (representativeColors.isEmpty()) {
        DefaultCourseCardPalette.forEach { fallback ->
            if (candidates.size < requiredCandidates && candidates.none {
                    courseCardPerceptualDistance(it, fallback) < 0.022
                }
            ) {
                candidates += fallback
            }
        }
    }

    // Keep the strict perceptual threshold for the normal case, but never let it make an
    // assignment map incomplete.  A very saturated or very pale seed can collapse many tonal
    // variants onto the same Oklab bucket; the old `getValue` below then crashed every startup
    // after that gradient preference had been persisted.
    if (candidates.size < keys.size) {
        val relaxedCandidateLimit = maxOf(
            keys.size * 32,
            bases.size * (1 + hueOffsets.size * saturationSteps.size * valueSteps.size)
        )
        var relaxedGeneration = 0
        while (candidates.size < keys.size && relaxedGeneration < relaxedCandidateLimit) {
            val candidate = generatedCandidate(relaxedGeneration++)
            if (candidates.none { it == candidate }) {
                candidates += candidate
            }
        }
    }

    // The relaxed generator above covers all normal palettes.  This last lattice is deliberately
    // bounded to the same hue family and only exists as a data-safety guard for malformed or
    // future palette inputs, so a saved appearance preference can never boot-loop the app.
    var emergencyGeneration = 0
    while (candidates.size < keys.size && emergencyGeneration < keys.size * 128) {
        val base = courseCardHsv(bases[emergencyGeneration % bases.size])
        val rung = emergencyGeneration / bases.size
        val hueBand = (rung % 17 - 8).toFloat() * if (tonalFamily) 1.5f else 7f
        val saturationBand = (rung / 17 % 17).toFloat() / 16f
        val valueBand = (rung / (17 * 17) % 17).toFloat() / 16f
        val candidate = courseCardArgb(
            base.copy(
                hue = (base.hue + hueBand + 360f) % 360f,
                saturation = (if (tonalFamily) 0.38f + saturationBand * 0.48f else 0.30f + saturationBand * 0.44f)
                    .coerceIn(0f, 1f),
                value = (if (tonalFamily) 0.62f + valueBand * 0.37f else 0.80f + valueBand * 0.17f)
                    .coerceIn(0f, 1f)
            )
        )
        if (candidates.none { it == candidate }) {
            candidates += candidate
        }
        emergencyGeneration++
    }

    // Assignment must be total even if a malformed seed palette cannot yield enough distinct
    // candidates.  Reusing a safe family member is preferable to losing a persisted course key
    // and crashing the whole schedule while Compose is measuring the first frame.
    val lastResortCandidates = candidates.ifEmpty { bases }
    val available = candidates.toMutableList()
    val assignedByKey = linkedMapOf<String, Long>()
    val allocationOrder = keys.sortedWith(compareByDescending<String> { adjacentKeys[it].orEmpty().size }.thenBy { it })
    allocationOrder.forEachIndexed { index, key ->
        if (available.isEmpty()) {
            assignedByKey[key] =
                lastResortCandidates[(key.hashCode() and Int.MAX_VALUE) % lastResortCandidates.size]
            return@forEachIndexed
        }
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
    return keys.associateWith { key ->
        assignedByKey[key]
            ?: lastResortCandidates[(key.hashCode() and Int.MAX_VALUE) % lastResortCandidates.size]
    }
}
@Composable
fun courseCardBaseColor(config: ScheduleConfigEntity, course: CourseEntity? = null): Color {
    if (!courseCardUsesAssignments(config)) {
        return Color(
            config.cardColorArgb.takeUnless { it == MulticolorCourseCardArgb }
                ?.toInt()
                ?: 0xFFD6E9FF.toInt()
        )
    }
    courseCardColorOverrideForMode(config, course)?.let { return Color(it.toInt()) }
    val palette = LocalCourseCardPalette.current.ifEmpty { DefaultCourseCardPalette }
    val stableKey = course?.let(::courseCardColorKey) ?: "default"
    LocalCourseCardColorAssignments.current[stableKey]?.let { return Color(it.toInt()) }
    return Color(palette[(stableKey.hashCode() and Int.MAX_VALUE) % palette.size].toInt())
}

internal fun courseCardColorOverrideForMode(
    config: ScheduleConfigEntity,
    course: CourseEntity?
): Long? = course?.customColorArgb?.takeIf {
    courseCardAllowsCustomOverrides(config)
}

fun courseCardTonalPreview(seed: Long): List<Long> {
    val base = courseCardHsv(seed)
    return listOf(
        courseCardArgb(base.copy(
            saturation = (base.saturation - 0.22f).coerceIn(0.18f, 0.56f),
            value = (base.value + 0.22f).coerceIn(0.90f, 0.99f)
        )),
        courseCardArgb(base.copy(
            saturation = base.saturation.coerceIn(0.36f, 0.74f),
            value = base.value.coerceIn(0.74f, 0.92f)
        )),
        courseCardArgb(base.copy(
            saturation = (base.saturation + 0.24f).coerceIn(0.56f, 0.88f),
            value = (base.value - 0.30f).coerceIn(0.62f, 0.70f)
        ))
    )
}
