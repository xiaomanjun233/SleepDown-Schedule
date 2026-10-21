package com.xiaomanjun.sleepdownschedule.core.wallpaper

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import androidx.test.core.app.ApplicationProvider
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WallpaperImageOrientationTest {
    @Test fun galleryExifOrientationIsAppliedBeforePreviewAndPersistedOnlyOnce() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val image = File(context.cacheDir, "wallpaper-exif.jpg")
        val original = Bitmap.createBitmap(80, 120, Bitmap.Config.ARGB_8888)
        original.eraseColor(android.graphics.Color.BLUE)
        image.outputStream().use { assertTrue(original.compress(Bitmap.CompressFormat.JPEG, 95, it)) }
        original.recycle()
        ExifInterface(image).apply {
            setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
            saveAttributes()
        }
        val uri = Uri.parse(image.toURI().toString())
        val loaded = loadWallpaperSource(context, uri)
        assertEquals(WallpaperSourceSize(120, 80), loaded.sourceSize)
        assertEquals(WallpaperSourceSize(120, 80), readWallpaperSourceSize(context, uri))
        assertEquals(120, loaded.bitmap?.width)
        assertEquals(80, loaded.bitmap?.height)
        loaded.bitmap?.recycle()
        val saved = persistWallpaperSource(context, uri)
        assertNotNull(saved)
        // Robolectric runs on Windows here; Android's Uri.fromFile assumes Unix paths.
        val persisted = File(context.filesDir, "wallpaper").listFiles().orEmpty().single()
        assertTrue(requireNotNull(saved).toString().contains(persisted.name))
        val reopened = loadWallpaperSource(context, Uri.parse(persisted.toURI().toString()))
        assertEquals(WallpaperSourceSize(120, 80), reopened.sourceSize)
        assertEquals(120, reopened.bitmap?.width)
        assertEquals(80, reopened.bitmap?.height)
        reopened.bitmap?.recycle()
    }

    @Test fun mirroredAndRotatedCornersKeepTheirImageCoordinates() {
        val source = Bitmap.createBitmap(2, 3, Bitmap.Config.ARGB_8888)
        source.setPixel(0, 0, android.graphics.Color.RED)
        source.setPixel(1, 0, android.graphics.Color.GREEN)
        source.setPixel(0, 2, android.graphics.Color.BLUE)
        source.setPixel(1, 2, android.graphics.Color.YELLOW)
        val rotated = WallpaperImageOrientation(90, flipped = true).applyTo(source)
        assertEquals(3, rotated.width)
        assertEquals(2, rotated.height)
        assertEquals(android.graphics.Color.YELLOW, rotated.getPixel(0, 0))
        assertEquals(android.graphics.Color.GREEN, rotated.getPixel(2, 0))
        assertEquals(android.graphics.Color.BLUE, rotated.getPixel(0, 1))
        assertEquals(android.graphics.Color.RED, rotated.getPixel(2, 1))
        rotated.recycle()
    }
}
