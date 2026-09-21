package com.xiaomanjun.sleepdownschedule.core.ui.text

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.*
import org.junit.Test

class CourseTextContrastTest {
    private val blue = Color(0xFF64B5F6)
    private fun resolve(background: Float, seed: Color = blue, previous: Color = Color.Black) =
        courseTextColorForBackground(seed, FloatArray(35) { background }, previous)
    private fun contrast(color: Color, background: Float): Float =
        (maxOf(color.luminance(), background) + 0.05f) / (minOf(color.luminance(), background) + 0.05f)

    @Test fun brightSurfaceDarkensBlueWhileKeepingItsHue() {
        val result = resolve(0.85f)
        assertTrue(result.luminance() < blue.luminance())
        assertTrue(contrast(result, 0.85f) >= 4.5f)
        assertEquals((blue.green - blue.red) / (blue.blue - blue.red),
            (result.green - result.red) / (result.blue - result.red), 0.001f)
    }

    @Test fun darkSurfaceLiftsDarkBlue() {
        val seed = Color(0xFF264E7A)
        val result = resolve(0.05f, seed)
        assertTrue(result.luminance() > seed.luminance())
        assertTrue(contrast(result, 0.05f) >= 4.5f)
        assertTrue(result.blue > result.green && result.green > result.red)
    }

    @Test fun readableOriginalColorIsPreserved() {
        assertEquals(blue, resolve(0.01f))
    }

    @Test fun midtoneBackgroundChoosesTheMoreReadableDirection() {
        val result = resolve(0.25f, previous = Color.White)
        assertTrue(result.luminance() < 0.18f)
        assertTrue(contrast(result, 0.25f) >= 4.5f)
    }

    @Test fun isolatedBrightPixelDoesNotFlipTheWholeLabel() {
        val samples = FloatArray(35) { 0.01f }.also { it[0] = 1f }
        assertEquals(blue, courseTextColorForBackground(blue, samples, blue))
    }

    @Test fun mutedCourseStaysNeutral() {
        val seed = Color(0xFF999999)
        for (background in listOf(0.02f, 0.4f, 0.9f)) {
            val result = resolve(background, seed)
            assertEquals(result.red, result.green, 0.001f)
            assertEquals(result.green, result.blue, 0.001f)
        }
    }

    @Test fun noValidSamplesKeepsThePreviousColor() {
        val previous = Color(0xFFAACCEE)
        assertEquals(previous, courseTextColorForBackground(blue, floatArrayOf(Float.NaN), previous))
    }
}
