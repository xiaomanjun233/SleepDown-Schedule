package com.xiaomanjun.sleepdownschedule.feature.settings

import com.xiaomanjun.sleepdownschedule.app.ui.*
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.*
import com.xiaomanjun.sleepdownschedule.glass.ui.*
import com.xiaomanjun.sleepdownschedule.*
import com.xiaomanjun.sleepdownschedule.core.ui.settings.*
import com.xiaomanjun.sleepdownschedule.feature.schedule.picker.*
import com.xiaomanjun.sleepdownschedule.feature.home.day.*
import com.xiaomanjun.sleepdownschedule.feature.home.week.*
import com.xiaomanjun.sleepdownschedule.app.config.SleepDownRemoteConfig
import com.xiaomanjun.sleepdownschedule.core.remoteconfig.*
import com.xiaomanjun.sleepdownschedule.feature.importing.*
import com.xiaomanjun.sleepdownschedule.core.identity.AppDistribution
import com.xiaomanjun.sleepdownschedule.core.identity.AppIconManager
import com.xiaomanjun.sleepdownschedule.core.identity.AppIconMode
import com.xiaomanjun.sleepdownschedule.core.identity.AppIconStyle
import com.xiaomanjun.sleepdownschedule.feature.update.GiteeAppUpdater
import com.xiaomanjun.sleepdownschedule.feature.update.AppUpdateChannel
import com.xiaomanjun.sleepdownschedule.feature.agent.*
import android.content.Context
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField as MaterialOutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.kyant.backdrop.Backdrop
import com.kyant.shapes.RoundedRectangle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import androidx.compose.runtime.DisposableEffect
import kotlin.math.max
import kotlin.math.min


@Composable
fun GeneralSettingsScreen(
    state: AppState,
    backdrop: Backdrop?,
    onUpdateConfig: (ScheduleConfigEntity) -> Unit,
    onOpenLiquidGlass: () -> Unit = {},
    exitCommitRequest: Int = 0,
    onExitCommitFinished: (Boolean) -> Unit = {},
    onCommitAndExit: ((ScheduleConfigEntity, (Boolean) -> Unit) -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val topPadding = detailContentTopPadding()
    var draft by remember(state.config.id) { mutableStateOf(state.config) }
    var hasLocalEdits by remember(state.config.id) { mutableStateOf(false) }
    var dayViewMode by remember(context, state.config.id) {
        mutableStateOf(
            DayViewPreferences.mode(
                context = context,
                legacyTwoDay = state.config.defaultHomeMode == HomeStartMode.TWO_DAY
            )
        )
    }
    var weekViewStyle by remember(context, state.config.id) {
        mutableStateOf(WeekViewPreferences.style(context))
    }
    var appIconMode by remember(context) {
        mutableStateOf(AppIconManager.currentMode(context))
    }
    var appIconStyle by remember(context) {
        mutableStateOf(AppIconManager.currentStyle(context))
    }
    var includeBetaUpdates by remember(context) { mutableStateOf(GiteeAppUpdater.includesBeta(context)) }
    var updateChannel by remember(context) { mutableStateOf(GiteeAppUpdater.updateChannel(context)) }
    LaunchedEffect(state.config) {
        if (hasLocalEdits) {
            val rebased = state.config.withGeneralSettingsFrom(draft)
            draft = rebased
            if (rebased == state.config) hasLocalEdits = false
        } else {
            draft = state.config
        }
    }
    val visualConfig = settingsVisualConfig(draft)
    fun applyChange(next: ScheduleConfigEntity) {
        draft = next
        hasLocalEdits = true
        onUpdateConfig(state.config.withGeneralSettingsFrom(next))
    }
    LaunchedEffect(exitCommitRequest) {
        if (exitCommitRequest > 0) {
            // Rebase the latest local taps onto the newest database row. This keeps a stale
            // eager-save emission from rolling the UI back and preserves settings owned by
            // other pages (for example notification options).
            val latestDraft = state.config.withGeneralSettingsFrom(draft)
            if (onCommitAndExit != null) {
                onCommitAndExit(latestDraft, onExitCommitFinished)
            } else {
                if (hasLocalEdits) onUpdateConfig(latestDraft)
                onExitCommitFinished(true)
            }
        }
    }
    SleepDownSecondaryPageList(
        contentTopPadding = topPadding,
        contentBottomPadding = DockScrollPadding
    ) {
        item(key = "general-appearance") {
            GlassPreferenceSection("外观与布局") {
                SettingsGroup(backdrop = backdrop, config = visualConfig, modifier = Modifier.fillMaxWidth()) {
                    SettingsToggleRow(
                        title = "跟随系统",
                        subtitle = "开启后将跟随系统切换浅色或深色模式。",
                        checked = draft.followSystemDarkMode,
                        backdrop = backdrop,
                        onCheckedChange = { applyChange(draft.copy(followSystemDarkMode = it)) }
                    )
                    SettingsDivider()
                    SettingsToggleRow(
                        title = "深色模式",
                        subtitle = if (draft.followSystemDarkMode) "当前由系统外观决定。" else "手动切换应用外观。",
                        checked = draft.darkMode,
                        backdrop = backdrop,
                        enabled = !draft.followSystemDarkMode,
                        onCheckedChange = { applyChange(draft.copy(darkMode = it, followSystemDarkMode = false)) }
                    )
                    SettingsDivider()
                    SettingsNavigationRow(
                        title = "液态玻璃",
                        subtitle = "调整首页顶栏、表头和底栏的玻璃效果。",
                        onClick = onOpenLiquidGlass
                    )
                    SettingsDivider()
                    SettingsAppIconStyleRow(
                        selected = appIconStyle,
                        backdrop = backdrop,
                        config = visualConfig,
                        onSelected = { style ->
                            appIconStyle = style
                            AppIconManager.setStyle(context, style)
                        }
                    )
                    SettingsDivider()
                    SettingsAppIconModeRow(
                        selected = appIconMode,
                        backdrop = backdrop,
                        config = visualConfig,
                        onSelected = { mode ->
                            appIconMode = mode
                            AppIconManager.setMode(context, mode)
                        }
                    )
                }
            }
        }
        item(key = "general-layout-mode") {
            GlassPreferenceSection("首页与模式") {
                SettingsGroup(backdrop = backdrop, config = visualConfig, modifier = Modifier.fillMaxWidth()) {
                    SettingsDockAlignmentRow(
                        selected = draft.dockAlignment,
                        backdrop = backdrop,
                        config = visualConfig,
                        onSelected = { applyChange(draft.copy(dockAlignment = it)) }
                    )
                    SettingsDivider()
                    SettingsHomeStartModeRow(
                        selected = draft.defaultHomeMode,
                        backdrop = backdrop,
                        config = visualConfig,
                        onSelected = { applyChange(draft.copy(defaultHomeMode = it)) }
                    )
                    SettingsDivider()
                    SettingsDayViewModeRow(
                        selected = dayViewMode,
                        backdrop = backdrop,
                        config = visualConfig,
                        onSelected = { mode ->
                            dayViewMode = mode
                            DayViewPreferences.setMode(context, mode)
                            if (draft.defaultHomeMode == HomeStartMode.TWO_DAY) {
                                applyChange(draft.copy(defaultHomeMode = HomeStartMode.DAY))
                            }
                        }
                    )
                    SettingsDivider()
                    SettingsWeekViewStyleRow(
                        selected = weekViewStyle,
                        backdrop = backdrop,
                        config = visualConfig,
                        onSelected = { style ->
                            weekViewStyle = style
                            WeekViewPreferences.setStyle(context, style)
                        }
                    )
                    SettingsDivider()
                    SettingsDefaultWallpaperRow(
                        selected = draft.defaultWallpaperStyle,
                        backdrop = backdrop,
                        config = visualConfig,
                        onSelected = { applyChange(draft.copy(defaultWallpaperStyle = it)) }
                    )
                }
            }
        }
        item(key = "general-system-behavior") {
            GlassPreferenceSection("系统行为") {
                SettingsGroup(backdrop = backdrop, config = visualConfig, modifier = Modifier.fillMaxWidth()) {
                SettingsToggleRow(
                    title = "隐藏后台卡片",
                    subtitle = "以任意方式离开应用后，都从最近任务列表中隐藏本应用。",
                    checked = draft.hideFromRecents,
                    backdrop = backdrop,
                    onCheckedChange = { applyChange(draft.copy(hideFromRecents = it)) }
                )
                if (AppDistribution.supportsSelfUpdate) {
                    SettingsDivider()
                    SettingsToggleRow(
                        title = "自动检查更新",
                        subtitle = "每天首次打开应用时检查 Gitee 上的新版本。",
                        checked = draft.autoCheckUpdates,
                        backdrop = backdrop,
                        onCheckedChange = { applyChange(draft.copy(autoCheckUpdates = it)) }
                    )
                    SettingsDivider()
                    if (BuildConfig.SLEEPDOWN_EXP_BUILD) {
                        val updateChannels = AppUpdateChannel.entries
                        SleepDownLiquidDropdownPreference(
                            items = listOf("正式版", "Beta 版", "实验版"),
                            selectedIndex = updateChannels.indexOf(updateChannel).coerceAtLeast(0),
                            title = "更新版本",
                            summary = when (updateChannel) {
                                AppUpdateChannel.Stable -> "仅接收正式版；正式版发布后可从实验版切回。"
                                AppUpdateChannel.Beta -> "接收正式版与 Beta 版更新。"
                                AppUpdateChannel.Experimental -> "仅接收后续实验版更新。"
                            },
                            backdrop = backdrop,
                            config = visualConfig,
                            modifier = Modifier.fillMaxWidth(),
                            insideMargin = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                            maxHeight = 240.dp,
                            onExpandedChange = {},
                            onSelectedIndexChange = { index ->
                                updateChannels.getOrNull(index)?.let { selected ->
                                    updateChannel = selected
                                    GiteeAppUpdater.setUpdateChannel(context, selected)
                                }
                            }
                        )
                    } else {
                        SettingsToggleRow(
                            title = "接收 Beta 版更新",
                            subtitle = if (includeBetaUpdates) "更新渠道：正式版与 Beta 版。同版本正式版发布后也会提示。"
                                else "更新渠道：仅正式版。开启后可提前体验 Beta 版。",
                            checked = includeBetaUpdates,
                            backdrop = backdrop,
                            onCheckedChange = {
                                includeBetaUpdates = it
                                updateChannel = if (it) AppUpdateChannel.Beta else AppUpdateChannel.Stable
                                GiteeAppUpdater.setIncludesBeta(context, it)
                            }
                        )
                    }
                }
                }
            }
        }
    }
}
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
fun DayAgentSettingsScreen(state: AppState, backdrop: Backdrop?) {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(DayAgentPreferences.isEnabled(context)) }
    var weekAssistantEnabled by remember { mutableStateOf(DayAgentPreferences.isWeekAssistantEnabled(context)) }
    var weatherEnabled by remember { mutableStateOf(DayAgentPreferences.isWeatherEnabled(context)) }
    var memoryEnabled by remember { mutableStateOf(DayAgentPreferences.isMemoryEnabled(context)) }
    var memoryText by remember { mutableStateOf(DayAgentPreferences.memory(context)) }
    var memoryDraft by remember { mutableStateOf(memoryText) }
    var showMemoryEditor by remember { mutableStateOf(false) }
    var historyRetentionDays by remember { mutableIntStateOf(AiImportHistoryStore.retentionDays(context)) }
    var historyMessage by remember { mutableStateOf<String?>(null) }
    val popupBackdrop = LocalSettingsPopupBackdrop.current ?: backdrop
    val topPadding = detailContentTopPadding()

    SleepDownSecondaryPageList(
        contentTopPadding = topPadding,
        contentBottomPadding = DockScrollPadding
    ) {
        item {
            GlassPreferenceSection("AI助理") {
                SettingsGroup(backdrop = backdrop, config = state.config, modifier = Modifier.fillMaxWidth()) {
                    SettingsInfoRow(
                        "AI助理",
                        "日视图展示今日安排；周视图下拉进入对话，在课前与开始时提醒。两个入口共享消息记录。"
                    )
                    SettingsDivider()
                    SettingsToggleRow(
                        title = "启用AI助理",
                        subtitle = "显示课程、空档、天气与问答入口。",
                        checked = enabled,
                        backdrop = backdrop,
                        onCheckedChange = {
                            enabled = it
                            DayAgentPreferences.setEnabled(context, it)
                        }
                    )
                    SettingsDivider()
                    SettingsToggleRow(
                        title = "周视图AI助理",
                        subtitle = "启用首页下拉对话与课程节点提醒。",
                        checked = weekAssistantEnabled,
                        backdrop = backdrop,
                        enabled = enabled,
                        onCheckedChange = {
                            weekAssistantEnabled = it
                            DayAgentPreferences.setWeekAssistantEnabled(context, it)
                        }
                    )
                    SettingsDivider()
                    SettingsToggleRow(
                        title = "天气提醒",
                        subtitle = "使用设备粗略位置查询天气。",
                        checked = weatherEnabled,
                        backdrop = backdrop,
                        enabled = enabled,
                        onCheckedChange = {
                            weatherEnabled = it
                            DayAgentPreferences.saveOptions(
                                context,
                                DayAgentPreferences.isDailyAiEnabled(context),
                                it
                            )
                        }
                    )
                    SettingsDivider()
                    SettingsToggleRow(
                        title = "助手记忆",
                        subtitle = "跨天记住你明确表达的长期偏好与背景。",
                        checked = memoryEnabled,
                        backdrop = backdrop,
                        onCheckedChange = {
                            memoryEnabled = it
                            DayAgentPreferences.setMemoryEnabled(context, it)
                        }
                    )
                    SettingsDivider()
                    SettingsNavigationRow(
                        title = "查看与编辑记忆",
                        subtitle = memoryText
                            .lineSequence()
                            .firstOrNull { it.isNotBlank() }
                            ?.take(42)
                            ?: "当前没有已保存的记忆",
                        badgeText = if (memoryEnabled) "已启用" else "已关闭",
                        onClick = {
                            memoryDraft = DayAgentPreferences.memory(context)
                            showMemoryEditor = true
                        }
                    )
                }
            }
        }
        item {
            GlassPreferenceSection("导入历史") {
                SettingsGroup(backdrop = backdrop, config = state.config, modifier = Modifier.fillMaxWidth()) {
                    SleepDownLiquidDropdownPreference(
                        items = listOf("7 天后", "30 天后", "90 天后", "手动删除"),
                        selectedIndex = AiImportHistoryStore.retentionOptions.indexOf(historyRetentionDays).coerceAtLeast(1),
                        title = "自动清理导入历史",
                        backdrop = backdrop,
                        config = state.config,
                        modifier = Modifier.fillMaxWidth(),
                        insideMargin = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                        maxHeight = 260.dp,
                        onExpandedChange = {},
                        onSelectedIndexChange = { index ->
                            historyRetentionDays = AiImportHistoryStore.retentionOptions[
                                index.coerceIn(AiImportHistoryStore.retentionOptions.indices)
                            ]
                            AiImportHistoryStore.setRetentionDays(context, historyRetentionDays)
                            historyMessage = if (historyRetentionDays == 0) {
                                "导入历史将由你手动删除"
                            } else {
                                "导入历史将在 $historyRetentionDays 天后自动删除"
                            }
                        }
                    )
                    SettingsDivider()
                    SettingsActionRow(
                        title = "清空导入历史",
                        subtitle = "删除本机保存的最近导入上下文，不影响已经导入的课表。",
                        buttonText = "清空",
                        iconRes = R.drawable.ic_delete_history,
                        backdrop = backdrop,
                        destructive = true,
                        onClick = {
                            AiImportHistoryStore.clear(context)
                            historyMessage = "导入历史已清空"
                        }
                    )
                    historyMessage?.let {
                        SettingsDivider()
                        Text(
                            it,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }

    SleepDownPickerDialog(
        show = showMemoryEditor,
        title = "助手记忆",
        onDismissRequest = { showMemoryEditor = false },
        backdrop = popupBackdrop,
        config = state.config
    ) {
            Text(
                "这里保存的是助手可跨天使用的简短长期记忆。你可以直接修改；关闭记忆后内容会保留，但不会再注入对话或由助手更新。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            MaterialOutlinedTextField(
                value = memoryDraft,
                onValueChange = { memoryDraft = it.take(1200) },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 180.dp, max = 300.dp),
                placeholder = {
                    Text("例如：默认只修改本周；跨校区课程之间预留 30 分钟。")
                },
                minLines = 6,
                maxLines = 10,
                shape = RoundedRectangle(24.dp)
            )
            Text(
                "${memoryDraft.length}/1200",
                modifier = Modifier.align(Alignment.End),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(SleepDownDesignTokens.Dialog.ActionSpacing)
            ) {
                QuickSheetLiquidAction(
                    label = "清空",
                    enabled = true,
                    backdrop = popupBackdrop,
                    config = state.config,
                    destructive = true,
                    modifier = Modifier.weight(1f),
                    height = SleepDownDesignTokens.CenteredDialog.ActionHeight
                ) {
                    memoryDraft = ""
                }
                QuickSheetLiquidAction(
                    label = "取消",
                    enabled = true,
                    backdrop = popupBackdrop,
                    config = state.config,
                    modifier = Modifier.weight(1f),
                    height = SleepDownDesignTokens.CenteredDialog.ActionHeight
                ) {
                    showMemoryEditor = false
                }
                QuickSheetLiquidAction(
                    label = "保存",
                    enabled = true,
                    backdrop = popupBackdrop,
                    config = state.config,
                    primary = true,
                    modifier = Modifier.weight(1f),
                    height = SleepDownDesignTokens.CenteredDialog.ActionHeight
                ) {
                    DayAgentPreferences.saveMemory(context, memoryDraft)
                    memoryText = DayAgentPreferences.memory(context)
                    memoryDraft = memoryText
                    showMemoryEditor = false
                }
            }
    }
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
    val modelEditable = !isManagedFreeProvider && (modelOptions.isEmpty() || modelUsesCustomInput)
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
    val profile = if (isManagedFreeProvider) {
        selectedPreset.copy(reasoningEffort = effectiveReasoningEffort)
    } else {
        editableProfile
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
        remoteConfigState.bootstrap?.ai?.message
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
        if (!aiDisabled && (profile.baseUrl.isBlank() || profile.defaultModel.isBlank())) {
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
        if (aiDisabled || (profile.baseUrl.isNotBlank() && profile.defaultModel.isNotBlank())) {
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
    SleepDownSecondaryPageList(
        contentTopPadding = topPadding,
        contentBottomPadding = DockScrollPadding
    ) {
        item(key = "ai-provider") {
            GlassPreferenceSection("服务商") {
                SettingsGroup(backdrop = backdrop, config = state.config, modifier = Modifier.fillMaxWidth()) {
        SettingsInfoRow(
            "AI 设置",
            "配置AI助理、AI 对话、教务课表解析等智能功能共用的模型服务。API Key 按服务商分别加密保存在本机，不会写入课表数据库或诊断日志。选择“无”可停用所有联网 AI 能力，本地课表功能不受影响。"
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
                "AI助理将使用本地时间与课程模板，AI 对话和 AI 教务解析入口不会发起模型请求。已保存的其他服务商 Key 会保留，重新选择后可继续使用。"
            )
        }
                }
            }
        }
        if (!aiDisabled) {
        if (isManagedFreeProvider) {
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
					?: if (remoteConfigState.isRefreshing) "正在获取后台最新配置…" else "进入本页时会自动刷新，也可在这里立即重试。",
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
                "开启后 AI 导入与AI助理的所有请求统一使用 /responses。"
            } else {
                "当前模型没有已知的 Responses 能力，将继续使用 Chat Completions。"
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
                subtitle = "允许AI助理向该兼容接口发送图片附件。",
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
        item(key = "ai-testing") {
            GlassPreferenceSection("测试与管理") {
                SettingsGroup(backdrop = backdrop, config = state.config, modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("连接测试", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SettingsActionButton(
                    "网络诊断",
                    backdrop,
                    onClick = {
                        val nextKey = apiKeyInput.ifBlank { saved.apiKey }
                        testResult = "正在诊断网络..."
                        scope.launch {
                            diagnoseAiProviderNetwork(AiImportSettings(profile, nextKey))
                                .onSuccess { testResult = it }
                                .onFailure { testResult = it.message ?: "网络诊断失败" }
                        }
                    },
                    modifier = Modifier.weight(1f)
                )
                SettingsActionButton(
                    "测试连接",
                    backdrop,
                    onClick = {
                        val nextKey = apiKeyInput.ifBlank { saved.apiKey }
                        testResult = "正在测试连接..."
                        scope.launch {
                            testAiProviderConnection(AiImportSettings(profile, nextKey))
                                .onSuccess { testResult = it }
                                .onFailure { testResult = it.message ?: "连接测试失败" }
                        }
                    },
                    modifier = Modifier.weight(1f)
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
    if (showDeleteProviderConfirm) {
        LiquidAlertDialog(
            title = "删除自定义接口？",
            message = "将删除此接口的名称、地址、模型和本机保存的 API Key，不会影响其他接口。",
            actions = listOf(
                LiquidAlertAction("取消", LiquidAlertActionStyle.Secondary) {
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
            "每行一个模型 ID。右下角菜单会从这里读取，第一项作为新建接口的默认模型。",
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

