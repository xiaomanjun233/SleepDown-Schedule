package com.xiaomanjun.sleepdownschedule.transition

internal const val OplusMinimumViewSeamlessVersion = 37_000

data class OplusCapabilityFacts(
    val runtimeClassPresent: Boolean,
    val requiredMethodsPresent: Boolean,
    val version: Int,
    val featureEnabled: Boolean?,
    val sourceReady: Boolean,
    val softwareBitmapReady: Boolean,
    val unsupportedWindowMode: Boolean,
    val opaqueDestinationCandidate: Boolean
)

data class OplusCapabilityDecision(val supported: Boolean, val reason: String)

object OplusCapabilityPolicy {
    fun evaluate(facts: OplusCapabilityFacts): OplusCapabilityDecision = when {
        !facts.runtimeClassPresent -> OplusCapabilityDecision(false, "runtimeClassMissing")
        !facts.requiredMethodsPresent -> OplusCapabilityDecision(false, "requiredApiMissing")
        facts.version <= OplusMinimumViewSeamlessVersion ->
            OplusCapabilityDecision(false, "unsupportedVersion")
        facts.featureEnabled == false -> OplusCapabilityDecision(false, "featureDisabled")
        !facts.sourceReady -> OplusCapabilityDecision(false, "sourceNotReady")
        !facts.softwareBitmapReady -> OplusCapabilityDecision(false, "softwareBitmapUnavailable")
        facts.unsupportedWindowMode -> OplusCapabilityDecision(false, "unsupportedWindowMode")
        !facts.opaqueDestinationCandidate ->
            OplusCapabilityDecision(false, "destinationNotOpaqueCandidate")
        else -> OplusCapabilityDecision(true, "supported")
    }
}
