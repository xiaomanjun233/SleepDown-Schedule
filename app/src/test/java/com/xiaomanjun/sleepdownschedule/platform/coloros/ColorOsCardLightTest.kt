package com.xiaomanjun.sleepdownschedule.platform.coloros

import android.app.Activity
import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.view.TextureView
import android.view.View
import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ColorOsCardLightTest {
    @Test fun hardwareSnapshotsAreCopiedToReadablePixelsForTheNativeTexture() {
        val pixels = Bitmap.createBitmap(20, 40, Bitmap.Config.ARGB_8888)
        pixels.eraseColor(android.graphics.Color.RED)
        val hardware = requireNotNull(pixels.copy(Bitmap.Config.HARDWARE, false))
        val texture = cardLightTextureBitmap(hardware)
        assertEquals(Bitmap.Config.ARGB_8888, texture.config)
        assertNotSame(hardware, texture)
        assertEquals(20, texture.width)
        assertEquals(40, texture.height)
        assertEquals(android.graphics.Color.RED, texture.getPixel(0, 0))
        assertFalse(hardware.isRecycled)
        assertSame(pixels, cardLightTextureBitmap(pixels))
    }

    @Test fun bitmapWaitsForTheNativeSurfaceFirstFrame() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val activity = controller.get()
        var uploads = 0
        var starts = 0
        var preparations = 0
        val texture = TextureView(activity).apply {
            surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {}
                override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}
                override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
                override fun onSurfaceTextureDestroyed(surface: SurfaceTexture) = true
            }
        }
        val driver = object : CardLightDriver {
            override val view = texture
            override fun setBitmap(value: Bitmap) { uploads++ }
            override fun prepareSurface() { preparations++ }
            override fun start() { starts++ }
            override fun stop() {}
        }
        val host = CardLightHost(activity, driver, 136)
        activity.setContentView(host)
        host.setStarted(true)
        host.update(CardLightFrame(Bitmap.createBitmap(20, 40, Bitmap.Config.ARGB_8888), Bounds, 1f))
        assertEquals(0, uploads)
        assertEquals(0, starts)
        assertEquals(1f, texture.alpha, 0f)
        val surface = SurfaceTexture(0)
        val listener = requireNotNull(texture.surfaceTextureListener)
        listener.onSurfaceTextureAvailable(surface, 600, 136)
        assertEquals(1, preparations)
        assertEquals(0, uploads)
        listener.onSurfaceTextureUpdated(surface)
        assertEquals(1, uploads)
        assertEquals(1, starts)
        listener.onSurfaceTextureUpdated(surface)
        assertEquals(1, uploads)
        assertEquals(1, starts)
        host.release()
        surface.release()
        controller.pause().stop().destroy()
    }

    @Test fun opacityDoesNotUploadTheBitmapAgainAndBackgroundingStopsRendering() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val activity = controller.get()
        val driver = Driver(activity)
        val host = CardLightHost(activity, driver, 136)
        activity.setContentView(host)
        assertTrue(host.isAttachedToWindow)
        val first = Bitmap.createBitmap(20, 40, Bitmap.Config.ARGB_8888)
        val second = Bitmap.createBitmap(20, 40, Bitmap.Config.ARGB_8888)
        host.setStarted(true)
        host.update(CardLightFrame(first, Bounds, 1f))
        host.update(CardLightFrame(first, Bounds, 0.5f))
        assertEquals(1, driver.uploads)
        assertEquals(1, driver.starts)
        assertEquals(0.5f, driver.view.alpha, 0f)
        assertEquals(listOf(1f, 0.5f), driver.progressValues)
        host.update(CardLightFrame(first, Bounds, 0.5f, revealProgress = 0.25f))
        assertEquals(0.5f, driver.view.alpha, 0f)
        assertEquals(0.25f, driver.progressValues.last(), 0f)
        assertEquals(1, driver.uploads)

        host.setStarted(false)
        host.update(CardLightFrame(second, Bounds, 1f))
        assertEquals(1, driver.uploads)
        assertEquals(1, driver.stops)
        assertEquals(0f, driver.view.alpha, 0f)
        host.setStarted(true)
        assertEquals(2, driver.uploads)
        assertEquals(2, driver.starts)
        host.update(CardLightFrame(null, Bounds, 1f))
        assertEquals(2, driver.stops)
        assertEquals(0f, driver.view.alpha, 0f)
        host.release()
        assertFalse(first.isRecycled)
        assertFalse(second.isRecycled)
        controller.pause().stop().destroy()
    }

    @Test fun lightKeepsItsNativeWidthAndFollowsTheActualCardBottom() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val driver = Driver(activity)
        val host = CardLightHost(activity, driver, 136)
        activity.setContentView(host)
        host.update(CardLightFrame(null, Bounds, 0f))
        host.measure(exactly(400), exactly(800))
        host.layout(0, 0, 400, 800)
        assertEquals(600, driver.view.measuredWidth)
        assertEquals(136, driver.view.height)
        assertEquals(-100f, driver.view.translationX, 0f)
        assertEquals(500f, driver.view.translationY, 0f)
        host.release()
    }

    @Test fun pickerKeepsTheAcceptedNativeGeometryEvenWithCornersAndDistantButtons() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val driver = Driver(activity)
        val host = CardLightHost(activity, driver, 136)
        activity.setContentView(host)
        host.update(CardLightFrame(null, Bounds, 0f, cornerRadius = 34f,
            receiverBounds = Rect(100f, 574f, 300f, 626f)))
        host.measure(exactly(400), exactly(800))
        host.layout(0, 0, 400, 800)
        assertEquals(500f, driver.view.translationY, 0f)
        assertEquals(1f, driver.view.scaleX, 0f)
        assertEquals(1f, driver.view.scaleY, 0f)
        assertEquals(136, driver.view.height)
        host.release()
    }

    @Test fun detachStopsOnceAndNeverRestartsTheDestroyedNativeEngine() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val activity = controller.get()
        val driver = Driver(activity)
        val host = CardLightHost(activity, driver, 136)
        activity.setContentView(host)
        host.setStarted(true)
        val bitmap = Bitmap.createBitmap(20, 40, Bitmap.Config.ARGB_8888)
        host.update(CardLightFrame(bitmap, Bounds, 1f))
        activity.setContentView(View(activity))
        host.setStarted(true)
        host.update(CardLightFrame(bitmap, Bounds, 1f))
        host.release()
        assertEquals(1, driver.starts)
        assertEquals(1, driver.stops)
        assertEquals(0f, driver.view.alpha, 0f)
        controller.pause().stop().destroy()
    }

    @Test fun unavailableNativeEngineHidesTheLightWithoutRetryingEveryFrame() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val activity = controller.get()
        val driver = Driver(activity, failUpload = true)
        val host = CardLightHost(activity, driver, 136)
        activity.setContentView(host)
        host.setStarted(true)
        val bitmap = Bitmap.createBitmap(20, 40, Bitmap.Config.ARGB_8888)
        repeat(4) { host.update(CardLightFrame(bitmap, Bounds, 1f)) }
        assertEquals(1, driver.uploads)
        assertEquals(0, driver.starts)
        assertEquals(0f, driver.view.alpha, 0f)
        host.release()
        controller.pause().stop().destroy()
    }

    private class Driver(context: Context, private val failUpload: Boolean = false) : CardLightDriver {
        override val view = object : View(context) {
            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                setMeasuredDimension(600, MeasureSpec.getSize(heightMeasureSpec))
            }
        }
        var uploads = 0
        var starts = 0
        var stops = 0
        val progressValues = mutableListOf<Float>()
        override fun setBitmap(value: Bitmap) {
            uploads++
            if (failUpload) error("Native engine unavailable")
        }
        override fun start() { starts++ }
        override fun setProgress(value: Float) { progressValues += value }
        override fun stop() { stops++ }
    }

    companion object {
        private val Bounds = Rect(100f, 100f, 300f, 500f)
        private fun exactly(value: Int) = View.MeasureSpec.makeMeasureSpec(value, View.MeasureSpec.EXACTLY)
    }
}
