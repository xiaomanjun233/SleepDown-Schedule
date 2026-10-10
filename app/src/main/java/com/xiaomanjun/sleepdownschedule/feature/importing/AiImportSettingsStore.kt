package com.xiaomanjun.sleepdownschedule.feature.importing

import com.xiaomanjun.sleepdownschedule.app.config.SleepDownRemoteConfig
import com.xiaomanjun.sleepdownschedule.core.remoteconfig.*
import com.xiaomanjun.sleepdownschedule.*
import com.xiaomanjun.sleepdownschedule.feature.backup.*
import com.xiaomanjun.sleepdownschedule.feature.importing.chatgpt.ChatGptAuthManager
import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.net.URL
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec


object AiImportSettingsStore {
    private const val PrefName = "ai_import_settings"
    private const val KeyProviderId = "provider_id"
    private const val KeyBaseUrl = "base_url"
    private const val KeyModel = "model"
    private const val KeyProviderType = "provider_type"
    private const val KeyImage = "supports_image"
    private const val KeyPdf = "supports_pdf"
    private const val KeyJsonSchema = "supports_json_schema"
    private const val KeyJsonMode = "supports_json_mode"
    private const val KeyFileUpload = "supports_file_upload"
    private const val KeyResponses = "supports_responses"
    private const val KeyEndpointStyle = "endpoint_style"
    private const val KeyStructuredOutputMode = "structured_output_mode"
    private const val KeyInputMode = "input_mode"
    private const val KeyVision = "supports_vision"
    private const val KeyPdfDirect = "supports_pdf_direct"
    private const val KeyAvailableModels = "available_models_v1"
    private const val KeyReasoningEffort = "reasoning_effort"
    private const val KeyEncryptedApiKey = "encrypted_api_key"
    private const val KeyCustomProviders = "custom_provider_profiles_v1"
    private const val KeyManagedFreeOfferDecision = "managed_free_offer_decision_v1"
    private const val ManagedFreeOfferEnabled = "enabled"
    private const val ManagedFreeOfferDeclined = "declined"
    private val settingsJson = Json { ignoreUnknownKeys = true }
    private val changeVersion = MutableStateFlow(0L)
    val changes = changeVersion.asStateFlow()
    private fun apiKeyKey(providerId: String): String = "${KeyEncryptedApiKey}_${providerId}"
    private fun providerKey(key: String, providerId: String): String = "${key}_${providerId}"
    private fun notifyChanged() {
        changeVersion.value = changeVersion.value + 1L
    }

    fun notifyRemoteConfigChanged() = notifyChanged()

    private fun managedFreeSettings(context: Context, prefs: android.content.SharedPreferences): AiImportSettings {
        val effort = runCatching {
            AiReasoningEffort.valueOf(
                prefs.getString(
                    providerKey(KeyReasoningEffort, AiProviderPresets.dailyFree.id),
                    AiProviderPresets.dailyFree.reasoningEffort.name
                ).orEmpty()
            )
        }.getOrDefault(AiProviderPresets.dailyFree.reasoningEffort)
        return SleepDownRemoteConfig.managedFreeSettings(context, effort)
    }

    fun hasUserConfiguredApiKey(context: Context): Boolean {
        val profiles = (AiProviderPresets.all + selectableProfiles(context))
            .distinctBy(AiProviderProfile::id)
            .filterNot { it.id == AiProviderPresets.none.id || AiProviderPresets.isManagedFreeId(it.id) }
        return profiles.any { profile -> loadProvider(context, profile.id).apiKey.isNotBlank() }
    }

    private fun AiImportSettings.isReadyForUse(): Boolean =
        profile.id != AiProviderPresets.none.id &&
            apiKey.isNotBlank() &&
            profile.baseUrl.isNotBlank() &&
            profile.defaultModel.isNotBlank()

    /**
     * Resolves the configuration an AI entry point can actually use.
     *
     * The settings page keeps keys scoped to each provider, so the selected provider can be
     * "关闭" (or an incomplete draft) while a valid bound provider still exists. Entry points
     * must not interpret that state as "no key". If no user provider is usable, the remotely
     * managed daily-free provider is a valid fallback whenever its signed configuration is ready.
     */
    fun resolveAvailableSettings(context: Context): AiImportSettings? {
        val selected = load(context)
        // Account sign-in, expiry and quota errors belong to the selected ChatGPT provider.
        // Never conceal them by using a separately billed API key or the daily-free service.
        if (AiProviderPresets.isChatGptId(selected.profile.id)) return selected
        selected.takeIf { it.isReadyForUse() }?.let { return it }

        val userSettings = selectableProfiles(context)
            .asSequence()
            .filterNot {
                it.id == AiProviderPresets.none.id || AiProviderPresets.isManagedFreeId(it.id) ||
                    AiProviderPresets.isChatGptId(it.id)
            }
            .map { loadProvider(context, it.id) }
            .firstOrNull { it.isReadyForUse() }
        if (userSettings != null) return userSettings

        val prefs = context.getSharedPreferences(PrefName, Context.MODE_PRIVATE)
        // A user who explicitly declined the managed offer must not be silently switched back
        // after a later remote-config refresh. Otherwise an updated bootstrap would overwrite the
        // user's provider choice even though the encrypted credential is correctly cached.
        if (prefs.getString(KeyManagedFreeOfferDecision, null) == ManagedFreeOfferDeclined) return null
        return managedFreeSettings(context, prefs).takeIf { it.isReadyForUse() }
    }

    /** Makes the resolved fallback active so the service and runtime picker read the same model. */
    fun activateAvailableSettings(context: Context): AiImportSettings? {
        val current = load(context)
        if (AiProviderPresets.isChatGptId(current.profile.id)) return current
        if (current.isReadyForUse()) return current
        return resolveAvailableSettings(context)?.also { resolved ->
            if (resolved.profile.id != current.profile.id) save(context, resolved)
        }
    }

    /**
     * Reads the configuration that a network request can actually use. UI settings may display an
     * incomplete selected draft, but request services must fall back to a complete user profile or
     * the signed daily-free configuration instead of sending an empty key.
     */
    fun loadForRuntime(context: Context): AiImportSettings? = resolveAvailableSettings(context)

    fun shouldOfferManagedFreeAi(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PrefName, Context.MODE_PRIVATE)
        if (prefs.contains(KeyManagedFreeOfferDecision)) return false
        val selectedId = load(context).profile.id
        if (selectedId == AiProviderPresets.dailyFree.id || AiProviderPresets.isChatGptId(selectedId)) return false
        return !hasUserConfiguredApiKey(context) && SleepDownRemoteConfig.isManagedFreeAvailable(context)
    }

    fun enableManagedFreeAi(context: Context) {
        val prefs = context.getSharedPreferences(PrefName, Context.MODE_PRIVATE)
        prefs.edit { putString(KeyManagedFreeOfferDecision, ManagedFreeOfferEnabled) }
        save(context, managedFreeSettings(context, prefs))
    }

    fun declineManagedFreeAi(context: Context) {
        context.getSharedPreferences(PrefName, Context.MODE_PRIVATE).edit {
            putString(KeyManagedFreeOfferDecision, ManagedFreeOfferDeclined)
        }
    }

    fun selectableProfiles(context: Context): List<AiProviderProfile> {
        val prefs = context.getSharedPreferences(PrefName, Context.MODE_PRIVATE)
        val entries = readCustomProviders(prefs)
        val namesById = entries.associate { it.id to it.displayName }
        val builtIns = AiProviderPresets.selectable.map { preset ->
            if (AiProviderPresets.isCustomId(preset.id)) {
                preset.copy(displayName = namesById[preset.id] ?: preset.displayName)
            } else {
                preset
            }
        }
        val additionalCustomProfiles = entries
            .filter { entry -> entry.id != AiProviderPresets.custom.id }
            .map { entry -> AiProviderPresets.customProfile(entry.id, entry.displayName) }
        // Retain existing API configurations and ciphertext under their original ID. Fresh
        // installations see account login instead of the former official OpenAI key form.
        val legacyOpenAi = AiProviderPresets.openAI.takeIf {
            prefs.getString(KeyProviderId, null) == it.id ||
                prefs.contains(providerKey(KeyBaseUrl, it.id)) || prefs.contains(apiKeyKey(it.id))
        }
        return builtIns + listOfNotNull(legacyOpenAi) + additionalCustomProfiles
    }

    /**
     * Reads provider configuration for backup without touching encrypted API-key values. The
     * selected provider uses the legacy global keys; other providers use their scoped keys.
     */
    fun exportForBackup(context: Context): BackupAiImportPreferences {
        val prefs = context.getSharedPreferences(PrefName, Context.MODE_PRIVATE)
        val selectedProviderId = prefs.getString(KeyProviderId, AiProviderPresets.none.id).orEmpty()
        val presets = (selectableProfiles(context) + AiProviderPresets.byId(selectedProviderId))
            .distinctBy(AiProviderProfile::id)
        val profiles = presets.map { preset ->
            readProfileWithoutSecret(prefs, preset, preset.id == selectedProviderId)
        }
        return BackupAiImportPreferences(
            selectedProviderId = selectedProviderId.ifBlank { AiProviderPresets.none.id },
            managedFreeOfferDecision = prefs.getString(KeyManagedFreeOfferDecision, null),
            providers = profiles.map { it.toBackupProvider() }
        )
    }

    /** Applies only the non-secret provider fields; existing encrypted API keys are untouched. */
    fun applyBackupPreferences(context: Context, backup: BackupAiImportPreferences) {
        val providerProfiles = backup.providers
            .map { provider -> provider.fromBackupProvider(context) }
            .distinctBy(AiProviderProfile::id)
        val profiles = if (providerProfiles.isEmpty() && backup.selectedProviderId == AiProviderPresets.none.id) {
            listOf(AiProviderPresets.none)
        } else {
            providerProfiles
        }
        val selected = profiles.firstOrNull { it.id == backup.selectedProviderId }
            ?: throw IllegalArgumentException("AI selectedProviderId 不在备份 provider 列表中")
        val customEntries = profiles
            .filter { AiProviderPresets.isCustomId(it.id) }
            .map { AiCustomProviderEntry(it.id, it.displayName.trim().ifBlank { AiProviderPresets.custom.displayName }) }
        val prefs = context.getSharedPreferences(PrefName, Context.MODE_PRIVATE)
        val committed = prefs.edit().apply {
            profiles.forEach { profile ->
                writeProviderSettings(this, AiImportSettings(profile, ""))
            }
            if (customEntries.isEmpty()) remove(KeyCustomProviders)
            else putString(KeyCustomProviders, settingsJson.encodeToString(customEntries))
            putString(KeyProviderId, selected.id)
            writeGlobalSettings(this, selected)
            if (backup.managedFreeOfferDecision == null) {
                remove(KeyManagedFreeOfferDecision)
            } else {
                putString(KeyManagedFreeOfferDecision, backup.managedFreeOfferDecision)
            }
        }.commit()
        check(committed) { "无法提交 AI import preferences" }
        notifyChanged()
    }

    fun createCustomProvider(): AiProviderProfile {
        val id = "${AiProviderPresets.custom.id}:${UUID.randomUUID()}"
        // This remains an in-memory draft until the user enters actual content.
        // Merely opening "add custom provider" must not grow the saved list.
        return AiProviderPresets.customProfile(id, "")
    }

    fun deleteCustomProvider(context: Context, providerId: String): Boolean {
        if (!AiProviderPresets.isCustomId(providerId)) return false
        val prefs = context.getSharedPreferences(PrefName, Context.MODE_PRIVATE)
        val wasActive = prefs.getString(KeyProviderId, AiProviderPresets.none.id) == providerId
        val remainingEntries = readCustomProviders(prefs).filterNot { it.id == providerId }
        prefs.edit {
            putString(KeyCustomProviders, settingsJson.encodeToString(remainingEntries))
            remove(apiKeyKey(providerId))
            listOf(
                KeyBaseUrl,
                KeyModel,
                KeyProviderType,
                KeyImage,
                KeyPdf,
                KeyJsonSchema,
                KeyJsonMode,
                KeyFileUpload,
                KeyResponses,
                KeyEndpointStyle,
                KeyStructuredOutputMode,
                KeyInputMode,
                KeyVision,
                KeyPdfDirect,
                KeyAvailableModels,
                KeyReasoningEffort
            ).forEach { key -> remove(providerKey(key, providerId)) }
        }
        if (wasActive) {
            save(context, AiImportSettings(AiProviderPresets.none, ""))
        }
        return true
    }

    private fun presetFor(context: Context, providerId: String): AiProviderProfile =
        selectableProfiles(context).firstOrNull { it.id == providerId }
            ?: AiProviderPresets.byId(providerId)

    private fun readProfileWithoutSecret(
        prefs: android.content.SharedPreferences,
        preset: AiProviderProfile,
        useGlobalKeys: Boolean
    ): AiProviderProfile {
        if (AiProviderPresets.isManagedFreeId(preset.id)) {
            return preset.copy(
                reasoningEffort = runCatching {
                    AiReasoningEffort.valueOf(
                        prefs.getString(
                            providerKey(KeyReasoningEffort, preset.id),
                            preset.reasoningEffort.name
                        ).orEmpty()
                    )
                }.getOrDefault(preset.reasoningEffort)
            )
        }
        if (!useGlobalKeys && !prefs.contains(providerKey(KeyBaseUrl, preset.id))) return preset
        fun key(name: String): String = if (useGlobalKeys) name else providerKey(name, preset.id)
        val providerType = runCatching {
            AiProviderType.valueOf(prefs.getString(key(KeyProviderType), preset.providerType.name).orEmpty())
        }.getOrDefault(preset.providerType)
        val endpointStyle = runCatching {
            AiEndpointStyle.valueOf(prefs.getString(key(KeyEndpointStyle), preset.endpointStyle.name).orEmpty())
        }.getOrDefault(preset.endpointStyle)
        val structuredOutputMode = runCatching {
            StructuredOutputMode.valueOf(
                prefs.getString(key(KeyStructuredOutputMode), preset.structuredOutputMode.name).orEmpty()
            )
        }.getOrDefault(preset.structuredOutputMode)
        val inputMode = runCatching {
            AiInputMode.valueOf(prefs.getString(key(KeyInputMode), preset.inputMode.name).orEmpty())
        }.getOrDefault(preset.inputMode)
        val capabilities = preset.capabilities.copy(
            supportsImageInput = prefs.getBoolean(key(KeyImage), preset.capabilities.supportsImageInput),
            supportsPdfFileInput = prefs.getBoolean(key(KeyPdf), preset.capabilities.supportsPdfFileInput),
            supportsJsonSchema = prefs.getBoolean(key(KeyJsonSchema), preset.capabilities.supportsJsonSchema),
            supportsJsonMode = prefs.getBoolean(key(KeyJsonMode), preset.capabilities.supportsJsonMode),
            supportsFileUpload = prefs.getBoolean(key(KeyFileUpload), preset.capabilities.supportsFileUpload),
            supportsResponses = prefs.getBoolean(key(KeyResponses), preset.capabilities.supportsResponses)
        )
        val defaultModel = prefs.getString(key(KeyModel), preset.defaultModel).orEmpty()
        val profile = preset.copy(
            providerType = providerType,
            baseUrl = normalizeAiBaseUrlForProvider(
                preset.id,
                prefs.getString(key(KeyBaseUrl), preset.baseUrl).orEmpty()
            ),
            defaultModel = defaultModel,
            capabilities = capabilities,
            endpointStyle = endpointStyle,
            structuredOutputMode = structuredOutputMode,
            inputMode = inputMode,
            supportsVision = prefs.getBoolean(
                key(KeyVision),
                preset.supportsVision || capabilities.supportsImageInput
            ),
            supportsFileUpload = prefs.getBoolean(
                key(KeyFileUpload),
                preset.supportsFileUpload || capabilities.supportsFileUpload
            ),
            supportsPdfDirect = prefs.getBoolean(
                key(KeyPdfDirect),
                preset.supportsPdfDirect || capabilities.supportsPdfFileInput
            ),
            availableModels = readModelIds(
                prefs.getString(key(KeyAvailableModels), null),
                preset.availableModels,
                defaultModel
            ),
            reasoningEffort = runCatching {
                AiReasoningEffort.valueOf(
                    prefs.getString(key(KeyReasoningEffort), preset.reasoningEffort.name).orEmpty()
                )
            }.getOrDefault(preset.reasoningEffort)
        )
        return if (AiProviderPresets.isChatGptId(profile.id)) {
            AiProviderPresets.normalizeChatGptProfile(profile)
        } else profile
    }

    private fun AiProviderProfile.toBackupProvider(): BackupAiProvider = BackupAiProvider(
        id = id,
        displayName = displayName,
        providerType = providerType.name,
        baseUrl = baseUrl,
        model = defaultModel,
        authType = authType.name,
        supportsImageInput = capabilities.supportsImageInput,
        supportsPdfFileInput = capabilities.supportsPdfFileInput,
        supportsJsonSchema = capabilities.supportsJsonSchema,
        supportsJsonMode = capabilities.supportsJsonMode,
        supportsFileUpload = capabilities.supportsFileUpload,
        supportsResponses = capabilities.supportsResponses,
        supportsVision = supportsVision,
        supportsPdfDirect = supportsPdfDirect,
        endpointStyle = endpointStyle.name,
        structuredOutputMode = structuredOutputMode.name,
        inputMode = inputMode.name,
        availableModels = availableModels,
        reasoningEffort = reasoningEffort.name
    )

    private fun BackupAiProvider.fromBackupProvider(context: Context): AiProviderProfile {
        require(id.matches(Regex("[A-Za-z0-9:_-]{1,128}"))) { "AI provider ID 非法" }
        val preset = (selectableProfiles(context) + AiProviderPresets.all).firstOrNull { it.id == id }
            ?: AiProviderPresets.customProfile(id, displayName)
        val providerType = runCatching { AiProviderType.valueOf(this.providerType) }
            .getOrElse { throw IllegalArgumentException("未知 AI providerType: ${this.providerType}") }
        val authType = runCatching { AiAuthType.valueOf(this.authType) }
            .getOrElse { throw IllegalArgumentException("未知 AI authType: ${this.authType}") }
        require(authType != AiAuthType.ChatGptOAuth || AiProviderPresets.isChatGptId(id)) {
            "ChatGPT 登录不能用于自定义接口"
        }
        val endpointStyle = runCatching { AiEndpointStyle.valueOf(this.endpointStyle) }
            .getOrElse { throw IllegalArgumentException("未知 AI endpointStyle: ${this.endpointStyle}") }
        val structuredOutputMode = runCatching { StructuredOutputMode.valueOf(this.structuredOutputMode) }
            .getOrElse { throw IllegalArgumentException("未知 AI structuredOutputMode: ${this.structuredOutputMode}") }
        val inputMode = runCatching { AiInputMode.valueOf(this.inputMode) }
            .getOrElse { throw IllegalArgumentException("未知 AI inputMode: ${this.inputMode}") }
        val reasoningEffort = runCatching { AiReasoningEffort.valueOf(this.reasoningEffort) }
            .getOrElse { throw IllegalArgumentException("未知 AI reasoningEffort: ${this.reasoningEffort}") }
        val capabilities = preset.capabilities.copy(
            supportsImageInput = supportsImageInput,
            supportsPdfFileInput = supportsPdfFileInput,
            supportsJsonSchema = supportsJsonSchema,
            supportsJsonMode = supportsJsonMode,
            supportsFileUpload = supportsFileUpload,
            supportsResponses = supportsResponses
        )
        val profile = preset.copy(
            id = id,
            displayName = displayName,
            providerType = providerType,
            baseUrl = normalizeAiBaseUrlForProvider(id, baseUrl),
            defaultModel = model,
            authType = authType,
            capabilities = capabilities,
            endpointStyle = endpointStyle,
            structuredOutputMode = structuredOutputMode,
            inputMode = inputMode,
            supportsVision = supportsVision,
            supportsFileUpload = supportsFileUpload,
            supportsPdfDirect = supportsPdfDirect,
            availableModels = (availableModels + model).filter(String::isNotBlank).distinct(),
            reasoningEffort = reasoningEffort
        )
        return if (AiProviderPresets.isChatGptId(id)) {
            AiProviderPresets.normalizeChatGptProfile(profile).copy(availableModels = emptyList())
        } else if (id == AiProviderPresets.openAI.id) {
            profile.copy(displayName = AiProviderPresets.openAI.displayName)
        } else profile
    }

    private fun readCustomProviders(prefs: android.content.SharedPreferences): List<AiCustomProviderEntry> {
        val encoded = prefs.getString(KeyCustomProviders, null) ?: return emptyList()
        return runCatching {
            settingsJson.decodeFromString<List<AiCustomProviderEntry>>(encoded)
                .filter { AiProviderPresets.isCustomId(it.id) }
                .distinctBy { it.id }
        }.getOrDefault(emptyList())
    }

    private fun writeCustomProviderEntry(
        prefs: android.content.SharedPreferences,
        editor: android.content.SharedPreferences.Editor,
        profile: AiProviderProfile
    ) {
        if (!AiProviderPresets.isCustomId(profile.id)) return
        val displayName = profile.displayName.trim().ifBlank { AiProviderPresets.custom.displayName }
        val entries = readCustomProviders(prefs).toMutableList()
        val index = entries.indexOfFirst { it.id == profile.id }
        val entry = AiCustomProviderEntry(profile.id, displayName)
        if (index >= 0) entries[index] = entry else entries += entry
        editor.putString(KeyCustomProviders, settingsJson.encodeToString(entries))
    }

    private fun readModelIds(encoded: String?, fallback: List<String>, defaultModel: String): List<String> {
        val decoded = encoded?.let { value ->
            runCatching { settingsJson.decodeFromString<List<String>>(value) }.getOrNull()
        }.orEmpty()
        return (decoded.ifEmpty { fallback } + defaultModel)
            .map(String::trim)
            .filter(String::isNotBlank)
            .distinctBy(String::lowercase)
    }

    fun load(context: Context): AiImportSettings {
        val prefs = context.getSharedPreferences(PrefName, Context.MODE_PRIVATE)
        val savedProviderId = prefs.getString(KeyProviderId, AiProviderPresets.none.id).orEmpty()
        val preset = selectableProfiles(context).firstOrNull { it.id == savedProviderId }
            ?: AiProviderPresets.none
        if (AiProviderPresets.isManagedFreeId(preset.id)) return managedFreeSettings(context, prefs)
        if (AiProviderPresets.isChatGptId(preset.id)) {
            return chatGptSettings(context, readProfileWithoutSecret(prefs, preset, useGlobalKeys = true))
        }
        val providerType = runCatching {
            AiProviderType.valueOf(prefs.getString(KeyProviderType, preset.providerType.name).orEmpty())
        }.getOrDefault(preset.providerType)
        val endpointStyle = runCatching {
            AiEndpointStyle.valueOf(prefs.getString(KeyEndpointStyle, preset.endpointStyle.name).orEmpty())
        }.getOrDefault(preset.endpointStyle)
        val structuredOutputMode = runCatching {
            StructuredOutputMode.valueOf(prefs.getString(KeyStructuredOutputMode, preset.structuredOutputMode.name).orEmpty())
        }.getOrDefault(preset.structuredOutputMode)
        val inputMode = runCatching {
            AiInputMode.valueOf(prefs.getString(KeyInputMode, preset.inputMode.name).orEmpty())
        }.getOrDefault(preset.inputMode)
        val capabilities = preset.capabilities.copy(
            supportsImageInput = prefs.getBoolean(KeyImage, preset.capabilities.supportsImageInput),
            supportsPdfFileInput = prefs.getBoolean(KeyPdf, preset.capabilities.supportsPdfFileInput),
            supportsJsonSchema = prefs.getBoolean(KeyJsonSchema, preset.capabilities.supportsJsonSchema),
            supportsJsonMode = prefs.getBoolean(KeyJsonMode, preset.capabilities.supportsJsonMode),
            supportsFileUpload = prefs.getBoolean(KeyFileUpload, preset.capabilities.supportsFileUpload),
            supportsResponses = prefs.getBoolean(KeyResponses, preset.capabilities.supportsResponses)
        )
        val defaultModel = prefs.getString(KeyModel, preset.defaultModel).orEmpty()
        val profile = preset.copy(
            providerType = providerType,
            baseUrl = normalizeAiBaseUrlForProvider(preset.id, prefs.getString(KeyBaseUrl, preset.baseUrl).orEmpty()),
            defaultModel = defaultModel,
            capabilities = capabilities,
            endpointStyle = endpointStyle,
            structuredOutputMode = structuredOutputMode,
            inputMode = inputMode,
            supportsVision = prefs.getBoolean(KeyVision, preset.supportsVision || capabilities.supportsImageInput),
            supportsFileUpload = prefs.getBoolean(KeyFileUpload, preset.supportsFileUpload || capabilities.supportsFileUpload),
            supportsPdfDirect = prefs.getBoolean(KeyPdfDirect, preset.supportsPdfDirect || capabilities.supportsPdfFileInput),
            availableModels = readModelIds(
                prefs.getString(KeyAvailableModels, null),
                preset.availableModels,
                defaultModel
            ),
            reasoningEffort = runCatching {
                AiReasoningEffort.valueOf(
                    prefs.getString(KeyReasoningEffort, preset.reasoningEffort.name).orEmpty()
                )
            }.getOrDefault(preset.reasoningEffort)
        )
        val scopedEncryptedApiKey = prefs.getString(apiKeyKey(profile.id), null)
        val legacyEncryptedApiKey = prefs.getString(KeyEncryptedApiKey, null)
        val apiKey = scopedEncryptedApiKey?.let { decrypt(context, it) }
            ?: legacyEncryptedApiKey?.let { decrypt(context, it) }.orEmpty()
        if (scopedEncryptedApiKey == null && legacyEncryptedApiKey != null) {
            prefs.edit {
                putString(apiKeyKey(profile.id), legacyEncryptedApiKey)
                remove(KeyEncryptedApiKey)
            }
        }
        return AiImportSettings(profile, apiKey)
    }

    fun loadProvider(context: Context, providerId: String): AiImportSettings {
        val prefs = context.getSharedPreferences(PrefName, Context.MODE_PRIVATE)
        val current = load(context)
        val preset = presetFor(context, providerId)
        if (AiProviderPresets.isManagedFreeId(preset.id)) return managedFreeSettings(context, prefs)
        if (AiProviderPresets.isChatGptId(preset.id)) {
            return chatGptSettings(
                context,
                readProfileWithoutSecret(prefs, preset, useGlobalKeys = current.profile.id == preset.id)
            )
        }
        val profile = when {
            current.profile.id == preset.id -> current.profile
            !prefs.contains(providerKey(KeyBaseUrl, preset.id)) -> preset
            else -> {
                val providerType = runCatching {
                    AiProviderType.valueOf(
                        prefs.getString(
                            providerKey(KeyProviderType, preset.id),
                            preset.providerType.name
                        ).orEmpty()
                    )
                }.getOrDefault(preset.providerType)
                val endpointStyle = runCatching {
                    AiEndpointStyle.valueOf(
                        prefs.getString(
                            providerKey(KeyEndpointStyle, preset.id),
                            preset.endpointStyle.name
                        ).orEmpty()
                    )
                }.getOrDefault(preset.endpointStyle)
                val structuredOutputMode = runCatching {
                    StructuredOutputMode.valueOf(
                        prefs.getString(
                            providerKey(KeyStructuredOutputMode, preset.id),
                            preset.structuredOutputMode.name
                        ).orEmpty()
                    )
                }.getOrDefault(preset.structuredOutputMode)
                val inputMode = runCatching {
                    AiInputMode.valueOf(
                        prefs.getString(
                            providerKey(KeyInputMode, preset.id),
                            preset.inputMode.name
                        ).orEmpty()
                    )
                }.getOrDefault(preset.inputMode)
                val capabilities = preset.capabilities.copy(
                    supportsImageInput = prefs.getBoolean(
                        providerKey(KeyImage, preset.id),
                        preset.capabilities.supportsImageInput
                    ),
                    supportsPdfFileInput = prefs.getBoolean(
                        providerKey(KeyPdf, preset.id),
                        preset.capabilities.supportsPdfFileInput
                    ),
                    supportsJsonSchema = prefs.getBoolean(
                        providerKey(KeyJsonSchema, preset.id),
                        preset.capabilities.supportsJsonSchema
                    ),
                    supportsJsonMode = prefs.getBoolean(
                        providerKey(KeyJsonMode, preset.id),
                        preset.capabilities.supportsJsonMode
                    ),
                    supportsFileUpload = prefs.getBoolean(
                        providerKey(KeyFileUpload, preset.id),
                        preset.capabilities.supportsFileUpload
                    ),
                    supportsResponses = prefs.getBoolean(
                        providerKey(KeyResponses, preset.id),
                        preset.capabilities.supportsResponses
                    )
                )
                val defaultModel = prefs.getString(
                    providerKey(KeyModel, preset.id),
                    preset.defaultModel
                ).orEmpty()
                preset.copy(
                    providerType = providerType,
                    baseUrl = normalizeAiBaseUrlForProvider(
                        preset.id,
                        prefs.getString(providerKey(KeyBaseUrl, preset.id), preset.baseUrl).orEmpty()
                    ),
                    defaultModel = defaultModel,
                    capabilities = capabilities,
                    endpointStyle = endpointStyle,
                    structuredOutputMode = structuredOutputMode,
                    inputMode = inputMode,
                    supportsVision = prefs.getBoolean(
                        providerKey(KeyVision, preset.id),
                        preset.supportsVision || capabilities.supportsImageInput
                    ),
                    supportsFileUpload = prefs.getBoolean(
                        providerKey(KeyFileUpload, preset.id),
                        preset.supportsFileUpload || capabilities.supportsFileUpload
                    ),
                    supportsPdfDirect = prefs.getBoolean(
                        providerKey(KeyPdfDirect, preset.id),
                        preset.supportsPdfDirect || capabilities.supportsPdfFileInput
                    ),
                    availableModels = readModelIds(
                        prefs.getString(providerKey(KeyAvailableModels, preset.id), null),
                        preset.availableModels,
                        defaultModel
                    ),
                    reasoningEffort = runCatching {
                        AiReasoningEffort.valueOf(
                            prefs.getString(
                                providerKey(KeyReasoningEffort, preset.id),
                                preset.reasoningEffort.name
                            ).orEmpty()
                        )
                    }.getOrDefault(preset.reasoningEffort)
                )
            }
        }
        val apiKey = prefs.getString(apiKeyKey(profile.id), null)?.let { decrypt(context, it) }.orEmpty()
        return AiImportSettings(profile, apiKey)
    }

    private fun chatGptSettings(context: Context, profile: AiProviderProfile): AiImportSettings {
        val models = ChatGptAuthManager.cachedModels(context)
        val selectedModel = models.firstOrNull { it.slug == profile.defaultModel }
        return AiImportSettings(
            AiProviderPresets.normalizeChatGptProfile(profile).copy(
                availableModels = models.map { it.slug },
                supportsVision = selectedModel?.supportsImages == true,
                capabilities = AiProviderPresets.chatGpt.capabilities.copy(
                    supportsImageInput = selectedModel?.supportsImages == true
                )
            ),
            apiKey = ""
        )
    }

    private fun sanitizeSettings(settings: AiImportSettings): AiImportSettings =
        if (AiProviderPresets.isChatGptId(settings.profile.id)) {
            AiImportSettings(AiProviderPresets.normalizeChatGptProfile(settings.profile), apiKey = "")
        } else settings

    fun save(context: Context, value: AiImportSettings) {
        val settings = sanitizeSettings(value)
        val prefs = context.getSharedPreferences(PrefName, Context.MODE_PRIVATE)
        if (AiProviderPresets.isManagedFreeId(settings.profile.id)) {
            prefs.edit {
                putString(KeyProviderId, AiProviderPresets.dailyFree.id)
                putString(
                    providerKey(KeyReasoningEffort, AiProviderPresets.dailyFree.id),
                    settings.profile.reasoningEffort.name
                )
                putString(KeyManagedFreeOfferDecision, ManagedFreeOfferEnabled)
                remove(apiKeyKey(AiProviderPresets.dailyFree.id))
            }
            notifyChanged()
            return
        }
        prefs.edit {
            putString(KeyProviderId, settings.profile.id)
            putString(KeyBaseUrl, normalizeAiBaseUrlForProvider(settings.profile.id, settings.profile.baseUrl))
            putString(KeyModel, settings.profile.defaultModel)
            putString(KeyProviderType, settings.profile.providerType.name)
            putString(KeyEndpointStyle, settings.profile.endpointStyle.name)
            putString(KeyStructuredOutputMode, settings.profile.structuredOutputMode.name)
            putString(KeyInputMode, settings.profile.inputMode.name)
            putBoolean(KeyImage, settings.profile.capabilities.supportsImageInput)
            putBoolean(KeyPdf, settings.profile.capabilities.supportsPdfFileInput)
            putBoolean(KeyJsonSchema, settings.profile.capabilities.supportsJsonSchema)
            putBoolean(KeyJsonMode, settings.profile.capabilities.supportsJsonMode)
            putBoolean(KeyFileUpload, settings.profile.capabilities.supportsFileUpload)
            putBoolean(KeyResponses, settings.profile.capabilities.supportsResponses)
            putBoolean(KeyVision, settings.profile.supportsVision)
            putBoolean(KeyPdfDirect, settings.profile.supportsPdfDirect)
            putString(KeyAvailableModels, settingsJson.encodeToString(settings.profile.availableModels))
            putString(KeyReasoningEffort, settings.profile.reasoningEffort.name)
            writeCustomProviderEntry(prefs, this, settings.profile)
            writeProviderSettings(this, settings)
            if (settings.apiKey.isBlank()) {
                remove(apiKeyKey(settings.profile.id))
            } else {
                putString(apiKeyKey(settings.profile.id), encrypt(context, settings.apiKey))
            }
        }
        notifyChanged()
    }

    fun saveProvider(context: Context, value: AiImportSettings) {
        val settings = sanitizeSettings(value)
        val prefs = context.getSharedPreferences(PrefName, Context.MODE_PRIVATE)
        if (AiProviderPresets.isManagedFreeId(settings.profile.id)) {
            prefs.edit {
                putString(
                    providerKey(KeyReasoningEffort, AiProviderPresets.dailyFree.id),
                    settings.profile.reasoningEffort.name
                )
                remove(apiKeyKey(AiProviderPresets.dailyFree.id))
            }
            return
        }
        prefs.edit {
            writeCustomProviderEntry(prefs, this, settings.profile)
            writeProviderSettings(this, settings)
            if (settings.apiKey.isNotBlank()) {
                putString(apiKeyKey(settings.profile.id), encrypt(context, settings.apiKey))
            }
        }
    }

    private fun writeProviderSettings(
        editor: android.content.SharedPreferences.Editor,
        settings: AiImportSettings
    ) {
        val profile = sanitizeSettings(settings).profile
        val id = profile.id
        editor
            .putString(providerKey(KeyBaseUrl, id), normalizeAiBaseUrlForProvider(id, profile.baseUrl))
            .putString(providerKey(KeyModel, id), profile.defaultModel)
            .putString(providerKey(KeyProviderType, id), profile.providerType.name)
            .putString(providerKey(KeyEndpointStyle, id), profile.endpointStyle.name)
            .putString(providerKey(KeyStructuredOutputMode, id), profile.structuredOutputMode.name)
            .putString(providerKey(KeyInputMode, id), profile.inputMode.name)
            .putBoolean(providerKey(KeyImage, id), profile.capabilities.supportsImageInput)
            .putBoolean(providerKey(KeyPdf, id), profile.capabilities.supportsPdfFileInput)
            .putBoolean(providerKey(KeyJsonSchema, id), profile.capabilities.supportsJsonSchema)
            .putBoolean(providerKey(KeyJsonMode, id), profile.capabilities.supportsJsonMode)
            .putBoolean(providerKey(KeyFileUpload, id), profile.capabilities.supportsFileUpload)
            .putBoolean(providerKey(KeyResponses, id), profile.capabilities.supportsResponses)
            .putBoolean(providerKey(KeyVision, id), profile.supportsVision)
            .putBoolean(providerKey(KeyPdfDirect, id), profile.supportsPdfDirect)
            .putString(providerKey(KeyAvailableModels, id), settingsJson.encodeToString(profile.availableModels))
             .putString(providerKey(KeyReasoningEffort, id), profile.reasoningEffort.name)
    }

    private fun writeGlobalSettings(
        editor: android.content.SharedPreferences.Editor,
        value: AiProviderProfile
    ) {
        val profile = if (AiProviderPresets.isChatGptId(value.id)) {
            AiProviderPresets.normalizeChatGptProfile(value)
        } else value
        editor
            .putString(KeyBaseUrl, normalizeAiBaseUrlForProvider(profile.id, profile.baseUrl))
            .putString(KeyModel, profile.defaultModel)
            .putString(KeyProviderType, profile.providerType.name)
            .putString(KeyEndpointStyle, profile.endpointStyle.name)
            .putString(KeyStructuredOutputMode, profile.structuredOutputMode.name)
            .putString(KeyInputMode, profile.inputMode.name)
            .putBoolean(KeyImage, profile.capabilities.supportsImageInput)
            .putBoolean(KeyPdf, profile.capabilities.supportsPdfFileInput)
            .putBoolean(KeyJsonSchema, profile.capabilities.supportsJsonSchema)
            .putBoolean(KeyJsonMode, profile.capabilities.supportsJsonMode)
            .putBoolean(KeyFileUpload, profile.capabilities.supportsFileUpload)
            .putBoolean(KeyResponses, profile.capabilities.supportsResponses)
            .putBoolean(KeyVision, profile.supportsVision)
            .putBoolean(KeyPdfDirect, profile.supportsPdfDirect)
            .putString(KeyAvailableModels, settingsJson.encodeToString(profile.availableModels))
            .putString(KeyReasoningEffort, profile.reasoningEffort.name)
    }

    fun clearApiKey(context: Context, providerId: String? = null) {
        val prefs = context.getSharedPreferences(PrefName, Context.MODE_PRIVATE)
        val currentProviderId = providerId ?: prefs.getString(KeyProviderId, AiProviderPresets.none.id).orEmpty()
        if (AiProviderPresets.isManagedFreeId(currentProviderId)) return
        context.getSharedPreferences(PrefName, Context.MODE_PRIVATE).edit {
            remove(apiKeyKey(currentProviderId))
        }
        notifyChanged()
    }

    private fun encrypt(context: Context, value: String): String {
        if (value.isBlank()) return ""
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        val payload = cipher.iv + encrypted
        return Base64.encodeToString(payload, Base64.NO_WRAP)
    }

    private fun decrypt(context: Context, value: String): String {
        if (value.isBlank()) return ""
        return runCatching {
            val payload = Base64.decode(value, Base64.NO_WRAP)
            val iv = payload.copyOfRange(0, 12)
            val encrypted = payload.copyOfRange(12, payload.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
            String(cipher.doFinal(encrypted), Charsets.UTF_8)
        }.getOrDefault("")
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey("sleepdown_ai_import_key", null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                "sleepdown_ai_import_key",
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return generator.generateKey()
    }
}

internal fun customProviderDraftHasContent(
    name: String,
    baseUrl: String,
    model: String,
    apiKey: String
): Boolean = listOf(name, baseUrl, model, apiKey).any { it.isNotBlank() }

fun normalizeAiBaseUrlForProvider(providerId: String, value: String): String {
    if (AiProviderPresets.isChatGptId(providerId)) return AiProviderPresets.chatGpt.baseUrl
    if (value.isBlank()) return ""
    var url = value.trim().trimEnd('/')
    listOf("/chat/completions", "/responses", "/files").forEach { suffix ->
        if (url.endsWith(suffix, ignoreCase = true)) {
            url = url.dropLast(suffix.length).trimEnd('/')
        }
    }
    return when (providerId) {
        AiProviderPresets.openAI.id ->
            if (url.equals("https://api.openai.com", ignoreCase = true)) "$url/v1" else url
        AiProviderPresets.deepSeek.id ->
            if (url.equals("https://api.deepseek.com/v1", ignoreCase = true)) {
                "https://api.deepseek.com"
            } else {
                url
            }
        AiProviderPresets.kimi.id ->
            if (url.equals("https://api.moonshot.cn", ignoreCase = true)) "$url/v1" else url
        AiProviderPresets.dashScope.id ->
            if (url.equals("https://dashscope.aliyuncs.com", ignoreCase = true)) "$url/compatible-mode/v1" else url
        AiProviderPresets.zhipu.id ->
            if (url.equals("https://open.bigmodel.cn", ignoreCase = true)) "$url/api/paas/v4" else url
        AiProviderPresets.qianfan.id ->
            if (url.equals("https://qianfan.baidubce.com", ignoreCase = true)) "$url/v2" else url
        AiProviderPresets.doubao.id ->
            if (url.equals("https://ark.cn-beijing.volces.com", ignoreCase = true)) "$url/api/v3" else url
        AiProviderPresets.siliconFlow.id ->
            if (url.equals("https://api.siliconflow.cn", ignoreCase = true)) "$url/v1" else url
        AiProviderPresets.miniMax.id ->
            if (url.equals("https://api.minimax.chat", ignoreCase = true)) "$url/v1" else url
        AiProviderPresets.mimo.id ->
            if (url.equals("https://api.xiaomimimo.com", ignoreCase = true)) "$url/v1" else url
        AiProviderPresets.mimoTokenPlan.id ->
            if (url.equals("https://token-plan-cn.xiaomimimo.com", ignoreCase = true)) "$url/v1" else url
        else -> url
    }
}

