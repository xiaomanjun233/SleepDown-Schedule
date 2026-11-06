package com.xiaomanjun.sleepdownschedule.platform.coloros

import com.xiaomanjun.sleepdownschedule.feature.home.NativeHomeDockChrome

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.xiaomanjun.sleepdownschedule.core.performance.StartupPhase
import com.xiaomanjun.sleepdownschedule.defaultConfig
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.LocalNativeCourseCardMode
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.NativeCourseCardMode
import com.xiaomanjun.sleepdownschedule.core.ui.material.LocalVisualEffects
import com.xiaomanjun.sleepdownschedule.core.ui.material.VisualEffectsState
import com.xiaomanjun.sleepdownschedule.model.VisualEffectMode
import com.itos.coui.backdrop.rememberCouiBackdrop
import com.xiaomanjun.sleepdownschedule.glass.GlassBackdropDomain
import com.xiaomanjun.sleepdownschedule.glass.LocalSharedCourseBackdrop
import com.xiaomanjun.sleepdownschedule.glass.rememberGlassLayerBackdrop
import com.kyant.backdrop.backdrops.SharedBlurBackdrop
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLog

/** Exercises the actual Dock composition boundary; vendor View drawing still needs a device. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ColorOsDockCompositionTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun nativeTintAndBlurPreviewOnlyInvalidateMaterialConsumers() {
        val tint = mutableFloatStateOf(0.5f)
        val blur = mutableFloatStateOf(1f)
        val mode = NativeCourseCardMode(mutableStateOf(true), {}, tint, blur, 24f,
            { _, _ -> }, { _, _ -> }, {})
        var received = 0f to 0f
        var providerCommits = 0
        val receive: (Pair<Float, Float>) -> Unit = { received = it }
        val select: (Int) -> Unit = {}
        val home: () -> Unit = {}
        val menu: (Rect, GraphicsLayer) -> Unit = { _, _ -> }
        ShadowLog.clear()
        compose.setContent {
            SideEffect { providerCommits++ }
            val fallback = rememberUpdatedState<@Composable () -> Unit> { Box(Modifier.size(64.dp)) }
            val plusBackdrop = rememberGlassLayerBackdrop(GlassBackdropDomain.Content, "dock-plus-test-1")
            CompositionLocalProvider(LocalNativeCourseCardMode provides mode) {
                NativeCoursePreviewConsumer(receive)
                NativeHomeDockChrome(
                    visible = true, selectedIndex = 1, dark = false,
                    dockDark = false, dockForeground = null, dockSampleBrightness = 1f,
                    buttonHidden = false, config = defaultConfig(),
                    couiBackdrop = null, plusBackdrop = plusBackdrop,
                    onSelect = select, onOpenMenu = menu,
                    fallback = fallback, phase = StartupPhase.FullQuality
                )
            }
        }
        compose.waitForIdle()
        fun commits() = ShadowLog.getLogsForTag("SleepDownCOUI").count { it.msg.startsWith("Dock commit:") }
        val initial = commits()
        val initialProvider = providerCommits
        assertTrue(initial > 0)
        for (value in 1..10) {
            compose.runOnIdle { tint.floatValue = value / 10f; blur.floatValue = value / 5f }
            compose.waitForIdle()
        }
        assertEquals(1f to 48f, received)
        assertEquals(initialProvider, providerCommits)
        assertEquals(initial, commits())
    }

    @Test @Config(sdk = [33])
    fun oldSystemFallsBackEvenWhenCouiKitIsMarkedAvailable() {
        var fallbackRendered = 0
        val select: (Int) -> Unit = {}
        val menu: (Rect, GraphicsLayer) -> Unit = { _, _ -> }
        compose.setContent {
            val plusBackdrop = rememberGlassLayerBackdrop(GlassBackdropDomain.Content, "dock-plus-test-33")
            val pageBackdrop = rememberCouiBackdrop(diagnosticName = "dock-page-test-33")
            val fallback = rememberUpdatedState<@Composable () -> Unit> {
                SideEffect { fallbackRendered++ }
            }
            CompositionLocalProvider(LocalVisualEffects provides VisualEffectsState(
                effectiveMode = VisualEffectMode.ColorOsGlow,
                colorOsAvailable = false,
                couiKitAvailable = true,
            )) {
                NativeHomeDockChrome(
                    visible = true, selectedIndex = 1, dark = false,
                    dockDark = false, dockForeground = null, dockSampleBrightness = 1f,
                    buttonHidden = false, config = defaultConfig(),
                    couiBackdrop = pageBackdrop, plusBackdrop = plusBackdrop,
                    onSelect = select, onOpenMenu = menu,
                    fallback = fallback, phase = StartupPhase.FullQuality
                )
            }
        }
        compose.waitForIdle()
        assertTrue("Android 13 must keep the existing dock", fallbackRendered > 0)
    }

    @Test fun changingBackgroundCapturedByFallbackDoesNotCommitDockHostAgain() {
        val backgroundRevision = mutableIntStateOf(0)
        var renderedFallbackRevision = -1
        val select: (Int) -> Unit = {}
        val home: () -> Unit = {}
        val menu: (Rect, GraphicsLayer) -> Unit = { _, _ -> }
        ShadowLog.clear()
        compose.setContent {
            val revision = backgroundRevision.intValue
            val fallback = rememberUpdatedState<@Composable () -> Unit> {
                Box(Modifier.size(64.dp))
                SideEffect { renderedFallbackRevision = revision }
            }
            val plusBackdrop = rememberGlassLayerBackdrop(GlassBackdropDomain.Content, "dock-plus-test-2")
            NativeHomeDockChrome(
                visible = true, selectedIndex = 1, dark = false,
                dockDark = false, dockForeground = null, dockSampleBrightness = 1f,
                buttonHidden = false, config = defaultConfig(),
                couiBackdrop = null, plusBackdrop = plusBackdrop,
                onSelect = select, onOpenMenu = menu,
                fallback = fallback, phase = StartupPhase.FullQuality
            )
        }
        compose.waitForIdle()
        fun commits() = ShadowLog.getLogsForTag("SleepDownCOUI").count { it.msg.startsWith("Dock commit:") }
        val initial = commits()
        assertTrue(initial > 0)
        for (revision in 1..12) {
            compose.runOnIdle { backgroundRevision.intValue = revision }
            compose.waitForIdle()
        }
        assertEquals("The fallback must still receive fresh state", 12, renderedFallbackRevision)
        assertEquals("Background-only changes must not commit the native Dock host", initial, commits())
    }

    @Test fun previewingCourseBlurUpdatesConsumersWithoutCommittingDock() {
        val radius = mutableIntStateOf(4)
        var receivedRadius = -1f
        val select: (Int) -> Unit = {}
        val home: () -> Unit = {}
        val menu: (Rect, GraphicsLayer) -> Unit = { _, _ -> }
        val receive: (Float) -> Unit = { receivedRadius = it }
        ShadowLog.clear()
        compose.setContent {
            val source = rememberGlassLayerBackdrop(GlassBackdropDomain.Content, "course-preview-test")
            val pixels = radius.intValue.toFloat()
            val shared = remember(source, pixels) { SharedBlurBackdrop(source, pixels, false) }
            val fallback = rememberUpdatedState<@Composable () -> Unit> { Box(Modifier.size(64.dp)) }
            CompositionLocalProvider(LocalSharedCourseBackdrop provides shared) {
                CourseSamplerConsumer(receive)
                NativeHomeDockChrome(
                    visible = true, selectedIndex = 1, dark = false,
                    dockDark = false, dockForeground = null, dockSampleBrightness = 1f,
                    buttonHidden = false, config = defaultConfig(),
                    couiBackdrop = null, plusBackdrop = source,
                    onSelect = select, onOpenMenu = menu,
                    fallback = fallback, phase = StartupPhase.FullQuality
                )
            }
        }
        compose.waitForIdle()
        fun commits() = ShadowLog.getLogsForTag("SleepDownCOUI").count { it.msg.startsWith("Dock commit:") }
        val initial = commits()
        assertTrue(initial > 0)
        for (value in 5..16) {
            compose.runOnIdle { radius.intValue = value }
            compose.waitForIdle()
        }
        assertEquals("Course consumers must receive the live preview", 16f, receivedRadius, 0f)
        assertEquals("The course provider must not invalidate the native Dock", initial, commits())
    }
}

@Composable
private fun NativeCoursePreviewConsumer(receive: (Pair<Float, Float>) -> Unit) {
    val mode = requireNotNull(LocalNativeCourseCardMode.current)
    val preview = mode.tintStrength to mode.blurRadiusPx
    SideEffect { receive(preview) }
}

@Composable
private fun CourseSamplerConsumer(receive: (Float) -> Unit) {
    val sampler = LocalSharedCourseBackdrop.current
    SideEffect { receive(requireNotNull(sampler).radiusPx) }
}
