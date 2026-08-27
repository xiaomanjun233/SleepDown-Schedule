package com.xiaomanjun.sleepdownschedule.glass.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RenderEffectCompatTest {
    @Test
    fun platformBlurIsUsedOnlyOnApi31AndAbove() {
        assertFalse(shouldUsePlatformBlurEffect(6f, 6f, sdkInt = 30))
        assertTrue(shouldUsePlatformBlurEffect(6f, 6f, sdkInt = 31))
        assertTrue(shouldUsePlatformBlurEffect(6f, 6f, sdkInt = 37))
    }

    @Test
    fun zeroAndNearZeroRadiiDoNotAllocateRenderEffects() {
        assertFalse(shouldUsePlatformBlurEffect(0f, 6f, sdkInt = 37))
        assertFalse(shouldUsePlatformBlurEffect(6f, 0.01f, sdkInt = 37))
        assertTrue(shouldUsePlatformBlurEffect(0.02f, 0.02f, sdkInt = 37))
    }
}
