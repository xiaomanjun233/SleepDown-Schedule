package com.xiaomanjun.sleepdownschedule.transition

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SharedViewKitRoundedPixelsTest {
    @Test fun sourcePixelsHaveTransparentCornersEvenWhenTheSamplerIgnoresOutline() {
        val original = Bitmap.createBitmap(100, 140, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }
        val rounded = checkNotNull(original.softwareCopyForKit(20f))
        assertEquals(0, Color.alpha(rounded.getPixel(0, 0)))
        assertEquals(0, Color.alpha(rounded.getPixel(99, 139)))
        assertEquals(Color.BLUE, rounded.getPixel(50, 1))
        assertEquals(Color.BLUE, rounded.getPixel(50, 70))
        assertEquals(Color.BLUE, original.getPixel(0, 0))
        rounded.recycle()
        original.recycle()
    }
}
