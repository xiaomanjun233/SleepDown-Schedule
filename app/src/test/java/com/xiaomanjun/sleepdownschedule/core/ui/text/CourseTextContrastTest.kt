package com.xiaomanjun.sleepdownschedule.core.ui.text

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.*
import org.junit.Test

class CourseTextContrastTest {
    private val blue = Color(0xFF64B5F6)

    @Test fun wallpaperLetteringKeepsCourseHueInsteadOfTurningIntoDarkInk() {
        val brightPage = courseTextColorForPage(blue, hasWallpaper = true, lightText = false)
        val darkPage = courseTextColorForPage(blue, hasWallpaper = true, lightText = true)
        assertTrue(brightPage.luminance() > 0.3f)
        assertTrue(darkPage.luminance() > 0.3f)
        assertTrue(darkPage.blue > darkPage.red)
        assertTrue(brightPage.blue > brightPage.red)
        assertTrue(darkPage.luminance() > blue.luminance())
        assertTrue(brightPage.luminance() > blue.luminance())
        assertEquals((blue.green - blue.red) / (blue.blue - blue.red),
            (darkPage.green - darkPage.red) / (darkPage.blue - darkPage.red), 0.01f) // 8-bit sRGB rounding
        val pink = courseTextColorForPage(Color(0xFFF48FB1), hasWallpaper = true, lightText = true)
        assertTrue(pink.red > pink.blue)
        assertNotEquals(darkPage, pink)
    }

    @Test fun flatCardsUseLighterCourseInkAndNeutralSeedsStayNeutral() {
        val flatInk = courseTextColorForPage(blue, hasWallpaper = false, lightText = false)
        assertTrue(flatInk.luminance() > blue.luminance())
        assertTrue(flatInk.blue > flatInk.green && flatInk.green > flatInk.red)
        val neutral = courseTextColorForPage(Color(0xFF444444), hasWallpaper = true, lightText = true)
        assertEquals(neutral.red, neutral.green, 0.001f)
        assertEquals(neutral.green, neutral.blue, 0.001f)
    }

    @Test fun shadowPolarityFollowsTheColoredGlyphAndProtectsLowContrastRegions() {
        val lightBlue = courseTextColorForPage(blue, hasWallpaper = true, lightText = false)
        assertTrue(courseTextNeedsDarkShadow(lightBlue))
        assertFalse(courseTextNeedsDarkShadow(Color(0xFF001199)))
        assertTrue(courseTextNeedsDarkShadow(Color(0xFF888888))) // luminance below 0.5
        assertTrue(softTextShadowStrength(FloatArray(20) { lightBlue.luminance() }, lightBlue.luminance()) > 0f)
    }

    @Test fun monochromeCardShadowProtectsWithoutChangingPolarity() {
        assertTrue(softTextShadowStrength(FloatArray(20) { 0.01f }, 0f) > 0f)
        assertEquals(0f, softTextShadowStrength(FloatArray(20) { 0.9f }, 0f), 0f)
    }
}
