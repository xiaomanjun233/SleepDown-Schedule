package com.xiaomanjun.sleepdownschedule.glass

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.BackdropDiagnostics
import com.kyant.backdrop.backdrops.MaterialBackdrop
import com.kyant.backdrop.backdrops.MaterialBackdropFilter
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Exercises ownership/invalidation of real draw nodes, including a first frame with no cache. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MaterialSourceTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @After fun cleanup() { BackdropDiagnostics.observer = null }

    @Test fun transformedConsumerSamplesTheSamePixelsAsTheScene() {
        val consumer = mutableStateOf(false)
        val motion = mutableIntStateOf(0)
        compose.setContent {
            val raw = rememberGlassLayerBackdrop(GlassBackdropDomain.Content, "mapping-test")
            val material = remember(raw) { MaterialBackdrop(raw) }
            Box(Modifier.size(300.dp, 400.dp)) {
                Box(Modifier.size(300.dp, 400.dp)
                    .graphicsLayer { translationX = 11f; scaleX = .94f; scaleY = .94f }
                    .then(material.sourceModifier { "pattern" })
                    .then(Modifier.background(Color.White))
                    .then(Modifier.drawBehind {
                        for (x in 0..9) for (y in 0..12) {
                            drawRect(Color((x + 1) / 12f, (y + 1) / 15f, .35f),
                                Offset(x * size.width / 10, y * size.height / 13),
                                Size(size.width / 10, size.height / 13))
                        }
                    }))
                if (consumer.value) {
                    Box(Modifier.offset(90.dp, 140.dp).size(90.dp, 100.dp)
                        .graphicsLayer {
                            translationX = motion.intValue * 9.25f
                            scaleX = 1f + motion.intValue * .08f
                            scaleY = 1f - motion.intValue * .05f
                            rotationZ = motion.intValue * 9f
                        }
                        .background(Color.Magenta)
                        .drawPreparedCourseBackdrop(material) { motion.intValue })
                }
            }
        }
        fun snapshot(): Bitmap {
            compose.waitForIdle()
            var bitmap: Bitmap? = null
            compose.runOnIdle {
                val view = compose.activity.window.decorView
                bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                view.draw(Canvas(bitmap!!))
            }
            return bitmap!!
        }
        val plain = snapshot()
        for (frame in 0..3) {
            compose.runOnIdle { motion.intValue = frame; consumer.value = true }
            snapshot().recycle() // Resolve layout coordinates before comparing pixels.
            val sampled = snapshot()
            var different = 0
            for (y in 0 until plain.height) for (x in 0 until plain.width) {
                val a = plain.getPixel(x, y)
                val b = sampled.getPixel(x, y)
                val delta = kotlin.math.abs(android.graphics.Color.red(a) - android.graphics.Color.red(b)) +
                    kotlin.math.abs(android.graphics.Color.green(a) - android.graphics.Color.green(b)) +
                    kotlin.math.abs(android.graphics.Color.blue(a) - android.graphics.Color.blue(b))
                if (delta > 25) different++
            }
            // Raster edges can differ with fractional transforms; a displaced/zoomed wallpaper
            // or a double inverse changes the interior of the sampled rectangle.
            assertTrue("frame=$frame mismatched pixels=$different", different < plain.width * plain.height / 200)
            sampled.recycle()
        }
        plain.recycle()
    }

    @Test fun manyConsumersShareOneFilterThroughoutInvalidationAndRelease() {
        val pixels = mutableIntStateOf(0)
        val radius = mutableFloatStateOf(8f)
        val offset = mutableFloatStateOf(0f)
        val scale = mutableFloatStateOf(1f)
        val mounted = mutableStateOf(true)
        val counts = mutableMapOf<String, Long>()
        BackdropDiagnostics.observer = { event, count -> counts[event] = (counts[event] ?: 0) + count }
        compose.setContent {
            val raw = rememberGlassLayerBackdrop(GlassBackdropDomain.Content, "source-test")
            val material = remember(raw) { MaterialBackdrop(raw) }
            material.filter = radius.floatValue.takeIf { it > 0f }?.let { MaterialBackdropFilter(it) }
            if (mounted.value) Box(Modifier.size(300.dp, 440.dp)) {
                Box(Modifier.size(300.dp, 440.dp).then(material.sourceModifier { pixels.intValue })
                    .background(if (pixels.intValue == 0) Color.Blue else Color.Red))
                repeat(20) { i ->
                    Box(Modifier.offset((i % 4 * 65).dp, (i / 4 * 75).dp).size(55.dp, 65.dp)
                        .graphicsLayer {
                            translationX = offset.floatValue
                            scaleX = scale.floatValue
                            scaleY = scale.floatValue
                        }
                        .drawPreparedCourseBackdrop(material) { offset.floatValue to scale.floatValue })
                }
            }
        }
        fun draw() {
            compose.waitForIdle()
            compose.runOnIdle {
                val view = compose.activity.window.decorView
                val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                view.draw(Canvas(bitmap))
                bitmap.recycle()
            }
            compose.waitForIdle()
        }
        fun count(event: String) = counts[event] ?: 0L
        draw(); draw()
        assertEquals(counts.toString(), 1L, count("MaterialSource.Recorded"))
        assertEquals(counts.toString(), 1L, count("MaterialFilter.Recorded"))
        assertEquals(2L, count("MaterialFilter.Created"))
        assertEquals("No first-frame per-card blur layers", 0L, count("Sample.Created"))
        assertTrue(count("Sample.SharedBlit") >= 20)
        counts.clear()
        compose.runOnIdle { offset.floatValue = 17f; scale.floatValue = 1.14f }
        draw()
        assertEquals(0L, count("MaterialSource.Recorded"))
        assertEquals(0L, count("MaterialFilter.Recorded"))
        counts.clear()
        compose.runOnIdle { radius.floatValue = 16f }
        draw()
        assertEquals(0L, count("MaterialSource.Recorded"))
        assertEquals(1L, count("MaterialFilter.Recorded"))
        assertEquals(0L, count("MaterialFilter.Created"))
        counts.clear()
        compose.runOnIdle { pixels.intValue++ }
        draw()
        assertEquals(1L, count("MaterialSource.Recorded"))
        assertEquals(1L, count("MaterialFilter.Recorded"))
        counts.clear()
        compose.runOnIdle { mounted.value = false }
        draw()
        assertEquals(2L, count("MaterialFilter.Released"))
        counts.clear()
        compose.runOnIdle { mounted.value = true }
        draw()
        assertEquals(2L, count("MaterialFilter.Created"))
        assertEquals(1L, count("MaterialSource.Recorded"))
        assertEquals(1L, count("MaterialFilter.Recorded"))
        counts.clear()
        compose.runOnIdle { radius.floatValue = 0f }
        draw()
        assertEquals(2L, count("MaterialFilter.Released"))
        assertEquals(0L, count("MaterialFilter.Recorded"))
    }
}
