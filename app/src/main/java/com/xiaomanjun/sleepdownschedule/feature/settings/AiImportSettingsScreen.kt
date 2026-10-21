package com.xiaomanjun.sleepdownschedule.feature.settings

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kyant.backdrop.Backdrop
import com.kyant.shapes.RoundedRectangle
import com.xiaomanjun.sleepdownschedule.AppState
import com.xiaomanjun.sleepdownschedule.R
import com.xiaomanjun.sleepdownschedule.ScheduleConfigEntity
import com.xiaomanjun.sleepdownschedule.app.config.SleepDownRemoteConfig
import com.xiaomanjun.sleepdownschedule.core.ui.chrome.DockScrollPadding
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.DialogLiquidButton
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.LiquidAlertAction
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.LiquidAlertActionStyle
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.LiquidAlertDialog
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.SleepDownSecondaryPageList
import com.xiaomanjun.sleepdownschedule.core.ui.scaffold.detailContentTopPadding
import com.xiaomanjun.sleepdownschedule.core.ui.scaffold.settingsPageBackground
import com.xiaomanjun.sleepdownschedule.core.ui.settings.GlassPreferenceSection
import com.xiaomanjun.sleepdownschedule.core.ui.settings.SettingsActionButton
import com.xiaomanjun.sleepdownschedule.core.ui.settings.SettingsActionRow
import com.xiaomanjun.sleepdownschedule.core.ui.settings.SettingsDivider
import com.xiaomanjun.sleepdownschedule.core.ui.settings.SettingsGroup
import com.xiaomanjun.sleepdownschedule.core.ui.settings.SettingsInfoRow
import com.xiaomanjun.sleepdownschedule.core.ui.settings.SettingsTextFieldRow
import com.xiaomanjun.sleepdownschedule.core.ui.settings.SettingsToggleRow
import com.xiaomanjun.sleepdownschedule.core.ui.settings.SleepDownLiquidDropdownPreference
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
    var providerMenuExpanded by remember { mutableStateOf(false) }
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
    val configuredModelIds = parseAiModelIdList(availableModelsText, model)
    val modelProfile = (if (isManagedFreeProvider) AiProviderPresets.dailyFree else selectedPreset).copy(
        defaultModel = model.trim(),
        capabilities = selectedPreset.capabilities.copy(supportsImageInput = supportsVision),
        supportsVision = supportsVision,
        availableModels = configuredModelIds
    )
    val modelOptions = AiProviderPresets.modelOptions(modelProfile)
    val modelEditable = !isManagedFreeProvider && !isChatGptProvider && (modelOptions.isEmpty() || modelUsesCustomInput)
    val selectedModelOptionIndex = if (modelUsesCustomInput) {
        modelOptions.size
    } else {
        modelOptions.indexOfFirst { it.model == model }.coerceAtLeast(0)
    }
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
    val usesOpenAICompatibleSite = selectedProviderId == AiProviderPresets.openAI.id &&
        !normalizedBaseUrl.equals("https://api.openai.com/v1", ignoreCase = true)
    val modelSupportsResponses = AiProviderPresets.supportsResponses(modelProfile)
    val reasoningOptions = AiProviderPresets.reasoningEfforts(modelProfile)
    val effectiveReasoningEffort = reasoningEffort.takeIf { it in reasoningOptions }
        ?: AiReasoningEffort.MEDIUM.takeIf { isChatGptProvider }
        ?: reasoningOptions.firstOrNull()
        ?: AiReasoningEffort.MEDIUM
    val endpointStyle = if (responsesEnabled && modelSupportsResponses) {
        AiEndpointStyle.RESPONSES
    } else AiEndpointStyle.CHAT_COMPLETIONS
    val selectedModelSupportsVision = modelOptions.firstOrNull {
        it.model.equals(model.trim(), ignoreCase = true)
    }?.supportsImageInput ?: supportsVision
    val editableProfile = selectedPreset.copy(
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
        providerMenuExpanded = false
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
        providerMenuExpanded = false
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
    val contentBackdrop = rememberGlassLayerBackdrop(GlassBackdropDomain.Content, "ai-settings-body") {
        drawRect(pageColor)
        drawContent()
    }
    Box(Modifier.fillMaxSize()) {
    SleepDownSecondaryPageList(
        contentTopPadding = topPadding,
        contentBottomPadding = DockScrollPadding,
        modifier = Modifier.fillMaxSize().glassBackdropProducer(contentBackdrop)
    ) {
        item(key = "ai-provider") {
            GlassPreferenceSection("服务商") {
                SettingsGroup(backdrop = backdrop, config = state.config, modifier = Modifier.fillMaxWidth()) {
        SettingsInfoRow(
            "AI 设置",
            "为 AI 助理、对话和课表识别选择服务。选择“无”可关闭联网 AI。"
        )
        AiProviderPickerRow(
            value = if (isCustomProvider) customProviderDisplayName else selectedPreset.displayName,
            expanded = providerMenuExpanded,
            presets = pickerPresets,
            selectedProviderId = selectedProviderId,
            backdrop = backdrop,
            config = state.config,
            onExpandedChange = { providerMenuExpanded = it },
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
                        supportsVision = selected.supportsImages
                        reasoningEffort = chatGptReasoningEffort(selected.slug, reasoningEffort)
                        modelUsesCustomInput = false
                        // Other AI entry points must see the selected model immediately.
                        AiImportSettingsStore.save(context, AiImportSettings(
                            AiProviderPresets.normalizeChatGptProfile(profile.copy(
                                defaultModel = selected.slug,
                                availableModels = models.map { it.slug },
                                supportsVision = selected.supportsImages
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
                SleepDownRemoteConfig.managedFreeStatusMessage(context)
            )
			SettingsActionRow(
				title = "远程配置",
				subtitle = remoteConfigState.lastError?.let { "刷新失败：$it" }
					?: if (remoteConfigState.isRefreshing) "正在获取后台最新配置…" else "进入本页时会自动刷新，也可在这里立即重试",
				buttonText = if (remoteConfigState.isRefreshing) "刷新中" else "刷新",
				iconRes = R.drawable.ic_refresh,
				backdrop = backdrop,
				onClick = { SleepDownRemoteConfig.refresh(scope, force = true) }
			)
			SettingsDivider()
            SleepDownLiquidDropdownPreference(
                items = reasoningOptions.map(AiReasoningEffort::label),
                selectedIndex = reasoningOptions.indexOf(effectiveReasoningEffort).coerceAtLeast(0),
                title = "思考强度",
                backdrop = backdrop,
                config = state.config,
                modifier = Modifier.fillMaxWidth(),
                insideMargin = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                maxHeight = 300.dp,
                onExpandedChange = {},
                onSelectedIndexChange = { index ->
                    reasoningEffort = reasoningOptions[index.coerceIn(reasoningOptions.indices)]
                }
            )
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
                insideMargin = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                maxHeight = 318.dp,
                onExpandedChange = {},
                onSelectedIndexChange = onModelOptionSelected
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
        SettingsDivider()
        if (responsesEnabled && modelSupportsResponses) {
            SleepDownLiquidDropdownPreference(
                items = reasoningOptions.map(AiReasoningEffort::label),
                selectedIndex = reasoningOptions.indexOf(effectiveReasoningEffort).coerceAtLeast(0),
                title = "思考强度",
                backdrop = backdrop,
                config = state.config,
                modifier = Modifier.fillMaxWidth(),
                insideMargin = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                maxHeight = 300.dp,
                onExpandedChange = {},
                onSelectedIndexChange = { index ->
                    reasoningEffort = reasoningOptions[index.coerceIn(reasoningOptions.indices)]
                }
            )
            SettingsDivider()
        }
        if (isCustomProvider) {
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
            SettingsDivider()
        }
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
            insideMargin = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
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
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("连接测试", modifier = Modifier.weight(1f),
                    style = top.yukonga.miuix.kmp.theme.MiuixTheme.textStyles.headline1,
                    fontWeight = FontWeight.Medium)
                var testing by remember { mutableStateOf(false) }
                DialogLiquidButton(
                    label = if (testing) "测试中" else "测试连接",
                    backdrop = backdrop,
                    iconRes = R.drawable.ic_refresh,
                    enabled = !testing,
                    onClick = {
                        val nextKey = apiKeyInput.ifBlank { saved.apiKey }
                        testing = true
                        testResult = "正在测试连接..."
                        scope.launch {
                            try {
                                testAiProviderConnection(AiImportSettings(profile, nextKey))
                                    .onSuccess { testResult = it }
                                    .onFailure { testResult = it.message ?: "连接测试失败" }
                            } finally { testing = false }
                        }
                    }
                )
            }
            testResult?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (it.contains("失败") || it.contains("请先")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp
                )
            }
        }
        if (!isManagedFreeProvider) {
            SettingsDivider()
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SettingsActionButton(
                    "清除 Key",
                    backdrop,
                    onClick = {
                        AiImportSettingsStore.clearApiKey(context, selectedProviderId)
                        saved = AiImportSettings(profile, "")
                        apiKeyInput = ""
                        message = "API Key 已清除"
                    },
                    modifier = Modifier.weight(1f),
                    destructive = true
                )
                if (isCustomProvider) {
                    SettingsActionButton(
                        "删除接口",
                        backdrop,
                        onClick = { showDeleteProviderConfirm = true },
                        modifier = Modifier.weight(1f),
                        destructive = true
                    )
                }
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
    if (isChatGptProvider && chatGptAuth.account?.isSignedIn == true && !chatGptAuth.isSigningIn) {
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(168.dp)
            .background(Brush.verticalGradient(listOf(pageColor.copy(alpha = 0f),
                pageColor.copy(alpha = 0.36f), pageColor.copy(alpha = 0.70f), pageColor.copy(alpha = 0.94f)))))
        SettingsActionButton(
            label = if (chatGptSigningOut) "正在退出…" else "退出登录",
            backdrop = contentBackdrop, destructive = true, glowing = true,
            modifier = Modifier.align(Alignment.BottomCenter).imePadding().navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, bottom = 18.dp).fillMaxWidth(),
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
            backdrop = backdrop,
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
    value: String,
    onValueChange: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("兼容站模型列表", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
        Text(
            "每行一个模型 ID；右下角菜单会从这里读取，第一项作为新建接口的默认模型",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                color = MaterialTheme.colorScheme.onSurface
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 112.dp, max = 220.dp)
                .clip(RoundedRectangle(14.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.46f))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            maxLines = 10
        )
    }
}

@Composable
private fun AiProviderPickerRow(
    value: String,
    expanded: Boolean,
    presets: List<AiProviderProfile>,
    selectedProviderId: String,
    backdrop: Backdrop?,
    config: ScheduleConfigEntity,
    onExpandedChange: (Boolean) -> Unit,
    onSelected: (String) -> Unit,
    onAddCustomProvider: () -> Unit
) {
    val selectedIndex = presets.indexOfFirst { it.id == selectedProviderId }.coerceAtLeast(0)
    SleepDownLiquidDropdownPreference(
        items = presets.map { it.displayName } + "新增自定义接口",
        selectedIndex = selectedIndex,
        title = "服务商",
        summary = value.ifBlank { "未设置" },
        backdrop = backdrop,
        config = config,
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
        maxHeight = 318.dp,
        expanded = expanded,
        onExpandedChange = onExpandedChange,
        onSelectedIndexChange = { index ->
            if (index == presets.size) {
                onAddCustomProvider()
            } else {
                presets.getOrNull(index)?.let { onSelected(it.id) }
            }
        }
    )
}
