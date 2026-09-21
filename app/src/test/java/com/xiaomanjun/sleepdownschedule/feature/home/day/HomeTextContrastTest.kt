package com.xiaomanjun.sleepdownschedule.feature.home.day

import org.junit.Assert.*
import org.junit.Test

class HomeTextContrastTest {
    private fun uniform(value: Float) = FloatArray(35) { value }

    @Test fun blackTextGetsStrongSoftProtectionOnDarkWallpaper() {
        assertTrue(homeTextShadowStrength(uniform(0.015f), 0f) >= 0.875f)
    }

    @Test fun whiteTextGetsStrongSoftProtectionOnBrightWallpaper() {
        assertTrue(homeTextShadowStrength(uniform(0.9f), 1f) >= 0.875f)
    }

    @Test fun readableTextDoesNotGetUnnecessaryShadows() {
        assertEquals(0f, homeTextShadowStrength(uniform(0.9f), 0f), 0f)
        assertEquals(0f, homeTextShadowStrength(uniform(0.015f), 1f), 0f)
    }

    @Test fun hysteresisPreventsTogglingAtTheShadowThreshold() {
        val nearThreshold = uniform(0.20f)
        assertEquals(0f, homeTextShadowStrength(nearThreshold, 0f), 0f)
        assertEquals(0.125f, homeTextShadowStrength(nearThreshold, 0f, currentStrength = 0.125f), 0f)
        assertEquals(0f, homeTextShadowStrength(uniform(0.25f), 0f, currentStrength = 0.125f), 0f)
    }

    @Test fun brightAverageCannotHideADarkStripeUnderPartOfTheText() {
        val striped = FloatArray(35) { if (it < 9) 0.015f else 0.85f }
        assertTrue(homeTextShadowStrength(striped, 0f) >= 0.875f)
        assertEquals(0f, homeTextShadowStrength(uniform(striped.average().toFloat()), 0f), 0f)
    }

    @Test fun isolatedPixelDoesNotAddShadowToOtherwiseReadableText() {
        val samples = uniform(0.9f).also { it[0] = 0f }
        assertEquals(0f, homeTextShadowStrength(samples, 0f), 0f)
    }

    @Test fun translucentSecondaryTextGetsAtLeastAsMuchProtection() {
        val samples = uniform(0.15f)
        assertTrue(homeTextShadowStrength(samples, 0f, 0.7f) >= homeTextShadowStrength(samples, 0f))
    }

    @Test fun missingWallpaperSamplesRemoveTheShadow() {
        assertEquals(0f, homeTextShadowStrength(floatArrayOf(Float.NaN), 0f), 0f)
        assertEquals(0f, homeTextShadowStrength(floatArrayOf(), 1f), 0f)
    }

    @Test fun dimmingAppliesToSrgbBeforeLuminanceConversion() {
        assertEquals(1f, visibleWallpaperLuminance(0xffffffff.toInt(), 1f), 0.0001f)
        assertEquals(0.21404f, visibleWallpaperLuminance(0xffffffff.toInt(), 0.5f), 0.0001f)
        assertEquals(0f, visibleWallpaperLuminance(0xff000000.toInt(), 0.5f), 0.0001f)
        assertTrue(visibleWallpaperLuminance(0xffeeeeee.toInt(), 0.6f) < visibleWallpaperLuminance(0xffeeeeee.toInt(), 1f))
    }
}
