package com.xiaomanjun.sleepdownschedule

import org.junit.Assert.assertEquals
import org.junit.Test

class DockImeCompensationTest {
    @Test
    fun hiddenKeyboardDoesNotMoveDock() {
        assertEquals(0, dockImeCompensationPx(imeBottomPx = 0, systemBottomPx = 72))
        assertEquals(0, dockImeCompensationPx(imeBottomPx = 72, systemBottomPx = 72))
    }

    @Test
    fun visibleKeyboardOnlyCompensatesImeBeyondSystemBar() {
        assertEquals(828, dockImeCompensationPx(imeBottomPx = 900, systemBottomPx = 72))
    }
}
