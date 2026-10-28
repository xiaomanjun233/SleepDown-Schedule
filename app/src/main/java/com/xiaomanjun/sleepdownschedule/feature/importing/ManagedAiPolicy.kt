package com.xiaomanjun.sleepdownschedule.feature.importing

import com.xiaomanjun.sleepdownschedule.core.remoteconfig.RemoteAiPolicy

internal data class ManagedReasoningPolicy(
    val effort: AiReasoningEffort,
    val options: List<AiReasoningEffort>,
    val locked: Boolean
)

/** Resolve again for every candidate, including fallbacks with different effort limits. */
internal fun RemoteAiPolicy.resolveManagedReasoning(requested: AiReasoningEffort): ManagedReasoningPolicy? {
    val options = allowedReasoning.mapNotNull { value ->
        AiReasoningEffort.entries.firstOrNull { it.apiValue == value }
    }.distinct()
    val fallback = options.firstOrNull { it.apiValue == defaultReasoning } ?: return null
    if (hidden || options.isEmpty()) return null
    return ManagedReasoningPolicy(
        effort = if (lockReasoning) fallback else requested.takeIf { it in options } ?: fallback,
        options = options,
        locked = lockReasoning
    )
}
