package com.xiaomanjun.sleepdownschedule.feature.settings

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import com.itos.coui.scroll.couiVerticalScroll
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.itos.coui.menu.rememberCouiWindowBackdrop
import com.itos.coui.sheet.CouiSheetDefaults
import com.itos.coui.sheet.ModalBottomSheet
import com.itos.coui.theme.LocalCouiMaterialEnabled
import com.kyant.backdrop.Backdrop
import com.kyant.shapes.Capsule
import com.kyant.shapes.RoundedRectangle
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.*
import com.xiaomanjun.sleepdownschedule.core.ui.scaffold.settingsPageBackground
import com.xiaomanjun.sleepdownschedule.core.ui.settings.LocalCouiLiquidMaterial
import com.xiaomanjun.sleepdownschedule.core.ui.settings.LocalSettingsPopupBackdrop
import com.xiaomanjun.sleepdownschedule.core.ui.settings.SettingsEditorSheetToolbar
import com.xiaomanjun.sleepdownschedule.core.ui.settings.SettingsMinutePickerContent
import com.xiaomanjun.sleepdownschedule.core.ui.settings.SettingsPickerValueRow
import com.xiaomanjun.sleepdownschedule.core.ui.settings.SettingsTextFieldRow
import com.xiaomanjun.sleepdownschedule.core.ui.settings.SettingsTimePickerContent
import com.xiaomanjun.sleepdownschedule.core.ui.theme.appUsesDarkTheme
import com.xiaomanjun.sleepdownschedule.domain.schedule.*
import com.xiaomanjun.sleepdownschedule.feature.home.week.WeekResizeCornerShape
import com.xiaomanjun.sleepdownschedule.glass.GlassBackdropDomain
import com.xiaomanjun.sleepdownschedule.glass.glassBackdropProducer
import com.xiaomanjun.sleepdownschedule.glass.rememberGlassLayerBackdrop
import com.xiaomanjun.sleepdownschedule.glass.ui.*
import com.xiaomanjun.sleepdownschedule.model.*
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private val TimelineCourseColor = Color(0xFF0A84FF)
private val TimelineBreakColor = Color(0xFFB078D8)
private val TimelineInsertEasing = CubicBezierEasing(0.2f, 0f, 0.2f, 1f)
private val TimelineRemoveEasing = CubicBezierEasing(0.4f, 0f, 1f, 1f)
private val TimelineMinuteHeight = 4.dp
private val TimelineDragMinuteStep = 8.dp
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
    val beforeFirst: Boolean = false
) {
    val key get() = when {
        beforeFirst -> "leading-$part"
        else -> "${if (isBreak) "break" else "lesson"}-$period"
    }
    val minutes get() = end - start
    val title get() = if (beforeFirst) "课前间隔" else if (isBreak) "课间" else "第 $period 节"
    val color get() = if (isBreak) TimelineBreakColor else TimelineCourseColor
}

/** Stored bells are authoritative for both the summary and the editor's entry points. */
internal fun periodSchemeTimelineTimes(config: ScheduleConfigEntity, active: PeriodSchemeDraft): List<PeriodSchemeTimeEntity> =
    if (active.times.isNotEmpty()) active.times.sortedBy { it.periodIndex }
    else resolveSchemeTimes(config, active).sortedBy { it.periodIndex }

private fun timelineBlocks(config: ScheduleConfigEntity, active: PeriodSchemeDraft,
    includeLeading: Boolean = false): List<TimelineBlock> {
    val times = periodSchemeTimelineTimes(config, active)
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
                // Keep a zero-minute break in the timeline so two back-to-back lessons stay editable.
                val nextStart = parseMinuteOfDay(next.startTime)
                if (nextStart != null && nextStart >= end) add(TimelineBlock(time.periodIndex, part, end, nextStart, true))
            }
        }
    }
}

private fun TimelineBlock.stableKey(lessonKeys: List<Int>): String = when {
    beforeFirst -> key
    else -> "${if (isBreak) "break" else "lesson"}-${lessonKeys[period - 1]}"
}

private fun TimelineBlock.displayHeight(fontScale: Float): Dp =
    (TimelineMinuteHeight * minutes).coerceAtLeast(72.dp * fontScale.coerceAtLeast(1f))

private fun resizeTimelineEntry(session: PeriodTimelineSession, block: TimelineBlock, minutes: Int): PeriodTimelineSession {
    return if (block.beforeFirst) resizeTimelineLeadingBreak(
        session, block.part, minutes
    ) else resizeTimelineBlock(session, block.period, block.isBreak, minutes)
}

/** Opening the timeline materializes automatic rules, which a rename must not overwrite. */
internal fun preservePeriodSchemeMetadataForRename(
    original: PeriodTimelineSession,
    edited: PeriodTimelineSession
): PeriodTimelineSession {
    val withoutRename = edited.updateActive(edited.active.copy(scheme = edited.active.scheme.copy(
        name = original.active.scheme.name)))
    return if (!withoutRename.hasChangesFrom(original)) {
        original.updateActive(original.active.copy(scheme = original.active.scheme.copy(
            name = edited.active.scheme.name)))
    } else edited
}

@Composable
internal fun PeriodSchemeEditor(
    config: ScheduleConfigEntity,
    request: PeriodTimelineSession,
    onSaveSession: suspend (PeriodTimelineSession) -> Unit,
    onEditorFinished: () -> Unit,
    exitCommitRequest: Int,
    hasAdditionalChanges: Boolean,
    saveMessage: String?,
    canChangeStructure: Boolean,
    metadataContent: @Composable (Backdrop, Boolean) -> Unit,
    onDeleteRoutine: (suspend () -> Unit)? = null,
) {
    val initialSession = remember(request) {
        request.updateActive(request.active.materializeForTimeline(request.config))
    }
    var session by remember(request) { mutableStateOf(initialSession) }
    var showExitConfirmation by remember { mutableStateOf(false) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    var deletingBlock by remember { mutableStateOf<TimelineBlock?>(null) }
    var pickingBlock by remember { mutableStateOf<TimelineBlock?>(null) }
    var pickingPart by remember { mutableStateOf<PeriodDayPart?>(null) }
    var sheetVisible by remember { mutableStateOf(true) }
    var editorReady by remember { mutableStateOf(false) }
    var dragBase by remember { mutableStateOf<PeriodTimelineSession?>(null) }
    var localError by remember { mutableStateOf<String?>(null) }
    var additionError by remember { mutableStateOf<String?>(null) }
    var closing by remember { mutableStateOf(false) }
    var lessonKeys by remember(request) { mutableStateOf((1..request.config.totalPeriodCount()).toList()) }
    var nextLessonKey by remember(request) { mutableIntStateOf(request.config.totalPeriodCount() + 1) }
    var changingKeys by remember { mutableStateOf(emptySet<String>()) }
    var changingStructure by remember { mutableStateOf(false) }
    val cardMotion = remember { Animatable(1f) }
    val breakExpansion = remember { Animatable(1f) }
    var previousBreakHeights by remember { mutableStateOf(emptyMap<String, Dp>()) }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val pageColor = settingsPageBackground(config)

    fun addPeriod(
        part: PeriodDayPart,
        restoring: Boolean = false,
        operation: (PeriodTimelineSession) -> PeriodTimelineSession?
    ) {
        if (changingStructure || !canChangeStructure) return
        val current = session
        val value = operation(current)
        if (value == null) {
            if (current.config.totalPeriodCount() >= 40) {
                additionError = "最多支持 40 节，添加或恢复节次前请先减少总节数。"
                return
            }
            // Diagnose the same atomic operation without duplicating the domain's time rules.
            val blocked = current.draft.schemes.firstOrNull { scheme ->
                operation(current.copy(draft = current.draft.copy(
                    schemes = listOf(scheme), activeSchemeId = scheme.scheme.id
                ))) == null
            }
            val action = if (restoring) "恢复" else "添加"
            additionError = if (blocked != null) {
                val name = blocked.scheme.name.ifBlank { "未命名作息" }
                val invalid = validateResolvedPeriodTimes(resolveSchemeTimes(current.config, blocked))
                if (invalid != null) "“$name”的时间有误：$invalid。请先修正这套作息。"
                else "“$name”的${part.timelineLabel()}没有足够空间${action}节次，请先调整这套作息的时间。"
            } else "当前作息无法${action}节次，请检查节数和时间安排。"
            return
        }
        val after = (value.draft.topologyOperations.last() as PeriodTopologyOperation.AddAfter).periodIndex
        val oldKeys = timelineBlocks(current.config, current.active, true).map { it.stableKey(lessonKeys) }.toSet()
        val updatedKeys = lessonKeys.toMutableList().apply { add(after, nextLessonKey++) }
        changingStructure = true
        scope.launch {
            cardMotion.snapTo(0f)
            changingKeys = timelineBlocks(value.config, value.active, true)
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
        if (closing || changingStructure || !editorReady) return
        val current = session
        val hasChanges = current.hasChangesFrom(initialSession) || hasAdditionalChanges
        if (commit && hasChanges) {
            val error = current.draft.schemes.firstNotNullOfOrNull {
                validateResolvedPeriodTimes(resolveSchemeTimes(current.config, it))
            }
            if (error != null) {
                localError = error
                return
            }
        }
        closing = true
        scope.launch {
            if (commit && hasChanges) {
                try {
                    onSaveSession(preservePeriodSchemeMetadataForRename(request, current))
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    localError = failure.message ?: "作息保存失败"
                    closing = false
                    return@launch
                }
            }
            sheetVisible = false
        }
    }

    fun requestExit() {
        if (closing || changingStructure || !editorReady) return
        if (session.hasChangesFrom(initialSession) || hasAdditionalChanges) showExitConfirmation = true
        else leave(commit = false)
    }

    var handledExitRequest by remember { mutableIntStateOf(exitCommitRequest) }
    LaunchedEffect(exitCommitRequest) {
        if (exitCommitRequest != handledExitRequest) {
            handledExitRequest = exitCommitRequest
            requestExit()
        }
    }

    session.let { edit ->
        val dark = appUsesDarkTheme(config)
        val windowBackdrop = rememberCouiWindowBackdrop()
        val sheetMaterial = if (Build.VERSION.SDK_INT >= 33 && windowBackdrop != null &&
            LocalCouiMaterialEnabled.current && !LocalCouiLiquidMaterial.current
        ) CouiSheetDefaults.material(dark) else null
        ModalBottomSheet(
            visible = sheetVisible,
            onDismissRequest = ::requestExit,
            onDismissFinished = onEditorFinished,
            onShowFinished = { editorReady = true },
            title = "编辑作息",
            dismissDescription = "关闭编辑作息",
            dark = dark,
            draggable = false,
            dismissOnClickOutside = false,
            showDragHandle = false,
            contentDrawsBehindNavigationBar = true,
            colors = CouiSheetDefaults.colors(dark).copy(surface = pageColor),
            material = sheetMaterial,
            backdrop = windowBackdrop,
            isolateContent = false,
        ) {
            val navigationInset = with(density) {
                (WindowInsets.navigationBars.getBottom(this) - WindowInsets.ime.getBottom(this)).coerceAtLeast(0).toDp()
            }
            val sheetScene = rememberCenteredDialogSceneBackdrop("period-editor-dialog-scene")
            val liquidButtons = Build.VERSION.SDK_INT >= 31 && LocalCouiLiquidMaterial.current
            val sheetBackgroundBackdrop = rememberGlassLayerBackdrop(
                GlassBackdropDomain.Background,
                "period-editor-background",
            )
            val buttonBackdrop = sheetBackgroundBackdrop.takeIf { liquidButtons }
            val editorBackdrop = rememberGlassLayerBackdrop(GlassBackdropDomain.Content, "period-editor-content") {
                drawRect(pageColor)
                drawContent()
            }
            Box(Modifier.fillMaxSize().centeredDialogSceneProducer(sheetScene)) {
                if (liquidButtons) {
                    // Add-period buttons sit inside the scrolled Column (which records editorBackdrop),
                    // so they sample this sibling background layer instead to avoid a self-sampling cycle.
                    Box(
                        Modifier
                            .matchParentSize()
                            .glassBackdropProducer(sheetBackgroundBackdrop)
                            .background(pageColor),
                    )
                }
                val blocks = remember(edit) { timelineBlocks(edit.config, edit.active, includeLeading = true) }
                val editScroll = rememberScrollState()
                var viewportHeight by remember { mutableIntStateOf(0) }
                var retainedScroll by remember { mutableIntStateOf(0) }
                val interactive = editorReady && !closing && !changingStructure
                LaunchedEffect(editScroll) {
                    snapshotFlow { editScroll.isScrollInProgress to editScroll.value }.collect { (scrolling, offset) ->
                        if (scrolling && dragBase == null) retainedScroll = offset
                    }
                }
                fun resize(block: TimelineBlock, minutes: Int): Int {
                    val current = session
                    val updated = resizeTimelineEntry(dragBase ?: current, block, minutes)
                    session = updated
                    return timelineBlocks(updated.config, updated.active, includeLeading = true)
                        .firstOrNull { it.key == block.key }?.minutes ?: if (block.isBreak) 0 else block.minutes
                }
                Column(Modifier.fillMaxSize().glassBackdropProducer(editorBackdrop)
                    .onSizeChanged { viewportHeight = it.height }
                    .couiVerticalScroll(editScroll, enabled = interactive && dragBase == null)
                    // Keep the current offset valid even when a card near the bottom becomes shorter.
                    .heightIn(min = with(density) { (retainedScroll + viewportHeight).toDp() })
                    .padding(start = 16.dp, end = 16.dp, top = 72.dp, bottom = 16.dp + navigationInset),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("拖动右下角调整时长 · 每格 1 分钟\n点按卡片选择时间", fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    SettingsTextFieldRow("作息名称", edit.active.scheme.name, { name ->
                        session = edit.updateActive(edit.active.copy(scheme = edit.active.scheme.copy(name = name.take(60))))
                    }, enabled = interactive)
                    CompositionLocalProvider(LocalSettingsPopupBackdrop provides sheetScene) {
                        metadataContent(sheetScene, interactive)
                    }
                    PeriodDayPart.entries.forEach { part ->
                        val partBlocks = blocks.filter { it.part == part }
                        val vacancies = edit.vacancies.filter { it.part == part }
                        if (partBlocks.isNotEmpty() || vacancies.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                TimelinePartDivider(part, timelinePartAnchorMinute(edit.config, edit.active, part),
                                    enabled = interactive && edit.config.periodCount(part) > 0) { pickingPart = part }
                                    Column(Modifier.fillMaxWidth().semantics {
                                        contentDescription = "${part.timelineLabel()}，课程 ${partBlocks.filterNot { it.isBreak }.sumOf { it.minutes }} 分钟，课间 ${partBlocks.filter { it.isBreak }.sumOf { it.minutes }} 分钟"
                                    },
                                        verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                        fun insertionPosition(block: TimelineBlock): Int = when {
                                            block.beforeFirst -> 0
                                            else -> block.period - edit.config.periodRange(part).first + 1
                                        }
                                        fun hasAddAfter(block: TimelineBlock): Boolean = block.isBreak &&
                                            vacancies.any { it.after == insertionPosition(block) }
                                        partBlocks.forEachIndexed { blockIndex, block ->
                                            val stableKey = block.stableKey(lessonKeys)
                                            key(stableKey) {
                                                if (block.isBreak && block.minutes == 0) {
                                                    Box(Modifier.fillMaxWidth().padding(end = 38.dp)
                                                        .heightIn(min = 48.dp)
                                                        .clickable(enabled = interactive, onClickLabel = "添加课间") { pickingBlock = block },
                                                        contentAlignment = Alignment.Center) {
                                                        Text("添加课间", color = TimelineBreakColor, style = MaterialTheme.typography.labelLarge)
                                                    }
                                                } else {
                                                Box(Modifier.fillMaxWidth()) {
                                                Box(Modifier.fillMaxWidth().padding(end = 38.dp)) {
                                                TimelineEditCard(block, dark = dark,
                                                    resizeStartHeight = if (block.isBreak && breakExpansion.value < 1f)
                                                        previousBreakHeights[stableKey] ?: 0.dp else null,
                                                    resizeProgress = { breakExpansion.value },
                                                    sizeProgress = { if (stableKey in changingKeys) cardMotion.value else 1f },
                                                    enabled = interactive,
                                                    onPick = { pickingBlock = block },
                                                    onDelete = if (block.isBreak || canChangeStructure) {
                                                        { deletingBlock = block }
                                                    } else null,
                                                    onResizeStarted = {
                                                        retainedScroll = editScroll.value
                                                        dragBase = session
                                                    },
                                                    onResizeFinished = { dragBase = null },
                                                    onResize = { resize(block, it) })
                                                }
                                                TimelineRatioRailSegment(block.color,
                                                    first = blockIndex == 0 || hasAddAfter(partBlocks[blockIndex - 1]) || partBlocks[blockIndex - 1].minutes == 0,
                                                    last = blockIndex == partBlocks.lastIndex || hasAddAfter(block) || partBlocks[blockIndex + 1].minutes == 0,
                                                    modifier = Modifier.matchParentSize().graphicsLayer {
                                                        alpha = if (stableKey in changingKeys) cardMotion.value else 1f
                                                    })
                                                }
                                                }
                                                if (block.isBreak) {
                                                    val after = insertionPosition(block)
                                                    vacancies.filter { it.after == after }.forEach { vacancy ->
                                                        TimelineAddPeriodButton(buttonBackdrop, interactive, Modifier.padding(top = 10.dp, end = 38.dp)) {
                                                            addPeriod(part, restoring = true) { insertTimelinePeriod(it, vacancy.id) }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                        val visibleInsertionPoints = partBlocks.filter { it.isBreak }.map(::insertionPosition).toSet()
                                        vacancies.filter { it.after !in visibleInsertionPoints }.forEach { vacancy ->
                                            TimelineAddPeriodButton(buttonBackdrop, interactive, Modifier.padding(end = 38.dp)) {
                                                addPeriod(part, restoring = true) { insertTimelinePeriod(it, vacancy.id) }
                                            }
                                        }
                                        if (canChangeStructure && vacancies.none { it.after == edit.config.periodCount(part) }) {
                                            TimelineAddPeriodButton(buttonBackdrop, interactive,
                                                Modifier.padding(end = 38.dp)) {
                                                addPeriod(part) { appendTimelinePeriod(it, part) }
                                            }
                                        }
                                    }
                            }
                        } else if (canChangeStructure) {
                            // Vacancies are editor-local; an empty saved section still needs an entry point.
                            TimelineAddPeriodButton(buttonBackdrop, interactive,
                                Modifier.padding(end = 38.dp),
                                label = "${part.timelineLabel()} · 添加节次") {
                                addPeriod(part) { appendTimelinePeriod(it, part) }
                            }
                        }
                    }
                    if (onDeleteRoutine != null) {
                        TextButton(
                            onClick = { showDeleteConfirmation = true },
                            enabled = interactive,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        ) {
                            Text("删除作息", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
                ProgressiveBackdropBlur(editorBackdrop, Modifier.align(Alignment.TopCenter),
                    tintColor = pageColor, height = 72.dp, blurRadius = 12.dp,
                    fallbackTintStops = listOf(0f to pageColor.copy(alpha = 0.42f),
                        0.68f to pageColor.copy(alpha = 0.18f), 1f to Color.Transparent))
                SettingsEditorSheetToolbar(
                    title = "编辑作息",
                    dark = dark,
                    backdrop = editorBackdrop,
                    enabled = interactive,
                    onCancel = ::requestExit,
                    actionLabel = "完成",
                    onAction = ::requestExit,
                )
            }
            CompositionLocalProvider(LocalCenteredDialogSceneBackdrop provides sheetScene) {
                additionError?.let { message ->
                    LiquidAlertDialog("无法添加节次", message,
                        listOf(LiquidAlertAction("知道了", LiquidAlertActionStyle.Primary, onClick = { additionError = null })), config, { additionError = null })
                }
                localError?.let { message ->
                    LiquidAlertDialog("无法调整作息", message,
                        listOf(LiquidAlertAction("知道了", LiquidAlertActionStyle.Primary) { localError = null }), config, { localError = null })
                }
                if (showExitConfirmation) LiquidAlertDialog(
                    "保存作息调整",
                    saveMessage ?: if (session.draft.topologyOperations.isEmpty()) "要保存这张课表的作息调整吗？"
                    else "节次结构会同步到本课表各套作息，保存前会检查课程的节次。",
                    listOf(
                        LiquidAlertAction("保存", LiquidAlertActionStyle.Primary) { showExitConfirmation = false; leave(true) },
                        LiquidAlertAction("不保存", LiquidAlertActionStyle.Destructive) { showExitConfirmation = false; leave(false) },
                        LiquidAlertAction("继续编辑", LiquidAlertActionStyle.Cancel) { showExitConfirmation = false }
                    ), config, { showExitConfirmation = false })
                if (showDeleteConfirmation && onDeleteRoutine != null) {
                    LiquidAlertDialog("删除作息", "删除“${initialSession.active.scheme.name}”及其日期安排？这些日期将使用下一条适用的作息。",
                        listOf(
                            LiquidAlertAction("取消", LiquidAlertActionStyle.Cancel) { showDeleteConfirmation = false },
                            LiquidAlertAction("删除", LiquidAlertActionStyle.Destructive) {
                                showDeleteConfirmation = false
                                closing = true
                                scope.launch {
                                    try {
                                        onDeleteRoutine()
                                        sheetVisible = false
                                    } catch (cancelled: CancellationException) {
                                        throw cancelled
                                    } catch (failure: Exception) {
                                        localError = failure.message ?: "删除作息失败"
                                        closing = false
                                    }
                                }
                            }
                        ), config, { showDeleteConfirmation = false })
                }
                deletingBlock?.let { block ->
                    LiquidAlertDialog("移除${block.title}", if (block.isBreak) "后续课程将在当前时段内提前 ${block.minutes} 分钟，节次编号不变。"
                        else "这套作息的节次编号将连续调整，删除后可从课间下方添加节次。如果课程占用了被删除的节次，保存会被阻止。",
                        listOf(LiquidAlertAction("取消", LiquidAlertActionStyle.Cancel, onClick = { deletingBlock = null }),
                            LiquidAlertAction("移除", LiquidAlertActionStyle.Destructive, onClick = {
                                session.let { edit ->
                                    val result = if (block.isBreak) resizeTimelineEntry(edit, block, 0) else deleteTimelinePeriod(edit, block.period)
                                    if (result == null) localError = "至少需要保留一个节次" else if (!changingStructure) {
                                        changingStructure = true
                                        val before = timelineBlocks(edit.config, edit.active, includeLeading = true)
                                        val precedingBreak = before.getOrNull(before.indexOfFirst { it.key == block.key } - 1)
                                            ?.takeIf { !block.isBreak && it.isBreak && it.part == block.part && block.period == edit.config.periodRange(block.part).last }
                                        changingKeys = listOfNotNull(block, precedingBreak).map { it.stableKey(lessonKeys) }.toSet()
                                        scope.launch {
                                            cardMotion.animateTo(0f, tween(160, easing = TimelineRemoveEasing))
                                            previousBreakHeights = before.filter { it.isBreak }.associate {
                                                it.stableKey(lessonKeys) to it.displayHeight(density.fontScale)
                                            }
                                            val updatedKeys = if (block.isBreak) lessonKeys else lessonKeys.filterIndexed { index, _ -> index != block.period - 1 }
                                            val expandingBreak = timelineBlocks(result.config, result.active, includeLeading = true)
                                                .filter { it.isBreak }.any {
                                                    it.displayHeight(density.fontScale) != (previousBreakHeights[it.stableKey(updatedKeys)] ?: 0.dp)
                                                }
                                            if (expandingBreak) breakExpansion.snapTo(0f)
                                            lessonKeys = updatedKeys
                                            session = result
                                            changingKeys = emptySet()
                                            cardMotion.snapTo(1f)
                                            if (expandingBreak) {
                                                withFrameNanos { }
                                                breakExpansion.animateTo(1f, tween(220, easing = TimelineInsertEasing))
                                            }
                                            previousBreakHeights = emptyMap()
                                            changingStructure = false
                                        }
                                    }
                                }
                                deletingBlock = null
                            })), config, { deletingBlock = null })
                }
                pickingBlock?.let { block ->
                    TimelineBlockPicker(block, session, sheetScene, config,
                        onDismiss = { pickingBlock = null }, onChange = { session = it; pickingBlock = null })
                }
                pickingPart?.let { part ->
                    TimelinePartStartPicker(part, session, sheetScene, config,
                        onDismiss = { pickingPart = null }, onChange = { session = it; pickingPart = null })
                }
            }
        }
    }
}

@Composable
private fun TimelineEditCard(
    block: TimelineBlock, dark: Boolean, enabled: Boolean, sizeProgress: () -> Float,
    resizeStartHeight: Dp?, resizeProgress: () -> Float,
    onPick: () -> Unit, onDelete: (() -> Unit)?, onResizeStarted: () -> Unit, onResizeFinished: () -> Unit,
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
    val surface = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 1f)
    val fill = lerp(surface, block.color, if (dragging) 0.34f else 0.24f)
    val durationColor = if (dark) lerp(block.color, Color.White, 0.32f)
        else lerp(block.color, Color.Black, 0.24f)
    val targetHeight = block.displayHeight(density.fontScale)
    val height = resizeStartHeight?.let { it + (targetHeight - it) * resizeProgress() } ?: targetHeight
    BoxWithConstraints(Modifier.fillMaxWidth().height(height).graphicsLayer {
        alpha = sizeProgress()
        scaleX = sizeProgress()
        scaleY = scaleX
    }) {
        val cardWidth = maxWidth
        Box(Modifier.fillMaxSize().clip(RoundedRectangle(24.dp))
            .background(fill)
            .clickable(enabled = enabled, onClickLabel = "选择${block.title}时间", onClick = onPick)) {
            Row(Modifier.fillMaxSize().padding(start = 22.dp, end = 24.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(block.title, fontWeight = if (block.isBreak) FontWeight.Bold else FontWeight.SemiBold,
                        fontSize = if (block.isBreak) 16.sp else 18.sp)
                    Text("${timelineMinuteText(block.start)} – ${timelineMinuteText(block.end)}", fontSize = if (block.isBreak) 12.sp else 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("${block.minutes}分钟", color = durationColor,
                    fontSize = if (cardWidth < 300.dp) 24.sp else 28.sp,
                    fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
        }
        if (onDelete != null) Box(
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
            Box(Modifier.size(34.dp).background(lerp(block.color, fill, if (dragging) 0f else 0.25f), cornerShape))
        }
    }
}

@Composable
private fun TimelineAddPeriodButton(
    backdrop: Backdrop?, enabled: Boolean, modifier: Modifier = Modifier,
    label: String = "添加节次", onClick: () -> Unit
) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        DialogLiquidButton(backdrop, label, { if (enabled) onClick() }, role = DialogButtonRole.Confirm)
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
    val bounds = timelineFlexiblePartStartBounds(session.config, session.active, part) ?: return
    val original = timelinePartAnchorMinute(session.config, session.active, part)
    var selected by remember { mutableIntStateOf(original.coerceIn(bounds)) }
    var visible by remember { mutableStateOf(true) }
    var pending by remember { mutableStateOf<PeriodTimelineSession?>(null) }
    SleepDownPickerDialog(visible, "${part.timelineLabel()}分段起点", { visible = false }, visualConfig,
        onDismissFinished = { pending?.let(onChange) ?: onDismiss() },
        bottomActions = {
            PeriodPickerActions(backdrop, visualConfig, onCancel = { visible = false }, onConfirm = {
                    pending = moveTimelinePartWithNeighbours(session, part, selected)
                    visible = false
            })
        },
        scrollableContent = true) {
        Column(verticalArrangement = Arrangement.spacedBy(SleepDownDesignTokens.QuickSheet.PickerContentSpacing)) {
            SettingsTimePickerContent(selected, bounds) { selected = it }
            val preview = remember(session, part, selected) { moveTimelinePartWithNeighbours(session, part, selected) }
            val movedParts = PeriodDayPart.entries.filter { section ->
                session.config.periodCount(section) > 0 &&
                    timelinePartAnchorMinute(session.config, session.active, section) !=
                    timelinePartAnchorMinute(preview.config, preview.active, section)
            }
            val movementHint = when (movedParts.size) {
                0 -> "当前起点未改变"
                1 -> "仅平移${part.timelineLabel()}；课程与课间时长不变"
                else -> "将同时平移${movedParts.joinToString("、") { it.timelineLabel() }}；课程与课间时长不变"
            }
            Text(movementHint, modifier = Modifier.padding(horizontal = 16.dp), fontSize = 12.sp,
                color = androidx.compose.material3.LocalContentColor.current.copy(alpha = 0.66f))

        }
    }
}

@Composable
private fun TimelineRatioRailSegment(color: Color, first: Boolean, last: Boolean, modifier: Modifier) {
    // Share the card's measured bounds. Bridge only the gap to another card; action rows carry no time.
    Canvas(modifier) {
        val radius = CornerRadius(7.dp.toPx())
        val halfGap = 6.dp.toPx()
        val outline = Path().apply {
            addRoundRect(RoundRect(
                left = size.width - 14.dp.toPx(), top = if (first) 0f else -halfGap,
                right = size.width, bottom = size.height + if (last) 0f else halfGap,
                topLeftCornerRadius = if (first) radius else CornerRadius.Zero,
                topRightCornerRadius = if (first) radius else CornerRadius.Zero,
                bottomLeftCornerRadius = if (last) radius else CornerRadius.Zero,
                bottomRightCornerRadius = if (last) radius else CornerRadius.Zero
            ))
        }
        drawPath(outline, color)
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
    val current = timelineBlocks(candidate.config, candidate.active, includeLeading = true)
        .firstOrNull { it.key == block.key } ?: block
    val firstLesson = !block.isBreak && block.period == candidate.config.periodRange(block.part).first
    val title = when (page) {
        TimelinePickerPage.DETAILS -> "编辑${block.title}"
        TimelinePickerPage.START -> "开始时间"
        TimelinePickerPage.DURATION -> if (block.isBreak) "${block.title}时长" else "课时时长"
    }
    SleepDownPickerDialog(visible, title,
        contentPadding = if (page == TimelinePickerPage.DETAILS) CouiPickerFormPadding else PaddingValues(0.dp),
        onDismissRequest = {
            if (page == TimelinePickerPage.DETAILS || block.isBreak) visible = false else page = TimelinePickerPage.DETAILS
        }, config = visualConfig,
        onDismissFinished = { pending?.let(onChange) ?: onDismiss() },
        scrollableContent = true,
        bottomActions = {
            val shown = page
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
        },
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
                        Text("不能早于分段起点 ${timelineMinuteText(bounds.first)}，后续课程一起顺延",
                            modifier = Modifier.padding(horizontal = 16.dp),
                            fontSize = 12.sp, color = androidx.compose.material3.LocalContentColor.current.copy(alpha = 0.66f))
                    }
                    TimelinePickerPage.DURATION -> {
                        val maximumSession = resizeTimelineEntry(candidate, current, LastMinuteOfDay)
                        val maximum = timelineBlocks(maximumSession.config, maximumSession.active, includeLeading = true)
                            .firstOrNull { it.key == block.key }?.minutes ?: current.minutes
                        // 0 keeps the break absent so two lessons stay back to back.
                        val minimum = if (block.isBreak) 0 else minimumTimelineLessonMinutes(candidate.config, candidate.active, block.period)
                        SettingsMinutePickerContent(selected, { selected = it }, minimum..maxOf(minimum, maximum))
                    }
                }

            }
        }
    ) {}
}
