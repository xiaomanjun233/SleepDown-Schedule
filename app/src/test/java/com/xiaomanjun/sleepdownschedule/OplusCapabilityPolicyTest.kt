package com.xiaomanjun.sleepdownschedule

import com.xiaomanjun.sleepdownschedule.transition.OplusCapabilityFacts
import com.xiaomanjun.sleepdownschedule.transition.OplusCapabilityPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OplusCapabilityPolicyTest {
    private val supported = OplusCapabilityFacts(
        runtimeClassPresent = true,
        requiredMethodsPresent = true,
        version = 40_003,
        featureEnabled = true,
        sourceReady = true,
        softwareBitmapReady = true,
        unsupportedWindowMode = false,
        opaqueDestinationCandidate = true
    )

    @Test
    fun runtimeFactsEnableAValidDeviceWithoutBrandOrRomChecks() {
        assertTrue(OplusCapabilityPolicy.evaluate(supported).supported)
    }

    @Test
    fun everyUnsafeRuntimeFactFallsBackWithAStableReason() {
        val cases = listOf(
            supported.copy(runtimeClassPresent = false) to "runtimeClassMissing",
            supported.copy(requiredMethodsPresent = false) to "requiredApiMissing",
            supported.copy(version = 37_000) to "unsupportedVersion",
            supported.copy(featureEnabled = false) to "featureDisabled",
            supported.copy(sourceReady = false) to "sourceNotReady",
            supported.copy(softwareBitmapReady = false) to "softwareBitmapUnavailable",
            supported.copy(unsupportedWindowMode = true) to "unsupportedWindowMode",
            supported.copy(opaqueDestinationCandidate = false) to "destinationNotOpaqueCandidate"
        )
        cases.forEach { (facts, reason) ->
            val decision = OplusCapabilityPolicy.evaluate(facts)
            assertFalse(reason, decision.supported)
            assertEquals(reason, decision.reason)
        }
    }

    @Test
    fun missingOptionalFeatureApiDoesNotRejectAnOtherwiseValidRuntime() {
        assertTrue(OplusCapabilityPolicy.evaluate(supported.copy(featureEnabled = null)).supported)
    }
}
