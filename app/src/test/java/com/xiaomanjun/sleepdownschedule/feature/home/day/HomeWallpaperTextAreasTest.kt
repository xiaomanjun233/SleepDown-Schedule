package com.xiaomanjun.sleepdownschedule.feature.home.day

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntSize
import com.xiaomanjun.sleepdownschedule.core.wallpaper.HomeReadabilityContext
import com.xiaomanjun.sleepdownschedule.model.defaultConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomeWallpaperTextAreasTest {
    @Test fun brightTitleWinsOverSeveralSmallDarkLabelsAndDarkWallpaper() {
        val context = splitWallpaper()
        val title = Rect(20f, 5f, 90f, 20f)
        val timeLabels = (0..5).map { index ->
            val top = 80f + index * 8f
            Rect(0f, top, 10f, top + 7f)
        }
        assertEquals(true, wallpaperLightStyleForTextAreas(context, timeLabels + title))
        assertEquals(false, wallpaperLightStyleForTextAreas(context, timeLabels))
    }

    @Test fun brightMinorityDoesNotOutvoteDarkTextArea() {
        val context = splitWallpaper()
        val mixed = Rect(10f, 0f, 70f, 80f)
        assertEquals(false, wallpaperLightStyleForTextAreas(context, listOf(mixed)))
    }

    @Test fun samplingUsesTheVisibleCropWindowOffsetAndWallpaperBrightness() {
        val context = splitWallpaper().copy(rootOffsetInWindow = Offset(16f, 32f))
        val labels = listOf(Rect(36f, 36f, 106f, 48f), Rect(300f, 80f, 500f, 100f))
        val config = requireNotNull(context.config)
        assertEquals(true, wallpaperLightStyleForTextAreas(context, labels))
        assertEquals(false, wallpaperLightStyleForTextAreas(context.copy(
            config = config.copy(wallpaperPortraitScale = 2f, wallpaperPortraitCenterY = 0.75f)
        ), labels))
        assertEquals(false, wallpaperLightStyleForTextAreas(context.copy(
            config = config.copy(wallpaperBrightness = 0.35f)
        ), labels))
        assertNull(wallpaperLightStyleForTextAreas(context, labels.drop(1)))
    }

    private fun splitWallpaper(): HomeReadabilityContext {
        val width = 100
        val height = 160
        val pixels = IntArray(width * height) { index -> if (index / width < 22) -1 else 0xff000000.toInt() }
        return HomeReadabilityContext(
            bitmap = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888),
            config = defaultConfig().copy(wallpaperUri = "content://wallpaper/test"),
            rootSize = IntSize(width, height)
        )
    }
}
