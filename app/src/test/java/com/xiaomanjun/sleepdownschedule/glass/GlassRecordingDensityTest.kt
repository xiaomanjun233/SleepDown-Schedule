package com.xiaomanjun.sleepdownschedule.glass

import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test

class GlassRecordingDensityTest {
    @Test fun recordedDpConversionBreaksTheLayoutScopeDensityCycle() {
        val canvas = CanvasDrawScope()
        // LayoutNodeDrawScope delegates to CanvasDrawScope. A recording can install that
        // owner as CanvasDrawScope's Density, creating the cycle in the device crash trace.
        val owner = object : DrawScope by canvas {}
        canvas.drawContext.density = owner
        assertThrows(StackOverflowError::class.java) { owner.density }

        canvas.withRecordingDensity(Density(2.5f, 1.2f)) {
            assertEquals(30f, 12.dp.toPx(), 0.001f)
            assertEquals(2.5f, owner.density, 0.001f)
            assertEquals(1.2f, owner.fontScale, 0.001f)
        }
        assertSame(owner, canvas.drawContext.density)
    }

    @Test fun nestedRecordingRestoresDensityEvenWhenDrawingThrows() {
        val canvas = CanvasDrawScope()
        val original = Density(1.5f)
        val outer = Density(2f, 1.1f)
        canvas.drawContext.density = original

        canvas.withRecordingDensity(outer) {
            assertThrows(IllegalStateException::class.java) {
                withRecordingDensity(Density(3f)) {
                    assertEquals(18f, 6.dp.toPx(), 0.001f)
                    error("draw interrupted")
                }
            }
            assertSame(outer, drawContext.density)
            assertEquals(12f, 6.dp.toPx(), 0.001f)
        }
        assertSame(original, canvas.drawContext.density)
    }
}
