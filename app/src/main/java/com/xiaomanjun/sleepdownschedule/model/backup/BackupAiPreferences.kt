package com.xiaomanjun.sleepdownschedule.model.backup

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

const val DefaultAiImportHistoryRetentionDays = 30
val AiImportHistoryRetentionOptions = listOf(7, 30, 90, 0)

@Serializable
@SerialName("com.xiaomanjun.sleepdownschedule.feature.backup.BackupDayAgentPreferences")
data class BackupDayAgentPreferences(
    val hasDecision: Boolean,
    val enabled: Boolean,
    val dailyAiEnabled: Boolean,
    val weatherEnabled: Boolean,
    val memoryEnabled: Boolean,
    val memory: String,
    val memoryTurnDay: String?,
    val memoryTurnCount: Int,
    val memoryLastAgentUpdateDay: String?,
    val appliedActionsBySchedule: Map<String, List<String>> = emptyMap(),
    val weekAssistantEnabled: Boolean = true
)

@Serializable
@SerialName("com.xiaomanjun.sleepdownschedule.feature.backup.BackupAiImportPreferences")
data class BackupAiImportPreferences(
    val selectedProviderId: String,
    val managedFreeOfferDecision: String? = null,
    val providers: List<BackupAiProvider> = emptyList()
)

@Serializable
@SerialName("com.xiaomanjun.sleepdownschedule.feature.backup.BackupAiProvider")
data class BackupAiProvider(
    val id: String,
    val displayName: String,
    val providerType: String,
    val baseUrl: String,
    val model: String,
    val authType: String,
    val supportsImageInput: Boolean,
    val supportsPdfFileInput: Boolean,
    val supportsJsonSchema: Boolean,
    val supportsJsonMode: Boolean,
    val supportsFileUpload: Boolean,
    val supportsResponses: Boolean,
    val supportsVision: Boolean,
    val supportsPdfDirect: Boolean,
    val endpointStyle: String,
    val structuredOutputMode: String,
    val inputMode: String,
    val availableModels: List<String> = emptyList(),
    val reasoningEffort: String
)

@Serializable
@SerialName("com.xiaomanjun.sleepdownschedule.feature.backup.BackupAiImportHistoryEntry")
data class BackupAiImportHistoryEntry(
    val id: String,
    val createdAt: Long,
    val title: String,
    val prompt: String,
    val sourceSummary: String,
    val payload: String,
    val contextAssetId: String? = null
)
