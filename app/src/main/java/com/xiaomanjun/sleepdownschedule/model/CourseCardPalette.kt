package com.xiaomanjun.sleepdownschedule.model

const val MulticolorCourseCardArgb = 0x00000000L

val DefaultCourseCardPalette = listOf(
    0xFF64B5F6L,
    0xFFF48FB1L,
    0xFF81C784L,
    0xFFFFD166L,
    0xFFB39DDBL,
    0xFF4DD0E1L
)

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
