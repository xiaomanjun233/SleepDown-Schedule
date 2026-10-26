package com.xiaomanjun.sleepdownschedule.core.ui.interaction

import androidx.compose.ui.geometry.Rect
import com.xiaomanjun.sleepdownschedule.feature.course.editor.legacyCourseEditorMorphSpec
import com.xiaomanjun.sleepdownschedule.feature.home.curveValues
import com.xiaomanjun.sleepdownschedule.feature.home.homeDropletFrame
import com.xiaomanjun.sleepdownschedule.feature.home.overlay.legacyPersonalizationMorphSpec
import com.xiaomanjun.sleepdownschedule.glass.*
import org.junit.Assert.*
import org.junit.Test

class MorphCurveContinuityTest {
    private val source = Rect(790f, 160f, 930f, 300f)
    private val target = Rect(40f, 440f, 960f, 1700f)

    private fun route(kind: Int, from: Rect, to: Rect, startCorner: Float = 24f,
        endCorner: Float = 32f): (Float, Boolean) -> FloatArray = when (kind) {
        0, 3 -> legacyCourseEditorMorphSpec(from, to, startCorner, endCorner, 180f, 6f, 5f,
            hasSourceTransform = true, preloadedContent = true, earlyContentHandoff = kind != 3,
            earlyOpeningContentHandoff = true).let { spec ->
            { p, closing -> spec.frame(input(from, to, p, closing)).curveValues(0f) }
        }
        1, 4 -> legacyPersonalizationMorphSpec(from, to, 18f, 36f, 72f, 90f, endCorner, 1f, 12f,
            6f, 14f, preloadedContent = true, earlyContentHandoff = kind != 4,
            earlyOpeningContentHandoff = true).let { spec ->
            { p, closing -> spec.frame(input(from, to, p, closing)).curveValues(0f) }
        }
        else -> { p, closing -> homeDropletFrame(from, to, p, closing, 1f, endCorner).curveValues() }
    }

    private fun input(from: Rect, to: Rect, p: Float, closing: Boolean) = LiquidMorphInput(from, to, p,
        if (closing) LiquidMorphDirection.Closing else LiquidMorphDirection.Opening)
    private fun values(curve: MorphCurveContinuity, route: (Float, Boolean) -> FloatArray, p: Float,
        closing: Boolean): FloatArray = route(p, closing).mapIndexed { i, v -> curve.value(i, p, v) }.toFloatArray()
    private fun bind(curve: MorphCurveContinuity, kind: Int): (Float, Boolean) -> FloatArray {
        val original = route(kind, source, target)
        curve.bind(original) { current, closing ->
            val captured = Rect(current[0], current[1], current[2], current[3])
            val replay = route(kind, if (closing) source else captured, if (closing) captured else target,
                if (closing) 24f else current[4], if (closing) current[4] else 32f)
            val frames: (Float) -> FloatArray = { p -> replay(p, closing) }
            frames
        }
        return original
    }

    @Test fun nativeEarlyOpeningHandoffStillCapturesEveryInterruptedVisibleFrame() {
        for (kind in 3..4) {
            val curve = MorphCurveContinuity()
            val original = bind(curve, kind)
            var p = 0.35f
            var closing = false
            repeat(12) {
                val visible = values(curve, original, p, closing)
                closing = !closing
                val duration = if (closing) 440 else 380
                curve.redirect(p, closing, duration)
                assertArrayEquals(visible, values(curve, original, p, closing), 0f)
                assertEquals(duration, curve.durationMillis)
                p = if (closing) p * 0.72f else p + (1f - p) * 0.28f
            }
        }
    }

    @Test fun completeFlightsKeepEveryOriginalOpenAndReturnSample() {
        for (kind in 0..2) {
            val curve = MorphCurveContinuity()
            val original = bind(curve, kind)
            curve.redirect(0f, false, 380)
            for (i in 0..100) assertArrayEquals(original(i / 100f, false), values(curve, original, i / 100f, false), 0f)
            curve.redirect(1f, true, 440)
            assertFalse(curve.isActive)
            for (i in 100 downTo 0) assertArrayEquals(original(i / 100f, true), values(curve, original, i / 100f, true), 0f)
            assertFalse(original(0.5f, false).contentEquals(original(0.5f, true)))
        }
    }

    @Test fun interruptedReturnPlaysTheOriginalExitCurveFromTheCapturedRect() {
        for (kind in 0..2) for (start in listOf(0.13f, 0.35f, 0.72f)) {
            val curve = MorphCurveContinuity()
            val original = bind(curve, kind)
            val current = original(start, false)
            val captured = Rect(current[0], current[1], current[2], current[3])
            val expectedRoute = route(kind, source, captured, endCorner = current[4])
            curve.redirect(start, true, 440)
            assertEquals(440, curve.durationMillis)
            assertArrayEquals(current, values(curve, original, start, true), 0f)
            for (step in 1..100) {
                val local = 1f - step / 100f
                val actual = values(curve, original, start * local, true)
                val expected = expectedRoute(local, true)
                // Rect follows the authored return throughout, with no old momentum or phase.
                for (channel in 0..3) assertEquals("route=$kind, step=$step, channel=$channel",
                    expected[channel], actual[channel], 0.002f)
            }
        }
    }

    @Test fun repeatedDirectionChangesCaptureTheActualFrameAndUseTheOriginalDuration() {
        for (kind in 0..2) {
            val curve = MorphCurveContinuity()
            val original = bind(curve, kind)
            var p = 0.35f
            var closing = false
            repeat(12) {
                val before = values(curve, original, p, closing)
                closing = !closing
                val duration = if (closing) 440 else 380
                curve.redirect(p, closing, duration)
                assertArrayEquals(before, values(curve, original, p, closing), 0f)
                assertEquals(duration, curve.durationMillis)
                val endpoint = if (closing) 0f else 1f
                p += (endpoint - p) * 0.1f
                assertTrue(values(curve, original, p, closing).all { value -> value.isFinite() })
            }
            val end = if (closing) 0f else 1f
            val result = values(curve, original, end, closing)
            val expected = original(end, closing)
            for (channel in 0..3) assertEquals(expected[channel], result[channel], 0.002f)
        }
    }

    @Test fun obsoleteRouteCannotPullTheNewFlightAfterItWasRedirected() {
        val curve = MorphCurveContinuity()
        bind(curve, 0)
        curve.redirect(0.5f, true, 440)
        for (step in 0..100) {
            val p = 0.5f * (1f - step / 100f)
            assertEquals(curve.value(0, p, 0f), curve.value(0, p, 100_000f), 0f)
            assertTrue(curve.value(0, p, Float.NaN).isFinite())
        }
    }

    @Test fun nearEndpointRedirectRemainsFiniteAndKeepsTheReturnDuration() {
        for (p in listOf(0.001f, 0.01f, 0.99f, 0.999f)) {
            val curve = MorphCurveContinuity()
            val original = bind(curve, 0)
            curve.redirect(p, true, 440)
            assertEquals(440, curve.durationMillis)
            for (step in 0..100) assertTrue(values(curve, original, p * (1f - step / 100f), true).all { it.isFinite() })
            assertArrayEquals(original(0f, true).take(4).toFloatArray(),
                values(curve, original, 0f, true).take(4).toFloatArray(), 0.001f)
        }
    }

    @Test fun oneCardCannotAlterAnotherCardsOriginalReturn() {
        val a = MorphCurveContinuity()
        val b = MorphCurveContinuity()
        bind(a, 0)
        val original = bind(b, 0)
        b.redirect(1f, true, 440)
        for (i in 1..99) {
            a.redirect(i / 100f, i % 2 == 1, 440)
            val p = 1f - i / 100f
            assertArrayEquals(original(p, true), values(b, original, p, true), 0f)
        }
    }

    @Test fun disposingOldBindingDoesNotClearItsReplacement() {
        val curve = MorphCurveContinuity()
        val old = bind(curve, 0)
        val replacement = bind(curve, 1)
        curve.unbind(old)
        curve.redirect(0.5f, true, 440)
        assertTrue(curve.isActive)
        curve.unbind(replacement)
        assertFalse(curve.isActive)
    }
}
