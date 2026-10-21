package com.xiaomanjun.sleepdownschedule

import com.xiaomanjun.sleepdownschedule.glass.ui.courseGlassTintAlpha
import com.xiaomanjun.sleepdownschedule.glass.ui.courseSimpleBlurTintAlpha
import com.xiaomanjun.sleepdownschedule.glass.ui.courseGlassSurfaceColor
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CourseGlassTintTest {
    @Test fun flattenedSurfacePreservesTintAndNeutralFillsAtAlphaBoundaries() {
        for (light in listOf(false, true)) for (alpha in listOf(0f, 0.5f, 1f)) {
            val tint = Color(0xFF4080C0).copy(alpha = alpha)
            val background = Color(0xFF90B050).copy(alpha = 0.6f)
            val white = Color.White.copy(alpha = if (light) 0.012f else 0.008f)
            val black = Color.Black.copy(alpha = if (light) 0.004f else 0.014f)
            val expected = black.compositeOver(white.compositeOver(tint.compositeOver(background)))
            val actual = courseGlassSurfaceColor(tint, light).compositeOver(background)
            assertEquals(expected.red, actual.red, 0.0001f)
            assertEquals(expected.green, actual.green, 0.0001f)
            assertEquals(expected.blue, actual.blue, 0.0001f)
            assertEquals(expected.alpha, actual.alpha, 0.0001f)
        }
    }

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
