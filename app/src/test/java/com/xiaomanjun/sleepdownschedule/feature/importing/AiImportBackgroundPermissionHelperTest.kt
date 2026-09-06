package com.xiaomanjun.sleepdownschedule.feature.importing

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiImportBackgroundPermissionHelperTest {
    @Test
    fun tipOnlyShowsWhenOptimizationIsActiveAndTipHasNeverBeenShown() {
        assertTrue(
            AiImportBackgroundPermissionHelper.shouldShowTip(
                batteryOptimizationIgnored = false,
                tipAlreadyShown = false
            )
        )
        assertFalse(
            AiImportBackgroundPermissionHelper.shouldShowTip(
                batteryOptimizationIgnored = false,
                tipAlreadyShown = true
            )
        )
        assertFalse(
            AiImportBackgroundPermissionHelper.shouldShowTip(
                batteryOptimizationIgnored = true,
                tipAlreadyShown = false
            )
        )
    }
}
