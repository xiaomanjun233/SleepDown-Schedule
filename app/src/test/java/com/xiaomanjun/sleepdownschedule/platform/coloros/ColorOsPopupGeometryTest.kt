package com.xiaomanjun.sleepdownschedule.platform.coloros

import org.junit.Assert.assertEquals
import org.junit.Test

class ColorOsPopupGeometryTest {
    @Test fun shortcutsAlignToSourceEdgeAndFlipBeforeClamping() {
        assertEquals(80, shortcutMenuLeft(80, 140, 220, 8, 392, false))
        assertEquals(130, shortcutMenuLeft(290, 350, 220, 8, 392, false))
        assertEquals(8, shortcutMenuLeft(0, 60, 220, 8, 392, false))
        assertEquals(8, shortcutMenuLeft(80, 140, 500, 8, 392, false))
    }

    @Test fun rtlStartsFromRightEdgeAndFlipsOnTheLeft() {
        assertEquals(130, shortcutMenuLeft(290, 350, 220, 8, 392, true))
        assertEquals(80, shortcutMenuLeft(80, 140, 220, 8, 392, true))
    }
}
