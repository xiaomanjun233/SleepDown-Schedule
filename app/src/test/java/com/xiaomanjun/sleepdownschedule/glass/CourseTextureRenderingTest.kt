package com.xiaomanjun.sleepdownschedule.glass

import android.app.Application
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.BackdropDiagnostics
import com.kyant.backdrop.backdrops.MaterialBackdrop
import com.xiaomanjun.sleepdownschedule.core.wallpaper.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CourseTextureRenderingTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @After fun cleanup() { BackdropDiagnostics.observer = null }

    @Test fun sharedTextureKeepsWallpaperAlignedForManyMovingConsumers() = runBlocking {
        val bitmap = Bitmap.createBitmap(256, 384, Bitmap.Config.ARGB_8888)
        for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) {
            bitmap.setPixel(x, y, android.graphics.Color.rgb(x, y * 255 / 384, 90))
        }
        val pyramid = buildWallpaperTexturePyramid(bitmap)
        val wallpaper = mutableStateOf<WallpaperPixels?>(WallpaperPixels(bitmap, WallpaperCropState(), null))
        val visible = mutableStateOf(false)
        val frame = mutableIntStateOf(0)
        val lens = mutableStateOf<GlassEffectFrame?>(null)
        val counts = mutableMapOf<String, Long>()
        BackdropDiagnostics.observer = { event, amount -> counts[event] = (counts[event] ?: 0) + amount }
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                val raw = rememberGlassLayerBackdrop(GlassBackdropDomain.Content, "texture-test")
                val sampling = remember {
                    CourseMaterialSampling(MaterialBackdrop(raw), wallpaper, mutableStateOf(pyramid)) { 0f }
                }
                Box(Modifier.size(256.dp, 384.dp)) {
                    FocusCroppedBitmap(bitmap, WallpaperCropState(),
                        Modifier.size(256.dp, 384.dp).then(sampling.filtered.sourceModifier { bitmap }))
                    if (visible.value) repeat(12) { index ->
                        Box(Modifier.offset((index % 3 * 70 + 10).dp, (index / 3 * 80 + 15).dp)
                            .size(52.dp, 65.dp).graphicsLayer {
                                translationX = frame.intValue * 3.25f
                                scaleX = 1f + frame.intValue * .06f
                                scaleY = 1f - frame.intValue * .04f
                                rotationZ = frame.intValue * 5f
                            }.courseTexture(sampling, { RoundedCornerShape(8.dp) }, frame = lens.value))
                    }
                }
            }
        }
        val plain = snapshot()
        compose.runOnIdle { visible.value = true }
        snapshot().recycle()
        counts.clear()
        repeat(4) { step ->
            compose.runOnIdle { frame.intValue = step }
            val sampled = snapshot()
            var mismatched = 0
            for (y in 0 until plain.height) for (x in 0 until plain.width) {
                val a = plain.getPixel(x, y)
                val b = sampled.getPixel(x, y)
                if (abs(android.graphics.Color.red(a) - android.graphics.Color.red(b)) +
                    abs(android.graphics.Color.green(a) - android.graphics.Color.green(b)) +
                    abs(android.graphics.Color.blue(a) - android.graphics.Color.blue(b)) > 18) mismatched++
            }
            assertTrue("frame=$step mismatched=$mismatched", mismatched < plain.width * plain.height / 150)
            sampled.recycle()
        }
        // Exercise both exported optical programs and their input-uniform snapshots.
        for (dispersion in listOf(false, true)) {
            compose.runOnIdle {
                frame.intValue = 0
                lens.value = GlassEffectFrame(blur = null, lensHeight = 8.dp, lensAmount = 6.dp,
                    chromaticAberration = dispersion)
            }
            val sampled = snapshot()
            for (index in 0 until 12) {
                val x = index % 3 * 70 + 36
                val y = index / 3 * 80 + 47
                val a = plain.getPixel(x, y)
                val b = sampled.getPixel(x, y)
                assertEquals("lens center R, dispersion=$dispersion, card=$index",
                    android.graphics.Color.red(a).toFloat(), android.graphics.Color.red(b).toFloat(), 2f)
                assertEquals("lens center G, dispersion=$dispersion, card=$index",
                    android.graphics.Color.green(a).toFloat(), android.graphics.Color.green(b).toFloat(), 2f)
            }
            sampled.recycle()
        }
        assertEquals("Moving textures must not record the wallpaper again", 0L, counts["MaterialSource.Recorded"] ?: 0L)
        assertEquals("Static cards own no local sampling layers", 0L, counts["Sample.Created"] ?: 0L)
        plain.recycle()
    }

    @Test fun pyramidPreservesSolidColorsAndHasContinuousBlurLevels() = runBlocking {
        val bitmap = Bitmap.createBitmap(96, 128, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.rgb(52, 105, 189))
        val pyramid = buildWallpaperTexturePyramid(bitmap)
        assertSame(bitmap, pyramid.levels.first())
        assertTrue(pyramid.sigma.toList().zipWithNext().all { (a, b) -> b > a })
        for (level in pyramid.levels) assertEquals(bitmap.getPixel(0, 0), level.getPixel(level.width / 2, level.height / 2))
        assertEquals(0, pyramid.levelPair(0f).first)
        for (i in 1 until pyramid.levels.size) {
            val exact = pyramid.levelPair(pyramid.sigma[i])
            assertEquals(i, exact.second)
            assertEquals(1f, exact.mix, .0001f)
        }
    }

    private fun snapshot(): Bitmap {
        compose.waitForIdle()
        return compose.onRoot().captureToImage().asAndroidBitmap()
    }
}
