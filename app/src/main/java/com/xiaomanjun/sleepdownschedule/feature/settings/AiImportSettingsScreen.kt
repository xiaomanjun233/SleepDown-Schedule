package com.xiaomanjun.sleepdownschedule.feature.settings

import com.xiaomanjun.sleepdownschedule.feature.importing.AiProviderModelCatalog
import com.xiaomanjun.sleepdownschedule.feature.importing.aiModelSelectionIndex

import com.xiaomanjun.sleepdownschedule.feature.importing.ManagedAiRemoteConfig
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kyant.backdrop.Backdrop
import com.xiaomanjun.sleepdownschedule.AppState
import com.xiaomanjun.sleepdownschedule.R
import com.xiaomanjun.sleepdownschedule.ScheduleConfigEntity
import com.xiaomanjun.sleepdownschedule.core.remoteconfig.SleepDownRemoteConfig
import com.xiaomanjun.sleepdownschedule.data.ai.FreeAiQuotaBucket
import com.xiaomanjun.sleepdownschedule.data.ai.FreeAiQuotaGate
import com.xiaomanjun.sleepdownschedule.data.ai.FreeAiQuotaKind
import com.xiaomanjun.sleepdownschedule.data.ai.FreeAiQuotaStore
import com.xiaomanjun.sleepdownschedule.core.ui.controls.conicQuotaRingColor
import com.xiaomanjun.sleepdownschedule.core.ui.scaffold.detailContentBottomPadding
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.DialogCapsuleField
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.LiquidAlertAction
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.LiquidAlertActionStyle
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.LiquidAlertDialog
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.SleepDownSecondaryPageList
import com.xiaomanjun.sleepdownschedule.core.ui.scaffold.detailContentTopPadding
import com.xiaomanjun.sleepdownschedule.core.ui.scaffold.settingsPageBackground
import com.xiaomanjun.sleepdownschedule.core.ui.settings.GlassPreferenceSection
import com.xiaomanjun.sleepdownschedule.core.ui.settings.SettingsActionButton
import com.xiaomanjun.sleepdownschedule.core.ui.settings.SettingsActionRow
import com.xiaomanjun.sleepdownschedule.core.ui.settings.SettingsConicQuotaRow
import com.xiaomanjun.sleepdownschedule.core.ui.settings.SettingsDivider
import com.xiaomanjun.sleepdownschedule.core.ui.settings.SettingsFloatingActionArea
import com.xiaomanjun.sleepdownschedule.core.ui.settings.SettingsGroup
import com.xiaomanjun.sleepdownschedule.core.ui.settings.SettingsInfoRow
import com.xiaomanjun.sleepdownschedule.core.ui.settings.SettingsTextFieldRow
import com.xiaomanjun.sleepdownschedule.core.ui.settings.SettingsToggleRow
import com.xiaomanjun.sleepdownschedule.core.ui.settings.SleepDownLiquidDropdownPreference
import com.xiaomanjun.sleepdownschedule.core.ui.settings.settingsPreferencePadding
import com.xiaomanjun.sleepdownschedule.core.ui.theme.appUsesDarkTheme
import com.xiaomanjun.sleepdownschedule.feature.importing.AiEndpointStyle
import com.xiaomanjun.sleepdownschedule.feature.importing.AiImportSettings
import com.xiaomanjun.sleepdownschedule.feature.importing.AiImportSettingsStore
import com.xiaomanjun.sleepdownschedule.feature.importing.AiModelOption
import com.xiaomanjun.sleepdownschedule.feature.importing.AiProviderPresets
import com.xiaomanjun.sleepdownschedule.feature.importing.AiProviderProfile
import com.xiaomanjun.sleepdownschedule.feature.importing.AiReasoningEffort
import com.xiaomanjun.sleepdownschedule.feature.importing.StructuredOutputMode
import com.xiaomanjun.sleepdownschedule.feature.importing.chatGptReasoningEffort
import com.xiaomanjun.sleepdownschedule.feature.importing.chatgpt.ChatGptAuthManager
import com.xiaomanjun.sleepdownschedule.feature.importing.customProviderDraftHasContent
import com.xiaomanjun.sleepdownschedule.feature.importing.normalizeAiBaseUrlForProvider
import com.xiaomanjun.sleepdownschedule.feature.importing.testAiProviderConnection
import com.xiaomanjun.sleepdownschedule.glass.GlassBackdropDomain
import com.xiaomanjun.sleepdownschedule.glass.glassBackdropProducer
import com.xiaomanjun.sleepdownschedule.glass.rememberGlassLayerBackdrop
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@Composable
fun AiImportSettingsScreen(
    state: AppState,
    backdrop: Backdrop?,
    exitCommitRequest: Int = 0,
    onExitCommitFinished: (Boolean) -> Unit = {}
) {
    AiImportSettingsSection(
        state = state,
        backdrop = backdrop,
        exitCommitRequest = exitCommitRequest,
        onExitCommitFinished = onExitCommitFinished
    )
}

@Composable
fun AiImportSettingsSection(
    state: AppState,
    backdrop: Backdrop?,
    exitCommitRequest: Int = 0,
    onExitCommitFinished: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val remoteConfigState by SleepDownRemoteConfig.state.collectAsStateWithLifecycle()
    val freeAiQuotaState by FreeAiQuotaStore.state.collectAsStateWithLifecycle()
    val effectiveFreeAiQuota = freeAiQuotaState ?: remember(context) { FreeAiQuotaStore.current(context) }
    var freeQuotaRefreshing by remember { mutableStateOf(false) }
    var freeQuotaError by remember { mutableStateOf<String?>(null) }
    val chatGptAuth by ChatGptAuthManager.state.collectAsStateWithLifecycle()
    var chatGptSigningOut by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(AiImportSettingsStore.load(context)) }
    var selectedProviderId by remember(saved.profile.id) { mutableStateOf(saved.profile.id) }
    var customProviderName by remember(saved.profile.id) { mutableStateOf(saved.profile.displayName) }
    var baseUrl by remember(saved.profile.baseUrl) { mutableStateOf(saved.profile.baseUrl) }
    var model by remember(saved.profile.defaultModel) { mutableStateOf(saved.profile.defaultModel) }
    var availableModelsText by remember(saved.profile.id) {
        mutableStateOf(
            saved.profile.availableModels
                .ifEmpty { AiProviderPresets.modelOptions(saved.profile.id).map(AiModelOption::model) }
                .joinToString("\n")
        )
    }
    var responsesEnabled by remember(saved.profile.id) {
        mutableStateOf(saved.profile.endpointStyle == AiEndpointStyle.RESPONSES)
    }
    var reasoningEffort by remember(saved.profile.id) { mutableStateOf(saved.profile.reasoningEffort) }
    var apiKeyInput by remember(saved.apiKey) { mutableStateOf("") }
    var structuredOutputMode by remember(saved.profile.structuredOutputMode) { mutableStateOf(saved.profile.structuredOutputMode) }
    var inputMode by remember(saved.profile.inputMode) { mutableStateOf(saved.profile.inputMode) }
    var supportsVision by remember(saved.profile.supportsVision) { mutableStateOf(saved.profile.supportsVision) }
    var supportsFileUpload by remember(saved.profile.supportsFileUpload) { mutableStateOf(saved.profile.supportsFileUpload) }
    var supportsPdfDirect by remember(saved.profile.supportsPdfDirect) { mutableStateOf(saved.profile.supportsPdfDirect) }
    var message by remember { mutableStateOf<String?>(null) }
    var testResult by remember { mutableStateOf<String?>(null) }
    var showDeleteProviderConfirm by remember { mutableStateOf(false) }
    var providerListRevision by remember { mutableIntStateOf(0) }
    var modelUsesCustomInput by remember(saved.profile.id) {
        mutableStateOf(AiProviderPresets.modelOptions(saved.profile.id).none { it.model == saved.profile.defaultModel })
    }
    var customModelInput by remember(saved.profile.id) {
        mutableStateOf(
            saved.profile.defaultModel.takeIf { savedModel ->
                AiProviderPresets.modelOptions(saved.profile.id).none { it.model == savedModel }
            }.orEmpty()
        )
    }
    val presets = remember(providerListRevision) { AiImportSettingsStore.selectableProfiles(context) }
    val selectedPreset = saved.profile.takeIf { it.id == selectedProviderId }
        ?: presets.firstOrNull { it.id == selectedProviderId }
        ?: AiProviderPresets.byId(selectedProviderId)
    val isCustomProvider = AiProviderPresets.isCustomId(selectedProviderId)
    val isManagedFreeProvider = AiProviderPresets.isManagedFreeId(selectedProviderId)
    val isChatGptProvider = AiProviderPresets.isChatGptId(selectedProviderId)
    val effectiveCustomProviderName = customProviderName.trim().ifBlank { selectedPreset.displayName }
    val customProviderDisplayName = effectiveCustomProviderName.ifBlank { "未命名自定义接口" }
    val pickerPresets = if (isCustomProvider) {
        val updatedPresets = presets.map { preset ->
            if (preset.id == selectedProviderId) preset.copy(displayName = customProviderDisplayName) else preset
        }
        if (updatedPresets.any { it.id == selectedProviderId }) {
            updatedPresets
        } else {
            updatedPresets + selectedPreset.copy(displayName = customProviderDisplayName)
        }
    } else {
        presets
    }
    val aiDisabled = selectedProviderId == AiProviderPresets.none.id
    var modelCatalogRevision by remember(selectedProviderId) { mutableIntStateOf(0) }
    var refreshingModels by remember(selectedProviderId) { mutableStateOf(false) }
    var modelCatalogMessage by remember(selectedProviderId) { mutableStateOf<String?>(null) }
    val officialModelCatalog = remember(selectedProviderId, baseUrl, modelCatalogRevision) {
        AiProviderModelCatalog.cached(context, selectedPreset.copy(baseUrl = baseUrl))
    }
    val configuredModelIds = parseAiModelIdList(availableModelsText, model)
    val modelProfile = selectedPreset.copy(
        defaultModel = model.trim(),
        capabilities = selectedPreset.capabilities.copy(supportsImageInput = supportsVision),
        supportsVision = supportsVision,
        availableModels = configuredModelIds,
        modelCatalog = officialModelCatalog
    )
    val modelOptions = AiProviderPresets.modelOptions(modelProfile)
    val modelEditable = !isManagedFreeProvider && !isChatGptProvider && (modelOptions.isEmpty() || modelUsesCustomInput)
    val selectedModelOptionIndex = aiModelSelectionIndex(modelOptions, model)
    val onModelOptionSelected: (Int) -> Unit = { index ->
        if (index >= modelOptions.size) {
            modelUsesCustomInput = true
            model = customModelInput
        } else {
            val selectedOption = modelOptions[index.coerceIn(modelOptions.indices)]
            val nextModel = selectedOption.model
            modelUsesCustomInput = false
            model = nextModel
            supportsVision = selectedOption.supportsImageInput
        }
    }
    val normalizedBaseUrl = normalizeAiBaseUrlForProvider(selectedProviderId, baseUrl)
    fun refreshOfficialModels() {
        if (refreshingModels) return
        val requestProfile = modelProfile.copy(baseUrl = normalizedBaseUrl)
        if (AiProviderModelCatalog.endpoint(requestProfile) == null) return
        val requestSettings = AiImportSettings(requestProfile, apiKeyInput.ifBlank { saved.apiKey })
        if (requestSettings.apiKey.isBlank()) {
            modelCatalogMessage = "填写 API Key 后可从官方刷新模型"
            return
        }
        refreshingModels = true
        scope.launch {
            try {
                val catalog = AiProviderModelCatalog.refresh(context, requestSettings)
                if (selectedProviderId == requestProfile.id &&
                    normalizeAiBaseUrlForProvider(selectedProviderId, baseUrl) == requestProfile.baseUrl) {
                    availableModelsText = catalog.joinToString("\n") { it.model }
                    modelCatalogRevision++
                    catalog.firstOrNull { it.model == model.trim() }?.let { supportsVision = it.supportsImageInput }
                    modelCatalogMessage = "已从官方获取 ${catalog.size} 个模型"
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (selectedProviderId == requestProfile.id) modelCatalogMessage = error.message ?: "模型列表刷新失败，已保留原列表"
            } finally {
                if (selectedProviderId == requestProfile.id) refreshingModels = false
            }
        }
    }
    val usesOpenAICompatibleSite = selectedProviderId == AiProviderPresets.openAI.id &&
        !normalizedBaseUrl.equals("https://api.openai.com/v1", ignoreCase = true)
    val modelSupportsResponses = AiProviderPresets.supportsResponses(modelProfile)
    val reasoningOptions = AiProviderPresets.reasoningEfforts(modelProfile)
    val effectiveReasoningEffort = reasoningEffort.takeIf { it in reasoningOptions }
        ?: AiReasoningEffort.MEDIUM.takeIf { isChatGptProvider }
        ?: reasoningOptions.firstOrNull()
        ?: selectedPreset.reasoningEffort
    val endpointStyle = if (responsesEnabled && modelSupportsResponses) {
        AiEndpointStyle.RESPONSES
    } else AiEndpointStyle.CHAT_COMPLETIONS
    val selectedModelSupportsVision = modelOptions.firstOrNull {
        it.model.equals(model.trim(), ignoreCase = true)
    }?.supportsImageInput ?: supportsVision
    val editableProfile = selectedPreset.copy(
        modelCatalog = officialModelCatalog,
        displayName = if (isCustomProvider) effectiveCustomProviderName else selectedPreset.displayName,
        baseUrl = normalizedBaseUrl,
        defaultModel = model.trim(),
        providerType = selectedPreset.providerType,
        capabilities = selectedPreset.capabilities.copy(
            supportsImageInput = selectedModelSupportsVision,
            supportsPdfFileInput = supportsPdfDirect && !usesOpenAICompatibleSite,
            supportsFileUpload = supportsFileUpload,
            supportsJsonSchema = structuredOutputMode == StructuredOutputMode.JSON_SCHEMA,
            supportsJsonMode = structuredOutputMode != StructuredOutputMode.PROMPT_ONLY,
            supportsResponses = selectedPreset.capabilities.supportsResponses
        ),
        endpointStyle = endpointStyle,
        structuredOutputMode = structuredOutputMode,
        inputMode = inputMode,
        supportsVision = selectedModelSupportsVision,
        supportsFileUpload = supportsFileUpload,
        supportsPdfDirect = supportsPdfDirect && !usesOpenAICompatibleSite,
        availableModels = configuredModelIds,
        reasoningEffort = effectiveReasoningEffort
    )
    val profile = when {
        isManagedFreeProvider -> selectedPreset.copy(reasoningEffort = effectiveReasoningEffort)
        isChatGptProvider -> AiProviderPresets.normalizeChatGptProfile(editableProfile)
        else -> editableProfile
    }
    fun hasCustomProviderDraft(apiKey: String): Boolean = customProviderDraftHasContent(
        name = customProviderName,
        baseUrl = baseUrl,
        model = model.takeUnless {
            customProviderName.isBlank() && baseUrl.isBlank() && apiKey.isBlank() &&
                configuredModelIds == AiProviderPresets.codexCompatibleModelIds
        }.orEmpty(),
        apiKey = apiKey
    )
    fun reload() {
        message = null
        saved = AiImportSettingsStore.load(context)
        selectedProviderId = saved.profile.id
        customProviderName = saved.profile.displayName
        baseUrl = saved.profile.baseUrl
        model = saved.profile.defaultModel
        availableModelsText = saved.profile.availableModels
            .ifEmpty { AiProviderPresets.modelOptions(saved.profile.id).map(AiModelOption::model) }
            .joinToString("\n")
        responsesEnabled = saved.profile.endpointStyle == AiEndpointStyle.RESPONSES
        reasoningEffort = saved.profile.reasoningEffort
        apiKeyInput = ""
        structuredOutputMode = saved.profile.structuredOutputMode
        inputMode = saved.profile.inputMode
        supportsVision = saved.profile.supportsVision
        supportsFileUpload = saved.profile.supportsFileUpload
        supportsPdfDirect = saved.profile.supportsPdfDirect
        testResult = null
        val savedModelOptions = AiProviderPresets.modelOptions(saved.profile.id)
        val savedUsesPresetModel = savedModelOptions.any { it.model == saved.profile.defaultModel }
        modelUsesCustomInput = savedModelOptions.isEmpty() || !savedUsesPresetModel
        customModelInput = if (savedUsesPresetModel) "" else saved.profile.defaultModel
    }
	LaunchedEffect(Unit) {
		SleepDownRemoteConfig.refresh(scope, force = true)
	}
    fun refreshFreeQuotaSnapshot(force: Boolean = false) {
        if (!isManagedFreeProvider || freeQuotaRefreshing) return
        if (!force && effectiveFreeAiQuota?.syncedFromServer == true) return
        freeQuotaRefreshing = true
        freeQuotaError = null
        scope.launch {
            try {
                FreeAiQuotaGate.refreshSnapshotOnDemand(context)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                freeQuotaError = error.message
            } finally {
                freeQuotaRefreshing = false
            }
        }
    }
    LaunchedEffect(isManagedFreeProvider) {
        if (isManagedFreeProvider) refreshFreeQuotaSnapshot(force = false)
    }
    LaunchedEffect(
        remoteConfigState.bootstrap?.ai?.configVersion,
        remoteConfigState.bootstrap?.ai?.enabled,
        remoteConfigState.bootstrap?.ai?.keyId,
        remoteConfigState.bootstrap?.ai?.message,
        remoteConfigState.bootstrap?.aiConfigs
    ) {
        if (isManagedFreeProvider) reload()
    }
    fun selectProvider(providerId: String) {
        message = null
        // Preserve the current provider draft before switching. In particular, the
        // custom compatible endpoint must not fall back to its empty preset whenever
        // the user temporarily selects another provider.
        val outgoingKey = apiKeyInput.ifBlank { saved.apiKey }
        if (
            !isCustomProvider ||
            hasCustomProviderDraft(outgoingKey)
        ) {
            AiImportSettingsStore.saveProvider(context, AiImportSettings(profile, outgoingKey))
        }
        providerListRevision++
        val providerSettings = AiImportSettingsStore.loadProvider(context, providerId)
        val providerModelOptions = AiProviderPresets.modelOptions(providerSettings.profile.id)
        val providerUsesPresetModel = providerModelOptions.any { it.model == providerSettings.profile.defaultModel }
        saved = providerSettings
        selectedProviderId = providerSettings.profile.id
        customProviderName = providerSettings.profile.displayName
        baseUrl = providerSettings.profile.baseUrl
        model = providerSettings.profile.defaultModel
        availableModelsText = providerSettings.profile.availableModels
            .ifEmpty { AiProviderPresets.modelOptions(providerSettings.profile.id).map(AiModelOption::model) }
            .joinToString("\n")
        responsesEnabled = providerSettings.profile.endpointStyle == AiEndpointStyle.RESPONSES
        reasoningEffort = providerSettings.profile.reasoningEffort
        apiKeyInput = ""
        structuredOutputMode = providerSettings.profile.structuredOutputMode
        inputMode = providerSettings.profile.inputMode
        supportsVision = providerSettings.profile.supportsVision
        supportsFileUpload = providerSettings.profile.supportsFileUpload
        supportsPdfDirect = providerSettings.profile.supportsPdfDirect
        testResult = null
        modelUsesCustomInput = providerModelOptions.isEmpty() || !providerUsesPresetModel
        customModelInput = if (providerUsesPresetModel) "" else providerSettings.profile.defaultModel
    }
    fun addCustomProvider() {
        val outgoingKey = apiKeyInput.ifBlank { saved.apiKey }
        if (
            !isCustomProvider ||
            hasCustomProviderDraft(outgoingKey)
        ) {
            AiImportSettingsStore.saveProvider(context, AiImportSettings(profile, outgoingKey))
        }
        val newProfile = AiImportSettingsStore.createCustomProvider()
        saved = AiImportSettings(newProfile, "")
        selectedProviderId = newProfile.id
        customProviderName = ""
        baseUrl = ""
        model = newProfile.defaultModel
        availableModelsText = newProfile.availableModels.joinToString("\n")
        responsesEnabled = false
        reasoningEffort = newProfile.reasoningEffort
        apiKeyInput = ""
        structuredOutputMode = newProfile.structuredOutputMode
        inputMode = newProfile.inputMode
        supportsVision = newProfile.supportsVision
        supportsFileUpload = newProfile.supportsFileUpload
        supportsPdfDirect = newProfile.supportsPdfDirect
        testResult = null
        modelUsesCustomInput = true
        customModelInput = newProfile.defaultModel
        providerListRevision++
        message = "填写接口名称或连接信息后才会加入列表"
    }
    fun save(showMessage: Boolean = true): Boolean {
        val nextKey = apiKeyInput.ifBlank { saved.apiKey }
        if (!aiDisabled && !isChatGptProvider && (profile.baseUrl.isBlank() || profile.defaultModel.isBlank())) {
            message = "请先填写接口地址和模型名称"
            return false
        }
        AiImportSettingsStore.save(context, AiImportSettings(profile, nextKey.takeUnless { aiDisabled }.orEmpty()))
        reload()
        if (showMessage) {
            message = if (aiDisabled) "AI 功能已关闭" else "AI 设置已保存"
        }
        return true
    }
    fun persistForExit() {
        val nextKey = apiKeyInput.ifBlank { saved.apiKey }
        if (
            isCustomProvider &&
            !hasCustomProviderDraft(nextKey)
        ) {
            return
        }
        val nextSettings = AiImportSettings(
            profile,
            nextKey.takeUnless { aiDisabled }.orEmpty()
        )
        if (aiDisabled || isChatGptProvider || (profile.baseUrl.isNotBlank() && profile.defaultModel.isNotBlank())) {
            AiImportSettingsStore.save(context, nextSettings)
        } else {
            // Preserve incomplete input as a provider-scoped draft without making
            // the invalid profile the active service.
            AiImportSettingsStore.saveProvider(context, nextSettings)
        }
    }
    val latestPersistForExit by rememberUpdatedState<(Unit) -> Unit>({ persistForExit() })
    DisposableEffect(Unit) {
        // Saving during disposal leaves Android's Activity back dispatcher free to
        // drive the predictive-back gesture instead of waiting for a save callback.
        onDispose { latestPersistForExit(Unit) }
    }
    LaunchedEffect(exitCommitRequest) {
        if (exitCommitRequest > 0) {
            persistForExit()
            onExitCommitFinished(true)
        }
    }

    val topPadding = detailContentTopPadding()
    val pageColor = settingsPageBackground(state.config)
    val showChatGptSignOut = isChatGptProvider && chatGptAuth.account?.isSignedIn == true && !chatGptAuth.isSigningIn
    val contentBackdrop = rememberGlassLayerBackdrop(GlassBackdropDomain.Content, "ai-settings-body") {
        drawRect(pageColor)
        drawContent()
    }
    Box(Modifier.fillMaxSize()) {
    SleepDownSecondaryPageList(
        contentTopPadding = topPadding,
        contentBottomPadding = detailContentBottomPadding() + (if (showChatGptSignOut) 60.dp else 0.dp),
        modifier = Modifier.fillMaxSize().glassBackdropProducer(contentBackdrop)
    ) {
        item(key = "ai-provider") {
            GlassPreferenceSection("服务商") {
                SettingsGroup(backdrop = backdrop, config = state.config, modifier = Modifier.fillMaxWidth()) {
        AiProviderPickerRow(
            presets = pickerPresets,
            selectedProviderId = selectedProviderId,
            backdrop = backdrop,
            config = state.config,
            onSelected = { selectProvider(it) },
            onAddCustomProvider = { addCustomProvider() }
        )
        if (aiDisabled) {
            SettingsDivider()
            SettingsInfoRow(
                "AI 功能已停用",
                "AI 助理将使用本地时间与课程模板，AI 对话和 AI 教务解析入口不会发起模型请求；已保存的其他服务商 Key 会保留，重新选择后可继续使用"
            )
        }
                }
            }
        }
        if (!aiDisabled) {
        if (isChatGptProvider) {
            item(key = "chatgpt-account") {
                ChatGptSettingsPanel(
                    config = state.config,
                    backdrop = backdrop,
                    selectedModel = model,
                    reasoningEffort = effectiveReasoningEffort,
                    signingOut = chatGptSigningOut,
                    onReasoningChanged = { selected ->
                        reasoningEffort = selected
                        AiImportSettingsStore.save(context, AiImportSettings(profile.copy(reasoningEffort = selected), ""))
                    },
                    onBeforeLogin = {
                        AiImportSettingsStore.save(context, AiImportSettings(profile, ""))
                    },
                    onModelsChanged = { models, selected ->
                        availableModelsText = models.joinToString("\n") { it.slug }
                        model = selected.slug
                        supportsVision = selected.supportsImages != false
                        reasoningEffort = chatGptReasoningEffort(selected.slug, reasoningEffort)
                        modelUsesCustomInput = false
                        // Other AI entry points must see the selected model immediately.
                        AiImportSettingsStore.save(context, AiImportSettings(
                            AiProviderPresets.normalizeChatGptProfile(profile.copy(
                                defaultModel = selected.slug,
                                availableModels = models.map { it.slug },
                                supportsVision = selected.supportsImages != false
                            )), ""
                        ))
                    }
                )
            }
        } else if (isManagedFreeProvider) {
            item(key = "ai-model-reasoning") {
                GlassPreferenceSection("模型与推理") {
                    SettingsGroup(backdrop = backdrop, config = state.config, modifier = Modifier.fillMaxWidth()) {
            SettingsInfoRow(
                "每日免费 AI",
                ManagedAiRemoteConfig.managedFreeStatusMessage(context)
            )
            val darkSurface = appUsesDarkTheme(state.config)
            val chatBucket = effectiveFreeAiQuota?.bucket(FreeAiQuotaKind.CHAT)
            val chatProgress = chatBucket?.remainingFraction() ?: if (freeQuotaRefreshing) 1f else 0f
            SettingsDivider()
            SettingsConicQuotaRow(
                title = "今日免费对话额度",
                centerLabel = quotaRingCenterLabel(chatBucket, freeQuotaRefreshing),
                progress = chatProgress,
                accent = conicQuotaRingColor(
                    progress = chatProgress,
                    isExhausted = chatBucket?.isExhausted == true,
                    isUnlimited = chatBucket?.isUnlimited == true,
                    darkSurface = darkSurface
                ),
                darkSurface = darkSurface,
                subtitle = quotaRingSubtitle(chatBucket, freeQuotaRefreshing),
                glowEnabled = chatBucket != null && !chatBucket.isExhausted
            )
            val importBucket = effectiveFreeAiQuota?.bucket(FreeAiQuotaKind.IMPORT)
            val importProgress = importBucket?.remainingFraction() ?: if (freeQuotaRefreshing) 1f else 0f
            SettingsDivider()
            SettingsConicQuotaRow(
                title = "今日免费导入额度",
                centerLabel = quotaRingCenterLabel(importBucket, freeQuotaRefreshing),
                progress = importProgress,
                accent = conicQuotaRingColor(
                    progress = importProgress,
                    isExhausted = importBucket?.isExhausted == true,
                    isUnlimited = importBucket?.isUnlimited == true,
                    darkSurface = darkSurface
                ),
                darkSurface = darkSurface,
                subtitle = quotaRingSubtitle(importBucket, freeQuotaRefreshing, freeQuotaError),
                glowEnabled = importBucket != null && !importBucket.isExhausted
            )
            SettingsDivider()
            SettingsActionRow(
                title = "远程配置与额度",
                subtitle = remoteConfigState.lastError?.let { "刷新失败：$it" }
                    ?: if (remoteConfigState.isRefreshing || freeQuotaRefreshing) "正在获取最新配置与额度…" else "进入本页时按需同步，也可在这里立即刷新",
                buttonText = "刷新", iconRes = R.drawable.ic_refresh, backdrop = backdrop,
                enabled = !remoteConfigState.isRefreshing && !freeQuotaRefreshing,
                onClick = {
                    SleepDownRemoteConfig.refresh(scope, force = true)
                    refreshFreeQuotaSnapshot(force = true)
                },
            )
			if (reasoningOptions.isNotEmpty()) {
                SettingsDivider()
            SleepDownLiquidDropdownPreference(
                items = reasoningOptions.map(AiReasoningEffort::label),
                selectedIndex = reasoningOptions.indexOf(effectiveReasoningEffort).coerceAtLeast(0),
                title = "思考强度",
                backdrop = backdrop,
                config = state.config,
                modifier = Modifier.fillMaxWidth(),
                insideMargin = settingsPreferencePadding(),
                maxHeight = 300.dp,
                onExpandedChange = {},
                onSelectedIndexChange = { index ->
                    reasoningEffort = reasoningOptions[index.coerceIn(reasoningOptions.indices)]
                }
            )
            }
                }
            }
        }
        } else {
        item(key = "ai-connection") {
            GlassPreferenceSection("连接与模型") {
                SettingsGroup(backdrop = backdrop, config = state.config, modifier = Modifier.fillMaxWidth()) {
        if (isCustomProvider) {
            SettingsTextFieldRow(
                title = "接口名称",
                value = customProviderName,
                onValueChange = { customProviderName = it },
                placeholder = "自定义接口"
            )
            SettingsDivider()
            AiCompatibleModelsEditor(
                config = state.config,
                value = availableModelsText,
                onValueChange = { next ->
                    availableModelsText = next
                    val ids = parseAiModelIdList(next, "")
                    if (ids.isNotEmpty() && ids.none { it.equals(model, ignoreCase = true) }) {
                        model = ids.first()
                        modelUsesCustomInput = false
                    }
                }
            )
            SettingsDivider()
        }
        SettingsTextFieldRow("接口地址", baseUrl, { baseUrl = it }, KeyboardType.Uri)
        SettingsDivider()
        SettingsTextFieldRow(
            "模型",
            model,
            {
                if (modelEditable) {
                    model = it
                    customModelInput = it
                    modelUsesCustomInput = true
                }
            },
            enabled = modelEditable
        )
        SettingsDivider()
        if (modelOptions.isNotEmpty()) {
            val modelLabels = modelOptions.map { it.label } + "自定义"
            SleepDownLiquidDropdownPreference(
                items = modelLabels,
                selectedIndex = selectedModelOptionIndex,
                title = "常用模型",
                backdrop = backdrop,
                config = state.config,
                modifier = Modifier.fillMaxWidth(),
                insideMargin = settingsPreferencePadding(),
                maxHeight = 318.dp,
                onExpandedChange = { if (it) refreshOfficialModels() },
                onSelectedIndexChange = onModelOptionSelected
            )
            SettingsDivider()
        }
        if (AiProviderModelCatalog.endpoint(modelProfile.copy(baseUrl = normalizedBaseUrl)) != null) {
            SettingsActionRow(
                title = "官方模型列表",
                subtitle = if (refreshingModels) "正在获取…" else modelCatalogMessage,
                buttonText = "刷新", iconRes = R.drawable.ic_refresh, backdrop = backdrop,
                enabled = !refreshingModels,
                onClick = ::refreshOfficialModels,
            )
            SettingsDivider()
        }
        SettingsTextFieldRow(
            title = if (saved.apiKey.isBlank()) "API Key" else "API Key（已保存）",
            value = apiKeyInput,
            onValueChange = { apiKeyInput = it },
            // Password mode can trigger an OEM secure keyboard and block paste/password-manager
            // affordances. Keep the key local and encrypted, but use the normal ASCII editor.
            keyboardType = KeyboardType.Ascii,
            moveCursorToEndOnFocus = true
        )
                }
            }
        }
        item(key = "ai-capability") {
            GlassPreferenceSection("接口能力") {
                SettingsGroup(backdrop = backdrop, config = state.config, modifier = Modifier.fillMaxWidth()) {
        SettingsToggleRow(
            title = "Responses API",
            subtitle = if (modelSupportsResponses) {
                "开启后 AI 导入与 AI 助理的所有请求统一使用 /responses"
            } else {
                "当前模型没有已知的 Responses 能力，将继续使用 Chat Completions"
            },
            checked = responsesEnabled && modelSupportsResponses,
            backdrop = backdrop,
            enabled = modelSupportsResponses,
            onCheckedChange = { responsesEnabled = it }
        )
        if (responsesEnabled && modelSupportsResponses) {
            SettingsDivider()
            SleepDownLiquidDropdownPreference(
                items = reasoningOptions.map(AiReasoningEffort::label),
                selectedIndex = reasoningOptions.indexOf(effectiveReasoningEffort).coerceAtLeast(0),
                title = "思考强度",
                backdrop = backdrop,
                config = state.config,
                modifier = Modifier.fillMaxWidth(),
                insideMargin = settingsPreferencePadding(),
                maxHeight = 300.dp,
                onExpandedChange = {},
                onSelectedIndexChange = { index ->
                    reasoningEffort = reasoningOptions[index.coerceIn(reasoningOptions.indices)]
                }
            )
        }
        if (isCustomProvider) {
            SettingsDivider()
            SettingsToggleRow(
                title = "文件上传",
                subtitle = "允许 AI 助理向该兼容接口发送图片附件",
                checked = supportsFileUpload,
                backdrop = backdrop,
                onCheckedChange = {
                    supportsFileUpload = it
                    supportsVision = it
                }
            )
        }
        SettingsDivider()
        val outputModes = listOf(
            StructuredOutputMode.JSON_SCHEMA,
            StructuredOutputMode.JSON_OBJECT,
            StructuredOutputMode.PROMPT_ONLY
        )
        SleepDownLiquidDropdownPreference(
            items = listOf("Schema", "JSON", "Prompt"),
            selectedIndex = outputModes.indexOf(structuredOutputMode).coerceAtLeast(0),
            title = "结构化输出",
            backdrop = backdrop,
            config = state.config,
            modifier = Modifier.fillMaxWidth(),
            insideMargin = settingsPreferencePadding(),
            maxHeight = 260.dp,
            onExpandedChange = {},
            onSelectedIndexChange = { index ->
                structuredOutputMode = outputModes[index.coerceIn(outputModes.indices)]
            }
        )
                }
            }
        }
        }
        if (!isChatGptProvider) {
        item(key = "ai-testing") {
            GlassPreferenceSection("测试与管理") {
                SettingsGroup(backdrop = backdrop, config = state.config, modifier = Modifier.fillMaxWidth()) {
        var testing by remember { mutableStateOf(false) }
        SettingsActionRow(
            title = "连接测试",
            subtitle = testResult,
            buttonText = "测试", iconRes = R.drawable.ic_check, backdrop = backdrop,
            enabled = !testing,
            onClick = {
                val nextKey = apiKeyInput.ifBlank { saved.apiKey }
                testing = true
                testResult = "正在测试连接…"
                scope.launch {
                    try {
                        testAiProviderConnection(AiImportSettings(profile, nextKey))
                            .onSuccess { testResult = it }
                            .onFailure { testResult = it.message ?: "连接测试失败" }
                    } finally { testing = false }
                }
            },
        )
        if (!isManagedFreeProvider) {
            SettingsDivider()
            SettingsActionRow(
                title = "清除 API Key", subtitle = null, buttonText = "清除",
                iconRes = R.drawable.ic_delete_history, backdrop = backdrop, destructive = true,
                onClick = {
                    AiImportSettingsStore.clearApiKey(context, selectedProviderId)
                    saved = AiImportSettings(profile, "")
                    apiKeyInput = ""
                    message = "API Key 已清除"
                },
            )
            if (isCustomProvider) {
                SettingsDivider()
                SettingsActionRow(
                    title = "删除接口", subtitle = null, buttonText = "删除",
                    iconRes = R.drawable.ic_delete_history, backdrop = backdrop, destructive = true,
                    onClick = { showDeleteProviderConfirm = true },
                )
            }
        }
                }
            }
        }
        }
        }
        val currentMessage = message
        if (currentMessage != null) {
            item(key = "ai-message") {
                Text(
                    currentMessage,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 14.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (currentMessage.contains("请先")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            }
        }
    }
    if (showChatGptSignOut) SettingsFloatingActionArea(pageColor, appUsesDarkTheme(state.config)) { modifier ->
        SettingsActionButton(
            label = if (chatGptSigningOut) "正在退出…" else "退出登录",
            backdrop = contentBackdrop,
            enabled = !chatGptSigningOut,
            modifier = modifier,
            destructive = true,
            glowing = true,
            onClick = {
                if (!chatGptSigningOut) {
                    chatGptSigningOut = true
                    scope.launch {
                        try { message = ChatGptAuthManager.signOut(context).message }
                        catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { message = "退出未完成，请重试。" }
                        finally { chatGptSigningOut = false }
                    }
                }
            }
        )
    }
    }
    if (showDeleteProviderConfirm) {
        LiquidAlertDialog(
            title = "删除自定义接口？",
            message = "将删除此接口的名称、地址、模型和本机保存的 API Key，不会影响其他接口。",
            actions = listOf(
                LiquidAlertAction("取消", LiquidAlertActionStyle.Cancel) {
                    showDeleteProviderConfirm = false
                },
                LiquidAlertAction("删除", LiquidAlertActionStyle.Destructive) {
                    val deletedName = effectiveCustomProviderName
                    AiImportSettingsStore.deleteCustomProvider(context, selectedProviderId)
                    providerListRevision++
                    showDeleteProviderConfirm = false
                    reload()
                    message = "已删除 $deletedName"
                }
            ),
            config = state.config,
            onDismissRequest = { showDeleteProviderConfirm = false }
        )
    }
}

private fun parseAiModelIdList(value: String, defaultModel: String): List<String> =
    (value.split(Regex("[\\r\\n,，;；]+")) + defaultModel)
        .map(String::trim)
        .filter(String::isNotBlank)
        .distinctBy(String::lowercase)

@Composable
private fun AiCompatibleModelsEditor(
    config: ScheduleConfigEntity,
    value: String,
    onValueChange: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        com.itos.coui.text.Text(text = "兼容站模型列表", kind = com.itos.coui.text.CouiTextKind.PreferenceTitle)
        com.itos.coui.text.Text(
            text = "每行一个模型 ID，第一项用于新建接口的默认模型。",
            kind = com.itos.coui.text.CouiTextKind.PreferenceSummary,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        DialogCapsuleField(
            value = value,
            onValueChange = onValueChange,
            placeholder = "模型 ID",
            config = config,
            modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp),
            minLines = 4,
        )
    }
}

@Composable
private fun AiProviderPickerRow(
    presets: List<AiProviderProfile>,
    selectedProviderId: String,
    backdrop: Backdrop?,
    config: ScheduleConfigEntity,
    onSelected: (String) -> Unit,
    onAddCustomProvider: () -> Unit
) {
    val selectedIndex = presets.indexOfFirst { it.id == selectedProviderId }.coerceAtLeast(0)
    SleepDownLiquidDropdownPreference(
        items = presets.map { it.displayName } + "新增自定义接口",
        selectedIndex = selectedIndex,
        title = "服务商",
        summary = "为 AI 助理、对话和课表识别选择服务。",
        backdrop = backdrop,
        config = config,
        modifier = Modifier.fillMaxWidth(),
        onSelectedIndexChange = { index ->
            if (index == presets.size) {
                onAddCustomProvider()
            } else {
                presets.getOrNull(index)?.let { onSelected(it.id) }
            }
        }
    )
}

private fun quotaRingCenterLabel(bucket: FreeAiQuotaBucket?, refreshing: Boolean): String = when {
    bucket == null -> if (refreshing) "…" else "--"
    bucket.isUnlimited -> "∞"
    bucket.isPaused -> "0"
    bucket.remaining != null -> bucket.remaining.toString()
    else -> bucket.limit.toString()
}

private fun quotaRingSubtitle(
    bucket: FreeAiQuotaBucket?,
    refreshing: Boolean,
    syncError: String? = null
): String = when {
    !syncError.isNullOrBlank() -> "同步失败：$syncError"
    bucket == null -> if (refreshing) "正在同步今日额度…" else "每日 00:00（北京时间）自动重置"
    bucket.isUnlimited -> "不限次数"
    bucket.isPaused -> "暂未开放（0 次/日）"
    bucket.used != null -> "已用 ${bucket.used} / ${bucket.limit} 次 · 00:00 重置"
    else -> "每日限 ${bucket.limit} 次 · 00:00 重置"
}
