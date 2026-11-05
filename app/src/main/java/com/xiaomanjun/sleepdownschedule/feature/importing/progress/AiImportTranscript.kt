package com.xiaomanjun.sleepdownschedule.feature.importing.progress

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.xiaomanjun.sleepdownschedule.feature.agent.AgentMarkdownText
import com.xiaomanjun.sleepdownschedule.feature.importing.AiImportReport
import com.xiaomanjun.sleepdownschedule.feature.importing.AiImportReportKind
import com.xiaomanjun.sleepdownschedule.feature.importing.normalizeAiImportReport
import com.xiaomanjun.sleepdownschedule.feature.importing.importPublicProgressText
import com.xiaomanjun.sleepdownschedule.R
import com.kyant.shapes.Capsule
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

internal val AiImportTranscriptIndent = 24.dp

/** Public model progress remains in the conversation; technical activity is available on demand. */
@Composable
internal fun AiImportTranscriptEntry(report: AiImportReport, streaming: Boolean = false) {
    val model = report.kind == AiImportReportKind.MODEL
    val colors = LocalAiImportTextColors.current
    if (report.kind == AiImportReportKind.USER) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            Text(report.text, color = colors.primary, style = AiImportTextStyles.Body,
                modifier = Modifier.fillMaxWidth(0.86f).clip(com.kyant.shapes.RoundedRectangle(20.dp))
                    .background(if (colors.primary.luminance() > 0.5f) androidx.compose.ui.graphics.Color(0xFF142D49) else androidx.compose.ui.graphics.Color(0xFFDCEBFF))
                    .padding(horizontal = 16.dp, vertical = 12.dp))
        }
        return
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(16.dp, 20.dp), contentAlignment = Alignment.Center) {
            if (model) Box(Modifier.size(4.dp).background(colors.muted, CircleShape))
            else Icon(painterResource(R.drawable.ic_import_step), contentDescription = null,
                tint = colors.muted, modifier = Modifier.size(16.dp))
        }
        Column(Modifier.weight(1f)) {
            if (model) AgentMarkdownText(report.text, colors.secondary, AiImportTextStyles.Progress, streaming = streaming)
            else Text(report.text, color = colors.muted, style = AiImportTextStyles.Operation)
        }
    }
}

/** Keep model narration in place; only adjacent execution events share one collapsible group. */
internal fun groupAiImportReports(reports: List<AiImportReport>): List<Pair<Int, List<AiImportReport>>> {
    val groups = mutableListOf<Pair<Int, MutableList<AiImportReport>>>()
    reports.forEachIndexed { index, raw ->
        val report = normalizeAiImportReport(raw)
        if (report.text.isBlank()) return@forEachIndexed
        val previous = groups.lastOrNull()?.second?.lastOrNull()
        if (previous?.text == report.text && previous.kind != AiImportReportKind.USER && report.kind != AiImportReportKind.USER) {
            // Keep public narration when two channels report exactly the same event.
            if (previous.kind == AiImportReportKind.MODEL || report.kind == AiImportReportKind.EXECUTION) return@forEachIndexed
            groups.last().second.removeAt(groups.last().second.lastIndex)
            if (groups.last().second.isEmpty()) groups.removeAt(groups.lastIndex)
        }
        if (report.kind == AiImportReportKind.EXECUTION && groups.lastOrNull()?.second?.lastOrNull()?.kind == report.kind)
            groups.last().second += report
        else groups += index to mutableListOf(report)
    }
    return groups
}

@Composable
private fun AiImportDisclosureRow(title: String, expanded: Boolean, onToggle: () -> Unit) {
    val colors = LocalAiImportTextColors.current
    val rotation by animateFloatAsState(if (expanded) 90f else 0f, label = "import-disclosure")
    Row(Modifier.heightIn(min = 40.dp).clip(Capsule())
        .semantics { stateDescription = if (expanded) "已展开" else "已折叠" }
        .clickable(role = Role.Button, onClickLabel = if (expanded) "收起$title" else "展开$title", onClick = onToggle)
        .padding(end = 12.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null,
            tint = colors.muted, modifier = Modifier.size(16.dp).graphicsLayer { rotationZ = rotation })
        Text(title, color = colors.muted, style = AiImportTextStyles.Label)
    }
}

@Composable
internal fun AiImportToolGroup(reports: List<AiImportReport>, active: Boolean) {
    var expandedOverride by remember { mutableStateOf<Boolean?>(null) }
    val expanded = expandedOverride ?: active
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        AiImportDisclosureRow("执行步骤 · ${reports.size}", expanded) { expandedOverride = !expanded }
        if (expanded) reports.forEach { AiImportTranscriptEntry(it) }
    }
}

@Composable
internal fun AiImportLegacyProgress(reports: List<AiImportReport>, summary: String, steps: List<String> = emptyList(), active: Boolean = false) {
    val legacySteps = steps.takeIf { reports.none { report -> report.kind == AiImportReportKind.EXECUTION } }.orEmpty()
    val legacySummary = summary.takeIf { reports.none { report -> report.kind == AiImportReportKind.MODEL } }
        ?.let(::importPublicProgressText).orEmpty()
    if (legacySummary.isBlank() && legacySteps.isEmpty()) return
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (legacySummary.isNotBlank()) AiImportTranscriptEntry(AiImportReport(legacySummary, AiImportReportKind.MODEL))
        if (legacySteps.isNotEmpty()) AiImportToolGroup(legacySteps.distinct().map { AiImportReport(it, AiImportReportKind.EXECUTION) }, active)
    }
}

internal suspend fun LazyListState.scrollToAiImportEnd() {
    val last = layoutInfo.totalItemsCount - 1
    if (last < 0) return
    scrollToItem(last)
    val layout = layoutInfo
    layout.visibleItemsInfo.lastOrNull()?.let {
        scrollBy((it.offset + it.size + layout.afterContentPadding - layout.viewportEndOffset).coerceAtLeast(0).toFloat())
    }
}

private data class TranscriptLayout(val count: Int, val lastIndex: Int, val lastHeight: Int,
    val viewportStart: Int, val viewportEnd: Int, val bottomPadding: Int)

private data class TranscriptViewport(val layout: TranscriptLayout, val canScrollForward: Boolean, val dragging: Boolean)

/** Follow new content until the user scrolls back; token updates never force a return to the end. */
@Composable
internal fun rememberAiImportFollowingLatest(list: LazyListState, taskId: String): MutableState<Boolean> {
    val following = remember(taskId) { mutableStateOf(true) }
    val dragging = list.interactionSource.collectIsDraggedAsState()
    LaunchedEffect(list, taskId) {
        var previous: TranscriptLayout? = null
        var scrolling: Job? = null
        snapshotFlow {
            val layout = list.layoutInfo
            val last = layout.visibleItemsInfo.lastOrNull()
            TranscriptViewport(TranscriptLayout(layout.totalItemsCount, last?.index ?: -1, last?.size ?: 0,
                layout.viewportStartOffset, layout.viewportEndOffset, layout.afterContentPadding),
                list.canScrollForward, dragging.value)
        }.collect { viewport ->
            if (viewport.dragging) {
                scrolling?.cancel()
                following.value = !viewport.canScrollForward
            } else if (!viewport.canScrollForward) following.value = true
            // The IME can grow the composer padding or resize the window without changing
            // a single message. Keep a previously pinned end above that changing boundary.
            val shape = viewport.layout
            if (shape != previous && following.value && !viewport.dragging) {
                scrolling?.cancel()
                scrolling = launch { withFrameNanos { }; list.scrollToAiImportEnd() }
            }
            previous = shape
        }
    }
    return following
}
