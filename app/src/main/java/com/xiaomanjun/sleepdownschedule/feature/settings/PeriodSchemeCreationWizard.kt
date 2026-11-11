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
import com.xiaomanjun.sleepdownschedule.core.ui.settings.SettingsDatePickerRow
import com.xiaomanjun.sleepdownschedule.core.ui.settings.SettingsMinutePickerContent
import com.xiaomanjun.sleepdownschedule.core.ui.settings.SettingsPickerValueRow
import com.xiaomanjun.sleepdownschedule.core.ui.settings.SettingsTextFieldRow
import com.xiaomanjun.sleepdownschedule.core.ui.settings.SettingsToggleRow
import com.xiaomanjun.sleepdownschedule.domain.schedule.*
import com.xiaomanjun.sleepdownschedule.model.*
import java.time.LocalDate

private enum class CreationPickerPage(val title: String) {
    FORM("新建作息"), TIMING("课程与课间时长"), COUNTS("节数分配"), STARTS("时段起点"), DURATION("每节课时长"), BREAK("课间时长")
}

@Composable
internal fun PeriodSchemeCreationWizard(
    source: ScheduleRoutines,
    copying: Boolean,
    backdrop: Backdrop?,
    visualConfig: ScheduleConfigEntity,
    onDismiss: () -> Unit,
    onCreated: (PeriodTimelineSession, List<TimetableDateRuleEntity>) -> Unit,
) {
    val config = source.config
    val today = remember { LocalDate.now() }
    val startingSession = remember(source, copying) {
        source.timeline(if (copying) source.regularId else source.routineOn(today))
    }
    val active = startingSession.active
    var name by remember { mutableStateOf(if (copying) active.scheme.name else "新作息") }
    var startDate by remember { mutableStateOf(today) }
    var endDate by remember { mutableStateOf(today) }
    var allocation by remember {
        mutableStateOf(PeriodSchemeCreationState(PeriodDayPart.entries.associateWith { config.periodCount(it) }))
    }
    val enabledParts = allocation.enabledParts
    val counts = allocation.counts
    val initialStarts = remember(active) {
        PeriodDayPart.entries.associateWith {
            parseMinuteOfDay(when (it) {
                PeriodDayPart.MORNING -> active.scheme.morningStartTime
                PeriodDayPart.NOON -> active.scheme.noonStartTime
                PeriodDayPart.AFTERNOON -> active.scheme.afternoonStartTime
                PeriodDayPart.EVENING -> active.scheme.eveningStartTime
            }) ?: 0
        }
    }
    val initialDuration = active.scheme.classDurationMinutes.coerceIn(1, 300)
    val initialGap = active.scheme.breakDurationMinutes.coerceIn(0, initialDuration)
    var starts by remember { mutableStateOf(initialStarts) }
    var duration by remember { mutableIntStateOf(initialDuration) }
    var gap by remember { mutableIntStateOf(initialGap) }
    var page by remember { mutableStateOf(CreationPickerPage.FORM) }
    var pendingCounts by remember { mutableStateOf(counts) }
    var pendingStarts by remember { mutableStateOf(starts) }
    var pendingMinutes by remember { mutableIntStateOf(duration) }
    var error by remember { mutableStateOf<String?>(null) }
    var visible by remember { mutableStateOf(true) }
    var created by remember {
        mutableStateOf<Pair<PeriodTimelineSession, List<TimetableDateRuleEntity>>?>(null)
    }
    val parts = PeriodDayPart.entries.filter { it in enabledParts }
    val targetConfig = PeriodDayPart.entries.fold(config) { next, part ->
        next.withTimelineCount(part, if (part in enabledParts) counts.getValue(part) else 0)
    }
    val total = targetConfig.totalPeriodCount()
    val template = active.copy(
        scheme = active.scheme.copy(classDurationMinutes = duration, breakDurationMinutes = gap),
        overriddenPeriods = emptySet(), specialBreaks = emptyMap()
    )
    fun changeParts(value: PeriodSchemeCreationState?) {
        if (value == null) {
            error = "当前总节数不足，每个启用的分段至少需要 1 节，请先在节数分配中增加总节数。"
            return
        }
        allocation = value
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
        if (name.isBlank() && (!copying || name != active.scheme.name)) {
            error = "请填写作息名称"
            return
        }
        if (!copying && startDate > endDate) {
            error = "结束日期不能早于开始日期"
            return
        }
        if (!copying && source.rules.any { it.startDate == startDate.toString() && it.endDate == endDate.toString() }) {
            error = "该日期范围已有作息，请调整生效日期"
            return
        }
        val resized = if (targetConfig.hasSamePeriodTopology(config)) startingSession
        else if (source.canChangeStructure) resizeTimelineStructure(startingSession, targetConfig) else null
        if (resized == null) {
            error = "现有作息无法按所选节数排布，请调整节数或时长。"
            return
        }
        val id = if (copying) active.scheme.id else -1L
        val seed = resized.active
        val timingChanged = starts != initialStarts || duration != initialDuration || gap != initialGap
        val times = if (timingChanged) {
            buildList {
                parts.forEach { part ->
                    var cursor = starts.getValue(part)
                    repeat(counts.getValue(part)) {
                        val end = cursor + duration
                        if (end > LastMinuteOfDay) {
                            error = "${part.timelineLabel()}结束时间超出当天，请调整起点或时长"
                            return
                        }
                        add(PeriodSchemeTimeEntity(id, size + 1, timelineMinuteText(cursor), timelineMinuteText(end)))
                        cursor = end + gap
                    }
                }
            }
        } else seed.times.map { it.copy(schemeId = id) }
        val conflict = validateResolvedPeriodTimes(times)
        if (conflict != null) {
            error = conflict
            return
        }
        val requestedName = if (copying && name == active.scheme.name) name else name.trim()
        val named = seed.scheme.copy(id = id, name = requestedName)
        val createdDraft = if (!timingChanged) seed.copy(scheme = named, times = times) else PeriodSchemeDraft(
            scheme = named.copy(
                mode = PeriodSchemeMode.MANUAL,
                classDurationMinutes = duration,
                breakDurationMinutes = gap,
                morningStartTime = timelineMinuteText(starts.getValue(PeriodDayPart.MORNING)),
                noonStartTime = timelineMinuteText(starts.getValue(PeriodDayPart.NOON)),
                afternoonStartTime = timelineMinuteText(starts.getValue(PeriodDayPart.AFTERNOON)),
                eveningStartTime = timelineMinuteText(starts.getValue(PeriodDayPart.EVENING)),
            ),
            times = times,
        )
        val session = if (copying) resized.updateActive(createdDraft)
        else resized.copy(draft = resized.draft.copy(schemes = resized.draft.schemes + createdDraft, activeSchemeId = id))
        val rules = if (copying) source.rules else source.rules +
            TimetableDateRuleEntity(0, source.rules.size, id, startDate.toString(), endDate.toString())
        created = session to rules
        visible = false
    }
    SleepDownPickerDialog(
        visible, page.title,
        onDismissRequest = ::back,
        backdrop = backdrop, config = visualConfig,
        onDismissFinished = { created?.let { (session, rules) -> onCreated(session, rules) } ?: onDismiss() },
        scrollableContent = true,
        adjustForIme = page == CreationPickerPage.FORM,
        contentPadding = PaddingValues(SleepDownDesignTokens.QuickSheet.PickerContentPadding),
        couiContentPadding = if (page in setOf(CreationPickerPage.DURATION, CreationPickerPage.BREAK))
            PaddingValues(0.dp) else CouiPickerFormPadding,
        bottomActions = {
            val shownPage = page
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
                        CreationPickerPage.COUNTS -> allocation = allocation.withCounts(pendingCounts)
                        CreationPickerPage.STARTS -> starts = starts + constrainAutomaticPartStarts(targetConfig, template, pendingStarts)
                        CreationPickerPage.DURATION -> duration = pendingMinutes
                        CreationPickerPage.BREAK -> gap = pendingMinutes
                    }
                    if (shownPage == CreationPickerPage.DURATION || shownPage == CreationPickerPage.BREAK) page = CreationPickerPage.TIMING
                    else if (shownPage == CreationPickerPage.COUNTS || shownPage == CreationPickerPage.STARTS) page = CreationPickerPage.FORM
                })
        },
        contentTransitionKey = page,
        contentForState = { displayed ->
            val shownPage = displayed as CreationPickerPage
            Column(verticalArrangement = Arrangement.spacedBy(SleepDownDesignTokens.QuickSheet.PickerContentSpacing)) {
                Column {
                    when (shownPage) {
                        CreationPickerPage.FORM -> {
                            Text("第 1 步 · 节数与分段", fontSize = 12.sp,
                                color = androidx.compose.material3.LocalContentColor.current.copy(alpha = 0.66f))
                            SettingsTextFieldRow("作息名称", name, { name = it.take(60); error = null })
                            if (!copying) {
                                SettingsDatePickerRow("开始日期", formatScheduleDate(startDate), { value ->
                                    parseScheduleDate(value)?.let { day ->
                                        if (endDate == startDate || endDate < day) endDate = day
                                        startDate = day
                                        error = null
                                    }
                                }, backdrop, visualConfig)
                                SettingsDatePickerRow("结束日期", formatScheduleDate(endDate), { value ->
                                    parseScheduleDate(value)?.let { day -> endDate = day; error = null }
                                }, backdrop, visualConfig)
                            }
                            val split = PeriodDayPart.AFTERNOON in enabledParts
                            SettingsToggleRow("上午 / 下午分段", "", split, backdrop) {
                                changeParts(allocation.withSplitEnabled(it))
                            }
                            if (split) {
                                SettingsToggleRow("启用中午分段", "", PeriodDayPart.NOON in enabledParts, backdrop) {
                                    changeParts(allocation.withEnabledParts(if (it) enabledParts + PeriodDayPart.NOON else enabledParts - PeriodDayPart.NOON))
                                }
                                SettingsToggleRow("启用晚上分段", "", PeriodDayPart.EVENING in enabledParts, backdrop) {
                                    changeParts(allocation.withEnabledParts(if (it) enabledParts + PeriodDayPart.EVENING else enabledParts - PeriodDayPart.EVENING))
                                }
                            }
                            SettingsPickerValueRow("节数分配", "共 $total 节", onClick = {
                                pendingCounts = counts; page = CreationPickerPage.COUNTS
                            })
                            SettingsPickerValueRow("时段起点", parts.joinToString(" · ") { timelineMinuteText(starts.getValue(it)) }, onClick = {
                                pendingStarts = starts; page = CreationPickerPage.STARTS
                            })
                            if (!targetConfig.hasSamePeriodTopology(config)) Text(
                                "节数和分段由这张课表的所有作息共用，保存时会检查课程节次。",
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
                            pendingMinutes, { pendingMinutes = it }, 0..duration, Modifier.fillMaxWidth()
                        )
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }

            }
        }
    ) {}
}
