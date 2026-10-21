package com.xiaomanjun.sleepdownschedule.platform.coloros

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import androidx.core.graphics.ColorUtils
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ColorOsDockAccentTest {
    @Test fun materialToneKeepsWallpaperAccentsChromaticAndLeavesTheButtonSeedUntouched() {
        // A mid-tone wallpaper previously drove the dark Dock accent nearly to white to reach
        // contrast against pixels behind the material. The Dock follows its own light/dark tone.
        for (seed in listOf(0xff2563eb.toInt(), 0xffb91c1c.toInt(), 0xff16a34a.toInt(), 0xffeab308.toInt())) {
            val original = FloatArray(3).also { ColorUtils.colorToHSL(seed, it) }
            for (dark in listOf(false, true)) {
                val accents = colorOsHomeAccents(seed, dark)
                val actual = FloatArray(3).also { ColorUtils.colorToHSL(accents.dock, it) }
                assertEquals(seed, accents.button)
                val distance = kotlin.math.abs(original[0] - actual[0])
                assertTrue(minOf(distance, 360f - distance) < 2f)
                assertTrue("The selected item must retain its accent", actual[1] >= 0.5f)
                assertTrue("The accent must not wash out to white", actual[2] < 0.8f)
                assertTrue(ColorUtils.calculateContrast(accents.dock, if (dark) Color.BLACK else Color.WHITE) >= 4.5)
            }
        }
    }

    @Test fun veryDarkWallpapersKeepTheirHueInsteadOfFallingBackToBlue() {
        for (seed in listOf(0xff2b1010.toInt(), 0xff0c2812.toInt(), 0xff20102b.toInt())) {
            val bitmap = Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(seed)
            val actual = requireNotNull(colorOsWallpaperAccent(bitmap))
            val originalHsv = FloatArray(3).also { Color.colorToHSV(seed, it) }
            val actualHsv = FloatArray(3).also { Color.colorToHSV(actual, it) }
            val distance = kotlin.math.abs(originalHsv[0] - actualHsv[0])
            assertTrue(minOf(distance, 360f - distance) < 12f)
            assertTrue(actualHsv[2] >= 0.59f)
            bitmap.recycle()
        }
    }

    @Test fun neutralWallpaperLeavesTheDefaultAccentAvailable() {
        val bitmap = Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(0xff151515.toInt())
        assertNull(colorOsWallpaperAccent(bitmap))
        bitmap.recycle()
    }

    @Test fun dockAccentsStayReadableOnBothBackgrounds() {
        for (seed in listOf(0xff2563eb.toInt(), 0xffb91c1c.toInt(), 0xff16a34a.toInt(), 0xffeab308.toInt())) {
            val darkBackground = 0xff151820.toInt()
            val lightBackground = 0xffeeeeee.toInt()
            val lightAccent = colorOsContrastingAccent(seed, darkBackground)
            val darkAccent = colorOsContrastingAccent(seed, lightBackground)
            assertTrue(ColorUtils.calculateLuminance(lightAccent) > ColorUtils.calculateLuminance(darkAccent))
            assertTrue(ColorUtils.calculateContrast(lightAccent, darkBackground) >= 4.5)
            assertTrue(ColorUtils.calculateContrast(darkAccent, lightBackground) >= 4.5)
        }
    }
}
