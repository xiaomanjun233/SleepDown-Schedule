package com.xiaomanjun.sleepdownschedule.feature.agent

internal data class AgentTaskInput(val text: String, val image: AgentImageAttachment? = null,
    val images: List<AgentImageAttachment> = emptyList()) {
    val allImages get() = listOfNotNull(image) + images
}
