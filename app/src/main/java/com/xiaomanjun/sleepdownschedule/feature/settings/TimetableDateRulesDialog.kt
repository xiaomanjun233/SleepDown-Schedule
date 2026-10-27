package com.xiaomanjun.sleepdownschedule.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.*
import com.xiaomanjun.sleepdownschedule.core.ui.settings.*
import com.xiaomanjun.sleepdownschedule.model.*
import java.time.LocalDate

/** Ordered date rules share the public template library; editing a table only edits its binding. */
@Composable
internal fun TimetableDateRulesDialog(state: AppState, backdrop: Backdrop?, saving: Boolean, error: String?,
    onDismiss: () -> Unit, onSave: (List<TimetableDateRuleEntity>) -> Unit) {
    var rules by remember { mutableStateOf(state.timetableCatalog.rules.filter { it.comboId==state.config.activeTimetableComboId }.sortedBy { it.position }) }
    val schemes = state.timetableCatalog.schemes
    val config = state.config
    val foreground = sleepDownPanelForegroundColor(config)
    fun update(index: Int, rule: TimetableDateRuleEntity) { rules=rules.toMutableList().also { it[index]=rule } }
    LiquidAlertDialog(title="日期作息",message="",backdrop=backdrop,config=config,
        onDismissRequest={ if(!saving) onDismiss() },scrollableMessageContent=true,
        messageMaxHeight=SleepDownDesignTokens.CenteredDialog.SelectionContentMaxHeight,
        actions=listOf(LiquidAlertAction("取消",LiquidAlertActionStyle.Cancel,enabled=!saving,onClick=onDismiss),
            LiquidAlertAction(if(saving) "保存中…" else "保存",LiquidAlertActionStyle.Primary,enabled=!saving,onClick={onSave(rules)})),
        messageContent={
            Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Text("固定网格保持基准作息。日期符合多条规则时使用最上方一条；目标作息缺少的节次沿用基准。独立作息和自定义课程不受影响。",
                    style=MaterialTheme.typography.bodySmall,color=foreground)
                rules.forEachIndexed { index,rule ->
                    Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
                        SleepDownLiquidDropdownPreference(items=schemes.map { "${it.name} · #${it.id}" },
                            selectedIndex=schemes.indexOfFirst { it.id==rule.schemeId },title="规则 ${index+1}",backdrop=backdrop,config=config,
                            insideMargin=PaddingValues(horizontal=14.dp,vertical=10.dp),maxHeight=260.dp,onExpandedChange={},
                            onSelectedIndexChange={ selected -> schemes.getOrNull(selected)?.let { update(index,rule.copy(schemeId=it.id)) } })
                        SettingsDatePickerRow("开始日期",rule.startDate,{update(index,rule.copy(startDate=it))},backdrop,config,enabled=!saving)
                        SettingsDatePickerRow("结束日期",rule.endDate,{update(index,rule.copy(endDate=it))},backdrop,config,enabled=!saving)
                        LiquidAlertActions(actions=listOf(
                            LiquidAlertAction("上移",LiquidAlertActionStyle.Secondary,enabled=index>0 && !saving) {
                                rules=rules.toMutableList().also { val previous=it[index-1];it[index-1]=it[index];it[index]=previous }
                            },
                            LiquidAlertAction("移除",LiquidAlertActionStyle.Destructive,enabled=!saving) { rules=rules.filterIndexed { i,_ -> i!=index } }),
                            backdrop=backdrop,config=config)
                    }
                }
                if(rules.isEmpty()) Text("未设置规则，全部日期使用基准作息。",style=MaterialTheme.typography.bodyMedium,color=foreground)
                error?.let { Text(it,color=MaterialTheme.colorScheme.error) }
                LiquidAlertActions(actions=listOf(LiquidAlertAction("添加规则",LiquidAlertActionStyle.Secondary,enabled=schemes.isNotEmpty() && rules.size<24 && !saving) {
                    val date=config.termStartDate ?: LocalDate.now().toString()
                    rules=rules+TimetableDateRuleEntity(0,rules.size,schemes.first().id,date,date)
                }),backdrop=backdrop,config=config)
            }
        })
}
