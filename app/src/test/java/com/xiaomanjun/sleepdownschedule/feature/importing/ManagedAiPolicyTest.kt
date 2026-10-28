package com.xiaomanjun.sleepdownschedule.feature.importing

import com.xiaomanjun.sleepdownschedule.core.remoteconfig.RemoteAiPolicy
import org.junit.Assert.*
import org.junit.Test
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive

class ManagedAiPolicyTest {
    @Test fun chatRequestsCarryServerSelectedEffort() {
        val settings = AiImportSettings(AiProviderPresets.dailyFree.copy(
            reasoningEffort = AiReasoningEffort.LOW,
            managedReasoningOptions = listOf(AiReasoningEffort.LOW)))
        val body = buildJsonObject { putChatSamplingAndReasoning(settings.toProviderConfig()) }
        assertEquals("low", body["reasoning_effort"]?.jsonPrimitive?.content)
        assertFalse(body.containsKey("temperature"))
    }

    @Test fun unsupportedSelectionUsesServerDefault() {
        val policy = RemoteAiPolicy(allowedReasoning = listOf("none", "low"), defaultReasoning = "low")
        val resolved = policy.resolveManagedReasoning(AiReasoningEffort.MAX)!!
        assertEquals(AiReasoningEffort.LOW, resolved.effort)
        assertEquals(listOf(AiReasoningEffort.NONE, AiReasoningEffort.LOW), resolved.options)
    }

    @Test fun lockedPolicyOverridesSavedPreferenceAndHidesChoices() {
        val policy = RemoteAiPolicy(allowedReasoning = listOf("low", "medium"), defaultReasoning = "low", lockReasoning = true)
        val resolved = policy.resolveManagedReasoning(AiReasoningEffort.MEDIUM)!!
        val profile = AiProviderPresets.dailyFree.copy(reasoningEffort = resolved.effort,
            managedReasoningOptions = resolved.options, managedReasoningLocked = resolved.locked)
        assertEquals(AiReasoningEffort.LOW, profile.reasoningEffort)
        assertTrue(AiProviderPresets.reasoningEfforts(profile).isEmpty())
    }

    @Test fun hiddenOrUnrecognizedPolicyCannotCreateCandidate() {
        assertNull(RemoteAiPolicy(hidden = true, allowedReasoning = listOf("low"), defaultReasoning = "low")
            .resolveManagedReasoning(AiReasoningEffort.LOW))
        assertNull(RemoteAiPolicy(allowedReasoning = listOf("future"), defaultReasoning = "future")
            .resolveManagedReasoning(AiReasoningEffort.LOW))
    }

    @Test fun customProvidersRetainTheirOwnOptions() {
        val original = AiProviderPresets.openAI
        assertEquals(AiProviderPresets.reasoningEfforts(original), AiProviderPresets.reasoningEfforts(
            original.copy(managedReasoningOptions = listOf(AiReasoningEffort.LOW), managedReasoningLocked = true)))
    }

    @Test fun allowedPreferenceRemainsUnchanged() {
        val policy = RemoteAiPolicy(allowedReasoning = listOf("none", "high"), defaultReasoning = "none")
        assertEquals(AiReasoningEffort.HIGH, policy.resolveManagedReasoning(AiReasoningEffort.HIGH)!!.effort)
    }
}
