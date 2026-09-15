package com.xiaomanjun.sleepdownschedule.feature.settings

import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.shapes.Capsule
import com.kyant.shapes.RoundedRectangle
import com.xiaomanjun.sleepdownschedule.app.ui.settingsPageBackground
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.*
import com.xiaomanjun.sleepdownschedule.core.ui.settings.*
import com.xiaomanjun.sleepdownschedule.domain.schedule.*
import com.xiaomanjun.sleepdownschedule.model.*
import com.xiaomanjun.sleepdownschedule.feature.home.week.WeekResizeCornerShape
import com.xiaomanjun.sleepdownschedule.glass.GlassBackdropDomain
import com.xiaomanjun.sleepdownschedule.glass.glassBackdropProducer
import com.xiaomanjun.sleepdownschedule.glass.rememberGlassLayerBackdrop
import com.xiaomanjun.sleepdownschedule.glass.ui.*
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val TimelineCourseColor = Color(0xFF0A84FF)
private val TimelineBreakColor = Color(0xFFB078D8)
private val TimelineEnterEasing = CubicBezierEasing(0.28f, 0f, 0.2f, 1f)
private val TimelineExitEasing = CubicBezierEasing(0.4f, 0f, 0.7f, 1f)
private val TimelineInsertEasing = CubicBezierEasing(0.2f, 0f, 0.2f, 1f)
private val TimelineRemoveEasing = CubicBezierEasing(0.4f, 0f, 1f, 1f)
private val TimelineMinuteHeight = 4.dp
private val TimelineDragMinuteStep = 8.dp
private fun timelineSceneProgress(progress: Float, closing: Boolean): Float =
    if (closing) 1f - TimelineExitEasing.transform(1f - progress) else TimelineEnterEasing.transform(progress)

private fun timelineRowProgress(progress: Float, order: Int, closing: Boolean): Float {
    val delay = order.coerceAtMost(8) * 0.024f
    val elapsed = if (closing) 1f - progress else progress
    val local = ((elapsed - delay) / (1f - delay)).coerceIn(0f, 1f)
    return if (closing) 1f - TimelineExitEasing.transform(local) else TimelineEnterEasing.transform(local)
}
// The moving action needs its position before the underlay starts to sink.
private fun LayoutCoordinates.timelineBoundsInRoot() = Rect(
    localToRoot(Offset.Zero), Size(size.width.toFloat(), size.height.toFloat())
)
internal fun PeriodDayPart.timelineLabel() = when (this) {
    PeriodDayPart.MORNING -> "上午"
    PeriodDayPart.NOON -> "中午"
    PeriodDayPart.AFTERNOON -> "下午"
    PeriodDayPart.EVENING -> "晚上"
}

private data class TimelineBlock(
    val period: Int,
    val part: PeriodDayPart,
    val start: Int,
    val end: Int,
    val isBreak: Boolean = false,
    val beforeFirst: Boolean = false,
    val vacancyId: Int? = null
) {
    val key get() = when {
        vacancyId != null -> "vacancy-break-$vacancyId"
        beforeFirst -> "leading-$part"
        else -> "${if (isBreak) "break" else "lesson"}-$period"
    }
    val minutes get() = end - start
    val title get() = if (beforeFirst) "课前间隔" else if (isBreak) "课间" else "第 $period 节"
    val color get() = if (isBreak) TimelineBreakColor else TimelineCourseColor
}

private fun timelineBlocks(config: ScheduleConfigEntity, active: PeriodSchemeDraft,
    vacancies: List<PeriodTimelineVacancy> = emptyList(), includeLeading: Boolean = false): List<TimelineBlock> {
    val times = resolveSchemeTimes(config, active).sortedBy { it.periodIndex }
    return buildList {
        times.forEachIndexed { position, time ->
            val part = PeriodDayPart.entries.firstOrNull { time.periodIndex in config.periodRange(it) } ?: return@forEachIndexed
            val start = parseMinuteOfDay(time.startTime) ?: return@forEachIndexed
            val end = parseMinuteOfDay(time.endTime) ?: return@forEachIndexed
            if (includeLeading && time.periodIndex == config.periodRange(part).first) {
                val anchor = timelinePartAnchorMinute(config, active, part)
                if (start > anchor) add(TimelineBlock(time.periodIndex, part, anchor, start, true, beforeFirst = true))
            }
            add(TimelineBlock(time.periodIndex, part, start, end))
            val next = times.getOrNull(position + 1)
            if (next != null && next.periodIndex in config.periodRange(part)) {
                val nextStart = parseMinuteOfDay(next.startTime) ?: end
                if (nextStart > end) add(TimelineBlock(time.periodIndex, part, end, nextStart, true))
            }
            if (time.periodIndex == config.periodRange(part).last) {
                val vacancy = vacancies.filter { it.part == part && it.after == config.periodCount(part) }
                    .minByOrNull { parseMinuteOfDay(it.removedTimes.getValue(active.scheme.id).startTime) ?: LastMinuteOfDay }
                if (vacancy != null) {
                    val savedStart = requireNotNull(parseMinuteOfDay(vacancy.removedTimes.getValue(active.scheme.id).startTime))
                    val boundary = timelinePartBoundary(config, active, part)
                    if (end < boundary) add(TimelineBlock(time.periodIndex, part, end,
                        maxOf(end + 1, savedStart).coerceAtMost(boundary), true, vacancyId = vacancy.id))
                }
            }
        }
    }
}

private fun TimelineBlock.stableKey(lessonKeys: List<Int>): String = when {
    vacancyId != null || beforeFirst -> key
    else -> "${if (isBreak) "break" else "lesson"}-${lessonKeys[period - 1]}"
}

private fun resizeTimelineEntry(session: PeriodTimelineSession, block: TimelineBlock, minutes: Int): PeriodTimelineSession {
    if (block.vacancyId != null) return resizeTimelineVacancyBreak(session, block.vacancyId, minutes)
    return if (block.beforeFirst) shiftTimelineFirstLesson(
        session, block.part, block.start + minutes.coerceAtLeast(1)
    ) else resizeTimelineBlock(session, block.period, block.isBreak, minutes)
}

@Composable
internal fun PeriodSchemeEditor(
    state: AppState,
    backdrop: Backdrop?,
    config: ScheduleConfigEntity,
    draft: SchedulePeriodSchemesDraft,
    onDraftChange: (SchedulePeriodSchemesDraft) -> Unit,
    onCountsChange: (Int, Int, Int, Int) -> Unit,
    topPadding: androidx.compose.ui.unit.Dp,
    leadingContent: @Composable () -> Unit
) {
    val active = draft.schemes.firstOrNull { it.scheme.id == draft.activeSchemeId } ?: return
    val popupBackdrop = LocalSettingsPopupBackdrop.current ?: backdrop
    val chromeProgress = LocalSettingsEditorProgress.current
    var session by remember(config.id) { mutableStateOf<PeriodTimelineSession?>(null) }
    var showChoice by remember { mutableStateOf(false) }
    var showWizard by remember { mutableStateOf(false) }
    var showDeleteScheme by remember { mutableStateOf(false) }
    var deletingBlock by remember { mutableStateOf<TimelineBlock?>(null) }
    var pickingBlock by remember { mutableStateOf<TimelineBlock?>(null) }
    var pickingPart by remember { mutableStateOf<PeriodDayPart?>(null) }
    var requestedBlock by remember { mutableStateOf<TimelineBlock?>(null) }
    var editorLaidOut by remember { mutableStateOf(false) }
    var dragBase by remember { mutableStateOf<PeriodTimelineSession?>(null) }
    var localError by remember { mutableStateOf<String?>(null) }
    var closing by remember { mutableStateOf(false) }
    var lessonKeys by remember { mutableStateOf(emptyList<Int>()) }
    var nextLessonKey by remember { mutableIntStateOf(0) }
    var changingKeys by remember { mutableStateOf(emptySet<String>()) }
    var changingStructure by remember { mutableStateOf(false) }
    val cardMotion = remember { Animatable(1f) }
    val motion = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val normalScroll = rememberScrollState()
    var actionSource by remember { mutableStateOf(Rect.Zero) }
    var frozenActionSource by remember { mutableStateOf(Rect.Zero) }
    val density = LocalDensity.current
    val sunkenBlur = remember(density) { platformMotionBlurRenderEffect(with(density) { 10.dp.toPx() }) }
    val headerTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 8.dp
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val summary = remember(config, active) { timelineBlocks(config, active) }
    val pageColor = settingsPageBackground(state.config)
    val editorBackdrop = rememberGlassLayerBackdrop(GlassBackdropDomain.Content, "period-editor-content") {
        drawRect(pageColor)
        drawContent()
    }
    SideEffect { chromeProgress?.floatValue = timelineSceneProgress(motion.value, closing) }
    DisposableEffect(chromeProgress) { onDispose { chromeProgress?.floatValue = 0f } }

    fun enter(value: PeriodTimelineSession) {
        frozenActionSource = actionSource
        localError = null
        editorLaidOut = false
        lessonKeys = (1..value.config.totalPeriodCount()).toList()
        nextLessonKey = value.config.totalPeriodCount() + 1
        session = value
    }
    fun addPeriod(value: PeriodTimelineSession?) {
        if (changingStructure) return
        if (value == null) { localError = "当前时段没有足够空间添加节次，请先调整时间。"; return }
        val current = session ?: return
        val after = (value.draft.topologyOperations.last() as PeriodTopologyOperation.AddAfter).periodIndex
        val oldKeys = timelineBlocks(current.config, current.active, current.vacancies, true).map { it.stableKey(lessonKeys) }.toSet()
        val updatedKeys = lessonKeys.toMutableList().apply { add(after, nextLessonKey++) }
        changingStructure = true
        scope.launch {
            cardMotion.snapTo(0f)
            changingKeys = timelineBlocks(value.config, value.active, value.vacancies, true)
                .map { it.stableKey(updatedKeys) }.toSet() - oldKeys
            lessonKeys = updatedKeys
            session = value
            localError = null
            withFrameNanos { }
            cardMotion.animateTo(1f, tween(200, easing = TimelineInsertEasing))
            changingKeys = emptySet()
            changingStructure = false
        }
    }
    fun leave(commit: Boolean) {
        if (closing || changingStructure || motion.value < 1f) return
        val current = session ?: return
        if (commit) {
            val error = current.draft.schemes.firstNotNullOfOrNull {
                validateResolvedPeriodTimes(resolveSchemeTimes(current.config, it))
            }
            if (error != null) { localError = error; return }
        }
        closing = true
        scope.launch {
            motion.animateTo(0f, tween(260, easing = LinearEasing))
            if (commit) {
                onCountsChange(current.config.morningPeriodCount, current.config.noonPeriodCount,
                    current.config.afternoonPeriodCount, current.config.eveningPeriodCount)
                onDraftChange(current.draft)
            }
            session = null
            editorLaidOut = false
            closing = false
            localError = null
        }
    }
    LaunchedEffect(session != null, editorLaidOut) {
        if (session != null && editorLaidOut) {
            withFrameNanos { }
            motion.animateTo(1f, tween(360, easing = LinearEasing))
        }
    }
    BackHandler(enabled = session != null) { leave(false) }
    LaunchedEffect(motion.value == 1f, requestedBlock) {
        if (motion.value == 1f && requestedBlock != null) {
            pickingBlock = requestedBlock
            requestedBlock = null
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize()
                .graphicsLayer {
                    val p = timelineSceneProgress(motion.value, closing)
                    alpha = 1f - p
                    translationY = 28.dp.toPx() * p
                    scaleX = 1f - 0.04f * p
                    scaleY = scaleX
                    renderEffect = if (motion.value > 0f && motion.value < 1f) sunkenBlur else null
                }
                .then(if (session != null) Modifier.clearAndSetSemantics { } else Modifier)
                .verticalScroll(normalScroll, enabled = session == null)
                .padding(start = 16.dp, end = 16.dp, top = topPadding + 12.dp, bottom = navBottom + 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            leadingContent()
            Text("作息安排", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 4.dp, top = 6.dp))
            SettingsGroup(backdrop, state.config, Modifier.fillMaxWidth()) {
                SleepDownLiquidDropdownPreference(
                    items = draft.schemes.map { it.scheme.name },
                    selectedIndex = draft.schemes.indexOf(active).coerceAtLeast(0),
                    title = "当前作息", backdrop = backdrop, config = state.config,
                    insideMargin = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                    maxHeight = 318.dp, onExpandedChange = {},
                    onSelectedIndexChange = { index ->
                        draft.schemes.getOrNull(index)?.let { onDraftChange(draft.copy(activeSchemeId = it.scheme.id)) }
                    }
                )
                SettingsDivider()
                SettingsTextFieldRow("作息名称", active.scheme.name, { name ->
                    onDraftChange(draft.copy(schemes = draft.schemes.map {
                        if (it == active) it.copy(scheme = it.scheme.copy(name = name)) else it
                    }))
                })
                if (draft.schemes.size > 1) Row(Modifier.fillMaxWidth().padding(14.dp)) {
                    DialogLiquidButton(backdrop, "删除作息", { showDeleteScheme = true }, monochromeNeutral = true)
                }
            }
            Row(Modifier.fillMaxWidth().padding(start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("详细节次", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                DialogLiquidButton(backdrop, "编辑", { showChoice = true }, role = DialogButtonRole.Confirm,
                    modifier = Modifier.onGloballyPositioned { if (session == null) actionSource = it.timelineBoundsInRoot() }
                        .graphicsLayer { alpha = if (editorLaidOut) 0f else 1f })
            }
            SettingsGroup(backdrop, state.config, Modifier.fillMaxWidth()) {
                val lessons = summary.filterNot { it.isBreak }
                lessons.forEachIndexed { position, block ->
                    key(block.key) {
                        if (position > 0) {
                            val previous = lessons[position - 1]
                            val gap = summary.firstOrNull { it.isBreak && it.period == previous.period }
                            Box(Modifier.fillMaxWidth()) {
                                when {
                                    previous.part != block.part -> PeriodTimelineSeparator("${block.part.timelineLabel()}时段", TimelineCourseColor)
                                    gap != null && gap.minutes != active.scheme.breakDurationMinutes -> PeriodTimelineSeparator("课间 · ${gap.minutes}分钟", TimelineBreakColor)
                                    else -> SettingsDivider()
                                }
                            }
                        }
                        SettingsPickerValueRow(block.title, "${timelineMinuteText(block.start)} - ${timelineMinuteText(block.end)}",
                            onClick = {
                                enter(PeriodTimelineSession(config, draft).updateActive(active.materializeForTimeline(config)))
                                requestedBlock = block
                            })
                    }
                }
            }
        }
        session?.let { edit ->
            // Keep the sinking underlay from receiving editor touches.
            Box(Modifier.fillMaxSize().clickable(interactionSource = null, indication = null) {})
            val blocks = remember(edit) { timelineBlocks(edit.config, edit.active, edit.vacancies, includeLeading = true) }
            val editScroll = rememberScrollState()
            var viewportHeight by remember { mutableIntStateOf(0) }
            var retainedScroll by remember { mutableIntStateOf(0) }
            val interactive = motion.value == 1f && !closing && !changingStructure
            LaunchedEffect(editScroll) {
                snapshotFlow { editScroll.isScrollInProgress to editScroll.value }.collect { (scrolling, offset) ->
                    if (scrolling && dragBase == null) retainedScroll = offset
                }
            }
            fun resize(block: TimelineBlock, minutes: Int): Int {
                val current = session ?: return block.minutes
                val updated = resizeTimelineEntry(dragBase ?: current, block, minutes)
                session = updated
                return timelineBlocks(updated.config, updated.active, updated.vacancies, includeLeading = true)
                    .firstOrNull { it.key == block.key }?.minutes ?: block.minutes
            }
            Column(Modifier.fillMaxSize().glassBackdropProducer(editorBackdrop)
                .onGloballyPositioned { editorLaidOut = true }
                .onSizeChanged { viewportHeight = it.height }
                .verticalScroll(editScroll, enabled = interactive && dragBase == null)
                // Keep the current offset valid even when a card near the bottom becomes shorter.
                .heightIn(min = with(density) { (retainedScroll + viewportHeight).toDp() })
                .padding(start = 16.dp, end = 16.dp, top = headerTop + 64.dp, bottom = navBottom + 40.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("拖动右下角调整时长 · 每格 1 分钟\n点按卡片选择时间", fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.graphicsLayer { alpha = timelineSceneProgress(motion.value, closing) })
                var order = 0
                PeriodDayPart.entries.forEach { part ->
                    val partBlocks = blocks.filter { it.part == part }
                    val vacancies = edit.vacancies.filter { it.part == part }
                    if (partBlocks.isNotEmpty() || vacancies.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            TimelinePartDivider(part, timelinePartAnchorMinute(edit.config, edit.active, part),
                                Modifier.graphicsLayer { alpha = timelineSceneProgress(motion.value, closing) },
                                enabled = interactive && edit.config.periodCount(part) > 0) { pickingPart = part }
                            Box(Modifier.fillMaxWidth()) {
                                Column(Modifier.fillMaxWidth().padding(end = 38.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    partBlocks.forEach { block ->
                                        val index = order++
                                        val stableKey = block.stableKey(lessonKeys)
                                        key(stableKey) {
                                            TimelineEditCard(block, index, { motion.value }, closing,
                                                sizeProgress = { if (stableKey in changingKeys) cardMotion.value else 1f },
                                                ready = editorLaidOut, enabled = interactive,
                                                onPick = { pickingBlock = block },
                                                onDelete = { deletingBlock = block },
                                                onResizeStarted = {
                                                    retainedScroll = editScroll.value
                                                    dragBase = session
                                                },
                                                onResizeFinished = { dragBase = null },
                                                onResize = { resize(block, it) })
                                            if (block.isBreak) {
                                                val after = when {
                                                    block.beforeFirst -> 0
                                                    block.vacancyId != null -> edit.config.periodCount(part)
                                                    else -> block.period - edit.config.periodRange(part).first + 1
                                                }
                                                vacancies.filter { it.after == after }.forEach { vacancy ->
                                                    TimelineAddPeriodButton(backdrop, interactive, Modifier.padding(top = 10.dp)) {
                                                        addPeriod(session?.let { insertTimelinePeriod(it, vacancy.id) })
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    val visibleInsertionPoints = partBlocks.filter { it.isBreak }.map {
                                        when {
                                            it.beforeFirst -> 0
                                            it.vacancyId != null -> edit.config.periodCount(part)
                                            else -> it.period - edit.config.periodRange(part).first + 1
                                        }
                                    }.toSet()
                                    vacancies.filter { it.after !in visibleInsertionPoints }.forEach { vacancy ->
                                        TimelineAddPeriodButton(backdrop, interactive) {
                                            addPeriod(session?.let { insertTimelinePeriod(it, vacancy.id) })
                                        }
                                    }
                                    val canAppend = remember(edit, part) { appendTimelinePeriod(edit, part) != null }
                                    if (canAppend && vacancies.none { it.after == edit.config.periodCount(part) }) {
                                        TimelineAddPeriodButton(backdrop, interactive,
                                            Modifier.graphicsLayer { alpha = timelineSceneProgress(motion.value, closing) }) {
                                            addPeriod(session?.let { appendTimelinePeriod(it, part) })
                                        }
                                    }
                                }
                                TimelineRatioRail(partBlocks, part,
                                    Modifier.matchParentSize().padding(start = 0.dp)
                                        .graphicsLayer { alpha = timelineSceneProgress(motion.value, closing) })
                            }
                        }
                    }
                }
                localError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
            val blurTint = if (glassUsesLightStyle(state.config)) Color.White else Color(0xFF111111)
            ProgressiveBackdropBlur(editorBackdrop, Modifier.align(Alignment.TopCenter).graphicsLayer { alpha = motion.value },
                tintColor = blurTint, height = headerTop + 76.dp, blurRadius = 12.dp,
                fallbackTintStops = listOf(0f to blurTint.copy(alpha = 0.42f), 0.68f to blurTint.copy(alpha = 0.18f), 1f to Color.Transparent))
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = headerTop),
                verticalAlignment = Alignment.CenterVertically) {
                DialogLiquidButton(editorBackdrop, "取消", { leave(false) }, monochromeNeutral = true,
                    modifier = Modifier.graphicsLayer {
                        alpha = timelineSceneProgress(motion.value, closing)
                        translationX = -32.dp.toPx() * (1f - timelineSceneProgress(motion.value, closing))
                    })
                Text("编辑作息", modifier = Modifier.weight(1f).graphicsLayer { alpha = motion.value },
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center, fontWeight = FontWeight.SemiBold)
                var actionDestination by remember { mutableStateOf(Rect.Zero) }
                Box(Modifier.onGloballyPositioned { actionDestination = it.timelineBoundsInRoot() }) {
                    DialogLiquidButton(editorBackdrop, if (timelineSceneProgress(motion.value, closing) < 0.5f) "编辑" else "完成", { leave(true) }, role = DialogButtonRole.Confirm,
                        modifier = Modifier.graphicsLayer {
                            val p = timelineSceneProgress(motion.value, closing)
                            alpha = if (editorLaidOut && actionDestination != Rect.Zero) 1f else 0f
                            if (frozenActionSource != Rect.Zero && actionDestination != Rect.Zero) {
                                translationX = (frozenActionSource.left - actionDestination.left) * (1f - p)
                                translationY = (frozenActionSource.top - actionDestination.top) * (1f - p)
                            }
                        })
                }
            }
        }
    }
    if (showChoice) LiquidAlertDialog("编辑作息", "要新建一个作息，还是在当前作息调整？",
        listOf(
            LiquidAlertAction("调整当前作息", LiquidAlertActionStyle.Primary, onClick = {
                showChoice = false
                enter(PeriodTimelineSession(config, draft).updateActive(active.materializeForTimeline(config)))
            }),
            LiquidAlertAction("新建作息", LiquidAlertActionStyle.Secondary, onClick = { showChoice = false; showWizard = true })
        ), popupBackdrop, state.config, { showChoice = false })
    if (showWizard) PeriodSchemeCreationWizard(config, draft, popupBackdrop, state.config,
        onDismiss = { showWizard = false }, onCreated = { showWizard = false; enter(it) })
    if (showDeleteScheme) LiquidAlertDialog("删除作息", "删除“${active.scheme.name}”？其他作息会保留。",
        listOf(LiquidAlertAction("取消", LiquidAlertActionStyle.Secondary, onClick = { showDeleteScheme = false }),
            LiquidAlertAction("删除", LiquidAlertActionStyle.Destructive, onClick = {
                val remaining = draft.schemes.filterNot { it.scheme.id == active.scheme.id }
                if (remaining.isNotEmpty()) onDraftChange(draft.copy(schemes = remaining, activeSchemeId = remaining.first().scheme.id))
                showDeleteScheme = false
            })), popupBackdrop, state.config, { showDeleteScheme = false })
    deletingBlock?.let { block ->
        LiquidAlertDialog("移除${block.title}", "节次编号将连续调整，其他作息也会同步减少这一节。删除后可从课间下方添加节次，课程对应关系会在保存详细设置时确认。",
            listOf(LiquidAlertAction("取消", LiquidAlertActionStyle.Secondary, onClick = { deletingBlock = null }),
                LiquidAlertAction("移除", LiquidAlertActionStyle.Destructive, onClick = {
                    session?.let { edit ->
                        val result = deleteTimelinePeriod(edit, block.period)
                        if (result == null) localError = "至少需要保留一个节次" else if (!changingStructure) {
                            changingStructure = true
                            changingKeys = setOf(block.stableKey(lessonKeys))
                            scope.launch {
                                cardMotion.animateTo(0f, tween(160, easing = TimelineRemoveEasing))
                                lessonKeys = lessonKeys.filterIndexed { index, _ -> index != block.period - 1 }
                                session = result
                                changingKeys = emptySet()
                                cardMotion.snapTo(1f)
                                changingStructure = false
                            }
                        }
                    }
                    deletingBlock = null
                })), popupBackdrop, state.config, { deletingBlock = null })
    }
    pickingBlock?.let { block ->
        val edit = session
        if (edit != null) TimelineBlockPicker(block, edit, popupBackdrop, state.config,
            onDismiss = { pickingBlock = null }, onChange = { session = it; pickingBlock = null })
    }
    pickingPart?.let { part ->
        session?.let { edit -> TimelinePartStartPicker(part, edit, popupBackdrop, state.config,
            onDismiss = { pickingPart = null }, onChange = { session = it; pickingPart = null }) }
    }
}

@Composable
private fun TimelineEditCard(
    block: TimelineBlock, order: Int, progress: () -> Float, closing: Boolean,
    ready: Boolean, enabled: Boolean, sizeProgress: () -> Float,
    onPick: () -> Unit, onDelete: () -> Unit, onResizeStarted: () -> Unit, onResizeFinished: () -> Unit,
    onResize: (Int) -> Int
) {
    val view = LocalView.current
    val density = LocalDensity.current
    val stepPx = with(density) { TimelineDragMinuteStep.toPx() }
    val latestResize by rememberUpdatedState(onResize)
    val latestMinutes by rememberUpdatedState(block.minutes)
    val latestStart by rememberUpdatedState(onResizeStarted)
    val latestFinish by rememberUpdatedState(onResizeFinished)
    var dragging by remember { mutableStateOf(false) }
    DisposableEffect(Unit) { onDispose { if (dragging) latestFinish() } }
    val height = (TimelineMinuteHeight * block.minutes).coerceAtLeast(72.dp * density.fontScale.coerceAtLeast(1f))
    BoxWithConstraints(Modifier.fillMaxWidth().height(height).graphicsLayer {
        val p = if (ready) timelineRowProgress(progress(), order, closing) else 0f
        alpha = p * sizeProgress()
        scaleX = sizeProgress()
        scaleY = scaleX
        translationY = 48.dp.toPx() * (1f - p)
    }) {
        val cardWidth = maxWidth
        Box(Modifier.fillMaxSize().clip(RoundedRectangle(24.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .background(block.color.copy(alpha = if (dragging) 0.18f else 0.08f))
            .clickable(enabled = enabled, onClickLabel = "选择${block.title}时间", onClick = onPick)) {
            Row(Modifier.fillMaxSize().padding(start = 22.dp, end = 24.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(block.title, fontWeight = if (block.isBreak) FontWeight.Bold else FontWeight.SemiBold,
                        fontSize = if (block.isBreak) 16.sp else 18.sp)
                    Text("${timelineMinuteText(block.start)} – ${timelineMinuteText(block.end)}", fontSize = if (block.isBreak) 12.sp else 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("${block.minutes}分钟", color = block.color,
                    fontSize = if (cardWidth < 300.dp) 24.sp else 28.sp,
                    fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
        }
        if (!block.isBreak) Box(
            Modifier.align(Alignment.TopStart).offset(x = (-5).dp, y = (-5).dp).size(48.dp)
                .semantics { contentDescription = "移除${block.title}" }
                .clickable(enabled = enabled, interactionSource = null, indication = null, onClick = onDelete)
        ) {
            Box(Modifier.size(22.dp).clip(Capsule()).background(Color(0xFFE94955)), contentAlignment = Alignment.Center) {
                Box(Modifier.width(10.dp).height(2.dp).clip(Capsule()).background(Color.White))
            }
        }
        val cornerShape = remember(cardWidth, height) { WeekResizeCornerShape(DpSize(cardWidth, height), 24.dp, 34.dp) }
        Box(Modifier.align(Alignment.BottomEnd).offset(x = 4.dp, y = 4.dp).size(48.dp)
            .semantics {
                contentDescription = "调整${block.title}时长"
                stateDescription = "${block.minutes} 分钟"
                customActions = listOf(
                    CustomAccessibilityAction("增加 1 分钟") { latestResize(latestMinutes + 1); true },
                    CustomAccessibilityAction("减少 1 分钟") { latestResize(latestMinutes - 1); true }
                )
            }
            .pointerInput(enabled, block.key, stepPx) {
                if (!enabled) return@pointerInput
                var distance = 0f
                var initial = 0
                var lastRequested = 0
                var lastApplied = 0
                detectVerticalDragGestures(
                    onDragStart = { distance = 0f; initial = latestMinutes; lastRequested = initial; lastApplied = initial; dragging = true; latestStart() },
                    onDragEnd = { dragging = false; latestFinish() }, onDragCancel = { dragging = false; latestFinish() }
                ) { change, amount ->
                    change.consume()
                    distance += amount
                    val requested = initial + (distance / stepPx).roundToInt()
                    if (requested != lastRequested) {
                        lastRequested = requested
                        val applied = latestResize(requested)
                        if (applied != lastApplied) {
                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            lastApplied = applied
                        }
                    }
                }
            }, contentAlignment = Alignment.BottomEnd) {
            Box(Modifier.size(34.dp).background(block.color.copy(alpha = if (dragging) 0.95f else 0.65f), cornerShape))
        }
    }
}

@Composable
private fun TimelineAddPeriodButton(backdrop: Backdrop?, enabled: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        DialogLiquidButton(backdrop, "添加节次", { if (enabled) onClick() }, role = DialogButtonRole.Confirm)
    }
}

@Composable
private fun PeriodTimelineSeparator(label: String, tint: Color) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        Box(Modifier.weight(1f).height(1.dp).background(tint.copy(alpha = 0.28f)))
        Text(label, Modifier.clip(Capsule()).background(tint.copy(alpha = 0.18f)).padding(horizontal = 11.dp, vertical = 5.dp),
            color = tint, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
        Box(Modifier.weight(1f).height(1.dp).background(tint.copy(alpha = 0.28f)))
    }
}

@Composable
private fun TimelinePartDivider(
    part: PeriodDayPart, start: Int, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit
) {
    val color = MaterialTheme.colorScheme.onSurface
    SleepDownTimeSectionDivider(color, modifier, label = {
        Text(part.timelineLabel(), style = MaterialTheme.typography.titleSmall, color = color, fontWeight = FontWeight.SemiBold)
    }, time = {
        Box(Modifier.heightIn(min = 48.dp).clickable(enabled = enabled, onClickLabel = "调整${part.timelineLabel()}起点", onClick = onClick),
            contentAlignment = Alignment.Center) {
            Text(timelineMinuteText(start), style = MaterialTheme.typography.labelMedium, color = TimelineCourseColor)
        }
    })
}

@Composable
private fun TimelinePartStartPicker(
    part: PeriodDayPart, session: PeriodTimelineSession, backdrop: Backdrop?, visualConfig: ScheduleConfigEntity,
    onDismiss: () -> Unit, onChange: (PeriodTimelineSession) -> Unit
) {
    val bounds = timelinePartStartBounds(session.config, session.active, part) ?: return
    val original = timelinePartAnchorMinute(session.config, session.active, part)
    var selected by remember { mutableIntStateOf(original.coerceIn(bounds)) }
    var visible by remember { mutableStateOf(true) }
    var pending by remember { mutableStateOf<PeriodTimelineSession?>(null) }
    SleepDownPickerDialog(visible, "${part.timelineLabel()}分段起点", { visible = false }, backdrop, visualConfig,
        onDismissFinished = { pending?.let(onChange) ?: onDismiss() },
        contentPadding = PaddingValues(SleepDownDesignTokens.QuickSheet.PickerContentPadding)) {
        Column(verticalArrangement = Arrangement.spacedBy(SleepDownDesignTokens.QuickSheet.PickerContentSpacing)) {
            SettingsTimePickerContent(selected, bounds) { selected = it }
            Text("整段课程一起平移；若碰到下一分段，只压缩本段最后一节课，最短保留到相邻课间的时长。", fontSize = 12.sp,
                color = androidx.compose.material3.LocalContentColor.current.copy(alpha = 0.66f))
            PeriodPickerActions(backdrop, visualConfig, onCancel = { visible = false }, onConfirm = {
                    pending = shiftTimelinePart(session, part, selected)
                    visible = false
            })
        }
    }
}

@Composable
private fun TimelineRatioRail(blocks: List<TimelineBlock>, part: PeriodDayPart, modifier: Modifier) {
    val course = blocks.filterNot { it.isBreak }.sumOf { it.minutes }
    val breaks = blocks.filter { it.isBreak }.sumOf { it.minutes }
    Box(modifier.semantics { contentDescription = "${part.timelineLabel()}，课程 $course 分钟，课间 $breaks 分钟" }) {
        Column(Modifier.align(Alignment.CenterEnd).width(14.dp).fillMaxHeight().clip(Capsule())) {
            blocks.filter { it.minutes > 0 }.forEach { block ->
                Box(Modifier.fillMaxWidth().weight(block.minutes.toFloat()).background(block.color))
            }
        }
    }
}

private enum class TimelinePickerPage { DETAILS, START, DURATION }

@Composable
private fun TimelineBlockPicker(
    block: TimelineBlock, session: PeriodTimelineSession, backdrop: Backdrop?, visualConfig: ScheduleConfigEntity,
    onDismiss: () -> Unit, onChange: (PeriodTimelineSession) -> Unit
) {
    var candidate by remember { mutableStateOf(session) }
    var page by remember { mutableStateOf(if (block.isBreak) TimelinePickerPage.DURATION else TimelinePickerPage.DETAILS) }
    var selected by remember { mutableIntStateOf(block.minutes) }
    var visible by remember { mutableStateOf(true) }
    var pending by remember { mutableStateOf<PeriodTimelineSession?>(null) }
    val current = timelineBlocks(candidate.config, candidate.active, candidate.vacancies, includeLeading = true)
        .firstOrNull { it.key == block.key } ?: block
    val firstLesson = !block.isBreak && block.period == candidate.config.periodRange(block.part).first
    val title = when (page) {
        TimelinePickerPage.DETAILS -> "编辑${block.title}"
        TimelinePickerPage.START -> "开始时间"
        TimelinePickerPage.DURATION -> if (block.isBreak) "${block.title}时长" else "课时时长"
    }
    SleepDownPickerDialog(visible, title,
        onDismissRequest = {
            if (page == TimelinePickerPage.DETAILS || block.isBreak) visible = false else page = TimelinePickerPage.DETAILS
        }, backdrop = backdrop, config = visualConfig,
        onDismissFinished = { pending?.let(onChange) ?: onDismiss() },
        contentPadding = PaddingValues(SleepDownDesignTokens.QuickSheet.PickerContentPadding),
        contentTransitionKey = page,
        contentForState = { displayed ->
            val shown = displayed as TimelinePickerPage
            Column(verticalArrangement = Arrangement.spacedBy(SleepDownDesignTokens.QuickSheet.PickerContentSpacing)) {
                when (shown) {
                    TimelinePickerPage.DETAILS -> Column {
                        SettingsPickerValueRow("开始时间", timelineMinuteText(current.start), enabled = firstLesson, onClick = {
                            selected = current.start; page = TimelinePickerPage.START
                        })
                        SettingsPickerValueRow("课时时长", "${current.minutes} 分钟", onClick = {
                            selected = current.minutes; page = TimelinePickerPage.DURATION
                        })
                        Text("结束于 ${timelineMinuteText(current.end)} · 后续课程在本时段内顺延",
                            color = androidx.compose.material3.LocalContentColor.current.copy(alpha = 0.66f), fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
                    }
                    TimelinePickerPage.START -> {
                        val bounds = timelineFirstLessonStartBounds(candidate.config, candidate.active, block.part)
                            ?: current.start..current.start
                        SettingsTimePickerContent(selected, bounds) { selected = it }
                        Text("不能早于分段起点 ${timelineMinuteText(bounds.first)}，后续课程一起顺延。",
                            fontSize = 12.sp, color = androidx.compose.material3.LocalContentColor.current.copy(alpha = 0.66f))
                    }
                    TimelinePickerPage.DURATION -> {
                        val maximumSession = resizeTimelineEntry(candidate, current, LastMinuteOfDay)
                        val maximum = timelineBlocks(maximumSession.config, maximumSession.active, maximumSession.vacancies, includeLeading = true)
                            .firstOrNull { it.key == block.key }?.minutes ?: current.minutes
                        val minimum = if (block.isBreak) 1 else minimumTimelineLessonMinutes(candidate.config, candidate.active, block.period)
                        SettingsMinutePickerContent(selected, { selected = it }, minimum..maxOf(minimum, maximum))
                    }
                }
                PeriodPickerActions(backdrop, visualConfig, onCancel = {
                        if (shown == TimelinePickerPage.DETAILS || block.isBreak) visible = false else page = TimelinePickerPage.DETAILS
                    }, onConfirm = {
                        when (shown) {
                            TimelinePickerPage.DETAILS -> { pending = candidate; visible = false }
                            TimelinePickerPage.START -> {
                                candidate = shiftTimelineFirstLesson(candidate, block.part, selected)
                                page = TimelinePickerPage.DETAILS
                            }
                            TimelinePickerPage.DURATION -> {
                                candidate = resizeTimelineEntry(candidate, current, selected)
                                if (block.isBreak) { pending = candidate; visible = false } else page = TimelinePickerPage.DETAILS
                            }
                        }
                    })
            }
        }
    ) {}
}
