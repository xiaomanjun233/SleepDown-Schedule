package com.xiaomanjun.sleepdownschedule.glass

import com.xiaomanjun.sleepdownschedule.core.performance.*
import org.junit.Assert.*
import org.junit.Test

class MaterialPolicyTest {
    @Test fun switchingOnlyDowngradesQualityBeyondTenCardsAndRestoresAtRest() {
        for (level in AppMaterialLevel.entries) {
            assertEquals(level, effectiveMaterialLevel(level, densePageNeedsPerformance(10, true)))
            assertEquals(level, effectiveMaterialLevel(level, densePageNeedsPerformance(30, false)))
            assertEquals(if (level == AppMaterialLevel.QUALITY) AppMaterialLevel.PERFORMANCE else level,
                effectiveMaterialLevel(level, densePageNeedsPerformance(11, true)))
        }
    }
    @Test fun superModeCannotBeBypassedByRestoredSettingsOrWallpaper() {
        val policy = EffectiveMaterialPolicy(AppMaterialLevel.SUPER_PERFORMANCE)
        for (glass in listOf(false, true)) for (gaussian in listOf(false, true)) {
            assertFalse(policy.courseSamples(glass, gaussian, true))
        }
        assertFalse(policy.samples(MaterialUsage.CONTROL))
        assertTrue(policy.samples(MaterialUsage.SCENE_BLUR))
        assertTrue(policy.simplifiedProgressiveBlur)
        assertEquals(AppMaterialLevel.SUPER_PERFORMANCE,
            restoredMaterialLevel(AppMaterialLevel.SUPER_PERFORMANCE, AppMaterialLevel.QUALITY))
    }
    @Test fun manualMaterialSwitchesStopCourseRecordingInOtherGradesToo() {
        for (level in listOf(AppMaterialLevel.QUALITY, AppMaterialLevel.PERFORMANCE)) {
            val policy = EffectiveMaterialPolicy(level)
            assertFalse(policy.courseSamples(false, false, true))
            assertFalse(policy.courseSamples(true, true, false))
            assertTrue(policy.courseSamples(true, false, true))
            assertTrue(policy.courseSamples(false, true, true))
        }
    }
    @Test fun opacityMappingIsFiniteMonotonicAndBounded() {
        val policy = EffectiveMaterialPolicy(AppMaterialLevel.SUPER_PERFORMANCE)
        assertEquals(0.55f, policy.opacity(0f, 24f), 0.001f)
        assertEquals(1f, policy.opacity(24f, 24f), 0.001f)
        assertTrue(policy.opacity(12f, 24f) < policy.opacity(18f, 24f))
        assertTrue(policy.opacity(Float.NaN, 24f).isFinite())
    }
}
