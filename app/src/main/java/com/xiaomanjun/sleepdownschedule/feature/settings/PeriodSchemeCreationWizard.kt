package com.xiaomanjun.sleepdownschedule.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.*
import com.xiaomanjun.sleepdownschedule.domain.schedule.*
import com.xiaomanjun.sleepdownschedule.model.*

private enum class CreationPickerPage(val title: String) {
    FORM("新建作息"), TIMING("课程与课间时长"), COUNTS("节数分配"), STARTS("时段起点"), DURATION("每节课时长"), BREAK("课间时长")
}

@Composable
internal fun PeriodSchemeCreationWizard(
    config: ScheduleConfigEntity,
    draft: SchedulePeriodSchemesDraft,
    backdrop: Backdrop?,
    visualConfig: ScheduleConfigEntity,
    onDismiss: () -> Unit,
    onCreated: (PeriodTimelineSession) -> Unit
) {
    val active = draft.schemes.first { it.scheme.id == draft.activeSchemeId }
    var name by remember { mutableStateOf("新作息") }
    var enabledParts by remember { mutableStateOf(PeriodDayPart.entries.filter { config.periodCount(it) > 0 }.toSet()) }
    var counts by remember { mutableStateOf(PeriodDayPart.entries.associateWith { config.periodCount(it) }) }
    var starts by remember {
        mutableStateOf(PeriodDayPart.entries.associateWith {
            parseMinuteOfDay(when (it) {
                PeriodDayPart.MORNING -> active.scheme.morningStartTime
                PeriodDayPart.NOON -> active.scheme.noonStartTime
                PeriodDayPart.AFTERNOON -> active.scheme.afternoonStartTime
                PeriodDayPart.EVENING -> active.scheme.eveningStartTime
            }) ?: 0
        })
    }
    var duration by remember { mutableIntStateOf(active.scheme.classDurationMinutes.coerceIn(1, 300)) }
    var gap by remember { mutableIntStateOf(active.scheme.breakDurationMinutes.coerceIn(1, duration)) }
    var page by remember { mutableStateOf(CreationPickerPage.FORM) }
    var pendingCounts by remember { mutableStateOf(counts) }
    var pendingStarts by remember { mutableStateOf(starts) }
    var pendingMinutes by remember { mutableIntStateOf(duration) }
    var error by remember { mutableStateOf<String?>(null) }
    var visible by remember { mutableStateOf(true) }
    var created by remember { mutableStateOf<PeriodTimelineSession?>(null) }
    val parts = PeriodDayPart.entries.filter { it in enabledParts }
    val targetConfig = PeriodDayPart.entries.fold(config) { next, part ->
        next.withTimelineCount(part, if (part in enabledParts) counts.getValue(part) else 0)
    }
    val total = targetConfig.totalPeriodCount()
    val template = active.copy(
        scheme = active.scheme.copy(classDurationMinutes = duration, breakDurationMinutes = gap),
        overriddenPeriods = emptySet(), specialBreaks = emptyMap()
    )
    fun changeParts(value: Set<PeriodDayPart>) {
        val ordered = PeriodDayPart.entries.filter { it in value }
        counts = allocateQuickPickerCounts(ordered, counts, total.coerceAtLeast(ordered.size))
        enabledParts = value
        error = null
    }
    fun back() {
        page = when (page) {
            CreationPickerPage.FORM -> { visible = false; CreationPickerPage.FORM }
            CreationPickerPage.DURATION, CreationPickerPage.BREAK -> CreationPickerPage.TIMING
            else -> CreationPickerPage.FORM
        }
    }
    fun create() {
        if (name.isBlank()) { error = "请填写作息名称"; return }
        if (duration < gap) { error = "课时不能短于课间"; return }
        val id = (draft.schemes.minOfOrNull { it.scheme.id } ?: 0L).coerceAtMost(0L) - 1
        val times = mutableListOf<PeriodSchemeTimeEntity>()
        parts.forEach { part ->
            var cursor = starts.getValue(part)
            repeat(counts.getValue(part)) {
                val end = cursor + duration
                if (end > LastMinuteOfDay) { error = "${part.timelineLabel()}结束时间超出当天，请调整起点或时长"; return }
                times += PeriodSchemeTimeEntity(id, times.size + 1, timelineMinuteText(cursor), timelineMinuteText(end))
                cursor = end + gap
            }
        }
        val conflict = validateResolvedPeriodTimes(times)
        if (conflict != null) { error = conflict; return }
        val resized = resizeTimelineStructure(PeriodTimelineSession(config, draft), targetConfig)
        if (resized == null) { error = "现有作息没有足够的时间容纳新增节次，请减少总节数或先调整现有作息"; return }
        val scheme = PeriodSchemeEntity(
            id = id, scheduleId = config.id, name = name.trim(), mode = PeriodSchemeMode.MANUAL,
            classDurationMinutes = duration, breakDurationMinutes = gap,
            morningStartTime = timelineMinuteText(starts.getValue(PeriodDayPart.MORNING)),
            noonStartTime = timelineMinuteText(starts.getValue(PeriodDayPart.NOON)),
            afternoonStartTime = timelineMinuteText(starts.getValue(PeriodDayPart.AFTERNOON)),
            eveningStartTime = timelineMinuteText(starts.getValue(PeriodDayPart.EVENING))
        )
        created = resized.copy(draft = resized.draft.copy(
            schemes = resized.draft.schemes + PeriodSchemeDraft(scheme, times), activeSchemeId = id
        ))
        visible = false
    }
    SleepDownPickerDialog(
        visible, page.title,
        onDismissRequest = ::back,
        backdrop = backdrop, config = visualConfig,
        onDismissFinished = { created?.let(onCreated) ?: onDismiss() },
        contentPadding = PaddingValues(SleepDownDesignTokens.QuickSheet.PickerContentPadding),
        contentTransitionKey = page,
        contentForState = { displayed ->
            val shownPage = displayed as CreationPickerPage
            Column(verticalArrangement = Arrangement.spacedBy(SleepDownDesignTokens.QuickSheet.PickerContentSpacing)) {
                Column {
                    when (shownPage) {
                        CreationPickerPage.FORM -> {
                            Text("第 1 步 · 节数与分段", fontSize = 12.sp,
                                color = androidx.compose.material3.LocalContentColor.current.copy(alpha = 0.66f))
                            SettingsTextFieldRow("作息名称", name, { name = it; error = null })
                            val split = PeriodDayPart.AFTERNOON in enabledParts
                            SettingsToggleRow("上午 / 下午分段", "", split, backdrop) {
                                changeParts(if (it) enabledParts + setOf(PeriodDayPart.MORNING, PeriodDayPart.AFTERNOON)
                                    else setOf(PeriodDayPart.MORNING))
                            }
                            if (split) {
                                SettingsToggleRow("启用中午分段", "", PeriodDayPart.NOON in enabledParts, backdrop) {
                                    changeParts(if (it) enabledParts + PeriodDayPart.NOON else enabledParts - PeriodDayPart.NOON)
                                }
                                SettingsToggleRow("启用晚上分段", "", PeriodDayPart.EVENING in enabledParts, backdrop) {
                                    changeParts(if (it) enabledParts + PeriodDayPart.EVENING else enabledParts - PeriodDayPart.EVENING)
                                }
                            }
                            SettingsPickerValueRow("节数分配", "共 $total 节", onClick = {
                                pendingCounts = counts; page = CreationPickerPage.COUNTS
                            })
                            SettingsPickerValueRow("时段起点", parts.joinToString(" · ") { timelineMinuteText(starts.getValue(it)) }, onClick = {
                                pendingStarts = starts; page = CreationPickerPage.STARTS
                            })
                            if (total != config.totalPeriodCount()) Text(
                                "总节数改变会同步调整其他作息的节次结构，保存时确认课程对应关系。",
                                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(12.dp)
                            )
                        }
                        CreationPickerPage.TIMING -> {
                            Text("第 2 步 · 确认课程与课间时长", fontSize = 12.sp,
                                color = androidx.compose.material3.LocalContentColor.current.copy(alpha = 0.66f))
                            SettingsPickerValueRow("每节课时长", "$duration 分钟", onClick = {
                                pendingMinutes = duration; page = CreationPickerPage.DURATION
                            })
                            SettingsPickerValueRow("课间时长", "$gap 分钟", onClick = {
                                pendingMinutes = gap; page = CreationPickerPage.BREAK
                            })
                        }
                        CreationPickerPage.COUNTS -> PeriodQuickCountContent(parts, pendingCounts) { pendingCounts = it }
                        CreationPickerPage.STARTS -> PeriodQuickStartsContent(targetConfig, template, pendingStarts) {
                            pendingStarts = pendingStarts + it
                        }
                        CreationPickerPage.DURATION -> SettingsMinutePickerContent(
                            pendingMinutes, { pendingMinutes = it }, gap.coerceAtLeast(1)..300, Modifier.fillMaxWidth()
                        )
                        CreationPickerPage.BREAK -> SettingsMinutePickerContent(
                            pendingMinutes, { pendingMinutes = it }, 1..duration, Modifier.fillMaxWidth()
                        )
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
                PeriodPickerActions(backdrop, visualConfig, onCancel = ::back,
                    cancelText = if (shownPage == CreationPickerPage.TIMING) "上一步" else "取消",
                    confirmText = when (shownPage) {
                        CreationPickerPage.FORM -> "下一步"
                        CreationPickerPage.TIMING -> "开始编辑"
                        else -> "确定"
                    }, onConfirm = {
                        error = null
                        when (shownPage) {
                            CreationPickerPage.FORM -> page = CreationPickerPage.TIMING
                            CreationPickerPage.TIMING -> create()
                            CreationPickerPage.COUNTS -> counts = pendingCounts
                            CreationPickerPage.STARTS -> starts = starts + constrainAutomaticPartStarts(targetConfig, template, pendingStarts)
                            CreationPickerPage.DURATION -> duration = pendingMinutes
                            CreationPickerPage.BREAK -> gap = pendingMinutes
                        }
                        if (shownPage == CreationPickerPage.DURATION || shownPage == CreationPickerPage.BREAK) page = CreationPickerPage.TIMING
                        else if (shownPage == CreationPickerPage.COUNTS || shownPage == CreationPickerPage.STARTS) page = CreationPickerPage.FORM
                    })
            }
        }
    ) {}
}
