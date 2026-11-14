package com.xiaomanjun.sleepdownschedule.feature.agent

import com.xiaomanjun.sleepdownschedule.feature.importing.AiImportFile

internal data class AgentTaskInput(val text: String, val image: AgentImageAttachment? = null,
    val images: List<AgentImageAttachment> = emptyList(),
    val files: List<AiImportFile> = emptyList()) {
    val allImages get() = listOfNotNull(image) + images
}
