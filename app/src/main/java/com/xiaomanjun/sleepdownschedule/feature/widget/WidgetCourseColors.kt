package com.xiaomanjun.sleepdownschedule.feature.widget

import com.xiaomanjun.sleepdownschedule.glass.ui.*

import com.xiaomanjun.sleepdownschedule.core.wallpaper.*

import com.xiaomanjun.sleepdownschedule.*

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
        if (!courseCardUsesAssignments(state.config)) return emptyMap()
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
            state.config.courseCardColorMode,
            state.config.cardColorArgb,
            state.config.courseCardPalette,
            darkMode,
            courseSignature
        ).joinToString("|")
        synchronized(cache) { cache[key]?.let { return it } }

        val explicitNoWallpaper = state.config.wallpaperUri.isNullOrBlank() &&
            state.config.defaultWallpaperStyle == DefaultWallpaperStyle.NONE
        val needsWallpaperPalette = state.config.courseCardColorMode == CourseCardColorMode.COLORFUL &&
            decodeCourseCardPalette(state.config.courseCardPalette).isEmpty() &&
            !explicitNoWallpaper
        val representativeColors = if (needsWallpaperPalette) {
            val source = loadWallpaperBitmap(context, state.config, darkMode)
            try {
                source?.let(::extractRepresentativeWallpaperColors) ?: DefaultCourseCardPalette
            } finally {
                source?.recycle()
            }
        } else {
            emptyList()
        }
        val resolvedPalette = resolvedCourseCardPalette(state.config, representativeColors)
        val resolved = buildCourseCardColorAssignments(
            state.courses,
            resolvedPalette,
            tonalFamily = state.config.courseCardColorMode == CourseCardColorMode.GRADIENT
        )
            .mapValues { (_, color) -> color.toInt() }
        synchronized(cache) { cache[key] = resolved }
        return resolved
    }

    fun color(
        config: ScheduleConfigEntity,
        course: CourseEntity,
        assignments: Map<String, Int>
    ): Int {
        if (!courseCardUsesAssignments(config)) return config.cardColorArgb.toInt()
        courseCardColorOverrideForMode(config, course)?.let { return it.toInt() }
        val key = courseCardColorKey(course)
        return assignments[key]
            ?: resolvedCourseCardPalette(config, emptyList())[
                (key.hashCode() and Int.MAX_VALUE) % resolvedCourseCardPalette(config, emptyList()).size
            ].toInt()
    }
}

internal fun readableWidgetTextColor(background: Int): Int =
    if (WidgetBackgroundRenderer.relativeLuminance(background) < 0.46) {
        android.graphics.Color.WHITE
    } else {
        android.graphics.Color.rgb(17, 17, 17)
    }
