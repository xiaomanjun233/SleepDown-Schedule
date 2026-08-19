package com.xiaomanjun.sleepdownschedule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CourseGlassTintTest {
    @Test
    fun noWallpaperKeepsMoreOfTheSampledBackdropVisible() {
        val wallpaperTint = courseGlassTintAlpha(cardAlpha = 1f, quality = 1f, hasWallpaper = true)
        val fallbackTint = courseGlassTintAlpha(cardAlpha = 1f, quality = 1f, hasWallpaper = false)

        assertEquals(0.68f, wallpaperTint, 0.0001f)
        assertEquals(0.16f, fallbackTint, 0.0001f)
        assertTrue(fallbackTint < wallpaperTint / 2f)
    }

    @Test
    fun noWallpaperTintStillTracksThePersonalizationSlider() {
        assertEquals(
            0.064f,
            courseGlassTintAlpha(cardAlpha = 0.4f, quality = 1f, hasWallpaper = false),
            0.0001f
        )
        assertEquals(
            0f,
            courseSimpleBlurTintAlpha(cardAlpha = 0f, quality = 1f, hasWallpaper = false),
            0.0001f
        )
    }

    @Test
    fun tintFunctionsClampOutOfRangeInputs() {
        assertEquals(0.16f, courseGlassTintAlpha(2f, 2f, hasWallpaper = false), 0.0001f)
        assertEquals(0f, courseGlassTintAlpha(-1f, 1f, hasWallpaper = false), 0.0001f)
        assertEquals(0.18f, courseSimpleBlurTintAlpha(2f, 2f, hasWallpaper = false), 0.0001f)
    }
}
