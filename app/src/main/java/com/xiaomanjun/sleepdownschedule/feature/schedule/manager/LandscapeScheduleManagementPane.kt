package com.xiaomanjun.sleepdownschedule.feature.schedule.manager

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.shapes.RoundedRectangle
import com.xiaomanjun.sleepdownschedule.AppState
import com.xiaomanjun.sleepdownschedule.app.ui.ScheduleNameDialog
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.*
import com.xiaomanjun.sleepdownschedule.domain.schedule.effectiveCurrentWeek
import com.xiaomanjun.sleepdownschedule.feature.schedule.picker.ScheduleShareType
import com.xiaomanjun.sleepdownschedule.feature.schedule.picker.forSchedule
import com.xiaomanjun.sleepdownschedule.feature.home.day.appPanelForegroundColor

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LandscapeScheduleManagementPane(
    state: AppState,
    activeScheduleId: Int,
    backdrop: Backdrop?,
    onActivate: (Int) -> Unit,
    onCustomize: (Int) -> Unit,
    onCreate: (String) -> Unit,
    onRename: (Int, String) -> Unit,
    onDelete: (Int) -> Unit,
    onShare: (Int, ScheduleShareType) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedId by rememberSaveable { mutableIntStateOf(activeScheduleId) }
    val selected = state.schedules.firstOrNull { it.id == selectedId }
        ?: state.schedules.firstOrNull { it.id == activeScheduleId } ?: state.schedules.firstOrNull()
    var naming by remember { mutableStateOf(false) }
    var creating by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    val ink = appPanelForegroundColor(state.config)
    BoxWithConstraints(modifier.fillMaxSize()) {
        val listWidth = (maxWidth * 0.36f).coerceIn(152.dp, 320.dp).coerceAtMost(maxWidth * 0.46f)
        Row(Modifier.fillMaxSize().padding(top = WindowInsets.safeDrawing.asPaddingValues().calculateTopPadding() + 12.dp,
            bottom = WindowInsets.safeDrawing.asPaddingValues().calculateBottomPadding() + 12.dp)) {
            LazyColumn(Modifier.width(listWidth).fillMaxHeight(), contentPadding = PaddingValues(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    Text("课表管理", color = ink, style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(vertical = 12.dp))
                }
                items(state.schedules, key = { it.id }) { schedule ->
                    Column(Modifier.fillMaxWidth().clip(RoundedRectangle(20.dp))
                        .background(if (schedule.id == selected?.id) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                            else MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.72f))
                        .clickable { selectedId = schedule.id }.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(schedule.name, color = ink, fontWeight = FontWeight.SemiBold)
                        Text(if (schedule.id == activeScheduleId) "正在使用" else
                            "${state.allCourses.count { it.scheduleId == schedule.id }} 个上课安排",
                            color = ink.copy(alpha = 0.60f), style = MaterialTheme.typography.bodySmall)
                    }
                }
                item { TextButton(onClick = { creating = true }, modifier = Modifier.fillMaxWidth()) { Text("新建课表") } }
            }
            Box(Modifier.width(1.dp).fillMaxHeight().background(ink.copy(alpha = 0.10f)))
            Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)) {
                selected?.let { schedule ->
                    val preview = state.forSchedule(schedule.id)
                    Text(schedule.name, color = ink, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text("${preview.config.totalWeeks} 周 · ${preview.courses.size} 个上课安排", color = ink.copy(alpha = 0.60f))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = { onActivate(schedule.id) }, enabled = schedule.id != activeScheduleId) {
                            Text(if (schedule.id == activeScheduleId) "正在使用" else "使用课表")
                        }
                        TextButton(onClick = { onCustomize(schedule.id) }) { Text("课表设置") }
                        TextButton(onClick = { naming = true }) { Text("重命名") }
                    }
                    StaticWeekSnapshotGrid(preview, effectiveCurrentWeek(preview.config),
                        MaterialTheme.colorScheme.primaryContainer, ink,
                        Modifier.fillMaxWidth().clip(RoundedRectangle(22.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.65f)).padding(12.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = { onShare(schedule.id, ScheduleShareType.TOKEN) }) { Text("分享口令") }
                        TextButton(onClick = { onShare(schedule.id, ScheduleShareType.ICS) }) { Text("导出 ICS") }
                        TextButton(onClick = { deleting = true }, enabled = state.schedules.size > 1) { Text("删除课表") }
                    }
                }
            }
        }
    }
    if (creating || naming) ScheduleNameDialog(
        title = if (creating) "新建课表" else "重命名课表", requireName = true,
        initialName = if (creating) "" else selected?.name.orEmpty(), backdrop = backdrop, config = state.config,
        onConfirm = { name ->
            if (name.isNotBlank()) {
                if (creating) onCreate(name) else selected?.let { onRename(it.id, name) }
                creating = false; naming = false
            }
        }, onDismiss = { creating = false; naming = false }
    )
    if (deleting && selected != null) LiquidAlertDialog(
        title = "删除课表？", message = "确定删除“${selected.name}”及其中的课程吗？",
        actions = listOf(
            LiquidAlertAction("取消", LiquidAlertActionStyle.Secondary) { deleting = false },
            LiquidAlertAction("删除", LiquidAlertActionStyle.Destructive) {
                deleting = false
                if (state.schedules.size > 1) onDelete(selected.id)
            }), backdrop = backdrop, config = state.config, onDismissRequest = { deleting = false }
    )
}
