package com.xiaomanjun.sleepdownschedule.core.remoteconfig

import kotlinx.serialization.Serializable

/** Optional policy keeps pre-policy bootstrap caches compatible. */
@Serializable
data class RemoteAiPolicy(
    val hidden: Boolean = false,
    val displayName: String = "",
    val allowedReasoning: List<String> = emptyList(),
    val defaultReasoning: String = "medium",
    val lockReasoning: Boolean = false
)
