package com.example.courseschedule

import android.content.Context
import java.util.LinkedHashMap

/**
 * Resolves the exact stable course-color assignment used by the in-app day/week cards.
 * Widgets run outside Compose, so they cannot read the CompositionLocals that normally carry
 * the wallpaper palette and assignment map.
 */
internal object WidgetCourseColors {
    private const val MaxCacheEntries = 4
    private val cache = object : LinkedHashMap<String, Map<String, Int>>(MaxCacheEntries, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Map<String, Int>>?): Boolean =
            size > MaxCacheEntries
    }

    fun assignments(
        context: Context,
        state: AppState,
        darkMode: Boolean
    ): Map<String, Int> {
        if (state.config.cardColorArgb != MulticolorCourseCardArgb) return emptyMap()
        val courseSignature = state.courses
            .sortedWith(compareBy<CourseEntity> { courseCardColorKey(it) }.thenBy { it.id })
            .fold(1) { result, course ->
                31 * result + listOf(
                    courseCardColorKey(course),
                    course.weekday,
                    course.periods,
                    course.weeks,
                    course.weekParity
                ).hashCode()
            }
        val key = listOf(
            state.config.id,
            state.config.wallpaperUri,
            state.config.defaultWallpaperStyle,
            darkMode,
            courseSignature
        ).joinToString("|")
        synchronized(cache) { cache[key]?.let { return it } }

        val source = loadWallpaperBitmap(context, state.config, darkMode)
        val representativeColors = try {
            extractRepresentativeWallpaperColors(source)
        } finally {
            source?.recycle()
        }
        val resolved = buildCourseCardColorAssignments(state.courses, representativeColors)
            .mapValues { (_, color) -> color.toInt() }
        synchronized(cache) { cache[key] = resolved }
        return resolved
    }

    fun color(
        config: ScheduleConfigEntity,
        course: CourseEntity,
        assignments: Map<String, Int>
    ): Int {
        if (config.cardColorArgb != MulticolorCourseCardArgb) return config.cardColorArgb.toInt()
        val key = courseCardColorKey(course)
        return assignments[key]
            ?: DefaultCourseCardPalette[(key.hashCode() and Int.MAX_VALUE) % DefaultCourseCardPalette.size].toInt()
    }
}

internal fun readableWidgetTextColor(background: Int): Int =
    if (WidgetBackgroundRenderer.relativeLuminance(background) < 0.46) {
        android.graphics.Color.WHITE
    } else {
        android.graphics.Color.rgb(17, 17, 17)
    }
