package com.xiaomanjun.sleepdownschedule.feature.home.overlay

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.LocalNativeCourseCardMode
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.LocalNativeMaterialSurfaceRenderer
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.NativeCourseCardMode
import com.xiaomanjun.sleepdownschedule.feature.home.calculateHomeAdaptiveMetrics
import com.xiaomanjun.sleepdownschedule.model.HomeMode
import com.xiaomanjun.sleepdownschedule.model.defaultConfig
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Tests real host ownership/timing with a small form; vendor drawing still needs the device. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomeMorphPreloadTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun nativeControlsMountBeforeMotionAndRetainUntilReturnCompletes() = verifyPreload(cardOnly = false)
    @Test fun independentlyEnabledCourseMaterialAlsoPreloads() = verifyPreload(cardOnly = true)
    @Test fun cancelWhilePreparingDoesNotReopenAReleasedForm() = verifyPreload(cardOnly = false, cancelPreparing = true)
    @Test fun interruptedPersonalizationJoinsBothDirectionsWithoutRemounting() = verifyPreload(cardOnly = false, interruptOpening = true)

    private fun frames(count: Int) { repeat(count) { compose.mainClock.advanceTimeByFrame(); compose.waitForIdle() } }

    private fun verifyPreload(cardOnly: Boolean, cancelPreparing: Boolean = false, interruptOpening: Boolean = false) {
        compose.mainClock.autoAdvance = false
        val request = mutableStateOf<HomeAnchoredOverlayRequest?>(HomeAnchoredOverlayRequest(
            HomeAnchoredOverlayKind.Personalize, Rect(240f, 40f, 288f, 88f)))
        val initialRequest = request.value
        val motion = HomeAnchoredMorphState()
        val gate = CompletableDeferred<Unit>()
        if (!cancelPreparing) gate.complete(Unit)
        var mounted = 0
        var mountCount = 0
        var mountedAt: HomeAnchoredOverlayPhase? = null
        val courseMode = NativeCourseCardMode(mutableStateOf(true), {}, mutableFloatStateOf(0.5f),
            mutableFloatStateOf(1f), 24f, { _, _ -> }, { _, _ -> }, {})
        compose.setContent {
            CompositionLocalProvider(
                LocalNativeMaterialSurfaceRenderer provides if (cardOnly) null else { _, _ -> false },
                LocalNativeCourseCardMode provides if (cardOnly) courseMode else null
            ) {
                HomeAnchoredMorphOverlayHost(
                    request = request.value, motionState = motion, backdrop = null, config = defaultConfig(),
                    addActions = emptyList(), homeMode = HomeMode.Week, onHomeModeChange = {},
                    adaptiveMetrics = calculateHomeAdaptiveMetrics(393, 852, 24.dp, 16.dp, 1f),
                    modifier = Modifier.fillMaxSize(), awaitOpeningGate = { gate.await() },
                    onDismissRequest = { request.value = null }, sourceContent = { _, modifier -> Box(modifier) },
                    personalizeContent = { modifier ->
                        DisposableEffect(Unit) {
                            mounted++
                            mountCount++
                            mountedAt = motion.phase
                            onDispose { mounted-- }
                        }
                        Box(modifier)
                    }
                )
            }
        }
        frames(8)
        compose.runOnIdle {
            assertEquals(1, mounted)
            assertEquals(HomeAnchoredOverlayPhase.Preparing, mountedAt)
            assertEquals(if (cancelPreparing) HomeAnchoredOverlayPhase.Preparing else HomeAnchoredOverlayPhase.Opening, motion.phase)
        }
        if (interruptOpening) {
            repeat(6) { turn ->
                compose.runOnIdle { request.value = if (turn % 2 == 0) null else initialRequest }
                frames(2)
                compose.runOnIdle {
                    assertEquals(if (turn % 2 == 0) HomeAnchoredOverlayPhase.Closing else HomeAnchoredOverlayPhase.Opening, motion.phase)
                    assertEquals(true, motion.curveContinuity.isActive)
                    assertEquals(1, mounted)
                    assertEquals(1, mountCount)
                }
            }
        }
        if (!cancelPreparing) {
            compose.mainClock.advanceTimeBy(1500)
            compose.waitForIdle()
            compose.runOnIdle { assertEquals(HomeAnchoredOverlayPhase.Open, motion.phase) }
        }
        compose.runOnIdle { request.value = null }
        frames(2)
        compose.runOnIdle {
            // Cancelling Preparing has no visible travel to retain. A zero clock returns to
            // Idle immediately; releasing the opening gate later must still never reopen it.
            assertEquals(if (cancelPreparing) HomeAnchoredOverlayPhase.Idle else HomeAnchoredOverlayPhase.Closing, motion.phase)
            // Disposal is observed after the pending composition below; visible returns must
            // keep their form while moving, whereas a cancelled zero clock has no such tail.
            if (!cancelPreparing) assertEquals(1, mounted)
        }
        compose.mainClock.advanceTimeBy(1500)
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(0, mounted)
            assertEquals(HomeAnchoredOverlayPhase.Idle, motion.phase)
            gate.complete(Unit)
        }
        frames(2)
        compose.runOnIdle { assertEquals(HomeAnchoredOverlayPhase.Idle, motion.phase) }
    }
}
