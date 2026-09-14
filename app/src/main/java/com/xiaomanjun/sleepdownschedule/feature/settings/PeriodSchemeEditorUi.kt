package com.xiaomanjun.sleepdownschedule.feature.settings

import com.xiaomanjun.sleepdownschedule.app.ui.*
import com.xiaomanjun.sleepdownschedule.app.startup.*
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.*
import com.xiaomanjun.sleepdownschedule.glass.ui.*
import com.xiaomanjun.sleepdownschedule.*
import com.xiaomanjun.sleepdownschedule.core.ui.settings.*
import com.xiaomanjun.sleepdownschedule.feature.schedule.picker.*
import com.xiaomanjun.sleepdownschedule.feature.home.day.*
import com.xiaomanjun.sleepdownschedule.feature.home.week.*
import com.xiaomanjun.sleepdownschedule.core.remoteconfig.*
import com.xiaomanjun.sleepdownschedule.domain.schedule.PeriodTopologyOperation
import com.xiaomanjun.sleepdownschedule.domain.schedule.allocatePeriodCountsByStartTimes
import com.xiaomanjun.sleepdownschedule.domain.schedule.deletePeriodFromSchemeDraft
import com.xiaomanjun.sleepdownschedule.domain.schedule.insertPeriodIntoSchemeDraft
import com.xiaomanjun.sleepdownschedule.feature.importing.*
import com.xiaomanjun.sleepdownschedule.feature.agent.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import com.kyant.shapes.Capsule
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.kyant.backdrop.catalog.components.LiquidButton
import com.kyant.backdrop.Backdrop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import java.time.LocalTime


@Composable
private fun PeriodTimelineSeparator(label: String, tint: ComposeColor) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        Box(Modifier.weight(1f).height(1.dp).background(tint.copy(alpha = 0.28f)))
        Text(
            text = label,
            modifier = Modifier
                .clip(Capsule())
                .background(tint.copy(alpha = 0.18f))
                .padding(horizontal = 11.dp, vertical = 5.dp),
            color = tint,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
        Box(Modifier.weight(1f).height(1.dp).background(tint.copy(alpha = 0.28f)))
    }
}

@Composable
private fun PeriodEditorActionButton(
    label: String,
    backdrop: Backdrop?,
    config: ScheduleConfigEntity,
    modifier: Modifier = Modifier,
    tint: ComposeColor = ComposeColor(0xFF0A84FF),
    onClick: () -> Unit
) {
    val surface = tint.copy(alpha = if (appUsesDarkTheme(config)) 0.82f else 0.90f)
    if (backdrop != null) {
        LiquidButton(
            onClick = onClick,
            backdrop = backdrop,
            modifier = modifier.height(44.dp),
            height = 44.dp,
            surfaceColor = surface,
            tint = tint,
            contentPadding = PaddingValues(horizontal = 12.dp),
            blurRadius = 3.dp,
            lensHeight = 14.dp,
            lensAmount = 18.dp,
            chromaticAberration = false
        ) {
            Text(label, color = ComposeColor.White, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    } else {
        Box(
            modifier = modifier
                .height(44.dp)
                .clip(Capsule())
                .background(surface)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Text(label, color = ComposeColor.White, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

@Composable
internal fun PeriodSchemeEditor(
    state: AppState,
    backdrop: Backdrop?,
    config: ScheduleConfigEntity,
    draft: SchedulePeriodSchemesDraft,
    onDraftChange: (SchedulePeriodSchemesDraft) -> Unit,
    onCountsChange: (Int, Int, Int, Int) -> Unit
) {
    val active = draft.schemes.firstOrNull { it.scheme.id == draft.activeSchemeId } ?: draft.schemes.first()
    var localError by remember { mutableStateOf<String?>(null) }
    var editingPeriod by remember { mutableIntStateOf(-1) }
    var editingPart by remember { mutableStateOf<PeriodDayPart?>(null) }
    var pickerStartHour by remember { mutableIntStateOf(8) }
    var pickerStartMinute by remember { mutableIntStateOf(0) }
    var pickerEndHour by remember { mutableIntStateOf(8) }
    var pickerEndMinute by remember { mutableIntStateOf(45) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showBreakPicker by remember { mutableStateOf(false) }
    var showCountPicker by remember { mutableStateOf(false) }
    var countPickerMorning by remember { mutableIntStateOf(config.morningPeriodCount.coerceAtLeast(1)) }
    var countPickerNoon by remember { mutableIntStateOf(config.noonPeriodCount.coerceAtLeast(1)) }
    var countPickerAfternoon by remember { mutableIntStateOf(config.afternoonPeriodCount.coerceAtLeast(1)) }
    var countPickerEvening by remember { mutableIntStateOf(config.eveningPeriodCount.coerceAtLeast(1)) }
    var countPickerTotal by remember { mutableIntStateOf(config.totalPeriodCount().coerceAtLeast(1)) }
    var showSegmentStartPicker by remember { mutableStateOf(false) }
    var morningStartMinute by remember { mutableIntStateOf(8 * 60) }
    var noonStartMinute by remember { mutableIntStateOf(12 * 60) }
    var afternoonStartMinute by remember { mutableIntStateOf(14 * 60) }
    var eveningStartMinute by remember { mutableIntStateOf(19 * 60) }
    var breakAfter by remember { mutableIntStateOf(1) }
    var breakPickerPosition by remember { mutableIntStateOf(0) }
    var breakMinutes by remember { mutableIntStateOf(20) }
    var pendingAutoSwitch by remember { mutableStateOf<PeriodSchemeDraft?>(null) }
    var pendingAutoOverrides by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var timeConflictMessage by remember { mutableStateOf<String?>(null) }
    var lastSegmentedCounts by remember(config.id) {
        mutableStateOf(
            listOf(
                config.morningPeriodCount,
                config.noonPeriodCount,
                config.afternoonPeriodCount,
                config.eveningPeriodCount
            )
        )
    }
    LaunchedEffect(
        config.morningPeriodCount,
        config.noonPeriodCount,
        config.afternoonPeriodCount,
        config.eveningPeriodCount
    ) {
        if (config.morningPeriodCount > 0 && config.afternoonPeriodCount > 0) {
            lastSegmentedCounts = listOf(
                config.morningPeriodCount,
                config.noonPeriodCount,
                config.afternoonPeriodCount,
                config.eveningPeriodCount
            )
        }
    }
    val specialBreakCandidates = PeriodDayPart.entries.flatMap { part ->
        config.periodRange(part).toList().dropLast(1)
    }

    fun updateActive(transform: (PeriodSchemeDraft) -> PeriodSchemeDraft) {
        onDraftChange(draft.copy(schemes = draft.schemes.map { if (it.scheme.id == active.scheme.id) transform(it) else it }))
    }

    fun addPeriod(part: PeriodDayPart) {
        val range = config.periodRange(part)
        val after = when {
            !range.isEmpty() -> range.last
            part == PeriodDayPart.MORNING -> 0
            part == PeriodDayPart.NOON -> config.morningPeriodCount
            part == PeriodDayPart.AFTERNOON -> config.morningPeriodCount + config.noonPeriodCount
            else -> config.morningPeriodCount + config.noonPeriodCount + config.afternoonPeriodCount
        }
        val newConfig = when (part) {
            PeriodDayPart.MORNING -> config.copy(morningPeriodCount = config.morningPeriodCount + 1)
            PeriodDayPart.NOON -> config.copy(noonPeriodCount = config.noonPeriodCount + 1)
            PeriodDayPart.AFTERNOON -> config.copy(afternoonPeriodCount = config.afternoonPeriodCount + 1)
            PeriodDayPart.EVENING -> config.copy(eveningPeriodCount = config.eveningPeriodCount + 1)
        }
        val migratedSchemes = mutableListOf<PeriodSchemeDraft>()
        draft.schemes.forEach { item ->
            val migrated = insertPeriodIntoSchemeDraft(item, after, newConfig)
            if (migrated == null) {
                localError = "${item.scheme.name} 无法在当前时间范围内新增节次"
                return
            }
            migratedSchemes += migrated
        }
        onCountsChange(newConfig.morningPeriodCount, newConfig.noonPeriodCount, newConfig.afternoonPeriodCount, newConfig.eveningPeriodCount)
        onDraftChange(
            draft.copy(
                schemes = migratedSchemes,
                topologyOperations = draft.topologyOperations + PeriodTopologyOperation.AddAfter(after)
            )
        )
    }

    fun deletePeriod(index: Int) {
        val part = PeriodDayPart.entries.firstOrNull { index in config.periodRange(it) } ?: return
        val newConfig = when (part) {
            PeriodDayPart.MORNING -> config.copy(morningPeriodCount = (config.morningPeriodCount - 1).coerceAtLeast(0))
            PeriodDayPart.NOON -> config.copy(noonPeriodCount = (config.noonPeriodCount - 1).coerceAtLeast(0))
            PeriodDayPart.AFTERNOON -> config.copy(afternoonPeriodCount = (config.afternoonPeriodCount - 1).coerceAtLeast(0))
            PeriodDayPart.EVENING -> config.copy(eveningPeriodCount = (config.eveningPeriodCount - 1).coerceAtLeast(0))
        }
        if (newConfig.totalPeriodCount() == 0) {
            localError = "至少需要保留一个节次"
            return
        }
        val migratedSchemes = mutableListOf<PeriodSchemeDraft>()
        draft.schemes.forEach { item ->
            val migrated = deletePeriodFromSchemeDraft(item, index, newConfig)
            if (migrated == null) {
                localError = "${item.scheme.name} 无法删除当前节次，请先修正该方案的时间"
                return
            }
            migratedSchemes += migrated
        }
        onCountsChange(newConfig.morningPeriodCount, newConfig.noonPeriodCount, newConfig.afternoonPeriodCount, newConfig.eveningPeriodCount)
        onDraftChange(
            draft.copy(
                schemes = migratedSchemes,
                topologyOperations = draft.topologyOperations + PeriodTopologyOperation.Delete(index)
            )
        )
    }

    fun repartitionExistingPeriods(
        morning: Int,
        noon: Int,
        afternoon: Int,
        evening: Int
    ) {
        val total = config.totalPeriodCount()
        if (morning + noon + afternoon + evening != total || total <= 0) return
        val repartitioned = config.copy(
            morningPeriodCount = morning,
            noonPeriodCount = noon,
            afternoonPeriodCount = afternoon,
            eveningPeriodCount = evening
        )
        val repartitionedSchemes = draft.schemes.map { item ->
            if (item.scheme.mode == PeriodSchemeMode.AUTO_MATCH) {
                item.copy(times = resolveSchemeTimes(repartitioned, item))
            } else {
                item
            }
        }
        val invalidScheme = repartitionedSchemes.firstOrNull { item ->
            item.times.size != total || validateResolvedPeriodTimes(item.times) != null
        }
        if (invalidScheme != null) {
            localError = "${invalidScheme.scheme.name} 的时间无法适配当前节数分配"
            return
        }
        onCountsChange(morning, noon, afternoon, evening)
        onDraftChange(draft.copy(schemes = repartitionedSchemes))
    }

    fun changePartCounts(requestedMorning: Int, requestedNoon: Int, requestedAfternoon: Int, requestedEvening: Int) {
        val targets = mapOf(
            PeriodDayPart.MORNING to requestedMorning.coerceIn(0, 40),
            PeriodDayPart.NOON to requestedNoon.coerceIn(0, 40),
            PeriodDayPart.AFTERNOON to requestedAfternoon.coerceIn(0, 40),
            PeriodDayPart.EVENING to requestedEvening.coerceIn(0, 40)
        )
        if (targets.values.sum() == 0) {
            localError = "上午、中午、下午、晚上至少需要启用一个时段"
            return
        }
        val currentTotal = config.totalPeriodCount()
        val requestedTotal = targets.values.sum()
        if (requestedTotal == currentTotal) {
            repartitionExistingPeriods(
                morning = targets.getValue(PeriodDayPart.MORNING),
                noon = targets.getValue(PeriodDayPart.NOON),
                afternoon = targets.getValue(PeriodDayPart.AFTERNOON),
                evening = targets.getValue(PeriodDayPart.EVENING)
            )
            return
        }
        var workingConfig = config
        var workingSchemes = draft.schemes
        val operations = mutableListOf<PeriodTopologyOperation>()
        PeriodDayPart.entries.forEach { part ->
            val oldCount = workingConfig.periodCount(part)
            val targetCount = targets.getValue(part)
            if (targetCount > oldCount) repeat(targetCount - oldCount) {
                val range = workingConfig.periodRange(part)
                val after = when {
                    !range.isEmpty() -> range.last
                    part == PeriodDayPart.MORNING -> 0
                    part == PeriodDayPart.NOON -> workingConfig.morningPeriodCount
                    part == PeriodDayPart.AFTERNOON -> workingConfig.morningPeriodCount + workingConfig.noonPeriodCount
                    else -> workingConfig.morningPeriodCount + workingConfig.noonPeriodCount + workingConfig.afternoonPeriodCount
                }
                workingConfig = when (part) {
                    PeriodDayPart.MORNING -> workingConfig.copy(morningPeriodCount = workingConfig.morningPeriodCount + 1)
                    PeriodDayPart.NOON -> workingConfig.copy(noonPeriodCount = workingConfig.noonPeriodCount + 1)
                    PeriodDayPart.AFTERNOON -> workingConfig.copy(afternoonPeriodCount = workingConfig.afternoonPeriodCount + 1)
                    PeriodDayPart.EVENING -> workingConfig.copy(eveningPeriodCount = workingConfig.eveningPeriodCount + 1)
                }
                val migratedSchemes = mutableListOf<PeriodSchemeDraft>()
                workingSchemes.forEach { item ->
                    val migrated = insertPeriodIntoSchemeDraft(item, after, workingConfig)
                    if (migrated == null) {
                        localError = "${item.scheme.name} 无法在当前时间范围内新增节次"
                        return
                    }
                    migratedSchemes += migrated
                }
                workingSchemes = migratedSchemes
                operations += PeriodTopologyOperation.AddAfter(after)
            }
            if (targetCount < oldCount) repeat(oldCount - targetCount) {
                val range = workingConfig.periodRange(part)
                val index = range.last
                val nextConfig = when (part) {
                    PeriodDayPart.MORNING -> workingConfig.copy(morningPeriodCount = workingConfig.morningPeriodCount - 1)
                    PeriodDayPart.NOON -> workingConfig.copy(noonPeriodCount = workingConfig.noonPeriodCount - 1)
                    PeriodDayPart.AFTERNOON -> workingConfig.copy(afternoonPeriodCount = workingConfig.afternoonPeriodCount - 1)
                    PeriodDayPart.EVENING -> workingConfig.copy(eveningPeriodCount = workingConfig.eveningPeriodCount - 1)
                }
                val migratedSchemes = mutableListOf<PeriodSchemeDraft>()
                workingSchemes.forEach { item ->
                    val migrated = deletePeriodFromSchemeDraft(item, index, nextConfig)
                    if (migrated == null) {
                        localError = "${item.scheme.name} 无法删除当前节次，请先修正该方案的时间"
                        return
                    }
                    migratedSchemes += migrated
                }
                workingSchemes = migratedSchemes
                workingConfig = nextConfig
                operations += PeriodTopologyOperation.Delete(index)
            }
        }
        onCountsChange(workingConfig.morningPeriodCount, workingConfig.noonPeriodCount, workingConfig.afternoonPeriodCount, workingConfig.eveningPeriodCount)
        onDraftChange(
            draft.copy(
                schemes = workingSchemes,
                topologyOperations = draft.topologyOperations + operations
            )
        )
    }

    fun repartitionForEnabledParts(enabledParts: Set<PeriodDayPart>) {
        val resolvedTimes = resolveSchemeTimes(config, active).sortedBy { it.periodIndex }
        val resolvedByIndex = resolvedTimes.associateBy { it.periodIndex }
        fun configuredStart(part: PeriodDayPart): String = when (part) {
            PeriodDayPart.MORNING -> active.scheme.morningStartTime
            PeriodDayPart.NOON -> active.scheme.noonStartTime
            PeriodDayPart.AFTERNOON -> active.scheme.afternoonStartTime
            PeriodDayPart.EVENING -> active.scheme.eveningStartTime
        }
        val startMinutes = PeriodDayPart.entries.associateWith { part ->
            config.periodRange(part).firstOrNull()
                ?.let(resolvedByIndex::get)
                ?.startTime
                ?.let(::parseMinuteOfDay)
                ?: parseMinuteOfDay(configuredStart(part))
                ?: when (part) {
                    PeriodDayPart.MORNING -> 8 * 60
                    PeriodDayPart.NOON -> 12 * 60
                    PeriodDayPart.AFTERNOON -> 14 * 60
                    PeriodDayPart.EVENING -> 19 * 60
                }
        }
        val orderedStartMinutes = resolvedTimes.map { time ->
            parseMinuteOfDay(time.startTime) ?: run {
                localError = "第 ${time.periodIndex} 节开始时间无效，请先修正时间"
                return
            }
        }
        val counts = allocatePeriodCountsByStartTimes(
            orderedPeriodStartMinutes = orderedStartMinutes,
            enabledParts = enabledParts,
            partStartMinutes = startMinutes
        )
        if (counts == null) {
            localError = "总节数不足，无法让每个已启用时段至少保留一节课"
            return
        }
        lastSegmentedCounts = listOf(counts.morning, counts.noon, counts.afternoon, counts.evening)
        localError = null
        repartitionExistingPeriods(counts.morning, counts.noon, counts.afternoon, counts.evening)
    }

    fun enableMorningAfternoonSplit() {
        val total = config.totalPeriodCount()
        if (total < 2) {
            localError = "至少需要 2 节课才能启用上午 / 下午分段"
            return
        }
        val remembered = lastSegmentedCounts.takeIf {
            it.size == 4 && it.sum() == total && it[0] > 0 && it[2] > 0
        }
        if (remembered != null) {
            repartitionExistingPeriods(remembered[0], remembered[1], remembered[2], remembered[3])
            return
        }
        val firstAfternoonIndex = active.times
            .sortedBy { it.periodIndex }
            .indexOfFirst { time ->
                runCatching { LocalTime.parse(time.startTime).hour >= 12 }.getOrDefault(false)
            }
        val morning = firstAfternoonIndex
            .takeIf { it in 1 until total }
            ?: ((total + 1) / 2)
        repartitionExistingPeriods(morning, 0, total - morning, 0)
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SettingsGroup(backdrop = backdrop, config = state.config, modifier = Modifier.fillMaxWidth()) {
            val selectedIndex = draft.schemes.indexOfFirst { it.scheme.id == draft.activeSchemeId }.coerceAtLeast(0)
            SleepDownLiquidDropdownPreference(
                items = draft.schemes.map { it.scheme.name },
                selectedIndex = selectedIndex,
                title = "当前作息方案",
                backdrop = backdrop,
                config = state.config,
                modifier = Modifier.fillMaxWidth(),
                insideMargin = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                maxHeight = 318.dp,
                onExpandedChange = {},
                onSelectedIndexChange = { index ->
                    draft.schemes.getOrNull(index)?.let { item ->
                        onDraftChange(draft.copy(activeSchemeId = item.scheme.id))
                    }
                }
            )
            SettingsDivider()
            SettingsTextFieldRow(
                "方案名称",
                active.scheme.name,
                { name -> updateActive { it.copy(scheme = it.scheme.copy(name = name.ifBlank { "未命名作息" })) } }
            )
            SettingsDivider()
            Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PeriodEditorActionButton("新建", backdrop, state.config, modifier = Modifier.weight(1f), onClick = {
                    val tempId = (draft.schemes.minOfOrNull { it.scheme.id } ?: 0L).coerceAtMost(0L) - 1L
                    val newScheme = PeriodSchemeEntity(
                        id = tempId,
                        scheduleId = config.id,
                        name = "新作息",
                        mode = PeriodSchemeMode.AUTO_MATCH,
                        classDurationMinutes = active.scheme.classDurationMinutes,
                        breakDurationMinutes = active.scheme.breakDurationMinutes,
                        morningStartTime = active.scheme.morningStartTime,
                        noonStartTime = active.scheme.noonStartTime,
                        afternoonStartTime = active.scheme.afternoonStartTime,
                        eveningStartTime = active.scheme.eveningStartTime
                    )
                    val newItem = PeriodSchemeDraft(newScheme, emptyList()).let {
                        it.copy(times = resolveSchemeTimes(config, it))
                    }
                    onDraftChange(draft.copy(schemes = draft.schemes + newItem, activeSchemeId = tempId))
                })
                PeriodEditorActionButton("复制", backdrop, state.config, modifier = Modifier.weight(1f), tint = ComposeColor(0xFF6750A4), onClick = {
                    val tempId = (draft.schemes.minOfOrNull { it.scheme.id } ?: 0L).coerceAtMost(0L) - 1L
                    val copy = active.copy(
                        scheme = active.scheme.copy(id = tempId, name = "${active.scheme.name} 副本", isActive = false),
                        times = active.times.map { it.copy(schemeId = tempId) }
                    )
                    onDraftChange(draft.copy(schemes = draft.schemes + copy, activeSchemeId = tempId))
                })
                PeriodEditorActionButton("删除", backdrop, state.config, modifier = Modifier.weight(1f), tint = ComposeColor(0xFFFF453A), onClick = {
                    if (draft.schemes.size <= 1) localError = "至少需要保留一套作息方案"
                    else {
                        val removedIndex = draft.schemes.indexOfFirst { it.scheme.id == active.scheme.id }
                        val remaining = draft.schemes.filterNot { it.scheme.id == active.scheme.id }
                        val adjacent = remaining[removedIndex.coerceAtMost(remaining.lastIndex)]
                        onDraftChange(draft.copy(schemes = remaining, activeSchemeId = adjacent.scheme.id))
                    }
                })
            }
        }

        SettingsGroup(backdrop = backdrop, config = state.config, modifier = Modifier.fillMaxWidth()) {
            SleepDownLiquidDropdownPreference(
                items = listOf("手动模式", "自动匹配"),
                selectedIndex = if (active.scheme.mode == PeriodSchemeMode.MANUAL) 0 else 1,
                title = "作息编辑模式",
                backdrop = backdrop,
                config = state.config,
                modifier = Modifier.fillMaxWidth(),
                insideMargin = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                maxHeight = 240.dp,
                onExpandedChange = {},
                onSelectedIndexChange = { selected ->
                        if (selected == 0 && active.scheme.mode != PeriodSchemeMode.MANUAL) {
                            val frozen = resolveSchemeTimes(config, active)
                            updateActive { it.copy(scheme = it.scheme.copy(mode = PeriodSchemeMode.MANUAL), times = frozen) }
                        } else if (selected == 1 && active.scheme.mode != PeriodSchemeMode.AUTO_MATCH) {
                            val times = active.times.sortedBy { it.periodIndex }
                            val partForIndex: (Int) -> PeriodDayPart? = { index ->
                                PeriodDayPart.entries.firstOrNull { index in config.periodRange(it) }
                            }
                            val detectedSpecialBreaks = times.zipWithNext().mapNotNull { (left, right) ->
                                if (partForIndex(left.periodIndex) != partForIndex(right.periodIndex)) return@mapNotNull null
                                val leftEnd = runCatching { LocalTime.parse(left.endTime) }.getOrNull() ?: return@mapNotNull null
                                val rightStart = runCatching { LocalTime.parse(right.startTime) }.getOrNull() ?: return@mapNotNull null
                                val gap = java.time.Duration.between(leftEnd, rightStart).toMinutes().toInt()
                                if (gap >= 0 && gap != active.scheme.breakDurationMinutes) left.periodIndex to gap else null
                            }.toMap()
                            val autoCandidate = active.copy(
                                scheme = active.scheme.copy(
                                    mode = PeriodSchemeMode.AUTO_MATCH,
                                    morningStartTime = times.firstOrNull { t -> t.periodIndex in config.periodRange(PeriodDayPart.MORNING) }?.startTime ?: active.scheme.morningStartTime,
                                    noonStartTime = times.firstOrNull { t -> t.periodIndex in config.periodRange(PeriodDayPart.NOON) }?.startTime ?: active.scheme.noonStartTime,
                                    afternoonStartTime = times.firstOrNull { t -> t.periodIndex in config.periodRange(PeriodDayPart.AFTERNOON) }?.startTime ?: active.scheme.afternoonStartTime,
                                    eveningStartTime = times.firstOrNull { t -> t.periodIndex in config.periodRange(PeriodDayPart.EVENING) }?.startTime ?: active.scheme.eveningStartTime
                                ),
                                specialBreaks = detectedSpecialBreaks,
                                overriddenPeriods = emptySet()
                            )
                            val generatedByIndex = resolveSchemeTimes(config, autoCandidate).associateBy { it.periodIndex }
                            val changedIndices = times.filter { manual ->
                                val generated = generatedByIndex[manual.periodIndex]
                                generated == null || generated.startTime != manual.startTime || generated.endTime != manual.endTime
                            }.mapTo(mutableSetOf()) { it.periodIndex }
                            if (changedIndices.isEmpty()) {
                                updateActive { autoCandidate.copy(times = generatedByIndex.values.sortedBy { it.periodIndex }) }
                            } else {
                                pendingAutoSwitch = autoCandidate
                                pendingAutoOverrides = changedIndices
                            }
                        }
                }
            )
            if (active.scheme.mode == PeriodSchemeMode.AUTO_MATCH) {
                SettingsDivider()
                SettingsTextFieldRow("单节课分钟数", active.scheme.classDurationMinutes.toString(), { value ->
                    value.toIntOrNull()?.let { minutes -> updateActive { it.copy(scheme = it.scheme.copy(classDurationMinutes = minutes.coerceIn(1, 300))) } }
                }, KeyboardType.Number)
                SettingsDivider()
                SettingsTextFieldRow("普通课间分钟数", active.scheme.breakDurationMinutes.toString(), { value ->
                    value.toIntOrNull()?.let { minutes -> updateActive { it.copy(scheme = it.scheme.copy(breakDurationMinutes = minutes.coerceIn(0, 300))) } }
                }, KeyboardType.Number)
            }
            val morningAfternoonSplitEnabled =
                config.morningPeriodCount > 0 && config.afternoonPeriodCount > 0
            SettingsDivider()
            SettingsToggleRow(
                title = "启用上午 / 下午分段",
                subtitle = if (morningAfternoonSplitEnabled) {
                    "上午 ${config.morningPeriodCount} 节 · 下午 ${config.afternoonPeriodCount} 节"
                } else {
                    "未分段 · 共 ${config.totalPeriodCount()} 节"
                },
                checked = morningAfternoonSplitEnabled,
                backdrop = backdrop,
                onCheckedChange = { enabled ->
                    if (enabled) {
                        enableMorningAfternoonSplit()
                    } else {
                        lastSegmentedCounts = listOf(
                            config.morningPeriodCount,
                            config.noonPeriodCount,
                            config.afternoonPeriodCount,
                            config.eveningPeriodCount
                        )
                        // Disabling segmentation only changes boundaries. It never deletes a
                        // period, remaps a course, or trips the old "last switch" rollback.
                        repartitionExistingPeriods(config.totalPeriodCount(), 0, 0, 0)
                    }
                }
            )
            listOf(PeriodDayPart.NOON to "中午", PeriodDayPart.EVENING to "晚上")
                .forEach { (part, title) ->
                    val count = config.periodCount(part)
                    val range = config.periodRange(part)
                    SettingsDivider()
                    SettingsToggleRow(
                        title = "启用$title",
                        subtitle = if (!morningAfternoonSplitEnabled) {
                            "请先启用上午 / 下午分段"
                        } else if (count == 0) {
                            "已关闭"
                        } else {
                            "${count} 节 · 第 ${range.first}-${range.last} 节"
                        },
                        checked = count > 0,
                        backdrop = backdrop,
                        enabled = morningAfternoonSplitEnabled,
                        onCheckedChange = { enabled ->
                            val nextEnabledParts = PeriodDayPart.entries.filterTo(linkedSetOf()) { candidate ->
                                if (candidate == part) enabled else config.periodCount(candidate) > 0
                            }
                            repartitionForEnabledParts(nextEnabledParts)
                        }
                    )
                }
            SettingsDivider()
            SettingsPickerValueRow(
                title = "节数分配",
                value = PeriodDayPart.entries.filter { config.periodCount(it) > 0 }.joinToString(" · ") { part ->
                    val name = when (part) { PeriodDayPart.MORNING -> "上午"; PeriodDayPart.NOON -> "中午"; PeriodDayPart.AFTERNOON -> "下午"; PeriodDayPart.EVENING -> "晚上" }
                    "$name ${config.periodCount(part)}"
                },
                onClick = {
                    countPickerMorning = config.morningPeriodCount.coerceAtLeast(1)
                    countPickerNoon = config.noonPeriodCount.coerceAtLeast(1)
                    countPickerAfternoon = config.afternoonPeriodCount.coerceAtLeast(1)
                    countPickerEvening = config.eveningPeriodCount.coerceAtLeast(1)
                    countPickerTotal = config.totalPeriodCount().coerceAtLeast(1)
                    showCountPicker = true
                }
            )
            if (active.scheme.mode == PeriodSchemeMode.AUTO_MATCH) {
                SettingsDivider()
                SettingsPickerValueRow(
                    title = "时段起点",
                    value = PeriodDayPart.entries.filter { config.periodCount(it) > 0 }.joinToString(" · ") { part ->
                        when (part) {
                            PeriodDayPart.MORNING -> active.scheme.morningStartTime
                            PeriodDayPart.NOON -> active.scheme.noonStartTime
                            PeriodDayPart.AFTERNOON -> active.scheme.afternoonStartTime
                            PeriodDayPart.EVENING -> active.scheme.eveningStartTime
                        }
                    },
                    onClick = {
                        morningStartMinute = runCatching { LocalTime.parse(active.scheme.morningStartTime) }.getOrDefault(LocalTime.of(8, 0)).let { it.hour * 60 + it.minute }
                        noonStartMinute = runCatching { LocalTime.parse(active.scheme.noonStartTime) }.getOrDefault(LocalTime.of(12, 0)).let { it.hour * 60 + it.minute }
                        afternoonStartMinute = runCatching { LocalTime.parse(active.scheme.afternoonStartTime) }.getOrDefault(LocalTime.of(14, 0)).let { it.hour * 60 + it.minute }
                        eveningStartMinute = runCatching { LocalTime.parse(active.scheme.eveningStartTime) }.getOrDefault(LocalTime.of(19, 0)).let { it.hour * 60 + it.minute }
                        showSegmentStartPicker = true
                    }
                )
            }
            if (active.scheme.mode == PeriodSchemeMode.AUTO_MATCH) {
                active.specialBreaks.toSortedMap().forEach { (after, minutes) ->
                    SettingsDivider()
                    SettingsPickerValueRow("特殊课间", "第 $after 节后 · ${minutes} 分钟", onClick = {
                        breakAfter = after
                        breakPickerPosition = specialBreakCandidates.indexOf(after).coerceAtLeast(0)
                        breakMinutes = minutes
                        showBreakPicker = true
                    })
                }
                SettingsDivider()
                Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PeriodEditorActionButton("+特殊课间", backdrop, state.config, modifier = Modifier.weight(1f), tint = ComposeColor(0xFF6750A4), onClick = {
                        val candidate = specialBreakCandidates.firstOrNull { it !in active.specialBreaks }
                        if (candidate == null) {
                            localError = "当前时段内没有可插入特殊课间的位置"
                        } else {
                            breakAfter = candidate
                            breakPickerPosition = specialBreakCandidates.indexOf(candidate)
                            breakMinutes = 20
                            showBreakPicker = true
                        }
                    })
                    PeriodEditorActionButton("应用自动匹配", backdrop, state.config, modifier = Modifier.weight(1.35f), onClick = {
                        updateActive { item -> item.copy(times = resolveSchemeTimes(config, item)) }
                        localError = null
                    })
                }
            }
        }

        val resolved = resolveSchemeTimes(config, active)
        SettingsGroup(backdrop = backdrop, config = state.config, modifier = Modifier.fillMaxWidth()) {
            resolved.forEachIndexed { position, time ->
                if (position > 0) {
                    val previous = resolved[position - 1]
                    val dayPartLabel = when {
                        config.noonPeriodCount > 0 && time.periodIndex == config.morningPeriodCount + 1 -> "中午时段"
                        config.afternoonPeriodCount > 0 && time.periodIndex == config.morningPeriodCount + config.noonPeriodCount + 1 -> "下午时段"
                        config.eveningPeriodCount > 0 && time.periodIndex == config.morningPeriodCount + config.noonPeriodCount + config.afternoonPeriodCount + 1 -> "晚上时段"
                        else -> null
                    }
                    val specialBreak = active.specialBreaks[previous.periodIndex]
                    when {
                        dayPartLabel != null -> PeriodTimelineSeparator(dayPartLabel, ComposeColor(0xFF0A84FF))
                        specialBreak != null -> PeriodTimelineSeparator("特殊课间 · ${specialBreak}分钟", ComposeColor(0xFF6750A4))
                        else -> SettingsDivider()
                    }
                }
                SettingsPickerValueRow(
                    if (time.periodIndex in active.overriddenPeriods) "第 ${time.periodIndex} 节 · 已覆盖" else "第 ${time.periodIndex} 节",
                    "${time.startTime} - ${time.endTime}",
                    onClick = {
                        editingPart = null
                        editingPeriod = time.periodIndex
                        val start = LocalTime.parse(time.startTime)
                        val end = LocalTime.parse(time.endTime)
                        pickerStartHour = start.hour; pickerStartMinute = start.minute
                        pickerEndHour = end.hour; pickerEndMinute = end.minute
                        showTimePicker = true
                    }
                )
            }
        }

        localError?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 4.dp)) }
    }

    val popupBackdrop = LocalSettingsPopupBackdrop.current ?: backdrop
    val enabledParts = PeriodDayPart.entries.filter { config.periodCount(it) > 0 }
    SleepDownPickerDialog(
        show = showCountPicker,
        title = "节数分配",
        onDismissRequest = { showCountPicker = false },
        backdrop = popupBackdrop,
        config = state.config,
        contentPadding = PaddingValues(SleepDownDesignTokens.QuickSheet.PickerContentPadding)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("总节次", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            top.yukonga.miuix.kmp.basic.NumberPicker(
                value = countPickerTotal,
                onValueChange = { changed ->
                    countPickerTotal = changed
                    var remaining = changed
                    enabledParts.forEachIndexed { index, part ->
                        val slotsAfter = enabledParts.lastIndex - index
                        val current = when (part) {
                            PeriodDayPart.MORNING -> countPickerMorning
                            PeriodDayPart.NOON -> countPickerNoon
                            PeriodDayPart.AFTERNOON -> countPickerAfternoon
                            PeriodDayPart.EVENING -> countPickerEvening
                        }
                        val allocated = if (index == enabledParts.lastIndex) remaining else current.coerceIn(1, (remaining - slotsAfter).coerceAtLeast(1))
                        when (part) {
                            PeriodDayPart.MORNING -> countPickerMorning = allocated
                            PeriodDayPart.NOON -> countPickerNoon = allocated
                            PeriodDayPart.AFTERNOON -> countPickerAfternoon = allocated
                            PeriodDayPart.EVENING -> countPickerEvening = allocated
                        }
                        remaining -= allocated
                    }
                },
                range = enabledParts.size.coerceAtLeast(1)..40,
                visibleItemCount = 3,
                label = { "${it}节" },
                modifier = Modifier.fillMaxWidth().height(112.dp)
            )
            Text("时段分配", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val fontScale = LocalDensity.current.fontScale
                val columnWidth = maxWidth / enabledParts.size.coerceAtLeast(1)
                val pickerStyle = top.yukonga.miuix.kmp.theme.MiuixTheme.textStyles.title1.copy(
                    fontSize = when {
                        columnWidth < 100.dp || fontScale > 1.3f -> 17.sp
                        columnWidth < 140.dp || fontScale > 1.1f -> 20.sp
                        else -> 28.sp
                    }
                )
                Row(Modifier.fillMaxWidth()) {
                    enabledParts.forEach { part ->
                        val value = when (part) {
                            PeriodDayPart.MORNING -> countPickerMorning
                            PeriodDayPart.NOON -> countPickerNoon
                            PeriodDayPart.AFTERNOON -> countPickerAfternoon
                            PeriodDayPart.EVENING -> countPickerEvening
                        }
                        val name = when (part) { PeriodDayPart.MORNING -> "上午"; PeriodDayPart.NOON -> "中午"; PeriodDayPart.AFTERNOON -> "下午"; PeriodDayPart.EVENING -> "晚上" }
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(name, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (part == enabledParts.last()) {
                                Box(Modifier.fillMaxWidth().height(140.dp), contentAlignment = Alignment.Center) {
                                    Text("${value}节", style = pickerStyle, fontWeight = FontWeight.SemiBold)
                                }
                            } else {
                                val otherBeforeLast = enabledParts.dropLast(1).filterNot { it == part }.sumOf { other ->
                                    when (other) {
                                        PeriodDayPart.MORNING -> countPickerMorning
                                        PeriodDayPart.NOON -> countPickerNoon
                                        PeriodDayPart.AFTERNOON -> countPickerAfternoon
                                        PeriodDayPart.EVENING -> countPickerEvening
                                    }
                                }
                                val maxCount = (countPickerTotal - otherBeforeLast - 1).coerceAtLeast(1)
                                top.yukonga.miuix.kmp.basic.NumberPicker(
                                    value = value.coerceIn(1, maxCount),
                                    onValueChange = { changed ->
                                        when (part) {
                                            PeriodDayPart.MORNING -> countPickerMorning = changed
                                            PeriodDayPart.NOON -> countPickerNoon = changed
                                            PeriodDayPart.AFTERNOON -> countPickerAfternoon = changed
                                            PeriodDayPart.EVENING -> countPickerEvening = changed
                                        }
                                        val allocated = enabledParts.dropLast(1).sumOf { beforeLast ->
                                            when (beforeLast) {
                                                PeriodDayPart.MORNING -> countPickerMorning
                                                PeriodDayPart.NOON -> countPickerNoon
                                                PeriodDayPart.AFTERNOON -> countPickerAfternoon
                                                PeriodDayPart.EVENING -> countPickerEvening
                                            }
                                        }
                                        when (enabledParts.last()) {
                                            PeriodDayPart.MORNING -> countPickerMorning = (countPickerTotal - allocated).coerceAtLeast(1)
                                            PeriodDayPart.NOON -> countPickerNoon = (countPickerTotal - allocated).coerceAtLeast(1)
                                            PeriodDayPart.AFTERNOON -> countPickerAfternoon = (countPickerTotal - allocated).coerceAtLeast(1)
                                            PeriodDayPart.EVENING -> countPickerEvening = (countPickerTotal - allocated).coerceAtLeast(1)
                                        }
                                    },
                                    range = 1..maxCount,
                                    visibleItemCount = 3,
                                    label = { "${it}节" },
                                    textStyle = pickerStyle,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = SleepDownDesignTokens.QuickSheet.PickerContentSpacing - 14.dp),
                horizontalArrangement = Arrangement.spacedBy(SleepDownDesignTokens.Dialog.ActionSpacing)
            ) {
                QuickSheetLiquidAction("取消", true, popupBackdrop, state.config, modifier = Modifier.weight(1f), height = SleepDownDesignTokens.CenteredDialog.ActionHeight) {
                    showCountPicker = false
                }
                QuickSheetLiquidAction("确定", true, popupBackdrop, state.config, primary = true, modifier = Modifier.weight(1f), height = SleepDownDesignTokens.CenteredDialog.ActionHeight) {
                    changePartCounts(
                        if (PeriodDayPart.MORNING in enabledParts) countPickerMorning else 0,
                        if (PeriodDayPart.NOON in enabledParts) countPickerNoon else 0,
                        if (PeriodDayPart.AFTERNOON in enabledParts) countPickerAfternoon else 0,
                        if (PeriodDayPart.EVENING in enabledParts) countPickerEvening else 0
                    )
                    showCountPicker = false
                }
            }
        }
    }

    SleepDownPickerDialog(
        show = showSegmentStartPicker,
        title = "时段起点",
        onDismissRequest = { showSegmentStartPicker = false },
        backdrop = popupBackdrop,
        config = state.config,
        contentPadding = PaddingValues(SleepDownDesignTokens.QuickSheet.PickerContentPadding)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            fun formatStartMinute(value: Int) = "%02d:%02d".format(value / 60, value % 60)
            val requestedStarts = mapOf(
                PeriodDayPart.MORNING to morningStartMinute,
                PeriodDayPart.NOON to noonStartMinute,
                PeriodDayPart.AFTERNOON to afternoonStartMinute,
                PeriodDayPart.EVENING to eveningStartMinute
            )
            val constrainedStarts = constrainAutomaticPartStarts(config, active, requestedStarts)
            val partSpans = enabledParts.associateWith { automaticPartSpanMinutes(config, active, it) }
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val fontScale = LocalDensity.current.fontScale
                val columnWidth = maxWidth / enabledParts.size.coerceAtLeast(1)
                val pickerStyle = top.yukonga.miuix.kmp.theme.MiuixTheme.textStyles.title1.copy(
                    fontSize = when {
                        columnWidth < 100.dp || fontScale > 1.3f -> 16.sp
                        columnWidth < 140.dp || fontScale > 1.1f -> 19.sp
                        else -> 24.sp
                    }
                )
                Row(Modifier.fillMaxWidth()) {
                    enabledParts.forEach { part ->
                        val partPosition = enabledParts.indexOf(part)
                        val previousPart = enabledParts.getOrNull(partPosition - 1)
                        val minimumMinute = previousPart?.let {
                            constrainedStarts.getValue(it) + partSpans.getValue(it)
                        }?.coerceIn(0, LastMinuteOfDay) ?: 0
                        val remainingSpan = enabledParts.drop(partPosition).sumOf { partSpans.getValue(it) }
                        val maximumMinute = (LastMinuteOfDay - remainingSpan)
                            .coerceAtLeast(minimumMinute)
                            .coerceAtMost(LastMinuteOfDay)
                        val value = constrainedStarts.getValue(part).coerceIn(minimumMinute, maximumMinute)
                        val name = when (part) { PeriodDayPart.MORNING -> "上午"; PeriodDayPart.NOON -> "中午"; PeriodDayPart.AFTERNOON -> "下午"; PeriodDayPart.EVENING -> "晚上" }
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(name, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            top.yukonga.miuix.kmp.basic.NumberPicker(
                                value = value,
                                onValueChange = { changed ->
                                    when (part) {
                                        PeriodDayPart.MORNING -> morningStartMinute = changed
                                        PeriodDayPart.NOON -> noonStartMinute = changed
                                        PeriodDayPart.AFTERNOON -> afternoonStartMinute = changed
                                        PeriodDayPart.EVENING -> eveningStartMinute = changed
                                    }
                                },
                                range = minimumMinute..maximumMinute,
                                visibleItemCount = 3,
                                label = { minute -> "%02d:%02d".format(minute / 60, minute % 60) },
                                textStyle = pickerStyle,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = SleepDownDesignTokens.QuickSheet.PickerContentSpacing - 14.dp),
                horizontalArrangement = Arrangement.spacedBy(SleepDownDesignTokens.Dialog.ActionSpacing)
            ) {
                QuickSheetLiquidAction("取消", true, popupBackdrop, state.config, modifier = Modifier.weight(1f), height = SleepDownDesignTokens.CenteredDialog.ActionHeight) {
                    showSegmentStartPicker = false
                }
                QuickSheetLiquidAction("确定", true, popupBackdrop, state.config, primary = true, modifier = Modifier.weight(1f), height = SleepDownDesignTokens.CenteredDialog.ActionHeight) {
                    val candidate = normalizeAutoSchemeStarts(config, active.copy(
                        scheme = active.scheme.copy(
                            morningStartTime = formatStartMinute(constrainedStarts[PeriodDayPart.MORNING] ?: morningStartMinute),
                            noonStartTime = formatStartMinute(constrainedStarts[PeriodDayPart.NOON] ?: noonStartMinute),
                            afternoonStartTime = formatStartMinute(constrainedStarts[PeriodDayPart.AFTERNOON] ?: afternoonStartMinute),
                            eveningStartTime = formatStartMinute(constrainedStarts[PeriodDayPart.EVENING] ?: eveningStartMinute)
                        )
                    ))
                    val conflict = validateResolvedPeriodTimes(candidate.times)
                    if (conflict != null) timeConflictMessage = conflict else {
                        updateActive { candidate }
                        showSegmentStartPicker = false
                    }
                }
            }
        }
    }

    SleepDownPickerDialog(
        show = showTimePicker,
        title = if (editingPeriod > 0) "编辑第 $editingPeriod 节" else "设置时段起点",
        onDismissRequest = { showTimePicker = false },
        backdrop = popupBackdrop,
        config = state.config,
        contentPadding = PaddingValues(SleepDownDesignTokens.QuickSheet.PickerContentPadding)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val resolvedPickerTimes = resolveSchemeTimes(config, active).sortedBy { it.periodIndex }
            val editingPosition = resolvedPickerTimes.indexOfFirst { it.periodIndex == editingPeriod }
            val pickerBounds = periodTimePickerBounds(
                previousEnd = resolvedPickerTimes.getOrNull(editingPosition - 1)?.endTime,
                nextStart = resolvedPickerTimes.getOrNull(editingPosition + 1)?.startTime
            )
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val columnCount = if (editingPeriod > 0) 4 else 2
                val fontScale = LocalDensity.current.fontScale
                val columnWidth = maxWidth / columnCount
                val pickerStyle = top.yukonga.miuix.kmp.theme.MiuixTheme.textStyles.title1.copy(
                    fontSize = when {
                        columnWidth < 68.dp || fontScale > 1.3f -> 17.sp
                        columnWidth < 84.dp || fontScale > 1.12f -> 20.sp
                        else -> 25.sp
                    }
                )
                if (editingPeriod > 0) {
                    ConstrainedPeriodTimePickers(
                        startMinute = pickerStartHour * 60 + pickerStartMinute,
                        endMinute = pickerEndHour * 60 + pickerEndMinute,
                        bounds = pickerBounds,
                        onSelectionChange = { selection ->
                            pickerStartHour = selection.startMinute / 60
                            pickerStartMinute = selection.startMinute % 60
                            pickerEndHour = selection.endMinute / 60
                            pickerEndMinute = selection.endMinute % 60
                        },
                        textStyle = pickerStyle,
                        showSectionLabels = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Row(Modifier.fillMaxWidth()) {
                        top.yukonga.miuix.kmp.basic.NumberPicker(value = pickerStartHour, onValueChange = { pickerStartHour = it }, range = 0..23, visibleItemCount = 3, label = { "%02d时".format(it) }, textStyle = pickerStyle, modifier = Modifier.weight(1f))
                        top.yukonga.miuix.kmp.basic.NumberPicker(value = pickerStartMinute, onValueChange = { pickerStartMinute = it }, range = 0..59, wrapAround = true, visibleItemCount = 3, label = { "%02d分".format(it) }, textStyle = pickerStyle, modifier = Modifier.weight(1f))
                    }
                }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = SleepDownDesignTokens.QuickSheet.PickerContentSpacing - 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                QuickSheetLiquidAction("取消", true, popupBackdrop, state.config, modifier = Modifier.weight(1f), height = SleepDownDesignTokens.CenteredDialog.ActionHeight) { showTimePicker = false }
                if (editingPeriod > 0 && editingPeriod in active.overriddenPeriods) {
                    QuickSheetLiquidAction("恢复自动", true, popupBackdrop, state.config, modifier = Modifier.weight(1f), height = SleepDownDesignTokens.CenteredDialog.ActionHeight) {
                        updateActive { it.copy(overriddenPeriods = it.overriddenPeriods - editingPeriod).let { changed -> changed.copy(times = resolveSchemeTimes(config, changed)) } }
                        showTimePicker = false
                    }
                }
                if (editingPeriod > 0) {
                    QuickSheetLiquidAction("删除", true, popupBackdrop, state.config, destructive = true, modifier = Modifier.weight(1f), height = SleepDownDesignTokens.CenteredDialog.ActionHeight) {
                        deletePeriod(editingPeriod); showTimePicker = false
                    }
                }
                QuickSheetLiquidAction("确定", true, popupBackdrop, state.config, primary = true, modifier = Modifier.weight(1f), height = SleepDownDesignTokens.CenteredDialog.ActionHeight) {
                    if (editingPeriod > 0) {
                        val selection = constrainPeriodTimeSelection(
                            pickerStartHour * 60 + pickerStartMinute,
                            pickerEndHour * 60 + pickerEndMinute,
                            pickerBounds
                        )
                        val start = "%02d:%02d".format(selection.startMinute / 60, selection.startMinute % 60)
                        val end = "%02d:%02d".format(selection.endMinute / 60, selection.endMinute % 60)
                        val updated = active.times.filterNot { it.periodIndex == editingPeriod } +
                            PeriodSchemeTimeEntity(active.scheme.id, editingPeriod, start, end)
                        val candidate = active.copy(
                            times = updated.sortedBy { it.periodIndex },
                            overriddenPeriods = if (active.scheme.mode == PeriodSchemeMode.AUTO_MATCH) active.overriddenPeriods + editingPeriod else active.overriddenPeriods
                        )
                        val conflict = validateResolvedPeriodTimes(resolveSchemeTimes(config, candidate))
                        if (conflict != null) {
                            timeConflictMessage = conflict
                        } else {
                            updateActive { candidate }
                            showTimePicker = false
                        }
                    } else editingPart?.let { part ->
                        val start = "%02d:%02d".format(pickerStartHour, pickerStartMinute)
                        val scheme = when (part) {
                            PeriodDayPart.MORNING -> active.scheme.copy(morningStartTime = start)
                            PeriodDayPart.NOON -> active.scheme.copy(noonStartTime = start)
                            PeriodDayPart.AFTERNOON -> active.scheme.copy(afternoonStartTime = start)
                            PeriodDayPart.EVENING -> active.scheme.copy(eveningStartTime = start)
                        }
                        val candidate = active.copy(scheme = scheme).let { changed -> changed.copy(times = resolveSchemeTimes(config, changed)) }
                        val conflict = validateResolvedPeriodTimes(candidate.times)
                        if (conflict != null) {
                            timeConflictMessage = conflict
                        } else {
                            updateActive { candidate }
                            showTimePicker = false
                        }
                    }
                }
            }
        }
    }

    SleepDownPickerDialog(
        show = showBreakPicker,
        title = "特殊课间",
        onDismissRequest = { showBreakPicker = false },
        backdrop = popupBackdrop,
        config = state.config,
        contentPadding = PaddingValues(SleepDownDesignTokens.QuickSheet.PickerContentPadding)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val fontScale = LocalDensity.current.fontScale
                val columnWidth = maxWidth / 2
                val pickerStyle = top.yukonga.miuix.kmp.theme.MiuixTheme.textStyles.title1.copy(
                    fontSize = when {
                        columnWidth < 120.dp || fontScale > 1.3f -> 19.sp
                        columnWidth < 150.dp || fontScale > 1.12f -> 22.sp
                        else -> 27.sp
                    }
                )
                Row(Modifier.fillMaxWidth()) {
                    top.yukonga.miuix.kmp.basic.NumberPicker(
                        value = breakPickerPosition.coerceIn(0, specialBreakCandidates.lastIndex.coerceAtLeast(0)),
                        onValueChange = { position ->
                            breakPickerPosition = position
                            specialBreakCandidates.getOrNull(position)?.let { breakAfter = it }
                        },
                        range = 0..specialBreakCandidates.lastIndex.coerceAtLeast(0),
                        visibleItemCount = 3,
                        label = { position -> specialBreakCandidates.getOrNull(position)?.let { "第${it}节后" } ?: "无可用位置" },
                        textStyle = pickerStyle,
                        modifier = Modifier.weight(1f)
                    )
                    top.yukonga.miuix.kmp.basic.NumberPicker(value = breakMinutes, onValueChange = { breakMinutes = it }, range = 0..120, visibleItemCount = 3, label = { "${it}分钟" }, textStyle = pickerStyle, modifier = Modifier.weight(1f))
                }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = SleepDownDesignTokens.QuickSheet.PickerContentSpacing - 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                QuickSheetLiquidAction("删除", true, popupBackdrop, state.config, destructive = true, modifier = Modifier.weight(1f), height = SleepDownDesignTokens.CenteredDialog.ActionHeight) {
                    updateActive { it.copy(specialBreaks = it.specialBreaks - breakAfter).let { changed -> changed.copy(times = resolveSchemeTimes(config, changed)) } }
                    showBreakPicker = false
                }
                QuickSheetLiquidAction("确定", true, popupBackdrop, state.config, primary = true, modifier = Modifier.weight(1f), height = SleepDownDesignTokens.CenteredDialog.ActionHeight) {
                    updateActive { it.copy(specialBreaks = it.specialBreaks + (breakAfter to breakMinutes)).let { changed -> changed.copy(times = resolveSchemeTimes(config, changed)) } }
                    showBreakPicker = false
                }
            }
        }
    }

    pendingAutoSwitch?.let { candidate ->
        LiquidAlertDialog(
            title = "保留手动调整？",
            message = "检测到 ${pendingAutoOverrides.size} 个节次与自动匹配结果不同。你可以把它们保留为局部微调，之后自动匹配只重算其余节次；也可以按当前自动参数重新生成整套时间。",
            actions = listOf(
                LiquidAlertAction("取消切换", LiquidAlertActionStyle.Secondary) {
                    pendingAutoSwitch = null
                    pendingAutoOverrides = emptySet()
                },
                LiquidAlertAction("重新匹配", LiquidAlertActionStyle.Destructive) {
                    val rebuilt = candidate.copy(
                        overriddenPeriods = emptySet(),
                        times = resolveSchemeTimes(config, candidate.copy(overriddenPeriods = emptySet()))
                    )
                    updateActive { rebuilt }
                    pendingAutoSwitch = null
                    pendingAutoOverrides = emptySet()
                },
                LiquidAlertAction("保留为微调", LiquidAlertActionStyle.Primary) {
                    val preserved = candidate.copy(
                        times = active.times,
                        overriddenPeriods = pendingAutoOverrides
                    )
                    updateActive { preserved }
                    pendingAutoSwitch = null
                    pendingAutoOverrides = emptySet()
                }
            ),
            backdrop = popupBackdrop,
            config = state.config,
            onDismissRequest = {
                pendingAutoSwitch = null
                pendingAutoOverrides = emptySet()
            }
        )
    }

    timeConflictMessage?.let { conflict ->
        LiquidAlertDialog(
            title = "节次时间冲突",
            message = "$conflict。请调整当前节次或相邻节次后再确认。",
            actions = listOf(
                LiquidAlertAction("继续调整", LiquidAlertActionStyle.Primary) {
                    timeConflictMessage = null
                }
            ),
            backdrop = popupBackdrop,
            config = state.config,
            onDismissRequest = { timeConflictMessage = null }
        )
    }
}

