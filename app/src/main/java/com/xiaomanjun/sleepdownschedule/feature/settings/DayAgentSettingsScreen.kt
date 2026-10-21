package com.xiaomanjun.sleepdownschedule.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField as MaterialOutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.kyant.backdrop.Backdrop
import com.kyant.shapes.RoundedRectangle
import com.xiaomanjun.sleepdownschedule.AppState
import com.xiaomanjun.sleepdownschedule.R
import com.xiaomanjun.sleepdownschedule.core.ui.chrome.DockScrollPadding
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.QuickSheetLiquidAction
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.SleepDownDesignTokens
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.SleepDownPickerDialog
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.SleepDownSecondaryPageList
import com.xiaomanjun.sleepdownschedule.core.ui.scaffold.detailContentTopPadding
import com.xiaomanjun.sleepdownschedule.core.ui.settings.GlassPreferenceSection
import com.xiaomanjun.sleepdownschedule.core.ui.settings.LocalSettingsPopupBackdrop
import com.xiaomanjun.sleepdownschedule.core.ui.settings.SettingsActionRow
import com.xiaomanjun.sleepdownschedule.core.ui.settings.SettingsDivider
import com.xiaomanjun.sleepdownschedule.core.ui.settings.SettingsGroup
import com.xiaomanjun.sleepdownschedule.core.ui.settings.SettingsInfoRow
import com.xiaomanjun.sleepdownschedule.core.ui.settings.SettingsNavigationRow
import com.xiaomanjun.sleepdownschedule.core.ui.settings.SettingsToggleRow
import com.xiaomanjun.sleepdownschedule.core.ui.settings.SleepDownLiquidDropdownPreference
import com.xiaomanjun.sleepdownschedule.feature.agent.DayAgentPreferences
import com.xiaomanjun.sleepdownschedule.feature.importing.AiImportHistoryStore
import kotlin.math.max
import kotlin.math.min

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
            GlassPreferenceSection("AI 助理") {
                SettingsGroup(backdrop = backdrop, config = state.config, modifier = Modifier.fillMaxWidth()) {
                    SettingsInfoRow(
                        "AI 助理",
                        "日视图展示今日安排；周视图下拉进入对话，在课前与开始时提醒；两个入口共享消息记录"
                    )
                    SettingsDivider()
                    SettingsToggleRow(
                        title = "启用 AI 助理",
                        subtitle = "显示课程、空档、天气与问答入口",
                        checked = enabled,
                        backdrop = backdrop,
                        onCheckedChange = {
                            enabled = it
                            DayAgentPreferences.setEnabled(context, it)
                        }
                    )
                    SettingsDivider()
                    SettingsToggleRow(
                        title = "周视图 AI 助理",
                        subtitle = "启用首页下拉对话与课程节点提醒",
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
                        subtitle = "使用设备粗略位置查询天气",
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
                        subtitle = "跨天记住你明确表达的长期偏好与背景",
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
                        subtitle = "删除本机保存的最近导入上下文，不影响已经导入的课表",
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
                "这里保存的是助手可跨天使用的简短长期记忆；你可以直接修改；关闭记忆后内容会保留，但不会再注入对话或由助手更新",
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
                    Text("例如：默认只修改本周；跨校区课程之间预留 30 分钟")
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
