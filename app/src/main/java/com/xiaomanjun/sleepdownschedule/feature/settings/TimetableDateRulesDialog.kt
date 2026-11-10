package com.xiaomanjun.sleepdownschedule.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.*
import com.xiaomanjun.sleepdownschedule.core.ui.settings.*
import com.xiaomanjun.sleepdownschedule.model.*
import java.time.LocalDate

@Composable
internal fun TimetableDateRulesDialog(
    state: AppState,
    backdrop: Backdrop?,
    saving: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSave: (List<TimetableDateRuleEntity>) -> Unit
) {
    val config = state.config
    val schemes = state.timetableCatalog.schemes
    var rules by remember {
        mutableStateOf(state.timetableCatalog.rules
            .filter { it.comboId == config.activeTimetableComboId }.sortedBy { it.position })
    }
    var visible by remember { mutableStateOf(true) }
    var selectingRule by remember { mutableStateOf<Int?>(null) }
    var schemePickerVisible by remember { mutableStateOf(false) }
    fun update(index: Int, rule: TimetableDateRuleEntity) {
        rules = rules.toMutableList().also { it[index] = rule }
    }
    fun dismiss() {
        if (!saving && selectingRule == null) visible = false
    }

    SleepDownPickerDialog(
        show = visible,
        title = "日期作息",
        onDismissRequest = ::dismiss,
        onDismissFinished = onDismiss,
        backdrop = backdrop,
        config = config,
        contentPadding = PaddingValues(horizontal = 8.dp),
        couiContentPadding = CouiPickerFormPadding,
        scrollableContent = true,
        actions = listOf(
            LiquidAlertAction("取消", LiquidAlertActionStyle.Cancel, enabled = !saving, onClick = ::dismiss),
            LiquidAlertAction(if (saving) "保存中…" else "保存", LiquidAlertActionStyle.Primary, enabled = !saving) {
                onSave(rules)
            }
        )
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val description = when {
                rules.isEmpty() -> "未设置日期安排，全部日期使用基准作息。"
                rules.size == 1 -> "在指定日期应用所选作息，其他日期继续使用基准作息。"
                else -> "日期重叠时优先使用编号靠前的安排。缺少的节次沿用基准作息。"
            }
            Text(description, style = MaterialTheme.typography.bodyMedium,
                color = androidx.compose.material3.LocalContentColor.current.copy(alpha = 0.72f))
            rules.forEachIndexed { index, rule ->
                key(rule.position) {
                    GlassPreferenceSection("安排 ${index + 1}") {
                        SettingsGroup(backdrop = null, config = config) {
                            SettingsPickerValueRow("使用作息",
                                schemes.firstOrNull { it.id == rule.schemeId }?.name ?: "请选择作息",
                                enabled = !saving, onClick = {
                                    selectingRule = index
                                    schemePickerVisible = true
                                })
                            SettingsDivider()
                            SettingsDatePickerRow("开始日期", rule.startDate,
                                { update(index, rule.copy(startDate = it)) }, backdrop, config, enabled = !saving)
                            SettingsDivider()
                            SettingsDatePickerRow("结束日期", rule.endDate,
                                { update(index, rule.copy(endDate = it)) }, backdrop, config, enabled = !saving)
                        }
                    }
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DialogLiquidButton(backdrop, "添加日期安排", role = DialogButtonRole.Neutral,
                    modifier = Modifier.weight(1f), enabled = schemes.isNotEmpty() && rules.size < 24 && !saving,
                    onClick = {
                        val date = config.termStartDate ?: LocalDate.now().toString()
                        rules = rules + TimetableDateRuleEntity(0, rules.size, schemes.first().id, date, date)
                    })
                if (rules.isNotEmpty()) {
                    DialogLiquidButton(backdrop, "恢复基准作息", role = DialogButtonRole.Cancel,
                        modifier = Modifier.weight(1f), enabled = !saving, onClick = { rules = emptyList() })
                }
            }
        }
    }

    selectingRule?.let { index ->
        val rule = rules.getOrNull(index) ?: return@let
        SleepDownPickerDialog(
            show = schemePickerVisible,
            title = "选择作息",
            onDismissRequest = { schemePickerVisible = false },
            onDismissFinished = { selectingRule = null },
            backdrop = backdrop,
            config = config,
            couiContentPadding = CouiPickerFormPadding,
            scrollableContent = true,
            actions = listOf(LiquidAlertAction("完成", LiquidAlertActionStyle.Primary) { schemePickerVisible = false })
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                schemes.forEach { scheme ->
                    LiquidDialogChoiceCard(scheme.name, selected = scheme.id == rule.schemeId, config = config,
                        onClick = { update(index, rule.copy(schemeId = scheme.id)) })
                }
            }
        }
    }
}
