package com.xiaomanjun.sleepdownschedule.feature.importing

import org.junit.Assert.*
import org.junit.Test
import java.util.Locale

class WebCompatDelegateTest {
    @Test fun actualWebViewWidthWinsOverScreenConfiguration() {
        assertEquals("width=1280.0000, initial-scale=0.3000", desktopViewportContent(1152, 3f, 800))
        assertEquals("width=1280.0000, initial-scale=0.1500", desktopViewportContent(576, 3f, 800))
    }

    @Test fun beforeLayoutUsesTheWindowConfigurationWidth() {
        assertEquals("width=1280.0000, initial-scale=0.3000", desktopViewportContent(0, 3f, 384))
        assertEquals("width=1280.0000, initial-scale=0.3000", desktopViewportContent(1152, Float.NaN, 384))
    }

    @Test fun largeWindowsNeverShrinkBelowTheirOwnCssWidth() {
        assertEquals("width=1600.0000, initial-scale=1.0000", desktopViewportContent(3200, 2f, 600))
    }

    @Test fun scaleAndLayoutWidthStayConsistentAcrossWindowSizes() {
        for (width in listOf(480, 720, 1080, 1256, 1920, 3200, 3840)) {
            for (density in listOf(1f, 2f, 2.75f, 3.5f)) {
                val values = desktopViewportContent(width, density, 999).split(", ")
                    .map { it.substringAfter('=').toDouble() }
                assertTrue(values[0] >= 1280.0)
                assertEquals(width / density.toDouble() / values[0], values[1], 0.000051)
            }
        }
    }

    @Test fun decimalFormattingDoesNotFollowTheDevicesLocale() {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            assertEquals("width=1280.0000, initial-scale=0.3000", desktopViewportContent(1152, 3f, 384))
        } finally {
            Locale.setDefault(original)
        }
    }
}
