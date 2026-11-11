package com.xiaomanjun.sleepdownschedule.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.itos.coui.shape.CouiCapsuleShape
import com.kyant.backdrop.Backdrop
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.*
import com.xiaomanjun.sleepdownschedule.core.ui.settings.LocalCouiSettingsRowShape
import com.xiaomanjun.sleepdownschedule.core.ui.settings.SettingsDatePickerRow
import com.xiaomanjun.sleepdownschedule.core.ui.settings.SettingsDivider
import com.xiaomanjun.sleepdownschedule.core.ui.settings.SettingsGroup
import com.xiaomanjun.sleepdownschedule.core.ui.settings.SettingsInfoRow
import com.xiaomanjun.sleepdownschedule.core.ui.settings.SettingsPickerValueRow
import com.xiaomanjun.sleepdownschedule.core.ui.settings.SettingsTextFieldRow
import com.xiaomanjun.sleepdownschedule.core.ui.settings.SettingsToggleRow
import com.xiaomanjun.sleepdownschedule.domain.schedule.*
import com.xiaomanjun.sleepdownschedule.model.*
import java.time.LocalDate

private enum class CreationPickerPage(val title: String, val step: Int? = null) {
    DETAILS("新建作息", 1),
    STRUCTURE("节次与分段", 2),
    TIMING("上课时间", 3),
    COUNTS("节数分配"),
    DURATION("每节课时长"),
    BREAK("课间时长"),
}

private fun creationStarts(session: PeriodTimelineSession): Map<PeriodDayPart, Int> {
    val scheme = session.active.scheme
    return PeriodDayPart.entries.associateWith { part ->
        if (session.config.periodCount(part) > 0) timelinePartAnchorMinute(session.config, session.active, part)
        else parseMinuteOfDay(when (part) {
            PeriodDayPart.MORNING -> scheme.morningStartTime
            PeriodDayPart.NOON -> scheme.noonStartTime
            PeriodDayPart.AFTERNOON -> scheme.afternoonStartTime
            PeriodDayPart.EVENING -> scheme.eveningStartTime
        }) ?: 0
    }
}

@Composable
private fun PeriodCreationGroup(config: ScheduleConfigEntity, content: @Composable ColumnScope.() -> Unit) {
    CompositionLocalProvider(LocalCouiSettingsRowShape provides CouiCapsuleShape) {
        SettingsGroup(backdrop = null, config = config, topSpacing = 0.dp, content = content)
    }
}

@Composable
internal fun PeriodSchemeCreationWizard(
    source: ScheduleRoutines,
    copying: Boolean,
    sourceName: String?,
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
    var prepared by remember { mutableStateOf(startingSession) }
    var defaultStarts by remember { mutableStateOf(creationStarts(startingSession)) }
    val initialDuration = active.scheme.classDurationMinutes.coerceIn(1, 300)
    val initialGap = active.scheme.breakDurationMinutes.coerceIn(0, initialDuration)
    var starts by remember { mutableStateOf(defaultStarts) }
    var duration by remember { mutableIntStateOf(initialDuration) }
    var gap by remember { mutableIntStateOf(initialGap) }
    var page by remember { mutableStateOf(CreationPickerPage.DETAILS) }
    var pendingCounts by remember { mutableStateOf(counts) }
    var pendingStarts by remember { mutableStateOf(starts) }
    var startsPickerMounted by remember { mutableStateOf(false) }
    var startsPickerVisible by remember { mutableStateOf(false) }
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
    val template = prepared.active.copy(
        scheme = prepared.active.scheme.copy(classDurationMinutes = duration, breakDurationMinutes = gap),
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
        error = null
        when (page) {
            CreationPickerPage.DETAILS -> visible = false
            CreationPickerPage.STRUCTURE -> page = CreationPickerPage.DETAILS
            CreationPickerPage.TIMING, CreationPickerPage.COUNTS -> page = CreationPickerPage.STRUCTURE
            else -> page = CreationPickerPage.TIMING
        }
    }

    fun advanceDetails() {
        error = when {
            name.isBlank() && (!copying || name != active.scheme.name) -> "请填写作息名称"
            !copying && startDate > endDate -> "结束日期不能早于开始日期"
            !copying && source.rules.any { it.startDate == startDate.toString() && it.endDate == endDate.toString() } ->
                "该日期范围已有作息，请调整生效日期"
            else -> null
        }
        if (error == null) page = CreationPickerPage.STRUCTURE
    }

    fun advanceStructure() {
        val resized = if (targetConfig.hasSamePeriodTopology(config)) startingSession
            else if (source.canChangeStructure) resizeTimelineStructure(startingSession, targetConfig) else null
        if (resized == null) {
            error = "现有作息无法按所选节数排布，请调整节数或时长。"
            return
        }
        val overrides = starts.filter { (part, start) -> start != defaultStarts[part] }
        defaultStarts = creationStarts(resized)
        starts = defaultStarts + overrides
        prepared = resized
        page = CreationPickerPage.TIMING
    }

    fun create() {
        val id = if (copying) active.scheme.id else -1L
        val seed = prepared.active
        val timingChanged = parts.any { starts[it] != defaultStarts[it] } || duration != initialDuration || gap != initialGap
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
        val session = if (copying) prepared.updateActive(createdDraft)
        else prepared.copy(draft = prepared.draft.copy(schemes = prepared.draft.schemes + createdDraft, activeSchemeId = id))
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
        // Retain the input window's layout policy while the wizard changes pages.
        adjustForIme = true,
        contentPadding = PaddingValues(SleepDownDesignTokens.QuickSheet.PickerContentPadding),
        couiContentPadding = if (page == CreationPickerPage.DURATION || page == CreationPickerPage.BREAK)
            PaddingValues(0.dp) else CouiPickerFormPadding,
        bottomActions = {
            val shownPage = page
            PeriodPickerActions(
                backdrop, visualConfig,
                onCancel = ::back,
                cancelText = when (shownPage.step) {
                    1 -> "取消"
                    2, 3 -> "上一步"
                    else -> "返回"
                },
                confirmText = when (shownPage.step) {
                    1, 2 -> "下一步"
                    3 -> "开始编辑"
                    else -> "确定"
                },
                onConfirm = {
                    error = null
                    when (shownPage) {
                        CreationPickerPage.DETAILS -> advanceDetails()
                        CreationPickerPage.STRUCTURE -> advanceStructure()
                        CreationPickerPage.TIMING -> create()
                        CreationPickerPage.COUNTS -> {
                            allocation = allocation.withCounts(pendingCounts)
                            page = CreationPickerPage.STRUCTURE
                        }
                        CreationPickerPage.DURATION -> {
                            duration = pendingMinutes
                            page = CreationPickerPage.TIMING
                        }
                        CreationPickerPage.BREAK -> {
                            gap = pendingMinutes
                            page = CreationPickerPage.TIMING
                        }
                    }
                },
            )
        },
        contentTransitionKey = page,
        smoothContentResize = true,
        contentForState = { displayed ->
            val shownPage = displayed as CreationPickerPage
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                shownPage.step?.let { step ->
                    Text(
                        text = "第 ${step} 步，共 3 步",
                        modifier = Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
                when (shownPage) {
                    CreationPickerPage.DETAILS -> PeriodCreationGroup(visualConfig) {
                        if (copying && sourceName != null) {
                            SettingsInfoRow("来源课表", sourceName)
                            SettingsDivider()
                        }
                        SettingsTextFieldRow("作息名称", name, { name = it.take(60); error = null })
                        if (!copying) {
                            SettingsDivider()
                            SettingsDatePickerRow("开始日期", formatScheduleDate(startDate), { value ->
                                parseScheduleDate(value)?.let { day ->
                                    if (endDate == startDate || endDate < day) endDate = day
                                    startDate = day
                                    error = null
                                }
                            }, backdrop, visualConfig)
                            SettingsDivider()
                            SettingsDatePickerRow("结束日期", formatScheduleDate(endDate), { value ->
                                parseScheduleDate(value)?.let { day -> endDate = day; error = null }
                            }, backdrop, visualConfig)
                        }
                    }
                    CreationPickerPage.STRUCTURE -> {
                        val split = PeriodDayPart.AFTERNOON in enabledParts
                        val canChange = source.canChangeStructure
                        PeriodCreationGroup(visualConfig) {
                            SettingsToggleRow("上午 / 下午分段", "", split, backdrop, enabled = canChange) {
                                changeParts(allocation.withSplitEnabled(it))
                            }
                            if (split) {
                                SettingsDivider()
                                SettingsToggleRow("启用中午分段", "", PeriodDayPart.NOON in enabledParts, backdrop, enabled = canChange) {
                                    val part = PeriodDayPart.NOON
                                    changeParts(allocation.withEnabledParts(if (it) enabledParts + part else enabledParts - part))
                                }
                                SettingsDivider()
                                SettingsToggleRow("启用晚上分段", "", PeriodDayPart.EVENING in enabledParts, backdrop, enabled = canChange) {
                                    val part = PeriodDayPart.EVENING
                                    changeParts(allocation.withEnabledParts(if (it) enabledParts + part else enabledParts - part))
                                }
                            }
                            SettingsDivider()
                            SettingsPickerValueRow("节数分配", "共 $total 节", enabled = canChange, onClick = {
                                pendingCounts = counts
                                page = CreationPickerPage.COUNTS
                            })
                        }
                        if (!canChange || !targetConfig.hasSamePeriodTopology(config)) {
                            Text(
                                if (canChange) "节次和分段应用于本课表的全部作息。"
                                else "这张课表的原始节次不连续，可在时间轴调整已有节次的时间。",
                                modifier = Modifier.padding(horizontal = 16.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    CreationPickerPage.TIMING -> {
                        PeriodCreationGroup(visualConfig) {
                            SettingsPickerValueRow(
                                "时段起点", parts.joinToString(" · ") { timelineMinuteText(starts.getValue(it)) },
                                valueBelowTitle = parts.size > 2,
                                onClick = {
                                    pendingStarts = starts
                                    startsPickerMounted = true
                                    startsPickerVisible = true
                                },
                            )
                            SettingsDivider()
                            SettingsPickerValueRow("每节课时长", "$duration 分钟", onClick = {
                                pendingMinutes = duration
                                page = CreationPickerPage.DURATION
                            })
                            SettingsDivider()
                            SettingsPickerValueRow("课间时长", "$gap 分钟", onClick = {
                                pendingMinutes = gap
                                page = CreationPickerPage.BREAK
                            })
                        }
                        if (startsPickerMounted) {
                            SleepDownPickerDialog(
                                show = startsPickerVisible,
                                title = "时段起点",
                                onDismissRequest = { startsPickerVisible = false },
                                onDismissFinished = { startsPickerMounted = false },
                                backdrop = backdrop,
                                config = visualConfig,
                                contentPadding = PaddingValues(SleepDownDesignTokens.QuickSheet.PickerContentPadding),
                                couiContentPadding = CouiPickerFormPadding,
                                actions = listOf(
                                    LiquidAlertAction("返回", LiquidAlertActionStyle.Cancel) { startsPickerVisible = false },
                                    LiquidAlertAction("确定", LiquidAlertActionStyle.Primary) {
                                        starts = starts + constrainAutomaticPartStarts(prepared.config, template, pendingStarts)
                                        startsPickerVisible = false
                                    },
                                ),
                            ) {
                                PeriodQuickStartsContent(prepared.config, template, pendingStarts) {
                                    pendingStarts = pendingStarts + it
                                }
                            }
                        }
                    }
                    CreationPickerPage.COUNTS -> PeriodQuickCountContent(parts, pendingCounts) { pendingCounts = it }
                    CreationPickerPage.DURATION -> PeriodQuickSingleWheel(
                        pendingMinutes, { pendingMinutes = it }, gap.coerceAtLeast(1)..300, { "${it} 分钟" }
                    )
                    CreationPickerPage.BREAK -> PeriodQuickSingleWheel(
                        pendingMinutes, { pendingMinutes = it }, 0..duration, { "${it} 分钟" }
                    )
                }
                error?.let {
                    Text(it, modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
    ) {}
}
