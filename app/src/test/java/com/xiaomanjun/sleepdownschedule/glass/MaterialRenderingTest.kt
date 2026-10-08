package com.xiaomanjun.sleepdownschedule.glass

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import com.xiaomanjun.sleepdownschedule.model.*
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.effects.blur
import com.xiaomanjun.sleepdownschedule.core.performance.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Counts real modifier drawing, not merely the policy's booleans. No personal device data. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MaterialRenderingTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val context get() = RuntimeEnvironment.getApplication()

    @After fun cleanup() {
        MaterialSamplingDiagnostics.enabled = false
        AppMaterialPreferences.setLevel(context, AppMaterialLevel.QUALITY)
    }

    @Test fun independentCardAlignmentFitsShortAndWideCardsWithLongNamesAndLargeFonts() {
        data class Preview(val vertical: WeekCardContentLayout, val horizontal: WeekCardTextAlignment,
            val wide: Boolean, val dark: Boolean)
        val preview = mutableStateOf(Preview(WeekCardContentLayout.TOP, WeekCardTextAlignment.START, false, false))
        val name = "一门很长很长的课程名称用于检查顶部和中部组合对齐"
        compose.setContent {
            val value = preview.value
            MaterialTheme(colorScheme = if (value.dark) androidx.compose.material3.darkColorScheme() else androidx.compose.material3.lightColorScheme()) {
                androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides
                    androidx.compose.ui.unit.Density(1f, 1.8f)) {
                    Box(Modifier.size(if (value.wide) 180.dp else 48.dp, if (value.wide) 110.dp else 40.dp).testTag("card")) {
                        com.xiaomanjun.sleepdownschedule.feature.home.week.WeekCourseOverlayCardContent(
                            CourseEntity(1, name, if (value.wide) "教师" else null, if (value.wide) "很长的教学地点" else null,
                                1, listOf(1), listOf(1), WeekParity.ALL, null),
                            defaultConfig().copy(weekCardContentLayout = value.vertical, weekCardTextAlignment = value.horizontal,
                                followSystemDarkMode = false, darkMode = value.dark))
                    }
                }
            }
        }
        for (vertical in listOf(WeekCardContentLayout.TOP, WeekCardContentLayout.MIDDLE))
            for (horizontal in listOf(WeekCardTextAlignment.START, WeekCardTextAlignment.CENTER))
                for (wide in listOf(false, true)) for (dark in listOf(false, true)) {
                    compose.runOnIdle { preview.value = Preview(vertical, horizontal, wide, dark) }
                    compose.waitForIdle()
                    val bounds = compose.onNodeWithTag("card").fetchSemanticsNode().boundsInRoot
                    val names = compose.onAllNodesWithText(name).fetchSemanticsNodes()
                    assertEquals(1, names.size)
                    val text = names.single().boundsInRoot
                    assertTrue("${preview.value}: $text outside $bounds", text.top >= bounds.top - 1 && text.bottom <= bounds.bottom + 1)
                }
    }

    @Test fun weekDedicatedRefreshStopsInSuperAndWhenBothCourseMaterialsAreOff() {
        AppMaterialPreferences.setLevel(context, AppMaterialLevel.SUPER_PERFORMANCE)
        MaterialSamplingDiagnostics.reset()
        MaterialSamplingDiagnostics.enabled = true
        val switches = mutableStateOf(true)
        compose.setContent {
            val enabled = AppMaterialPreferences.policy.courseSamples(switches.value, switches.value, true)
            val pager = androidx.compose.foundation.pager.rememberPagerState { 1 }
            val motion = com.xiaomanjun.sleepdownschedule.feature.home.week.rememberWeekPageTailMotion(pager, enabled)
            com.xiaomanjun.sleepdownschedule.feature.home.week.WeekPageSamplingScope(
                motion, 0, androidx.compose.runtime.remember { mutableStateOf(false) }) { Box(Modifier.size(50.dp)) }
        }
        compose.waitForIdle()
        assertEquals(0L, MaterialSamplingDiagnostics.dedicatedRefreshes)
        compose.runOnIdle { AppMaterialPreferences.setLevel(context, AppMaterialLevel.QUALITY) }
        compose.waitForIdle()
        assertTrue(MaterialSamplingDiagnostics.dedicatedRefreshes > 0)
        compose.runOnIdle { switches.value = false }
        compose.waitForIdle()
        compose.runOnIdle { MaterialSamplingDiagnostics.reset(); AppMaterialPreferences.setLevel(context, AppMaterialLevel.SUPER_PERFORMANCE) }
        compose.waitForIdle()
        compose.runOnIdle { AppMaterialPreferences.setLevel(context, AppMaterialLevel.QUALITY) }
        compose.waitForIdle()
        assertEquals(0L, MaterialSamplingDiagnostics.dedicatedRefreshes)
        compose.runOnIdle { switches.value = true }
        compose.waitForIdle()
        assertTrue(MaterialSamplingDiagnostics.dedicatedRefreshes > 0)
    }

    @Test fun superStopsDenseAndProducerDrawsSceneExceptionReleasesThenQualityRecovers() {
        AppMaterialPreferences.setLevel(context, AppMaterialLevel.QUALITY)
        val scene = mutableStateOf(false)
        val pulse = mutableStateOf(false)
        compose.setContent {
            MaterialTheme {
                val backdrop = rememberGlassLayerBackdrop(GlassBackdropDomain.Content, "material-test")
                val material = GlassMaterialSpec.courseCard(8f)
                val descriptor = rememberGlassSurfaceDescriptor("test-card", GlassBackdropDomain.Content, material.role)
                Box(Modifier.size(180.dp)) {
                    Box(Modifier.size(180.dp).glassBackdropProducer(backdrop)
                        .background(if (pulse.value) Color.Red else Color.Blue))
                    Box(Modifier.size(100.dp).sleepDownPlainGlassSurface(backdrop, descriptor, material,
                        { RoundedCornerShape(16.dp) }) { blur((if (pulse.value) 8.dp else 9.dp).toPx()) })
                    if (scene.value) Box(Modifier.size(170.dp).sleepDownPlainGlassSurface(backdrop, descriptor, material,
                        { RoundedCornerShape(8.dp) }, usage = MaterialUsage.SCENE_BLUR) { blur((if (pulse.value) 8.dp else 9.dp).toPx()) })
                }
            }
        }
        fun drawCounts(): Triple<Long, Long, Long> {
            compose.waitForIdle()
            compose.runOnIdle { MaterialSamplingDiagnostics.reset(); MaterialSamplingDiagnostics.enabled = true; pulse.value = !pulse.value }
            compose.runOnIdle {
                val root = compose.activity.window.decorView
                val bitmap = android.graphics.Bitmap.createBitmap(root.width, root.height, android.graphics.Bitmap.Config.ARGB_8888)
                root.draw(android.graphics.Canvas(bitmap))
                bitmap.recycle()
            }
            compose.waitForIdle()
            return Triple(MaterialSamplingDiagnostics.denseDraws, MaterialSamplingDiagnostics.sceneDraws, MaterialSamplingDiagnostics.producerDraws)
        }
        val quality = drawCounts()
        assertTrue("quality: $quality", quality.first > 0 && quality.third > 0)
        compose.runOnIdle { AppMaterialPreferences.setLevel(context, AppMaterialLevel.SUPER_PERFORMANCE) }
        assertEquals(Triple(0L, 0L, 0L), drawCounts())
        repeat(3) {
            compose.runOnIdle { scene.value = true }
            val popup = drawCounts()
            assertEquals(0L, popup.first)
            assertTrue("scene: $popup", popup.second > 0 && popup.third > 0)
            compose.runOnIdle { scene.value = false }
            assertEquals(Triple(0L, 0L, 0L), drawCounts())
        }
        compose.runOnIdle { AppMaterialPreferences.setLevel(context, AppMaterialLevel.QUALITY) }
        val recovered = drawCounts()
        assertTrue("recovered: $recovered", recovered.first > 0 && recovered.third > 0)
    }
}
