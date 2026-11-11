package com.xiaomanjun.sleepdownschedule.feature.settings

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.xiaomanjun.sleepdownschedule.CourseScheduleApp
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.CouiPickerFormPadding
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.LiquidAlertAction
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.LiquidAlertActionStyle
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.LiquidDialogChoiceCard
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.SleepDownPickerDialog
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.SleepDownSecondaryPageList
import com.xiaomanjun.sleepdownschedule.core.ui.scaffold.detailContentBottomPadding
import com.xiaomanjun.sleepdownschedule.core.ui.scaffold.detailContentTopPadding
import com.xiaomanjun.sleepdownschedule.core.ui.scaffold.settingsVisualConfig
import com.xiaomanjun.sleepdownschedule.core.ui.settings.GlassPreferenceSection
import com.xiaomanjun.sleepdownschedule.core.ui.settings.LocalSettingsPopupBackdrop
import com.xiaomanjun.sleepdownschedule.core.ui.settings.SettingsActionButton
import com.xiaomanjun.sleepdownschedule.core.ui.settings.SettingsDatePickerRow
import com.xiaomanjun.sleepdownschedule.core.ui.settings.SettingsDivider
import com.xiaomanjun.sleepdownschedule.core.ui.settings.SettingsGroup
import com.xiaomanjun.sleepdownschedule.core.ui.settings.SettingsInfoRow
import com.xiaomanjun.sleepdownschedule.core.ui.settings.SettingsNavigationRow
import com.xiaomanjun.sleepdownschedule.core.ui.settings.SettingsTextFieldRow
import com.xiaomanjun.sleepdownschedule.domain.schedule.PeriodTimelineSession
import com.xiaomanjun.sleepdownschedule.domain.schedule.ScheduleRoutines
import com.xiaomanjun.sleepdownschedule.feature.reminder.NotificationScheduler
import com.xiaomanjun.sleepdownschedule.model.AppState
import com.xiaomanjun.sleepdownschedule.model.ScheduleConfigEntity
import com.xiaomanjun.sleepdownschedule.model.TimetableDateRuleEntity
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private enum class RoutineDialog { Add, Copy }

private data class RoutineEdit(val original: ScheduleRoutines, val session: PeriodTimelineSession)

@Composable
fun PeriodSchemeManagementScreen(
    state: AppState,
    backdrop: Backdrop?,
    exitCommitRequest: Int = 0,
    onExitCommitFinished: (Boolean) -> Unit = {},
    onExitInterceptionChange: (Boolean) -> Unit = {},
) {
    val context = LocalContext.current
    val repository = remember(context) { (context.applicationContext as CourseScheduleApp).repository }
    val scope = rememberCoroutineScope()
    val scheduleId = state.config.id
    val config = settingsVisualConfig(state.config)
    val popupBackdrop = LocalSettingsPopupBackdrop.current ?: backdrop
    var routines by remember(scheduleId) { mutableStateOf<ScheduleRoutines?>(null) }
    var editing by remember(scheduleId) { mutableStateOf<RoutineEdit?>(null) }
    var editedRules by remember(scheduleId) { mutableStateOf(emptyList<TimetableDateRuleEntity>()) }
    var dialog by remember(scheduleId) { mutableStateOf<RoutineDialog?>(null) }
    var dialogVisible by remember(scheduleId) { mutableStateOf(false) }
    var dialogError by remember(scheduleId) { mutableStateOf<String?>(null) }
    var busy by remember(scheduleId) { mutableStateOf(false) }
    var loadError by remember(scheduleId) { mutableStateOf<String?>(null) }
    var retry by remember(scheduleId) { mutableIntStateOf(0) }
    var handledExitRequest by remember { mutableIntStateOf(exitCommitRequest) }
    val sources = state.schedules.filterNot { it.id == scheduleId }

    LaunchedEffect(scheduleId, retry) {
        loadError = null
        try {
            routines = repository.loadScheduleRoutines(scheduleId)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            loadError = failure.message ?: "作息读取失败"
        }
    }
    SideEffect { onExitInterceptionChange(editing != null || dialog != null) }
    DisposableEffect(Unit) { onDispose { onExitInterceptionChange(false) } }

    fun dismissDialog() {
        if (!busy) dialogVisible = false
    }
    LaunchedEffect(exitCommitRequest) {
        if (exitCommitRequest != handledExitRequest) {
            handledExitRequest = exitCommitRequest
            if (dialog != null) dismissDialog()
            else if (editing == null) onExitCommitFinished(true)
        }
    }
    fun showDialog(value: RoutineDialog) {
        dialogError = null
        dialog = value
        dialogVisible = true
    }
    fun publish(updated: ScheduleRoutines, message: String) {
        routines = updated
        NotificationScheduler.requestReschedule(context)
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }
    fun applyDialogChange(operation: suspend () -> Pair<ScheduleRoutines, String>) {
        if (busy) return
        busy = true
        dialogError = null
        scope.launch {
            try {
                val (updated, message) = operation()
                publish(updated, message)
                dialogVisible = false
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                dialogError = failure.message ?: "作息操作失败"
            } finally {
                busy = false
            }
        }
    }

    val current = routines
    SleepDownSecondaryPageList(
        modifier = Modifier.fillMaxSize(),
        contentTopPadding = detailContentTopPadding(),
        contentBottomPadding = detailContentBottomPadding(),
    ) {
        if (current == null) item {
            Text(loadError ?: "正在读取作息…", Modifier.padding(vertical = 16.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = if (loadError == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error)
            if (loadError != null) SettingsActionButton("重新读取", backdrop, onClick = { retry++ })
        } else {
            item {
                val activeId = current.routineOn(LocalDate.now())
                GlassPreferenceSection("作息方案") {
                    SettingsGroup(backdrop, config) {
                        current.schemes.forEachIndexed { index, scheme ->
                            val id = scheme.scheme.id
                            key(id) {
                                if (index > 0) SettingsDivider()
                                SettingsNavigationRow(
                                    title = scheme.scheme.name,
                                    subtitle = routineSubtitle(current, id),
                                    badgeText = if (id == activeId && current.schemes.size > 1) "生效中" else null,
                                    enabled = dialog == null && editing == null,
                                    onClick = {
                                        editedRules = current.rules
                                        editing = RoutineEdit(current, current.timeline(id))
                                    },
                                )
                            }
                        }
                        SettingsDivider()
                        SettingsNavigationRow("添加作息", enabled = dialog == null && editing == null,
                            onClick = { showDialog(RoutineDialog.Add) })
                    }
                }
            }
            item {
                GlassPreferenceSection("更多") {
                    SettingsGroup(backdrop, config) {
                        SettingsNavigationRow(
                            title = "从其他课表复制",
                            subtitle = if (sources.isEmpty()) "暂无其他课表" else null,
                            enabled = sources.isNotEmpty() && dialog == null && editing == null,
                            onClick = { showDialog(RoutineDialog.Copy) },
                        )
                    }
                }
            }
        }
    }

    editing?.let { (original, session) ->
        val id = session.draft.activeSchemeId
        val dates = editedRules.filter { it.schemeId == id }
        PeriodSchemeEditor(
            config = config,
            request = session,
            onSaveSession = { edited ->
                publish(repository.saveScheduleRoutine(original, edited, editedRules), "作息已保存")
            },
            onEditorFinished = { editing = null },
            exitCommitRequest = exitCommitRequest,
            hasAdditionalChanges = editedRules != original.rules,
            canChangeStructure = original.canChangeStructure,
            metadataContent = { sheetBackdrop, enabled ->
                SettingsGroup(backdrop = null, config = config) {
                    if (dates.isEmpty()) SettingsInfoRow("生效日期", routineSubtitle(original, id))
                    dates.forEachIndexed { index, rule ->
                        key(rule.position) {
                            if (index > 0) SettingsDivider()
                            SettingsDatePickerRow("开始日期", rule.startDate, { value ->
                                editedRules = editedRules.map { if (it.position == rule.position) it.copy(startDate = value) else it }
                            }, sheetBackdrop, config, enabled)
                            SettingsDivider()
                            SettingsDatePickerRow("结束日期", rule.endDate, { value ->
                                editedRules = editedRules.map { if (it.position == rule.position) it.copy(endDate = value) else it }
                            }, sheetBackdrop, config, enabled)
                        }
                    }
                }
            },
            onDeleteRoutine = if (id == original.regularId || id == original.gridId) null else {
                { publish(repository.deleteScheduleRoutine(original, id), "已删除作息") }
            },
        )
    }

    val popup = dialog
    if (popup != null && current != null) {
        val add = popup == RoutineDialog.Add
        val today = remember(popup) { LocalDate.now().toString() }
        var name by remember(popup) { mutableStateOf("") }
        var start by remember(popup) { mutableStateOf(today) }
        var end by remember(popup) { mutableStateOf(today) }
        var sourceId by remember(popup) { mutableStateOf<Int?>(null) }
        SleepDownPickerDialog(
            show = dialogVisible,
            title = if (add) "添加作息" else "从其他课表复制",
            onDismissRequest = ::dismissDialog,
            onDismissFinished = { dialog = null },
            backdrop = popupBackdrop,
            config = config,
            couiContentPadding = CouiPickerFormPadding,
            contentPadding = PaddingValues(horizontal = 8.dp),
            scrollableContent = true,
            adjustForIme = add,
            actions = listOf(
                LiquidAlertAction("取消", LiquidAlertActionStyle.Cancel, enabled = !busy, onClick = ::dismissDialog),
                LiquidAlertAction(
                    if (busy) "处理中…" else if (add) "添加" else "确定复制",
                    LiquidAlertActionStyle.Primary,
                    enabled = !busy && (add || sourceId != null),
                    dismissOnClick = false,
                ) {
                    if (add) applyDialogChange {
                        repository.addScheduleRoutine(current, name, LocalDate.parse(start), LocalDate.parse(end)) to "已添加作息"
                    } else sourceId?.let { source ->
                        applyDialogChange {
                            val preview = repository.previewScheduleRoutineCopy(source, scheduleId)
                            check(preview.target == current) { "当前课表已有变化，请重新打开作息管理后复制" }
                            val message = if (preview.changedCourses == 0) "已复制全部作息"
                                else "已复制全部作息，调整 ${preview.changedCourses} 门课程的节次"
                            repository.copyScheduleRoutines(preview) to message
                        }
                    }
                },
            ),
        ) {
            if (add) {
                SettingsGroup(backdrop = null, config = config) {
                    SettingsTextFieldRow("作息名称", name, { name = it.take(60) },
                        placeholder = "新作息", enabled = !busy)
                    SettingsDivider()
                    SettingsDatePickerRow("开始日期", start, { value ->
                        if (end == start || end < value) end = value
                        start = value
                    }, popupBackdrop, config, !busy)
                    SettingsDivider()
                    SettingsDatePickerRow("结束日期", end, { end = it }, popupBackdrop, config, !busy)
                }
            } else {
                Text("复制会替换当前课表的节次、作息和日期安排，并调整需要重新对应的课程节次。",
                    style = MaterialTheme.typography.bodyMedium)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    sources.forEach { source ->
                        key(source.id) {
                            LiquidDialogChoiceCard(
                                title = source.name,
                                selected = source.id == sourceId,
                                config = config,
                                onClick = { if (!busy) sourceId = source.id },
                                description = routineSourceSummary(state, source.id),
                            )
                        }
                    }
                }
            }
            dialogError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

private fun routineSubtitle(routines: ScheduleRoutines, id: Long): String {
    val dates = routines.rules.filter { it.schemeId == id }
    if (dates.isEmpty()) return when {
        id != routines.regularId -> "节次基准"
        routines.rules.isEmpty() -> "始终生效"
        else -> "其他日期使用"
    }
    val first = dates.first()
    val date = if (first.startDate == first.endDate) first.startDate else "${first.startDate} 至 ${first.endDate}"
    return if (dates.size == 1) date else "$date 等 ${dates.size} 段"
}

private fun routineSourceSummary(state: AppState, id: Int): String {
    val config = state.allConfigs.firstOrNull { it.id == id } ?: return ""
    val catalog = state.timetableCatalog
    val combo = catalog.combos.firstOrNull { it.id == config.activeTimetableComboId }
    val count = (listOfNotNull(config.activePeriodSchemeId, combo?.baseSchemeId) +
        catalog.rules.filter { it.comboId == combo?.id }.map { it.schemeId }).distinct().size.coerceAtLeast(1)
    return buildList {
        if (config.morningPeriodCount > 0) add("上午 ${config.morningPeriodCount}")
        if (config.noonPeriodCount > 0) add("中午 ${config.noonPeriodCount}")
        if (config.afternoonPeriodCount > 0) add("下午 ${config.afternoonPeriodCount}")
        if (config.eveningPeriodCount > 0) add("晚上 ${config.eveningPeriodCount}")
        add("$count 套作息")
    }.joinToString(" · ")
}
