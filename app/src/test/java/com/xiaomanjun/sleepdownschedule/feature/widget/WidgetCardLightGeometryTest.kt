package com.xiaomanjun.sleepdownschedule.feature.widget

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetCardLightGeometryTest {
    @Test fun smallAndLargePreviewsBothIlluminateTheEntireActionRow() {
        val controls = Rect(16f, 360f, 384f, 410f)
        for (card in listOf(Rect(110f, 100f, 290f, 280f), Rect(40f, 100f, 360f, 320f))) {
            val depth = widgetLightProjectionDepth(card, controls, 16f)
            assertEquals(controls.bottom + 16f, card.bottom + depth, .001f)
        }
    }

    @Test fun scrollingOrMovingThePaneDoesNotChangeTheLightRange() {
        val card = Rect(110f, 100f, 290f, 280f)
        val controls = Rect(16f, 360f, 384f, 410f)
        val offset = Offset(72f, -140f)
        assertEquals(widgetLightProjectionDepth(card, controls, 16f),
            widgetLightProjectionDepth(card.translate(offset), controls.translate(offset), 16f), .001f)
    }

    @Test fun unmeasuredPreviewDoesNotCreateAProjection() {
        assertEquals(0f, widgetLightProjectionDepth(Rect.Zero, Rect(0f, 20f, 100f, 60f), 16f), 0f)
    }
}
