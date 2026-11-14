package com.xiaomanjun.sleepdownschedule.platform.coloros

import androidx.compose.ui.geometry.Rect
import org.junit.Assert.*
import org.junit.Test

class ColorOsCardLightTest {
    @Test fun projectionCoversReceiverAndClipsOnlyToTheViewport() {
        val card = Rect(100f, 10f, 300f, 180f)
        val receiver = Rect(50f, 200f, 350f, 260f)
        val band = cardLightDrawingBounds(card, receiver, 120f)
        assertTrue(band.left <= receiver.left && band.right >= receiver.right)
        assertEquals(card.bottom, band.top, 0f)
        assertEquals(300f, band.bottom, 0f)
        val viewport = Rect(0f, 0f, 400f, 275f)
        assertEquals(band.intersect(viewport), cardLightDrawingBounds(card, receiver, 120f, viewport))
    }
}
