package com.xiaomanjun.sleepdownschedule.feature.importing

import org.junit.Assert.*
import org.junit.Test
import java.util.Locale

class WebCompatDelegateTest {
    @Test fun desktopZoomCorrectsTheUnscaledViewportAndDoesNotRepeatAtTheTarget() {
        val expected = desktopViewportScale(1152, 3f, 800)
        assertEquals(0.3f, expected, 0.00001f)
        assertEquals(0.3f, desktopZoomFactor(1f, expected)!!, 0.00001f)
        assertNull(desktopZoomFactor(0.3f, expected))
        assertNull(desktopZoomFactor(-1f, expected))
        assertNull(desktopZoomFactor(Float.NaN, expected))
        assertNull(desktopZoomFactor(Float.POSITIVE_INFINITY, expected))
        assertEquals(1f, desktopViewportScale(3200, 2f, 800), 0f)
        assertEquals(0.3f, desktopViewportScale(0, 0f, 384), 0.00001f)
    }

    @Test fun desktopModeKeepsTheInstalledChromiumVersion() {
        val mobile = "Mozilla/5.0 (Linux; Android 16; TestDevice; wv) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Version/4.0 Chrome/140.0.7339.12 Mobile Safari/537.36"
        val desktop = desktopWebUserAgent(mobile)
        assertTrue(desktop.contains("Chrome/140.0.7339.12"))
        assertFalse(desktop.contains("Android"))
        assertFalse(desktop.contains("Mobile"))
        assertEquals("CustomEngine/5", desktopWebUserAgent("CustomEngine/5"))
    }

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
