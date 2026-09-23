package com.xiaomanjun.sleepdownschedule.feature.importing

import org.junit.Assert.assertEquals
import org.junit.Test

class SchoolDockInsetsTest {
    @Test fun fullWindowAddsTheKeyboardOnce() {
        assertEquals(800, schoolDockBottomInsetPx(800, 60, 2400, 2400))
    }

    @Test fun resizedOrPannedHostDoesNotAddAnotherKeyboard() {
        assertEquals(0, schoolDockBottomInsetPx(800, 60, 2400, 1600))
        assertEquals(60, schoolDockBottomInsetPx(800, 60, 2400, 1660))
        assertEquals(300, schoolDockBottomInsetPx(800, 60, 2400, 1900))
        assertEquals(0, schoolDockBottomInsetPx(800, 60, 2400, 1400))
    }

    @Test fun hiddenKeyboardAndMissingMeasurementPreserveSafeInsets() {
        assertEquals(60, schoolDockBottomInsetPx(0, 60, 2400, 2400))
        assertEquals(800, schoolDockBottomInsetPx(800, 60, 0, 0))
    }
}
