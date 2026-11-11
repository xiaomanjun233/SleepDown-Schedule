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
import com.xiaomanjun.sleepdownschedule.domain.schedule.PeriodTimelineSession
import com.xiaomanjun.sleepdownschedule.domain.schedule.ScheduleRoutines
import com.xiaomanjun.sleepdownschedule.domain.schedule.parseScheduleDate
import com.xiaomanjun.sleepdownschedule.feature.reminder.NotificationScheduler
import com.xiaomanjun.sleepdownschedule.model.AppState
import com.xiaomanjun.sleepdownschedule.model.ScheduleConfigEntity
import com.xiaomanjun.sleepdownschedule.model.TimetableDateRuleEntity
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private data class RoutineCreation(val source: ScheduleRoutines, val target: ScheduleRoutines) {
    val copying: Boolean get() = source.config.id != target.config.id
}

private data class RoutineEdit(
    val source: ScheduleRoutines,
    val target: ScheduleRoutines,
    val session: PeriodTimelineSession,
    val creating: Boolean = false,
) {
    val copying: Boolean get() = source.config.id != target.config.id
}

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
    var creation by remember(scheduleId) { mutableStateOf<RoutineCreation?>(null) }
    var pendingCopy by remember(scheduleId) { mutableStateOf<RoutineCreation?>(null) }
    var copyPickerMounted by remember(scheduleId) { mutableStateOf(false) }
    var copyPickerVisible by remember(scheduleId) { mutableStateOf(false) }
    var copyError by remember(scheduleId) { mutableStateOf<String?>(null) }
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
    val modalOpen = editing != null || creation != null || copyPickerMounted
    SideEffect { onExitInterceptionChange(modalOpen) }
    DisposableEffect(Unit) { onDispose { onExitInterceptionChange(false) } }

    fun dismissDialog() {
        if (!busy) copyPickerVisible = false
    }
    LaunchedEffect(exitCommitRequest) {
        if (exitCommitRequest != handledExitRequest) {
            handledExitRequest = exitCommitRequest
            if (copyPickerMounted) dismissDialog()
            else if (!modalOpen) onExitCommitFinished(true)
        }
    }
    fun publish(updated: ScheduleRoutines, message: String) {
        routines = updated
        NotificationScheduler.requestReschedule(context)
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }
    fun prepareCopy(sourceId: Int, target: ScheduleRoutines) {
        if (busy) return
        busy = true
        copyError = null
        scope.launch {
            try {
                pendingCopy = RoutineCreation(repository.loadScheduleRoutines(sourceId), target)
                copyPickerVisible = false
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                copyError = failure.message ?: "来源课表读取失败"
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
                                    enabled = !modalOpen,
                                    onClick = {
                                        editedRules = current.rules
                                        editing = RoutineEdit(current, current, current.timeline(id))
                                    },
                                )
                            }
                        }
                        SettingsDivider()
                        SettingsNavigationRow("添加作息",
                            subtitle = if (current.rules.size >= 24) "已达到 24 条日期安排" else null,
                            enabled = !modalOpen && current.rules.size < 24,
                            onClick = { creation = RoutineCreation(current, current) })
                    }
                }
            }
            item {
                GlassPreferenceSection("更多") {
                    SettingsGroup(backdrop, config) {
                        SettingsNavigationRow(
                            title = "从其他课表复制",
                            subtitle = if (sources.isEmpty()) "暂无其他课表" else null,
                            enabled = sources.isNotEmpty() && !modalOpen,
                            onClick = {
                                copyError = null
                                copyPickerMounted = true
                                copyPickerVisible = true
                            },
                        )
                    }
                }
            }
        }
    }

    creation?.let { request ->
        PeriodSchemeCreationWizard(
            source = request.source,
            copying = request.copying,
            backdrop = popupBackdrop,
            visualConfig = config,
            onDismiss = { creation = null },
            onCreated = { session, rules ->
                editedRules = rules
                editing = RoutineEdit(request.source, request.target, session, creating = !request.copying)
                creation = null
            },
        )
    }

    editing?.let { request ->
        val original = request.source
        val session = request.session
        val id = session.draft.activeSchemeId
        val dates = editedRules.filter { it.schemeId == id }
        PeriodSchemeEditor(
            config = config,
            request = session,
            onSaveSession = { edited ->
                val copiedTo = request.target.takeIf { request.copying }
                val updated = repository.saveScheduleRoutine(original, edited, editedRules, copyTarget = copiedTo)
                val message = when {
                    request.copying -> "已复制全部作息"
                    request.creating -> "已添加作息"
                    else -> "作息已保存"
                }
                publish(updated, message)
            },
            onEditorFinished = { editing = null },
            exitCommitRequest = exitCommitRequest,
            hasAdditionalChanges = request.copying || request.creating || editedRules != original.rules,
            saveMessage = when {
                request.copying -> "保存后会把这套节次、作息和全部日期安排复制到当前课表。"
                request.creating -> "要添加这套日期作息吗？节次结构的调整会应用到本课表各套作息。"
                else -> null
            },
            canChangeStructure = original.canChangeStructure,
            metadataContent = { sheetBackdrop, enabled ->
                SettingsGroup(backdrop = null, config = config) {
                    if (dates.isEmpty()) SettingsInfoRow("生效日期", routineSubtitle(original, id))
                    dates.forEachIndexed { index, rule ->
                        key(rule.position) {
                            if (index > 0) SettingsDivider()
                            SettingsDatePickerRow("开始日期", rule.startDate, { value ->
                                parseScheduleDate(value)?.toString()?.let { date ->
                                    editedRules = editedRules.map { if (it.position == rule.position) it.copy(startDate = date) else it }
                                }
                            }, sheetBackdrop, config, enabled)
                            SettingsDivider()
                            SettingsDatePickerRow("结束日期", rule.endDate, { value ->
                                parseScheduleDate(value)?.toString()?.let { date ->
                                    editedRules = editedRules.map { if (it.position == rule.position) it.copy(endDate = date) else it }
                                }
                            }, sheetBackdrop, config, enabled)
                        }
                    }
                }
            },
            onDeleteRoutine = if (request.copying || request.creating || id == original.regularId || id == original.gridId) null else {
                { publish(repository.deleteScheduleRoutine(original, id), "已删除作息") }
            },
        )
    }

    if (copyPickerMounted && current != null) {
        var sourceId by remember { mutableStateOf<Int?>(null) }
        SleepDownPickerDialog(
            show = copyPickerVisible,
            title = "从其他课表复制",
            onDismissRequest = ::dismissDialog,
            onDismissFinished = {
                copyPickerMounted = false
                creation = pendingCopy
                pendingCopy = null
            },
            backdrop = popupBackdrop,
            config = config,
            couiContentPadding = CouiPickerFormPadding,
            contentPadding = PaddingValues(horizontal = 8.dp),
            scrollableContent = true,
            actions = listOf(
                LiquidAlertAction("取消", LiquidAlertActionStyle.Cancel, enabled = !busy, onClick = ::dismissDialog),
                LiquidAlertAction(
                    if (busy) "正在读取…" else "下一步",
                    LiquidAlertActionStyle.Primary,
                    enabled = !busy && sourceId != null,
                    dismissOnClick = false,
                ) {
                    sourceId?.let { prepareCopy(it, current) }
                },
            ),
        ) {
            Text("选择来源课表后可先调整节次和作息，保存时会替换当前课表的时间配置。",
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
            copyError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
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
