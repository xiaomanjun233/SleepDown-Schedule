package com.xiaomanjun.sleepdownschedule.glass

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.BackdropDiagnostics
import com.kyant.backdrop.backdrops.SharedBlurBackdrop
import com.kyant.backdrop.backdrops.SharedBlurSampleScale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Same source/card translation as the dense home-to-settings switch, without user data. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SharedCourseMotionTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @After fun cleanup() { BackdropDiagnostics.observer = null }

    @Test fun sharedSamplesSurviveCommonPageTranslationButRefreshOnRelativeMotion() {
        val pageX = mutableFloatStateOf(0f)
        val cardX = mutableFloatStateOf(0f)
        val refraction = mutableStateOf(true)
        val counts = mutableMapOf<String, Long>()
        BackdropDiagnostics.observer = { event, count -> counts[event] = (counts[event] ?: 0L) + count }
        compose.setContent {
            MaterialTheme {
                val source = rememberGlassLayerBackdrop(GlassBackdropDomain.Content, "motion-wallpaper")
                val shared = remember(source) { SharedBlurBackdrop(source, 8f, false) }
                Box(Modifier.size(320.dp, 480.dp)) {
                    Box(Modifier.size(320.dp, 480.dp).graphicsLayer { translationX = pageX.floatValue }
                        .glassBackdropProducer(source, recordKey = { "wallpaper" }).background(Color.Blue))
                    Box(Modifier.size(320.dp, 480.dp).then(shared.preRenderModifier { "wallpaper" }))
                    Box(Modifier.size(320.dp, 480.dp).graphicsLayer { translationX = pageX.floatValue }) {
                        repeat(20) { index ->
                            val spec = GlassMaterialSpec.courseCard(8f)
                            val descriptor = rememberGlassSurfaceDescriptor("card-$index", GlassBackdropDomain.Content, spec.role)
                            Box(Modifier.offset((index % 4 * 72).dp, (index / 4 * 76).dp).size(64.dp, 68.dp)
                                .graphicsLayer { translationX = cardX.floatValue }
                                .sleepDownGlassSurface(shared, descriptor, spec, { RoundedCornerShape(10.dp) },
                                    GlassEffectFrame(blur = null, lensHeight = if (refraction.value) 8.dp else null,
                                        lensAmount = if (refraction.value) 12.dp else null), backdropSampleScale = SharedBlurSampleScale,
                                    cacheSharedSamples = true, placementLayer = false))
                        }
                    }
                }
            }
        }
        fun draw() {
            compose.waitForIdle()
            compose.runOnIdle {
                val root = compose.activity.window.decorView
                val bitmap = android.graphics.Bitmap.createBitmap(root.width, root.height, android.graphics.Bitmap.Config.ARGB_8888)
                root.draw(android.graphics.Canvas(bitmap)); bitmap.recycle()
            }
            compose.waitForIdle()
        }
        draw(); draw()
        assertTrue("Expected real sampled cards: $counts", (counts["Sample.SharedDirect"] ?: 0L) >= 20)
        counts.clear()
        for (x in listOf(3.14f, 18.91f, 73.29f, 143.17f, 0f)) {
            compose.runOnIdle { pageX.floatValue = x }
            draw()
        }
        assertEquals("Common page translation must retain samples: $counts", 0L, counts["Sample.Recorded"] ?: 0L)
        counts.clear()
        compose.runOnIdle { cardX.floatValue = 24f }
        draw()
        assertTrue("Actual card movement must resample: $counts", (counts["Sample.Recorded"] ?: 0L) >= 20)
        compose.runOnIdle { refraction.value = false }
        draw(); counts.clear()
        compose.runOnIdle { cardX.floatValue = 36f }
        draw()
        assertTrue("Effect-free cards must draw the shared blur directly: $counts", (counts["Sample.SharedBlit"] ?: 0L) >= 20)
        assertEquals(0L, counts["Sample.Recorded"] ?: 0L)
    }
}
