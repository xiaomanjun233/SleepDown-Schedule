package com.xiaomanjun.sleepdownschedule.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.shapes.Capsule
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.*
import com.xiaomanjun.sleepdownschedule.core.ui.settings.*
import com.xiaomanjun.sleepdownschedule.model.*
import java.time.LocalDate
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun TimetableDateRulesDialog(state: AppState, backdrop: Backdrop?, saving: Boolean, error: String?,
    onDismiss: () -> Unit, onSave: (List<TimetableDateRuleEntity>) -> Unit) {
    var rules by remember { mutableStateOf(state.timetableCatalog.rules.filter { it.comboId == state.config.activeTimetableComboId }.sortedBy { it.position }) }
    var selectingRule by remember { mutableStateOf<Int?>(null) }
    val schemes = state.timetableCatalog.schemes
    val config = state.config
    val foreground = sleepDownPanelForegroundColor(config)
    fun update(index: Int, rule: TimetableDateRuleEntity) { rules = rules.toMutableList().also { it[index] = rule } }
    MaterialTheme(typography = MaterialTheme.typography.copy(
        titleMedium = MiuixTheme.textStyles.main,
        bodyMedium = MiuixTheme.textStyles.body1,
        bodySmall = MiuixTheme.textStyles.footnote1,
        labelLarge = MiuixTheme.textStyles.button
    )) {
    LiquidAlertDialog(title = "日期作息", message = "", backdrop = backdrop, config = config,
        onDismissRequest = { if (!saving && selectingRule == null) onDismiss() }, scrollableMessageContent = true,
        messageMaxHeight = SleepDownDesignTokens.CenteredDialog.SelectionContentMaxHeight,
        actions = listOf(
            LiquidAlertAction("取消", LiquidAlertActionStyle.Cancel, enabled = !saving, onClick = onDismiss),
            LiquidAlertAction(if (saving) "保存中…" else "保存", LiquidAlertActionStyle.Primary, enabled = !saving) { onSave(rules) }),
        messageContent = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("在不同时间应用不同的作息",
                    style = MaterialTheme.typography.bodyMedium, color = foreground)
                if (rules.size > 1) Text("日期重叠时，按规则编号优先使用第一条；缺少的节次沿用基准。",
                    style = MaterialTheme.typography.bodySmall, color = foreground)
                rules.forEachIndexed { index, rule ->
                    Surface(shape = RoundedCornerShape(20.dp), color = foreground.copy(alpha = 0.05f)) {
                    Column(Modifier.fillMaxWidth().padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("安排 ${index + 1}", Modifier.padding(start = 14.dp, top = 8.dp),
                            style = MiuixTheme.textStyles.footnote1, color = foreground.copy(alpha = 0.65f))
                        SettingsPickerValueRow("使用作息",
                            schemes.firstOrNull { it.id == rule.schemeId }?.name ?: "请选择作息",
                            enabled = !saving, valueBelowTitle = true, onClick = { selectingRule = index })
                        SettingsDivider()
                        SettingsDatePickerRow("开始日期", rule.startDate, { update(index, rule.copy(startDate = it)) }, backdrop, config, enabled = !saving, highlightSelection = true)
                        SettingsDivider()
                        SettingsDatePickerRow("结束日期", rule.endDate, { update(index, rule.copy(endDate = it)) }, backdrop, config, enabled = !saving, highlightSelection = true)
                    }
                    }
                }
                if (rules.isEmpty()) Text("未设置日期安排，全部日期使用基准作息。", style = MaterialTheme.typography.bodyMedium, color = foreground)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                LiquidAlertActions(actions = listOf(LiquidAlertAction("添加日期安排", LiquidAlertActionStyle.Secondary,
                    enabled = schemes.isNotEmpty() && rules.size < 24 && !saving) {
                    val date = config.termStartDate ?: LocalDate.now().toString()
                    rules = rules + TimetableDateRuleEntity(0, rules.size, schemes.first().id, date, date)
                }), backdrop = backdrop, config = config)
                if (rules.isNotEmpty()) LiquidAlertActions(actions = listOf(
                    LiquidAlertAction("全部恢复基准作息", LiquidAlertActionStyle.Secondary, enabled = !saving) { rules = emptyList() }
                ), backdrop = backdrop, config = config)
            }
        })
    selectingRule?.let { index ->
        val rule = rules.getOrNull(index) ?: return@let
        LiquidAlertDialog(title = "选择作息", message = "", backdrop = backdrop, config = config,
            onDismissRequest = { selectingRule = null }, scrollableMessageContent = true,
            messageMaxHeight = SleepDownDesignTokens.CenteredDialog.SelectionContentMaxHeight,
            actions = listOf(LiquidAlertAction("完成", LiquidAlertActionStyle.Primary) { selectingRule = null }),
            messageContent = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    schemes.forEach { scheme ->
                        val selected = scheme.id == rule.schemeId
                        Surface(onClick = { update(index, rule.copy(schemeId = scheme.id)) },
                            modifier = Modifier.fillMaxWidth(), shape = Capsule(),
                            color = foreground.copy(alpha = if (selected) 0.13f else 0.04f)) {
                            Text(scheme.name, Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = foreground)
                        }
                    }
                }
            })
    }
    }
}
